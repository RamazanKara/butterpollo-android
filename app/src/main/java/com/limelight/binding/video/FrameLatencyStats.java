package com.limelight.binding.video;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

class FrameLatencyStats {
    static final int WINDOW_SIZE = 600;
    private static final int MAX_PENDING = 2048;
    private static final int MAX_COMPLETED = 4096;
    private static final long CALLBACK_TIMEOUT_NS = 5000000000L;
    static final String CSV_HEADER = "frame_number,pts_us,receive_ns,decoder_input_ns,decoder_output_ns," +
            "render_ns,release_ns,status,receive_to_input_ms,input_to_output_ms,output_to_render_ms," +
            "receive_to_render_ms,csv_rows_lost,host_processing_ms," +
            "decode_to_present_ms,receive_to_present_ms," +
            "decode_to_release_ms,decode_interval_ms,release_interval_ms\n";

    private final LinkedHashMap<Long, Frame> pending = new LinkedHashMap<>();
    private final HashMap<Integer, Frame> outputs = new HashMap<>();
    private final ArrayDeque<Frame> completed = new ArrayDeque<>();
    private final long[][] samples = new long[5][WINDOW_SIZE];
    private final int[] counts = new int[5];
    private final int[] positions = new int[5];
    private long csvRowsLost;
    private final boolean vrr;
    private long lastOutputNs;
    private long lastReleaseNs;
    private long releaseIntervalTotalNs;
    private int releaseIntervalCount;
    private long decodeTimeTotalNs;
    private int decodeTimeSamples;

    FrameLatencyStats() {
        this(false);
    }

    FrameLatencyStats(boolean vrr) {
        this.vrr = vrr;
    }

    private static class Frame {
        int number;
        int outputIndex = -1;
        long ptsUs;
        long receiveNs;
        long inputNs;
        long outputNs;
        long renderNs;
        long releaseNs;
        long hostProcessingNs;
        long previousOutputNs;
        long previousReleaseNs;
        String status;
    }

    synchronized void onDecoderInput(int frameNumber, long ptsUs, long receiveNs, long inputNs, char hostProcessingLatency) {
        if (pending.size() == MAX_PENDING) {
            finish(pending.values().iterator().next(), "tracking_overflow");
        }
        Frame frame = new Frame();
        frame.number = frameNumber;
        frame.ptsUs = ptsUs;
        frame.receiveNs = receiveNs;
        frame.inputNs = inputNs;
        // The unsigned wire value is in 100 us units; zero means unavailable.
        frame.hostProcessingNs = hostProcessingLatency * 100000L;
        pending.put(ptsUs, frame);
        addSample(0, receiveNs, inputNs);
        if (frame.hostProcessingNs != 0) {
            addDuration(4, frame.hostProcessingNs);
        }
    }

    synchronized void onDecoderOutput(int index, long ptsUs, long outputNs) {
        Frame frame = pending.get(ptsUs);
        if (frame != null) {
            frame.outputIndex = index;
            frame.outputNs = outputNs;
            if (vrr) {
                frame.previousOutputNs = lastOutputNs;
                lastOutputNs = outputNs;
            }
            outputs.put(index, frame);
            addSample(1, frame.inputNs, outputNs);
            if (frame.inputNs > 0 && outputNs >= frame.inputNs) {
                decodeTimeTotalNs += outputNs - frame.inputNs;
                decodeTimeSamples++;
            }
        }
    }

    synchronized void onOutputReleased(int index, long releaseNs, boolean render, boolean hasRenderCallback) {
        Frame frame = outputs.remove(index);
        if (frame != null) {
            frame.outputIndex = -1;
            frame.releaseNs = releaseNs;
            if (vrr && render && frame.outputNs > 0 && releaseNs >= frame.outputNs) {
                frame.previousReleaseNs = lastReleaseNs;
                if (lastReleaseNs > 0 && releaseNs > lastReleaseNs) {
                    releaseIntervalTotalNs += releaseNs - lastReleaseNs;
                    releaseIntervalCount++;
                }
                lastReleaseNs = releaseNs;
            }
            if (!render) {
                finish(frame, "dropped");
            } else if (!hasRenderCallback) {
                finish(frame, "render_unavailable");
            }
        }
    }

    synchronized void onFrameRendered(long ptsUs, long renderNs) {
        Frame frame = pending.get(ptsUs);
        if (frame != null) {
            frame.renderNs = renderNs;
            boolean valid = frame.inputNs > 0 && frame.outputNs >= frame.inputNs && renderNs >= frame.outputNs;
            if (valid) {
                addSample(2, frame.outputNs, renderNs);
                addSample(3, frame.receiveNs, renderNs);
            }
            finish(frame, valid ? "rendered" : "invalid_render_time");
        }
    }

    synchronized void discard(long ptsUs, String reason) {
        Frame frame = pending.get(ptsUs);
        if (frame != null) {
            finish(frame, reason);
        }
    }

