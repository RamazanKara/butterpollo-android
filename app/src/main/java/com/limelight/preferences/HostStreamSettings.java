package com.limelight.preferences;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.text.InputType;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.limelight.R;
import com.limelight.binding.video.UpscalingPolicy;
import com.google.android.material.slider.Slider;
import com.limelight.utils.UiHelper;

public final class HostStreamSettings extends AppCompatActivity {
    private PreferenceConfiguration draft;
    private boolean useGlobal;
    private String hostUuid;

    public static void show(Activity activity, String hostUuid, String hostName) {
        activity.startActivity(new Intent(activity, HostStreamSettings.class)
                .putExtra("host_uuid", hostUuid).putExtra("host_name", hostName));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        UiHelper.setLocale(this);
        hostUuid = getIntent().getStringExtra("host_uuid");
        if (hostUuid == null) {
            finish();
            return;
        }
        draft = PreferenceConfiguration.readPreferences(this, hostUuid);
        useGlobal = HostStreamProfile.load(this, hostUuid) == null;
        if (state != null) {
            HostStreamProfile.deserialize(state.getString("draft")).applyTo(draft);
            useGlobal = state.getBoolean("global");
        }
        setContentView(R.layout.activity_host_stream_settings);
        MaterialToolbar toolbar = findViewById(R.id.host_profile_toolbar);
        toolbar.setSubtitle(getIntent().getStringExtra("host_name"));
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.inflateMenu(R.menu.host_profile);
        toolbar.setOnMenuItemClickListener(item -> {
            new MaterialAlertDialogBuilder(this).setTitle(R.string.host_profile_menu)
                    .setMessage(R.string.host_profile_explanation).setPositiveButton(android.R.string.ok, null).show();
            return true;
        });
        findViewById(R.id.host_profile_save).setOnClickListener(v -> {
            if (useGlobal) {
                HostStreamProfile.reset(this, hostUuid);
            } else {
                profile().save(this, hostUuid);
            }
            Toast.makeText(this, useGlobal ? R.string.host_profile_reset_done : R.string.host_profile_saved,
                    Toast.LENGTH_SHORT).show();
            finish();
        });
        if (state == null) {
            getFragmentManager().beginTransaction().replace(R.id.host_profile_settings, new SettingsFragment()).commit();
        }
        UiHelper.notifyNewRootView(this);
    }

    private HostStreamProfile profile() {
        return new HostStreamProfile(draft.width, draft.height,
                draft.launchRefreshRateX100 == 0 ? draft.fps * 100 : draft.launchRefreshRateX100,
                draft.bitrate, draft.virtualDisplayScale, draft.videoFormat, draft.virtualDisplay,
                draft.enableHdr, draft.fullRange, draft.enableYuv444, draft.vrr,
                draft.upscalingMode, draft.upscalingSharpness);
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("draft", profile().serialize());
        state.putBoolean("global", useGlobal);
    }

    public static class SettingsFragment extends PreferenceFragment {
        private HostStreamSettings activity;
        private PreferenceCategory stream;

