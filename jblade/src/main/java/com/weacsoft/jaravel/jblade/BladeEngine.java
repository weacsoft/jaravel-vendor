package com.weacsoft.jaravel.jblade;

import com.weacsoft.jaravel.utils.memory.MemoryClassLoader;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;

public class BladeEngine {
    //编译器
    private final BladeCompiler compiler;
    //模板缓存
    private final Map<String, Class<?>> templateCache;
    //内存类加载器
    private MemoryClassLoader memoryClassLoader;

    public BladeEngine(String templateDir) {
        this(templateDir, new MemoryClassLoader());
    }

    public BladeEngine(String templateDir, MemoryClassLoader memoryClassLoader) {
        this.memoryClassLoader = memoryClassLoader;
        this.compiler = new BladeCompiler(templateDir, memoryClassLoader);
        this.templateCache = new HashMap<>();
    }

    public BladeEngine(String templateDir, String suffix) {
        this(templateDir, suffix, new MemoryClassLoader());
    }

    public BladeEngine(String templateDir, String suffix, MemoryClassLoader memoryClassLoader) {
        this.memoryClassLoader = memoryClassLoader;
        this.compiler = new BladeCompiler(templateDir, memoryClassLoader, suffix);
        this.templateCache = new HashMap<>();
    }

    public MemoryClassLoader getMemoryClassLoader() {
        if (memoryClassLoader == null) {
            memoryClassLoader = new MemoryClassLoader();
        }
        return memoryClassLoader;
    }

    public void removeMemoryClassLoader() {
        memoryClassLoader = null;
    }

    public String render(String templateName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
        template.setEngine(this);
        BladeContext context = template.getContext();

        if (variables != null) {
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                context.setVariable(entry.getKey(), entry.getValue());
            }
        }

        template.init();

        String parentTemplate = context.getParentTemplate();
        if (parentTemplate != null && !parentTemplate.isEmpty()) {
            BladeTemplate parent = loadTemplate(parentTemplate);
            parent.setEngine(this);
            parent.setContext(context);
            parent.init();
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

        Class<?> templateClass = templateCache.get(className);
        if (templateClass == null) {
            //用内存加载器加载
            templateClass = getMemoryClassLoader().loadClass(className);
            templateCache.put(className, templateClass);
        }

        BladeTemplate template = (BladeTemplate) templateClass.getDeclaredConstructor().newInstance();
        template.setEngine(this);
        return template;
    }

    public void clearCache() {
        templateCache.clear();
    }
}
