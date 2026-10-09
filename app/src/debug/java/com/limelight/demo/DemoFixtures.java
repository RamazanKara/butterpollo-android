package com.limelight.demo;

import android.content.Context;
import android.preference.PreferenceManager;
import android.view.WindowManager;

import com.limelight.binding.video.DisplayFrameRatePolicy;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.NvApp;
import com.limelight.nvstream.http.PairingManager.PairState;
import com.limelight.utils.CacheHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class DemoFixtures {
    public static final String PC_UUID = "d3e00000-0000-4000-8000-000000000001";
    public static final String[] TITLES = {"Desktop", "Racing Game", "Open World", "Space Sim",
            "Puzzle Night", "Retro Arcade", "Strategy", "Music Studio", "Ocean Explorer", "Skybound"};

    public static List<ComputerDetails> computers() {
        List<ComputerDetails> computers = new ArrayList<>();
        String[] names = {"Living-room PC", "Studio PC", "Laptop"};
        for (int i = 0; i < names.length; i++) {
            ComputerDetails pc = new ComputerDetails();
            pc.uuid = "d3e00000-0000-4000-8000-00000000000" + (i + 1);
            pc.name = names[i];
            pc.state = i == 2 ? ComputerDetails.State.OFFLINE : ComputerDetails.State.ONLINE;
            pc.pairState = i == 1 ? PairState.NOT_PAIRED : PairState.PAIRED;
            pc.macAddress = "02:00:00:00:00:0" + (i + 1);
            pc.runningGameId = i == 0 ? 2 : 0;
            pc.rawAppList = appList();
            computers.add(pc);
        }
        return computers;
    }

    public static String appList() {
        StringBuilder xml = new StringBuilder("<root status_code=\"200\">");
        for (int i = 0; i < TITLES.length; i++) {
            xml.append("<App><AppTitle>").append(TITLES[i]).append("</AppTitle><ID>")
                    .append(i + 1).append("</ID><IsHdrSupported>1</IsHdrSupported></App>");
        }
        return xml.append("</root>").toString();
    }

    public static void prepare(Context context) throws IOException {
        context.getSharedPreferences("FirstRun", 0).edit().putBoolean("pairing_guide_shown", true).apply();
        WindowManager windows = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        int fps = DisplayFrameRatePolicy.streamFrameRate(DisplayFrameRatePolicy.maxRefreshRate(windows.getDefaultDisplay()));
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString("list_resolution", "1920x1080").putString("list_fps", Integer.toString(fps))
                .putInt("seekbar_bitrate_kbps", 40000).putString("video_format", "auto")
                .putString("frame_pacing", "balanced").putString("upscaling_mode", "fsr1")
                .putBoolean("checkbox_vrr", false).putBoolean("checkbox_enable_hdr", false)
                .putBoolean("checkbox_drop_late_frames", false).putBoolean("checkbox_reduce_refresh_rate", false)
                .putBoolean("checkbox_codec_performance", true).putBoolean("checkbox_phone_performance_hints", false)
                .putBoolean("checkbox_small_icon_mode", true)
                .putBoolean("checkbox_enable_pip", true).putBoolean("checkbox_show_onscreen_controls", true)
                .putBoolean("checkbox_enable_perf_overlay", true).apply();
        try (OutputStream output = CacheHelper.openCacheFileForOutput(context.getCacheDir(), "applist", PC_UUID)) {
            output.write(appList().getBytes(StandardCharsets.UTF_8));
        }
        for (int i = 0; i < TITLES.length; i++) {
            NvApp app = new NvApp(TITLES[i], i + 1, true);
            try (InputStream input = context.getAssets().open("demo/art/" + (i + 1) + ".png");
                 OutputStream output = CacheHelper.openCacheFileForOutput(context.getCacheDir(),
                         "boxart", PC_UUID, app.getAssetCacheKey() + ".png")) {
                byte[] buffer = new byte[8192];
                for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
            }
        }
    }
}
