package com.whinc.util.updater;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * <p>Check app version, download app and install apk. </p>
 */
public class AppUpdater {
    private static final String TAG = AppUpdater.class.getSimpleName();
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

    /*************************** Interface Define ***************************/

    public interface VersionParser {
        /**
         * <p>Parse version information from input stream</p>
         * @param inputStream data return from server
         * @return return a instance of {@link Version}, otherwise return null.
         */
        Version parse(InputStream inputStream);
    }

    /**
     * <p>A implementation of {@link com.whinc.util.updater.AppUpdater.VersionParser}. Parse xml format data.</p>
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

    public interface CheckVersionListener {
        /**
         * <p>This method will be called on finishing check version.</p>
         * @param hasNewVersion true if has new version, otherwise false
         * @param version
         */
        void complete(boolean hasNewVersion, Version version);
    }
}
