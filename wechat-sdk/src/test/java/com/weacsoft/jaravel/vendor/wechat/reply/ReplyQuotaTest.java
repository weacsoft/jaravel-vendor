package com.weacsoft.jaravel.vendor.wechat.reply;

import com.weacsoft.jaravel.vendor.cache.CacheManager;
import com.weacsoft.jaravel.vendor.wechat.WechatProperties;
import com.weacsoft.jaravel.vendor.wechat.message.MiniProgramPage;
import com.weacsoft.jaravel.vendor.wechat.message.Text;
import com.weacsoft.jaravel.vendor.wechat.response.WeChatResponse;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回复额度软限制与自动拆分的单测（不联网：MessageSender 用假实现，执行器用直跑）。
 */
class ReplyQuotaTest {

    private static WechatProperties.OfficialAccountConfig cfg() {
        WechatProperties.OfficialAccountConfig cfg = new WechatProperties.OfficialAccountConfig();
        cfg.setAppId("wxquota0000000001");
        cfg.setSecret("s");
        cfg.setToken("t");
        cfg.setMessageMode("plain");
        return cfg;
    }

    private static WechatProperties props(WechatProperties.OfficialAccountConfig cfg) {
        WechatProperties props = new WechatProperties();
        props.getOfficialAccounts().put("default", cfg);
        return props;
    }

