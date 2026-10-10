package com.limelight.preferences;

import android.app.Activity;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;

/** The "Display on the PC" picker for one PC; the choice is saved at once and used from the next stream. */
public final class HostDisplaySettings {
    private HostDisplaySettings() {
    }

    public static void show(Activity activity, String hostUuid, String hostName) {
        if (hostUuid == null) {
            return;
        }
        HostDisplayChoice[] choices = HostDisplayChoice.values();
        CharSequence[] labels = new CharSequence[choices.length];
        for (int i = 0; i < choices.length; i++) {
            labels[i] = activity.getString(choices[i].label);
        }
        HostDisplayChoice current = HostDisplayChoice.load(activity, hostUuid);
        new MaterialAlertDialogBuilder(activity)
                .setTitle(activity.getString(R.string.host_display_title, hostName))
                .setSingleChoiceItems(labels, current.ordinal(), (dialog, which) -> {
                    HostDisplayChoice.save(activity, hostUuid, choices[which]);
                    Toast.makeText(activity, R.string.host_display_saved, Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNeutralButton(R.string.help, (dialog, which) ->
                        new MaterialAlertDialogBuilder(activity).setTitle(R.string.host_display_menu)
                                .setMessage(R.string.host_display_explanation)
                                .setPositiveButton(android.R.string.ok, null).show())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
