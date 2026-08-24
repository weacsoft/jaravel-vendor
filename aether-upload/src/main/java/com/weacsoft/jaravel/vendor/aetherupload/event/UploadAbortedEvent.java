package com.weacsoft.jaravel.vendor.aetherupload.event;

/**
 * 上传中止事件（前端主动 abort 后分发）。
 */
public class UploadAbortedEvent extends UploadEvent {

    public UploadAbortedEvent(String group, String resourceId, String filename, long size) {
        super(group, resourceId, filename, size);
    }
}
