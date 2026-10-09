package com.limelight.preferences;

import android.content.Context;
import android.os.Bundle;
import android.preference.ListPreference;
import android.util.AttributeSet;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.limelight.R;
import com.limelight.utils.UiHelper;

public class MaterialListPreference extends ListPreference {
    public MaterialListPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setDialogMessage(getSummary());
    }

    @Override
    protected void showDialog(Bundle state) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(getContext())
                .setTitle(getTitle())
                .setSingleChoiceItems(getEntries(), findIndexOfValue(getValue()), (dialog, which) -> {
                    String value = getEntryValues()[which].toString();
                    if (callChangeListener(value)) {
                        setValue(value);
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (getDialogMessage() != null) {
            builder.setNeutralButton(R.string.help, (dialog, which) ->
                    UiHelper.showDialog(getContext(), new MaterialAlertDialogBuilder(getContext()).setTitle(getTitle())
                            .setMessage(getDialogMessage()).setPositiveButton(android.R.string.ok, null).create()));
        }
        UiHelper.showDialog(getContext(), builder.create());
    }
}
