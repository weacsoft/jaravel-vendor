package com.weacsoft.jaravel.jblade;

import com.weacsoft.jaravel.utils.memory.MemoryClassLoader;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

public class BladeEngine {
    //模板目录
    private String templateDir;
    //编译器
    private final BladeCompiler compiler;
    //模板缓存
    private final Map<String, Class<?>> templateCache;
    //内存类加载器
    private MemoryClassLoader memoryClassLoader;

    public MemoryClassLoader getMemoryClassLoader() {
        if (memoryClassLoader == null) {
            memoryClassLoader = new MemoryClassLoader();
        }
        return memoryClassLoader;
    }

    public void removeMemoryClassLoader() {
        memoryClassLoader = null;
    }

    public BladeEngine(String templateDir) {
        this.templateDir = templateDir;
        this.compiler = new BladeCompiler(templateDir, getMemoryClassLoader());
        this.templateCache = new HashMap<>();
    }

    public String render(String templateName, Map<String, Object> variables) throws Exception {
        BladeTemplate template = loadTemplate(templateName);
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
            parent.setContext(context);
            parent.init();
            return parent.render();
        }

        return template.render();
    }

    public String render(String templateName) throws Exception {
        return render(templateName, null);
    }

    private BladeTemplate loadTemplate(String templateName) throws IOException, ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        templateName = templateName.replace("'", "").replace("\"", "");

        String className = compiler.compile(templateName);

        Class<?> templateClass = templateCache.get(className);
        if (templateClass == null) {
            URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(outputDir).toURI().toURL()});
            templateClass = classLoader.loadClass("jblade.generated." + className);
            templateCache.put(className, templateClass);
        }

        return (BladeTemplate) templateClass.getDeclaredConstructor().newInstance();
    }

    public void clearCache() {
        templateCache.clear();
    }
}
