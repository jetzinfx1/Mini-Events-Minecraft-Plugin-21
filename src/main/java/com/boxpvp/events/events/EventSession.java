package com.boxpvp.events.events;

import org.bukkit.Location;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runtime state of the event currently waiting/running. Only one event
 * session is active at a time.
 */
public class EventSession {

    private final EventDefinition definition;
    private EventState state = EventState.WAITING;
    private final Set<UUID> participants = new LinkedHashSet<>();
    private final Set<UUID> disqualified = new LinkedHashSet<>();
    /** Jackpot only: players who typed "jackpot" in chat during the window */
    private final Set<UUID> jackpotEntries = new LinkedHashSet<>();

    /** Parkour only: last checkpoint (gold pressure plate) each player touched. */
    private final Map<UUID, Location> checkpoints = new LinkedHashMap<>();

    /** KOTH only: who is currently alone in the capture zone, and how long (ticks). */
    private UUID kothHolder;
    private int kothHoldTicks;

    /** Parkour only: players who toggled off seeing other participants (auto-reset when they leave/return). */
    private final Set<UUID> hiddenTogglers = new LinkedHashSet<>();

    /** Dodgeball/Volley Charge only: player -> "BLUE" or "RED", assigned when the match starts. */
    private final Map<UUID, String> teams = new LinkedHashMap<>();

    /** Dodgeball/Volley Charge ammo economy: which team currently gets to use the live/next ammo, and who's physically holding it right now (null once fired or before pickup). */
    private String ammoEntitledTeam;
    private UUID ammoHolder;

    public EventSession(EventDefinition definition) {
        this.definition = definition;
    }

    public EventDefinition getDefinition() {
        return definition;
    }

    public EventState getState() {
        return state;
    }

    public void setState(EventState state) {
        this.state = state;
    }

    public Set<UUID> getParticipants() {
        return participants;
    }

    public Set<UUID> getDisqualified() {
        return disqualified;
    }

    public Set<UUID> getJackpotEntries() {
        return jackpotEntries;
    }

    public boolean isParticipant(UUID uuid) {
        return participants.contains(uuid);
    }

    /** Participants still active (joined but not disqualified). */
    public Set<UUID> getActiveParticipants() {
        Set<UUID> active = new LinkedHashSet<>(participants);
        active.removeAll(disqualified);
        return active;
    }

    public Map<UUID, Location> getCheckpoints() {
        return checkpoints;
    }

    public UUID getKothHolder() {
        return kothHolder;
    }

    public void setKothHolder(UUID kothHolder) {
        this.kothHolder = kothHolder;
    }

    public int getKothHoldTicks() {
        return kothHoldTicks;
    }

    public void setKothHoldTicks(int kothHoldTicks) {
        this.kothHoldTicks = kothHoldTicks;
    }

    public Set<UUID> getHiddenTogglers() {
        return hiddenTogglers;
    }

    public Map<UUID, String> getTeams() {
        return teams;
    }

    public String getAmmoEntitledTeam() {
        return ammoEntitledTeam;
    }

    public void setAmmoEntitledTeam(String ammoEntitledTeam) {
        this.ammoEntitledTeam = ammoEntitledTeam;
    }

    public UUID getAmmoHolder() {
        return ammoHolder;
    }

    public void setAmmoHolder(UUID ammoHolder) {
        this.ammoHolder = ammoHolder;
    }
}
