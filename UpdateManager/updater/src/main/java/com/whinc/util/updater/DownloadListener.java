package com.whinc.util.updater;

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

