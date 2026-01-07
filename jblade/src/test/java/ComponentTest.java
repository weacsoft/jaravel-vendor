import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ComponentTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);

            System.out.println("=== JBlade 组件功能测试 ===\n");

            Map<String, Object> variables = new HashMap<>();
            variables.put("items", Arrays.asList("苹果", "香蕉", "橙子"));

            String result = engine.render("component_test", variables);
            System.out.println(result);

            System.out.println("\n=== 测试完成 ===");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}