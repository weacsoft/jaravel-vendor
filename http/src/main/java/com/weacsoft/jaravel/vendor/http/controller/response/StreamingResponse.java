package com.weacsoft.jaravel.vendor.http.controller.response;

import java.io.OutputStream;

/**
 * 可流式写出的响应：把内容<b>边产边写</b>到输出流，而不是先整体缓冲成
 * {@code String}/{@code byte[]}。
 * <p>
 * <b>为什么并入 {@link Response} 体系而不是另立一套</b>：{@link Response} 是路由
 * 处理层唯一认识的响应契约。若为了流式再引入一个平行接口，调用方就得判断
 * 「这个方法是流式还是普通」，错误分支（401/503 的 JSON）还得走另一条路——
 * 那正是「一个处理方法只能返回一种形态」的由来。把能力做成 {@code Response}
 * 的<b>可选扩展</b>后，同一个方法既能返回流式成功、也能返回 JSON 错误：
 * <pre>
 * public Response proxy(Request request) {
 *     if (badRequest) return ResponseBuilder.error(400, "...");        // 普通
 *     return ResponseBuilder.stream(200, sse -> copy(upstream, sse));  // 流式
 * }
 * </pre>
 * 适配层（springboot 的 {@code createResponse}）检测到实现本接口就走流式分支，
 * 否则沿用原来的 {@code getContent()} / {@code getBytes()}。
 * <p>
 * <b>向后兼容</b>：独立可选接口，不实现它的 {@code Response} 行为完全不变。
 */
@FunctionalInterface
public interface StreamingResponse {

    /**
     * 把响应体写入输出流。
     * <p>
     * 实现方负责在需要时 {@code flush()}；<b>不要</b>关闭该流——生命周期由容器管理。
     *
     * @param outputStream 目标输出流
     * @throws Exception 写入过程中的任何异常（调用方会记录并终止响应）
     */
    void writeTo(OutputStream outputStream) throws Exception;
}
