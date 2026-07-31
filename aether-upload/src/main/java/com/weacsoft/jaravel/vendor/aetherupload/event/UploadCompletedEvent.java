package com.weacsoft.jaravel.vendor.aetherupload.event;

/**
 * 上传完成事件（全部分片合并落盘后分发，同步上传完成也会分发）。
 */
public class UploadCompletedEvent extends UploadEvent {

    /** 完成文件的绝对保存路径 */
    public final String savedPath;

    public UploadCompletedEvent(String group, String resourceId, String filename, long size, String savedPath) {
        super(group, resourceId, filename, size);
        this.savedPath = savedPath;
    }
}
