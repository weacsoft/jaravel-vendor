import com.weacsoft.jaravel.jblade.BladeEngine;

public class SimpleTest3 {
    public static void main(String[] args) {
        try {
            String templateDir = "templates";
            BladeEngine engine = new BladeEngine(templateDir);
            
            System.out.println("=== JBlade 简单测试3 ===\n");
            
            String result = engine.render("simple_test3");
            System.out.println(result);
            
            System.out.println("\n=== 测试完成 ===");
            
        } catch (Exception e) {
            System.err.println("测试失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}