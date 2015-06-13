package com.wuhui.update;

import android.annotation.TargetApi;
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
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by wuhui on 6/9/15.
 */
public class UpdateManager {
    public static final String TAG = "UpdateManager";
    private static final int MSG_CHECK_VERSION = 1;
    private static final int HTTP_CONNECTION_TIMEOUT = 5000;

    private Context mContext;

    private String mUpdateUrl;          // 检查更新的地址
    private int mRemoteVersionCode;     // 版本号
    private String mRemoteVersionName;  // 版本名称
    private String mUpdateLog;          // 更新日志
    private String mApkDownloadUrl;     // APK下载地址
    private String mApkSavePath;
    private String mTitleInNotification = "";    // 下载通知栏中显示的标题
    private String mDescriptionInNotification = "";  // 下载通知栏中的描述

    private HandlerThread mThread;
    private Handler mHandler;
    private long mDownloadId;
    private BroadcastReceiver mCompleteReceiver;
    private ContentObserver mContentObserver;
    private OnDownloadChangeListener mOnDownloadChangeListener;
    private OnCheckVersionResultListener mOnCheckVersionResultListener;
    private DownloadManager mDownloadMgr;

    public static void checkUpdate(Context context) {
        checkUpdate(context, null, null);
    }

    public static void checkUpdate(Context context, OnCheckVersionResultListener listener) {
        checkUpdate(context, listener, null);
    }

    /**
     * 检查更新
     * @param context
     * @param listener1 如果 listens1 为 null 或者其 onResult 方法返回false 进行默认处理（弹出日志对话框），否则不进行默认处理
     * @param listener2 如果 listent2 不为 null 其可以接收到下载进度
     */
    public static void checkUpdate(Context context, OnCheckVersionResultListener listener1,  OnDownloadChangeListener listener2) {
        String apkDownloadUrl = "http://192.168.1.168:8000/update.xml";
        String title = "MyApp";
        String description = "MyApp 正在下载中...";
        String apkSavePath = String.format("%s_MyApp.apk",
                new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()));