    synchronized void discardPending(String reason) {
        for (Frame frame : new ArrayList<>(pending.values())) {
            finish(frame, reason);
        }
        lastOutputNs = lastReleaseNs = releaseIntervalTotalNs = 0;
        releaseIntervalCount = 0;
        decodeTimeTotalNs = 0;
        decodeTimeSamples = 0;
    }

    synchronized float takeDecodeTimeMs() {
        float result = decodeTimeSamples == 0 ? -1 : decodeTimeTotalNs / (decodeTimeSamples * 1000000.0f);
        decodeTimeTotalNs = 0;
        decodeTimeSamples = 0;
        return result;
    }

    synchronized float takeReleaseFrameRate() {
        float frameRate = releaseIntervalCount == 0 ? 0 :
                (float) (releaseIntervalCount * 1000000000.0 / releaseIntervalTotalNs);
        releaseIntervalTotalNs = 0;
        releaseIntervalCount = 0;
        return frameRate;
    }

    synchronized void expire(long nowNs) {
        for (Frame frame : new ArrayList<>(pending.values())) {
            if (nowNs - frame.inputNs >= CALLBACK_TIMEOUT_NS) {
                finish(frame, frame.releaseNs != 0 ? "render_unobserved" : "output_unobserved");
            }
        }
    }

    private void finish(Frame frame, String status) {
        pending.remove(frame.ptsUs);
        if (frame.outputIndex != -1) {
            outputs.remove(frame.outputIndex);
        }
        frame.status = status;
        if (completed.size() == MAX_COMPLETED) {
            completed.removeFirst();
            csvRowsLost++;
        }
        completed.addLast(frame);
    }

    private void addSample(int stage, long startNs, long endNs) {
        if (startNs == 0 || endNs < startNs) {
            return;
        }
        addDuration(stage, endNs - startNs);
    }

    private void addDuration(int stage, long durationNs) {
        samples[stage][positions[stage]] = durationNs;
        positions[stage] = (positions[stage] + 1) % WINDOW_SIZE;
        counts[stage] = Math.min(counts[stage] + 1, WINDOW_SIZE);
    }

    double[][] summarize() {
        long[][] snapshot = new long[samples.length][];
        synchronized (this) {
            for (int stage = 0; stage < snapshot.length; stage++) {
                snapshot[stage] = Arrays.copyOf(samples[stage], counts[stage]);
            }
        }
        double[][] summary = new double[samples.length][4];
        for (int stage = 0; stage < snapshot.length; stage++) {
            long[] values = snapshot[stage];
            summary[stage][0] = values.length;
            if (values.length == 0) {
                continue;
            }
            Arrays.sort(values);
            double sum = 0;
            for (long value : values) {
                sum += value;
            }
            summary[stage][1] = sum / values.length / 1000000;
            summary[stage][2] = values[(int) Math.ceil(values.length * 0.95) - 1] / 1000000.0;
            summary[stage][3] = values[(int) Math.ceil(values.length * 0.99) - 1] / 1000000.0;
        }
        return summary;
    }

    synchronized long getCsvRowsLost() {
        return csvRowsLost;
    }

    void writeCsv(Writer writer) throws IOException {
        List<Frame> frames;
        long lost;
        synchronized (this) {
            frames = new ArrayList<>(completed);
            completed.clear();
            lost = csvRowsLost;
        }
        // Disk I/O and formatting must never hold the lock used by the decoder threads.
        for (Frame frame : frames) {
            String decodeToPresent = "rendered".equals(frame.status) ? duration(frame.outputNs, frame.renderNs) : "";
            String receiveToPresent = "rendered".equals(frame.status) ? duration(frame.receiveNs, frame.renderNs) : "";
            writer.write(frame.number + "," + frame.ptsUs + "," + timestamp(frame.receiveNs) + "," +
                    timestamp(frame.inputNs) + "," + timestamp(frame.outputNs) + "," + timestamp(frame.renderNs) + "," +
                    timestamp(frame.releaseNs) + "," + frame.status + "," +
                    duration(frame.receiveNs, frame.inputNs) + "," + duration(frame.inputNs, frame.outputNs) + "," +
                    decodeToPresent + "," + receiveToPresent + "," + lost + "," +
                    (frame.hostProcessingNs == 0 ? "" : String.format(Locale.ROOT, "%.6f", frame.hostProcessingNs / 1000000.0)) + "," +
                    decodeToPresent + "," + receiveToPresent + "," +
                    (vrr ? duration(frame.outputNs, frame.releaseNs) : "") + "," +
                    duration(frame.previousOutputNs, frame.outputNs) + "," +
                    duration(frame.previousReleaseNs, frame.releaseNs) + "\n");
        }
    }

    private static String timestamp(long timeNs) {
        return timeNs == 0 ? "" : Long.toString(timeNs);
    }

    private static String duration(long startNs, long endNs) {
        return startNs == 0 || endNs == 0 || endNs < startNs ? "" :
                String.format(Locale.ROOT, "%.6f", (endNs - startNs) / 1000000.0);
    }
}
