import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.HashMap;
import java.util.Map;

public class SimpleComponentTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);
            
            System.out.println("=== JBlade 简单组件测试 ===\n");
            
            Map<String, Object> variables = new HashMap<>();
            
            String result = engine.render("simple_test", variables);
            System.out.println(result);
            
            System.out.println("\n=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}