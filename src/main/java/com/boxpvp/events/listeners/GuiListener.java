package com.boxpvp.events.listeners;

import com.boxpvp.events.events.EventDefinition;
import com.boxpvp.events.events.EventManager;
import com.boxpvp.events.events.EventRegistry;
import com.boxpvp.events.events.EventType;
import com.boxpvp.events.gui.ColorPickerGUI;
import com.boxpvp.events.gui.EventDetailGUI;
import com.boxpvp.events.gui.EventManagerGUI;
import com.boxpvp.events.gui.ItemEditorGUI;
import com.boxpvp.events.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orchestrates every custom GUI in the plugin. Unlike a title-text-matching
 * approach (fragile - depends on exact Component round-tripping through the
 * client), this tracks "which of our GUIs is this player currently looking
 * at" directly in server-side state, set the moment WE open an inventory for
 * them and cleared the moment it closes. That is what actually makes clicks
 * (including something as simple as the Back button) reliably do something.
 */
public class GuiListener implements Listener {

    private enum GuiType { MAIN, DETAIL, COLOR, ITEM_EDITOR }

    private record OpenGui(GuiType type, String eventId, ItemEditorGUI.Mode itemMode) {}

    private final JavaPlugin plugin;
    private final EventRegistry registry;
    private final EventManager eventManager;
    private final EventManagerGUI mainGui;
    private final EventDetailGUI detailGui;
    private final ColorPickerGUI colorGui;
    private final ItemEditorGUI itemGui;

    private final Map<UUID, OpenGui> openGuis = new HashMap<>();
    /** player -> "ADD_EVENT" | "RENAME:<id>" | "DURATION:<id>" - what their next chat message means. */
    private final Map<UUID, String> chatMode = new HashMap<>();

    public GuiListener(JavaPlugin plugin, EventRegistry registry, EventManager eventManager,
                        EventManagerGUI mainGui, EventDetailGUI detailGui, ColorPickerGUI colorGui, ItemEditorGUI itemGui) {
        this.plugin = plugin;
        this.registry = registry;
        this.eventManager = eventManager;
        this.mainGui = mainGui;
        this.detailGui = detailGui;
        this.colorGui = colorGui;
        this.itemGui = itemGui;
    }

    // ---------------------------------------------------------------
    // Entry points - every place a GUI gets opened goes through here so the
    // tracking map always stays correct.
    // ---------------------------------------------------------------

    public void openMain(Player player) {
        mainGui.open(player);
        openGuis.put(player.getUniqueId(), new OpenGui(GuiType.MAIN, null, null));
    }

    private void openDetail(Player player, EventDefinition def) {
        detailGui.open(player, def);
        openGuis.put(player.getUniqueId(), new OpenGui(GuiType.DETAIL, def.getId(), null));
    }

    private void openColor(Player player, EventDefinition def) {
        colorGui.open(player, def.getDisplayName());
        openGuis.put(player.getUniqueId(), new OpenGui(GuiType.COLOR, def.getId(), null));
    }

    private void openItemEditor(Player player, EventDefinition def, ItemEditorGUI.Mode mode) {
        itemGui.open(player, def, mode);
        openGuis.put(player.getUniqueId(), new OpenGui(GuiType.ITEM_EDITOR, def.getId(), mode));
    }

    // ---------------------------------------------------------------
    // Clicks
    // ---------------------------------------------------------------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        OpenGui open = openGuis.get(player.getUniqueId());
        if (open == null) return; // not one of our GUIs - leave vanilla behaviour alone

