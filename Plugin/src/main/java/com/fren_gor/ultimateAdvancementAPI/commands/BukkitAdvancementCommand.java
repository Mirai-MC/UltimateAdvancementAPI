package com.fren_gor.ultimateAdvancementAPI.commands;

import com.fren_gor.ultimateAdvancementAPI.AdvancementMain;
import com.fren_gor.ultimateAdvancementAPI.AdvancementTab;
import com.fren_gor.ultimateAdvancementAPI.advancement.Advancement;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandException;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_ALL;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_ONE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_GRANT_TAB;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_MAIN_COMMAND;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION_GET;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_PROGRESSION_SET;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_ALL;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_ONE;
import static com.fren_gor.ultimateAdvancementAPI.commands.CommandAPIManager.PERMISSION_REVOKE_TAB;

/**
 * Native Bukkit implementation of the /ultimateadvancementapi command tree (with the usual
 * uaapi alias). Used as a fallback when the CommandAPI library cannot be downloaded at runtime
 * (offline servers); it delegates the actual advancement operations to CommandsCommon so the
 * behaviour matches the CommandAPI-based command exactly.
 */
public final class BukkitAdvancementCommand extends Command {

    private static final String[] ALIASES = {"uaapi", "uladv", "uladvapi"};
    private static volatile BukkitAdvancementCommand INSTANCE;

    private final AdvancementMain main;
    private final CommandsCommon<CommandException> commandsCommon;

    private BukkitAdvancementCommand(@NotNull AdvancementMain main) {
        super("ultimateadvancementapi", "Command to handle advancements.",
                "Usage: /uaapi <progression|grant|revoke> ...", List.of(ALIASES));
        this.main = Objects.requireNonNull(main, "AdvancementMain is null.");
        this.commandsCommon = new CommandsCommon<>(main, CommandException::new);
        setPermission(PERMISSION_MAIN_COMMAND);
    }

    /**
     * Registers the fallback command on the server's command map. Safe to call more than once (a second call
     * replaces the first). Returns silently if the command map can't be reached via the CraftBukkit API.
     */
    public static void register(@NotNull AdvancementMain main) {
        Objects.requireNonNull(main, "AdvancementMain is null.");
        CommandMap map = craftCommandMap();
        if (map == null) {
            return;
        }
        INSTANCE = new BukkitAdvancementCommand(main);
        map.register("ultimateadvancementapi", INSTANCE);
    }

    /** Unregisters the fallback command, if previously registered. Idempotent. */
    public static void unregister() {
        BukkitAdvancementCommand instance = INSTANCE;
        INSTANCE = null;
        CommandMap map = craftCommandMap();
        if (instance != null && map != null) {
            instance.unregister(map);
        }
    }

