import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.HashMap;
import java.util.Map;

public class BasicTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);

            System.out.println("=== JBlade 基础测试 ===\n");

            Map<String, Object> variables = new HashMap<>();
            variables.put("name", "测试");
            variables.put("count", 100);
            variables.put("show", true);

            String result = engine.render("basic_test", variables);
            System.out.println(result);

            System.out.println("\n=== 测试完成 ===");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}