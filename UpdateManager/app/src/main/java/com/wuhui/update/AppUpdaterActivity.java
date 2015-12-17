package com.wuhui.update;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.ActionBarActivity;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.whinc.util.Log;
import com.whinc.util.updater.AppUpdater;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class AppUpdaterActivity extends ActionBarActivity {

    public static final String TAG = AppUpdaterActivity.class.getSimpleName();
    private static final String CHECK_VERSION_URL =
            "http://admin.qfoxtech.com:9006/distribution/update.do?type=QIAO_QIAO_ANDROID";
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

    AppUpdater.Version mVersion;
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

    @OnClick(R.id.check_version_async_button)
    protected void checkVersionAsync() {
        AppUpdater.getInstance().checkVersionAsync(CHECK_VERSION_URL, new AppUpdater.VersionParseXML(), new AppUpdater.CheckVersionListener() {

            @Override
            public void complete(AppUpdater.Version version, AppUpdater appUpdater) {
                Log.i(TAG, "version:" + version);
                if (version != null) {
                    if (BuildConfig.VERSION_CODE < version.getVersionCode()) {
                        mServerVersionCode.setText(String.valueOf(version.getVersionCode()));
                        mServerVersionName.setText(version.getVersionName());
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, Log.getStackString(e));
            }
        });
    }

    @OnClick(R.id.check_version_sync_button)
    protected void checkVersionSync() {
        new AsyncTask<Void, Void, AppUpdater.Version>() {
            @Override
            protected AppUpdater.Version doInBackground(Void... params) {
                AppUpdater.Version version = null;
                try {
                    version = AppUpdater.getInstance().checkVersion(
                            CHECK_VERSION_URL, new AppUpdater.VersionParseXML());
                } catch (PackageManager.NameNotFoundException e) {
                    Log.e(TAG, Log.getStackString(e));
                } catch (AppUpdater.VersionParserException e) {
                    Log.e(TAG, Log.getStackString(e));
                } catch (IOException e) {
                    Log.e(TAG, Log.getStackString(e));
                }
                return version;
            }

            @Override
            protected void onPostExecute(AppUpdater.Version version) {
                super.onPostExecute(version);
                if (version != null) {
                    mVersion = version;
                    Log.i(TAG, "version:" + version);
                    if (BuildConfig.VERSION_CODE < version.getVersionCode()) {
                        mServerVersionCode.setText(String.valueOf(version.getVersionCode()));
                        mServerVersionName.setText(version.getVersionName());
                    }
                }
            }
        }.execute();
    }

    @OnClick(R.id.download_app_button)
    protected void download() {
        if (mVersion == null) {
            return;
        }

        String url = mVersion.getDownloadUrl();
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
            }
        };
        try {
            mDownloadId = AppUpdater.getInstance().download(this, url, downloadListener);
            Log.i(TAG, "start download " + mDownloadId);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnClick(R.id.cancel_download_button)
    protected void cancelDownload() {
        if (mDownloadId != -1) {
            if(AppUpdater.getInstance().cancelDownload(this, mDownloadId) == 1) {
                Log.i(TAG, "Has cancel download " + mDownloadId);
            }
        }
    }

    @OnClick(R.id.install_app_button)
    protected void installApk() {
        try {
            AppUpdater.getInstance().installApk(this, mApkFile);
        } catch (FileNotFoundException e) {
            Log.e(TAG, Log.getStackString(e));
        }
    }

    @OnClick(R.id.check_update_btn)
    protected void checkUpdate() {
        String url = CHECK_VERSION_URL;
        AppUpdater.VersionParser parser = new AppUpdater.VersionParseXML();
        AppUpdater.CheckVersionListener checkVersionListener = new AppUpdater.CheckVersionListener() {
            @Override
            public void complete(AppUpdater.Version version, AppUpdater appUpdater) {
                Log.i(TAG, "server version:" + version);
                Log.i(TAG, "local version:" + BuildConfig.VERSION_CODE);
                if (version != null) {
                    if (BuildConfig.VERSION_CODE < version.getVersionCode()) {
                        download(version.getDownloadUrl());
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, Log.getStackString(e));
            }
        };

        AppUpdater.getInstance().checkVersionAsync(url, parser, checkVersionListener);
    }

    private void download(String downloadUrl) {
        Log.i(TAG, "begin downloading");
        try {
            AppUpdater.getInstance().download(this, downloadUrl, new AppUpdater.DownloadListenerAdapter() {
                @Override
                public void onRunning(int totalBytes, int downloadedBytes) {
                    super.onRunning(totalBytes, downloadedBytes);
                    Log.i(TAG, String.format("progress:%d/%d", downloadedBytes, totalBytes));
                }

                @Override
                public void onComplete(String file, AppUpdater appUpdater) {
                    super.onComplete(file, appUpdater);
                    try {
                        appUpdater.installApk(AppUpdaterActivity.this, file);
                    } catch (FileNotFoundException e) {
                        Log.e(TAG, Log.getStackString(e));
                    }
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
