package com.boxpvp.events.events;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A configured event: its id, display name (legacy &codes or MiniMessage
 * tags, e.g. "<gradient:red:blue>Parkour</gradient>"), mechanic type, and
 * every world location / item list the admin has set up for it.
 */
public class EventDefinition {

    private final String id;
    private String displayName;
    private EventType type;

    /** Where players are teleported the moment they join (waiting room). Optional. */
    private Location lobbyLocation;
    /** Where players are teleported when the event actually starts. */
    private Location arenaLocation;

    // Barrier wall region (usable by any event type, not just parkour) - fills
    // with barrier blocks while players are joining, clears to air on start.
    private Location barrierPos1;
    private Location barrierPos2;

    // Parkour-only: touching this region declares the toucher the winner.
    private Location finishPos1;
    private Location finishPos2;

    // Parkour-only optional: an alternative to placing physical mud blocks -
    // any player moving through this region gets sent back to their checkpoint.
    private Location mudPos1;
    private Location mudPos2;

    // Optional: while a player is inside this region during an active session
    // for this event, all commands are blocked except /events leave (chat is
    // never blocked). Works for any event type - set with the wand.
    private Location restrictedPos1;
    private Location restrictedPos2;

    // Optional: where eliminated/dead players are sent instead of the normal
    // pre-join lobby, so they can spectate the rest of the match. Separate
    // from lobbyLocation on purpose - set independently in the GUI.
    private Location spectatorLocation;

    // Optional: instead of everyone starting at the exact arenaLocation point,
    // pick a random spot (X/Z) within this cuboid for each participant - X and
    // Z are randomized, Y stays fixed at arenaLocation's height for safety.
    private Location spawnAreaPos1;
    private Location spawnAreaPos2;

    /** Overrides the global prepare-seconds/countdown for this event only (e.g. a shorter Jackpot window). Null = use global default. */
    private Integer customDurationSeconds;

    /** Temporary items given on start, removed automatically when the event ends or the player leaves early. */
    private List<ItemStack> tempItems = new ArrayList<>();
    /** Permanent rewards given to the winner. */
    private List<ItemStack> rewards = new ArrayList<>();

    // Explicit equipment slots for temp items - set via dedicated slots in the
    // Edit Temporary Items GUI. Using explicit slots (rather than guessing from
    // Material type) means ANY custom item - a skull as a helmet, a nether star
    // as an offhand item, whatever a custom gear plugin uses - equips correctly.
    private ItemStack tempHelmet;
    private ItemStack tempChestplate;
    private ItemStack tempLeggings;
    private ItemStack tempBoots;
    private ItemStack tempOffhand;

    /** Given to whoever gets a kill during PvP-style events (Tournament, Dodgeball, Volley Charge). Permanent, not stripped. */
    private List<ItemStack> killRewards = new ArrayList<>();

    // Dodgeball / Volley Charge only: separate spawn points for each team,
    // used instead of the single arenaLocation.
    private Location blueSpawnLocation;
    private Location redSpawnLocation;

    // Dodgeball / Volley Charge only: an invisible divider - players cannot
    // walk into this region, but projectiles (arrows, wind charges) pass
    // through it untouched, since it's enforced purely by cancelling player
    // movement rather than placing any real blocks.
    private Location teamWallPos1;
    private Location teamWallPos2;

    // Dodgeball / Volley Charge only: single points where each team's ammo
    // (an arrow or a wind charge, depending on event type) spawns.
    private Location blueAmmoPoint;
    private Location redAmmoPoint;

    // Dodgeball / Volley Charge only: optional random-spawn regions per team -
    // instead of everyone on a team starting at the exact same point, pick a
    // random spot within their side's area each round.
    private Location blueSpawnAreaPos1;
    private Location blueSpawnAreaPos2;
    private Location redSpawnAreaPos1;
    private Location redSpawnAreaPos2;

    // Optional, any event type: inside this region, players never lose
    // hunger while an event session is active - set with the wand.
    private Location noHungerPos1;
    private Location noHungerPos2;

