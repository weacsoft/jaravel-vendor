package com.weacsoft.jaravel.vendor.wechat.reply;

import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.message.Text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 回复拆分计划：把业务返回的一批消息，按「微信协议能力 + 额度软限制」切成三段。
 * <p>
 * 规则（<b>不改变消息顺序</b>）：
 * <ol>
 *   <li><b>被动回复</b>：只有列表的<b>第 1 条</b>可以作为被动回复（协议上也只能有一条），
 *       且该消息类必须支持被动回复（{@link Message#toXmlArray()} 不抛异常，
 *       即 text/image/voice/video/music/news）；</li>
 *   <li>第 1 条不支持被动回复（小程序卡片、卡券、菜单消息…）或配置
 *       {@code passive-reply-limit=0} 时，本次<b>不做被动回复</b>（回空串），全部走客服消息
 *       —— 不会把后面的消息悄悄提到前面；</li>
 *   <li><b>客服消息</b>：其余消息走 {@code message/custom/send}，条数受
 *       {@code customer-service-reply-limit}（默认 5）与<b>该用户剩余额度</b>双重约束；</li>
 *   <li><b>溢出</b>：{@code reply-overflow-policy=drop}（默认）丢弃并记 warn；
 *       {@code merge} 则把溢出文本合并 —— 额度有空位时并成 1 条，已满时追加到最后一条文本里
 *       （总条数不变、内容不丢）。</li>
 * </ol>
 * 纯函数（不联网、不写缓存），便于单测与压测推演。
 */
public final class ReplyPlan {

    private final Message passiveReply;
    private final List<Message> customerMessages;
    private final List<Message> dropped;

    private ReplyPlan(Message passiveReply, List<Message> customerMessages, List<Message> dropped) {
        this.passiveReply = passiveReply;
        this.customerMessages = Collections.unmodifiableList(customerMessages);
        this.dropped = Collections.unmodifiableList(dropped);
    }

    /**
     * @param passiveReply     作为被动回复返回给微信的消息（null 表示回空串）
     * @param customerMessages 需要异步补发的客服消息
     * @param dropped          因额度不足被放弃的消息
     * @return 计划
     */
    public static ReplyPlan of(Message passiveReply, List<Message> customerMessages, List<Message> dropped) {
        return new ReplyPlan(passiveReply, new ArrayList<>(customerMessages), new ArrayList<>(dropped));
    }

    /**
     * 按配置与剩余额度做拆分。
     *
     * @param account        公众号配置（额度与溢出策略）
     * @param messages       业务想发的消息（按到达顺序）
     * @param remainingSlots 该用户当前剩余的客服消息额度
     * @return 拆分计划
     */
    public static ReplyPlan plan(WechatProperties.OfficialAccountConfig account,
                                 List<Message> messages, int remainingSlots) {
        if (messages == null || messages.isEmpty()) {
            return new ReplyPlan(null, List.of(), List.of());
        }
        int passiveLimit = account == null ? 1 : Math.max(0, Math.min(1, account.getPassiveReplyLimit()));
        int configuredCustomerLimit = account == null ? 5 : Math.max(0, account.getCustomerServiceReplyLimit());
        int budget = Math.min(configuredCustomerLimit, Math.max(0, remainingSlots));
        boolean merge = account != null && "merge".equalsIgnoreCase(account.getReplyOverflowPolicy());

        Message passive = null;
        int from = 0;
        if (passiveLimit == 1 && supportsPassive(messages.get(0))) {
            passive = messages.get(0);
            from = 1;
        }

        List<Message> customer = new ArrayList<>();
        List<Message> overflow = new ArrayList<>();
        for (int i = from; i < messages.size(); i++) {
            if (customer.size() < budget) {
                customer.add(messages.get(i));
            } else {
                overflow.add(messages.get(i));
            }
        }

        List<Message> dropped = new ArrayList<>();
        if (!overflow.isEmpty()) {
            if (merge) {
                // merge 策略：能并则并（条数不变、内容不丢），并无可并时才记丢弃
                if (!mergeOverflow(customer, overflow, budget)) {
                    dropped.addAll(overflow);
                }
            } else {
                // drop 策略（默认）：超出额度的部分直接丢弃并记账
                dropped.addAll(overflow);
            }
        }
        return new ReplyPlan(passive, customer, dropped);
    }

    /**
     * 把溢出文本合并进计划：有空位则并成 1 条；额度已满则追加到最后一条文本上。
     *
     * @return true 表示已全部安置（无需记丢弃）
     */
    private static boolean mergeOverflow(List<Message> customer, List<Message> overflow, int budget) {
        Text merged = mergeToText(overflow);
        if (merged == null) {
            return false;
        }
        if (customer.size() < budget) {
            customer.add(merged);
            return true;
        }
        if (!customer.isEmpty() && customer.get(customer.size() - 1) instanceof Text last) {
            customer.set(customer.size() - 1, new Text(last.getContent() + "\n" + merged.getContent()));
            return true;
        }
        return false;
    }

    /**
     * 该消息类能否作为被动回复（微信只支持 text/image/voice/video/music/news）。
     *
     * @param message 消息
     * @return true 表示可以
     */
    public static boolean supportsPassive(Message message) {
        if (message == null) {
            return false;
        }
        try {
            message.toXmlArray();
            return true;
        } catch (UnsupportedOperationException e) {
            return false;
        }
    }

    /**
     * 把多条溢出消息合并成一条文本（含非文本消息时放弃合并）。
     *
     * @param messages 溢出消息
     * @return 合并后的文本，无法合并时 null
     */
    private static Text mergeToText(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message message : messages) {
            if (!(message instanceof Text text)) {
                return null;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(text.getContent());
        }
        return sb.length() == 0 ? null : new Text(sb.toString());
    }

    /**
     * @return 被动回复消息（可能为 null）
     */
    public Message passiveReply() {
        return passiveReply;
    }

    /**
     * @return 需要补发的客服消息（不可变）
     */
    public List<Message> customerMessages() {
        return customerMessages;
    }

    /**
     * @return 被丢弃的消息（不可变）
     */
    public List<Message> dropped() {
        return dropped;
    }

    /**
     * @return 是否没有任何需要异步补发/丢弃的内容（等价于单条被动回复的老行为）
     */
    public boolean passiveOnly() {
        return customerMessages.isEmpty() && dropped.isEmpty();
    }

    @Override
    public String toString() {
        return "ReplyPlan{passive=" + (passiveReply == null ? "-" : passiveReply.getType())
                + ", customer=" + customerMessages.size() + ", dropped=" + dropped.size() + "}";
    }
}