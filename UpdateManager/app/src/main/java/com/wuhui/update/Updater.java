package com.wuhui.update;

import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Updater 通过一行代码就可以完成自动检查更新，如果有更新显示提示对话框，用户点击更新后
 * 自动下载和安装新版App，用户可以监听下载进度。
 */
public class Updater {
    public static final String TAG = "Updater";

    private final Context mContext;

    /** 更新对话框标题 */
    private final String mTitle;

    /** 更新对话框取消按钮文本 */
    private final String mDialogCancelBtnTxt;

    /** 更新对话框取确定按钮文本 */
    private final String mDialogOkBtnTxt;

    /** 检查更新的 HTTP 地址 */
    private final String mCheckUpdateUrl;

    /** 下载过程中显示在通知栏的标题 */
    private final String mNotificationTitle;

    /** 下载过程中显示在通知栏的消息 */
    private final String mNotificationMessage;

    /** App下载后的保存路径 */
    private final String mSavePath;

    /** 下载进度监听器 */
    private final DownloadListener mDownloadListener;

    /** 检查更新监听器，每次检查更新 */
    private final CheckUpdateListener mCheckUpdateListener;

    /* 下面字段保存从更新检查文件 update.xml 中获取到的信息 */
    private int mRemoteVersionCode = 0;
    private String mRemoteVersionName = "";
    private String mUpdateLog = "";
    private String mApkDownloadUrl = "";

    private long mDownloadId;
    private BroadcastReceiver mCompleteReceiver;
    private ContentObserver mContentObserver;
    private DownloadManager mDownloadMgr;

    private Updater(Context context, String title, String savePath, String checkUpdateUrl,
            String notificationTitle, String notificationMessage,
            String dialogCancelBtnTxt, String dialogOkBtnTxt,
            CheckUpdateListener l1, DownloadListener l2) {
        mContext = context;
        mTitle = title;
        mSavePath = savePath;
        mCheckUpdateUrl = checkUpdateUrl;
        mNotificationTitle = notificationTitle;
        mNotificationMessage = notificationMessage;
        mDialogCancelBtnTxt = dialogCancelBtnTxt;
        mDialogOkBtnTxt = dialogOkBtnTxt;
        mCheckUpdateListener = l1;
        mDownloadListener = l2;

        init();
    }

    public static Builder with(Context context) {
        return new Builder(context);
    }