        switch (open.type()) {
            case MAIN -> {
                e.setCancelled(true);
                handleMainClick(player, e.getRawSlot(), e.isShiftClick());
            }
            case DETAIL -> {
                e.setCancelled(true);
                EventDefinition def = registry.get(open.eventId());
                if (def != null) handleDetailClick(player, def, e.getRawSlot(), e.isShiftClick());
                else openMain(player);
            }
            case COLOR -> {
                e.setCancelled(true);
                EventDefinition def = registry.get(open.eventId());
                if (def != null) handleColorClick(player, def, e.getRawSlot());
                else openMain(player);
            }
            case ITEM_EDITOR -> {
                // Deliberately NOT cancelled - it behaves like a normal chest so
                // admins can freely drag items from their inventory into it.
            }
        }
    }

    private void handleMainClick(Player player, int slot, boolean shift) {
        if (slot == EventManagerGUI.ADD_SLOT) {
            chatMode.put(player.getUniqueId(), "ADD_EVENT");
            player.closeInventory();
            player.sendMessage(Msg.legacy("&eType the id for your new event in chat (e.g. &fspleef&e). Type &ccancel&e to abort."));
            return;
        }
        if (slot == EventManagerGUI.SPAWN_SLOT) {
            if (shift) {
                plugin.getConfig().set("return-spawn.use-world-spawn", true);
                plugin.saveConfig();
                player.sendMessage(Msg.legacy("&aReturn spawn reset to follow the world's actual spawn point (e.g. from /setspawn)."));
                player.closeInventory();
                return;
            }
            plugin.getConfig().set("return-spawn.use-world-spawn", false);
            plugin.getConfig().set("return-spawn.world", player.getWorld().getName());
            plugin.getConfig().set("return-spawn.x", player.getLocation().getX());
            plugin.getConfig().set("return-spawn.y", player.getLocation().getY());
            plugin.getConfig().set("return-spawn.z", player.getLocation().getZ());
            plugin.getConfig().set("return-spawn.yaw", (double) player.getLocation().getYaw());
            plugin.getConfig().set("return-spawn.pitch", (double) player.getLocation().getPitch());
            plugin.saveConfig();
            player.sendMessage(Msg.legacy("&aReturn spawn set to your current position. Every event sends players here. (Shift-click this button to go back to following the world's spawn point instead.)"));
            player.closeInventory();
            return;
        }
        EventDefinition def = mainGui.getEventAt(slot);
        if (def != null) openDetail(player, def);
    }

    private void handleDetailClick(Player player, EventDefinition def, int slot, boolean shift) {
        boolean isKoth = def.getType() == EventType.KOTH;
        boolean isParkour = def.getType() == EventType.PARKOUR;
        boolean isTeamEvent = def.isTeamEvent();

        if (isTeamEvent && slot == EventDetailGUI.BLUE_SPAWN_SLOT) {
            if (shift) { def.setBlueSpawnLocation(null); registry.save(); player.sendMessage(Msg.legacy("&cBlue team spawn cleared.")); }
            else { def.setBlueSpawnLocation(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aBlue team spawn set.")); }
            openDetail(player, def);
        } else if (isTeamEvent && slot == EventDetailGUI.RED_SPAWN_SLOT) {
            if (shift) { def.setRedSpawnLocation(null); registry.save(); player.sendMessage(Msg.legacy("&cRed team spawn cleared.")); }
            else { def.setRedSpawnLocation(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aRed team spawn set.")); }
            openDetail(player, def);
        } else if (isTeamEvent && slot == EventDetailGUI.AMMO_BLUE_POINT_SLOT) {
            if (shift) { def.setBlueAmmoPoint(null); registry.save(); player.sendMessage(Msg.legacy("&cBlue ammo generator cleared.")); }
            else { def.setBlueAmmoPoint(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aBlue ammo generator set.")); }
            openDetail(player, def);
        } else if (isTeamEvent && slot == EventDetailGUI.AMMO_RED_POINT_SLOT) {
            if (shift) { def.setRedAmmoPoint(null); registry.save(); player.sendMessage(Msg.legacy("&cRed ammo generator cleared.")); }
            else { def.setRedAmmoPoint(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aRed ammo generator set.")); }
            openDetail(player, def);
        } else if (isTeamEvent && slot == EventDetailGUI.SECOND_WAND_SLOT) {
            if (shift) {
                def.setTeamWallPos1(null); def.setTeamWallPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cTeam divider wall cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "teamwall");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (isTeamEvent && slot == EventDetailGUI.BLUE_SPAWN_AREA_WAND_SLOT) {
            if (shift) {
                def.setBlueSpawnAreaPos1(null); def.setBlueSpawnAreaPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cBlue spawn area cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "blue-spawn-area");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (isTeamEvent && slot == EventDetailGUI.RED_SPAWN_AREA_WAND_SLOT) {
            if (shift) {
                def.setRedSpawnAreaPos1(null); def.setRedSpawnAreaPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cRed spawn area cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "red-spawn-area");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (!isTeamEvent && slot == EventDetailGUI.ARENA_SLOT) {
            if (shift) { def.setArenaLocation(null); registry.save(); player.sendMessage(Msg.legacy("&cArena location cleared.")); }
            else { def.setArenaLocation(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aArena location set.")); }
            openDetail(player, def);
        } else if (slot == EventDetailGUI.LOBBY_SLOT) {
            if (shift) { def.setLobbyLocation(null); registry.save(); player.sendMessage(Msg.legacy("&cLobby location cleared.")); }
            else { def.setLobbyLocation(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aLobby location set.")); }
            openDetail(player, def);
        } else if (slot == EventDetailGUI.BARRIER_WAND_SLOT) {
            if (shift) {
                def.setBarrierPos1(null); def.setBarrierPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cBarrier region cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "barrier");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2. It won't interfere with WorldEdit."));
            }
        } else if (slot == EventDetailGUI.SECOND_WAND_SLOT && isKoth) {
            if (shift) {
                def.setFinishPos1(null); def.setFinishPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cCapture zone cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "zone");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (slot == EventDetailGUI.MUD_WAND_SLOT && isParkour) {
            if (shift) {
                def.setMudPos1(null); def.setMudPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cMud region cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "mud");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (slot == EventDetailGUI.CHECKPOINT_SLOT && isParkour) {
            player.getInventory().addItem(new com.boxpvp.events.util.ItemBuilder(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 16)
                    .name("&6&lCheckpoint Saver").build());
            player.sendMessage(Msg.legacy("&6Got 16 Checkpoint Saver plates. Place them along the course."));
        } else if (slot == EventDetailGUI.WIN_PLATE_SLOT && isParkour) {
            player.getInventory().addItem(new com.boxpvp.events.util.ItemBuilder(Material.CHERRY_PRESSURE_PLATE, 1)
                    .name("&d&lWinner Checkpoint").build());
            player.sendMessage(Msg.legacy("&dGot the Winner Checkpoint plate. Place it at the finish."));
        } else if (slot == EventDetailGUI.DURATION_SLOT) {
            if (shift) {
                def.setCustomDurationSeconds(null); registry.save();
                player.sendMessage(Msg.legacy("&aJoin duration reset to the default."));
                openDetail(player, def);
            } else {
                chatMode.put(player.getUniqueId(), "DURATION:" + def.getId());
                player.closeInventory();
                player.sendMessage(Msg.legacy("&eType the new join duration in seconds (e.g. &f60&e). Type &ccancel&e to abort."));
            }
        } else if (slot == EventDetailGUI.SPECTATOR_SLOT) {
            if (shift) { def.setSpectatorLocation(null); registry.save(); player.sendMessage(Msg.legacy("&cSpectator area cleared.")); }
            else { def.setSpectatorLocation(player.getLocation()); registry.save(); player.sendMessage(Msg.legacy("&aSpectator area set.")); }
            openDetail(player, def);
        } else if (slot == EventDetailGUI.SPAWN_AREA_WAND_SLOT) {
            if (shift) {
                def.setSpawnAreaPos1(null); def.setSpawnAreaPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cRandom spawn area cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "spawn-area");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (slot == EventDetailGUI.TEMP_ITEMS_SLOT) {
            openItemEditor(player, def, ItemEditorGUI.Mode.TEMP);
        } else if (slot == EventDetailGUI.REWARDS_SLOT) {
            openItemEditor(player, def, ItemEditorGUI.Mode.REWARDS);
        } else if (slot == EventDetailGUI.KILL_REWARDS_SLOT) {
            openItemEditor(player, def, ItemEditorGUI.Mode.KILL_REWARDS);
        } else if (slot == EventDetailGUI.RECOLOR_SLOT) {
            openColor(player, def);
        } else if (slot == EventDetailGUI.DELETE_EVENT_SLOT) {
            if (shift) {
                registry.remove(def.getId());
                player.sendMessage(Msg.legacy("&cDeleted event '" + def.getId() + "'."));
                openMain(player);
            } else {
                player.sendMessage(Msg.legacy("&cShift-click to confirm deletion."));
            }
        } else if (slot == EventDetailGUI.RESTRICTED_WAND_SLOT) {
            if (shift) {
                def.setRestrictedPos1(null); def.setRestrictedPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cNo-commands zone cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "restricted");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (slot == EventDetailGUI.NO_HUNGER_WAND_SLOT) {
            if (shift) {
                def.setNoHungerPos1(null); def.setNoHungerPos2(null); registry.save();
                player.sendMessage(Msg.legacy("&cNo-hunger zone cleared."));
                openDetail(player, def);
            } else {
                player.closeInventory();
                eventManager.beginWandSelection(player, def.getId(), "no-hunger");
                player.sendMessage(Msg.legacy("&6You received the Region Wand. Left-click = pos 1, right-click = pos 2."));
            }
        } else if (slot == EventDetailGUI.BACK_SLOT) {
            openMain(player);
        }
    }

    private void handleColorClick(Player player, EventDefinition def, int slot) {
        if (slot == 20) { // custom / gradient via chat
            chatMode.put(player.getUniqueId(), "RENAME:" + def.getId());
            player.closeInventory();
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            player.sendMessage(Msg.legacy("&eType the new display name in chat (colours/gradients supported). Type &ccancel&e to abort."));
            return;
        }
        if (slot == 18) { // back
            openDetail(player, def);
            return;
        }
        Character code = colorGui.colorAt(slot);
        if (code == null) return;

        String plain = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', def.getDisplayName()));
        def.setDisplayName("&" + code + plain);
        registry.save();
        player.sendMessage(Msg.legacy("&aColour updated."));
        openDetail(player, def);
    }

    // ---------------------------------------------------------------
    // Close (only the item/reward editor needs to persist anything here)
    // ---------------------------------------------------------------

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player player)) return;
        OpenGui open = openGuis.remove(player.getUniqueId());
        if (open == null || open.type() != GuiType.ITEM_EDITOR) return;

        EventDefinition def = registry.get(open.eventId());
        if (def == null) return;

        List<ItemStack> items = itemGui.extract(e.getInventory());
        String label;
        switch (open.itemMode()) {
            case TEMP -> {
                def.setTempItems(items);
                def.setTempHelmet(itemGui.extractEquipmentSlot(e.getInventory(), ItemEditorGUI.HELMET_SLOT));
                def.setTempChestplate(itemGui.extractEquipmentSlot(e.getInventory(), ItemEditorGUI.CHESTPLATE_SLOT));
                def.setTempLeggings(itemGui.extractEquipmentSlot(e.getInventory(), ItemEditorGUI.LEGGINGS_SLOT));
                def.setTempBoots(itemGui.extractEquipmentSlot(e.getInventory(), ItemEditorGUI.BOOTS_SLOT));
                def.setTempOffhand(itemGui.extractEquipmentSlot(e.getInventory(), ItemEditorGUI.OFFHAND_SLOT));
                label = "Temporary items";
            }
            case REWARDS -> {
                def.setRewards(items);
                label = "Rewards";
            }
            default -> {
                def.setKillRewards(items);
                label = "Kill rewards";
            }
        }
        registry.save();
        player.sendMessage(Msg.legacy("&a" + label + " for '" + def.getId() + "' saved (" + items.size() + " item(s))."));
    }

    // ---------------------------------------------------------------
    // Chat capture: add-event id, rename/gradient, custom duration
    // ---------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent e) {
        Player player = e.getPlayer();
        String mode = chatMode.get(player.getUniqueId());
        if (mode == null) return;

        e.setCancelled(true);
        String message = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            chatMode.remove(player.getUniqueId());

            if (mode.equals("ADD_EVENT")) {
                if (message.equalsIgnoreCase("cancel")) { player.sendMessage(Msg.legacy("&cCancelled.")); return; }
                if (!message.matches("[a-zA-Z0-9_]{2,32}")) {
                    player.sendMessage(Msg.legacy("&cInvalid id - use only letters, numbers and underscores (2-32 chars)."));
                    return;
                }
                if (registry.get(message) != null) { player.sendMessage(Msg.legacy("&cAn event with that id already exists.")); return; }
                mainGui.createCustomEvent(message);
                player.sendMessage(Msg.legacy("&aCreated custom event '" + message + "'. Open &f/boxevents gui &ato configure it."));
                return;
            }

            if (mode.startsWith("RENAME:")) {
                String id = mode.substring("RENAME:".length());
                if (message.equalsIgnoreCase("cancel")) { player.sendMessage(Msg.legacy("&cCancelled.")); return; }
                EventDefinition def = registry.get(id);
                if (def == null) return;
                def.setDisplayName(message);
                registry.save();
                player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.6f);
                player.sendMessage(Msg.legacy("&aName updated: &r").append(Msg.parseName(message)));
                openDetail(player, def); // reopen so the new name is immediately visible, not just a chat line
                return;
            }

            if (mode.startsWith("DURATION:")) {
                String id = mode.substring("DURATION:".length());
                if (message.equalsIgnoreCase("cancel")) { player.sendMessage(Msg.legacy("&cCancelled.")); return; }
                EventDefinition def = registry.get(id);
                if (def == null) return;
                try {
                    int seconds = Integer.parseInt(message.trim());
                    if (seconds < 5 || seconds > 3600) {
                        player.sendMessage(Msg.legacy("&cPlease use a value between 5 and 3600 seconds."));
                        return;
                    }
                    def.setCustomDurationSeconds(seconds);
                    registry.save();
                    player.sendMessage(Msg.legacy("&aJoin duration for '" + id + "' set to " + seconds + " seconds."));
                } catch (NumberFormatException ex) {
                    player.sendMessage(Msg.legacy("&cThat's not a number. Try again from the GUI."));
                }
            }
        });
    }
}
