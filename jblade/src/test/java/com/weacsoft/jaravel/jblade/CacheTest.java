package com.weacsoft.jaravel.jblade;

import java.util.HashMap;
import java.util.Map;

public class CacheTest {
    public static void main(String[] args) throws Exception {
        String templateDir = "templates";
        BladeEngine engine = new BladeEngine(templateDir);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "测试");
        variables.put("count", 100);
        variables.put("show", true);
        
        System.out.println("=== 缓存测试 ===\n");
        
        System.out.println("第一次渲染:");
        String result1 = engine.render("basic_test", variables);
        System.out.println(result1);
        
        System.out.println("\n第二次渲染 (应该复用模板实例):");
        variables.put("name", "测试2");
        String result2 = engine.render("basic_test", variables);
        System.out.println(result2);
        
        System.out.println("\n=== 验证缓存效果 ===");
        System.out.println("模板实例缓存大小: " + engine.getTemplateInstanceCacheSize());
        
        if (engine.getTemplateInstanceCacheSize() > 0) {
            System.out.println("✓ 模板实例缓存成功！");
        } else {
            System.out.println("✗ 模板实例缓存失败！");
        }
        
        System.out.println("\n=== 测试完成 ===");
    }
}
