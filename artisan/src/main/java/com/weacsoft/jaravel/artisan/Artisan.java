package com.weacsoft.jaravel.artisan;

import com.weacsoft.jaravel.contract.console.Command;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Artisan implements com.weacsoft.jaravel.contract.console.Kernel {
    private CommandRegistry registry;
    private String version;
    private String appName;

    public Artisan() {
        this("Jaravel Artisan", "1.0.0");
    }

    public Artisan(String appName, String version) {
        this.appName = appName;
        this.version = version;
        this.registry = new CommandRegistry();
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        registry.register(new HelpCommand());
        registry.register(new ListCommand());
        registry.register(new VersionCommand());
    }

    @Override
    public void register(Command command) {
        if (command instanceof com.weacsoft.jaravel.artisan.Command) {
            registry.register((com.weacsoft.jaravel.artisan.Command) command);
        } else {
            throw new IllegalArgumentException("Command must be an instance of artisan.Command");
        }
    }

    public void register(List<com.weacsoft.jaravel.artisan.Command> commands) {
        registry.register(commands);
    }

    @Override
    public void run(String[] args) {
        if (args.length == 0) {
            showWelcome();
            registry.printCommands();
            return;
        }

        String commandName = args[0];
        com.weacsoft.jaravel.artisan.Command command = registry.get(commandName);

        if (command == null) {
            Console.error("Command '%s' not found", commandName);
            Console.newLine();
            Console.info("Run 'artisan list' to see available commands");
            return;
        }

        Map<String, String> arguments = parseArguments(args, command);
        Map<String, String> options = parseOptions(args, command);

        try {
            command.execute();
        } catch (Exception e) {
            Console.error("Error executing command: %s", e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, String> parseArguments(String[] args, com.weacsoft.jaravel.artisan.Command command) {
        Map<String, String> arguments = new HashMap<>();
        List<String> argNames = new ArrayList<>(command.getArguments().keySet());

        int argIndex = 1;
        for (String argName : argNames) {
            if (argIndex < args.length && !args[argIndex].startsWith("--")) {
                arguments.put(argName, args[argIndex]);
                argIndex++;
            }
        }

        return arguments;
    }

    private Map<String, String> parseOptions(String[] args, com.weacsoft.jaravel.artisan.Command command) {
        Map<String, String> options = new HashMap<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                String optionName = arg.substring(2);
                if (optionName.contains("=")) {
                    String[] parts = optionName.split("=", 2);
                    options.put(parts[0], parts[1]);
                } else {
                    options.put(optionName, "true");
                }
            } else if (arg.startsWith("-") && arg.length() == 2) {
                String shortcut = arg.substring(1);
                for (com.weacsoft.jaravel.artisan.Command.Option option : command.getOptions().values()) {
                    if (shortcut.equals(option.getShortcut())) {
                        options.put(option.getName(), "true");
                        break;
                    }
                }
            }
        }

        return options;
    }

    private void showWelcome() {
        Console.newLine();
        Console.info("  %s v%s", appName, version);
        Console.newLine();
        Console.comment("  A powerful command-line interface for Jaravel");
        Console.newLine();
    }

    public static void main(String[] args) {
        Artisan artisan = new Artisan();
        artisan.run(args);
    }

    private static class HelpCommand extends com.weacsoft.jaravel.artisan.Command {
        @Override
        protected void configure() {
            setName("help");
            setDescription("Display help for a command");
            argument("command", "The command name", false);
        }

        @Override
        protected void handle() {
            Console.info("Usage: artisan <command> [arguments] [options]");
            Console.newLine();
            Console.info("Options:");
            Console.line("  -h, --help    Display this help message");
            Console.line("  -V, --version Display this application version");
            Console.newLine();
            Console.info("Available commands:");
            Console.newLine();
            Console.comment("  help      Display help for a command");
            Console.comment("  list      List all commands");
            Console.comment("  version   Display the application version");
            Console.newLine();
        }
    }

    private static class ListCommand extends com.weacsoft.jaravel.artisan.Command {
        @Override
        protected void configure() {
            setName("list");
            setDescription("List all commands");
            alias("ls");
        }

        @Override
        protected void handle() {
            Console.newLine();
            Console.info("Available commands:");
            Console.newLine();
            Console.comment("  help      Display help for a command");
            Console.comment("  list      List all commands");
            Console.comment("  version   Display the application version");
            Console.newLine();
        }
    }

    private static class VersionCommand extends com.weacsoft.jaravel.artisan.Command {
        @Override
        protected void configure() {
            setName("version");
            setDescription("Display the application version");
            alias("v");
        }

        @Override
        protected void handle() {
            Console.info("Jaravel Artisan version %s", "1.0.0");
        }
    }
}
