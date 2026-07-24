package com.weacsoft.jaravel.vendor.utils.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link IpMatcher} 的单元测试。
 * <p>
 * 覆盖三种表达式格式（单 IP / CIDR / 范围）、IPv4 + IPv6、混合规则、边界值、异常输入。
 */
class IpMatcherTest {

    // ==================== 单 IP（IPv4） ====================

    @Nested
    @DisplayName("单个 IPv4")
    class SingleIpv4 {

        @Test
        @DisplayName("精确匹配")
        void testExactMatch() {
            IpMatcher m = new IpMatcher("127.0.0.1");
            assertTrue(m.matches("127.0.0.1"));
            assertFalse(m.matches("127.0.0.2"));
            assertFalse(m.matches("10.0.0.1"));
        }

        @Test
        @DisplayName("0.0.0.0 匹配")
        void testAllZeros() {
            IpMatcher m = new IpMatcher("0.0.0.0");
            assertTrue(m.matches("0.0.0.0"));
            assertFalse(m.matches("0.0.0.1"));
        }

        @Test
        @DisplayName("255.255.255.255 匹配")
        void testBroadcast() {
            IpMatcher m = new IpMatcher("255.255.255.255");
            assertTrue(m.matches("255.255.255.255"));
            assertFalse(m.matches("255.255.255.254"));
        }
    }

    // ==================== 单 IP（IPv6） ====================

    @Nested
    @DisplayName("单个 IPv6")
    class SingleIpv6 {

        @Test
        @DisplayName("::1 匹配（缩写与完整写法等价）")
        void testLoopback() {
            IpMatcher m = new IpMatcher("::1");
            assertTrue(m.matches("::1"));
            assertTrue(m.matches("0:0:0:0:0:0:0:1"));
            assertFalse(m.matches("::2"));
            assertFalse(m.matches("0:0:0:0:0:0:0:2"));
        }

        @Test
        @DisplayName("完整 IPv6 地址匹配")
        void testFullIpv6() {
            IpMatcher m = new IpMatcher("2001:db8::1");
            assertTrue(m.matches("2001:db8::1"));
            assertTrue(m.matches("2001:0db8:0000:0000:0000:0000:0000:0001"));
            assertFalse(m.matches("2001:db8::2"));
        }
    }

    // ==================== CIDR（IPv4） ====================

    @Nested
    @DisplayName("CIDR IPv4")
    class CidrIpv4 {

        @Test
        @DisplayName("/24 子网")
        void test24() {
            IpMatcher m = new IpMatcher("192.168.1.0/24");
            assertTrue(m.matches("192.168.1.0"));
            assertTrue(m.matches("192.168.1.1"));
            assertTrue(m.matches("192.168.1.255"));
            assertFalse(m.matches("192.168.0.255"));
            assertFalse(m.matches("192.168.2.0"));
        }

        @Test
        @DisplayName("/23 子网（跨越 .146.x 和 .147.x）")
        void test23() {
            IpMatcher m = new IpMatcher("120.236.146.0/23");
            // /23 覆盖 120.236.146.0 ~ 120.236.147.255
            assertTrue(m.matches("120.236.146.0"));
            assertTrue(m.matches("120.236.146.255"));
            assertTrue(m.matches("120.236.147.0"));
            assertTrue(m.matches("120.236.147.255"));
            assertFalse(m.matches("120.236.145.255"));
            assertFalse(m.matches("120.236.148.0"));
        }

        @Test
        @DisplayName("/8 大网段")
        void test8() {
            IpMatcher m = new IpMatcher("10.0.0.0/8");
            assertTrue(m.matches("10.0.0.0"));
            assertTrue(m.matches("10.255.255.255"));
            assertTrue(m.matches("10.5.5.5"));
            assertFalse(m.matches("11.0.0.0"));
            assertFalse(m.matches("9.255.255.255"));
        }

        @Test
        @DisplayName("/32 等价于单 IP")
        void test32() {
            IpMatcher m = new IpMatcher("10.0.0.1/32");
            assertTrue(m.matches("10.0.0.1"));
            assertFalse(m.matches("10.0.0.2"));
        }

