package com.boxpvp.events.events;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;

/**
 * Persists the timestamp of the last fired event to data.yml, so that after
 * a server restart the "time until next event" countdown picks up where it
 * left off instead of resetting to a full interval.
 */
public class SchedulerState {

    private final JavaPlugin plugin;
    private final File file;
    private long lastEventMillis;

    public SchedulerState(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data.yml");
    }

    public void load() {
        if (!file.exists()) {
            lastEventMillis = System.currentTimeMillis();
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        lastEventMillis = yaml.getLong("last-event-millis", System.currentTimeMillis());
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("last-event-millis", lastEventMillis);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml: " + e.getMessage());
        }
    }

    public long getLastEventMillis() {
        return lastEventMillis;
    }

    public void setLastEventMillis(long lastEventMillis) {
        this.lastEventMillis = lastEventMillis;
        save();
    }
}
