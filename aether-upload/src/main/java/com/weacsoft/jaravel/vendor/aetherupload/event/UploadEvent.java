package com.weacsoft.jaravel.vendor.aetherupload.event;

import com.weacsoft.jaravel.vendor.event.Event;

/**
 * 上传事件基类：携带组名、资源 id、文件名、文件大小。
 * <p>
 * 通过 {@code EventFacade.listen(UploadCompletedEvent.class, e -> ...)} 监听。
 */
public abstract class UploadEvent implements Event {

    /** 上传组名 */
    public final String group;
    /** 资源 id */
    public final String resourceId;
    /** 原始文件名 */
    public final String filename;
    /** 文件总大小（字节） */
    public final long size;

    protected UploadEvent(String group, String resourceId, String filename, long size) {
        this.group = group;
        this.resourceId = resourceId;
        this.filename = filename;
        this.size = size;
    }
}
