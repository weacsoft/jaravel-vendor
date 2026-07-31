package com.weacsoft.jaravel.vendor.aetherupload.event;

/**
 * 上传失败事件（校验失败 / IO 异常时分发）。
 */
public class UploadFailedEvent extends UploadEvent {

    /** 失败原因 */
    public final String reason;

    public UploadFailedEvent(String group, String resourceId, String filename, long size, String reason) {
        super(group, resourceId, filename, size);
        this.reason = reason;
    }
}
