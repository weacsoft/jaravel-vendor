import com.weacsoft.jaravel.jblade.BladeEngine;

import java.util.HashMap;
import java.util.Map;

public class FullTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir, ".jblade.html");

            System.out.println("=== JBlade 实际测试 ===\n");

            Map<String, Object> data = new HashMap<>();
            data.put("title", "Welcome to Jaravel");
            data.put("version", "1.0.0");
            data.put("description", "A Laravel-inspired Java Web Framework");
            data.put("features", new String[]{
                    "Elegant Routing System",
                    "Powerful Request & Response Handling",
                    "Middleware Support",
                    "Blade Template Engine",
                    "Event & Listener System",
                    "Database ORM Support",
                    "Migration Support"
            });
            data.put("documentation", "https://github.com/weacsoft/jaravel");
            data.put("community", "https://github.com/weacsoft/jaravel/discussions");

            String result = engine.render("welcome", data);
            System.out.println(result);

            System.out.println("\n=== 测试完成 ===");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
