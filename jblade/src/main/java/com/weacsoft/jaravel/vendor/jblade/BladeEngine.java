package com.weacsoft.jaravel.vendor.jblade;

import com.weacsoft.jaravel.vendor.cache.Cache;
import com.weacsoft.jaravel.vendor.utils.memory.MemoryClassLoader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BladeEngine {
    /** 默认模板文件后缀，与 BladeCompiler.DEFAULT_SUFFIX 保持一致 */
    public static final String DEFAULT_SUFFIX = BladeCompiler.DEFAULT_SUFFIX;

    private final BladeCompiler compiler;
    private final Map<String, Class<?>> templateClassCache;
    private final Map<String, BladeTemplate> templateInstanceCache;
    private final Cache cache;
    private final MemoryClassLoader memoryClassLoader;
    private final boolean useCache;

    public BladeEngine(String templateDir) {
        this(templateDir, DEFAULT_SUFFIX, null, null);
    }

    public BladeEngine(String templateDir, MemoryClassLoader memoryClassLoader) {
        this(templateDir, DEFAULT_SUFFIX, null, memoryClassLoader);
    }

    public BladeEngine(String templateDir, String suffix) {
        this(templateDir, suffix, null, null);
    }

    public BladeEngine(String templateDir, String suffix, MemoryClassLoader memoryClassLoader) {
        this(templateDir, suffix, null, memoryClassLoader);
    }

    public BladeEngine(String templateDir, Cache cache) {
        this(templateDir, DEFAULT_SUFFIX, cache, null);
    }

    public BladeEngine(String templateDir, Cache cache, MemoryClassLoader memoryClassLoader) {
        this(templateDir, DEFAULT_SUFFIX, cache, memoryClassLoader);
    }

    public BladeEngine(String templateDir, String suffix, Cache cache) {
        this(templateDir, suffix, cache, null);
    }

    public BladeEngine(String templateDir, String suffix, Cache cache, MemoryClassLoader memoryClassLoader) {
        this.memoryClassLoader = memoryClassLoader != null ? memoryClassLoader : new MemoryClassLoader();
        this.compiler = new BladeCompiler(templateDir, this.memoryClassLoader, suffix);
        this.cache = cache;
        this.useCache = cache != null;
        this.templateClassCache = useCache ? null : new ConcurrentHashMap<>();
        this.templateInstanceCache = new ConcurrentHashMap<>();
    }

    /**
     * 获取当前模板文件后缀
     * @return 后缀字符串，如 ".blade.java"
     */
    public String getSuffix() {
        return compiler.getSuffix();
    }

    public MemoryClassLoader getMemoryClassLoader() {
        if (memoryClassLoader == null) {
            return new MemoryClassLoader();
        }
        return memoryClassLoader;
    }

    public void removeMemoryClassLoader() {
        throw new UnsupportedOperationException("Cannot remove memoryClassLoader after initialization");
    }

    public String render(String templateName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        
        template.resetContext();
        BladeContext context = template.getContext();

        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        if (!template.isInitialized()) {
            synchronized (template) {
                if (!template.isInitialized()) {
                    template.init();
                    template.setInitialized(true);
                }
            }
        }

        String parentTemplate = context.getParentTemplate();
        if (parentTemplate != null && !parentTemplate.isEmpty()) {
            BladeTemplate parent = loadTemplate(parentTemplate);
            parent.setEngine(this);
            
            BladeContext parentContext = parent.getContext();
            parentContext.reset();
            
            if (!parent.isInitialized()) {
                synchronized (parent) {
                    if (!parent.isInitialized()) {
                        parent.init();
                        parent.setInitialized(true);
                    }
                }
            }

            for (Map.Entry<String, Object> entry : context.getVariables().entrySet()) {
                parentContext.setVariable(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, String> entry : context.getSections().entrySet()) {
                parentContext.setSection(entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, java.util.function.Consumer<java.io.Writer>> entry : context.getSectionRenderers().entrySet()) {
                parentContext.setSectionRenderer(entry.getKey(), entry.getValue());
            }

            return parent.render();
        }

        return template.render();
    }

    public String render(String templateName) throws Exception {
        return render(templateName, null);
    }

    public BladeTemplate loadTemplate(String templateName) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        templateName = templateName.replace("'", "").replace("\"", "");
        String className = compiler.compile(templateName);

        Class<?> templateClass = null;
        if (useCache && cache != null) {
            Object cached = cache.get(className);
            if (cached instanceof Class) {
                templateClass = (Class<?>) cached;
            }
        } else if (!useCache && templateClassCache != null) {
            templateClass = templateClassCache.get(className);
        }

        if (templateClass == null) {
            templateClass = getMemoryClassLoader().loadClass(className);
            if (useCache && cache != null) {
                cache.put(className, templateClass);
            } else if (!useCache && templateClassCache != null) {
                templateClassCache.put(className, templateClass);
            }
        }

        BladeTemplate template = templateInstanceCache.get(className);
        if (template == null) {
            synchronized (this) {
                template = templateInstanceCache.get(className);
                if (template == null) {
                    template = (BladeTemplate) templateClass.getDeclaredConstructor().newInstance();
                    template.setEngine(this);
                    templateInstanceCache.put(className, template);
                }
            }
        }
        
        return template;
    }

    public void clearCache() {
        if (useCache && cache != null) {
            cache.flush();
        } else if (!useCache && templateClassCache != null) {
            templateClassCache.clear();
        }
        clearTemplateInstanceCache();
    }

    public void clearTemplateInstanceCache() {
        for (BladeTemplate template : templateInstanceCache.values()) {
            template.resetContext();
        }
        templateInstanceCache.clear();
    }

    public Cache getCache() {
        return cache;
    }

    public boolean isUseCache() {
        return useCache;
    }

    public int getTemplateInstanceCacheSize() {
        return templateInstanceCache.size();
    }
}