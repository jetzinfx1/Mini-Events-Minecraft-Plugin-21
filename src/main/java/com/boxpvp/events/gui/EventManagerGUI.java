package com.boxpvp.events.gui;

import com.boxpvp.events.events.EventDefinition;
import com.boxpvp.events.events.EventRegistry;
import com.boxpvp.events.events.EventType;
import com.boxpvp.events.util.ItemBuilder;
import com.boxpvp.events.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

/** Main event list: click an event to open its detail menu, or use the bottom row buttons. */
public class EventManagerGUI {

    public static final String TITLE = "\u00a76\u00a7lBoxPvP Events Manager";
    public static final int ADD_SLOT = 49;
    public static final int SPAWN_SLOT = 45;

    private final JavaPlugin plugin;
    private final EventRegistry registry;

    public EventManagerGUI(JavaPlugin plugin, EventRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void open(Player player) {
        Inventory inv = plugin.getServer().createInventory(null, 54, Msg.legacy(TITLE));

        int slot = 0;
        for (EventDefinition def : registry.all().values()) {
            if (slot >= 45) break;
            inv.setItem(slot++, buildEventIcon(def));
        }

        ItemStack add = new ItemBuilder(Material.EMERALD, 1)
                .name("&a&l+ Add New Event")
                .lore(List.of("&7Click, then type the id of", "&7your new custom event in chat.", "&7(letters, numbers, underscores only)"))
                .build();
        inv.setItem(ADD_SLOT, add);

        ItemStack spawn = new ItemBuilder(Material.RED_BED, 1)
                .name("&c&lSet Return Spawn")
                .lore(List.of("&7By default this follows your world's", "&7actual spawn point (e.g. /setspawn).",
                        "&7Click while standing somewhere to use", "&7that custom position instead.",
                        "&7Shift-click: go back to following the world spawn."))
                .build();
        inv.setItem(SPAWN_SLOT, spawn);

        player.openInventory(inv);
    }

    private ItemStack buildEventIcon(EventDefinition def) {
        Material icon = switch (def.getType()) {
            case PARKOUR -> Material.GOLDEN_BOOTS;
            case KOTH -> Material.BEACON;
            case DODGEBOLT -> Material.BOW;
            case STRAFE -> Material.ARROW;
            case PVP_TOURNAMENT -> Material.IRON_SWORD;
            case DODGEBALL -> Material.FIRE_CHARGE;
            case VOLLEY_CHARGE -> Material.WIND_CHARGE;
            case JACKPOT -> Material.GOLD_INGOT;
            case CUSTOM -> Material.PAPER;
        };
        List<String> lore = new ArrayList<>();
        lore.add("&7Type: &f" + def.getType().name());
        if (def.needsLocation()) {
            lore.add(def.getArenaLocation() != null ? "&aArena set" : "&cArena NOT set");
        } else if (def.isTeamEvent()) {
            lore.add(def.isReadyToRun() ? "&aBlue/Red spawns set" : "&cBlue/Red spawns NOT set");
        }
        lore.add(" ");
        lore.add("&eClick to open its settings menu");

        return new ItemBuilder(icon, 1)
                .name(Msg.parseName(def.getDisplayName()))
                .lore(lore)
                .build();
    }

    public EventDefinition getEventAt(int slot) {
        if (slot < 0 || slot >= 45) return null;
        List<EventDefinition> defs = new ArrayList<>(registry.all().values());
        return slot < defs.size() ? defs.get(slot) : null;
    }

    public void createCustomEvent(String id) {
        EventDefinition def = new EventDefinition(id.toLowerCase(), "&d" + id, EventType.CUSTOM, null);
        registry.add(def);
    }
}
