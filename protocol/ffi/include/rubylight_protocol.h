// C interface of rubylight-protocol (protocol/ffi/src/lib.rs), the wire formats Rubylight's
// host and clients share. Built as a static library by protocol/build-android.sh.
#pragma once
#include <stdbool.h>
#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// Receives one decoder packet; return false to stop and fail the frame.
typedef bool (*RpPushPacket)(void *user, const uint8_t *data, size_t length);

typedef struct RpPyroWaveRecords RpPyroWaveRecords;

// Record reassembly for a stream of the negotiated geometry; a zero width or height means none
// was negotiated.
RpPyroWaveRecords *rp_pyrowave_records_new(uint32_t width, uint32_t height, bool chroma444);
void rp_pyrowave_records_free(RpPyroWaveRecords *records);
// Feeds one record-framed frame to push, sequence header first; records the frame lost are
// replayed from earlier frames of the same format.
bool rp_pyrowave_records_push_frame(RpPyroWaveRecords *records, const uint8_t *data, size_t length,
                                    RpPushPacket push, void *user);
// Share of the last frame's records that were lost, 0-100 (100 after a rejected frame).
float rp_pyrowave_records_loss_percent(const RpPyroWaveRecords *records);

// Validates an ordinary (pre-record) container and feeds its packets to push.
bool rp_pyrowave_push_container(const uint8_t *data, size_t length, RpPushPacket push, void *user);

// Phase lock (core/src/phase_lock.rs): the client reports how early its frames are ready
// before the display latch, and the host times its frames to match.
#define RP_PHASE_REPORT_MESSAGE_TYPE 0x5530
#define RP_PHASE_REPORT_BYTES 16

typedef struct RpSlackWindow RpSlackWindow;

RpSlackWindow *rp_slack_window_new(void);
void rp_slack_window_free(RpSlackWindow *window);
// Records one shown frame: time from ready to the next latch deadline, and the refresh period.
void rp_slack_window_push(RpSlackWindow *window, int64_t slack_ns, int64_t period_ns);
size_t rp_slack_window_len(const RpSlackWindow *window);
// Writes the report message to out and clears the window; false below min_frames frames.
bool rp_slack_window_report(RpSlackWindow *window, size_t min_frames, uint8_t *out);

#ifdef __cplusplus
}  // extern "C"

#include <memory>

// Calls a C++ callable through RpPushPacket.
template <typename Push>
inline bool rpPushThunk(void *user, const uint8_t *data, size_t length) {
    return (*static_cast<Push *>(user))(data, length);
}

// Owns RpPyroWaveRecords with the interface the renderer used before (frame.h's PyroWaveRecords).
class PyroWaveRecords {
public:
    PyroWaveRecords() : PyroWaveRecords(0, 0, false) {}
    PyroWaveRecords(uint32_t width, uint32_t height, bool chroma444)
        : state(rp_pyrowave_records_new(width, height, chroma444)) {}

    template <typename Push>
    bool pushFrame(const uint8_t *data, size_t length, Push push) {
        const bool ok = state && rp_pyrowave_records_push_frame(state.get(), data, length, &rpPushThunk<Push>, &push);
        lossPercent = state ? rp_pyrowave_records_loss_percent(state.get()) : 100;
        return ok;
    }

    float lossPercent = 0;

private:
    struct Free {
        void operator()(RpPyroWaveRecords *records) const { rp_pyrowave_records_free(records); }
    };
    std::unique_ptr<RpPyroWaveRecords, Free> state;
};

template <typename Push>
bool pushPyroWaveFrame(const uint8_t *data, size_t length, Push push) {
    return rp_pyrowave_push_container(data, length, &rpPushThunk<Push>, &push);
}
#endif
