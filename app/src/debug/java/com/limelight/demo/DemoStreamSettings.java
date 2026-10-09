package com.limelight.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.limelight.preferences.ControllerMappingActivity;
import com.limelight.preferences.StreamSettings;

public final class DemoStreamSettings extends StreamSettings {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new DemoContext(base));
    }

    @Override public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (intent.getComponent() != null && ControllerMappingActivity.class.getName().equals(intent.getComponent().getClassName())) {
            super.startActivityForResult(new Intent(this, DemoControllerMappingActivity.class), requestCode, options);
        }
    }
}
