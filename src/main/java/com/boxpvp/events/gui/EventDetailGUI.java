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

import java.util.List;

/** Per-event settings menu: locations, wands, item/reward editors, rename/colour, back. */
public class EventDetailGUI {

    public static final String TITLE_PREFIX = "\u00a76\u00a7lEvent: ";

    // Row 1 (9-17)
    public static final int LOBBY_SLOT = 9;
    public static final int ARENA_SLOT = 10;         // non-team events only
    public static final int BLUE_SPAWN_SLOT = 11;    // team events only
    public static final int RED_SPAWN_SLOT = 12;     // team events only
    public static final int BARRIER_WAND_SLOT = 13;
    public static final int SECOND_WAND_SLOT = 14;   // KOTH zone OR team wall - mutually exclusive types
    public static final int MUD_WAND_SLOT = 15;      // parkour only
    public static final int CHECKPOINT_SLOT = 16;    // parkour only
    public static final int WIN_PLATE_SLOT = 17;     // parkour only

    // Row 2 (18-26)
    public static final int DURATION_SLOT = 18;
    public static final int SPECTATOR_SLOT = 19;
    public static final int TEMP_ITEMS_SLOT = 20;
    public static final int REWARDS_SLOT = 21;
    public static final int KILL_REWARDS_SLOT = 22;
    public static final int RECOLOR_SLOT = 23;
    public static final int SPAWN_AREA_WAND_SLOT = 24; // non-team events only
    public static final int DELETE_EVENT_SLOT = 25;
    public static final int RESTRICTED_WAND_SLOT = 26;

    // Row 3 (27-35)
    public static final int AMMO_BLUE_POINT_SLOT = 27; // Dodgeball / Volley Charge only
    public static final int AMMO_RED_POINT_SLOT = 28;  // Dodgeball / Volley Charge only
    public static final int BLUE_SPAWN_AREA_WAND_SLOT = 10; // team events reuse ARENA_SLOT's number
    public static final int RED_SPAWN_AREA_WAND_SLOT = 24;  // team events reuse SPAWN_AREA_WAND_SLOT's number
    public static final int NO_HUNGER_WAND_SLOT = 29; // any event type
    public static final int BACK_SLOT = 31;

    private final JavaPlugin plugin;
    private final EventRegistry registry;

