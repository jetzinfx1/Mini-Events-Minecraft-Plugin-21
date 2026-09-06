package com.boxpvp.events.commands;

import com.boxpvp.events.events.EventDefinition;
import com.boxpvp.events.events.EventManager;
import com.boxpvp.events.events.EventRegistry;
import com.boxpvp.events.listeners.GuiListener;
import com.boxpvp.events.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/** Admin-only command. Gated at the plugin.yml level too, so it never shows up for regular players. */
public class BoxEventsCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EventRegistry registry;
    private final EventManager eventManager;
    private final GuiListener guiListener;

    public BoxEventsCommand(JavaPlugin plugin, EventRegistry registry, EventManager eventManager, GuiListener guiListener) {
        this.plugin = plugin;
        this.registry = registry;
        this.eventManager = eventManager;
        this.guiListener = guiListener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!requireAdmin(sender)) return true;
        if (args.length == 0) { sendHelp(sender); return true; }
        String sub = args[0].toLowerCase();

        switch (sub) {
            case "gui" -> handleGui(sender);
            case "help" -> sendHelp(sender);
            case "reload" -> handleReload(sender);
            case "start" -> handleStart(sender, args);
            case "rename" -> handleRename(sender, args);
            case "stop" -> handleStop(sender);
            case "forcewin" -> handleForceWin(sender, args);
            case "wand" -> handleWand(sender, args);
            case "sethologram" -> handleSetHologram(sender);
            case "removehologram" -> handleRemoveHologram(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Msg.legacy("&6&l--- BoxPvpEvents (admin) ---"));
        sender.sendMessage(Msg.legacy("&e/boxevents gui &7- open the event manager GUI (locations, items, rewards, colours)"));
        sender.sendMessage(Msg.legacy("&e/boxevents rename <id> <name...> &7- set an event's display name/colour directly (works even if the GUI picker misbehaves)"));
        sender.sendMessage(Msg.legacy("&e/boxevents wand <id> <barrier|zone|mud|restricted|spawn-area|teamwall> &7- get the region-select wand"));
        sender.sendMessage(Msg.legacy("&e/boxevents start <id|random> &7- force-start a specific event, or a genuinely random one, right now"));
        sender.sendMessage(Msg.legacy("&e/boxevents stop &7- cancel the current event"));
        sender.sendMessage(Msg.legacy("&e/boxevents forcewin <player> &7- declare a winner manually"));
        sender.sendMessage(Msg.legacy("&e/boxevents sethologram &7- place the next-event hologram at your position"));
        sender.sendMessage(Msg.legacy("&e/boxevents removehologram &7- remove the hologram"));
        sender.sendMessage(Msg.legacy("&e/boxevents reload &7- reload config.yml"));
    }

    private void handleGui(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can open the GUI."); return; }
        guiListener.openMain(player);
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        registry.load();
        sender.sendMessage(Msg.legacy("&aConfig and events reloaded."));
    }

    private void handleStart(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.legacy("&cUsage: /boxevents start <id|random>")); return; }
        if (args[1].equalsIgnoreCase("random")) {
            boolean ok = eventManager.triggerRandomEvent();
            sender.sendMessage(Msg.legacy(ok ? "&aStarting a random event..." : "&cNo event is ready yet (or one is already running) - configure locations in the GUI first."));
            return;
        }
        EventDefinition def = registry.get(args[1]);
        if (def == null) { sender.sendMessage(Msg.legacy("&cUnknown event id.")); return; }
        if (!def.isReadyToRun()) {
            sender.sendMessage(Msg.legacy("&cThat event isn't fully configured yet - use the GUI (/boxevents gui)."));
            return;
        }
        boolean ok = eventManager.triggerEvent(def);
        sender.sendMessage(Msg.legacy(ok ? "&aStarting '" + def.getId() + "'..." : "&cAn event is already running."));
    }

    private void handleRename(CommandSender sender, String[] args) {
        if (args.length < 3) { sender.sendMessage(Msg.legacy("&cUsage: /boxevents rename <id> <new name...>")); return; }
        EventDefinition def = registry.get(args[1]);
        if (def == null) { sender.sendMessage(Msg.legacy("&cUnknown event id.")); return; }
        String name = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
        def.setDisplayName(name);
        registry.save();
        sender.sendMessage(Msg.legacy("&aName updated: &r").append(Msg.parseName(name)));
    }

    private void handleStop(CommandSender sender) {
        eventManager.forceStop();
        sender.sendMessage(Msg.legacy("&aCurrent event stopped and participants returned to spawn."));
    }

    private void handleForceWin(CommandSender sender, String[] args) {
        if (args.length < 2) { sender.sendMessage(Msg.legacy("&cUsage: /boxevents forcewin <player>")); return; }
        Player winner = Bukkit.getPlayerExact(args[1]);
        if (winner == null) { sender.sendMessage(Msg.legacy("&cPlayer not found/online.")); return; }
        if (eventManager.getSession() == null) { sender.sendMessage(Msg.legacy("&cNo event is currently running.")); return; }
        eventManager.declareWinner(winner);
    }

    private void handleWand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return; }
        if (args.length < 3 || !(args[2].equalsIgnoreCase("barrier") || args[2].equalsIgnoreCase("zone") || args[2].equalsIgnoreCase("mud") || args[2].equalsIgnoreCase("restricted") || args[2].equalsIgnoreCase("spawn-area") || args[2].equalsIgnoreCase("teamwall"))) {
            sender.sendMessage(Msg.legacy("&cUsage: /boxevents wand <id> <barrier|zone|mud|restricted|spawn-area|teamwall>"));
            return;
        }
        EventDefinition def = registry.get(args[1]);
        if (def == null) { sender.sendMessage(Msg.legacy("&cUnknown event id.")); return; }
        eventManager.beginWandSelection(player, def.getId(), args[2].toLowerCase());
        player.sendMessage(Msg.legacy("&6You received the Region Wand (a plain stick, tagged internally - it will never interfere with WorldEdit). Left-click a block = pos 1, right-click = pos 2. Each click saves immediately."));
    }

    private void handleSetHologram(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return; }
        var loc = player.getLocation();
        plugin.getConfig().set("hologram.enabled", true);
        plugin.getConfig().set("hologram.location.world", loc.getWorld().getName());
        plugin.getConfig().set("hologram.location.x", loc.getX());
        plugin.getConfig().set("hologram.location.y", loc.getY());
        plugin.getConfig().set("hologram.location.z", loc.getZ());
        plugin.saveConfig();
        eventManager.removeHologram();
        player.sendMessage(Msg.legacy("&aHologram placed here - it'll show the countdown to the next event."));
    }

    private void handleRemoveHologram(CommandSender sender) {
        plugin.getConfig().set("hologram.enabled", false);
        plugin.saveConfig();
        eventManager.removeHologram();
        sender.sendMessage(Msg.legacy("&cHologram removed."));
    }

    private boolean requireAdmin(CommandSender sender) {
        if (!sender.hasPermission("boxpvpevents.admin")) {
            sender.sendMessage(Msg.legacy("&cUnknown command."));
            return false;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("boxpvpevents.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("gui", "rename", "wand", "reload", "start", "stop", "forcewin", "sethologram", "removehologram", "help"));
            return filter(options, args[0]);
        }
        if (args.length == 2 && List.of("start", "wand", "rename").contains(args[0].toLowerCase())) {
            List<String> ids = new ArrayList<>(registry.all().keySet());
            if (args[0].equalsIgnoreCase("start")) ids.add("random");
            return filter(ids, args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("wand")) {
            return filter(List.of("barrier", "zone", "mud", "restricted", "spawn-area", "teamwall"), args[2]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("forcewin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList()), args[1]);
        }
        return options;
    }

    private List<String> filter(List<String> options, String prefix) {
        return options.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }
}
