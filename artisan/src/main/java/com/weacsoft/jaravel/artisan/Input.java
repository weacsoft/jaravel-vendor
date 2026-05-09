package com.weacsoft.jaravel.artisan;

import java.util.List;
import java.util.Scanner;

public class Input {
    private static final Scanner scanner = new Scanner(System.in);
    private static final java.io.Console systemConsole = System.console();

    public static String ask(String question) {
        return ask(question, null);
    }

    public static String ask(String question, String defaultValue) {
        if (defaultValue != null) {
            Console.info("%s [%s]: ", question, defaultValue);
        } else {
            Console.info("%s: ", question);
        }

        String input = scanner.nextLine().trim();
        if (input.isEmpty() && defaultValue != null) {
            return defaultValue;
        }
        return input;
    }

    public static String ask(String question, String defaultValue, List<String> validOptions) {
        while (true) {
            String input = ask(question, defaultValue);
            if (validOptions.contains(input)) {
                return input;
            }
            Console.error("Invalid option. Please choose from: %s", String.join(", ", validOptions));
        }
    }

    public static boolean confirm(String question) {
        return confirm(question, false);
    }

    public static boolean confirm(String question, boolean defaultValue) {
        String defaultText = defaultValue ? "yes" : "no";
        Console.info("%s [%s]: ", question, defaultText);

        String input = scanner.nextLine().trim().toLowerCase();
        if (input.isEmpty()) {
            return defaultValue;
        }

        return input.equals("y") || input.equals("yes");
    }

    public static String choice(String question, List<String> options) {
        return choice(question, options, null);
    }

    public static String choice(String question, List<String> options, Integer defaultIndex) {
        Console.newLine();
        for (int i = 0; i < options.size(); i++) {
            Console.comment("  [%d] %s", i + 1, options.get(i));
        }
        Console.newLine();

        String defaultText = defaultIndex != null ? String.valueOf(defaultIndex + 1) : null;
        while (true) {
            String input = ask(question, defaultText);
            try {
                int index = Integer.parseInt(input) - 1;
                if (index >= 0 && index < options.size()) {
                    return options.get(index);
                }
            } catch (NumberFormatException e) {
            }
            Console.error("Invalid selection. Please enter a number between 1 and %d", options.size());
        }
    }

    public static String secret(String question) {
        if (systemConsole != null) {
            char[] password = systemConsole.readPassword("%s: ", question);
            return password != null ? new String(password) : "";
        } else {
            Console.warning("Console not available, input will be visible");
            return ask(question);
        }
    }

    public static int askInt(String question) {
        return askInt(question, null);
    }

    public static int askInt(String question, Integer defaultValue) {
        while (true) {
            String input = ask(question, defaultValue != null ? String.valueOf(defaultValue) : null);
            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                Console.error("Please enter a valid number");
            }
        }
    }

    public static int askInt(String question, Integer defaultValue, int min, int max) {
        while (true) {
            int value = askInt(question, defaultValue);
            if (value >= min && value <= max) {
                return value;
            }
            Console.error("Please enter a number between %d and %d", min, max);
        }
    }

    public static void close() {
        scanner.close();
    }
}
