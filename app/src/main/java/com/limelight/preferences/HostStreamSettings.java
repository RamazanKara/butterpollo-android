package com.limelight.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;

public final class HostStreamSettings {
    public static void show(Activity activity, String hostUuid, String hostName) {
        PreferenceConfiguration config = PreferenceConfiguration.readPreferences(activity, hostUuid);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        form.setPadding(padding, padding / 2, padding, padding / 2);
        TextView explanation = new TextView(activity);
        explanation.setText(R.string.host_profile_explanation);
        form.addView(explanation);

        EditText width = addNumber(activity, form, R.string.host_profile_width, Integer.toString(config.width), false);
        EditText height = addNumber(activity, form, R.string.host_profile_height, Integer.toString(config.height), false);
        EditText refresh = addNumber(activity, form, R.string.host_profile_refresh,
                HostStreamProfile.formatRefreshRate(config.launchRefreshRateX100 == 0 ?
                        config.fps * 100 : config.launchRefreshRateX100), true);
        EditText bitrate = addNumber(activity, form, R.string.host_profile_bitrate, Integer.toString(config.bitrate), false);
        EditText scale = addNumber(activity, form, R.string.host_profile_scale, Integer.toString(config.virtualDisplayScale), false);

        Spinner codec = new Spinner(activity);
        codec.setId(View.generateViewId());
        addLabel(activity, form, R.string.host_profile_codec, codec);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(activity,
                R.array.host_profile_codecs, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        codec.setAdapter(adapter);
        codec.setSelection(config.videoFormat.ordinal());
        form.addView(codec);

        CheckBox virtualDisplay = addCheck(activity, form, R.string.host_profile_virtual_display, config.virtualDisplay);
        CheckBox hdr = addCheck(activity, form, R.string.host_profile_hdr, config.enableHdr);
        CheckBox fullRange = addCheck(activity, form, R.string.host_profile_full_range, config.fullRange);
        CheckBox yuv444 = addCheck(activity, form, R.string.host_profile_yuv444, config.enableYuv444);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.host_profile_title, hostName))
                .setView(scroll)
                .setPositiveButton(R.string.host_profile_save, null)
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.host_profile_reset, (d, which) -> {
                    HostStreamProfile.reset(activity, hostUuid);
                    Toast.makeText(activity, R.string.host_profile_reset_done, Toast.LENGTH_SHORT).show();
                }).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer selectedWidth = readNumber(activity, width, 64, 16384);
            Integer selectedHeight = readNumber(activity, height, 64, 16384);
            Integer selectedBitrate = readNumber(activity, bitrate, 500, 1000000);
            Integer selectedScale = readNumber(activity, scale, 50, 200);
            Integer selectedRefresh = null;
            try {
                selectedRefresh = HostStreamProfile.parseRefreshRate(refresh.getText().toString());
                refresh.setError(null);
            } catch (IllegalArgumentException e) {
                refresh.setError(activity.getString(R.string.host_profile_invalid_refresh));
            }
            if (selectedWidth == null || selectedHeight == null || selectedBitrate == null ||
                    selectedScale == null || selectedRefresh == null) {
                return;
            }
            new HostStreamProfile(selectedWidth, selectedHeight, selectedRefresh, selectedBitrate, selectedScale,
                    PreferenceConfiguration.FormatOption.values()[codec.getSelectedItemPosition()],
                    virtualDisplay.isChecked(), hdr.isChecked(), fullRange.isChecked(), yuv444.isChecked())
                    .save(activity, hostUuid);
            dialog.dismiss();
            Toast.makeText(activity, R.string.host_profile_saved, Toast.LENGTH_SHORT).show();
        }));
        dialog.show();
    }

    private static void addLabel(Activity activity, LinearLayout form, int title, View field) {
        TextView label = new TextView(activity);
        label.setText(title);
        label.setLabelFor(field.getId());
        form.addView(label);
    }

    private static EditText addNumber(Activity activity, LinearLayout form, int title, String value, boolean decimal) {
        EditText field = new EditText(activity);
        field.setId(View.generateViewId());
        field.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setText(value);
        addLabel(activity, form, title, field);
        form.addView(field);
        return field;
    }

    private static CheckBox addCheck(Activity activity, LinearLayout form, int title, boolean checked) {
        CheckBox check = new CheckBox(activity);
        check.setText(title);
        check.setChecked(checked);
        form.addView(check);
        return check;
    }

    private static Integer readNumber(Activity activity, EditText field, int min, int max) {
        try {
            int value = HostStreamProfile.requireRange(Integer.parseInt(field.getText().toString().trim()), min, max);
            field.setError(null);
            return value;
        } catch (IllegalArgumentException e) {
            field.setError(activity.getString(R.string.host_profile_invalid_number, min, max));
            return null;
        }
    }
}
