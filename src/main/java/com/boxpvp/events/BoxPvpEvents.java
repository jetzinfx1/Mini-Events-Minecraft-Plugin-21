package com.boxpvp.events;

import com.boxpvp.events.commands.BoxEventsCommand;
import com.boxpvp.events.commands.EventsCommand;
import com.boxpvp.events.events.EventManager;
import com.boxpvp.events.events.EventRegistry;
import com.boxpvp.events.events.SchedulerState;
import com.boxpvp.events.gui.ColorPickerGUI;
import com.boxpvp.events.gui.EventDetailGUI;
import com.boxpvp.events.gui.EventManagerGUI;
import com.boxpvp.events.gui.ItemEditorGUI;
import com.boxpvp.events.listeners.EventPlayerListener;
import com.boxpvp.events.listeners.GuiListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class BoxPvpEvents extends JavaPlugin {

    private static BoxPvpEvents instance;

    private EventRegistry eventRegistry;
    private SchedulerState schedulerState;
    private EventManager eventManager;
    private EventManagerGUI eventManagerGUI;
    private GuiListener guiListener;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();

        eventRegistry = new EventRegistry(this);
        eventRegistry.load();

        schedulerState = new SchedulerState(this);
        schedulerState.load();

        eventManager = new EventManager(this, eventRegistry, schedulerState);

        eventManagerGUI = new EventManagerGUI(this, eventRegistry);
        EventDetailGUI detailGui = new EventDetailGUI(this, eventRegistry);
        ColorPickerGUI colorGui = new ColorPickerGUI(this);
        ItemEditorGUI itemGui = new ItemEditorGUI(this);

        guiListener = new GuiListener(this, eventRegistry, eventManager, eventManagerGUI, detailGui, colorGui, itemGui);

        BoxEventsCommand adminCommand = new BoxEventsCommand(this, eventRegistry, eventManager, guiListener);
        getCommand("boxevents").setExecutor(adminCommand);
        getCommand("boxevents").setTabCompleter(adminCommand);

        EventsCommand playerCommand = new EventsCommand(this, eventRegistry, eventManager);
        getCommand("events").setExecutor(playerCommand);
        getCommand("events").setTabCompleter(playerCommand);

        getServer().getPluginManager().registerEvents(new EventPlayerListener(this, eventManager), this);
        getServer().getPluginManager().registerEvents(guiListener, this);

        eventManager.startScheduler();

        getLogger().info("BoxPvpEvents enabled - random events every "
                + getConfig().getInt("event-interval-minutes", 60) + " minute(s) during active hours.");
    }

    @Override
    public void onDisable() {
        if (eventManager != null) {
            eventManager.stopScheduler();
            eventManager.forceStop();
            eventManager.removeHologram();
        }
        if (schedulerState != null) {
            schedulerState.save();
        }
    }

    public static BoxPvpEvents getInstance() {
        return instance;
    }

    public EventRegistry getEventRegistry() {
        return eventRegistry;
    }

    public EventManager getEventManager() {
        return eventManager;
    }

    public EventManagerGUI getEventManagerGUI() {
        return eventManagerGUI;
    }
}