        @Override
        public void onActivityCreated(Bundle state) {
            super.onActivityCreated(state);
            activity = (HostStreamSettings) getActivity();
            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(activity);
            setPreferenceScreen(screen);
            MaterialSwitchPreference global = new MaterialSwitchPreference(activity, null);
            global.setTitle(R.string.host_profile_reset);
            global.setLayoutResource(R.layout.settings_preference);
            global.setPersistent(false);
            global.setChecked(activity.useGlobal);
            screen.addPreference(global);
            stream = new PreferenceCategory(activity);
            stream.setTitle(R.string.settings_section_video);
            stream.setLayoutResource(R.layout.settings_category);
            screen.addPreference(stream);
            row("resolution", R.string.title_resolution_list).setOnPreferenceClickListener(pref -> {
                showResolution();
                return true;
            });
            row("refresh", R.string.title_fps_list).setOnPreferenceClickListener(pref -> {
                showNumber("refresh", R.string.host_profile_refresh);
                return true;
            });
            row("bitrate", R.string.title_seekbar_bitrate).setOnPreferenceClickListener(pref -> {
                showNumber("bitrate", R.string.host_profile_bitrate);
                return true;
            });
            row("codec", R.string.title_video_format).setOnPreferenceClickListener(pref -> {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_video_format)
                        .setSingleChoiceItems(R.array.host_profile_codecs, activity.draft.videoFormat.ordinal(), (dialog, which) -> {
                            activity.draft.videoFormat = PreferenceConfiguration.FormatOption.values()[which];
                            updateSummaries();
                            dialog.dismiss();
                        }).setNegativeButton(android.R.string.cancel, null).show();
                return true;
            });
            row("upscaling", R.string.title_upscaling).setOnPreferenceClickListener(pref -> {
                new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_upscaling)
                        .setSingleChoiceItems(R.array.upscaling_names, activity.draft.upscalingMode.ordinal(), (dialog, which) -> {
                            activity.draft.upscalingMode = UpscalingPolicy.Mode.values()[which];
                            updateSummaries();
                            dialog.dismiss();
                        }).setNeutralButton(R.string.help, (dialog, which) ->
                                new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_upscaling)
                                        .setMessage(R.string.summary_upscaling).setPositiveButton(android.R.string.ok, null).show())
                        .setNegativeButton(android.R.string.cancel, null).show();
                return true;
            });
            row("sharpness", R.string.title_upscaling_sharpness).setOnPreferenceClickListener(pref -> {
                showSharpness();
                return true;
            });
            toggle("hdr", R.string.title_enable_hdr, R.string.summary_enable_hdr, activity.draft.enableHdr)
                    .setOnPreferenceChangeListener((pref, value) -> {
                        activity.draft.enableHdr = (Boolean) value;
                        updateSummaries();
                        return true;
                    });
            toggle("vrr", R.string.title_vrr, R.string.summary_vrr, activity.draft.vrr)
                    .setOnPreferenceChangeListener((pref, value) -> { activity.draft.vrr = (Boolean) value; return true; });
            toggle("virtual", R.string.title_virtual_display, R.string.summary_virtual_display, activity.draft.virtualDisplay)
                    .setOnPreferenceChangeListener((pref, value) -> {
                        activity.draft.virtualDisplay = (Boolean) value;
                        updateSummaries();
                        return true;
                    });
            row("scale", R.string.title_virtual_display_scale).setOnPreferenceClickListener(pref -> {
                showNumber("scale", R.string.host_profile_scale);
                return true;
            });
            toggle("yuv444", R.string.title_yuv444, R.string.summary_yuv444, activity.draft.enableYuv444)
                    .setOnPreferenceChangeListener((pref, value) -> { activity.draft.enableYuv444 = (Boolean) value; return true; });
            toggle("full_range", R.string.title_full_range, R.string.summary_full_range, activity.draft.fullRange)
                    .setOnPreferenceChangeListener((pref, value) -> { activity.draft.fullRange = (Boolean) value; return true; });
            global.setOnPreferenceChangeListener((pref, value) -> {
                activity.useGlobal = (Boolean) value;
                stream.setEnabled(!activity.useGlobal);
                updateSummaries();
                return true;
            });
            stream.setEnabled(!activity.useGlobal);
            Preference footer = new Preference(activity);
            footer.setLayoutResource(R.layout.settings_preference);
            footer.setSummary(R.string.host_profile_footer);
            footer.setSelectable(false);
            screen.addPreference(footer);
            updateSummaries();
        }

        private Preference row(String key, int title) {
            Preference preference = new Preference(activity);
            preference.setKey(key);
            preference.setTitle(title);
            preference.setLayoutResource(R.layout.settings_preference);
            preference.setPersistent(false);
            stream.addPreference(preference);
            return preference;
        }

        private MaterialSwitchPreference toggle(String key, int title, int help, boolean checked) {
            MaterialSwitchPreference preference = new MaterialSwitchPreference(activity, null);
            preference.setKey(key);
            preference.setTitle(title);
            preference.setExplanation(getString(help));
            preference.setLayoutResource(R.layout.settings_preference);
            preference.setPersistent(false);
            preference.setChecked(checked);
            stream.addPreference(preference);
            return preference;
        }

        private void updateSummaries() {
            PreferenceConfiguration draft = activity.useGlobal ? PreferenceConfiguration.readPreferences(activity) : activity.draft;
            findPreference("resolution").setSummary(draft.width + " × " + draft.height);
            findPreference("refresh").setSummary(HostStreamProfile.formatRefreshRate(
                    draft.launchRefreshRateX100 == 0 ? draft.fps * 100 : draft.launchRefreshRateX100) + " Hz");
            findPreference("bitrate").setSummary(HostStreamProfile.formatBitrateMbps(draft.bitrate) + " Mbps");
            findPreference("codec").setSummary(getResources().getStringArray(R.array.host_profile_codecs)[draft.videoFormat.ordinal()]);
            String upscaling = getResources().getStringArray(R.array.upscaling_names)[draft.upscalingMode.ordinal()];
            if (draft.upscalingMode != UpscalingPolicy.Mode.OFF) {
                if (draft.enableHdr) upscaling += "\n" + getString(R.string.upscaling_hdr);
                else if (draft.videoFormat == PreferenceConfiguration.FormatOption.FORCE_PYROWAVE) {
                    upscaling += "\n" + getString(R.string.upscaling_pyrowave);
                }
            }
            findPreference("upscaling").setSummary(upscaling);
            findPreference("sharpness").setSummary(getString(R.string.upscaling_sharpness_value, draft.upscalingSharpness));
            findPreference("sharpness").setEnabled(draft.upscalingMode == UpscalingPolicy.Mode.FSR1 ||
                    draft.upscalingMode == UpscalingPolicy.Mode.SGSR1);
            findPreference("scale").setSummary(draft.virtualDisplayScale + "%");
            findPreference("scale").setEnabled(draft.virtualDisplay);
            ((MaterialSwitchPreference) findPreference("hdr")).setChecked(draft.enableHdr);
            ((MaterialSwitchPreference) findPreference("vrr")).setChecked(draft.vrr);
            ((MaterialSwitchPreference) findPreference("virtual")).setChecked(draft.virtualDisplay);
            ((MaterialSwitchPreference) findPreference("yuv444")).setChecked(draft.enableYuv444);
            ((MaterialSwitchPreference) findPreference("full_range")).setChecked(draft.fullRange);
        }

        private LinearLayout form() {
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            int padding = Math.round(24 * getResources().getDisplayMetrics().density);
            form.setPadding(padding, padding / 3, padding, 0);
            return form;
        }

        private EditText field(LinearLayout form, int title, String value, boolean decimal) {
            TextInputLayout container = new TextInputLayout(activity);
            container.setHint(title);
            EditText field = new TextInputEditText(container.getContext());
            field.setId(View.generateViewId());
            field.setContentDescription(getString(title));
            field.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
            field.setSingleLine(true);
            field.setSelectAllOnFocus(true);
            field.setMinHeight(Math.round(56 * getResources().getDisplayMetrics().density));
            field.setText(value);
            container.addView(field);
            form.addView(container);
            return field;
        }

        // Inline under the field (a TextInputLayout error), so it stays visible and screen readers announce it.
        private void showError(EditText field, String error) {
            View parent = (View) field.getParent();
            while (!(parent instanceof TextInputLayout)) parent = (View) parent.getParent();
            ((TextInputLayout) parent).setError(error);
        }

        private void showSharpness() {
            LinearLayout form = form();
            TextView value = new TextView(activity);
            value.setText(getString(R.string.upscaling_sharpness_value, activity.draft.upscalingSharpness));
            value.setTextSize(24);
            form.addView(value);
            Slider slider = new Slider(activity);
            slider.setContentDescription(getString(R.string.title_upscaling_sharpness));
            slider.setValueFrom(0);
            slider.setValueTo(100);
            slider.setStepSize(1);
            slider.setValue(activity.draft.upscalingSharpness);
            slider.addOnChangeListener((view, strength, fromUser) ->
                    value.setText(getString(R.string.upscaling_sharpness_value, Math.round(strength))));
            form.addView(slider);
            new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_upscaling_sharpness).setView(form)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        activity.draft.upscalingSharpness = Math.round(slider.getValue());
                        updateSummaries();
                    }).setNegativeButton(android.R.string.cancel, null).show();
        }

        private void showResolution() {
            LinearLayout form = form();
            EditText width = field(form, R.string.host_profile_width, Integer.toString(activity.draft.width), false);
            EditText height = field(form, R.string.host_profile_height, Integer.toString(activity.draft.height), false);
            AlertDialog dialog = new MaterialAlertDialogBuilder(activity).setTitle(R.string.title_resolution_list)
                    .setView(form).setPositiveButton(android.R.string.ok, null)
                    .setNegativeButton(android.R.string.cancel, null)
                    .setNeutralButton(R.string.host_profile_match_screen, null).create();
            dialog.setOnShowListener(ignored -> {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                    DisplayMetrics metrics = new DisplayMetrics();
                    activity.getWindowManager().getDefaultDisplay().getRealMetrics(metrics);
                    width.setText(Integer.toString(Math.max(metrics.widthPixels, metrics.heightPixels)));
                    height.setText(Integer.toString(Math.min(metrics.widthPixels, metrics.heightPixels)));
                });
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    Integer w = readNumber(width, 64, 16384);
                    Integer h = readNumber(height, 64, 16384);
                    if (w != null && h != null) {
                        activity.draft.width = w;
                        activity.draft.height = h;
                        updateSummaries();
                        dialog.dismiss();
                    }
                });
            });
            UiHelper.showDialog(activity, dialog);
        }

        private void showNumber(String key, int title) {
            LinearLayout form = form();
            String value = findPreference(key).getSummary().toString().split(" ")[0].replace("%", "");
            EditText field = field(form, title, value, !key.equals("scale"));
            MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity).setTitle(title).setView(form)
                    .setPositiveButton(android.R.string.ok, null).setNegativeButton(android.R.string.cancel, null);
            if (key.equals("refresh")) builder.setNeutralButton(R.string.host_profile_match_screen, null);
            AlertDialog dialog = builder.create();
            dialog.setOnShowListener(ignored -> {
                if (key.equals("refresh")) {
                    dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> field.setText(
                            HostStreamProfile.formatRefreshRate(Math.round(100 *
                                    com.limelight.binding.video.DisplayFrameRatePolicy.maxRefreshRate(
                                            activity.getWindowManager().getDefaultDisplay())))));
                }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    showError(field, null);
                    try {
                        if (key.equals("refresh")) {
                            activity.draft.launchRefreshRateX100 = HostStreamProfile.parseRefreshRate(field.getText().toString());
                        } else if (key.equals("bitrate")) {
                            activity.draft.bitrate = HostStreamProfile.parseBitrateMbps(field.getText().toString());
                        } else {
                            Integer scale = readNumber(field, 50, 200);
                            if (scale == null) return;
                            activity.draft.virtualDisplayScale = scale;
                        }
                        updateSummaries();
                        dialog.dismiss();
                    } catch (IllegalArgumentException e) {
                        showError(field, getString(key.equals("refresh") ? R.string.host_profile_invalid_refresh : R.string.host_profile_invalid_bitrate));
                    }
                });
            });
            UiHelper.showDialog(activity, dialog);
        }

        private Integer readNumber(EditText field, int min, int max) {
            showError(field, null);
            try {
                return HostStreamProfile.requireRange(Integer.parseInt(field.getText().toString().trim()), min, max);
            } catch (IllegalArgumentException e) {
                showError(field, getString(R.string.host_profile_invalid_number, min, max));
                return null;
            }
        }

        @Override
        public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle state) {
            View view = super.onCreateView(inflater, container, state);
            ListView list = view.findViewById(android.R.id.list);
            com.limelight.ui.UiNavigation.bindPreferences(list);
            list.setNextFocusDownId(R.id.host_profile_save);
            list.setDivider(null);
            int padding = Math.round(8 * getResources().getDisplayMetrics().density);
            list.setPadding(padding, 0, padding, padding);
            return view;
        }
    }
}
