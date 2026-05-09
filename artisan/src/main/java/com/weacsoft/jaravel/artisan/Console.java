package com.weacsoft.jaravel.artisan;

public class Console {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_BLACK = "\u001B[30m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_PURPLE = "\u001B[35m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_WHITE = "\u001B[37m";
    private static final String ANSI_BRIGHT_BLACK = "\u001B[90m";

    private static boolean supportsColor = true;

    static {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                String term = System.getenv("TERM");
                supportsColor = "xterm".equals(term) || "xterm-256color".equals(term);
            }
        } catch (Exception e) {
            supportsColor = false;
        }
    }

    public static void info(String message) {
        print(message, ANSI_CYAN);
    }

    public static void info(String format, Object... args) {
        print(String.format(format, args), ANSI_CYAN);
    }

    public static void error(String message) {
        print(message, ANSI_RED);
    }

    public static void error(String format, Object... args) {
        print(String.format(format, args), ANSI_RED);
    }

    public static void warning(String message) {
        print(message, ANSI_YELLOW);
    }

    public static void warning(String format, Object... args) {
        print(String.format(format, args), ANSI_YELLOW);
    }

    public static void success(String message) {
        print(message, ANSI_GREEN);
    }

    public static void success(String format, Object... args) {
        print(String.format(format, args), ANSI_GREEN);
    }

    public static void comment(String message) {
        print(message, ANSI_BRIGHT_BLACK);
    }

    public static void comment(String format, Object... args) {
        print(String.format(format, args), ANSI_BRIGHT_BLACK);
    }

    public static void line(String message) {
        System.out.println(message);
    }

    public static void line(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    public static void newLine() {
        System.out.println();
    }

    public static void newLine(int count) {
        for (int i = 0; i < count; i++) {
            System.out.println();
        }
    }

    private static void print(String message, String color) {
        if (supportsColor) {
            System.out.println(color + message + ANSI_RESET);
        } else {
            System.out.println(message);
        }
    }

    public static void setSupportsColor(boolean supportsColor) {
        Console.supportsColor = supportsColor;
    }

    public static boolean isSupportsColor() {
        return supportsColor;
    }
}
