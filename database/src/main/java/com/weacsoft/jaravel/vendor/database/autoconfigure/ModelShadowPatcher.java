package com.weacsoft.jaravel.vendor.database.autoconfigure;

import gaarason.database.contract.eloquent.Model;
import gaarason.database.provider.ModelShadowProvider;
import gaarason.database.support.EntityMember;
import gaarason.database.support.ModelMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

import java.util.Map;

/**
 * 修复 gaarason ORM 的 model_shadow 字段扫描 bug。
 * <p>
 * <b>问题根因</b>：gaarason 的 {@code ModelBase} 声明了内部缓存字段
 * {@code protected transient ModelShadowProvider modelShadow}，但<b>未</b>标注
 * {@code @Column(inDatabase = false)}。当 gaarason 的 {@code EntityMember.primitiveFieldDeal()}
 * 扫描类层次字段时，会将该字段映射为数据库列 {@code model_shadow} 并加入 SELECT 列表。
 * <p>
 * {@code BaseModel} 通过字段隐藏（field hiding）方式覆盖了该字段并标注
 * {@code @Column(inDatabase = false)}，但 gaarason 的 {@code dealColumnMap()} 和
 * {@code dealSelectColumnList()} 方法使用<b>列名</b>而非<b>字段名</b>做去重：
 * <ul>
 *   <li>子类字段 {@code inDatabase=false} → 跳过，未加入 {@code columnFieldMap}</li>
 *   <li>父类字段 {@code inDatabase=true}（默认）→ 列名 {@code model_shadow} 不在 map 中 → 被加入</li>
 * </ul>
 * 这导致 {@code model_shadow} 始终出现在 SELECT 查询中，若数据库表无此列则报 SQL 异常。
 * <p>
 * <b>修复方式</b>：在 Spring 容器初始化完成后（所有 Model Bean 已就绪），
 * 遍历所有已注册的 Model，通过 {@link Model#getContainer()} 获取 gaarason 内部容器的
 * {@link ModelShadowProvider}，再获取其 {@link EntityMember}，
 * 从 {@code selectColumnList} 和 {@code columnFieldMap} 中移除 {@code model_shadow}。
 * <p>
 * <b>注意</b>：{@link ModelShadowProvider} 是 gaarason 内部容器的 bean，
 * 不是 Spring bean，不能通过 {@code @Autowired} 注入，必须通过
 * {@code model.getContainer().getBean(ModelShadowProvider.class)} 获取。
 * <p>
 * <b>执行时机</b>：使用 {@link Ordered#HIGHEST_PRECEDENCE} 确保在业务 ApplicationRunner
 * （如数据初始化）之前执行。
 *
 * @see ModelShadowProvider
 * @see EntityMember
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ModelShadowPatcher implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ModelShadowPatcher.class);

    /**
     * 需要从 ORM 映射中移除的 gaarason 内部列名
     */
    private static final String SHADOW_COLUMN = "model_shadow";

    private final Map<String, Model> models;

    /**
     * @param models Spring 容器中所有 Model Bean
     */
    @SuppressWarnings("rawtypes")
    public ModelShadowPatcher(Map<String, Model> models) {
        this.models = models;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void run(ApplicationArguments args) {
        if (models.isEmpty()) {
            return;
        }
        // ModelShadowProvider 是 gaarason 内部容器的 bean，不是 Spring bean，
        // 通过任意 Model 的 getContainer() 获取
        ModelShadowProvider modelShadowProvider = null;
        for (Model<?, ?, ?> model : models.values()) {
            try {
                modelShadowProvider = model.getContainer().getBean(ModelShadowProvider.class);
                break;
            } catch (Exception e) {
                log.debug("[model-shadow] Failed to get ModelShadowProvider from {}: {}",
                        model.getClass().getName(), e.getMessage());
            }
        }
        if (modelShadowProvider == null) {
            log.warn("[model-shadow] Could not obtain ModelShadowProvider from any Model, skipping patch");
            return;
        }

        int patched = 0;
        for (Model<?, ?, ?> model : models.values()) {
            try {
                ModelMember<?, ?, ?> modelMember = modelShadowProvider.get(model);
                EntityMember<?, ?> entityMember = modelMember.getEntityMember();

                boolean removedFromSelect = entityMember.getSelectColumnList()
                        .removeIf(SHADOW_COLUMN::equals);
                boolean removedFromColumn = entityMember.getColumnFieldMap()
                        .remove(SHADOW_COLUMN) != null;

                if (removedFromSelect || removedFromColumn) {
                    patched++;
                    log.debug("[model-shadow] Patched {} : removed '{}' from select/column maps",
                            model.getClass().getName(), SHADOW_COLUMN);
                }
            } catch (Exception e) {
                log.warn("[model-shadow] Failed to patch {}: {}",
                        model.getClass().getName(), e.getMessage());
            }
        }
        if (patched > 0) {
            log.info("[model-shadow] Patched {} model(s): removed gaarason internal '{}' "
                    + "from SELECT queries (no database column needed)", patched, SHADOW_COLUMN);
        }
    }
}