        @Test
        @DisplayName("/0 匹配所有 IPv4")
        void test0() {
            IpMatcher m = new IpMatcher("0.0.0.0/0");
            assertTrue(m.matches("0.0.0.0"));
            assertTrue(m.matches("127.0.0.1"));
            assertTrue(m.matches("255.255.255.255"));
            // /0 仅匹配 IPv4，不匹配 IPv6
            assertFalse(m.matches("::1"));
        }

        @Test
        @DisplayName("/16 非对齐网络地址自动修正")
        void testNonAlignedNetwork() {
            // 192.168.1.3/24 等价于 192.168.1.0/24
            IpMatcher m = new IpMatcher("192.168.1.3/24");
            assertTrue(m.matches("192.168.1.0"));
            assertTrue(m.matches("192.168.1.255"));
            assertFalse(m.matches("192.168.2.0"));
        }
    }

    // ==================== CIDR（IPv6） ====================

    @Nested
    @DisplayName("CIDR IPv6")
    class CidrIpv6 {

        @Test
        @DisplayName("/32 子网")
        void test32() {
            IpMatcher m = new IpMatcher("2001:db8::/32");
            assertTrue(m.matches("2001:db8::1"));
            assertTrue(m.matches("2001:db8:ffff:ffff:ffff:ffff:ffff:ffff"));
            assertFalse(m.matches("2001:db9::1"));
        }

        @Test
        @DisplayName("/128 等价于单 IP")
        void test128() {
            IpMatcher m = new IpMatcher("2001:db8::1/128");
            assertTrue(m.matches("2001:db8::1"));
            assertFalse(m.matches("2001:db8::2"));
        }
    }

    // ==================== IP 范围 ====================

    @Nested
    @DisplayName("IP 范围")
    class Range {

        @Test
        @DisplayName("IPv4 范围匹配")
        void testIpv4Range() {
            IpMatcher m = new IpMatcher("192.168.1.10-192.168.1.20");
            assertTrue(m.matches("192.168.1.10"));
            assertTrue(m.matches("192.168.1.15"));
            assertTrue(m.matches("192.168.1.20"));
            assertFalse(m.matches("192.168.1.9"));
            assertFalse(m.matches("192.168.1.21"));
        }

        @Test
        @DisplayName("IPv4 跨段范围")
        void testIpv4CrossSubnetRange() {
            IpMatcher m = new IpMatcher("10.0.0.250-10.0.2.5");
            assertTrue(m.matches("10.0.0.250"));
            assertTrue(m.matches("10.0.1.0"));
            assertTrue(m.matches("10.0.1.255"));
            assertTrue(m.matches("10.0.2.5"));
            assertFalse(m.matches("10.0.0.249"));
            assertFalse(m.matches("10.0.2.6"));
        }

        @Test
        @DisplayName("0.0.0.0-1.1.1.1 范围")
        void testLargeRange() {
            IpMatcher m = new IpMatcher("0.0.0.0-1.1.1.1");
            assertTrue(m.matches("0.0.0.0"));
            assertTrue(m.matches("1.1.1.1"));
            assertTrue(m.matches("0.0.0.1"));
            assertTrue(m.matches("1.0.0.0"));
            assertFalse(m.matches("1.1.1.2"));
            assertFalse(m.matches("2.0.0.0"));
        }

        @Test
        @DisplayName("反序范围自动修正（end < start 时交换）")
        void testReversedRange() {
            IpMatcher m = new IpMatcher("192.168.1.20-192.168.1.10");
            assertTrue(m.matches("192.168.1.10"));
            assertTrue(m.matches("192.168.1.15"));
            assertTrue(m.matches("192.168.1.20"));
            assertFalse(m.matches("192.168.1.9"));
        }

        @Test
        @DisplayName("IPv6 范围匹配")
        void testIpv6Range() {
            IpMatcher m = new IpMatcher("2001:db8::-2001:db8::ffff");
            assertTrue(m.matches("2001:db8::1"));
            assertTrue(m.matches("2001:db8::ffff"));
            assertFalse(m.matches("2001:db8::1:0"));
        }
    }

