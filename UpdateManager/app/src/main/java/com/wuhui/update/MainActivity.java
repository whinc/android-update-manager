package com.wuhui.update;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
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
    private ProgressBar mDownloadProgress;
    private TextView mDownloadProgressTxtView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;
        mVersionNumtView = (TextView)findViewById(R.id.version_num_textview);
        mCheckUpdateBtn = (Button)findViewById(R.id.check_update_btn);
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
                String savePath = Environment.getExternalStorageDirectory() + "/whinc/download/whinc.apk";
                String updateUrl = "http://192.168.1.168:8000/update.xml";
                Updater.with(mContext)
                        .downloadListener(mListener)
                        .update(updateUrl)
                        .save(savePath)
                        .create()
                        .checkUpdate();
            }
        });
    }

    private Updater.DownloadListener mListener = new Updater.DownloadListener() {

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
}

