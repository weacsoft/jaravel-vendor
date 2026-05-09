package com.weacsoft.jaravel.artisan.example;

import com.weacsoft.jaravel.artisan.Console;
import com.weacsoft.jaravel.artisan.Input;
import com.weacsoft.jaravel.artisan.ProgressBar;

public class QuickStart {
    public static void main(String[] args) {
        Console.success("=== Quick Start Guide ===");
        Console.newLine();

        Console.info("1. Console Output:");
        Console.info("   Info message");
        Console.success("   Success message");
        Console.warning("   Warning message");
        Console.error("   Error message");
        Console.comment("   Comment message");
        Console.newLine();

        Console.info("2. Progress Bar:");
        ProgressBar bar = ProgressBar.create(50)
            .setDescription("Loading")
            .setShowPercentage(true);
        bar.start();
        for (int i = 0; i <= 50; i++) {
            bar.advance();
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        bar.finish();
        Console.newLine();

        Console.info("3. User Input:");
        Console.comment("   (This example uses default values to avoid blocking)");
        Console.newLine();

        Console.success("Quick Start completed!");
        Console.newLine();
        Console.info("Run 'Main' class with 'demo' command for full interactive demo");
    }
}