    private static List<Text> texts(int n) {
        List<Text> list = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            list.add(new Text("第" + i + "条"));
        }
        return list;
    }

    private static WeChatResponse ok() {
        return WeChatResponse.of(Map.of("errcode", 0, "errmsg", "ok"));
    }

    private static WeChatResponse err(int code) {
        return WeChatResponse.of(Map.of("errcode", code, "errmsg", "rejected"));
    }

    // ==================== ReplyPlan ====================

    @Test
    void splitsFirstAsPassiveAndRestAsCustomerMessages() {
        ReplyPlan plan = ReplyPlan.plan(cfg(), List.copyOf(texts(7)), 5);
        assertEquals("text", plan.passiveReply().getType(), "第 1 条应作为被动回复");
        assertEquals(5, plan.customerMessages().size(), "客服消息受 5 条额度限制");
        assertEquals(1, plan.dropped().size(), "第 7 条应被溢出丢弃");
    }

    @Test
    void honoursRemainingSlotsBelowConfiguredLimit() {
        ReplyPlan plan = ReplyPlan.plan(cfg(), List.copyOf(texts(7)), 2);
        assertEquals(2, plan.customerMessages().size(), "剩余额度只有 2 时只补发 2 条");
        assertEquals(4, plan.dropped().size());
    }

    @Test
    void messageTypesWithoutPassiveSupportFallToCustomerChannel() {
        MiniProgramPage card = new MiniProgramPage("标题", "wxapp0000000001",
                "pages/index", "THUMB_MEDIA");
        ReplyPlan plan = ReplyPlan.plan(cfg(), List.of(card, new Text("第二部")), 5);
        assertNull(plan.passiveReply(), "第 1 条不支持被动回复时不做被动回复（不把后面的消息提到前面）");
        assertEquals(2, plan.customerMessages().size(), "两条都应按原顺序走客服消息");
        assertEquals("miniprogrampage", plan.customerMessages().get(0).getType(), "顺序不能被打乱");
    }

    @Test
    void mergePolicyCollapsesOverflowIntoOneText() {
        WechatProperties.OfficialAccountConfig cfg = cfg();
        cfg.setReplyOverflowPolicy("merge");
        ReplyPlan plan = ReplyPlan.plan(cfg, List.copyOf(texts(7)), 5);
        assertEquals(5, plan.customerMessages().size(), "额度 5 条不变，溢出内容并入最后一条");
        assertTrue(plan.dropped().isEmpty(), "merge 策略下不应丢内容");
        Text merged = (Text) plan.customerMessages().get(4);
        assertTrue(merged.getContent().contains("第6条") && merged.getContent().contains("第7条"),
                "溢出内容应被追加到最后一条文本里");
    }

    @Test
    void passiveLimitZeroSendsEverythingAsCustomerMessages() {
        WechatProperties.OfficialAccountConfig cfg = cfg();
        cfg.setPassiveReplyLimit(0);
        ReplyPlan plan = ReplyPlan.plan(cfg, List.copyOf(texts(3)), 5);
        assertNull(plan.passiveReply(), "passive-reply-limit=0 时不占用被动回复");
        assertEquals(3, plan.customerMessages().size());
    }

    @Test
    void emptyMessagesPlanIsPassiveOnlyNoOp() {
        ReplyPlan plan = ReplyPlan.plan(cfg(), List.of(), 5);
        assertTrue(plan.passiveOnly());
        assertFalse(plan.passiveReply() != null);
    }

    // ==================== Dispatcher ====================

    @Test
    void dispatcherSendsWithinQuotaAndDecrementsIt() {
        List<com.weacsoft.jaravel.vendor.wechat.message.Message> sent = new ArrayList<>();
        WechatProperties.OfficialAccountConfig cfg = cfg();
        AsyncReplyDispatcher dispatcher = new AsyncReplyDispatcher(
                (account, message) -> {
                    sent.add(message);
                    return ok();
                },
                CacheManager.createDefaultStore(), props(cfg));
        dispatcher.withExecutor(Runnable::run);

        int slots = dispatcher.beginInteraction("default", cfg, "oUser1");
        assertEquals(5, slots, "默认额度应为 5");
        ReplyPlan plan = ReplyPlan.plan(cfg, List.copyOf(texts(7)), slots);
        dispatcher.dispatch("default", "oUser1", plan);

        assertEquals(5, sent.size(), "只应补发 5 条");
        assertEquals(0, dispatcher.remaining("default", "oUser1"), "额度应被扣完");
        assertEquals("oUser1", ((Text) sent.get(0)).getUser(), "touser 应由调度器填好");
    }

    @Test
    void dispatcherStopsAndExhaustsQuotaWhenWechatRejects45047() {
        List<String> attempts = new ArrayList<>();
        WechatProperties.OfficialAccountConfig cfg = cfg();
        AsyncReplyDispatcher dispatcher = new AsyncReplyDispatcher((account, message) -> {
            attempts.add(((Text) message).getContent());
            // 第 2 条开始模拟微信返回「下行条数超上限」
            return attempts.size() >= 2 ? err(45047) : ok();
        }, CacheManager.createDefaultStore(), props(cfg));
        dispatcher.withExecutor(Runnable::run);

        ReplyPlan plan = ReplyPlan.plan(cfg, List.copyOf(texts(5)), dispatcher.beginInteraction("default", cfg, "oUser2"));
        dispatcher.dispatch("default", "oUser2", plan);

        assertEquals(2, attempts.size(), "收到 45047 后应立即停止，不再刷接口");
        assertEquals(0, dispatcher.remaining("default", "oUser2"), "额度应被清零");
    }

    @Test
    void resetPerInteractionRestoresQuotaButAccumulateModeDoesNot() {
        WechatProperties.OfficialAccountConfig resetting = cfg();
        AsyncReplyDispatcher dispatcher = new AsyncReplyDispatcher(
                (account, message) -> ok(), CacheManager.createDefaultStore(), props(resetting));
        dispatcher.withExecutor(Runnable::run);

        dispatcher.beginInteraction("default", resetting, "oA");
        dispatcher.dispatch("default", "oA", ReplyPlan.plan(resetting, List.copyOf(texts(6)), 5));
        assertEquals(0, dispatcher.remaining("default", "oA"), "首次互动后额度耗尽");
        assertEquals(5, dispatcher.beginInteraction("default", resetting, "oA"),
                "reset-per-interaction=true：用户再次互动应恢复额度");

        WechatProperties.OfficialAccountConfig accumulate = cfg();
        accumulate.setReplyQuotaResetPerInteraction(false);
        AsyncReplyDispatcher accumulateDispatcher = new AsyncReplyDispatcher(
                (account, message) -> ok(), CacheManager.createDefaultStore(), props(accumulate));
        accumulateDispatcher.withExecutor(Runnable::run);
        accumulateDispatcher.beginInteraction("default", accumulate, "oB");
        accumulateDispatcher.dispatch("default", "oB", ReplyPlan.plan(accumulate, List.copyOf(texts(6)), 5));
        assertEquals(0, accumulateDispatcher.remaining("default", "oB"));
        assertEquals(0, accumulateDispatcher.beginInteraction("default", accumulate, "oB"),
                "累计模式：新消息不恢复额度");
    }
}