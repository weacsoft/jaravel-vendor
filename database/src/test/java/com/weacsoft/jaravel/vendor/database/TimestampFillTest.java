package com.weacsoft.jaravel.vendor.database;

import gaarason.database.contract.support.FieldFill;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link TimestampFill} 及其内部填充器的测试。
 * <p>
 * 验证：
 * <ul>
 *   <li>{@link TimestampFill#nowString()} 返回正确格式的本地时间字符串</li>
 *   <li>{@link TimestampFill.CreatedTimeStringFill} 仅在插入时填充，更新时保留原值</li>
 *   <li>{@link TimestampFill.UpdatedTimeStringFill} 在插入和更新时均填充</li>
 *   <li>填充的时间格式为 {@code "yyyy-MM-dd HH:mm:ss"}</li>
 * </ul>
 */
class TimestampFillTest {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    @DisplayName("nowString() 返回 'yyyy-MM-dd HH:mm:ss' 格式的有效时间字符串")
    void testNowStringFormat() {
        String timeStr = TimestampFill.nowString();
        assertNotNull(timeStr);
        // 验证格式可被解析
        assertDoesNotThrow(() -> LocalDateTime.parse(timeStr, FORMATTER));

        // 验证与当前时间接近（允许 2 秒误差）
        LocalDateTime parsed = LocalDateTime.parse(timeStr, FORMATTER);
        LocalDateTime currentTime = LocalDateTime.now();
        assertTrue(parsed.isAfter(currentTime.minusSeconds(2)));
        assertTrue(parsed.isBefore(currentTime.plusSeconds(2)));
    }

    @Test
    @DisplayName("DEFAULT_FORMAT 常量正确")
    void testDefaultFormat() {
        assertEquals("yyyy-MM-dd HH:mm:ss", TimestampFill.DEFAULT_FORMAT);
    }

    @Test
    @DisplayName("CreatedTimeStringFill: inserting 返回当前时间字符串")
    void testCreatedTimeStringFillInserting() throws Exception {
        FieldFill fill = new TimestampFill.CreatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String result = fill.inserting(this, dummyField, "old_value");
        assertNotNull(result);
        assertDoesNotThrow(() -> LocalDateTime.parse(result, FORMATTER));
    }

    @Test
    @DisplayName("CreatedTimeStringFill: updating 保留原值")
    void testCreatedTimeStringFillUpdating() throws Exception {
        FieldFill fill = new TimestampFill.CreatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String original = "2023-04-28 16:06:33";
        String result = fill.updating(this, dummyField, original);
        assertEquals(original, result);
    }

    @Test
    @DisplayName("CreatedTimeStringFill: condition 保留原值")
    void testCreatedTimeStringFillCondition() throws Exception {
        FieldFill fill = new TimestampFill.CreatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String original = "2023-04-28 16:06:33";
        String result = fill.condition(this, dummyField, original);
        assertEquals(original, result);
    }

    @Test
    @DisplayName("UpdatedTimeStringFill: inserting 返回当前时间字符串")
    void testUpdatedTimeStringFillInserting() throws Exception {
        FieldFill fill = new TimestampFill.UpdatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String result = fill.inserting(this, dummyField, "old_value");
        assertNotNull(result);
        assertDoesNotThrow(() -> LocalDateTime.parse(result, FORMATTER));
    }

    @Test
    @DisplayName("UpdatedTimeStringFill: updating 返回当前时间字符串")
    void testUpdatedTimeStringFillUpdating() throws Exception {
        FieldFill fill = new TimestampFill.UpdatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String result = fill.updating(this, dummyField, "old_value");
        assertNotNull(result);
        assertDoesNotThrow(() -> LocalDateTime.parse(result, FORMATTER));
    }

    @Test
    @DisplayName("UpdatedTimeStringFill: condition 保留原值")
    void testUpdatedTimeStringFillCondition() throws Exception {
        FieldFill fill = new TimestampFill.UpdatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String original = "2023-04-28 16:06:33";
        String result = fill.condition(this, dummyField, original);
        assertEquals(original, result);
    }

    @Test
    @DisplayName("CreatedTimeStringFill 与 UpdatedTimeStringFill 的 inserting 返回不同时间戳（验证非缓存）")
    void testFillReturnsFreshTimestamp() throws Exception {
        FieldFill createdFill = new TimestampFill.CreatedTimeStringFill();
        Field dummyField = String.class.getDeclaredField("value");

        String time1 = createdFill.inserting(this, dummyField, null);
        // 等待一小段时间确保时间不同
        Thread.sleep(1100);
        String time2 = createdFill.inserting(this, dummyField, null);

        assertNotEquals(time1, time2, "两次调用应返回不同的时间戳");
    }
}
