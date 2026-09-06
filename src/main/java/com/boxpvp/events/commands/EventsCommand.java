package com.boxpvp.events.commands;

import com.boxpvp.events.events.EventDefinition;
import com.boxpvp.events.events.EventManager;
import com.boxpvp.events.events.EventRegistry;
import com.boxpvp.events.util.Msg;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.stream.Collectors;

/** Player-facing command. Deliberately separate from /boxevents so admin subcommands never show up for regular players. */
public class EventsCommand implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EventRegistry registry;
    private final EventManager eventManager;

    public EventsCommand(JavaPlugin plugin, EventRegistry registry, EventManager eventManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.eventManager = eventManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length > 0 ? args[0].toLowerCase() : "help";
        switch (sub) {
            case "join" -> handleJoin(sender);
            case "leave" -> handleLeave(sender);
            case "list" -> handleList(sender);
            case "next" -> handleNext(sender);
            default -> sendHelp(sender);
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Msg.legacy("&b&l--- Events ---"));
        sender.sendMessage(Msg.legacy("&e/events join &7- join the current event"));
        sender.sendMessage(Msg.legacy("&e/events leave &7- leave the event you're in"));
        sender.sendMessage(Msg.legacy("&e/events list &7- list configured events"));
        sender.sendMessage(Msg.legacy("&e/events next &7- see time until the next random event"));
    }

    private void handleJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can join events."); return; }
        if (!player.hasPermission("boxpvpevents.join")) {
            player.sendMessage(Msg.legacy("&cYou don't have permission to join events."));
            return;
        }
        var result = eventManager.join(player);
        switch (result) {
            case JOINED -> {
                String name = Msg.plainName(eventManager.getSession().getDefinition().getDisplayName());
                player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "joined", "event", name)));
            }
            case ALREADY_JOINED -> player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "already-joined")));
            case INVENTORY_NOT_EMPTY -> player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "inventory-not-empty")));
            case NO_EVENT -> player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "no-active-event")));
            case USE_CHAT_JACKPOT -> player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "jackpot-join-hint", "time", eventManager.getSessionDurationFormatted())));
            case NOT_JOINABLE -> player.sendMessage(Msg.legacy("&eThis event has no join step - just head to the arena!"));
        }
    }

    private void handleLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Only players can use this command."); return; }
        var result = eventManager.leave(player);
        if (result == EventManager.LeaveResult.LEFT) {
            player.sendMessage(Msg.legacy("&cYou left the event and were returned to spawn."));
        } else {
            player.sendMessage(Msg.legacy("&cYou're not currently in an event."));
        }
    }

    private void handleList(CommandSender sender) {
        sender.sendMessage(Msg.legacy("&6Configured events:"));
        for (EventDefinition def : registry.all().values()) {
            sender.sendMessage(Msg.legacy(" &7- &f" + def.getId() + " &7(" + def.getType() + ") "
                    + (def.isReadyToRun() ? "&a[ready]" : "&c[needs location]")));
        }
    }

    private void handleNext(CommandSender sender) {
        sender.sendMessage(Msg.legacy("&8&m                                  "));
        sender.sendMessage(Msg.legacy("&b&l\u2726 " + eventManager.getNextEventSummary()));
        sender.sendMessage(Msg.legacy("&8&m                                  "));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("join", "leave", "list", "next").stream()
                    .filter(o -> o.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        return List.of();
    }
}
