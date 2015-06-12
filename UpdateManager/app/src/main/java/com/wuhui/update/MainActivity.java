package com.wuhui.update;

import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends ActionBarActivity {
    private static final String AUTO_CHECK_UPDATE = "auto_check_update";

    private Context mContext;
    private TextView mVersionNumtView;
    private Button mCheckUpdateBtn;
    private CheckBox mAutoCheckUpdateChkBox;
    private ProgressBar mDownloadProgress;
    private TextView mDownloadProgressTxtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        boolean autoCheckUpdate = getPreferences(MODE_PRIVATE).getBoolean(AUTO_CHECK_UPDATE, true);

        mVersionNumtView = (TextView)findViewById(R.id.version_num_textview);
        mCheckUpdateBtn = (Button)findViewById(R.id.check_update_btn);
        mAutoCheckUpdateChkBox = (CheckBox)findViewById(R.id.auto_check_update_checkbox);
        mDownloadProgress = (ProgressBar)findViewById(R.id.download_progress_progressbar);
        mDownloadProgressTxtView = (TextView)findViewById(R.id.download_progress_textview);

        try {
            PackageInfo packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            mVersionNumtView.setText(packageInfo.versionName);
            Log.d("TAG", "package name:" + getClass().getPackage().getName());
            Log.d("TAG", "version name:" + packageInfo.versionName);
            Log.d("TAG", "version code:" + packageInfo.versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        mCheckUpdateBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                UpdateManager.checkUpdate(MainActivity.this, new UpdateManager.OnCheckVersionResultListener() {
                    @Override
                    public boolean onResult(boolean hasNewVersion, int versionCode, String versionName, String updateLog, String apkDownloadUrl) {
                        if (hasNewVersion) {
                            Log.d("TAG", "version code:" + versionCode);
                            Log.d("TAG", "version name:" + versionName);
                            Log.d("TAG", "update log:" + updateLog);
                            Log.d("TAG", "apk download url:" + apkDownloadUrl);
                        } else {
                            Log.d("TAG", "no new version");
                        }
                        return false;
                    }
                }, mListener);
            }
        });

        mAutoCheckUpdateChkBox.setChecked(autoCheckUpdate);

        if (autoCheckUpdate) {
            UpdateManager.checkUpdate(this, null, mListener);
        }

    }

    private UpdateManager.OnDownloadChangeListener mListener = new UpdateManager.OnDownloadChangeListener() {

        @Override
        public void onChange(int state, int totalSize, int downloadedSize) {
            Log.d("TAG", "state:" + state);
            Log.d("TAG", "progress:" + String.format("%d/%d", downloadedSize, totalSize));
            switch (state) {
                case DownloadManager.STATUS_RUNNING:
                    mDownloadProgress.setMax(totalSize);
                    mDownloadProgress.setProgress(downloadedSize);
                    mDownloadProgressTxtView.setText(String.format("%d%%", downloadedSize*100/totalSize));
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    mDownloadProgress.setMax(100);
                    mDownloadProgress.setProgress(mDownloadProgress.getMax());
                    mDownloadProgressTxtView.setText("100%");
                    break;
            }
        }
    };

    @Override
    protected void onPause() {
        super.onPause();

        // 记住用户选择
        boolean autoCheckUpdate = mAutoCheckUpdateChkBox.isChecked();
        getPreferences(MODE_PRIVATE)
                .edit()
                .putBoolean(AUTO_CHECK_UPDATE, autoCheckUpdate)
                .commit();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}