    private void init() {

        mCompleteReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (downloadId == mDownloadId) {
                    installApk();
                    release();
                }
            }
        };

        mContentObserver = new ContentObserver(new Handler(mContext.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                if (mDownloadListener == null) {
                    return;
                }

                mDownloadMgr = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Query query = new DownloadManager.Query().setFilterById(mDownloadId);
                Cursor cursor = mDownloadMgr.query(query);
                try {
                    if (cursor != null && cursor.moveToFirst()) {
                        int totalSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                        int downloadedSize = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                        int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                        int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                        mDownloadListener.onChange(state, totalSize, downloadedSize);
                        Log.d("TAG", "state:" + state);
                        Log.d("TAG", "reason:" + reason);
                        if (state == DownloadManager.STATUS_FAILED) {
                            release();
                        }
                    }
                } finally {
                    if (cursor != null) {
                        cursor.close();
                    }
                }
            }
        };
    }

    /**
     * 检查 App 更新
     * 如果用户没有设置ChcekUpdateListener或者CheckUdpateListener.onCompleted()返回false，
     * 则进入更新处理，如果有新版本弹出对话框提示用户更新
     */
    public void checkUpdate() {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                boolean hasNewVersion = checkVersion();
                return hasNewVersion;
            }

            @Override
            protected void onPostExecute(Boolean hasNewVersion) {
                super.onPostExecute(hasNewVersion);

                if (mCheckUpdateListener == null
                        || !mCheckUpdateListener.onCompleted(hasNewVersion, mRemoteVersionCode,
                        mRemoteVersionName, mUpdateLog, mApkDownloadUrl)) {
                    if (hasNewVersion) {
                        showUpdateDialog();
                    }
                }
            }
        }.execute();
    }

    private void release() {
        // 取消广播注册
        mContext.unregisterReceiver(mCompleteReceiver);
        // 取消监听下载进度
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }

    /**
     * 检查 App 版本号
     *
     * @return 如果有新版本返回true，否则返回false
     */
    private boolean checkVersion() {
        URL url;
        HttpURLConnection httpConn = null;
        try {
            url = new URL(mCheckUpdateUrl);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(200000);
            httpConn.setReadTimeout(200000);
            httpConn.setUseCaches(false);       // disable cache for current http connection
            httpConn.connect();
            if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpConn.getInputStream();
                // 解析 XML 数据
                if (!parseXml(inputStream)) {
                    return false;
                }
                // 比较本地版本号与服务器版本号
                PackageInfo packageInfo = mContext.getPackageManager()
                        .getPackageInfo(mContext.getPackageName(), 0);
                if (packageInfo.versionCode < mRemoteVersionCode) {
                    return true;
                }
            } else {
                return false;
            }
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        } finally {
            httpConn.disconnect();
        }

        return false;
    }

    /**
     * 显示更新对话框
     */
    private void showUpdateDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle(mTitle);
        builder.setMessage(mUpdateLog);
        builder.setPositiveButton(mDialogOkBtnTxt, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                // 后台下载
                mDownloadMgr = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mApkDownloadUrl));
                if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                    // 如果保存路径包含子目录，需要先递归创建目录
                    if (!createDirIfAbsent(mSavePath)) {
                        Log.e("TAG", "apk save path can not be created:" + mSavePath);
                        return;
                    }

                    request.setDestinationUri(Uri.fromFile(new File(mSavePath)));
                    request.setTitle(mNotificationTitle);
                    request.setTitle(mNotificationMessage);
                    // 注册广播，监听下载完成事件
                    mContext.registerReceiver(mCompleteReceiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                    // 注册监听下载进度
                    mContext.getContentResolver().registerContentObserver(Uri.parse("content://downloads/my_downloads"),
                            true, mContentObserver);
                    mDownloadId = mDownloadMgr.enqueue(request);
                } else {
                    Log.e("TAG", "can not access external storage!");
                    return;
                }
                Toast.makeText(mContext, "正在后台下载...", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton(mDialogCancelBtnTxt, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.create().show();
    }

    /**
     * 如果参数 path 指定的路径中的目录不存在就创建指定目录，确保path路径的父目录存在
     *
     * @param path 绝对路径（包含文件名，例如 '/sdcard/storage/download/test.apk'）
     * @return 如果成功创建目录返回true，否则返回false
     */
    private boolean createDirIfAbsent(String path) {
        String[] array = path.trim().split(File.separator);
        List<String> dirNames = Arrays.asList(array).subList(1, array.length - 1);
        StringBuilder pathBuilder = new StringBuilder(File.separator);
        for (String d : dirNames) {
            pathBuilder.append(d);
            File f = new File(pathBuilder.toString());
            if (!f.exists() && !f.mkdir()) {
                return false;
            }
            pathBuilder.append(File.separator);
        }
        return true;
    }

    /**
     * 替换安装当前App，注意：签名一致
     */
    private void installApk() {
        // 获取下载的 APK 地址
        Uri apkUri = mDownloadMgr.getUriForDownloadedFile(mDownloadId);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        mContext.startActivity(intent);
    }

    /**
     * 解析XML文件，提取APK更新信息
     * <p/>
     * XML 格式，description 标签中分号表示换行
     * <pre>
     * {@code<?xml version="1.0" encoding="utf-8"?>
     * <info>
     *     <version>
     *         <code>4</code>
     *         <name>1.0.3</name>
     *     </version>
     *     <url>http://192.168.1.168:8000/whinc.apk</url>
     *     <description>更新 - 吧啦吧啦;修复 - 吧啦吧啦;新增 - 吧啦吧啦</description>
     * </info>
     * }
     * </pre>
     *
     * @param inputStream 包含XML内容的输入流
     * @return 如果解析成功返回true，否则返回false
     */
    private boolean parseXml(InputStream inputStream) {
        Document document = null;
        DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
        try {
            DocumentBuilder builder = builderFactory.newDocumentBuilder();
            document = builder.parse(inputStream);
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (document == null) {
            return false;
        }

        // <info>
        NodeList info = document.getDocumentElement().getChildNodes();
        for (int i = 0; i < info.getLength(); ++i) {
            Node node = info.item(i);
            if (node instanceof Element) {
                String nodeName = node.getNodeName();
                // <version>
                if (nodeName.equals("version")) {
                    NodeList version = node.getChildNodes();
                    for (int j = 0; j < version.getLength(); ++j) {
                        Node n = version.item(j);
                        if (n instanceof Element) {
                            if (n.getNodeName().equals("code")) {
                                mRemoteVersionCode = Integer.parseInt(n.getFirstChild().getNodeValue());
                            } else if (n.getNodeName().equals("name")) {
                                mRemoteVersionName = n.getFirstChild().getNodeValue();
                            }
                        }
                    }
                } else if (nodeName.equals("url")) {
                    mApkDownloadUrl = node.getFirstChild().getNodeValue();
                } else if (nodeName.equals("description")) {
                    mUpdateLog = node.getFirstChild().getNodeValue();
                    mUpdateLog = mUpdateLog.replaceAll(";", "\n");   // 将分号替换成换行符
                }
            }
        }
        return true;
    }

    public interface DownloadListener {
        /**
         * 下载进度变化时被调用
         *
         * @param state          下载状态，参考{@link DownloadManager}
         * @param totalSize      下载文件总大小
         * @param downloadedSize 已经下载的文件大小
         */
        void onChange(int state, int totalSize, int downloadedSize);
    }

    public interface CheckUpdateListener {
        /**
         * 检查版本结果返回时调。其中仅当 hasNewVersion 为 true 时，参数 versionCode
         * 、VersionName、UpdateLog、apkDownloadUrl 才有效。返回结果为 true 表示已经处理了不需要进行默认处理，否则
         * 交给 Updater 进行对新版本的默认处理（弹出更新日志对话框，给用户选择是否更新）
         *
         * @param hasNewVersion  是否有新版本
         * @param versionCode    版本号
         * @param versionName    版本名称，
         * @param updateLog      更新日志
         * @param apkDownloadUrl APK 下载地址
         * @return true表示不进行默认处理，false表示进行默认处理
         */
        boolean onCompleted(boolean hasNewVersion, int versionCode, String versionName, String updateLog, String apkDownloadUrl);
    }

    public static class Builder {
        private final Context mContext;
        private String mTitle = "发现新版本";
        private String mSavePath;
        private String mCheckUpdateUrl;
        private String mNotificationTitle;
        private String mNotificationMessage;
        private String mDialogCancelBtnTxt = "下次";
        private String mDialogOkBtnTxt = "更新";
        private DownloadListener mDownloadListener;
        private CheckUpdateListener mCheckUpdateListener;

        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            } else {
                mContext = context;
            }
        }

        /**
         * 设置更新提示对话框的标题
         */
        public Builder title(String title) {
            if (title == null) {
                mTitle = "";
            } else {
                mTitle = title;
            }
            return this;
        }

        /**
         * 设置下载的Apk安装文件保存路径
         */
        public Builder save(String savePath) {
            if (savePath == null || savePath.isEmpty()) {
                throw new IllegalArgumentException("save path must not be empty");
            } else {
                mSavePath = savePath;
            }
            return this;
        }

        /**
         * 设置应用检查更新地址
         */
        public Builder update(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("url must not be null or empty");
            } else {
                mCheckUpdateUrl = url;
            }
            return this;
        }

        /**
         * 设置通知栏消息标题
         */
        public Builder notificationTitle(String title) {
            if (title == null) {
                mNotificationTitle = "";
            } else {
                mNotificationTitle = title;
            }
            return this;
        }

        /**
         * 设置通知栏中消息
         */
        public Builder notificationMessage(String message) {
            if (message == null) {
                mNotificationMessage = "";
            } else {
                mNotificationMessage = message;
            }
            return this;
        }

        /**
         * 设置检查更新监听器
         */
        public Builder checkUpdateListener(CheckUpdateListener l) {
            mCheckUpdateListener = l;
            return this;
        }

        /**
         * 设置下载进度监听器
         */
        public Builder downloadListener(DownloadListener l) {
            mDownloadListener = l;
            return this;
        }

        /**
         * 设置更新提示对话框取消按钮显示文本
         */
        public Builder dialogCancelBtnTxt(String text) {
            if (text != null) {
                mDialogCancelBtnTxt = text;
            }
            return this;
        }

        /**
         * 设置更新提示对话框确认按钮显示文本
         */
        public Builder dialogOkBtnTxt(String text) {
            if (text != null) {
                mDialogOkBtnTxt = text;
            }
            return this;
        }

        /**
         * 创建Updater类实例
         */
        public Updater create() {
            if (mCheckUpdateUrl == null || mCheckUpdateUrl.isEmpty()) {
                throw new IllegalStateException(
                        "Must call Updater.Builder.update() to set update url before create");
            }
            if (mSavePath == null || mSavePath.isEmpty()) {
                throw new IllegalStateException(
                        "Must call Updater.Builder.save() to set downloaded apk save path before create");
            }

            return new Updater(mContext, mTitle, mSavePath, mCheckUpdateUrl,
                    mNotificationTitle, mNotificationMessage,
                    mDialogCancelBtnTxt, mDialogOkBtnTxt,
                    mCheckUpdateListener, mDownloadListener);
        }
    }
}
