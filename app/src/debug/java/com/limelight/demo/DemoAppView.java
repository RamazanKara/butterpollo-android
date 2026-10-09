package com.limelight.demo;

import android.content.Context;
import android.content.Intent;
import android.widget.AbsListView;

import com.limelight.AppView;

public final class DemoAppView extends AppView {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new DemoContext(base));
    }

    @Override public void receiveAbsListView(AbsListView list) {
        super.receiveAbsListView(list);
        list.setOnItemClickListener((parent, view, position, id) -> startActivity(
                new Intent(this, DemoGameActivity.class).putExtra("state", "compact")));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            list.performItemClick(view, position, id);
            return true;
        });
    }
}
