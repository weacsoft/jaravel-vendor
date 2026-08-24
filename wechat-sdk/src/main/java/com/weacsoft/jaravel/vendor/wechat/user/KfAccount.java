package com.weacsoft.jaravel.vendor.wechat.user;

import java.util.Map;

/**
 * 客服人员（{@code custom/service/kf_account/list} 响应单条）。
 * <p>
 * kflist 中每个客服账号包含 {@code kf_account}（客服账号，通常以 @corp 或邮箱形式）与
 * {@code kf_id}（客服 id）。
 *
 * @author weacsoft
 */
public final class KfAccount {

    private final String kfAccount;
    private final String kfId;
    private final String name;
    private final String email;

    private KfAccount(String kfAccount, String kfId, String name, String email) {
        this.kfAccount = kfAccount;
        this.kfId = kfId;
        this.name = name;
        this.email = email;
    }

    /**
     * 从原始节点构建（兼容 kf_account/kf_id 与 name/email 字段）。
     *
     * @param raw 客服节点
     * @return 客服对象
     */
    public static KfAccount from(Map<String, Object> raw) {
        String kfAccount = str(raw.get("kf_account"));
        String kfId = str(raw.get("kf_id"));
        String name = str(raw.get("name"));
        String email = str(raw.get("email"));
        return new KfAccount(kfAccount, kfId, name, email);
    }

    public String getKfAccount() {
        return kfAccount;
    }

    public String getKfId() {
        return kfId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    private static String str(Object value) {
        return value instanceof String s ? s : (value != null ? String.valueOf(value) : null);
    }

    @Override
    public String toString() {
        return "KfAccount{kfAccount=" + kfAccount + ", kfId=" + kfId + "}";
    }
}
