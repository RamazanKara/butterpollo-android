#include <jni.h>

// Keep the pinned core intact while sharing its encryption, sequence and ENet mutex.
#include "moonlight-common-c/src/ControlStream.c"

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendServerCommand(JNIEnv *env, jclass clazz, jbyteArray payload) {
    jsize length = (*env)->GetArrayLength(env, payload);
    uint8_t bytes[4];
    if ((length != 1 && length != 4) || !IS_SUNSHINE() || !encryptedControlStream || stopping || peer == NULL) {
        return JNI_FALSE;
    }
    (*env)->GetByteArrayRegion(env, payload, 0, length, (jbyte*)bytes);
    if ((*env)->ExceptionCheck(env)) {
        return JNI_FALSE;
    }
    // Apollo's server-control channel is 8; older hosts use the core's channel-zero fallback.
    return sendMessageAndForget(0x3000, (short)length, bytes, 0x08, ENET_PACKET_FLAG_RELIABLE, false)
        ? JNI_TRUE : JNI_FALSE;
}

// Phase lock (protocol/core/src/phase_lock.rs): every half second, tell a Rubylight host how
// early frames were ready before this display's latch, so it can time frames to land just
// before it. Hosts that don't know the message ignore it.
#include <pthread.h>
#include "rubylight_protocol.h"
#include "phase_lock.h"

atomic_bool PhaseLockReportsEnabled;
static pthread_mutex_t phaseLockMutex = PTHREAD_MUTEX_INITIALIZER;
static RpSlackWindow* phaseLockWindow;
static uint64_t phaseLockLastReportMs;
static atomic_int phaseLockLastLeadUs = ATOMIC_VAR_INIT(INT32_MIN);

#define PHASE_LOCK_REPORT_INTERVAL_MS 500
#define PHASE_LOCK_MIN_FRAMES 10

void resetPhaseLockReports(void) {
    pthread_mutex_lock(&phaseLockMutex);
    if (phaseLockWindow) rp_slack_window_free(phaseLockWindow);
    phaseLockWindow = NULL;
    phaseLockLastReportMs = 0;
    atomic_store(&phaseLockLastLeadUs, INT32_MIN);
    pthread_mutex_unlock(&phaseLockMutex);
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_reportFrameSlack(JNIEnv *env, jclass clazz, jlong slackNs, jlong periodNs) {
    (void)env; (void)clazz;
    if (!atomic_load(&PhaseLockReportsEnabled)) return;
    uint8_t report[RP_PHASE_REPORT_BYTES];
    bool send = false;
    pthread_mutex_lock(&phaseLockMutex);
    if (!phaseLockWindow) phaseLockWindow = rp_slack_window_new();
    if (phaseLockWindow) {
        uint64_t now = PltGetMillis();
        if (phaseLockLastReportMs == 0) phaseLockLastReportMs = now;
        rp_slack_window_push(phaseLockWindow, slackNs, periodNs);
        if (now - phaseLockLastReportMs >= PHASE_LOCK_REPORT_INTERVAL_MS) {
            phaseLockLastReportMs = now;
            send = rp_slack_window_report(phaseLockWindow, PHASE_LOCK_MIN_FRAMES, report);
        }
    }
    pthread_mutex_unlock(&phaseLockMutex);
    if (!send) return;
    int32_t leadNs = (int32_t)((uint32_t)report[8] | ((uint32_t)report[9] << 8) |
                               ((uint32_t)report[10] << 16) | ((uint32_t)report[11] << 24));
    atomic_store(&phaseLockLastLeadUs, leadNs / 1000);
    if (!encryptedControlStream || stopping || peer == NULL) return;
    // Timing feedback is only useful fresh, so it is sent unreliably; a lost report is
    // replaced half a second later.
    sendMessageAndForget(RP_PHASE_REPORT_MESSAGE_TYPE, (short)sizeof(report), report,
                         CTRL_CHANNEL_GENERIC, 0, false);
}

// The early edge of the last reported window, in microseconds before the latch (negative when
// late), or Integer.MIN_VALUE before the first report.
JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_getPhaseLockLeadUs(JNIEnv *env, jclass clazz) {
    (void)env; (void)clazz;
    return atomic_load(&phaseLockLastLeadUs);
}
