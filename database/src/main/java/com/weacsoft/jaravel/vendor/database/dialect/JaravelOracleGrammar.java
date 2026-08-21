package com.weacsoft.jaravel.vendor.database.dialect;

import gaarason.database.contract.query.Alias;
import gaarason.database.contract.query.Grammar.SQLPartInfo;
import gaarason.database.contract.query.Grammar.SQLPartType;
import gaarason.database.query.grammars.OracleGrammar;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * jaravel Oracle 方言（dialect name: <b>jaravel-oracle</b>）。
 * <p>
 * 基于 gaarason/database 7.0.15（未修改）的 {@link OracleGrammar} 做子类扩展，
 * 修复 Oracle「schema 限定表名」（如 {@code JW_USER.ZF_JWC_XSJBXXB}）场景下的两个 SQL 生成缺陷：
 * <ol>
 *   <li>ROWNUM 行数限制在已存在 WHERE 条件时缺少 {@code AND} 连接 → {@code ORA-00933}。
 *       本方言统一把 {@code ROWNUM <= ?} 并入 WHERE：无 WHERE 时作为唯一条件，
 *       有 WHERE 时以 {@code AND} 追加。</li>
 *   <li>限定表名被整体当一个标识符加引号（{@code "JW_USER.ZF_JWC_XSJBXXB"}），
 *       且自动别名 {@code 表名_hash} 也带 {@code .}，均为 Oracle 非法标识符。
 *       本方言对表名<b>逐段</b>加引号（{@code "JW_USER"."ZF_JWC_XSJBXXB"}），
 *       别名中的 {@code .} 替换为 {@code _} 并控制在 30 字符内（Oracle 11g 标识符上限）。</li>
 * </ol>
 * 单段表名（无 schema）行为与官方方言完全一致，不产生影响。
 * <p>
 * 配套类：{@link JaravelOracleQueryBuilderConfig}（方言路由）、
 * {@link JaravelOracleDialect}（注册入口）。
 *
 * @author weacsoft
 * @see JaravelOracleDialect#register(Object)
 */
public class JaravelOracleGrammar extends OracleGrammar {

    private static final long serialVersionUID = 1L;

    /**
     * @param tableName 表名，可为 schema 限定名（eg. {@code JW_USER.ZF_JWC_XSJBXXB}）
     */
    public JaravelOracleGrammar(String tableName) {
        super(tableName);
        // 官方实现的自动别名为「表名 + '_' + hash」：限定表名含 '.'，
        // 被引号包裹后仍是非法 Oracle 标识符（例如 "JW_USER.ZF_JWC_XSJBXXB_1842"）。
        // 这里重建 Alias（table 保留原始限定名，alias 变为单个合法标识符，
        // alias 与 aliasPlaceHolder 保持一致，保证 UPDATE/DELETE 的别名剥离逻辑正常）。
        this.alias = new Alias(tableName, buildSafeAlias(tableName));
    }

    /**
     * 单段表名逐段加引号，多段（schema.table）生成 "schema"."table" 形式。
     */
    private String quoteSegments(String name) {
        if (name == null || name.isEmpty()) {
            return symbol + symbol;
        }
        String[] parts = name.split("\\.", -1);
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) {
                sb.append('.');
            }
            sb.append(symbol).append(part).append(symbol);
        }
        return sb.toString();
    }

    /**
     * 生成单个合法 Oracle 标识符：去掉 '.'，长度上限 30 字符（Oracle 11g），
     * 保留表名前缀以便阅读，附加短 hash 避免同前缀表名冲突。
     */
    private static String buildSafeAlias(String tableName) {
        String base = tableName == null ? "t" : tableName.replace('.', '_');
        final int maxBase = 22; // 22 + 1 + 7(哈希) = 30，满足 Oracle 11g 标识符上限
        if (base.length() > maxBase) {
            base = base.substring(0, maxBase);
        }
        int hash = (base.hashCode() & 0x7fffffff) % 0xfffff;
        return base + "_" + Integer.toString(hash, 36);
    }

    /**
     * 覆盖默认表名/别名 SQL 片段，使限定表名逐段加引号、别名无点。
     * <pre>
     * from   → "JW_USER"."ZF_JWC_XSJBXXB" as "JW_USER_ZF_JWC_x9k2"
     * update → "JW_USER"."ZF_JWC_XSJBXXB"
     * </pre>
     */
    @Override
    protected List<SQLPartInfo> getDefault(SQLPartType type) {
        if (type == SQLPartType.TABLE) {
            return Collections.singletonList(
                simpleInstanceSQLPartInfo(quoteSegments(alias.getTable()), null));
        }
        if (type == SQLPartType.FROM) {
            return Collections.singletonList(simpleInstanceSQLPartInfo(
                quoteSegments(alias.getTable()) + " as " + symbol + alias + symbol, null));
        }
        return super.getDefault(type);
    }

    /**
     * 修复 {@code ORA-00933}：ROWNUM 条件并入 WHERE。
     * <ul>
     *   <li>无 WHERE → {@code where ROWNUM <= ?}</li>
     *   <li>有 WHERE → {@code where ... and ROWNUM <= ?}</li>
     * </ul>
     * 注意：Oracle 11g 的「偏移+行数」两段分页（offset+take）仍按官方方言不支持
     * （继承 {@link TypeNotSupportedException} 行为）；本方言覆盖的是「取前 N 行」
     * （对应 {@code first()} / {@code limit(n)}）的常用路径。
     */
    @Override
    public void formatLimit(Object take, Collection<Object> parameters) {
        String rownumCondition = "ROWNUM <= " + replaceValueAndFillParameters(take, parameters);
        if (isEmpty(SQLPartType.WHERE)) {
            set(SQLPartType.WHERE, rownumCondition, parameters);
        } else {
            addSmartSeparator(SQLPartType.WHERE, rownumCondition, parameters, " and ");
        }
    }
}
