package com.limelight.utils;

import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import com.limelight.R;
import com.limelight.binding.PlatformBinding;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvHTTP;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

public final class PyroWaveBandwidthTest {
    private final Activity activity;
    private final ComputerDetails computer;
    private final String uniqueId;
    private volatile boolean cancelled;
    private volatile NvHTTP http;
    private AlertDialog dialog;

    public PyroWaveBandwidthTest(Activity activity, ComputerDetails computer, String uniqueId) {
        this.activity = activity;
        this.computer = new ComputerDetails(computer);
        this.uniqueId = uniqueId;
    }

    public void show() {
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.bandwidth_probe_title)
                .setMessage(R.string.bandwidth_probe_confirmation)
                .setPositiveButton(R.string.bandwidth_probe_start, (d, which) -> start())
                .setNegativeButton(android.R.string.cancel, (d, which) -> cancel())
                .setOnCancelListener(d -> cancel()).create();
        dialog.show();
    }

    private void start() {
        LinearProgressIndicator progress = new LinearProgressIndicator(activity);
        progress.setMax(100);
        progress.setIndeterminate(true);
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        progress.setPadding(padding, 0, padding, 0);
        dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.bandwidth_probe_title)
                .setMessage(R.string.bandwidth_probe_running)
                .setView(progress)
                .setNegativeButton(android.R.string.cancel, (d, which) -> cancel())
                .setOnCancelListener(d -> cancel()).create();
        dialog.show();
        new Thread(() -> {
            String result;
            try {
                http = new NvHTTP(ServerHelper.getCurrentAddressFromComputer(computer), computer.httpsPort,
                        uniqueId, computer.serverCert, PlatformBinding.getCryptoProvider(activity));
                if (cancelled) {
                    http.cancelPendingRequests();
                    return;
                }
                ComputerDetails current = http.getComputerDetails(true);
                double mbps = http.probePyroWaveBandwidth(current, percent -> activity.runOnUiThread(() -> {
                    if (!cancelled) {
                        progress.setProgressCompat(percent, true);
                    }
                }));
                result = activity.getString(R.string.bandwidth_probe_result, mbps);
                if (current.pyroWaveHostLinkMbps > 0) {
                    result += "\n\n" + activity.getString(R.string.bandwidth_probe_link, current.pyroWaveHostLinkMbps);
                }
                result += "\n\n" + activity.getString(R.string.bandwidth_probe_limits);
            } catch (IOException | XmlPullParserException e) {
                result = activity.getString(R.string.bandwidth_probe_failed, e.getLocalizedMessage());
            }
            final String message = result;
            activity.runOnUiThread(() -> {
                if (cancelled || activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                dialog.dismiss();
                dialog = new MaterialAlertDialogBuilder(activity)
                        .setTitle(R.string.bandwidth_probe_title)
                        .setMessage(message)
                        .setPositiveButton(android.R.string.ok, null).create();
                dialog.show();
            });
        }, "PyroWaveBandwidthTest").start();
    }

    public void cancel() {
        cancelled = true;
        NvHTTP request = http;
        if (request != null) {
            request.cancelPendingRequests();
        }
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
