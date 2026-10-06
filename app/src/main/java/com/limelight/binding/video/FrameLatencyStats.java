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
            "receive_to_render_ms,csv_rows_lost\n";

    private final LinkedHashMap<Long, Frame> pending = new LinkedHashMap<>();
    private final HashMap<Integer, Frame> outputs = new HashMap<>();
    private final ArrayDeque<Frame> completed = new ArrayDeque<>();
    private final long[][] samples = new long[4][WINDOW_SIZE];
    private final int[] counts = new int[4];
    private final int[] positions = new int[4];
    private long csvRowsLost;

    private static class Frame {
        int number;
        int outputIndex = -1;
        long ptsUs;
        long receiveNs;
        long inputNs;
        long outputNs;
        long renderNs;
        long releaseNs;
        String status;
    }

    synchronized void onDecoderInput(int frameNumber, long ptsUs, long receiveNs, long inputNs) {
        if (pending.size() == MAX_PENDING) {
            finish(pending.values().iterator().next(), "tracking_overflow");
        }
        Frame frame = new Frame();
        frame.number = frameNumber;
        frame.ptsUs = ptsUs;
        frame.receiveNs = receiveNs;
        frame.inputNs = inputNs;
        pending.put(ptsUs, frame);
        addSample(0, receiveNs, inputNs);
    }

    synchronized void onDecoderOutput(int index, long ptsUs, long outputNs) {
        Frame frame = pending.get(ptsUs);
        if (frame != null) {
            frame.outputIndex = index;
            frame.outputNs = outputNs;
            outputs.put(index, frame);
            addSample(1, frame.inputNs, outputNs);
        }
    }

    synchronized void onOutputReleased(int index, long releaseNs, boolean render, boolean hasRenderCallback) {
        Frame frame = outputs.remove(index);
        if (frame != null) {
            frame.outputIndex = -1;
            frame.releaseNs = releaseNs;
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
            boolean valid = frame.outputNs >= frame.inputNs && renderNs >= frame.outputNs;
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
        samples[stage][positions[stage]] = endNs - startNs;
        positions[stage] = (positions[stage] + 1) % WINDOW_SIZE;
        counts[stage] = Math.min(counts[stage] + 1, WINDOW_SIZE);
    }

    double[][] summarize() {
        long[][] snapshot = new long[4][];
        synchronized (this) {
            for (int stage = 0; stage < snapshot.length; stage++) {
                snapshot[stage] = Arrays.copyOf(samples[stage], counts[stage]);
            }
        }
        double[][] summary = new double[4][4];
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
            writer.write(frame.number + "," + frame.ptsUs + "," + timestamp(frame.receiveNs) + "," +
                    timestamp(frame.inputNs) + "," + timestamp(frame.outputNs) + "," + timestamp(frame.renderNs) + "," +
                    timestamp(frame.releaseNs) + "," + frame.status + "," +
                    duration(frame.receiveNs, frame.inputNs) + "," + duration(frame.inputNs, frame.outputNs) + "," +
                    duration(frame.outputNs, frame.renderNs) + "," + duration(frame.receiveNs, frame.renderNs) + "," + lost + "\n");
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
