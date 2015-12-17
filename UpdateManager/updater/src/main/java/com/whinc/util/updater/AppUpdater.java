package com.whinc.util.updater;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * <p>Check app version, download app and install apk. </p>
 */
public class AppUpdater {
    private static final String TAG = AppUpdater.class.getSimpleName();
    private static AppUpdater sSingleton = null;

    private AppUpdater() {}

    public static AppUpdater getInstance() {
        if (sSingleton == null) {
            sSingleton = new AppUpdater();
        }
        return sSingleton;
    }

    public Version checkVersion(@NonNull String checkVersionUrl,
                                @NonNull VersionParser parser)
            throws PackageManager.NameNotFoundException, VersionParserException, IOException {
        return checkVersion(checkVersionUrl, parser, Params.getDefault());
    }

    /**
     * <p>Check if exist new version.</p>
     */
    public Version checkVersion(@NonNull String checkVersionUrl,
                                @NonNull VersionParser parser,
                                @NonNull Params params)
            throws VersionParserException, IOException, PackageManager.NameNotFoundException {
        Version version = null;
        URL url = null;
        HttpURLConnection httpConn = null;
        try {
            url = new URL(checkVersionUrl);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setConnectTimeout(params.getConnectTimeout());
            httpConn.setReadTimeout(params.getReadTimeout());
            httpConn.setUseCaches(false);       // disable cache for current http connection
            httpConn.connect();
            if (httpConn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = httpConn.getInputStream();
                // parse version info.
                version = parser.parse(inputStream);
            } else {
                StringBuilder builder = new StringBuilder();
                if (httpConn.getErrorStream() != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(httpConn.getErrorStream()));
                    String line = reader.readLine();
                    while (line != null) {
                        builder.append(line).append('\n');
                        line = reader.readLine();
                    }
                    reader.close();
                }
                throw new IOException("unrecognized response code:"
                        + httpConn.getResponseCode() + "\n" + builder );
            }
        } finally {
            if (httpConn != null) {
                httpConn.disconnect();
            }
        }

