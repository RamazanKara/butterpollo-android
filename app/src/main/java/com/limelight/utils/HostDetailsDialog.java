package com.limelight.utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;

/** Readable host details, with the raw dump kept behind "Copy debug info" for support reports. */
public final class HostDetailsDialog {
    private final Activity activity;
    private final LinearLayout content;
    private final int padding;

    private HostDetailsDialog(Activity activity) {
        this.activity = activity;
        padding = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 24,
                activity.getResources().getDisplayMetrics()));
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(padding, padding / 3, padding, padding / 3);
    }

    private void section(int title) {
        TextView header = new TextView(activity, null, android.R.attr.listSeparatorTextViewStyle);
        header.setText(title);
        header.setPadding(0, padding / 2, 0, padding / 6);
        content.addView(header);
    }

    private void row(int label, CharSequence value) {
        if (value == null || value.length() == 0) {
            return;
        }
        if (label != 0) {
            TextView name = new TextView(activity);
            name.setText(label);
            name.setTextAppearance(activity, android.R.style.TextAppearance_Material_Caption);
            content.addView(name);
        }
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextAppearance(activity, android.R.style.TextAppearance_Material_Body1);
        text.setTextIsSelectable(true);
        text.setPadding(0, 0, 0, padding / 3);
        content.addView(text);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    public static void show(Activity activity, ComputerDetails details) {
        HostDetailsDialog dialog = new HostDetailsDialog(activity);
        dialog.build(details);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(dialog.content);
        new AlertDialog.Builder(activity)
                .setTitle(details.name)
                .setView(scroll)
                .setPositiveButton(R.string.host_details_close, null)
                .setNeutralButton(R.string.host_details_copy, (d, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText(details.name, details.toString()));
                    Toast.makeText(activity, R.string.host_details_copied, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void build(ComputerDetails details) {
        section(R.string.host_details_status);
        int state = details.state == ComputerDetails.State.ONLINE ? R.string.pcview_menu_header_online :
                details.state == ComputerDetails.State.OFFLINE ? R.string.pcview_menu_header_offline :
                        R.string.pcview_menu_header_unknown;
        row(R.string.host_details_state, activity.getString(state));
        row(R.string.host_details_pairing, activity.getString(details.pairState == PairingManager.PairState.PAIRED ?
                R.string.host_details_paired : R.string.host_details_not_paired));
        if (details.state == ComputerDetails.State.ONLINE) {
            row(R.string.host_details_running, details.runningGameId != 0 ?
                    activity.getString(R.string.host_details_running_app, details.runningGameId) :
                    activity.getString(R.string.host_details_running_none));
        }

        section(R.string.host_details_host);
        String software;
        if (details.rustHostVersion != null) {
            software = activity.getString(R.string.host_details_software_butterpollo, details.rustHostVersion);
        }
        else if (details.nvidiaServer) {
            software = activity.getString(R.string.host_details_software_gamestream);
        }
        else {
            software = activity.getString(R.string.host_details_software_other);
        }
        row(R.string.host_details_software, software);
        row(R.string.host_details_id, details.uuid);

        section(R.string.host_details_network);
        row(R.string.host_details_active_address, text(details.activeAddress));
        row(R.string.host_details_local_address, text(details.localAddress));
        row(R.string.host_details_remote_address, text(details.remoteAddress));
        row(R.string.host_details_ipv6_address, text(details.ipv6Address));
        row(R.string.host_details_manual_address, text(details.manualAddress));
        if (details.httpsPort != 0) {
            row(R.string.host_details_https_port, Integer.toString(details.httpsPort));
        }
        row(R.string.host_details_mac, details.macAddress);

        section(R.string.host_details_permissions);
        if (details.permission == -1) {
            row(0, activity.getString(R.string.stream_permissions_unknown));
        }
        else {
            String[] names = activity.getResources().getStringArray(R.array.host_permission_names);
            StringBuilder allowed = new StringBuilder();
            StringBuilder denied = new StringBuilder();
            for (int i = 0; i < names.length; i++) {
                StringBuilder target = details.hasPermission(ComputerDetails.PERMISSION_DISPLAY_MASKS[i]) ? allowed : denied;
                target.append(target.length() == 0 ? "" : "\n").append(names[i]);
            }
            row(R.string.stream_permission_allowed, allowed);
            row(R.string.stream_permission_denied, denied);
        }
    }
}
