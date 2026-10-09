package com.limelight.ui;


import android.app.Activity;
import android.app.Fragment;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.BaseAdapter;

import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;

public class AdapterFragment extends Fragment {
    private AdapterFragmentCallbacks callbacks;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        callbacks = (AdapterFragmentCallbacks) activity;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(callbacks.getAdapterFragmentLayoutId(), container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        GridView grid = getView().findViewById(R.id.fragmentView);
        callbacks.receiveAbsListView(grid);
        UiNavigation.bindGrid(grid);
        grid.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            float density = getResources().getDisplayMetrics().density;
            float fontScale = Math.max(1, getResources().getConfiguration().fontScale);
            boolean tv = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK)
                    == Configuration.UI_MODE_TYPE_TELEVISION;
            boolean computers = callbacks.getAdapterFragmentLayoutId() == R.layout.pc_grid_view;
            int columns = AdaptiveLayout.gridColumns(
                    Math.max(0, grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight()) / density / fontScale,
                    grid.getHeight() / density, computers,
                    PreferenceConfiguration.readPreferences(getActivity()).smallIconMode, tv);
            if (grid.getNumColumns() != columns) grid.setNumColumns(columns);
            if ((bottom - top < 400 * density) != (oldBottom - oldTop < 400 * density) &&
                    grid.getAdapter() instanceof BaseAdapter) {
                ((BaseAdapter) grid.getAdapter()).notifyDataSetChanged();
            }
        });
    }
}
