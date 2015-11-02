## [当前readme已经废弃，请移步到新版的readme](https://github.com/whinc/AndroidUpdateManager/tree/master/UpdateManager)

### AndroidUpdateManager

通过单一的类`Updater`可以方便的实现自动检查更新、下载安装包和自动安装，可以监听下载进度。保存路径可以自由书写，如果路径中某个目录不存在会自动创建，流式API接口易于使用。

![demo运行截图](screenshot1.png)

下面是使用示例，一行代码搞定自动更新：

```
String savePath = Environment.getExternalStorageDirectory() 
                    + "/whinc/download/whinc.apk";
String updateUrl = "http://192.168.1.168:8000/update.xml";
Updater.with(mContext)
        .checkUpdateListener(mCheckUpdateListener)
        .downloadListener(mListener)
        .update(updateUrl)
        .save(savePath)
        .create()
        .checkUpdate();

private Updater.CheckUpdateListener mCheckUpdateListener = new Updater.CheckUpdateListener() {
    @Override
    public boolean onCompleted(boolean hasNewVersion, int versionCode, String versionName, String updateLog, String apkDownloadUrl) {
        if (hasNewVersion) {
            Log.i(TAG, "Find new version:");
            Log.i(TAG, "    version code:" + versionCode);
            Log.i(TAG, "    version name:" + versionName);
            Log.i(TAG, "    apk download url:" + apkDownloadUrl);
        } else {
            Log.i(TAG, "It's already the newest version!");
        }
        return false;
    }
};

private Updater.DownloadListener mDownloadListener = new Updater.DownloadListener() {

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
```

`update.xml` file format:

```
<?xml version="1.0" encoding="utf-8"?>
<info>
    <version>
        <code>2</code>
        <name>1.0.1</name>
    </version>
    <url>http://192.168.1.182:1234/app-debug.apk</url>
    <description>update description</description>
</info>
```

### LICENSE

The MIT License (MIT)

Copyright (c) 2015 WuHui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:
