package com.weacsoft.jaravel.vendor.aetherupload.event;

/**
 * 单个分片上传成功事件。
 */
public class ChunkUploadedEvent extends UploadEvent {

    /** 本次分片索引（从 0 开始） */
    public final int chunkIndex;
    /** 已上传分片数 */
    public final int uploadedCount;
    /** 总分片数 */
    public final int totalChunks;
    /** 进度百分比（0-100） */
    public final double percent;

    public ChunkUploadedEvent(String group, String resourceId, String filename, long size,
                              int chunkIndex, int uploadedCount, int totalChunks, double percent) {
        super(group, resourceId, filename, size);
        this.chunkIndex = chunkIndex;
        this.uploadedCount = uploadedCount;
        this.totalChunks = totalChunks;
        this.percent = percent;
    }
}
