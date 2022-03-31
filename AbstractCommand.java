package dev.tezvn.elitechest.commands;

import com.google.common.collect.Lists;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author TezVN
 */
public class AbstractCommand extends BukkitCommand {

    private final JavaPlugin plugin;

    private UUID uniqueId;

    private List<SubCommand> subCommands;

    private String noPermissionsMessage;

    private String noSubCommandFoundMessage;

    private String noConsoleAllowMessage;

    public AbstractCommand(JavaPlugin plugin, @NotNull String name, @NotNull String description, @NotNull String usageMessage, @NotNull List<String> aliases) {
        super(name.toLowerCase(), description, usageMessage,
                aliases.stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toList()));
        this.uniqueId = UUID.randomUUID();
        this.plugin = plugin;
        this.subCommands = Lists.newArrayList();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            // Consoleo
            if (args.length > 0) {
                String name = args[0];
                Optional<SubCommand> optCommand = this.subCommands.stream().filter(new Predicate<SubCommand>() {
                    @Override
                    public boolean test(SubCommand command) {
                        boolean matchAliases = command.getAliases().contains(name);
                        return command.getName().equalsIgnoreCase(name) || matchAliases;
                    }
                }).findAny();

                if (!optCommand.isPresent()) {
                    sender.sendMessage(this.noSubCommandFoundMessage);
                    return true;
                }

                SubCommand command = optCommand.get();

                if (!command.allowConsole()) {
                    sender.sendMessage(this.noConsoleAllowMessage);
                    return true;
                }

                optCommand.get().consoleExecute(sender, args);
            }

