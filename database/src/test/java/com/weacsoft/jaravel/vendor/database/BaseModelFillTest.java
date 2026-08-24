package com.weacsoft.jaravel.vendor.database;

import gaarason.database.annotation.Column;
import gaarason.database.annotation.Primary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link BaseModel#fill(Map)} 简易填充方法测试。
 * 覆盖：类型转换、跳过 null/空值、忽略多余属性、链式返回。
 */
class BaseModelFillTest {

    public static class FillUser extends BaseModel<FillUser, Long> {
        @Primary
        @Column(name = "id")
        private Long id;

        @Column(name = "username")
        private String username;

        @Column(name = "age")
        private Integer age;

        @Column(name = "score")
        private Double score;

        @Column(name = "active")
        private Boolean active;

        @Column(name = "balance")
        private BigDecimal balance;

        @Column(name = "birthday")
        private LocalDate birthday;

        @Column(name = "login_at")
        private LocalDateTime loginAt;

        @Column(name = "nickname")
        private String nickname;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }

        public Double getScore() {
            return score;
        }

        public void setScore(Double score) {
            this.score = score;
        }

        public Boolean getActive() {
            return active;
        }

        public void setActive(Boolean active) {
            this.active = active;
        }

        public BigDecimal getBalance() {
            return balance;
        }

        public void setBalance(BigDecimal balance) {
            this.balance = balance;
        }

        public LocalDate getBirthday() {
            return birthday;
        }

        public void setBirthday(LocalDate birthday) {
            this.birthday = birthday;
        }

        public LocalDateTime getLoginAt() {
            return loginAt;
        }

        public void setLoginAt(LocalDateTime loginAt) {
            this.loginAt = loginAt;
        }

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }
    }

    @Test
    void testFillConvertsTypes() {
        FillUser u = new FillUser();
        Map<String, Object> data = new HashMap<>();
        data.put("username", "alice");
        data.put("age", "18");
        data.put("score", "99.5");
        data.put("active", "true");
        data.put("balance", "12.34");
        data.put("birthday", "2000-01-01");
        data.put("loginAt", "2024-05-20 12:30:00");

        FillUser result = u.fill(data);

        assertEquals("alice", u.getUsername());
        assertEquals(18, u.getAge());
        assertEquals(99.5, u.getScore());
        assertEquals(true, u.getActive());
        assertEquals(new BigDecimal("12.34"), u.getBalance());
        assertEquals(LocalDate.parse("2000-01-01"), u.getBirthday());
        assertEquals(LocalDateTime.parse("2024-05-20T12:30:00"), u.getLoginAt());
        // 链式返回自身
        assertEquals(u, result);
    }

    @Test
    void testFillSkipsNullAndEmptyValues() {
        FillUser u = new FillUser();
        u.setUsername("original");
        u.setAge(1);

        Map<String, Object> data = new HashMap<>();
        data.put("username", null);     // null 值 → 不做任何操作
        data.put("age", "");            // 空字符串值 → 不做任何操作
        data.put("nickname", "neo");    // 正常填充

        u.fill(data);

        // 原值保持不变（null/空字符串未覆盖）
        assertEquals("original", u.getUsername());
        assertEquals(1, u.getAge());
        assertEquals("neo", u.getNickname());
    }

    @Test
    void testFillIgnoresExtraPropertiesAndNullKeys() {
        FillUser u = new FillUser();

        Map<String, Object> data = new HashMap<>();
        data.put("notAField", "x");  // 多余属性（无对应 setter）→ 忽略
        data.put(null, "y");         // 键为 null → 忽略

        // 不应抛异常
        u.fill(data);
        assertNull(u.getNickname());
    }

    @Test
    void testFillNullMapReturnsThis() {
        FillUser u = new FillUser();
        assertEquals(u, u.fill(null));
    }
}
