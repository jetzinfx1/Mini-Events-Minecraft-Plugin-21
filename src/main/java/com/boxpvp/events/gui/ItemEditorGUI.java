package com.boxpvp.events.gui;

import com.boxpvp.events.events.EventDefinition;
import com.boxpvp.events.util.ItemBuilder;
import com.boxpvp.events.util.Msg;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Generic 45-slot editor: pre-filled with the event's current temp items,
 * rewards, or kill rewards. Admins drag items from their own inventory into
 * it; whatever remains when the inventory is closed becomes the saved list.
 * This is also how the exact crate key item gets matched - the admin pushes
 * their real key item in here, no guessing involved.
 *
 * In TEMP mode only, the bottom row also has 5 dedicated equipment slots
 * (helmet/chest/legs/boots/offhand) - whatever's placed there always equips
 * in exactly that slot regardless of Material, so custom gear plugins (a
 * skull as a helmet, a nether star as an offhand item, etc.) work correctly
 * instead of relying on guessing from vanilla armor materials.
 */
public class ItemEditorGUI {

    public enum Mode { TEMP, REWARDS, KILL_REWARDS }

    public static final String TEMP_PREFIX = "\u00a7b\u00a7lTemp Items: ";
    public static final String REWARD_PREFIX = "\u00a7a\u00a7lRewards: ";
    public static final String KILL_REWARD_PREFIX = "\u00a7d\u00a7lKill Rewards: ";
    private static final int SIZE = 45; // general item area

    public static final int HELMET_SLOT = 45;
    public static final int CHESTPLATE_SLOT = 46;
    public static final int LEGGINGS_SLOT = 47;
    public static final int BOOTS_SLOT = 48;
    public static final int OFFHAND_SLOT = 49;

    private final JavaPlugin plugin;
    private final NamespacedKey placeholderKey;

    public ItemEditorGUI(JavaPlugin plugin) {
        this.plugin = plugin;
        this.placeholderKey = new NamespacedKey(plugin, "gui_placeholder");
    }

    public static String titleFor(String id, Mode mode) {
        String prefix = switch (mode) {
            case TEMP -> TEMP_PREFIX;
            case REWARDS -> REWARD_PREFIX;
            case KILL_REWARDS -> KILL_REWARD_PREFIX;
        };
        return prefix + id;
    }

    /** Returns [id, mode.name()] or null if this isn't one of our editor titles. */
    public static String[] parse(String title) {
        if (title.startsWith(KILL_REWARD_PREFIX)) return new String[]{title.substring(KILL_REWARD_PREFIX.length()), Mode.KILL_REWARDS.name()};
        if (title.startsWith(REWARD_PREFIX)) return new String[]{title.substring(REWARD_PREFIX.length()), Mode.REWARDS.name()};
        if (title.startsWith(TEMP_PREFIX)) return new String[]{title.substring(TEMP_PREFIX.length()), Mode.TEMP.name()};
        return null;
    }

    public void open(Player player, EventDefinition def, Mode mode) {
        Inventory inv = plugin.getServer().createInventory(null, 54, Msg.legacy(titleFor(def.getId(), mode)));
        List<ItemStack> current = switch (mode) {
            case TEMP -> def.getTempItems();
            case REWARDS -> def.getRewards();
            case KILL_REWARDS -> def.getKillRewards();
        };
        for (int i = 0; i < current.size() && i < SIZE; i++) {
            inv.setItem(i, current.get(i).clone());
        }

        if (mode == Mode.TEMP) {
            inv.setItem(HELMET_SLOT, equipmentSlotItem(def.getTempHelmet(), Material.IRON_HELMET, "Helmet"));
            inv.setItem(CHESTPLATE_SLOT, equipmentSlotItem(def.getTempChestplate(), Material.IRON_CHESTPLATE, "Chestplate"));
            inv.setItem(LEGGINGS_SLOT, equipmentSlotItem(def.getTempLeggings(), Material.IRON_LEGGINGS, "Leggings"));
            inv.setItem(BOOTS_SLOT, equipmentSlotItem(def.getTempBoots(), Material.IRON_BOOTS, "Boots"));
            inv.setItem(OFFHAND_SLOT, equipmentSlotItem(def.getTempOffhand(), Material.SHIELD, "Offhand"));
        }

        player.openInventory(inv);
    }

    private ItemStack equipmentSlotItem(ItemStack configured, Material guideIcon, String label) {
        if (configured != null) return configured.clone();
        return new ItemBuilder(guideIcon, 1)
                .name("&7(Empty - drag a " + label + " here)")
                .lore(List.of("&8This item will always be equipped", "&8as the player's " + label.toLowerCase() + ",",
                        "&8regardless of its Material type."))
                .tag(placeholderKey, "true")
                .build();
    }

    private boolean isPlaceholder(ItemStack stack) {
        if (stack == null || stack.getItemMeta() == null) return false;
        return stack.getItemMeta().getPersistentDataContainer().has(placeholderKey, PersistentDataType.STRING);
    }

    /** Extracts the saved general item list from a closed editor inventory (first SIZE slots only). */
    public List<ItemStack> extract(Inventory inv) {
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < SIZE && i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack != null && stack.getType() != Material.AIR) items.add(stack.clone());
        }
        return items;
    }

    /** Extracts one dedicated equipment slot's configured item, or null if it's still just the empty-slot guide icon. */
    public ItemStack extractEquipmentSlot(Inventory inv, int slot) {
        if (slot >= inv.getSize()) return null;
        ItemStack stack = inv.getItem(slot);
        if (stack == null || stack.getType() == Material.AIR || isPlaceholder(stack)) return null;
        return stack.clone();
    }
}
