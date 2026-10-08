#include "Limelight-internal.h"

bool AndroidInputOnly;
bool AndroidRemoteMonitor;

// Keep the core's queue initialization/teardown for control-channel callbacks, but never
// start media decoders, receive threads or missing-video timers for an input-only session.
#define startVideoStream(ctx, flags) (AndroidInputOnly ? 0 : startVideoStream(ctx, flags))
#define startAudioStream(ctx, flags) (AndroidInputOnly ? 0 : startAudioStream(ctx, flags))
#define stopVideoStream() do { if (!AndroidInputOnly) stopVideoStream(); } while (0)
#define stopAudioStream() do { if (!AndroidInputOnly) stopAudioStream(); } while (0)
#define startInputStream() (AndroidRemoteMonitor ? 0 : startInputStream())
#define stopInputStream() (AndroidRemoteMonitor ? 0 : stopInputStream())
#define LiSendMouseMoveEvent(x, y) (AndroidRemoteMonitor ? 0 : LiSendMouseMoveEvent(x, y))
#include "moonlight-common-c/src/Connection.c"
