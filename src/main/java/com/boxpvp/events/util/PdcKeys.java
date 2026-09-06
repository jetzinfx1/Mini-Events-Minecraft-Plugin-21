package com.boxpvp.events.util;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

/** Centralized PersistentDataContainer keys so every class tags/reads items the same way. */
public class PdcKeys {

    public final NamespacedKey tempItem;
    public final NamespacedKey visibilityToggle;
    public final NamespacedKey wandTool;
    public final NamespacedKey guiPlaceholder;
    public final NamespacedKey ammoTag;
    public final NamespacedKey ammoTeam;
    public final NamespacedKey ammoResolved;
    public final NamespacedKey noHunger;

    public PdcKeys(JavaPlugin plugin) {
        this.tempItem = new NamespacedKey(plugin, "temp_item");
        this.visibilityToggle = new NamespacedKey(plugin, "visibility_toggle");
        this.wandTool = new NamespacedKey(plugin, "wand_tool");
        this.guiPlaceholder = new NamespacedKey(plugin, "gui_placeholder");
        this.ammoTag = new NamespacedKey(plugin, "ammo_tag");
        this.ammoTeam = new NamespacedKey(plugin, "ammo_team");
        this.ammoResolved = new NamespacedKey(plugin, "ammo_resolved");
        this.noHunger = new NamespacedKey(plugin, "no_hunger");
    }
}
