package com.limelight.preferences;

import android.app.Activity;
import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;
import com.limelight.utils.UiHelper;
import com.limelight.binding.video.DisplayFrameRatePolicy;

public class PresetPreference extends Preference {
    public PresetPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayoutResource(R.layout.settings_presets);
        setSelectable(false);
    }

    @Override
    protected void onBindView(View view) {
        super.onBindView(view);
        float refresh = DisplayFrameRatePolicy.maxRefreshRate(((Activity) getContext()).getWindowManager().getDefaultDisplay());
        ChipGroup group = view.findViewById(R.id.preset_chips);
        group.removeAllViews();
        String[] names = getContext().getResources().getStringArray(R.array.stream_preset_names);
        String selected = getContext().getString(R.string.settings_custom);
        for (StreamPreset preset : StreamPreset.values()) {
            Chip chip = new Chip(getContext());
            chip.setId(View.generateViewId());
            chip.setText(names[preset.ordinal()]);
            chip.setCheckable(true);
            chip.setEnsureMinTouchTargetSize(true);
            group.addView(chip);
            if (preset.matches(getSharedPreferences(), refresh)) {
                chip.setChecked(true);
                selected = names[preset.ordinal()];
            }
            chip.setOnClickListener(v -> {
                preset.apply(getSharedPreferences(), refresh);
                notifyChanged();
            });
        }
        ((TextView) view.findViewById(R.id.preset_value)).setText(selected);
        view.findViewById(R.id.preset_help).setOnClickListener(v ->
                UiHelper.showDialog(getContext(), new MaterialAlertDialogBuilder(getContext()).setTitle(R.string.stream_presets)
                        .setMessage(R.string.stream_presets_help).setPositiveButton(android.R.string.ok, null).create()));
    }
}
