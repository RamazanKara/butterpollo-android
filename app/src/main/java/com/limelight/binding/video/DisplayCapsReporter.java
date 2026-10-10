package com.limelight.binding.video;

import java.util.Locale;
import java.util.Objects;

/**
 * Tells a Rubylight host the client display's HDR luminance (control message 0x5531,
 * protocol/core/src/control.rs DisplayCaps), so it tone-maps for the real panel. Sent at
 * stream start and again only when a value changes, for example when a foldable switches
 * screens. One reporter per connection.
 */
public final class DisplayCapsReporter {
    /** Delivers the message; false when it could not go out (it is then retried on the next update). */
    public interface Sender {
        boolean send(boolean hdr, float maxNits, float maxAverageNits, float minNits);
    }

    /** Luminance in nits, 0 when unknown, limited to what the host accepts. */
    public static final class Caps {
        public final boolean hdr;
        public final float maxNits;
        public final float maxAverageNits;
        public final float minNits;

        private Caps(boolean hdr, float maxNits, float maxAverageNits, float minNits) {
            this.hdr = hdr;
            this.maxNits = maxNits;
            this.maxAverageNits = maxAverageNits;
            this.minNits = minNits;
        }

        /**
         * Phones report 0, negative or absurd values when they do not know; those become 0
         * (unknown) because the host drops a message whose peak is outside 1-10 000 nits,
         * whose average is above the peak, or whose black level is 1 nit or more.
         */
        public static Caps of(boolean hdr, float maxNits, float maxAverageNits, float minNits) {
            float max = known(maxNits);
            if (max < 1 || max > 10000) max = 0;
            float average = known(maxAverageNits);
            if (average > 10000) average = 0;
            if (max != 0 && average > max) average = max;
            float min = known(minNits);
            if (min >= 0.9999f) min = 0;
            return new Caps(hdr, max, average, min);
        }

        private static float known(float nits) {
            return Float.isNaN(nits) || Float.isInfinite(nits) || nits <= 0 ? 0 : nits;
        }

        // The wire carries hundredths of a nit (ten-thousandths for black), rounded like the
        // shared crate does; values that encode the same are the same.
        private static long wire(float nits, float perNit) {
            return (long) (nits * perNit + 0.5f);
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Caps)) return false;
            Caps caps = (Caps) other;
            return hdr == caps.hdr &&
                    wire(maxNits, 100) == wire(caps.maxNits, 100) &&
                    wire(maxAverageNits, 100) == wire(caps.maxAverageNits, 100) &&
                    wire(minNits, 10000) == wire(caps.minNits, 10000);
        }

        @Override
        public int hashCode() {
            return Objects.hash(hdr, wire(maxNits, 100), wire(maxAverageNits, 100), wire(minNits, 10000));
        }

        @Override
        public String toString() {
            return "HDR " + (hdr ? "on" : "off") +
                    ", peak " + nits(maxNits, "%.0f") +
                    ", frame average " + nits(maxAverageNits, "%.0f") +
                    ", black " + nits(minNits, "%.4f");
        }

        private static String nits(float value, String format) {
            return value == 0 ? "unknown" : String.format(Locale.US, format, value) + " nits";
        }
    }

    private final Sender sender;
    private Caps lastSent;

    public DisplayCapsReporter(Sender sender) {
        this.sender = sender;
    }

    /** Sends the values unless the host already has them; returns what was sent, or null. */
    public synchronized Caps update(boolean hdr, float maxNits, float maxAverageNits, float minNits) {
        Caps caps = Caps.of(hdr, maxNits, maxAverageNits, minNits);
        if (caps.equals(lastSent) || !sender.send(caps.hdr, caps.maxNits, caps.maxAverageNits, caps.minNits)) {
            return null;
        }
        lastSent = caps;
        return caps;
    }
}
