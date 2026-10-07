package com.limelight.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
        boolean hasProfile = HostStreamProfile.load(activity, hostUuid) != null;
        int padding = dp(activity, 24);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(padding, padding / 3, padding, padding / 3);

        TextView status = new TextView(activity);
        status.setText(hasProfile ? R.string.host_profile_status_custom : R.string.host_profile_status_global);
        status.setTextAppearance(activity, android.R.style.TextAppearance_Material_Body2);
        status.setPadding(0, 0, 0, padding / 3);
        form.addView(status);

        // Width and height share one row so the form stays short on phones
        EditText width = numberField(activity, Integer.toString(config.width), false);
        width.setContentDescription(activity.getString(R.string.host_profile_width));
        EditText height = numberField(activity, Integer.toString(config.height), false);
        height.setContentDescription(activity.getString(R.string.host_profile_height));
        addLabel(activity, form, R.string.host_profile_resolution, width);
        LinearLayout resolution = new LinearLayout(activity);
        resolution.setGravity(Gravity.CENTER_VERTICAL);
        resolution.addView(width, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        TextView times = new TextView(activity);
        times.setText("×");
        times.setPadding(padding / 3, 0, padding / 3, 0);
        resolution.addView(times);
        resolution.addView(height, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        form.addView(resolution);

        EditText refresh = addNumber(activity, form, R.string.host_profile_refresh,
                HostStreamProfile.formatRefreshRate(config.launchRefreshRateX100 == 0 ?
                        config.fps * 100 : config.launchRefreshRateX100), true);
        Button matchScreen = new Button(activity, null, android.R.attr.borderlessButtonStyle);
        matchScreen.setText(R.string.host_profile_match_screen);
        matchScreen.setOnClickListener(v -> {
            Display display = activity.getWindowManager().getDefaultDisplay();
            DisplayMetrics metrics = new DisplayMetrics();
            display.getRealMetrics(metrics);
            width.setText(Integer.toString(Math.max(metrics.widthPixels, metrics.heightPixels)));
            height.setText(Integer.toString(Math.min(metrics.widthPixels, metrics.heightPixels)));
            float rate = display.getRefreshRate();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (Display.Mode mode : display.getSupportedModes()) {
                    rate = Math.max(rate, mode.getRefreshRate());
                }
            }
            refresh.setText(HostStreamProfile.formatRefreshRate(Math.round(rate * 100)));
        });
        LinearLayout.LayoutParams matchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        matchParams.gravity = Gravity.END;
        form.addView(matchScreen, matchParams);

        EditText bitrate = addNumber(activity, form, R.string.host_profile_bitrate,
                HostStreamProfile.formatBitrateMbps(config.bitrate), true);

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
        EditText scale = addNumber(activity, form, R.string.host_profile_scale, Integer.toString(config.virtualDisplayScale), false);
        // Render scale only applies to a host virtual display
        scale.setEnabled(config.virtualDisplay);
        virtualDisplay.setOnCheckedChangeListener((button, checked) -> scale.setEnabled(checked));
        CheckBox hdr = addCheck(activity, form, R.string.host_profile_hdr, config.enableHdr);
        CheckBox fullRange = addCheck(activity, form, R.string.host_profile_full_range, config.fullRange);
        CheckBox yuv444 = addCheck(activity, form, R.string.host_profile_yuv444, config.enableYuv444);

        TextView explanation = new TextView(activity);
        explanation.setText(R.string.host_profile_explanation);
        explanation.setTextAppearance(activity, android.R.style.TextAppearance_Material_Caption);
        explanation.setPadding(0, padding / 2, 0, 0);
        form.addView(explanation);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(form);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.host_profile_title, hostName))
                .setView(scroll)
                .setPositiveButton(R.string.host_profile_save, null)
                .setNegativeButton(android.R.string.cancel, null);
        if (hasProfile) {
            builder.setNeutralButton(R.string.host_profile_reset, (d, which) -> {
                HostStreamProfile.reset(activity, hostUuid);
                Toast.makeText(activity, R.string.host_profile_reset_done, Toast.LENGTH_SHORT).show();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Integer selectedWidth = readNumber(activity, width, 64, 16384);
            Integer selectedHeight = readNumber(activity, height, 64, 16384);
            Integer selectedScale = readNumber(activity, scale, 50, 200);
            Integer selectedRefresh = null;
            try {
                selectedRefresh = HostStreamProfile.parseRefreshRate(refresh.getText().toString());
                refresh.setError(null);
            } catch (IllegalArgumentException e) {
                refresh.setError(activity.getString(R.string.host_profile_invalid_refresh));
            }
            Integer selectedBitrate = null;
            try {
                selectedBitrate = HostStreamProfile.parseBitrateMbps(bitrate.getText().toString());
                bitrate.setError(null);
            } catch (IllegalArgumentException e) {
                bitrate.setError(activity.getString(R.string.host_profile_invalid_bitrate));
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

    private static int dp(Activity activity, int value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                activity.getResources().getDisplayMetrics()));
    }

    private static void addLabel(Activity activity, LinearLayout form, int title, View field) {
        TextView label = new TextView(activity);
        label.setText(title);
        label.setLabelFor(field.getId());
        label.setPadding(0, dp(activity, 8), 0, 0);
        form.addView(label);
    }

    private static EditText numberField(Activity activity, String value, boolean decimal) {
        EditText field = new EditText(activity);
        field.setId(View.generateViewId());
        field.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        field.setSingleLine(true);
        field.setSelectAllOnFocus(true);
        field.setText(value);
        return field;
    }

    private static EditText addNumber(Activity activity, LinearLayout form, int title, String value, boolean decimal) {
        EditText field = numberField(activity, value, decimal);
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