            return true;
        }

        Player player = (Player) sender;
        if (args.length > 0) {
            String name = args[0];
            Optional<SubCommand> optCommand = this.subCommands.stream().filter(new Predicate<SubCommand>() {
                @Override
                public boolean test(SubCommand command) {
                    boolean matchAliases = command.getAliases().contains(name);
                    return command.getName().equalsIgnoreCase(name) || matchAliases;
                }
            }).findAny();

            if (!optCommand.isPresent()) {
                sender.sendMessage(this.noSubCommandFoundMessage);
                return true;
            }

            SubCommand command = optCommand.get();
            if (!player.hasPermission(command.getPermission())) {
                sender.sendMessage(this.noPermissionsMessage);
                return true;
            }

            optCommand.get().playerExecute(sender, args);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias, @NotNull String[] args)
            throws IllegalArgumentException {
        return new CommandCompleter(this).onTabComplete(sender, alias, args);
    }

    /**
     * Add sub command to your main command
     *
     * @param commands Sub command to add
     */
    public AbstractCommand addSubCommand(SubCommand... commands) {
        List<SubCommand> filter = Arrays.asList(commands).stream()
                .filter(c -> !isRegistered(c)).collect(Collectors.toList());
        this.subCommands.addAll(filter);
        return this;
    }

    public boolean isRegistered(SubCommand command) {
        return this.subCommands.stream().anyMatch(c -> c.getName().equals(command.getName()));
    }

    /**
     * Register command to server in {@code onEnable()} method
     */
    public AbstractCommand register() {
        try {
            if (!getKnownCommands().containsKey(getName())) {
                getKnownCommands().put(getName(), this);
                getKnownCommands().put(plugin.getDescription().getName().toLowerCase() + ":" + getName(), this);
            }
            for (String alias : getAliases()) {
                if (getKnownCommands().containsKey(alias))
                    continue;
                getKnownCommands().put(alias, this);
                getKnownCommands().put(plugin.getDescription().getName().toLowerCase() + ":" + alias, this);
            }
            register(getCommandMap());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    /**
     * Unregister command from server in {@code onDisable()} method
     */
    public AbstractCommand unregister() {
        try {
            unregister(getCommandMap());
            getKnownCommands().entrySet().removeIf(entry ->
                    entry.getValue() instanceof AbstractCommand
                            && ((AbstractCommand) entry.getValue()).getUniqueId().equals(this.getUniqueId())
            );
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    private CommandMap getCommandMap() throws Exception {
        Field field = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        field.setAccessible(true);
        return (CommandMap) field.get(Bukkit.getServer());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands() throws Exception {
        Field cmField = Bukkit.getServer().getClass().getDeclaredField("commandMap");
        cmField.setAccessible(true);
        CommandMap cm = (CommandMap) cmField.get(Bukkit.getServer());
        cmField.setAccessible(false);
        Method method = cm.getClass().getDeclaredMethod("getKnownCommands");
        return (Map<String, Command>) method.invoke(cm, new Object[]{});
    }

    /**
     * Get unique id of this command
     *
     * @return Comamnd unique id
     */
    public UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * Set message when player don't have permission to access to sub command
     *
     * @param noPermissionsMessage Message to set
     */
    public AbstractCommand setNoPermissionsMessage(String noPermissionsMessage) {
        this.noPermissionsMessage = noPermissionsMessage.replace("&", "§");
        return this;
    }

    /**
     * Set message when player's input match no sub command
     *
     * @param noSubCommandFoundMessage Message to set
     */
    public AbstractCommand setNoSubCommandFoundMessage(String noSubCommandFoundMessage) {
        this.noSubCommandFoundMessage = noSubCommandFoundMessage.replace("&", "§");
        return this;
    }

    /**
     * Set message when command is not allowed for console to use
     *
     * @param noConsoleAllowMessage Message to set
     */
    public AbstractCommand setNoConsoleAllowMessage(String noConsoleAllowMessage) {
        this.noConsoleAllowMessage = noConsoleAllowMessage.replace("&", "§");
        return this;
    }

    /**
     * Get list of registered sub commands
     *
     * @return List of sub commands
     */
    public List<SubCommand> getSubCommands() {
        return subCommands;
    }


    public static abstract class SubCommand {

        public SubCommand() {
        }

        /**
         * Get name of sub command
         *
         * @return Sub command name
         */
        public abstract String getName();

        /**
         * Get permission of sub command
         *
         * @return Sub command permission
         */
        public abstract String getPermission();

        /**
         * Get description of sub command
         *
         * @return Sub command description
         */
        public abstract String getDescription();

        /**
         * Get usage of sub command
         *
         * @return Sub command usage
         */
        public abstract String getUsage();

        /**
         * Get list of aliases of sub command
         *
         * @return Sub command aliases
         */
        public abstract List<String> getAliases();

        /**
         * Allow console to use this command
         *
         * @return True if allow, otherwise false
         */
        public abstract boolean allowConsole();

        /**
         * Player execution
         */
        public abstract void playerExecute(CommandSender sender, String[] args);

        /**
         * Console execution
         */
        public abstract void consoleExecute(CommandSender sender, String[] args);

        /**
         * Tab complete for sub command
         */
        public abstract List<String> tabComplete(CommandSender sender, String[] args);
    }

    protected static class CommandCompleter {

        private List<SubCommand> commands;

        protected CommandCompleter(AbstractCommand handle) {
            this.commands = handle.getSubCommands();
        }

        private List<String> getMainSuggestions(CommandSender sender, String start) {
            return this.commands.stream().filter(new Predicate<SubCommand>() {
                        @Override
                        public boolean test(SubCommand command) {
                            if (start.length() < 1) {
                                return sender.hasPermission(command.getPermission());
                            } else {
                                return command.getName().startsWith(start) && sender.hasPermission(command.getPermission());
                            }
                        }
                    })
                    .map(SubCommand::getName)
                    .collect(Collectors.toList());
        }

        private List<String> getSubSuggestions(CommandSender sender, String[] args) {
            Optional<SubCommand> optCommand = this.commands.stream()
                    .filter(command -> command.getName().equalsIgnoreCase(args[0])
                            && sender.hasPermission(command.getPermission()))
                    .findAny();

            return optCommand.map(subCommand -> subCommand.tabComplete(sender, args)).orElse(null);
        }

        public List<String> onTabComplete(CommandSender sender, String label, String[] args) {
            if (args.length == 1) {
                return this.getMainSuggestions(sender, args[0]);
            } else if (args.length > 1) {
                return this.getSubSuggestions(sender, args);
            }
            return null;
        }
    }
}
