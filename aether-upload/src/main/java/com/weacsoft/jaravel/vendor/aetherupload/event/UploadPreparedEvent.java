package com.weacsoft.jaravel.vendor.aetherupload.event;

/**
 * 上传任务已创建事件（prepare 成功后分发）。
 */
public class UploadPreparedEvent extends UploadEvent {

    /** 总分片数 */
    public final int totalChunks;
    /** 分片大小（字节） */
    public final long chunkSize;
    /** 是否为断点续传恢复的既有任务 */
    public final boolean resumed;

    public UploadPreparedEvent(String group, String resourceId, String filename, long size,
                               int totalChunks, long chunkSize, boolean resumed) {
        super(group, resourceId, filename, size);
        this.totalChunks = totalChunks;
        this.chunkSize = chunkSize;
        this.resumed = resumed;
    }
}
