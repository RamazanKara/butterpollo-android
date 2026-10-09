package com.limelight.demo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.AbsListView;
import android.widget.Toast;

import com.limelight.AppView;
import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.preferences.StreamSettings;
import com.limelight.ui.ActionSheet;

public final class DemoPcView extends PcView {
    @Override protected void attachBaseContext(Context base) {
        super.attachBaseContext(new DemoContext(base));
    }

    @Override public void startActivityForResult(Intent intent, int requestCode, Bundle options) {
        if (intent.getComponent() != null && StreamSettings.class.getName().equals(intent.getComponent().getClassName())) {
            super.startActivityForResult(new Intent(this, DemoStreamSettings.class), requestCode, options);
        } else {
            Toast.makeText(this, R.string.demo_offline, Toast.LENGTH_SHORT).show();
        }
    }

    @Override public void receiveAbsListView(AbsListView list) {
        super.receiveAbsListView(list);
        list.setOnItemClickListener((parent, view, position, id) -> open((ComputerObject) parent.getItemAtPosition(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            open((ComputerObject) parent.getItemAtPosition(position));
            return true;
        });
    }

    private void open(ComputerObject computer) {
        if (computer.details.uuid.equals(DemoFixtures.PC_UUID)) {
            super.startActivityForResult(new Intent(this, DemoAppView.class)
                    .putExtra(AppView.UUID_EXTRA, computer.details.uuid)
                    .putExtra(AppView.NAME_EXTRA, computer.details.name), -1, null);
            return;
        }
        ActionSheet sheet = new ActionSheet(this, computer.details.name);
        int label = computer.details.state == ComputerDetails.State.OFFLINE ?
                R.string.pcview_menu_send_wol : R.string.pcview_menu_pair_pc;
        sheet.addAction(R.drawable.ic_power, getString(label)).setOnClickListener(view -> {
            sheet.dismiss();
            Toast.makeText(this, R.string.demo_offline, Toast.LENGTH_SHORT).show();
        });
        sheet.show();
    }
}
