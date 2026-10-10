# rubylight-protocol

The wire formats Rubylight's host and clients share, written once in Rust.

- `core/` is the `rubylight-protocol` crate: `no_std` with `alloc` and no dependencies, so
  the host can take it as an ordinary dependency.
- `ffi/` wraps it in a C ABI for the Android client (`ffi/include/rubylight_protocol.h`).
- `tests/differential.sh` compares it with the C++ framing the client used before
  (`tests/frame_reference.h`) on random, lossy and corrupted frames.

Today it holds the client side of PyroWave framing: the packet container and the record
reassembly that lets a frame with lost packets still decode.

## Building for Android

The app links prebuilt static libraries from `app/src/main/jni/protocol/prebuilt/`, so a
normal app build needs no Rust. After changing the crate, refresh them:

```sh
rustup target add aarch64-linux-android x86_64-linux-android
protocol/build-android.sh      # runs the tests, then rebuilds both ABIs
protocol/tests/differential.sh # optional: 8 seeds x 2000 streams against the old C++
```

Only the crate's own object goes into each archive (about 25 KB); it needs nothing but libc.
