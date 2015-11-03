package com.wuhui.update;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.whinc.util.Log;
import com.whinc.util.updater.AppUpdater;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AppUpdaterActivity extends ActionBarActivity {

    public static final String TAG = AppUpdaterActivity.class.getSimpleName();
    @Bind(R.id.local_version_code_textView)
    TextView mLocalVersionCode;
    @Bind(R.id.local_version_name_textView)
    TextView mLocalVersionName;
    @Bind(R.id.server_version_code_textView)
    TextView mServerVersionCode;
    @Bind(R.id.server_version_name_textView)
    TextView mServerVersionName;
    @Bind(R.id.download_progress_progressbar)
    ProgressBar mProgressBar;
    @Bind(R.id.download_progress_textview)
    TextView mProgress;

    AppUpdater mAppUpdater = AppUpdater.newInstance(this);
    private String mApkFile;
    private long mDownloadId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_updater);
        ButterKnife.bind(this);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            mLocalVersionCode.setText(String.valueOf(packageInfo.versionCode));
            mLocalVersionName.setText(packageInfo.versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    @OnClick(R.id.check_version_button)
    protected void checkVersion() {
        String checkVersionUrl = "http://192.168.1.182:1234/update.xml";
        mAppUpdater.checkVersionAsync(checkVersionUrl, new AppUpdater.VersionParseXML(), new AppUpdater.CheckVersionListener() {
            @Override
            public void complete(boolean hasNewVersion, AppUpdater.Version version, AppUpdater appUpdater) {
                Log.i(TAG, "version:" + version);
                if (hasNewVersion) {
                    mServerVersionCode.setText(String.valueOf(version.getVersionCode()));
                    mServerVersionName.setText(version.getVersionName());
                }
            }
        });
    }

    @OnClick(R.id.download_app_button)
    protected void download() {
        AppUpdater.Version version = mAppUpdater.getVersion();
        if (version == null) {
            return;
        }

        String url = version.getDownloadUrl();
        String dateStr = new SimpleDateFormat("MMddhhmmss").format(new Date());
        final String path = Environment.getExternalStorageDirectory() + File.separator + dateStr + ".apk";
        AppUpdater.DownloadListenerAdapter downloadListener = new AppUpdater.DownloadListenerAdapter() {
            @Override
            public void onFailed(int reason, String reasonText) {
                super.onFailed(reason, reasonText);
                Log.i(TAG, "onFailed:" + reasonText);
            }

            @Override
            public void onPaused(int reason, String reasonText) {
                super.onPaused(reason, reasonText);
                Log.i(TAG, "onPuased:" + reasonText);
            }

            @Override
            public void onPending() {
                super.onPending();
                Log.i(TAG, "onPending");
            }

            @Override
            public void onComplete(String file, AppUpdater appUpdater) {
                super.onComplete(file, appUpdater);
                Log.i(TAG, "onComplete filepath:" + file);
                mApkFile = file;
            }

            @Override
            public void onRunning(int totalBytes, int downloadedBytes) {
                super.onRunning(totalBytes, downloadedBytes);
                mProgressBar.setMax(totalBytes);
                mProgressBar.setProgress(downloadedBytes);
                mProgress.setText(String.format("%.0f%%", downloadedBytes * 100.0f / totalBytes));
                Log.i(TAG, "onRunning:" + downloadedBytes + "/" + totalBytes);
            }

            @Override
            public void onSuccessful() {
                super.onSuccessful();
                mProgressBar.setProgress(mProgressBar.getMax());
                mProgress.setText("100%");
//                        Log.i(TAG, "onSuccessful");
            }
        };
        try {
            mDownloadId = mAppUpdater.download(url, downloadListener);
            Log.i(TAG, "start download " + mDownloadId);
        } catch (IOException e) {
            e.printStackTrace();
        }
//        mAppUpdater.download(url, null, downloadListener,"title", "description",  true, DownloadManager.Request.VISIBILITY_VISIBLE);
    }

    @OnClick(R.id.cancel_download_button)
    protected void cancelDownload() {
        if (mDownloadId != -1) {
            if(mAppUpdater.cancelDownload(mDownloadId) == 1) {
                Log.i(TAG, "Has cancel download " + mDownloadId);
            }
        }
    }

    @OnClick(R.id.install_app_button)
    protected void installApk() {
        mAppUpdater.installApk(mApkFile);
    }

    @OnClick(R.id.check_update_btn)
    protected void checkUpdate() {
        String checkVersionUrl = "http://192.168.1.182:1234/update.xml";
        AppUpdater updater = AppUpdater.newInstance(this);
        updater.checkVersionAsync(checkVersionUrl, new AppUpdater.VersionParseXML(), new AppUpdater.CheckVersionListener() {
            @Override
            public void complete(boolean hasNewVersion, AppUpdater.Version version, AppUpdater appUpdater) {
                if (hasNewVersion) {
                    Log.i(TAG, "version info:" + version);
                    try {
                        appUpdater.download(version.getDownloadUrl(), new AppUpdater.DownloadListenerAdapter() {
                            @Override
                            public void onRunning(int totalBytes, int downloadedBytes) {
                                super.onRunning(totalBytes, downloadedBytes);
                                Log.i(TAG, String.format("progress:%d/%d", downloadedBytes, totalBytes));
                            }

                            @Override
                            public void onComplete(String file, AppUpdater appUpdater) {
                                super.onComplete(file, appUpdater);
                                appUpdater.installApk(file);
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }
}
