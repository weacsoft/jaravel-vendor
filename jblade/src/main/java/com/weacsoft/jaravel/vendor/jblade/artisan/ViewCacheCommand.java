package com.weacsoft.jaravel.vendor.jblade.artisan;

import com.weacsoft.jaravel.vendor.artisan.ArtisanCommand;
import com.weacsoft.jaravel.vendor.jblade.ViewCache;

/**
 * Artisan 命令：{@code view:cache}，编译 Blade 模板并写入<b>内存字节码缓存</b>。
 * <p>
 * 对齐 Laravel {@code php artisan view:cache}。执行流程：
 * <ol>
 *   <li>清空内存字节码缓存、模板类缓存、外部 CacheStore 与 区域元数据；</li>
 *   <li>扫描模板目录下的全部模板，逐个强制编译，生成字节码写入内存缓存
 *       （{@code templateName → byte[]}），后续渲染时跳过源码生成与 javac 编译；</li>
 *   <li>额外输出预编译包 {@code storage/framework/views/templates.jblade.zip}，
 *       供无 JDK 的 JRE 部署形态直接加载。</li>
 * </ol>
 * 编译副产物同时包含模板继承结构分析结果（@extends/@section/@yield 推导出的区域元数据），
 * 
 * <p>
 * 底层能力由静态方法 {@link ViewCache#rebuild()} 提供，可在代码中直接调用。
 *
 * <h3>选项</h3>
 * <ul>
 *   <li>{@code --quiet}：静默模式，仅输出最终统计结果。</li>
 * </ul>
 */
public class ViewCacheCommand extends ArtisanCommand {

    @Override
    public String signature() {
        return "view:cache {--quiet}";
    }

    @Override
    public String description() {
        return "编译全部 Blade 模板并写入缓存（含 区域元数据与预编译包）";
    }

    @Override
    public int handle() {
        boolean quiet = hasOption("quiet");
        if (!quiet) {
            info("正在编译模板缓存...");
        }
        long start = System.currentTimeMillis();
        int compiled;
        try {
            compiled = ViewCache.rebuild();
        } catch (RuntimeException e) {
            error("模板缓存编译失败: " + e.getMessage());
            return 1;
        }
        long cost = System.currentTimeMillis() - start;

        if (compiled <= 0) {
            warn("未编译任何模板（视图引擎未就绪或模板目录为空）");
            return 0;
        }
        info("模板缓存编译完成：" + compiled + " 个模板，耗时 " + cost + "ms");
        if (!quiet) {
            info("预编译包输出：storage/framework/views/templates.jblade.zip");
        }
        return 0;
    }
}
