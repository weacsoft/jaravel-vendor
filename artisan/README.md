# Jaravel Artisan

A powerful command-line interface (CLI) library for Java, inspired by Laravel's Artisan.

## Features

- **Console Output**: Colored console output with multiple message types (info, success, warning, error, comment)
- **User Input**: Interactive input methods (ask, confirm, choice, secret, askInt)
- **Progress Bar**: Animated progress bars with percentage and ETA estimation
- **Command System**: Easy-to-use command framework with argument and option parsing
- **Command Registry**: Automatic command registration and discovery

## Quick Start

### Basic Console Output

```java
import com.weacsoft.jaravel.artisan.Console;

public class Example {
    public static void main(String[] args) {
        Console.info("This is an info message");
        Console.success("This is a success message");
        Console.warning("This is a warning message");
        Console.error("This is an error message");
        Console.comment("This is a comment message");
        Console.line("This is a plain line");
    }
}
```

### Progress Bar

```java
import com.weacsoft.jaravel.artisan.ProgressBar;

public class Example {
    public static void main(String[] args) {
        ProgressBar bar = ProgressBar.create(100)
            .setDescription("Processing")
            .setShowPercentage(true)
            .setShowETA(true);

        bar.start();

        for (int i = 0; i <= 100; i++) {
            bar.advance();
            Thread.sleep(50);
        }

        bar.finish();
    }
}
```

### User Input

```java
import com.weacsoft.jaravel.artisan.Input;
import java.util.Arrays;

public class Example {
    public static void main(String[] args) {
        String name = Input.ask("What is your name?");
        boolean confirm = Input.confirm("Continue?");
        String choice = Input.choice("Choose color", Arrays.asList("Red", "Green", "Blue"));
        String password = Input.secret("Enter password");
        int age = Input.askInt("How old are you?");
    }
}
```

### Creating Commands

```java
import com.weacsoft.jaravel.artisan.Command;
import com.weacsoft.jaravel.artisan.Console;

public class MyCommand extends Command {
    @Override
    protected void configure() {
        setName("mycommand");
        setDescription("My custom command");
        alias("mc");
        
        argument("name", "The name argument", true);
        option("verbose", "Verbose output", "v");
    }

    @Override
    protected void handle() {
        Console.success("Executing my command!");
    }
}
```

### Running Artisan

```java
import com.weacsoft.jaravel.artisan.Artisan;

public class Main {
    public static void main(String[] args) {
        Artisan artisan = new Artisan("My App", "1.0.0");
        artisan.register(new MyCommand());
        artisan.run(args);
    }
}
```

## API Reference

### Console

- `info(String message)` - Display info message (cyan)
- `success(String message)` - Display success message (green)
- `warning(String message)` - Display warning message (yellow)
- `error(String message)` - Display error message (red)
- `comment(String message)` - Display comment message (gray)
- `line(String message)` - Display plain line
- `newLine()` - Print empty line

### Input

- `ask(String question)` - Ask for text input
- `ask(String question, String defaultValue)` - Ask with default value
- `confirm(String question)` - Ask yes/no question
- `confirm(String question, boolean defaultValue)` - Confirm with default
- `choice(String question, List<String> options)` - Select from options
- `secret(String question)` - Ask for password (hidden input)
- `askInt(String question)` - Ask for integer input
- `askInt(String question, Integer defaultValue)` - Ask for integer with default

### ProgressBar

- `ProgressBar.create(int total)` - Create progress bar
- `ProgressBar.create(int total, int barWidth)` - Create with custom width
- `setDescription(String description)` - Set description text
- `setShowPercentage(boolean show)` - Show/hide percentage
- `setShowETA(boolean show)` - Show/hide estimated time
- `setCharacter(String character)` - Set filled character
- `setEmptyCharacter(String character)` - Set empty character
- `start()` - Start progress bar
- `advance()` - Advance by 1
- `advance(int steps)` - Advance by steps
- `setCurrent(int current)` - Set current progress
- `finish()` - Finish and display final state

### Command

- `setName(String name)` - Set command name
- `setDescription(String description)` - Set description
- `alias(String alias)` - Add alias
- `argument(String name)` - Add argument
- `argument(String name, String description)` - Add argument with description
- `argument(String name, String description, boolean required)` - Add argument with required flag
- `option(String name)` - Add option
- `option(String name, String description)` - Add option with description
- `option(String name, String description, String shortcut)` - Add option with shortcut
- `option(String name, String description, String shortcut, boolean required)` - Add option with all parameters

## Examples

Run the demo command to see all features in action:

```bash
java com.weacsoft.jaravel.artisan.example.Main demo
```

Run the quick start example:

```bash
java com.weacsoft.jaravel.artisan.example.QuickStart
```

## License

MIT License
