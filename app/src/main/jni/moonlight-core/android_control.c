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
