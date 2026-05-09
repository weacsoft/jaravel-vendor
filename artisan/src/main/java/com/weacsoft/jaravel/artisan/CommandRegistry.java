package com.weacsoft.jaravel.artisan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandRegistry {
    private Map<String, Command> commands;
    private Map<String, String> aliases;

    public CommandRegistry() {
        this.commands = new HashMap<>();
        this.aliases = new HashMap<>();
    }

    public void register(Command command) {
        if (command.getName() == null || command.getName().isEmpty()) {
            throw new IllegalArgumentException("Command name cannot be null or empty");
        }
        commands.put(command.getName(), command);

        for (String alias : command.getAliases()) {
            aliases.put(alias, command.getName());
        }
    }

    public void register(List<Command> commandList) {
        for (Command command : commandList) {
            register(command);
        }
    }

    public Command get(String name) {
        if (commands.containsKey(name)) {
            return commands.get(name);
        }
        if (aliases.containsKey(name)) {
            return commands.get(aliases.get(name));
        }
        return null;
    }

    public boolean has(String name) {
        return commands.containsKey(name) || aliases.containsKey(name);
    }

    public List<String> getCommandNames() {
        return new ArrayList<>(commands.keySet());
    }

    public List<Command> getAllCommands() {
        return new ArrayList<>(commands.values());
    }

    public void printCommands() {
        Console.newLine();
        Console.info("Available commands:");
        Console.newLine();

        int maxLength = 0;
        for (String name : commands.keySet()) {
            maxLength = Math.max(maxLength, name.length());
        }

        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            String name = entry.getKey();
            Command command = entry.getValue();
            String description = command.getDescription() != null ? command.getDescription() : "";
            Console.comment("  %-" + maxLength + "s  %s", name, description);
        }
        Console.newLine();
    }

    public void printCommandHelp(String commandName) {
        Command command = get(commandName);
        if (command == null) {
            Console.error("Command '%s' not found", commandName);
            return;
        }

        Console.newLine();
        Console.info("Command: %s", command.getName());
        Console.newLine();
        Console.comment("Description:");
        Console.line("  %s", command.getDescription() != null ? command.getDescription() : "No description");
        Console.newLine();

        if (!command.getArguments().isEmpty()) {
            Console.comment("Arguments:");
            for (Command.Argument argument : command.getArguments().values()) {
                String required = argument.isRequired() ? " (required)" : " (optional)";
                Console.line("  %s%s", argument.getName(), required);
                if (argument.getDescription() != null) {
                    Console.comment("    %s", argument.getDescription());
                }
            }
            Console.newLine();
        }

        if (!command.getOptions().isEmpty()) {
            Console.comment("Options:");
            for (Command.Option option : command.getOptions().values()) {
                String shortcut = option.getShortcut() != null ? ", -" + option.getShortcut() : "";
                String required = option.isRequired() ? " (required)" : " (optional)";
                Console.line("  --%s%s%s", option.getName(), shortcut, required);
                if (option.getDescription() != null) {
                    Console.comment("    %s", option.getDescription());
                }
            }
            Console.newLine();
        }

        if (!command.getAliases().isEmpty()) {
            Console.comment("Aliases:");
            Console.line("  %s", String.join(", ", command.getAliases()));
            Console.newLine();
        }
    }
}