    // ==================== 混合规则 ====================

    @Nested
    @DisplayName("混合多规则")
    class Mixed {

        @Test
        @DisplayName("单 IP + CIDR + 范围 + IPv6 混合")
        void testMixedRules() {
            IpMatcher m = new IpMatcher(
                "127.0.0.1",
                "10.0.0.0/8",
                "192.168.1.0-192.168.1.100",
                "::1"
            );
            // 单 IP
            assertTrue(m.matches("127.0.0.1"));
            assertFalse(m.matches("127.0.0.2"));
            // CIDR
            assertTrue(m.matches("10.5.5.5"));
            assertTrue(m.matches("10.255.255.255"));
            assertFalse(m.matches("11.0.0.0"));
            // 范围
            assertTrue(m.matches("192.168.1.50"));
            assertFalse(m.matches("192.168.1.200"));
            // IPv6
            assertTrue(m.matches("::1"));
            assertFalse(m.matches("::2"));
        }

        @Test
        @DisplayName("List 构造")
        void testListConstructor() {
            IpMatcher m = new IpMatcher(Arrays.asList("192.168.1.0/24", "10.0.0.1"));
            assertTrue(m.matches("192.168.1.100"));
            assertTrue(m.matches("10.0.0.1"));
            assertFalse(m.matches("172.16.0.1"));
        }
    }

    // ==================== 边界与异常 ====================

    @Nested
    @DisplayName("边界与异常")
    class EdgeCases {

        @Test
        @DisplayName("空匹配器不匹配任何 IP")
        void testEmpty() {
            IpMatcher m = new IpMatcher();
            assertTrue(m.isEmpty());
            assertFalse(m.matches("127.0.0.1"));
        }

        @Test
        @DisplayName("null / 空字符串集合不匹配")
        void testNullAndEmpty() {
            IpMatcher m = new IpMatcher((String[]) null);
            assertTrue(m.isEmpty());

            IpMatcher m2 = new IpMatcher("");
            assertTrue(m2.isEmpty());

            IpMatcher m3 = new IpMatcher(Collections.singletonList(null));
            assertTrue(m3.isEmpty());
        }

        @Test
        @DisplayName("matches(null) 和 matches(\"\") 返回 false")
        void testMatchesNull() {
            IpMatcher m = new IpMatcher("127.0.0.1");
            assertFalse(m.matches(null));
            assertFalse(m.matches(""));
        }

        @Test
        @DisplayName("无效 IP 返回 false 而非抛异常")
        void testInvalidIp() {
            IpMatcher m = new IpMatcher("127.0.0.1");
            assertFalse(m.matches("not-an-ip"));
            assertFalse(m.matches("999.999.999.999"));
        }

        @Test
        @DisplayName("无效表达式构造时抛异常")
        void testInvalidExpression() {
            assertThrows(IllegalArgumentException.class, () -> new IpMatcher("not-an-ip"));
            assertThrows(IllegalArgumentException.class, () -> new IpMatcher("192.168.1.0/33"));
            assertThrows(IllegalArgumentException.class, () -> new IpMatcher("192.168.1.0/notanumber"));
            assertThrows(IllegalArgumentException.class, () -> new IpMatcher("192.168.1.0-not-an-ip"));
            assertThrows(IllegalArgumentException.class, () -> new IpMatcher("192.168.1.0-::1"));
        }

        @Test
        @DisplayName("IPv4 规则不匹配 IPv6 地址（反之亦然）")
        void testVersionIsolation() {
            IpMatcher m = new IpMatcher("127.0.0.1");
            assertFalse(m.matches("::1"));

            IpMatcher m2 = new IpMatcher("::1");
            assertFalse(m2.matches("127.0.0.1"));
        }

        @Test
        @DisplayName("带空格的表达式自动 trim")
        void testTrim() {
            IpMatcher m = new IpMatcher("  127.0.0.1  ");
            assertTrue(m.matches("127.0.0.1"));

            IpMatcher m2 = new IpMatcher("  192.168.1.0/24  ");
            assertTrue(m2.matches("192.168.1.100"));
        }
    }
}
