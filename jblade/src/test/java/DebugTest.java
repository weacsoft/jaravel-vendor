import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.HashMap;
import java.util.Map;

public class DebugTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);
            
            System.out.println("=== JBlade 调试测试 ===\n");
            
            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "测试");
            
            String result = engine.render("debug_test", variables);
            System.out.println(result);
            
            System.out.println("\n=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}