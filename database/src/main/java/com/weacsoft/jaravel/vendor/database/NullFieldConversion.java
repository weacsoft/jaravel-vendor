package com.weacsoft.jaravel.vendor.database;

import gaarason.database.contract.support.FieldConversion;

import java.lang.reflect.Field;
import java.sql.ResultSet;

/**
 * 自定义字段转换器：始终返回 null，用于排除 gaarason 内部字段（如 modelShadow）的 ORM 映射。
 * <p>
 * gaarason 的 {@code ModelBase.modelShadow} 字段未标注 {@code @Column(inDatabase=false)}，
 * 导致即使子类用字段隐藏（field hiding）方式覆盖并标注 {@code @Column(inDatabase=false)}，
 * 父类字段仍会被 {@code EntityMember.primitiveFieldDeal()} 扫描并加入 {@code columnFieldMap}
 * 与 {@code selectColumnList}，使得 {@code model_shadow} 列被 SELECT。
 * <p>
 * 同时，{@code javaFieldMap} 使用子类的字段（带 @Column 注解），反序列化时默认使用
 * {@code FieldConversion.Auto}（对复杂类型解析为 {@code JsonConversion}），尝试将
 * 数据库值反序列化为 {@code ModelShadowProvider} 实例，因无默认构造器而失败。
 * <p>
 * 本转换器在 {@code serialize}、{@code deserialize}、{@code acquisition} 三个环节均返回 null，
 * 彻底绕过 JsonConversion 对 {@code ModelShadowProvider} 类型的反序列化。
 */
public class NullFieldConversion implements FieldConversion<Object, Object> {

    @Override
    public Object serialize(Field field, Object fieldValue) {
        return null;
    }

    @Override
    public Object acquisition(Field field, ResultSet resultSet, String columnName) {
        return null;
    }

    @Override
    public Object deserialize(Field field, Object originalValue) {
        return null;
    }
}