        UpdateManager updateMgr = new UpdateManager(context, apkDownloadUrl, apkSavePath);
        updateMgr.setTitleInNotification(title);
        updateMgr.setDescriptionInNotification(description);
        updateMgr.setOnCheckVersionResultListener(listener1);
        updateMgr.setOnDownloadChangeListener(listener2);
        updateMgr.checkUpdate();
    }

    public UpdateManager(Context context, String updateUrl, String savePath) {
        mContext = context;
        mApkSavePath = savePath;
        mUpdateUrl = updateUrl;

        mThread = new HandlerThread(this.getClass().getSimpleName());
        mThread.start();

        mHandler = new Handler(mThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case MSG_CHECK_VERSION:
                        boolean hasNewVersion = checkVersion();
                        if (mOnCheckVersionResultListener != null
                                && mOnCheckVersionResultListener.onResult(
                                hasNewVersion, mRemoteVersionCode, mRemoteVersionName, mUpdateLog, mApkDownloadUrl)) {
                            // 不进行默认处理
                            break;
                        } else {
                            if (hasNewVersion) {
                                showUpdateLogDialog();
                            }
                        }
                        break;
                }
            }
        };

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
                if (mOnDownloadChangeListener == null) {
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
                        mOnDownloadChangeListener.onChange(state, totalSize, downloadedSize);
                        if (state == DownloadManager.STATUS_FAILED) {
                            release();
                            Log.d("TAG", "realease() called");
                        }
                        Log.d("TAG", "state:" + state);
                        Log.d("TAG", "reason:" + reason);
                    }
                } finally {
                    if(cursor != null) {
                        cursor.close();
                    }
                }
            }
        };
    }

    /**
     * 检查 App 更新，如果有新版本则显示更新提示
     */
    private void checkUpdate() {
        mHandler.sendEmptyMessage(MSG_CHECK_VERSION);
    }

    public void setTitleInNotification(String title) {
        mTitleInNotification = title;
    }

    public void setDescriptionInNotification(String description) {
        mDescriptionInNotification = description;
    }

    public void setOnDownloadChangeListener(OnDownloadChangeListener listener) {
        mOnDownloadChangeListener = listener;
    }

    public void setOnCheckVersionResultListener(OnCheckVersionResultListener listener) {
        mOnCheckVersionResultListener = listener;
    }

    public void release() {
        // 取消广播注册
        mContext.unregisterReceiver(mCompleteReceiver);
        // 取消监听下载进度
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }

    /**
     * 下载的 APK 保存路径（相对路径），以下面代码获取的路径作为父目录
     * Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
     * @param path APK 的保存路径，例如 "/appdir/appname.apk"
     */
    public void setApkSavePath(String path) {
        mApkSavePath = path;
    }

    /**
     * 检查 App 版本号
     * @return 如果有新版本返回true，否则返回false
     */
    private boolean checkVersion() {
        URL url;
        HttpURLConnection httpConn = null;
        try {
            url = new URL(mUpdateUrl);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(HTTP_CONNECTION_TIMEOUT);
            httpConn.setUseCaches(false);       // disable cache for current http connection
            httpConn.connect();
            if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpConn.getInputStream();
                // 解析 XML 数据
                if (!parseXml(inputStream)) {
//                    Log.d("TAG", "invalid update.xml format");
                    return false;
                }
                // 比较版本号
                PackageInfo packageInfo = mContext.getPackageManager()
                        .getPackageInfo(mContext.getPackageName(), 0);
                if (packageInfo.versionCode < mRemoteVersionCode) {
                    return true;
                }
            } else {
                Toast.makeText(mContext, "Please check network connection!", Toast.LENGTH_SHORT).show();
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
     * 显示更新日志对话框
     */
    private void showUpdateLogDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        builder.setTitle("New Version");
        mUpdateLog = String.format("v%s\n%s", mRemoteVersionName, mUpdateLog);
        builder.setMessage(mUpdateLog);
        builder.setPositiveButton("Download", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                // 后台下载
                mDownloadMgr = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(mApkDownloadUrl));
                try {
                    // 如果保存路径包含子目录，需要先递归创建目录
//                    String s = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getAbsolutePath() + "/Qfox";
//                    File f = new File(s);
//                    if (!f.exists()) {
//                        if (!f.mkdir()) {
//                            Log.d("TAG", "mkdir failed!");
//                            return;
//                        }
//                    }
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, mApkSavePath);
                    request.setTitle(mTitleInNotification);
                    request.setTitle(mDescriptionInNotification);
                    // 注册广播，监听下载完成事件
                    mContext.registerReceiver(mCompleteReceiver,
                            new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
                    // 注册监听下载进度
                    mContext.getContentResolver().registerContentObserver(Uri.parse("content://downloads/my_downloads"),
                            true, mContentObserver);
                    mDownloadId = mDownloadMgr.enqueue(request);
                    Toast.makeText(mContext, "Download in background...",
                            Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                    Toast.makeText(mContext, "can not access external storage!", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        });
        builder.setNegativeButton("Next time", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        builder.create().show();
    }

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
     *
     * XML 格式，description 标签中分号表示换行
     * <?xml version="1.0" encoding="utf-8"?>
     * <info>
     *     <version>
     *         <code>4</code>
     *         <name>1.0.3</name>
     *     </version>
     *     <url>http://192.168.1.168:8000/Qfox-QQ.apk</url>
     *     <description>更新 - 吧啦吧啦;修复 - 吧啦吧啦;新增 - 吧啦吧啦</description>
     * </info>
     *
     * @param inputStream
     * @return
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

    public interface OnDownloadChangeListener {
        void onChange(int state, int totalSize, int downloadedSize);
    }

    public interface OnCheckVersionResultListener {
        /**
         * 检查版本结果返回时调。其中仅当 hasNewVersion 为 true 时，参数 versionCode
         * 、VersionName、UpdateLog、apkDownloadUrl 才有效。返回结果为 true 表示已经处理了不需要进行默认处理，否则
         * 交给 UpdateManager 进行对新版本的默认处理（弹出更新日志对话框，给用户选择是否更新）
         *
         * @param hasNewVersion 是否有新版本
         * @param versionCode 版本号
         * @param versionName 版本名称，
         * @param updateLog 更新日志
         * @param apkDownloadUrl APK 下载地址
         * @return true表示不需要进行默认处理，false表示需要进行默认处理
         */
        boolean onResult(boolean hasNewVersion, int versionCode, String versionName, String updateLog, String apkDownloadUrl);
    }
}
