package com.wuhui.update;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.whinc.util.Log;
import com.whinc.util.updater.AppUpdater;
import com.whinc.util.updater.Version;

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
        mAppUpdater.checkVersion(checkVersionUrl, new AppUpdater.VersionParseXML(), new AppUpdater.CheckVersionListener() {
            @Override
            public void complete(boolean hasNewVersion, Version version) {
                Log.i(TAG, "version:" + version);
                if (hasNewVersion) {
                    mServerVersionCode.setText(String.valueOf(version.getVersionCode()));
                    mServerVersionName.setText(version.getVersionName());
                }
            }
        });
    }
}