        return version;
    }


    public void checkVersionAsync(@NonNull final String checkVersionUrl,
                                  @NonNull final VersionParser parser,
                                  @NonNull final CheckVersionListener listener) {
        checkVersionAsync(checkVersionUrl, parser, listener, Params.getDefault());
    }
    /**
     * <p>Async version of {@link #checkVersion(String, VersionParser)}</p>
     * @param checkVersionUrl
     * @param parser
     * @param listener
     */
    public void checkVersionAsync(@NonNull final String checkVersionUrl,
                                  @NonNull final VersionParser parser,
                                  @NonNull final CheckVersionListener listener,
                                  @NonNull final Params params) {
        new AsyncTask<Object, Void, Version>() {

            @Override
            protected Version doInBackground(Object... args) {
                Version version = null;
                try {
                    version = checkVersion(checkVersionUrl, parser, params);
                } catch (PackageManager.NameNotFoundException e) {
                    listener.onError(e);
                } catch (IOException e) {
                    listener.onError(e);
                } catch (VersionParserException e) {
                    listener.onError(e);
                }
                return version;
            }

            @Override
            protected void onPostExecute(Version version) {
                super.onPostExecute(version);
                listener.complete(version, AppUpdater.this);
            }
        }.execute();
    }

    /**
     * Require android permission:{@code android.permission.DOWNLOAD_WITHOUT_NOTIFICATION}
     * @param from
     * @param downloadListener
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public long download(@NonNull Context context,
                         @NonNull String from,
                         @Nullable DownloadListener downloadListener) throws IOException {
        return download(context, from, null, downloadListener);
    }

    /**
     * Require android permission:{@code android.permission.DOWNLOAD_WITHOUT_NOTIFICATION}
     * @param from
     * @param to
     * @param downloadListener
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public long download(@NonNull Context context,
                         @NonNull String from,
                         @Nullable String to,
                         @Nullable DownloadListener downloadListener) throws IOException {
        return download(context, from, to, downloadListener, "", "", false, DownloadManager.Request.VISIBILITY_HIDDEN);
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
     * @return return the download id
     */
    @RequiresPermission(Manifest.permission.INTERNET)
    public long download(@NonNull Context context,
                         @NonNull String from,
                         @Nullable String to,
                         @Nullable final DownloadListener downloadListener,
                         @Nullable String title,
                         @Nullable String description,
                         boolean visibleInDownloadUi,
                         int notificationVisibility
    ) throws IOException {
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            throw new IOException("Cannot access external storage.");
        }

        Uri downloadUri = Uri.parse("content://downloads/my_downloads");
        final DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);

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
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                long id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (id == downloadId) {
                    if (downloadListener != null) {
                        String filePath = DownloadContentObserver.queryDownloadFilePath(downloadManager, downloadId);
                        downloadListener.onComplete(filePath, AppUpdater.this);
                    }
                    context.unregisterReceiver(this);
                }
            }
        }, intentFilter);

        // query download progress
        DownloadContentObserver observer = new DownloadContentObserver(
                new Handler(Looper.getMainLooper()), downloadManager, downloadId, downloadListener);
        context.getContentResolver().registerContentObserver(downloadUri, true, observer);

        return downloadId;
    }

    /**
     * Cancel downloads and remove them from the download manager.  Each download will be stopped if
     * it was running, and it will no longer be accessible through the download manager.
     * If there is a downloaded file, partial or complete, it is deleted.
     *
     * @param downloadIds the IDs of the downloads to remove
     * @return the number of downloads actually removed
     */
    public int cancelDownload(@NonNull Context context, @NonNull long... downloadIds) {
        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        return downloadManager.remove(downloadIds);
    }

    /**
     * Install specified apk installation file.
     */
    public void installApk(@NonNull Context context, @NonNull Uri apkUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
        context.startActivity(intent);
    }

    public void installApk(@NonNull Context context, @NonNull String apkFile) throws FileNotFoundException {
        if (TextUtils.isEmpty(apkFile)) {
            throw new IllegalArgumentException("apk file path is empty!");
        }

        File file = new File(apkFile);
        if (!file.exists()) {
            throw new FileNotFoundException("cannot find file:" + file.getAbsolutePath());
        }
        installApk(context, Uri.fromFile(file));
    }

    /*************************** Interface Define ***************************/

    public interface VersionParser {
        /**
         * <p>Parse version information from input stream</p>
         * @param inputStream data return from server
         * @return return a instance of {@link Version}, otherwise return null.
         */
        Version parse(InputStream inputStream) throws VersionParserException;
    }

    public interface CheckVersionListener {
        /**
         * <p>This method will be called on finishing check version.</p>
         */
        void complete(Version version, AppUpdater appUpdater);

        void onError(Exception e);
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
        void onComplete(String file, AppUpdater appUpdater);
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
         * @return return a {@link Version} object if parse successfully, otherwise throws exception.
         */
        @Override
        public Version parse(InputStream inputStream) throws VersionParserException {
            Version version = new Version();
            Document document = null;
            DocumentBuilderFactory builderFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = null;
            try {
                builder = builderFactory.newDocumentBuilder();
                document = builder.parse(inputStream);
            } catch (ParserConfigurationException e) {
                throw new VersionParserException(e);
            } catch (SAXException e) {
                throw new VersionParserException(e);
            } catch (IOException e) {
                throw new VersionParserException(e);
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
                                    int versionCode = 0;
                                    try {
                                        versionCode = Integer.parseInt(n.getFirstChild().getNodeValue());
                                    } catch (NumberFormatException e) {
                                        throw new VersionParserException(e);
                                    } catch (DOMException e) {
                                        throw new VersionParserException(e);
                                    }
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
        public void onComplete(String file, AppUpdater appUpdater) {

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

    public static class VersionParserException extends Exception {
        public VersionParserException(Throwable throwable) {
            super(throwable);
        }
    }

    public static class Params {
        private static final int HTTP_CONNECT_TIMEOUT = 20 * 000;
        private static final int HTTP_READ_TIMEOUT = 20 * 000;
        private static Params sDefault = null;

        private int mConnectTimeout;
        private int mReadTimeout;

        public Params(int connectTimeout, int readTimeout) {
            mConnectTimeout = connectTimeout;
            mReadTimeout = readTimeout;
        }

        public static Params getDefault() {
            if (sDefault == null) {
                sDefault = new Params(HTTP_CONNECT_TIMEOUT, HTTP_READ_TIMEOUT);
            }
            return sDefault;
        }

        public int getReadTimeout() {
            return mReadTimeout;
        }

        public void setReadTimeout(int readTimeout) {
            mReadTimeout = readTimeout;
        }

        public int getConnectTimeout() {
            return mConnectTimeout;
        }

        public void setConnectTimeout(int connectTimeout) {
            mConnectTimeout = connectTimeout;
        }
    }
}
