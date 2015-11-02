### AppUpdater

Help you check, download and install your newest version app simply and quickly.


### How to use

在模块的build.gradle中添加依赖，由于还没有发布到[jcenter](https://bintray.com/whinc/maven/appudpater/view)所以需要添加一个仓库地址：

```
repositories {
    maven {
        url 'https://dl.bintray.com/whinc/maven'
    }
}

dependencies {
    ...
    compile 'com.whinc.util.updater:appupdater:1.0.0'
}
```

下面代码演示了如何检查App版本、下载最新的App安装包和安装APK文件：

>注意：AppUpdater的download方法有几个重载版本，其中参数较少的版本默认设置是不在通知栏显示下载任务，这需要添加权限 android.permission.DOWNLOAD_WITHOUT_NOTIFICATION

```
protected void checkUpdate() {
    String checkVersionUrl = "http://192.168.1.182:1234/update.xml";
    AppUpdater updater = AppUpdater.newInstance(this);
    updater.checkVersionAsync(checkVersionUrl, new AppUpdater.VersionParseXML(), new AppUpdater.CheckVersionListener() {
        @Override
        public void complete(boolean hasNewVersion, AppUpdater.Version version, AppUpdater appUpdater) {
            if (hasNewVersion) {
                Log.i(TAG, "version info:" + version);
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
            }
        }
    });
}
```
AppUpdater检查版本信息时调用 `VersionParse`接口的parse()方法，该方法会返回一个包含了App版本信息`Version`对象，AppUpdater通过比较`Version`对象中的版本号与当前App的版本号来决定是否需要更新。AppUpdater默认实现了下面XML格式的版本信息解析，如要实现自定义的版本信息格式，实现`VersionParse`接口即可。（App的版本号就是build.gradle文件中的`android.defaultConfig.versionCode`字段）

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
