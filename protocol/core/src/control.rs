//! Client-to-host control messages beyond the ones Moonlight defines.
//!
//! Each travels on the encrypted control stream, like the phase lock report
//! ([`crate::phase_lock::REPORT_MESSAGE_TYPE`]). A client sends them only to a host that
//! announced support (Rubylight 2.2.0 or later), and a host that does not know an id
//! ignores it, so old hosts and stock clients are unaffected. Every message starts with a
//! version byte; a newer client may append fields, which an older host ignores.

/// Control-stream message type of [`DisplayCaps`] (client to host).
pub const DISPLAY_CAPS_MESSAGE_TYPE: u16 = 0x5531;
/// Encoded size of [`DisplayCaps`].
pub const DISPLAY_CAPS_BYTES: usize = 16;
/// Control-stream message type of [`Reconfigure`] (client to host).
pub const RECONFIGURE_MESSAGE_TYPE: u16 = 0x5532;
/// Encoded size of [`Reconfigure`].
pub const RECONFIGURE_BYTES: usize = 12;

const VERSION: u8 = 1;

fn u16_at(data: &[u8], at: usize) -> u16 {
    u16::from_le_bytes([data[at], data[at + 1]])
}

fn u32_at(data: &[u8], at: usize) -> u32 {
    u32::from_le_bytes([data[at], data[at + 1], data[at + 2], data[at + 3]])
}

/// The client display's HDR luminance, sent at stream start and when the display changes
/// (for example a foldable switching screens), so the host tone-maps for the real panel.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct DisplayCaps {
    /// The display can show HDR (HDR10 or HLG) right now.
    pub hdr: bool,
    /// Peak luminance, in hundredths of a nit (1000 nits = 100_000).
    pub max_centinits: u32,
    /// Maximum frame-average luminance, in hundredths of a nit.
    pub max_average_centinits: u32,
    /// Minimum luminance, in ten-thousandths of a nit (0.0005 nits = 5).
    pub min_decimillinits: u32,
}

impl DisplayCaps {
    const HDR_FLAG: u8 = 1;

    /// Little-endian wire form: version, flags, reserved, max, max average, min.
    pub fn encode(&self) -> [u8; DISPLAY_CAPS_BYTES] {
        let mut out = [0; DISPLAY_CAPS_BYTES];
        out[0] = VERSION;
        out[1] = if self.hdr { Self::HDR_FLAG } else { 0 };
        out[4..8].copy_from_slice(&self.max_centinits.to_le_bytes());
        out[8..12].copy_from_slice(&self.max_average_centinits.to_le_bytes());
        out[12..16].copy_from_slice(&self.min_decimillinits.to_le_bytes());
        out
    }

    /// Parses the message; unknown versions and implausible values are rejected.
    pub fn decode(data: &[u8]) -> Option<Self> {
        if data.len() < DISPLAY_CAPS_BYTES || data[0] != VERSION {
            return None;
        }
        let caps = Self {
            hdr: data[1] & Self::HDR_FLAG != 0,
            max_centinits: u32_at(data, 4),
            max_average_centinits: u32_at(data, 8),
            min_decimillinits: u32_at(data, 12),
        };
        // Phones report 0 when they do not know a value; otherwise 1 to 10 000 nits peak,
        // an average no higher than the peak, and a black level below 1 nit.
        let known = |value: u32| value != 0;
        let plausible = (!known(caps.max_centinits)
            || (100..=1_000_000).contains(&caps.max_centinits))
            && (!known(caps.max_average_centinits)
                || !known(caps.max_centinits)
                || caps.max_average_centinits <= caps.max_centinits)
            && caps.min_decimillinits < 10_000;
        plausible.then_some(caps)
    }

    /// Peak luminance in whole nits, or `None` when the client did not know it.
    pub fn max_nits(&self) -> Option<u32> {
        (self.max_centinits != 0).then(|| self.max_centinits.div_ceil(100))
    }

    /// Maximum frame-average luminance in whole nits, or `None` when unknown.
    pub fn max_average_nits(&self) -> Option<u32> {
        (self.max_average_centinits != 0).then(|| self.max_average_centinits.div_ceil(100))
    }
}

