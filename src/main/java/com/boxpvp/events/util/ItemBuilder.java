package com.boxpvp.events.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

public class ItemBuilder {

    private final ItemStack stack;
    private final ItemMeta meta;

    public ItemBuilder(Material material, int amount) {
        this.stack = new ItemStack(material, Math.max(1, amount));
        this.meta = stack.getItemMeta();
    }

    public ItemBuilder name(String name) {
        if (name != null && meta != null) {
            meta.setDisplayName(Msg.color(name));
        }
        return this;
    }

    /** Component-based name - use this for anything that might contain MiniMessage tags (gradients etc.), since setDisplayName(String) only understands legacy &codes and would show raw tags as literal text. */
    public ItemBuilder name(net.kyori.adventure.text.Component name) {
        if (name != null && meta != null) {
            meta.displayName(name.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        }
        return this;
    }

    public ItemBuilder lore(List<String> lore) {
        if (lore != null && meta != null) {
            meta.setLore(lore.stream().map(Msg::color).collect(Collectors.toList()));
        }
        return this;
    }

    public ItemBuilder tag(NamespacedKey key, String value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, value);
        }
        return this;
    }

    /** Component-based lore - use for any line that might contain MiniMessage tags. */
    public ItemBuilder loreLines(List<net.kyori.adventure.text.Component> lines) {
        if (lines != null && meta != null) {
            meta.lore(lines.stream()
                    .map(c -> c.decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
        }
        return this;
    }

    public ItemStack build() {
        stack.setItemMeta(meta);
        return stack;
    }
}
