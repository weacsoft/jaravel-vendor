package com.weacsoft.jaravel.vendor.captcha;

/**
 * 验证码验证结果（详细版）。
 * <p>
 * 区分三种情况：
 * <ul>
 *   <li>{@code passed=true} —— 验证通过</li>
 *   <li>{@code passed=false, alreadyUsed=false} —— 验证失败（答案错误/过期/解密失败），但验证码未被使用过</li>
 *   <li>{@code passed=false, alreadyUsed=true} —— 验证码已被使用（不可再次验证）</li>
 * </ul>
 */
public class VerifyResult {

    private final boolean passed;
    private final boolean alreadyUsed;

    private VerifyResult(boolean passed, boolean alreadyUsed) {
        this.passed = passed;
        this.alreadyUsed = alreadyUsed;
    }

    /** 验证通过 */
    public static VerifyResult pass() {
        return new VerifyResult(true, false);
    }

    /** 验证失败（答案错误等，验证码未被使用过） */
    public static VerifyResult fail() {
        return new VerifyResult(false, false);
    }

    /** 验证码已被使用（不可再次验证） */
    public static VerifyResult alreadyUsed() {
        return new VerifyResult(false, true);
    }

    /** 验证是否通过 */
    public boolean isPassed() {
        return passed;
    }

    /** 验证码是否已被使用（一次性消费后不可再用） */
    public boolean isAlreadyUsed() {
        return alreadyUsed;
    }

    @Override
    public String toString() {
        return "VerifyResult{passed=" + passed + ", alreadyUsed=" + alreadyUsed + "}";
    }
}
