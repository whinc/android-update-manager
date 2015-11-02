package com.whinc.util.updater;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresPermission;
import android.text.TextUtils;
import android.util.Log;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * <p>Check app version, download app and install apk. </p>
 */
public class AppUpdater {
    private static final String TAG = AppUpdater.class.getSimpleName();
    private static final int MSG_DOWNLOAD = 0;
    private final Context mContext;

    public Version getVersion() {
        return mVersion;
    }

    private Version mVersion;

    private AppUpdater(Context context) {
        mContext = context;
    }

    public static AppUpdater newInstance(Context context) {
        return new AppUpdater(context);
    }

    /**
     * <p>Check if exist new version. You can access the new version info with {@link #getVersion()}</p>
     * @param checkVersionUrl
     * @param parser
     * @return return true if exist new version, otherwise return false.
     */
    public boolean checkVersion(@NonNull String checkVersionUrl, @NonNull VersionParser parser) {
        URL url = null;
        HttpURLConnection httpConn = null;
        try {
            url = new URL(checkVersionUrl);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(200000);
            httpConn.setReadTimeout(200000);
            httpConn.setUseCaches(false);       // disable cache for current http connection
            httpConn.connect();
            if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpConn.getInputStream();
                // parse version info.
                mVersion = parser.parse(inputStream);
                if (mVersion != null) {
                    PackageInfo packageInfo = mContext.getPackageManager()
                            .getPackageInfo(mContext.getPackageName(), 0);
                    // check if exist new version.
                    if (packageInfo.versionCode < mVersion.getVersionCode()) {
                        return true;
                    }
                }
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
     * <p>Async version of {@link #checkVersion(String, VersionParser)}</p>
     * @param checkVersionUrl
     * @param parser
     * @param listener
     */
    public void checkVersion(@NonNull final String checkVersionUrl, @NonNull final VersionParser parser, @Nullable final CheckVersionListener listener) {
        new AsyncTask<Void, Void, Boolean>() {

            @Override
            protected Boolean doInBackground(Void... params) {
                boolean hasNewVersion = checkVersion(checkVersionUrl, parser);
                return hasNewVersion;
            }

            @Override
            protected void onPostExecute(Boolean hasNewVersion) {
                super.onPostExecute(hasNewVersion);
                if (listener != null) {
                    listener.complete(hasNewVersion, getVersion());
                }
            }
        }.execute();
    }

    /**
     * Require android permission:{@code android.permission.DOWNLOAD_WITHOUT_NOTIFICATION}
     * @param from
     * @param downloadListener
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void download(@NonNull String from, @Nullable DownloadListener downloadListener) {
        download(from, null, downloadListener);
    }

    /**
     * Require android permission:{@code android.permission.DOWNLOAD_WITHOUT_NOTIFICATION}
     * @param from
     * @param to
     * @param downloadListener
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void download(@NonNull String from, @Nullable String to, @Nullable DownloadListener downloadListener) {
        download(from, to, downloadListener, "", "", false, DownloadManager.Request.VISIBILITY_HIDDEN);
    }

    /**
     * Download specified file with DownloadManger provided by Android.
     * @param from url address of file need to download
     * @param to save path of downloaded file. If it's null or empty string the save path will be determined by DownloadManager
     * @param downloadListener listener to the download progress.
     * @param title The task title displayed in notification bar
     * @param description The task description displayed in notification bar
     * @param visibleInDownloadUi If download task is visible in system download task manager ui.
     * @param notificationVisibility If download task is visible in notification bar.
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public void download(@NonNull String from,
                         @Nullable String to,
                         @Nullable final DownloadListener downloadListener,
                         @Nullable String title,
                         @Nullable String description,
                         boolean visibleInDownloadUi,
                         int notificationVisibility
    ) {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Log.e(TAG, "Cannot access external storage.");
            return;
        }

        Uri downloadUri = Uri.parse("content://downloads/my_downloads");
        final DownloadManager downloadManager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);

        // create download request
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(from));

        if (!TextUtils.isEmpty(to)) {
            File file = new File(to);
            request.setDestinationUri(Uri.fromFile(file));
        }
        request.setNotificationVisibility(notificationVisibility);
        request.setTitle(title);
        request.setDescription(description);
        request.setVisibleInDownloadsUi(visibleInDownloadUi);
        final long downloadId = downloadManager.enqueue(request);

        // register download complete broadcast
        IntentFilter intentFilter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    if (downloadListener != null) {
                        String filePath = DownloadContentObserver.queryDownloadFilePath(downloadManager, downloadId);
                        downloadListener.onComplete(filePath);
                    }
                    mContext.unregisterReceiver(this);
                }
            }
        }, intentFilter);

        // query download progress
        DownloadContentObserver observer = new DownloadContentObserver(
                new Handler(Looper.getMainLooper()), downloadManager, downloadId, downloadListener);
        mContext.getContentResolver().registerContentObserver(downloadUri, true, observer);
    }

    /**
     * Install specified apk installation file.
     * @param apkUri uri of apk installation file.
     */
    public void installApk(@NonNull Uri apkUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        mContext.startActivity(intent);
    }

    public void installApk(@NonNull String apkFile) {
        File file = new File(apkFile);
        if (!file.exists()) {
            Log.e(TAG, "Cannot install apk file: " + file.getAbsolutePath());
            return;
        }
        installApk(Uri.fromFile(file));
    }

    /*************************** Interface Define ***************************/

    public interface VersionParser {
        /**
         * <p>Parse version information from input stream</p>
         * @param inputStream data return from server
         * @return return a instance of {@link Version}, otherwise return null.
         */
        Version parse(InputStream inputStream);
    }

    public interface CheckVersionListener {
        /**
         * <p>This method will be called on finishing check version.</p>
         * @param hasNewVersion true if has new version, otherwise false
         * @param version
         */
        void complete(boolean hasNewVersion, Version version);
    }

    public interface DownloadListener {
        void onFailed(int reason, String reasonText);
        void onPaused(int reason, String reasonText);
        void onPending();

        /**
         * Reports download progress.
         * @param totalBytes this value will be -1 before knowing target file total size.
         * @param downloadedBytes
         */
        void onRunning(int totalBytes, int downloadedBytes);
        void onSuccessful();
        void onComplete(String file);
    }

    /*************************** Inner Class Define ***************************/

    /**
     * <p>A implementation of {@link VersionParser}. Parse xml format data.</p>
     */
    public static class VersionParseXML implements VersionParser {

        /**
         * <p>Parse xml format data and return a instance of {@link Version} include version info. </p>
         *
         * @param inputStream xml data with format below:
         * <pre>
         * {@code<?xml version="1.0" encoding="utf-8"?>
         * <info>
         *     <version>
         *         <code>4</code>
         *         <name>1.0.3</name>
         *     </version>
         *     <url>http://192.168.1.168:8000/whinc.apk</url>
         *     <description>update log</description>
         * </info>
         * }
         * </pre>
         * @return return a {@link Version} object if parse successfully, otherwise return null.
         */
        @Override
        public Version parse(InputStream inputStream) {
            Version version = new Version();
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
                return null;
            }

            // <info>
            NodeList info = document.getDocumentElement().getChildNodes();
            for (int i = 0; i < info.getLength(); ++i) {
                Node node = info.item(i);
                if (node instanceof Element) {
                    String nodeName = node.getNodeName();
                    // <version>
                    if (nodeName.equals("version")) {
                        NodeList versionNode = node.getChildNodes();
                        for (int j = 0; j < versionNode.getLength(); ++j) {
                            Node n = versionNode.item(j);
                            if (n instanceof Element) {
                                if (n.getNodeName().equals("code")) {
                                    int versionCode = Integer.parseInt(n.getFirstChild().getNodeValue());
                                    version.setVersionCode(versionCode);
                                } else if (n.getNodeName().equals("name")) {
                                    String versionName = n.getFirstChild().getNodeValue();
                                    version.setVersionName(versionName);
                                }
                            }
                        }
                    } else if (nodeName.equals("url")) {
                        String downloadUrl = node.getFirstChild().getNodeValue();
                        version.setDownloadUrl(downloadUrl);
                    } else if (nodeName.equals("description")) {
                        String updateLog = node.getFirstChild().getNodeValue();
                        version.setUpdateLog(updateLog);
                    }
                }
            }
            return version;
        }
    }

    /**
     * <p>A default implementation of interface {@link DownloadListener}</p>
     */
    public static class DownloadListenerAdapter implements DownloadListener {

        @Override
        public void onFailed(int reason, String reasonText) {

        }

        @Override
        public void onPaused(int reason, String reasonText) {

        }

        @Override
        public void onPending() {

        }

        @Override
        public void onRunning(int totalBytes, int downloadedBytes) {

        }

        @Override
        public void onSuccessful() {

        }

        @Override
        public void onComplete(String file) {

        }
    }

    /**
     * <p>Listener to download progress</p>
     */
    private static class DownloadContentObserver extends ContentObserver {
        private final DownloadManager mDownloadManager;
        private final DownloadListener mDownloadListener;
        private final long mDownloadId;

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public DownloadContentObserver(Handler handler, DownloadManager downloadManager, long downloadId, DownloadListener downloadListener) {
            super(handler);
            mDownloadManager = downloadManager;
            mDownloadId = downloadId;
            mDownloadListener = downloadListener;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            if (mDownloadListener == null) {
                return;
            }

            DownloadManager.Query query = new DownloadManager.Query().setFilterById(mDownloadId);
            Cursor cursor = mDownloadManager.query(query);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    int reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON));
                    switch (state) {
                        case DownloadManager.STATUS_FAILED:
                            mDownloadListener.onFailed(reason, getReasonText(state, reason));
                            break;
                        case DownloadManager.STATUS_PAUSED:
                            mDownloadListener.onPaused(reason, getReasonText(state, reason));
                            break;
                        case DownloadManager.STATUS_SUCCESSFUL:
                            mDownloadListener.onSuccessful();
                            break;
                        case DownloadManager.STATUS_PENDING:
                            mDownloadListener.onPending();
                            break;
                        case DownloadManager.STATUS_RUNNING:
                            int totalBytes = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                            int downloadedBytes = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                            mDownloadListener.onRunning(totalBytes, downloadedBytes);
                            break;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        public static String queryDownloadFilePath(DownloadManager downloadManager, long downloadId) {
            return queryColumn(downloadManager, downloadId, DownloadManager.COLUMN_LOCAL_FILENAME);
        }

        public static String queryColumn(DownloadManager downloadManager, long downloadId, String column) {
            String filepath = null;
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
            Cursor cursor = downloadManager.query(query);
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    filepath = cursor.getString(cursor.getColumnIndexOrThrow(column));
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return filepath;
        }

        /**
         * <p>Get reason string of DownloadManger error status</p>
         * @param status
         * @param reason
         * @return
         */
        private String getReasonText(int status, int reason) {
            String reasonText = "";
            switch(status){
                case DownloadManager.STATUS_FAILED:
                    switch(reason){
                        case DownloadManager.ERROR_CANNOT_RESUME:
                            reasonText = "ERROR_CANNOT_RESUME";
                            break;
                        case DownloadManager.ERROR_DEVICE_NOT_FOUND:
                            reasonText = "ERROR_DEVICE_NOT_FOUND";
                            break;
                        case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
                            reasonText = "ERROR_FILE_ALREADY_EXISTS";
                            break;
                        case DownloadManager.ERROR_FILE_ERROR:
                            reasonText = "ERROR_FILE_ERROR";
                            break;
                        case DownloadManager.ERROR_HTTP_DATA_ERROR:
                            reasonText = "ERROR_HTTP_DATA_ERROR";
                            break;
                        case DownloadManager.ERROR_INSUFFICIENT_SPACE:
                            reasonText = "ERROR_INSUFFICIENT_SPACE";
                            break;
                        case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
                            reasonText = "ERROR_TOO_MANY_REDIRECTS";
                            break;
                        case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
                            reasonText = "ERROR_UNHANDLED_HTTP_CODE";
                            break;
                        case DownloadManager.ERROR_UNKNOWN:
                            reasonText = "ERROR_UNKNOWN";
                            break;
                    }
                    break;
                case DownloadManager.STATUS_PAUSED:
                    switch(reason){
                        case DownloadManager.PAUSED_QUEUED_FOR_WIFI:
                            reasonText = "PAUSED_QUEUED_FOR_WIFI";
                            break;
                        case DownloadManager.PAUSED_UNKNOWN:
                            reasonText = "PAUSED_UNKNOWN";
                            break;
                        case DownloadManager.PAUSED_WAITING_FOR_NETWORK:
                            reasonText = "PAUSED_WAITING_FOR_NETWORK";
                            break;
                        case DownloadManager.PAUSED_WAITING_TO_RETRY:
                            reasonText = "PAUSED_WAITING_TO_RETRY";
                            break;
                    }
                    break;
                case DownloadManager.STATUS_PENDING:
                    break;
                case DownloadManager.STATUS_RUNNING:
                    break;
                case DownloadManager.STATUS_SUCCESSFUL:
                    break;
            }
            return reasonText;
        }
    }

    /**
     * <p>Java bean object used to store version info.</p>
     */
    public static class Version {
        private int mVersionCode;
        private String mVersionName;
        private String mDownloadUrl;
        private String mUpdateLog;

        public int getVersionCode() {
            return mVersionCode;
        }

        public void setVersionCode(int versionCode) {
            mVersionCode = versionCode;
        }

        public String getVersionName() {
            return mVersionName;
        }

        public void setVersionName(String versionName) {
            mVersionName = versionName;
        }

        public String getDownloadUrl() {
            return mDownloadUrl;
        }

        public void setDownloadUrl(String downloadUrl) {
            mDownloadUrl = downloadUrl;
        }

        public String getUpdateLog() {
            return mUpdateLog;
        }

        public void setUpdateLog(String updateLog) {
            mUpdateLog = updateLog;
        }

        @Override
        public String toString() {
            return String.format("[%d, %s, %s, %s]",
                    mVersionCode, mVersionName, mDownloadUrl, mUpdateLog);
        }
    }
}