    public EventDefinition(String id, String displayName, EventType type, Location arenaLocation) {
        this.id = id;
        this.displayName = displayName;
        this.type = type;
        this.arenaLocation = arenaLocation;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public EventType getType() {
        return type;
    }

    public void setType(EventType type) {
        this.type = type;
    }

    public Location getArenaLocation() {
        return arenaLocation;
    }

    public void setArenaLocation(Location arenaLocation) {
        this.arenaLocation = arenaLocation;
    }

    /** Kept for backwards compatibility with older code paths - same as the arena location. */
    public Location getLocation() {
        return arenaLocation;
    }

    public Location getLobbyLocation() {
        return lobbyLocation;
    }

    public void setLobbyLocation(Location lobbyLocation) {
        this.lobbyLocation = lobbyLocation;
    }

    public Location getBarrierPos1() {
        return barrierPos1;
    }

    public void setBarrierPos1(Location barrierPos1) {
        this.barrierPos1 = barrierPos1;
    }

    public Location getBarrierPos2() {
        return barrierPos2;
    }

    public void setBarrierPos2(Location barrierPos2) {
        this.barrierPos2 = barrierPos2;
    }

    public boolean hasBarrier() {
        return barrierPos1 != null && barrierPos2 != null;
    }

    public Location getFinishPos1() {
        return finishPos1;
    }

    public void setFinishPos1(Location finishPos1) {
        this.finishPos1 = finishPos1;
    }

    public Location getFinishPos2() {
        return finishPos2;
    }

    public void setFinishPos2(Location finishPos2) {
        this.finishPos2 = finishPos2;
    }

    public boolean hasFinishRegion() {
        return finishPos1 != null && finishPos2 != null;
    }

    public Location getMudPos1() {
        return mudPos1;
    }

    public void setMudPos1(Location mudPos1) {
        this.mudPos1 = mudPos1;
    }

    public Location getMudPos2() {
        return mudPos2;
    }

    public void setMudPos2(Location mudPos2) {
        this.mudPos2 = mudPos2;
    }

    public boolean hasMudRegion() {
        return mudPos1 != null && mudPos2 != null;
    }

    public Location getRestrictedPos1() {
        return restrictedPos1;
    }

    public void setRestrictedPos1(Location restrictedPos1) {
        this.restrictedPos1 = restrictedPos1;
    }

    public Location getRestrictedPos2() {
        return restrictedPos2;
    }

    public void setRestrictedPos2(Location restrictedPos2) {
        this.restrictedPos2 = restrictedPos2;
    }

    public boolean hasRestrictedZone() {
        return restrictedPos1 != null && restrictedPos2 != null;
    }

    public Location getSpectatorLocation() {
        return spectatorLocation;
    }

    public void setSpectatorLocation(Location spectatorLocation) {
        this.spectatorLocation = spectatorLocation;
    }

    public Location getSpawnAreaPos1() {
        return spawnAreaPos1;
    }

    public void setSpawnAreaPos1(Location spawnAreaPos1) {
        this.spawnAreaPos1 = spawnAreaPos1;
    }

    public Location getSpawnAreaPos2() {
        return spawnAreaPos2;
    }

    public void setSpawnAreaPos2(Location spawnAreaPos2) {
        this.spawnAreaPos2 = spawnAreaPos2;
    }

    public boolean hasSpawnArea() {
        return spawnAreaPos1 != null && spawnAreaPos2 != null;
    }

    public ItemStack getTempHelmet() {
        return tempHelmet;
    }

    public void setTempHelmet(ItemStack tempHelmet) {
        this.tempHelmet = tempHelmet;
    }

    public ItemStack getTempChestplate() {
        return tempChestplate;
    }

    public void setTempChestplate(ItemStack tempChestplate) {
        this.tempChestplate = tempChestplate;
    }

    public ItemStack getTempLeggings() {
        return tempLeggings;
    }

    public void setTempLeggings(ItemStack tempLeggings) {
        this.tempLeggings = tempLeggings;
    }

    public ItemStack getTempBoots() {
        return tempBoots;
    }

    public void setTempBoots(ItemStack tempBoots) {
        this.tempBoots = tempBoots;
    }

    public ItemStack getTempOffhand() {
        return tempOffhand;
    }

    public void setTempOffhand(ItemStack tempOffhand) {
        this.tempOffhand = tempOffhand;
    }

    public Integer getCustomDurationSeconds() {
        return customDurationSeconds;
    }

    public void setCustomDurationSeconds(Integer customDurationSeconds) {
        this.customDurationSeconds = customDurationSeconds;
    }

    public List<ItemStack> getTempItems() {
        return tempItems;
    }

    public void setTempItems(List<ItemStack> tempItems) {
        this.tempItems = tempItems;
    }

    public List<ItemStack> getRewards() {
        return rewards;
    }

    public void setRewards(List<ItemStack> rewards) {
        this.rewards = rewards;
    }

    public List<ItemStack> getKillRewards() {
        return killRewards;
    }

    public void setKillRewards(List<ItemStack> killRewards) {
        this.killRewards = killRewards;
    }

    public Location getBlueSpawnLocation() {
        return blueSpawnLocation;
    }

    public void setBlueSpawnLocation(Location blueSpawnLocation) {
        this.blueSpawnLocation = blueSpawnLocation;
    }

    public Location getRedSpawnLocation() {
        return redSpawnLocation;
    }

    public void setRedSpawnLocation(Location redSpawnLocation) {
        this.redSpawnLocation = redSpawnLocation;
    }

    public Location getTeamWallPos1() {
        return teamWallPos1;
    }

    public void setTeamWallPos1(Location teamWallPos1) {
        this.teamWallPos1 = teamWallPos1;
    }

    public Location getTeamWallPos2() {
        return teamWallPos2;
    }

    public void setTeamWallPos2(Location teamWallPos2) {
        this.teamWallPos2 = teamWallPos2;
    }

    public boolean hasTeamWall() {
        return teamWallPos1 != null && teamWallPos2 != null;
    }

    public Location getBlueAmmoPoint() {
        return blueAmmoPoint;
    }

    public void setBlueAmmoPoint(Location blueAmmoPoint) {
        this.blueAmmoPoint = blueAmmoPoint;
    }

    public Location getRedAmmoPoint() {
        return redAmmoPoint;
    }

    public void setRedAmmoPoint(Location redAmmoPoint) {
        this.redAmmoPoint = redAmmoPoint;
    }

    public boolean hasAmmoPoints() {
        return blueAmmoPoint != null && redAmmoPoint != null;
    }

    public Location getBlueSpawnAreaPos1() {
        return blueSpawnAreaPos1;
    }

    public void setBlueSpawnAreaPos1(Location blueSpawnAreaPos1) {
        this.blueSpawnAreaPos1 = blueSpawnAreaPos1;
    }

    public Location getBlueSpawnAreaPos2() {
        return blueSpawnAreaPos2;
    }

    public void setBlueSpawnAreaPos2(Location blueSpawnAreaPos2) {
        this.blueSpawnAreaPos2 = blueSpawnAreaPos2;
    }

    public boolean hasBlueSpawnArea() {
        return blueSpawnAreaPos1 != null && blueSpawnAreaPos2 != null;
    }

    public Location getRedSpawnAreaPos1() {
        return redSpawnAreaPos1;
    }

    public void setRedSpawnAreaPos1(Location redSpawnAreaPos1) {
        this.redSpawnAreaPos1 = redSpawnAreaPos1;
    }

    public Location getRedSpawnAreaPos2() {
        return redSpawnAreaPos2;
    }

    public void setRedSpawnAreaPos2(Location redSpawnAreaPos2) {
        this.redSpawnAreaPos2 = redSpawnAreaPos2;
    }

    public boolean hasRedSpawnArea() {
        return redSpawnAreaPos1 != null && redSpawnAreaPos2 != null;
    }

    public Location getNoHungerPos1() {
        return noHungerPos1;
    }

    public void setNoHungerPos1(Location noHungerPos1) {
        this.noHungerPos1 = noHungerPos1;
    }

    public Location getNoHungerPos2() {
        return noHungerPos2;
    }

    public void setNoHungerPos2(Location noHungerPos2) {
        this.noHungerPos2 = noHungerPos2;
    }

    public boolean hasNoHungerZone() {
        return noHungerPos1 != null && noHungerPos2 != null;
    }

    public boolean isTeamEvent() {
        return type == EventType.DODGEBALL || type == EventType.VOLLEY_CHARGE;
    }

    /** Whether this event type requires an arena location to be set before it can run. */
    public boolean needsLocation() {
        return type != EventType.JACKPOT && !isTeamEvent(); // team events use blue/red spawns instead
    }

    /** Centralized "is this event fully configured enough to actually run" check. */
    public boolean isReadyToRun() {
        if (type == EventType.JACKPOT || type == EventType.KOTH) return true;
        if (isTeamEvent()) return blueSpawnLocation != null && redSpawnLocation != null;
        return arenaLocation != null;
    }
}