    public EventDetailGUI(JavaPlugin plugin, EventRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public static String titleFor(String id) {
        return TITLE_PREFIX + id;
    }

    public void open(Player player, EventDefinition def) {
        Inventory inv = plugin.getServer().createInventory(null, 36, Msg.legacy(titleFor(def.getId())));

        boolean isParkour = def.getType() == EventType.PARKOUR;
        boolean isKoth = def.getType() == EventType.KOTH;
        boolean isTeamEvent = def.isTeamEvent();
        boolean isJackpot = def.getType() == EventType.JACKPOT;
        boolean eliminates = def.getType() == EventType.DODGEBOLT || def.getType() == EventType.STRAFE
                || def.getType() == EventType.PVP_TOURNAMENT || isTeamEvent;

        // Lobby applies to everything with a join step except Jackpot/KOTH (team events included).
        if (!isJackpot && !isKoth) {
            inv.setItem(LOBBY_SLOT, locItem(Material.OAK_DOOR, "&e&lSet Lobby Location",
                    def.getLobbyLocation(), "&7Where players wait after joining,", "&7before the match/teams start.",
                    "&7Click: set to your position", "&7Shift-click: clear it"));
        }

        if (isTeamEvent) {
            inv.setItem(BLUE_SPAWN_SLOT, locItem(Material.BLUE_WOOL, "&9&lSet Blue Team Spawn",
                    def.getBlueSpawnLocation(), "&7Click: set to your position", "&7Shift-click: clear it"));
            inv.setItem(RED_SPAWN_SLOT, locItem(Material.RED_WOOL, "&c&lSet Red Team Spawn",
                    def.getRedSpawnLocation(), "&7Click: set to your position", "&7Shift-click: clear it"));
            inv.setItem(SECOND_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&5&lRegion Wand: Team Divider Wall")
                    .lore(List.of(def.hasTeamWall() ? "&aWall region set" : "&cWall region NOT set",
                            "&7Blocks players from walking through,", "&7but arrows and wind charges pass",
                            "&7right through it untouched.",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the wall"))
                    .build());
            inv.setItem(AMMO_BLUE_POINT_SLOT, locItem(Material.SPECTRAL_ARROW, "&9&lSet Blue Ammo Generator",
                    def.getBlueAmmoPoint(), "&7Where Blue team's arrow/wind charge", "&7spawns when it's their turn.",
                    "&7Click: set to your position", "&7Shift-click: clear it"));
            inv.setItem(AMMO_RED_POINT_SLOT, locItem(Material.SPECTRAL_ARROW, "&c&lSet Red Ammo Generator",
                    def.getRedAmmoPoint(), "&7Where Red team's arrow/wind charge", "&7spawns when it's their turn.",
                    "&7Click: set to your position", "&7Shift-click: clear it"));
            inv.setItem(BLUE_SPAWN_AREA_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&9&lRegion Wand: Blue Random Spawn Area")
                    .lore(List.of(def.hasBlueSpawnArea() ? "&aSpawn area set" : "&cSpawn area NOT set",
                            "&7Optional: instead of every Blue player", "&7starting at the exact same point, pick",
                            "&7a random spot within this area.",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the area"))
                    .build());
            inv.setItem(RED_SPAWN_AREA_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&c&lRegion Wand: Red Random Spawn Area")
                    .lore(List.of(def.hasRedSpawnArea() ? "&aSpawn area set" : "&cSpawn area NOT set",
                            "&7Optional: instead of every Red player", "&7starting at the exact same point, pick",
                            "&7a random spot within this area.",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the area"))
                    .build());
        } else if (def.needsLocation()) {
            inv.setItem(ARENA_SLOT, locItem(Material.NETHER_STAR, "&e&lSet Arena Location",
                    def.getArenaLocation(), "&7Click: set to your position", "&7Shift-click: clear it"));
        }

        inv.setItem(BARRIER_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                .name("&6&lRegion Wand: Barrier Wall")
                .lore(List.of(def.hasBarrier() ? "&aBarrier region set" : "&cBarrier region NOT set",
                        "&7Click to receive the wand.",
                        "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                        "&7Shift-click here: clear the region",
                        "&8Won't interfere with WorldEdit."))
                .build());

        if (isKoth) {
            inv.setItem(SECOND_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&c&lRegion Wand: KOTH Capture Zone")
                    .lore(List.of(def.hasFinishRegion() ? "&aCapture zone set" : "&cCapture zone NOT set",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the zone"))
                    .build());
        }

        if (isParkour) {
            inv.setItem(CHECKPOINT_SLOT, new ItemBuilder(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, 1)
                    .name("&6&lGet Checkpoint Plates")
                    .lore(List.of("&7Click to receive gold pressure plates.",
                            "&7Place them along the course - stepping", "&7on one saves it as a checkpoint."))
                    .build());
            inv.setItem(WIN_PLATE_SLOT, new ItemBuilder(Material.CHERRY_PRESSURE_PLATE, 1)
                    .name("&d&lGet Win Plate")
                    .lore(List.of("&7Click to receive a cherry pressure plate.",
                            "&7The first player to step on it wins", "&7the parkour event, instantly."))
                    .build());
            inv.setItem(MUD_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&2&lRegion Wand: Mud Trap Zone")
                    .lore(List.of(def.hasMudRegion() ? "&aMud region set" : "&cMud region NOT set",
                            "&7Optional alternative to placing real", "&7mud blocks - mark a zone instead.",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the zone"))
                    .build());
        }

        inv.setItem(DURATION_SLOT, new ItemBuilder(Material.CLOCK, 1)
                .name("&b&lSet Join Duration")
                .lore(List.of("&7Current: &f" + (def.getCustomDurationSeconds() != null
                                ? def.getCustomDurationSeconds() + "s" : "default (from config.yml)"),
                        "&7Click to type a new value in chat.",
                        "&7Shift-click: reset to the default."))
                .build());

        if (eliminates) {
            inv.setItem(SPECTATOR_SLOT, locItem(Material.IRON_BARS, "&e&lSet Spectator Area",
                    def.getSpectatorLocation(), "&7Where eliminated players go so they", "&7can watch the rest of the match.",
                    "&7Separate from the pre-join lobby -", "&7falls back to it if not set.",
                    "&7Click: set to your position", "&7Shift-click: clear it"));
            inv.setItem(KILL_REWARDS_SLOT, new ItemBuilder(Material.TOTEM_OF_UNDYING, 1)
                    .name("&d&lEdit Kill Rewards (" + def.getKillRewards().size() + ")")
                    .lore(List.of("&7Given instantly to whoever gets a kill.", "&7Permanent - not removed like temp items.",
                            "&7Drag items from your inventory in;", "&7whatever is left when you close it saves."))
                    .build());
        }

        if (def.needsLocation()) {
            inv.setItem(SPAWN_AREA_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                    .name("&b&lRegion Wand: Random Spawn Area")
                    .lore(List.of(def.hasSpawnArea() ? "&aSpawn area set" : "&cSpawn area NOT set",
                            "&7Optional: instead of everyone starting", "&7at the exact same point, pick a random",
                            "&7spot within this area for each player.",
                            "&7Click to receive the wand.",
                            "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                            "&7Shift-click here: clear the area"))
                    .build());
        }

        inv.setItem(TEMP_ITEMS_SLOT, new ItemBuilder(Material.CHEST, 1)
                .name("&b&lEdit Temporary Items (" + def.getTempItems().size() + ")")
                .lore(List.of("&7Items players get only while the event", "&7runs - automatically removed after.",
                        "&7The editor has 5 dedicated equipment", "&7slots (helmet/chest/legs/boots/offhand)",
                        "&7so ANY custom item equips correctly -", "&7not just vanilla armor materials.",
                        "&7Drag items from your inventory in;", "&7whatever is left when you close it saves."))
                .build());

        inv.setItem(REWARDS_SLOT, new ItemBuilder(Material.CHEST_MINECART, 1)
                .name("&a&lEdit Rewards (" + def.getRewards().size() + ")")
                .lore(List.of("&7Permanent items the winner keeps.",
                        "&7Push your ACTUAL crate key item in here", "&7so it matches your Crates system exactly.",
                        "&7Drag items from your inventory in;", "&7whatever is left when you close it saves."))
                .build());

        inv.setItem(RECOLOR_SLOT, new ItemBuilder(Material.NAME_TAG, 1)
                .name("&d&lRename / Recolor")
                .loreLines(List.of(
                        Msg.legacy("&7Current: &r").append(Msg.parseName(def.getDisplayName())),
                        Msg.legacy("&7Click to open the colour picker.")))
                .build());

        inv.setItem(DELETE_EVENT_SLOT, new ItemBuilder(Material.BARRIER, 1)
                .name("&c&lDelete This Event")
                .lore(List.of("&7Shift-click to permanently remove", "&7this event and all its settings."))
                .build());

        inv.setItem(RESTRICTED_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                .name("&4&lRegion Wand: No-Commands Zone")
                .lore(List.of(def.hasRestrictedZone() ? "&aZone set" : "&cZone NOT set",
                        "&7Anyone inside can only chat and", "&7type /events leave - every other",
                        "&7command is blocked (admins exempt).",
                        "&7Click to receive the wand.",
                        "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                        "&7Shift-click here: clear the zone"))
                .build());

        inv.setItem(NO_HUNGER_WAND_SLOT, new ItemBuilder(Material.STICK, 1)
                .name("&6&lRegion Wand: No-Hunger Zone")
                .lore(List.of(def.hasNoHungerZone() ? "&aZone set" : "&cZone NOT set",
                        "&7Players never lose hunger while", "&7inside this area during the event.",
                        "&7Click to receive the wand.",
                        "&7Left-click a block = pos 1", "&7Right-click a block = pos 2",
                        "&7Shift-click here: clear the zone"))
                .build());

        inv.setItem(BACK_SLOT, new ItemBuilder(Material.ARROW, 1).name("&7&lBack").build());

        player.openInventory(inv);
    }

    private ItemStack locItem(Material mat, String name, org.bukkit.Location loc, String... extraLore) {
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add(loc != null ? "&aSet" : "&cNot set");
        for (String s : extraLore) lore.add(s);
        return new ItemBuilder(mat, 1).name(name).lore(lore).build();
    }

    public static String parseId(String title) {
        return title.startsWith(TITLE_PREFIX) ? title.substring(TITLE_PREFIX.length()) : null;
    }
}