    @Nullable
    private static CommandMap craftCommandMap() {
        try {
            Method method = Bukkit.getServer().getClass().getMethod("getCommandMap");
            return (CommandMap) method.invoke(Bukkit.getServer());
        } catch (Exception e) {
            Bukkit.getLogger().log(Level.WARNING, "[UltimateAdvancementAPI] Couldn't reach the command map for the fallback /uaapi command.", e);
            return null;
        }
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION_MAIN_COMMAND)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <progression|grant|revoke> ...");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "grant" -> grant(sender, args, label);
            case "revoke" -> revoke(sender, args, label);
            case "progression" -> progression(sender, args, label);
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " <progression|grant|revoke> ...");
        }
        return true;
    }

    private void grant(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_GRANT)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant <all|tab|one> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "all" -> {
                if (!check(sender, PERMISSION_GRANT_ALL)) {
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 2);
                if (!targetsOrUsage(sender, args, 2, "Usage: /" + label + " grant all <player> [giveRewards]", targets)) {
                    return;
                }
                boolean giveRewards = args.length > 3 && Boolean.parseBoolean(args[3]);
                runSafely(sender, () -> commandsCommon.grantAll(sender, targets, giveRewards),
                        "Could not grant every advancement");
            }
            case "tab" -> {
                if (!check(sender, PERMISSION_GRANT_TAB)) {
                    return;
                }
                AdvancementTab tab = requireTab(sender, args.length > 2 ? args[2] : null);
                if (tab == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant tab <advancementTab> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                if (!targetsOrUsage(sender, args, 3, "Usage: /" + label + " grant tab <advancementTab> <player> [giveRewards]", targets)) {
                    return;
                }
                boolean giveRewards = args.length > 4 && Boolean.parseBoolean(args[4]);
                runSafely(sender, () -> commandsCommon.grantTab(sender, tab, targets, giveRewards),
                        "Could not grant every advancement of tab " + tab);
            }
            case "one" -> {
                if (!check(sender, PERMISSION_GRANT_ONE)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant one <advancement> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                if (!targetsOrUsage(sender, args, 3, "Usage: /" + label + " grant one <advancement> <player> [giveRewards]", targets)) {
                    return;
                }
                boolean giveRewards = args.length > 4 && Boolean.parseBoolean(args[4]);
                runSafely(sender, () -> commandsCommon.grantOne(sender, adv, targets, giveRewards),
                        "Could not grant advancement " + adv);
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " grant <all|tab|one> ...");
        }
    }

    private void revoke(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_REVOKE)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke <all|tab|one> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "all" -> {
                if (!check(sender, PERMISSION_REVOKE_ALL)) {
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 2);
                if (!targetsOrUsage(sender, args, 2, "Usage: /" + label + " revoke all <player> [hideTabs]", targets)) {
                    return;
                }
                boolean hideTabs = args.length > 3 && Boolean.parseBoolean(args[3]);
                runSafely(sender, () -> commandsCommon.revokeAll(sender, targets, hideTabs),
                        "Could not revoke every advancement");
            }
            case "tab" -> {
                if (!check(sender, PERMISSION_REVOKE_TAB)) {
                    return;
                }
                AdvancementTab tab = requireTab(sender, args.length > 2 ? args[2] : null);
                if (tab == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke tab <advancementTab> [player] [hideTab]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                if (!targetsOrUsage(sender, args, 3, "Usage: /" + label + " revoke tab <advancementTab> <player> [hideTab]", targets)) {
                    return;
                }
                boolean hideTab = args.length > 4 && Boolean.parseBoolean(args[4]);
                runSafely(sender, () -> commandsCommon.revokeTab(sender, tab, targets, hideTab),
                        "Could not revoke every advancement of tab " + tab);
            }
            case "one" -> {
                if (!check(sender, PERMISSION_REVOKE_ONE)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke one <advancement> [player]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                if (!targetsOrUsage(sender, args, 3, "Usage: /" + label + " revoke one <advancement> <player>", targets)) {
                    return;
                }
                runSafely(sender, () -> commandsCommon.revokeOne(sender, adv, targets),
                        "Could not revoke advancement " + adv);
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " revoke <all|tab|one> ...");
        }
    }

    private void progression(CommandSender sender, String[] args, String label) {
        if (!sender.hasPermission(PERMISSION_PROGRESSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression <get|set> ...");
            return;
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        switch (kind) {
            case "get" -> {
                if (!check(sender, PERMISSION_PROGRESSION_GET)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                if (adv == null) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression get <advancement> [player]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 3);
                if (!targetsOrUsage(sender, args, 3, "Usage: /" + label + " progression get <advancement> <player>", targets)) {
                    return;
                }
                for (Player p : targets) {
                    runSafely(sender, () -> commandsCommon.getProgression(sender, adv, p),
                            "Could not get progression of advancement " + adv);
                }
            }
            case "set" -> {
                if (!check(sender, PERMISSION_PROGRESSION_SET)) {
                    return;
                }
                Advancement adv = requireAdvancement(sender, args.length > 2 ? args[2] : null);
                int progression;
                try {
                    progression = Integer.parseInt(args.length > 3 ? args[3] : "-1");
                } catch (NumberFormatException e) {
                    progression = -1;
                }
                if (adv == null || progression < 0) {
                    sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression set <advancement> <progression> [player] [giveRewards]");
                    return;
                }
                List<Player> targets = resolveTargets(sender, args, 4);
                if (!targetsOrUsage(sender, args, 4, "Usage: /" + label + " progression set <advancement> <progression> <player> [giveRewards]", targets)) {
                    return;
                }
                boolean giveRewards = args.length > 5 && Boolean.parseBoolean(args[5]);
                final int prog = progression;
                for (Player p : targets) {
                    runSafely(sender, () -> commandsCommon.setProgression(sender, adv, prog, p, giveRewards),
                            "Could not set progression of advancement " + adv);
                }
            }
            default -> sender.sendMessage(ChatColor.RED + "Usage: /" + label + " progression <get|set> ...");
        }
    }

    private boolean runSafely(CommandSender sender, Runnable action, String error) {
        try {
            action.run();
            return true;
        } catch (CommandException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return false;
        } catch (Exception e) {
            main.getLogger().log(Level.SEVERE, error, e);
            sender.sendMessage(ChatColor.RED + error);
            return false;
        }
    }

    private boolean check(CommandSender sender, String permission) {
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return false;
        }
        return true;
    }

    // A target is required everywhere except the "player runs it on themselves" case. Without one the
    // CommandAPI-based command prints its usage line, so the fallback prints the same instead of letting
    // CommandsCommon raise "No player has been provided." past the command boundary.
    private boolean targetsOrUsage(CommandSender sender, String[] args, int index, String usage, List<Player> targets) {
        if (!targets.isEmpty()) {
            return true;
        }
        sender.sendMessage(ChatColor.RED + (args.length <= index ? usage : "No player has been provided."));
        return false;
    }

    // Resolves the optional target-player argument: an "@"-selector or a named online player. When no argument
    // is given the sender is used (players only), matching the CommandAPI's executesPlayer default-to-self path.
    private List<Player> resolveTargets(CommandSender sender, String[] args, int index) {
        if (args.length > index) {
            String arg = args[index].trim();
            try {
                if (arg.charAt(0) == '@') {
                    return Bukkit.selectEntities(sender, arg).stream()
                            .filter(e -> e instanceof Player)
                            .map(e -> (Player) e)
                            .toList();
                }
            } catch (IllegalArgumentException ignored) {
                // fall through to exact-name lookup
            }
            Player exact = Bukkit.getPlayerExact(arg);
            if (exact != null) {
                return List.of(exact);
            }
            Player byName = Bukkit.getPlayer(arg);
            return byName == null ? List.of() : List.of(byName);
        }
        return sender instanceof Player player ? List.of(player) : List.of();
    }

    @Nullable
    private AdvancementTab requireTab(CommandSender sender, String arg) {
        if (arg == null) {
            return null;
        }
        AdvancementTab tab = main.getAdvancementTab(arg);
        if (tab == null) {
            sender.sendMessage(ChatColor.RED + "Unknown advancement tab: " + arg);
            return null;
        }
        if (!tab.isActive()) {
            sender.sendMessage(ChatColor.RED + "Invalid advancement tab: " + arg);
            return null;
        }
        return tab;
    }

    @Nullable
    private Advancement requireAdvancement(CommandSender sender, String arg) {
        if (arg == null) {
            return null;
        }
        Advancement adv;
        try {
            adv = main.getAdvancement(arg);
        } catch (IllegalArgumentException e) {
            adv = null;
        }
        if (adv == null) {
            sender.sendMessage(ChatColor.RED + "Unknown advancement: " + arg);
            return null;
        }
        if (!adv.isValid()) {
            sender.sendMessage(ChatColor.RED + "Invalid advancement: " + arg);
            return null;
        }
        return adv;
    }

    @Override
    public @Nullable List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
                                               @NotNull String[] args) {
        if (!sender.hasPermission(PERMISSION_MAIN_COMMAND)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return filter(args[0], "progression", "grant", "revoke");
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "grant", "revoke" -> filter(args[1], "all", "tab", "one");
                case "progression" -> filter(args[1], "get", "set");
                default -> Collections.emptyList();
            };
        }
        String kind = args[1].toLowerCase(Locale.ROOT);
        boolean tabSlot = (sub.equals("grant") || sub.equals("revoke")) && kind.equals("tab");
        boolean advancementSlot = ((sub.equals("grant") || sub.equals("revoke")) && (kind.equals("tab") || kind.equals("one")))
                || (sub.equals("progression") && (kind.equals("get") || kind.equals("set")));
        if (args.length == 3 && advancementSlot) {
            return tabSlot
                    ? filter(args[2], main.getAdvancementTabNamespaces().toArray(new String[0]))
                    : filter(args[2], main.filterNamespaces(null).toArray(new String[0]));
        }
        int playerSlot = playerSlot(sub, kind);
        if (playerSlot > 0 && args.length == playerSlot + 1) {
            String[] names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toArray(String[]::new);
            return filter(args[playerSlot], names);
        }
        if (playerSlot > 0 && args.length == playerSlot + 2 && hasBooleanArgument(sub, kind)) {
            return filter(args[playerSlot + 1], "true", "false");
        }
        return Collections.emptyList();
    }

    /** Index of the optional target-player argument, or -1 when the subcommand takes none. */
    private static int playerSlot(String sub, String kind) {
        return switch (sub) {
            case "grant" -> kind.equals("all") ? 2 : kind.equals("tab") || kind.equals("one") ? 3 : -1;
            case "revoke" -> kind.equals("all") ? 2 : kind.equals("tab") || kind.equals("one") ? 3 : -1;
            case "progression" -> kind.equals("get") ? 3 : kind.equals("set") ? 4 : -1;
            default -> -1;
        };
    }

    private static boolean hasBooleanArgument(String sub, String kind) {
        return switch (sub) {
            case "grant" -> kind.equals("all") || kind.equals("tab") || kind.equals("one");
            case "revoke" -> kind.equals("all") || kind.equals("tab");
            case "progression" -> kind.equals("set");
            default -> false;
        };
    }

    private List<String> filter(String prefix, String... options) {
        List<String> result = new ArrayList<>();
        String lower = prefix.toLowerCase(Locale.ROOT);
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}