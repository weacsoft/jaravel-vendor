package com.weacsoft.jaravel.artisan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class Command implements com.weacsoft.jaravel.contract.console.Command {
    protected String name;
    protected String description;
    protected Map<String, Argument> arguments;
    protected Map<String, Option> options;
    protected List<String> aliases;

    public Command() {
        this.arguments = new HashMap<>();
        this.options = new HashMap<>();
        this.aliases = new ArrayList<>();
        configure();
    }

    protected abstract void configure();

    protected abstract void handle();

    @Override
    public void execute() {
        handle();
    }

    protected void argument(String name) {
        argument(name, null, false);
    }

    protected void argument(String name, String description) {
        argument(name, description, false);
    }

    protected void argument(String name, String description, boolean required) {
        arguments.put(name, new Argument(name, description, required));
    }

    protected void option(String name) {
        option(name, null, null, false);
    }

    protected void option(String name, String description) {
        option(name, description, null, false);
    }

    protected void option(String name, String description, String shortcut) {
        option(name, description, shortcut, false);
    }

    protected void option(String name, String description, String shortcut, boolean required) {
        options.put(name, new Option(name, description, shortcut, required));
    }

    protected void alias(String alias) {
        aliases.add(alias);
    }

    protected void setDescription(String description) {
        this.description = description;
    }

    protected void setName(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Map<String, Argument> getArguments() {
        return arguments;
    }

    @Override
    public Map<String, Option> getOptions() {
        return options;
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    public static class Argument {
        private String name;
        private String description;
        private boolean required;

        public Argument(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRequired() {
            return required;
        }
    }

    public static class Option {
        private String name;
        private String description;
        private String shortcut;
        private boolean required;

        public Option(String name, String description, String shortcut, boolean required) {
            this.name = name;
            this.description = description;
            this.shortcut = shortcut;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getShortcut() {
            return shortcut;
        }

        public boolean isRequired() {
            return required;
        }
    }
}
