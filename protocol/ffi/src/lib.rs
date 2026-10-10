//! C ABI over `rubylight-protocol` for the Android client (see `include/rubylight_protocol.h`).
//!
//! Built as a static library linked into the client's native code. Outside tests it is
//! `no_std`: memory comes from the C allocator and a panic aborts, so the library needs
//! nothing beyond libc.
#![cfg_attr(not(test), no_std)]

extern crate alloc;

use alloc::boxed::Box;
use core::ffi::c_void;
use core::slice;
use rubylight_protocol::pyrowave::{self, Records};

/// Receives one decoder packet; returns false to stop and fail the frame.
pub type PushPacket = unsafe extern "C" fn(user: *mut c_void, data: *const u8, length: usize) -> bool;

unsafe fn bytes<'a>(data: *const u8, length: usize) -> &'a [u8] {
    if length == 0 { &[] } else { slice::from_raw_parts(data, length) }
}

fn forward(push: PushPacket, user: *mut c_void) -> impl FnMut(&[u8]) -> bool {
    move |packet| unsafe { push(user, packet.as_ptr(), packet.len()) }
}

/// Creates record reassembly state for a stream of the negotiated geometry; a zero width or
/// height means none was negotiated.
#[no_mangle]
pub extern "C" fn rp_pyrowave_records_new(width: u32, height: u32, chroma444: bool) -> *mut Records {
    let records = if width == 0 || height == 0 { Records::default() } else { Records::with_geometry(width, height, chroma444) };
    Box::into_raw(Box::new(records))
}

/// # Safety
/// `records` comes from `rp_pyrowave_records_new` and is not used afterwards; null is ignored.
#[no_mangle]
pub unsafe extern "C" fn rp_pyrowave_records_free(records: *mut Records) {
    if !records.is_null() {
        drop(Box::from_raw(records));
    }
}

/// Feeds one record-framed frame to `push`, sequence header first.
///
/// # Safety
/// `records` is live, `data` points to `length` readable bytes, and `push` is safe to call
/// with `user`.
#[no_mangle]
pub unsafe extern "C" fn rp_pyrowave_records_push_frame(
    records: *mut Records,
    data: *const u8,
    length: usize,
    push: PushPacket,
    user: *mut c_void,
) -> bool {
    (*records).push_frame(bytes(data, length), forward(push, user))
}

/// Share of the last frame's records that were lost, 0-100.
///
/// # Safety
/// `records` is live.
#[no_mangle]
pub unsafe extern "C" fn rp_pyrowave_records_loss_percent(records: *const Records) -> f32 {
    (*records).loss_percent
}

/// Validates an ordinary (pre-record) container and feeds its packets to `push`.
///
/// # Safety
/// `data` points to `length` readable bytes, and `push` is safe to call with `user`.
#[no_mangle]
pub unsafe extern "C" fn rp_pyrowave_push_container(
    data: *const u8,
    length: usize,
    push: PushPacket,
    user: *mut c_void,
) -> bool {
    pyrowave::push_container(bytes(data, length), forward(push, user))
}

#[cfg(not(test))]
mod runtime {
    use core::alloc::{GlobalAlloc, Layout};
    use core::ffi::c_void;

    extern "C" {
        fn malloc(size: usize) -> *mut c_void;
        fn free(pointer: *mut c_void);
        fn realloc(pointer: *mut c_void, size: usize) -> *mut c_void;
        fn posix_memalign(pointer: *mut *mut c_void, alignment: usize, size: usize) -> i32;
        fn abort() -> !;
    }

    /// malloc already aligns to 16 bytes on every Android ABI.
    const MALLOC_ALIGN: usize = 16;

    struct CAllocator;

    unsafe impl GlobalAlloc for CAllocator {
        unsafe fn alloc(&self, layout: Layout) -> *mut u8 {
            if layout.align() <= MALLOC_ALIGN {
                return malloc(layout.size()) as *mut u8;
            }
            let mut pointer = core::ptr::null_mut();
            let alignment = layout.align().max(core::mem::size_of::<usize>());
            if posix_memalign(&mut pointer, alignment, layout.size()) == 0 { pointer as *mut u8 } else { core::ptr::null_mut() }
        }

        unsafe fn dealloc(&self, pointer: *mut u8, _: Layout) {
            free(pointer as *mut c_void);
        }

        unsafe fn realloc(&self, pointer: *mut u8, layout: Layout, size: usize) -> *mut u8 {
            if layout.align() <= MALLOC_ALIGN {
                return realloc(pointer as *mut c_void, size) as *mut u8;
            }
            let moved = self.alloc(Layout::from_size_align_unchecked(size, layout.align()));
            if !moved.is_null() {
                core::ptr::copy_nonoverlapping(pointer, moved, layout.size().min(size));
                self.dealloc(pointer, layout);
            }
            moved
        }
    }

    #[global_allocator]
    static ALLOCATOR: CAllocator = CAllocator;

    #[panic_handler]
    fn panic(_: &core::panic::PanicInfo) -> ! {
        unsafe { abort() }
    }
}
