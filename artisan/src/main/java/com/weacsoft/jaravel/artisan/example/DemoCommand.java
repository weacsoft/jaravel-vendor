package com.weacsoft.jaravel.artisan.example;

import com.weacsoft.jaravel.artisan.Command;
import com.weacsoft.jaravel.artisan.Console;
import com.weacsoft.jaravel.artisan.Input;
import com.weacsoft.jaravel.artisan.ProgressBar;

public class DemoCommand extends Command {
    @Override
    protected void configure() {
        setName("demo");
        setDescription("Demonstrate Artisan features");
        alias("example");
    }

    @Override
    protected void handle() {
        Console.newLine();
        Console.success("=== Artisan Demo ===");
        Console.newLine();

        demoOutput();
        demoInput();
        demoProgressBar();

        Console.newLine();
        Console.success("Demo completed!");
    }

    private void demoOutput() {
        Console.info("=== Output Demo ===");
        Console.newLine();

        Console.info("This is an info message");
        Console.success("This is a success message");
        Console.warning("This is a warning message");
        Console.error("This is an error message");
        Console.comment("This is a comment message");
        Console.line("This is a plain line");
        Console.newLine();
    }

    private void demoInput() {
        Console.info("=== Input Demo ===");
        Console.newLine();

        String name = Input.ask("What is your name?");
        Console.success("Hello, %s!", name);
        Console.newLine();

        boolean confirm = Input.confirm("Do you want to continue?");
        Console.info("You chose: %s", confirm ? "yes" : "no");
        Console.newLine();

        String choice = Input.choice("Choose a color", 
            java.util.Arrays.asList("Red", "Green", "Blue", "Yellow"));
        Console.success("You chose: %s", choice);
        Console.newLine();

        int age = Input.askInt("How old are you?");
        Console.info("You are %d years old", age);
        Console.newLine();
    }

    private void demoProgressBar() {
        Console.info("=== Progress Bar Demo ===");
        Console.newLine();

        ProgressBar bar = ProgressBar.create(100, 50)
            .setDescription("Processing")
            .setShowPercentage(true)
            .setShowETA(true);

        bar.start();

        for (int i = 0; i <= 100; i++) {
            bar.advance();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        bar.finish();
        Console.success("Processing completed!");
        Console.newLine();
    }
}
