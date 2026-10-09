package com.limelight.utils;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.limelight.BuildConfig;
import com.limelight.LimeLog;
import com.limelight.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ProblemReport {
    public static void show(Activity activity) {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.report_problem)
                .setMessage(R.string.report_problem_details)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.report_problem_share, (dialog, which) -> share(activity))
                .show();
    }

    private static void share(Activity activity) {
        try {
            File directory = new File(activity.getCacheDir(), "reports");
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("Cannot create report directory");
            }
            File report = new File(directory, "butterpollo-problem.txt");
            String text = "Butterpollo " + BuildConfig.VERSION_NAME + " / Android API " + Build.VERSION.SDK_INT
                    + "\nRedacted event log for this app process, oldest first (up to 200 entries)."
                    + "\nAddresses, device/host names, PINs, credentials and free-text diagnostic details are omitted.\n\n"
                    + LimeLog.getRedactedLog() + "\n";
            try (FileOutputStream output = new FileOutputStream(report)) {
                output.write(text.getBytes(StandardCharsets.UTF_8));
            }
            Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".reports", report);
            Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, activity.getString(R.string.report_problem))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setClipData(ClipData.newRawUri("Problem report", uri));
            activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.report_problem_share)));
        } catch (IOException | ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.report_problem_failed, Toast.LENGTH_LONG).show();
        }
    }
}
