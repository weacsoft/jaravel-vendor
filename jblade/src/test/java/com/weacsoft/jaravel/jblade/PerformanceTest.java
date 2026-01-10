package com.weacsoft.jaravel.jblade;

import java.util.HashMap;
import java.util.Map;

public class PerformanceTest {
    public static void main(String[] args) throws Exception {
        String templateDir = "templates";
        BladeEngine engine = new BladeEngine(templateDir);
        
        Map<String, Object> variables = new HashMap<>();
        variables.put("name", "World");
        variables.put("count", 5);
        
        int iterations = 1000;
        
        System.out.println("=== 性能测试 ===");
        System.out.println("迭代次数: " + iterations);
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            engine.render("basic_test", variables);
        }
        long endTime = System.currentTimeMillis();
        
        long totalTime = endTime - startTime;
        double avgTime = (double) totalTime / iterations;
        
        System.out.println("总耗时: " + totalTime + " ms");
        System.out.println("平均每次渲染: " + avgTime + " ms");
        System.out.println("每秒渲染次数: " + (1000.0 / avgTime));
        
        System.out.println();
        System.out.println("=== 验证缓存效果 ===");
        System.out.println("模板实例缓存大小: " + engine.getTemplateInstanceCacheSize());
        
        System.out.println();
        System.out.println("=== 测试完成 ===");
    }
}
