package com.weacsoft.jaravel.vendor.wechat.reply;

import com.weacsoft.jaravel.vendor.cache.CacheStore;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.message.Message;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 多条回复调度器：<b>额度软限制 + 自动拆分下发</b>的执行者。
 * <p>
 * 职责：
 * <ul>
 *   <li>每次互动开始时按配置算出本次可用的客服消息额度
 *       （{@code reply-quota-reset-per-interaction}：true=每条用户消息重置额度，false=窗口内累计）；</li>
 *   <li>把 {@link ReplyPlan} 里剩下的消息<b>异步</b>用客服消息发出（被动回复必须 5 秒内返回，
 *       所以补发只能异步）；</li>
 *   <li>微信返回 {@code 45047}（下行条数超上限）或 {@code 45015}（回复超时/取消）时，
 *       立即把该用户的剩余额度清零并停止后续下发，避免继续刷接口。</li>
 * </ul>
 * 额度记录存在 {@link CacheStore}（与 access_token 同一套缓存，多实例可共享 redis）。
 * <p>
 * 线程模型：默认单线程守护池（保证多条消息按顺序到达）；
 * 需要接自己的任务队列（如 jaravel queue）时用 {@link #withExecutor(Executor)} 替换。
 */
public final class AsyncReplyDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(AsyncReplyDispatcher.class);

    /** 额度缓存键前缀：{@code wechat:reply_quota:{account}:{openid}} */
    private static final String KEY_PREFIX = "wechat:reply_quota:";

    /** 微信：客服接口下行条数超过上限 */
    private static final int ERRCODE_OUT_OF_RESPONSE_COUNT = 45047;

    /** 微信：回复时间超过限制（48 小时窗口外） */
    private static final int ERRCODE_RESPONSE_OUT_OF_TIME = 45015;

    private final MessageSender sender;
    private final CacheStore store;
    private final WechatProperties properties;
    private final AtomicInteger threadSeq = new AtomicInteger();

    private volatile Executor executor;

    /**
     * @param sender     客服消息发送器
     * @param store      额度缓存（可为 null：退化为「不记账，只按配置条数限制」）
     * @param properties 微信配置（取窗口时长等）
     */
    public AsyncReplyDispatcher(MessageSender sender, CacheStore store, WechatProperties properties) {
        this.sender = sender;
        this.store = store;
        this.properties = properties;
        this.executor = null;   // 懒创建，避免不用多条回复时白起线程
    }

    /**
     * 替换异步执行器（例如交给业务自己的线程池 / 队列）。
     *
     * @param executor 执行器
     * @return 本对象（fluent）
     */
    public AsyncReplyDispatcher withExecutor(Executor executor) {
        this.executor = executor;
        return this;
    }

    // ==================== 额度 ====================

    /**
     * 一次互动开始时计算本次可用的客服消息额度。
     *
     * @param account 配置名
     * @param cfg     公众号配置
     * @param openid  用户
     * @return 本次可下发的客服消息条数
     */
    public int beginInteraction(String account, WechatProperties.OfficialAccountConfig cfg, String openid) {
        int limit = cfg == null ? 5 : Math.max(0, cfg.getCustomerServiceReplyLimit());
        boolean reset = cfg == null || cfg.isReplyQuotaResetPerInteraction();
        if (store == null || openid == null) {
            return limit;
        }
        String key = key(account, openid);
        if (reset) {
            store.put(key, limit, windowSeconds(cfg));
            return limit;
        }
        Integer remaining = read(key);
        if (remaining == null) {
            store.put(key, limit, windowSeconds(cfg));
            return limit;
        }
        return Math.max(0, remaining);
    }

    /**
     * 查询某用户当前剩余客服消息额度（未记录过时返回配置的默认额度）。
     *
     * @param account 配置名
     * @param openid  用户
     * @return 剩余额度
     */
    public int remaining(String account, String openid) {
        WechatProperties.OfficialAccountConfig cfg = accountConfig(account);
        int limit = cfg == null ? 5 : Math.max(0, cfg.getCustomerServiceReplyLimit());
        if (store == null || openid == null) {
            return limit;
        }
        Integer remaining = read(key(account, openid));
        return remaining == null ? limit : Math.max(0, remaining);
    }

    /**
     * 主动消耗额度（业务自行用 {@code sendCustomerMessage} 连发时也该记账）。
     *
     * @param account 配置名
     * @param openid  用户
     * @param slots   消耗条数
     */
    public void consume(String account, String openid, int slots) {
        if (store == null || openid == null || slots <= 0) {
            return;
        }
        String key = key(account, openid);
        Integer remaining = read(key);
        int base = remaining == null ? remainingDefault(account) : remaining;
        store.put(key, Math.max(0, base - slots), windowSeconds(accountConfig(account)));
    }

    /**
     * 清零额度（微信已明确拒绝时调用，自动止血）。
     *
     * @param account 配置名
     * @param openid  用户
     */
    public void exhaust(String account, String openid) {
        if (store == null || openid == null) {
            return;
        }
        store.put(key(account, openid), 0, windowSeconds(accountConfig(account)));
    }

    /**
     * 重置为配置额度（例如收到新的用户消息、或压测时手动复位）。
     *
     * @param account 配置名
     * @param openid  用户
     * @return 重置后的额度
     */
    public int reset(String account, String openid) {
        WechatProperties.OfficialAccountConfig cfg = accountConfig(account);
        int limit = cfg == null ? 5 : Math.max(0, cfg.getCustomerServiceReplyLimit());
        if (store != null && openid != null) {
            store.put(key(account, openid), limit, windowSeconds(cfg));
        }
        return limit;
    }

    // ==================== 下发 ====================

    /**
     * 按拆分计划异步补发客服消息（同步部分立刻返回，不占用 5 秒窗口）。
     *
     * @param account 配置名
     * @param openid  接收者
     * @param plan    拆分计划
     */
    public void dispatch(String account, String openid, ReplyPlan plan) {
        if (plan == null || plan.customerMessages().isEmpty()) {
            logDropped(account, openid, plan);
            return;
        }
        List<Message> messages = plan.customerMessages();
        consume(account, openid, messages.size());
        logDropped(account, openid, plan);
        Runnable task = () -> sendAll(account, openid, messages);
        try {
            executor().execute(task);
        } catch (RuntimeException e) {
            logger.warn("[wechat-reply] 异步执行器不可用，退化为当前线程发送: {}", e.getMessage());
            sendAll(account, openid, messages);
        }
    }

    private void sendAll(String account, String openid, List<Message> messages) {
        int index = 0;
        for (Message message : messages) {
            index++;
            message.toUser(openid);
            try {
                WeChatResponse resp = sender.send(account, message);
                int errcode = resp.getErrcode();
                if (resp.isSuccess()) {
                    logger.info("[wechat-reply] 补发第 {} 条成功: account={}, openid={}, type={}",
                            index, account, openid, message.getType());
                    continue;
                }
                logger.warn("[wechat-reply] 补发第 {} 条被拒: errcode={}, errmsg={}",
                        index, errcode, resp.getErrmsg());
                if (errcode == ERRCODE_OUT_OF_RESPONSE_COUNT || errcode == ERRCODE_RESPONSE_OUT_OF_TIME) {
                    exhaust(account, openid);
                    logger.warn("[wechat-reply] 用户 {} 的客服消息额度已清零，停止后续 {} 条补发",
                            openid, messages.size() - index);
                    return;
                }
            } catch (RuntimeException e) {
                logger.error("[wechat-reply] 补发第 {} 条异常（继续发后面的）: {}", index, e.getMessage());
            }
        }
    }

    private void logDropped(String account, String openid, ReplyPlan plan) {
        if (plan != null && !plan.dropped().isEmpty()) {
            logger.warn("[wechat-reply] 额度不足，丢弃 {} 条消息: account={}, openid={}（可下调业务消息数或调大 "
                    + "customer-service-reply-limit）", plan.dropped().size(), account, openid);
        }
    }

    private Executor executor() {
        Executor current = executor;
        if (current == null) {
            synchronized (this) {
                current = executor;
                if (current == null) {
                    current = Executors.newSingleThreadExecutor(runnable -> {
                        Thread thread = new Thread(runnable,
                                "wechat-reply-dispatch-" + threadSeq.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    });
                    executor = current;
                }
            }
        }
        return current;
    }

    private int remainingDefault(String account) {
        WechatProperties.OfficialAccountConfig cfg = accountConfig(account);
        return cfg == null ? 5 : Math.max(0, cfg.getCustomerServiceReplyLimit());
    }

    private WechatProperties.OfficialAccountConfig accountConfig(String account) {
        if (properties == null) {
            return null;
        }
        return properties.getOfficialAccounts().get(account);
    }

    private static long windowSeconds(WechatProperties.OfficialAccountConfig cfg) {
        long window = cfg == null ? 48 * 60 * 60L : cfg.getReplyQuotaWindowSeconds();
        return window <= 0 ? 48 * 60 * 60L : window;
    }

    private Integer read(String key) {
        Object raw = store.get(key);
        return raw instanceof Number number ? number.intValue() : null;
    }

    private static String key(String account, String openid) {
        return KEY_PREFIX + (account == null ? "default" : account) + ":" + openid;
    }
}