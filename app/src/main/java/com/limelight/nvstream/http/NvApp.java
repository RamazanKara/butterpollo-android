package com.limelight.nvstream.http;

import com.limelight.LimeLog;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class NvApp {
    private String appName = "";
    private int appId;
    private boolean initialized;
    private boolean hdrSupported;
    private String appUuid = "";
    private int hostIndex = Integer.MAX_VALUE;
    private String artVersion = "";
    
    public NvApp() {}
    
    public NvApp(String appName) {
        this.appName = appName;
    }
    
    public NvApp(String appName, int appId, boolean hdrSupported) {
        this.appName = appName;
        this.appId = appId;
        this.hdrSupported = hdrSupported;
        this.initialized = true;
    }
    
    public void setAppName(String appName) {
        this.appName = appName;
    }
    
    public void setAppId(String appId) {
        try {
            this.appId = Integer.parseInt(appId);
            this.initialized = true;
        } catch (NumberFormatException e) {
            LimeLog.warning("Malformed app ID: "+appId);
        }
    }
    
    public void setAppId(int appId) {
        this.appId = appId;
        this.initialized = true;
    }

    public void setHdrSupported(boolean hdrSupported) {
        this.hdrSupported = hdrSupported;
    }

    public void setAppUuid(String appUuid) {
        this.appUuid = appUuid == null ? "" : appUuid.trim();
    }

    public String getAppUuid() {
        return appUuid;
    }

    public void setHostIndex(String index) {
        try {
            int value = Integer.parseInt(index.trim());
            hostIndex = value >= 0 ? value : Integer.MAX_VALUE;
        } catch (NumberFormatException e) {
            hostIndex = Integer.MAX_VALUE;
        }
    }

    public int getHostIndex() {
        return hostIndex;
    }

    public void setArtVersion(String artVersion) {
        this.artVersion = artVersion;
    }

    public String getArtVersion() {
        return artVersion;
    }

    public String getAssetCacheKey() {
        if (appUuid.isEmpty() && artVersion.isEmpty()) {
            return Integer.toString(appId);
        }
        // Host artwork versions are opaque and must not become filesystem paths.
        return appId + "-" + UUID.nameUUIDFromBytes(
                (appUuid + "\0" + artVersion).getBytes(StandardCharsets.UTF_8));
    }

    public boolean matchesRunningApp(int runningId, String runningUuid) {
        if (runningId == 0) {
            return false;
        }
        return !appUuid.isEmpty() && runningUuid != null && !runningUuid.isEmpty() ?
                appUuid.equals(runningUuid) : appId == runningId;
    }

    public int compareForDisplay(NvApp other) {
        int order = Integer.compare(hostIndex, other.hostIndex);
        return order != 0 ? order : appName.compareToIgnoreCase(other.appName);
    }
    
    public String getAppName() {
        return this.appName;
    }
    
    public int getAppId() {
        return this.appId;
    }

    public boolean isHdrSupported() {
        return this.hdrSupported;
    }
    
    public boolean isInitialized() {
        return this.initialized;
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Name: ").append(appName).append("\n");
        str.append("HDR Supported: ").append(hdrSupported ? "Yes" : "Unknown").append("\n");
        str.append("ID: ").append(appId).append("\n");
        return str.toString();
    }
}
