import com.weacsoft.jaravel.jblade.BladeEngine;

public class LinesTest {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);

            System.out.println("=== JBlade 行测试 ===\n");

            String result = engine.render("lines_test");
            System.out.println(result);

            System.out.println("\n=== 测试完成 ===");

        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}