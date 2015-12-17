### AppUpdater

You can use AppUpdater to check latest version, download and install apk. It provides sync and async style interface and dependent no other library.

`AppUpdater`集成了App版本检查、下载和安装，可以方便的实现App的自动更新逻辑。同时提供同步和异步接口，没有任何依赖，可自定义。

### Integaration （集成）

Add it in your **root** build.gradle at the end of repositories:

在项目**根目录**下的 build.gradle 文件中添加下面maven仓库地址：

```groovy
allprojects {
    repositories {
        ...
        maven { url "https://jitpack.io" }
    }
}
```

Add dependency to you **module** build.gradle file:

在**模块**的build.gradle中添加下面依赖：

```groovy
dependencies {
    ...
    compile 'com.github.whinc:android-update-manager:1.1.0'
}
```

### How to use （使用）

下面代码演示了如何检查App版本、下载最新的App安装包和安装APK文件：

>注意：`AppUpdater`的下载使用的是Android系统的`DownloadManager`服务，下载时默认会在通知栏显示下载任务，如果不想在状态栏显示下载任务，需要添加权限 `android.permission.DOWNLOAD_WITHOUT_NOTIFICATION`

```
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
            AppUpdater.getInstance().download(this, downloadUrl,
                    new AppUpdater.DownloadListenerAdapter() {
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
```

`AppUpdater`的`download()`方法会返回下载`id`，可以调用`AppUpdater`的`cancelDownload()`来取消指定id的下载任务。

`AppUpdater`检查版本信息时调用 `VersionParse`接口的`parse()`方法，该方法会返回一个包含了App版本信息`Version`对象。通过比较`Version`对象中的版本号与当前App的版本号来决定是否需要更新。

`AppUpdater.VersionParseXML`类实现了对下面XML格式的版本信息解析，如要实现自定义的版本信息格式，实现`VersionParse`接口即可。

```
<?xml version="1.0" encoding="utf-8"?>
<info>
	<version>
		<code>3</code>
		<name>1.0.2</name>
	</version>
	<url>http://192.168.1.182:1234/app-debug.apk</url>
	<description>update description</description>
</info>
```

### The MIT License (MIT)

Copyright (c) 2015 WuHui

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
