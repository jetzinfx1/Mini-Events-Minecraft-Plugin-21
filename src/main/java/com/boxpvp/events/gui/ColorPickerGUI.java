package com.boxpvp.events.gui;

import com.boxpvp.events.util.ItemBuilder;
import com.boxpvp.events.util.Msg;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Solid-colour palette for event names. For gradients, admins type a MiniMessage string in chat instead. */
public class ColorPickerGUI {

    public static final String TITLE_PREFIX = "\u00a75\u00a7lColors: ";

    private final JavaPlugin plugin;
    /** slot -> legacy colour code, filled when open() runs */
    private final List<Character> slotColors = new ArrayList<>();

    public ColorPickerGUI(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public static String titleFor(String id) {
        return TITLE_PREFIX + id;
    }

    public static String parseId(String title) {
        return title.startsWith(TITLE_PREFIX) ? title.substring(TITLE_PREFIX.length()) : null;
    }

    public void open(Player player, String currentDisplayName) {
        Inventory inv = plugin.getServer().createInventory(null, 27, Msg.legacy(titleFor("")));
        slotColors.clear();

        int slot = 0;
        for (Map.Entry<String, Character> entry : Msg.COLOR_PALETTE.entrySet()) {
            inv.setItem(slot, new ItemBuilder(dyeFor(entry.getValue()), 1)
                    .name("&" + entry.getValue() + "&l" + entry.getKey())
                    .lore(List.of("&7Click to apply this colour"))
                    .build());
            slotColors.add(entry.getValue());
            slot++;
        }

        inv.setItem(20, new ItemBuilder(Material.PAPER, 1)
                .name("&d&lCustom / Gradient")
                .lore(List.of("&7Click, then type a full display name", "&7in chat. Supports & colour codes AND",
                        "&7MiniMessage tags, e.g.:", "&f<gradient:red:blue>Parkour</gradient>"))
                .build());

        inv.setItem(18, new ItemBuilder(Material.ARROW, 1).name("&7&lBack").build());

        player.openInventory(inv);
    }

    /** Which colour code a palette slot represents, or null for non-colour slots. */
    public Character colorAt(int slot) {
        return slot >= 0 && slot < slotColors.size() ? slotColors.get(slot) : null;
    }

    private Material dyeFor(char code) {
        return switch (code) {
            case 'f' -> Material.WHITE_DYE;
            case '7' -> Material.LIGHT_GRAY_DYE;
            case '8' -> Material.GRAY_DYE;
            case 'c' -> Material.RED_DYE;
            case '4' -> Material.RED_DYE;
            case 'a' -> Material.LIME_DYE;
            case '2' -> Material.GREEN_DYE;
            case '9' -> Material.LIGHT_BLUE_DYE;
            case '1' -> Material.BLUE_DYE;
            case 'e' -> Material.YELLOW_DYE;
            case '6' -> Material.ORANGE_DYE;
            case 'b' -> Material.CYAN_DYE;
            case '3' -> Material.CYAN_DYE;
            case '5' -> Material.PURPLE_DYE;
            case 'd' -> Material.PINK_DYE;
            case '0' -> Material.BLACK_DYE;
            default -> Material.GRAY_DYE;
        };
    }
}
