package com.limelight.preferences;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limelight.R;
import com.limelight.binding.input.ControllerButtonMap;
import com.limelight.utils.UiHelper;

import java.util.Map;

/** Lets the user choose what each physical controller button sends to the host. */
public class ControllerMappingActivity extends Activity {
    private TextView deviceView;
    private LinearLayout mappingList;
    private Button resetButton;
    private String deviceKey;
    private String deviceName;
    private ControllerButtonMap map;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        UiHelper.setLocale(this);
        setTitle(R.string.controller_mapping_title);

        float density = getResources().getDisplayMetrics().density;
        int padding = Math.round(16 * density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);

        TextView heading = new TextView(this);
        heading.setText(R.string.controller_mapping_title);
        heading.setTextSize(22);
        heading.setPadding(0, 0, 0, padding / 2);
        root.addView(heading);

        TextView intro = new TextView(this);
        intro.setText(R.string.controller_mapping_intro);
        intro.setTextSize(16);
        root.addView(intro);

        deviceView = new TextView(this);
        deviceView.setTextSize(18);
        deviceView.setPadding(0, padding, 0, padding / 2);
        root.addView(deviceView);

        mappingList = new LinearLayout(this);
        mappingList.setOrientation(LinearLayout.VERTICAL);
        root.addView(mappingList);

        resetButton = new Button(this);
        resetButton.setText(R.string.controller_mapping_reset);
        resetButton.setOnClickListener(v -> {
            map = new ControllerButtonMap();
            map.save(this, deviceKey);
            refresh();
        });
        root.addView(resetButton);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        UiHelper.notifyNewRootView(this);

        selectSingleConnectedController();
        refresh();
    }

    private void selectSingleConnectedController() {
        InputDevice found = null;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null || device.isVirtual() ||
                    (device.getSources() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
                continue;
            }
            if (found != null) {
                return; // Several controllers: wait for a button press to pick one
            }
            found = device;
        }
        if (found != null) {
            select(found.getVendorId(), found.getProductId(), found.getName());
        }
    }

    private void select(int vendorId, int productId, String name) {
        deviceName = name == null || name.trim().isEmpty() ? getString(R.string.controller_mapping_unnamed) : name.trim();
        deviceKey = ControllerButtonMap.deviceKey(vendorId, productId, name);
        map = ControllerButtonMap.load(this, deviceKey);
    }

    private void refresh() {
        mappingList.removeAllViews();
        if (deviceKey == null) {
            deviceView.setText(R.string.controller_mapping_waiting);
            resetButton.setVisibility(View.GONE);
            return;
        }
        deviceView.setText(deviceName);
        resetButton.setVisibility(map.isEmpty() ? View.GONE : View.VISIBLE);
        if (map.isEmpty()) {
            mappingList.addView(row(getString(R.string.controller_mapping_none), false));
            return;
        }
        for (Map.Entry<Integer, Integer> entry : map.entries().entrySet()) {
            TextView row = row(getString(R.string.controller_mapping_row,
                    buttonName(entry.getKey()), targetName(entry.getValue())), true);
            int source = entry.getKey();
            row.setOnClickListener(v -> chooseTarget(source));
            mappingList.addView(row);
        }
    }

    private TextView row(String text, boolean clickable) {
        TextView row = new TextView(this);
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        row.setPadding(0, padding, 0, padding);
        row.setTextSize(16);
        row.setText(text);
        if (clickable) {
            row.setBackgroundResource(android.R.drawable.list_selector_background);
            row.setFocusable(true);
        }
        return row;
    }

    private static boolean isControllerEvent(KeyEvent event) {
        return (event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (!isControllerEvent(event) || !ControllerButtonMap.isRemappableSource(event.getKeyCode())) {
            return super.dispatchKeyEvent(event);
        }
        // Act on release so the press cannot leak into the dialog that opens next
        if (event.getAction() == KeyEvent.ACTION_UP) {
            InputDevice device = event.getDevice();
            if (device != null) {
                select(device.getVendorId(), device.getProductId(), device.getName());
            }
            else {
                select(0, 0, null);
            }
            refresh();
            chooseTarget(event.getKeyCode());
        }
        return true;
    }

    private void chooseTarget(int source) {
        int[] targets = ControllerButtonMap.TARGETS;
        CharSequence[] labels = new CharSequence[targets.length + 1];
        labels[0] = getString(R.string.controller_mapping_default, buttonName(source));
        for (int i = 0; i < targets.length; i++) {
            labels[i + 1] = targetName(targets[i]);
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.controller_mapping_choose, buttonName(source)))
                .setItems(labels, (dialog, which) -> {
                    if (which == 0) {
                        map.remove(source);
                    }
                    else {
                        map.put(source, targets[which - 1]);
                    }
                    map.save(this, deviceKey);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String targetName(int keyCode) {
        return keyCode == ControllerButtonMap.DISABLED ?
                getString(R.string.controller_mapping_disabled) : buttonName(keyCode);
    }

    private String buttonName(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_A: return "A";
            case KeyEvent.KEYCODE_BUTTON_B: return "B";
            case KeyEvent.KEYCODE_BUTTON_C: return "C";
            case KeyEvent.KEYCODE_BUTTON_X: return "X";
            case KeyEvent.KEYCODE_BUTTON_Y: return "Y";
            case KeyEvent.KEYCODE_BUTTON_Z: return "Z";
            case KeyEvent.KEYCODE_BUTTON_L1: return "LB";
            case KeyEvent.KEYCODE_BUTTON_R1: return "RB";
            case KeyEvent.KEYCODE_BUTTON_THUMBL: return getString(R.string.controller_button_left_stick);
            case KeyEvent.KEYCODE_BUTTON_THUMBR: return getString(R.string.controller_button_right_stick);
            case KeyEvent.KEYCODE_BUTTON_START: return getString(R.string.controller_button_start);
            case KeyEvent.KEYCODE_BUTTON_SELECT: return getString(R.string.controller_button_select);
            case KeyEvent.KEYCODE_BUTTON_MODE: return getString(R.string.controller_button_guide);
            default:
                if (keyCode >= KeyEvent.KEYCODE_BUTTON_1 && keyCode <= KeyEvent.KEYCODE_BUTTON_16) {
                    return getString(R.string.controller_button_number, keyCode - KeyEvent.KEYCODE_BUTTON_1 + 1);
                }
                return KeyEvent.keyCodeToString(keyCode);
        }
    }
}
