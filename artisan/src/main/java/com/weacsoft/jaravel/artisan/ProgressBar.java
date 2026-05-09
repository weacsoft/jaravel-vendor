package com.weacsoft.jaravel.artisan;

public class ProgressBar {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_BOLD = "\u001B[1m";

    private int total;
    private int current;
    private int barWidth;
    private String description;
    private boolean showPercentage;
    private boolean showETA;
    private long startTime;
    private boolean finished;
    private String character;
    private String emptyCharacter;

    public ProgressBar(int total) {
        this(total, 50);
    }

    public ProgressBar(int total, int barWidth) {
        this.total = total;
        this.current = 0;
        this.barWidth = barWidth;
        this.description = "";
        this.showPercentage = true;
        this.showETA = true;
        this.finished = false;
        this.character = "=";
        this.emptyCharacter = " ";
    }

    public ProgressBar setDescription(String description) {
        this.description = description;
        return this;
    }

    public ProgressBar setShowPercentage(boolean showPercentage) {
        this.showPercentage = showPercentage;
        return this;
    }

    public ProgressBar setShowETA(boolean showETA) {
        this.showETA = showETA;
        return this;
    }

    public ProgressBar setCharacter(String character) {
        this.character = character;
        return this;
    }

    public ProgressBar setEmptyCharacter(String emptyCharacter) {
        this.emptyCharacter = emptyCharacter;
        return this;
    }

    public void start() {
        this.startTime = System.currentTimeMillis();
        this.finished = false;
        this.current = 0;
        display();
    }

    public void advance() {
        advance(1);
    }

    public void advance(int steps) {
        this.current = Math.min(this.current + steps, this.total);
        display();
    }

    public void setCurrent(int current) {
        this.current = Math.min(current, this.total);
        display();
    }

    public void finish() {
        this.current = this.total;
        this.finished = true;
        display();
        Console.newLine();
    }

    private void display() {
        if (Console.isSupportsColor()) {
            displayColored();
        } else {
            displayPlain();
        }
    }

    private void displayColored() {
        double percentage = total > 0 ? (double) current / total : 0;
        int filledWidth = (int) (percentage * barWidth);
        int emptyWidth = barWidth - filledWidth;

        StringBuilder bar = new StringBuilder();
        bar.append("\r");
        bar.append(ANSI_BOLD);

        if (!description.isEmpty()) {
            bar.append(description).append(" ");
        }

        bar.append(ANSI_GREEN).append("[");
        for (int i = 0; i < filledWidth; i++) {
            bar.append(character);
        }
        bar.append(ANSI_RESET);
        for (int i = 0; i < emptyWidth; i++) {
            bar.append(emptyCharacter);
        }
        bar.append(ANSI_GREEN).append("]");

        if (showPercentage) {
            bar.append(String.format(" %d%%", (int) (percentage * 100)));
        }

        if (showETA && !finished && current > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long eta = (long) ((elapsed / (double) current) * (total - current));
            bar.append(String.format(" %s", formatTime(eta)));
        }

        bar.append(ANSI_RESET);

        System.out.print(bar.toString());
    }

    private void displayPlain() {
        double percentage = total > 0 ? (double) current / total : 0;
        int filledWidth = (int) (percentage * barWidth);
        int emptyWidth = barWidth - filledWidth;

        StringBuilder bar = new StringBuilder();
        bar.append("\r");

        if (!description.isEmpty()) {
            bar.append(description).append(" ");
        }

        bar.append("[");
        for (int i = 0; i < filledWidth; i++) {
            bar.append(character);
        }
        for (int i = 0; i < emptyWidth; i++) {
            bar.append(emptyCharacter);
        }
        bar.append("]");

        if (showPercentage) {
            bar.append(String.format(" %d%%", (int) (percentage * 100)));
        }

        if (showETA && !finished && current > 0) {
            long elapsed = System.currentTimeMillis() - startTime;
            long eta = (long) ((elapsed / (double) current) * (total - current));
            bar.append(String.format(" %s", formatTime(eta)));
        }

        System.out.print(bar.toString());
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % 60);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % 60);
        } else {
            return String.format("%ds", seconds);
        }
    }

    public static ProgressBar create(int total) {
        return new ProgressBar(total);
    }

    public static ProgressBar create(int total, int barWidth) {
        return new ProgressBar(total, barWidth);
    }
}
