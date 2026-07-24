package com.weacsoft.jaravel.vendor.utils.net;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * IP 地址匹配器，支持三种表达式格式：
 * <ul>
 *   <li><b>单个 IP</b>：{@code "127.0.0.1"}、{@code "::1"}</li>
 *   <li><b>CIDR 掩码</b>：{@code "120.236.146.0/23"}、{@code "2001:db8::/32"}</li>
 *   <li><b>IP 范围</b>：{@code "0.0.0.0-1.1.1.1"}（起止 IP 用 {@code -} 连接，两端必须同版本）</li>
 * </ul>
 * 同时支持 IPv4 和 IPv6。CIDR 和范围表达式中的两端 IP 必须为同一版本（不可混用 v4/v6）。
 * <p>
 * 线程安全：内部规则列表在构造后不可变，{@link #matches(String)} 可并发调用。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // 单个 IP
 * IpMatcher m1 = new IpMatcher("127.0.0.1");
 * m1.matches("127.0.0.1"); // true
 * m1.matches("10.0.0.1");  // false
 *
 * // CIDR
 * IpMatcher m2 = new IpMatcher("120.236.146.0/23");
 * m2.matches("120.236.146.255"); // true
 * m2.matches("120.236.148.0");   // false
 *
 * // 范围
 * IpMatcher m3 = new IpMatcher("0.0.0.0-1.1.1.1");
 * m3.matches("0.0.0.0"); // true
 * m3.matches("1.1.1.1"); // true
 * m3.matches("1.1.1.2"); // false
 *
 * // 混合多规则
 * IpMatcher m4 = new IpMatcher("127.0.0.1", "10.0.0.0/8", "192.168.1.0-192.168.1.100", "::1");
 * m4.matches("10.5.5.5");          // true（匹配 CIDR）
 * m4.matches("192.168.1.50");      // true（匹配范围）
 * m4.matches("192.168.1.200");     // false
 * m4.matches("0:0:0:0:0:0:0:1");  // true（::1 的完整写法）
 * }</pre>
 */
public final class IpMatcher {

    private final List<Rule> rules;

    /**
     * 用一组表达式构造匹配器。
     *
     * @param expressions IP 表达式数组，每项可为单 IP / CIDR / 范围
     */
    public IpMatcher(String... expressions) {
        this(expressions == null ? null : Arrays.asList(expressions));
    }

    /**
     * 用一组表达式构造匹配器。
     *
     * @param expressions IP 表达式集合，每项可为单 IP / CIDR / 范围
     */
    public IpMatcher(Collection<String> expressions) {
        this.rules = new ArrayList<>();
        if (expressions != null) {
            for (String expr : expressions) {
                if (expr != null) {
                    String trimmed = expr.trim();
                    if (!trimmed.isEmpty()) {
                        this.rules.add(parse(trimmed));
                    }
                }
            }
        }
    }

