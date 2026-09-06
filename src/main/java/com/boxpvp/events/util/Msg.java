package com.boxpvp.events.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

public class Msg {

    /** Solid colour palette offered in the GUI colour picker: label -> legacy code. */
    public static final Map<String, Character> COLOR_PALETTE = new LinkedHashMap<>();
    static {
        COLOR_PALETTE.put("White", 'f');
        COLOR_PALETTE.put("Gray", '7');
        COLOR_PALETTE.put("Dark Gray", '8');
        COLOR_PALETTE.put("Red", 'c');
        COLOR_PALETTE.put("Dark Red", '4');
        COLOR_PALETTE.put("Green", 'a');
        COLOR_PALETTE.put("Dark Green", '2');
        COLOR_PALETTE.put("Blue", '9');
        COLOR_PALETTE.put("Dark Blue", '1');
        COLOR_PALETTE.put("Yellow", 'e');
        COLOR_PALETTE.put("Gold", '6');
        COLOR_PALETTE.put("Aqua", 'b');
        COLOR_PALETTE.put("Dark Aqua", '3');
        COLOR_PALETTE.put("Purple", '5');
        COLOR_PALETTE.put("Pink", 'd');
        COLOR_PALETTE.put("Black", '0');
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Fetches a message from config.yml under messages.<key>, applies prefix + placeholders, colours it. */
    public static String get(FileConfiguration config, String key, String... placeholders) {
        String prefix = config.getString("messages.prefix", "");
        String raw = config.getString("messages." + key, key);
        String result = prefix + raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return color(result);
    }

    /** Same as get(), but WITHOUT the messages.prefix - use for any fragment that gets appended onto an already-prefixed message, so the prefix doesn't show up twice in one line. */
    public static String getRaw(FileConfiguration config, String key, String... placeholders) {
        String raw = config.getString("messages." + key, key);
        String result = raw;
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }
        return color(result);
    }

    /** Looks up a configurable sound by key under sounds.<key>, falling back safely if unset/invalid. */
    public static org.bukkit.Sound sound(FileConfiguration config, String key, org.bukkit.Sound fallback) {
        String name = config.getString("sounds." + key);
        if (name == null || name.isBlank()) return fallback;
        try {
            return org.bukkit.Sound.valueOf(name.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    public static Component legacy(String coloredString) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(coloredString);
    }

    /**
     * Parses an event display name. Supports both legacy "&c" codes and
     * MiniMessage tags (gradients, hex, etc.) - e.g. "<gradient:red:blue>Parkour</gradient>".
     * MiniMessage is used automatically when the string contains a "<...>" tag.
     */
    public static Component parseName(String raw) {
        if (raw == null) return Component.empty();
        if (raw.contains("<") && raw.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(raw);
            } catch (Exception ignored) {
                // fall through to legacy parsing if it wasn't valid MiniMessage
            }
        }
        return legacy(raw);
    }

    /** Plain, uncoloured text of a display name - safe for titles/logs that need to strip formatting. */
    public static String plainName(String raw) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(parseName(raw));
    }

    /** Wraps plain text in a per-character rainbow of legacy colour codes, kept bold throughout. */
    public static String rainbow(String plainText) {
        char[] palette = {'c', '6', 'e', 'a', 'b', '9', 'd'};
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char c : plainText.toCharArray()) {
            if (c != ' ') {
                sb.append('&').append(palette[i % palette.length]).append("&l");
                i++;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Builds a clickable "[CLICK TO JOIN]" style component that runs a command. */
    public static Component clickable(String text, String command) {
        return legacy(text).clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click to join!", NamedTextColor.GREEN)));
    }

    public static void broadcast(Component component) {
        Bukkit.getServer().sendMessage(component);
    }
}