/// A request to change the stream's resolution or frame rate without reconnecting, for
/// example when a foldable opens or the window is resized. The host answers with a
/// keyframe at the new size; until then the client keeps decoding the old one.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Reconfigure {
    pub width: u16,
    pub height: u16,
    /// Frame rate in thousandths of a frame per second (120 fps = 120_000).
    pub fps_millihz: u32,
}

impl Reconfigure {
    /// Little-endian wire form: version, reserved, width, height, reserved, fps.
    pub fn encode(&self) -> [u8; RECONFIGURE_BYTES] {
        let mut out = [0; RECONFIGURE_BYTES];
        out[0] = VERSION;
        out[2..4].copy_from_slice(&self.width.to_le_bytes());
        out[4..6].copy_from_slice(&self.height.to_le_bytes());
        out[8..12].copy_from_slice(&self.fps_millihz.to_le_bytes());
        out
    }

    /// Parses the message; unknown versions, odd or tiny sizes and rates outside
    /// 10-500 fps are rejected.
    pub fn decode(data: &[u8]) -> Option<Self> {
        if data.len() < RECONFIGURE_BYTES || data[0] != VERSION {
            return None;
        }
        let request = Self {
            width: u16_at(data, 2),
            height: u16_at(data, 4),
            fps_millihz: u32_at(data, 8),
        };
        let plausible = (256..=8192).contains(&request.width)
            && (256..=8192).contains(&request.height)
            && request.width % 2 == 0
            && request.height % 2 == 0
            && (10_000..=500_000).contains(&request.fps_millihz);
        plausible.then_some(request)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn display_caps_round_trip_and_reject_nonsense() {
        // A Galaxy Z Fold 7 inner screen: 2600 nits peak, 0.0005 nits black.
        let caps = DisplayCaps {
            hdr: true,
            max_centinits: 260_000,
            max_average_centinits: 120_000,
            min_decimillinits: 5,
        };
        let bytes = caps.encode();
        assert_eq!(DisplayCaps::decode(&bytes), Some(caps));
        assert_eq!(caps.max_nits(), Some(2600));
        assert_eq!(caps.max_average_nits(), Some(1200));
        let mut longer = bytes.to_vec();
        longer.extend_from_slice(&[0xff; 4]);
        assert_eq!(
            DisplayCaps::decode(&longer),
            Some(caps),
            "newer clients may append fields"
        );
        assert_eq!(DisplayCaps::decode(&bytes[..DISPLAY_CAPS_BYTES - 1]), None);
        let mut version = bytes;
        version[0] = 2;
        assert_eq!(DisplayCaps::decode(&version), None);
        let unknown = DisplayCaps {
            hdr: false,
            ..DisplayCaps::default()
        };
        assert_eq!(DisplayCaps::decode(&unknown.encode()), Some(unknown));
        assert_eq!(unknown.max_nits(), None);
        for wild in [
            DisplayCaps {
                max_centinits: 5_000_000,
                ..caps
            },
            DisplayCaps {
                max_average_centinits: 300_000,
                ..caps
            },
            DisplayCaps {
                min_decimillinits: 20_000,
                ..caps
            },
        ] {
            assert_eq!(DisplayCaps::decode(&wild.encode()), None, "{wild:?}");
        }
    }

    #[test]
    fn reconfigure_round_trips_and_rejects_nonsense() {
        let request = Reconfigure {
            width: 2176,
            height: 1812,
            fps_millihz: 120_000,
        };
        let bytes = request.encode();
        assert_eq!(Reconfigure::decode(&bytes), Some(request));
        assert_eq!(Reconfigure::decode(&bytes[..RECONFIGURE_BYTES - 1]), None);
        for wild in [
            Reconfigure {
                width: 2177,
                ..request
            },
            Reconfigure {
                height: 100,
                ..request
            },
            Reconfigure {
                width: 10_000,
                ..request
            },
            Reconfigure {
                fps_millihz: 1_000,
                ..request
            },
            Reconfigure {
                fps_millihz: 1_000_000,
                ..request
            },
        ] {
            assert_eq!(Reconfigure::decode(&wild.encode()), None, "{wild:?}");
        }
    }
}