    /**
     * 判断给定 IP 是否匹配任一规则。
     *
     * @param ip IP 地址字符串（IPv4 或 IPv6）
     * @return 匹配返回 {@code true}，否则 {@code false}；解析失败也返回 {@code false}
     */
    public boolean matches(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(ip);
            byte[] bytes = addr.getAddress();
            for (Rule rule : rules) {
                if (rule.matches(bytes, addr instanceof Inet6Address)) {
                    return true;
                }
            }
        } catch (UnknownHostException e) {
            return false;
        }
        return false;
    }

    /**
     * 判断是否没有任何规则（空匹配器）。
     *
     * @return 无规则返回 {@code true}
     */
    public boolean isEmpty() {
        return rules.isEmpty();
    }

    // ==================== 内部解析逻辑 ====================

    /**
     * 解析单个表达式为 Rule。
     * <p>
     * 优先级：CIDR（含 {@code /}）> 范围（含 {@code -}）> 单 IP。
     */
    private static Rule parse(String expr) {
        // CIDR: 含 "/"
        int slashIdx = expr.indexOf('/');
        if (slashIdx > 0) {
            return parseCidr(expr, slashIdx);
        }
        // 范围: 含 "-"（注意 IPv6 不含 "-"，所以不会误判）
        int dashIdx = expr.indexOf('-');
        if (dashIdx > 0) {
            return parseRange(expr, dashIdx);
        }
        // 单 IP
        return parseSingle(expr);
    }

    /**
     * 解析 CIDR 表达式，如 "192.168.1.0/24" 或 "2001:db8::/32"。
     */
    private static Rule parseCidr(String expr, int slashIdx) {
        String ipPart = expr.substring(0, slashIdx).trim();
        String maskPart = expr.substring(slashIdx + 1).trim();
        byte[] ipBytes = toBytes(ipPart);
        if (ipBytes == null) {
            throw new IllegalArgumentException("无效的 CIDR IP: " + expr);
        }
        int mask;
        try {
            mask = Integer.parseInt(maskPart);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("无效的 CIDR 掩码: " + expr);
        }
        int maxBits = ipBytes.length * 8;
        if (mask < 0 || mask > maxBits) {
            throw new IllegalArgumentException(
                "CIDR 掩码超出范围 [0," + maxBits + "]: " + expr);
        }
        return new CidrRule(ipBytes, mask);
    }

    /**
     * 解析范围表达式，如 "0.0.0.0-1.1.1.1"。
     */
    private static Rule parseRange(String expr, int dashIdx) {
        String startIp = expr.substring(0, dashIdx).trim();
        String endIp = expr.substring(dashIdx + 1).trim();
        byte[] start = toBytes(startIp);
        byte[] end = toBytes(endIp);
        if (start == null) {
            throw new IllegalArgumentException("无效的范围起始 IP: " + expr);
        }
        if (end == null) {
            throw new IllegalArgumentException("无效的范围结束 IP: " + expr);
        }
        if (start.length != end.length) {
            throw new IllegalArgumentException(
                "范围两端 IP 版本不一致: " + expr);
        }
        // 确保 start <= end
        if (compareBytes(start, end) > 0) {
            byte[] tmp = start;
            start = end;
            end = tmp;
        }
        return new RangeRule(start, end);
    }

    /**
     * 解析单个 IP，如 "127.0.0.1" 或 "::1"。
     */
    private static Rule parseSingle(String expr) {
        byte[] bytes = toBytes(expr);
        if (bytes == null) {
            throw new IllegalArgumentException("无效的 IP 地址: " + expr);
        }
        // 单 IP 等价于掩码为全 1 的 CIDR
        return new CidrRule(bytes, bytes.length * 8);
    }

    /**
     * 将 IP 字符串转为字节数组，失败返回 null。
     */
    private static byte[] toBytes(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            return addr.getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    /**
     * 比较两个字节数组的大小（无符号逐字节比较）。
     *
     * @return 负数 a<b，0 相等，正数 a>b
     */
    private static int compareBytes(byte[] a, byte[] b) {
        for (int i = 0; i < a.length; i++) {
            int ai = a[i] & 0xFF;
            int bi = b[i] & 0xFF;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    // ==================== 规则接口与实现 ====================

    private interface Rule {
        boolean matches(byte[] addrBytes, boolean isIpv6);
    }

    /**
     * CIDR 规则：IP & mask == network & mask。
     */
    private static final class CidrRule implements Rule {
        private final byte[] network;
        private final byte[] mask;
        private final boolean isIpv6;

        CidrRule(byte[] ipBytes, int prefixBits) {
            this.isIpv6 = ipBytes.length == 16;
            this.mask = buildMask(ipBytes.length, prefixBits);
            // 对 IP 应用掩码，确保网络地址部分正确
            this.network = applyMask(ipBytes, this.mask);
        }

        @Override
        public boolean matches(byte[] addrBytes, boolean addrIsIpv6) {
            // IPv4 和 IPv6 不混
            if (isIpv6 != addrIsIpv6) {
                return false;
            }
            // 逐字节用掩码比较
            for (int i = 0; i < addrBytes.length; i++) {
                if ((addrBytes[i] & mask[i]) != network[i]) {
                    return false;
                }
            }
            return true;
        }

        private static byte[] buildMask(int byteLength, int prefixBits) {
            byte[] m = new byte[byteLength];
            int fullBytes = prefixBits / 8;
            int remainingBits = prefixBits % 8;
            for (int i = 0; i < fullBytes; i++) {
                m[i] = (byte) 0xFF;
            }
            if (remainingBits > 0 && fullBytes < byteLength) {
                m[fullBytes] = (byte) (0xFF << (8 - remainingBits));
            }
            return m;
        }

        private static byte[] applyMask(byte[] ip, byte[] mask) {
            byte[] result = new byte[ip.length];
            for (int i = 0; i < ip.length; i++) {
                result[i] = (byte) (ip[i] & mask[i]);
            }
            return result;
        }
    }

    /**
     * 范围规则：start <= addr <= end（无符号逐字节比较）。
     */
    private static final class RangeRule implements Rule {
        private final byte[] start;
        private final byte[] end;
        private final boolean isIpv6;

        RangeRule(byte[] start, byte[] end) {
            this.start = start;
            this.end = end;
            this.isIpv6 = start.length == 16;
        }

        @Override
        public boolean matches(byte[] addrBytes, boolean addrIsIpv6) {
            if (isIpv6 != addrIsIpv6) {
                return false;
            }
            return compareBytes(addrBytes, start) >= 0
                && compareBytes(addrBytes, end) <= 0;
        }
    }
}
