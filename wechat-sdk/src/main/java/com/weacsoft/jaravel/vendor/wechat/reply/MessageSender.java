package com.weacsoft.jaravel.vendor.wechat.reply;

import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;

/**
 * 客服消息发送器（SDK 内部拆分下发时使用的最小抽象）。
 * <p>
 * 生产实现就是 {@code OfficialAccountService::sendCustomerMessage}；
 * 单测/压测可以注入自己的实现（例如只计数不联网），因此这里刻意做成函数式接口。
 *
 * @author weacsoft
 */
@FunctionalInterface
public interface MessageSender {

    /**
     * 以客服消息下发一条消息。
     *
     * @param account 公众号配置名（{@code jaravel.wechat.official-accounts} 下的名字）
     * @param message 消息（touser 已由调度器填好）
     * @return 微信响应（调用方按 errcode 判断；额度耗尽时微信返回 45047）
     */
    WeChatResponse send(String account, Message message);
}