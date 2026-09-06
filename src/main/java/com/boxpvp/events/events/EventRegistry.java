package com.boxpvp.events.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Loads, saves and stores all EventDefinitions from events.yml, and picks
 * a random one when the scheduler needs to fire a new event.
 */
public class EventRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration yaml;
    private final Map<String, EventDefinition> events = new LinkedHashMap<>();
    private final Random random = new Random();

    public EventRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("events.yml", false);
        }
        yaml = YamlConfiguration.loadConfiguration(file);
        events.clear();

        ConfigurationSection root = yaml.getConfigurationSection("events");
        if (root == null) return;

        for (String id : root.getKeys(false)) {
            ConfigurationSection sec = root.getConfigurationSection(id);
            if (sec == null) continue;

            String displayName = sec.getString("display-name", id);
            EventType type;
            try {
                type = EventType.valueOf(sec.getString("type", "CUSTOM").toUpperCase());
            } catch (IllegalArgumentException ex) {
                type = EventType.CUSTOM;
            }

            Location arena = readLocation(sec, "arena-location");
            EventDefinition def = new EventDefinition(id, displayName, type, arena);
            def.setLobbyLocation(readLocation(sec, "lobby-location"));
            def.setBarrierPos1(readLocation(sec, "barrier-pos1"));
            def.setBarrierPos2(readLocation(sec, "barrier-pos2"));
            def.setFinishPos1(readLocation(sec, "zone-pos1"));
            def.setFinishPos2(readLocation(sec, "zone-pos2"));
            def.setMudPos1(readLocation(sec, "mud-pos1"));
            def.setMudPos2(readLocation(sec, "mud-pos2"));
            def.setRestrictedPos1(readLocation(sec, "restricted-pos1"));
            def.setRestrictedPos2(readLocation(sec, "restricted-pos2"));
            def.setSpectatorLocation(readLocation(sec, "spectator-location"));
            def.setSpawnAreaPos1(readLocation(sec, "spawn-area-pos1"));
            def.setSpawnAreaPos2(readLocation(sec, "spawn-area-pos2"));
            def.setTempItems(readItems(sec, "temp-items"));
            def.setRewards(readItems(sec, "rewards"));
            def.setTempHelmet(readSingleItem(sec, "temp-helmet"));
            def.setTempChestplate(readSingleItem(sec, "temp-chestplate"));
            def.setTempLeggings(readSingleItem(sec, "temp-leggings"));
            def.setTempBoots(readSingleItem(sec, "temp-boots"));
            def.setTempOffhand(readSingleItem(sec, "temp-offhand"));
            def.setKillRewards(readItems(sec, "kill-rewards"));
            def.setBlueSpawnLocation(readLocation(sec, "blue-spawn-location"));
            def.setRedSpawnLocation(readLocation(sec, "red-spawn-location"));
            def.setTeamWallPos1(readLocation(sec, "team-wall-pos1"));
            def.setTeamWallPos2(readLocation(sec, "team-wall-pos2"));
            def.setBlueAmmoPoint(readLocation(sec, "blue-ammo-point"));
            def.setRedAmmoPoint(readLocation(sec, "red-ammo-point"));
            def.setBlueSpawnAreaPos1(readLocation(sec, "blue-spawn-area-pos1"));
            def.setBlueSpawnAreaPos2(readLocation(sec, "blue-spawn-area-pos2"));
            def.setRedSpawnAreaPos1(readLocation(sec, "red-spawn-area-pos1"));
            def.setRedSpawnAreaPos2(readLocation(sec, "red-spawn-area-pos2"));
            def.setNoHungerPos1(readLocation(sec, "no-hunger-pos1"));
            def.setNoHungerPos2(readLocation(sec, "no-hunger-pos2"));
            if (sec.contains("custom-duration-seconds")) {
                def.setCustomDurationSeconds(sec.getInt("custom-duration-seconds"));
            }

            events.put(id.toLowerCase(), def);
        }
    }

    public void save() {
        if (yaml == null) yaml = new YamlConfiguration();
        yaml.set("events", null); // clear before rewriting

        for (EventDefinition def : events.values()) {
            String base = "events." + def.getId();
            yaml.set(base + ".display-name", def.getDisplayName());
            yaml.set(base + ".type", def.getType().name());
            writeLocation(base + ".arena-location", def.getArenaLocation());
            writeLocation(base + ".lobby-location", def.getLobbyLocation());
            writeLocation(base + ".barrier-pos1", def.getBarrierPos1());
            writeLocation(base + ".barrier-pos2", def.getBarrierPos2());
            writeLocation(base + ".zone-pos1", def.getFinishPos1());
            writeLocation(base + ".zone-pos2", def.getFinishPos2());
            writeLocation(base + ".mud-pos1", def.getMudPos1());
            writeLocation(base + ".mud-pos2", def.getMudPos2());
            writeLocation(base + ".restricted-pos1", def.getRestrictedPos1());
            writeLocation(base + ".restricted-pos2", def.getRestrictedPos2());
            writeLocation(base + ".spectator-location", def.getSpectatorLocation());
            writeLocation(base + ".spawn-area-pos1", def.getSpawnAreaPos1());
            writeLocation(base + ".spawn-area-pos2", def.getSpawnAreaPos2());
            yaml.set(base + ".temp-items", def.getTempItems());
            yaml.set(base + ".rewards", def.getRewards());
            yaml.set(base + ".temp-helmet", def.getTempHelmet());
            yaml.set(base + ".temp-chestplate", def.getTempChestplate());
            yaml.set(base + ".temp-leggings", def.getTempLeggings());
            yaml.set(base + ".temp-boots", def.getTempBoots());
            yaml.set(base + ".temp-offhand", def.getTempOffhand());
            yaml.set(base + ".kill-rewards", def.getKillRewards());
            writeLocation(base + ".blue-spawn-location", def.getBlueSpawnLocation());
            writeLocation(base + ".red-spawn-location", def.getRedSpawnLocation());
            writeLocation(base + ".team-wall-pos1", def.getTeamWallPos1());
            writeLocation(base + ".team-wall-pos2", def.getTeamWallPos2());
            writeLocation(base + ".blue-ammo-point", def.getBlueAmmoPoint());
            writeLocation(base + ".red-ammo-point", def.getRedAmmoPoint());
            writeLocation(base + ".blue-spawn-area-pos1", def.getBlueSpawnAreaPos1());
            writeLocation(base + ".blue-spawn-area-pos2", def.getBlueSpawnAreaPos2());
            writeLocation(base + ".red-spawn-area-pos1", def.getRedSpawnAreaPos1());
            writeLocation(base + ".red-spawn-area-pos2", def.getRedSpawnAreaPos2());
            writeLocation(base + ".no-hunger-pos1", def.getNoHungerPos1());
            writeLocation(base + ".no-hunger-pos2", def.getNoHungerPos2());
            yaml.set(base + ".custom-duration-seconds", def.getCustomDurationSeconds());
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save events.yml: " + e.getMessage());
        }
    }

    private Location readLocation(ConfigurationSection sec, String path) {
        ConfigurationSection loc = sec.getConfigurationSection(path);
        if (loc == null) return null;
        World world = Bukkit.getWorld(loc.getString("world", "world"));
        if (world == null) return null;
        return new Location(world,
                loc.getDouble("x"), loc.getDouble("y"), loc.getDouble("z"),
                (float) loc.getDouble("yaw"), (float) loc.getDouble("pitch"));
    }

    private void writeLocation(String path, Location loc) {
        if (loc == null) {
            yaml.set(path, null);
            return;
        }
        yaml.set(path + ".world", loc.getWorld().getName());
        yaml.set(path + ".x", loc.getX());
        yaml.set(path + ".y", loc.getY());
        yaml.set(path + ".z", loc.getZ());
        yaml.set(path + ".yaw", (double) loc.getYaw());
        yaml.set(path + ".pitch", (double) loc.getPitch());
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> readItems(ConfigurationSection sec, String path) {
        List<?> raw = sec.getList(path);
        List<ItemStack> items = new ArrayList<>();
        if (raw == null) return items;
        for (Object o : raw) {
            if (o instanceof ItemStack stack) items.add(stack);
        }
        return items;
    }

    private ItemStack readSingleItem(ConfigurationSection sec, String path) {
        Object o = sec.get(path);
        return o instanceof ItemStack stack ? stack : null;
    }

    public EventDefinition get(String id) {
        return events.get(id.toLowerCase());
    }

    public Map<String, EventDefinition> all() {
        return events;
    }

    public void add(EventDefinition def) {
        events.put(def.getId().toLowerCase(), def);
        save();
    }

    public void remove(String id) {
        events.remove(id.toLowerCase());
        save();
    }

    /** Picks a random event that is actually ready to run (has an arena location, or doesn't need one). */
    public EventDefinition pickRandomReady() {
        var ready = events.values().stream()
                .filter(EventDefinition::isReadyToRun)
                .toList();
        if (ready.isEmpty()) return null;
        return ready.get(random.nextInt(ready.size()));
    }
}
