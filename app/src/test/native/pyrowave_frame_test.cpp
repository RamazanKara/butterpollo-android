#include <cassert>
#include <cstring>
#include <vector>
#include "../../main/jni/pyrowave-renderer/frame.h"
#include "../../main/jni/moonlight-core/pyrowave_protocol.h"

int main() {
    int calls = 0;
    auto push = [&calls](const uint8_t *, size_t) { calls++; return true; };
    // One sequence header and an empty eight-byte coefficient block in a single packet.
    std::vector<uint8_t> frame = {1,0,0,0, 16,0,0,0, 63,0,0,128, 1,0,0,0, 0,0,2,0, 0,0,0,0};
    assert(pushPyroWaveFrame(frame.data(), frame.size(), push));
    assert(calls == 1);
    for (size_t n = 0; n < frame.size(); ++n) {
        assert(!pushPyroWaveFrame(frame.data(), n, push));
    }
    auto bad = frame;
    bad[0] = 0;
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    bad = frame;
    bad[0] = 255;
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    bad = frame;
    bad[4] = 255;
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    bad = frame;
    bad[18] = 0;
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    bad[18] = 1;
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    bad = frame;
    bad.push_back(0);
    assert(!pushPyroWaveFrame(bad.data(), bad.size(), push));
    assert(!pushPyroWaveFrame(frame.data(), frame.size(), [](const uint8_t *, size_t) { return false; }));

    const char *sdp = "a=rtpmap:99 PYROWAVE/90000\r\na=x-ss-pyrowave.bitstream:186f0393\r\n";
    assert(selectPyroWaveFormat(0xf0000, 0x07800000, sdp) == VIDEO_FORMAT_PYROWAVE_MAIN10_444);
    const int profiles[] = {0x10000, 0x20000, 0x40000, 0x80000};
    for (int i = 0; i < 4; ++i) {
        assert(selectPyroWaveFormat(0xf0000, 0x800000u << i, sdp) == profiles[i]);
        assert(selectPyroWaveFormat(profiles[i], 0x07800000, sdp) == profiles[i]);
    }
    assert(selectPyroWaveFormat(0xf0000, 0, sdp) == 0);
    assert(selectPyroWaveFormat(0xf0000, 0x07800000, "PYROWAVE/90000") == 0);
    assert(selectPyroWaveFormat(0xf0000, 0x07800000, "AV1/90000") == 0);
    assert(selectPyroWaveFormat(0xf0000, 0x07800000,
        "PYROWAVE/90000\na=x-ss-pyrowave.bitstream:186f03930\n") == 0);
}
