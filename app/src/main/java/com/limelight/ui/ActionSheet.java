package com.limelight.ui;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.ViewCompat;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.limelight.R;

public final class ActionSheet extends BottomSheetDialog {
    private final LinearLayout content;
    private final int spacing;

    public ActionSheet(Context context, CharSequence title) {
        super(context);
        spacing = Math.round(8 * context.getResources().getDisplayMetrics().density);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(spacing * 2, spacing, spacing * 2, spacing * 2);
        TextView heading = new TextView(context);
        heading.setTextAppearance(context, com.google.android.material.R.style.TextAppearance_Material3_TitleLarge);
        heading.setText(title);
        heading.setPadding(spacing, spacing, spacing, spacing);
        ViewCompat.setAccessibilityHeading(heading, true);
        content.addView(heading);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.addView(content);
        setContentView(scroll);
    }

    public void addSection(int title) {
        TextView heading = new TextView(getContext());
        heading.setText(title);
        heading.setTextAppearance(getContext(), com.google.android.material.R.style.TextAppearance_Material3_LabelLarge);
        heading.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.on_surface_variant));
        heading.setPadding(spacing, spacing * 2, spacing, spacing);
        ViewCompat.setAccessibilityHeading(heading, true);
        content.addView(heading);
    }

    public MaterialButton addAction(int icon, CharSequence label) {
        MaterialButton button = new MaterialButton(getContext(), null, androidx.appcompat.R.attr.borderlessButtonStyle);
        button.setText(label);
        button.setTextColor(androidx.core.content.ContextCompat.getColor(getContext(), R.color.on_surface));
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setMinHeight(spacing * 7);
        button.setMaxLines(3);
        button.setIconResource(icon);
        button.setIconTintResource(R.color.on_surface_variant);
        button.setIconPadding(spacing * 2);
        button.setIconSize(spacing * 3);
        content.addView(button, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return button;
    }

    public void addDivider() {
        com.google.android.material.divider.MaterialDivider divider =
                new com.google.android.material.divider.MaterialDivider(getContext());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(spacing, spacing, spacing, spacing);
        content.addView(divider, params);
    }

    @Override
    protected void onStart() {
        super.onStart();
        getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        getBehavior().setSkipCollapsed(true);
    }
}
