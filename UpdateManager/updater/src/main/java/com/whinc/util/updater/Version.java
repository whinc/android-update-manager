package com.whinc.util.updater;

public class Version {
    private int mVersionCode;
    private String mVersionName;
    private String mDownloadUrl;
    private String mUpdateLog;

    public int getVersionCode() {
        return mVersionCode;
    }

    public void setVersionCode(int versionCode) {
        mVersionCode = versionCode;
    }

    public String getVersionName() {
        return mVersionName;
    }

    public void setVersionName(String versionName) {
        mVersionName = versionName;
    }

    public String getDownloadUrl() {
        return mDownloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        mDownloadUrl = downloadUrl;
    }

    public String getUpdateLog() {
        return mUpdateLog;
    }

    public void setUpdateLog(String updateLog) {
        mUpdateLog = updateLog;
    }

    @Override
    public String toString() {
        return String.format("[%d, %s, %s, %s]",
                mVersionCode, mVersionName, mDownloadUrl, mUpdateLog);
    }
}
