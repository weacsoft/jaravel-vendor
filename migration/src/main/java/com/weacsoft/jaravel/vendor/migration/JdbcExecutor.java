package com.weacsoft.jaravel.vendor.migration;

import javax.sql.DataSource;

/**
 * 迁移模块 JDBC 执行器（兼容层）。
 * <p>
 * <b>已迁移</b>：统一 SQL 执行底座移至 database 模块
 * {@code com.weacsoft.jaravel.vendor.database.JdbcExecutor}
 * （数据库操作统一经由 database 模块执行）。
 * 本类保留为等价子类，使既有迁移引擎代码与外部引用无需改动；
 * 新代码请直接使用 database 模块的执行器。
 *
 * @deprecated 请使用 {@code com.weacsoft.jaravel.vendor.database.JdbcExecutor}
 */
@Deprecated
public class JdbcExecutor extends com.weacsoft.jaravel.vendor.database.JdbcExecutor {

    /**
     * 构造 JDBC 执行器（等价于 database 模块同构造签名）。
     *
     * @param dataSource 数据源
     */
    public JdbcExecutor(DataSource dataSource) {
        super(dataSource);
    }
}
