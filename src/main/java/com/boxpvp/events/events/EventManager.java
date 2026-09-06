package com.boxpvp.events.events;

import com.boxpvp.events.util.ItemBuilder;
import com.boxpvp.events.util.Msg;
import com.boxpvp.events.util.PdcKeys;
import com.boxpvp.events.util.RegionUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.*;

public class EventManager {

    private final JavaPlugin plugin;
    private final EventRegistry registry;
    private final SchedulerState schedulerState;
    private final PdcKeys keys;
    private final Random random = new Random();

    private EventSession session; // null when nothing is happening
    private BukkitTask schedulerTask;
    private BukkitTask countdownTask;
    private BukkitTask jackpotSpinTask;
    private BukkitTask tickTask; // 1/s: next-event bar, hologram, KOTH capture

    private BossBar nextEventBar;
    private ArmorStand hologram;

    /** Admin wand selections in progress: player -> [eventId, target ("barrier"/"zone")] */
    private final Map<UUID, String[]> wandContext = new HashMap<>();

    /** UUID -> destination for a player who was disqualified while dead (real PVP death), consumed by PlayerRespawnEvent. */
    private final Map<UUID, Location> pendingRespawns = new HashMap<>();

    /** UUIDs of ammo projectiles already resolved - tracked in memory rather than by reading PersistentData back off the entity, which is not reliable once that entity has just been removed. */
    private final Set<UUID> resolvedAmmoEntities = new HashSet<>();

    public EventManager(JavaPlugin plugin, EventRegistry registry, SchedulerState schedulerState) {
        this.plugin = plugin;
        this.registry = registry;
        this.schedulerState = schedulerState;
        this.keys = new PdcKeys(plugin);
    }

    public PdcKeys getKeys() {
        return keys;
    }

    // ---------------------------------------------------------------
    // Scheduler
    // ---------------------------------------------------------------

    public void startScheduler() {
        stopScheduler();
        schedulerTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 20L * 10L, 20L * 10L); // check every 10 seconds

        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                secondTick();
            }
        }.runTaskTimer(plugin, 20L, 20L);

        initBossBar();
    }

    public void stopScheduler() {
        if (schedulerTask != null) { schedulerTask.cancel(); schedulerTask = null; }
        if (tickTask != null) { tickTask.cancel(); tickTask = null; }
        schedulerState.save();
    }

    private void tick() {
        schedulerState.save(); // cheap safety net in case the server ever goes down uncleanly

        int intervalMinutes = plugin.getConfig().getInt("event-interval-minutes", 60);
        long intervalMillis = intervalMinutes * 60_000L;

        if (System.currentTimeMillis() - schedulerState.getLastEventMillis() < intervalMillis) return;
        if (isBlackedOut()) return;
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        if (session != null) {
            // The next event's time has come while one is still active -
            // cancel it and immediately replace it with a freshly chosen one,
            // instead of silently skipping forever (which is what caused the
            // "Next Event in 0s" stuck state).
            forceStop();
        }
        triggerRandomEvent();
    }

    private boolean isBlackedOut() {
        List<Map<?, ?>> windows = plugin.getConfig().getMapList("blackout-windows");
        if (windows.isEmpty()) return false;
        LocalTime now = LocalTime.now();
        for (Map<?, ?> w : windows) {
            try {
                LocalTime start = LocalTime.parse(String.valueOf(w.get("start")));
                LocalTime end = LocalTime.parse(String.valueOf(w.get("end")));
                boolean inWindow = start.isBefore(end)
                        ? (!now.isBefore(start) && now.isBefore(end))
                        : (!now.isBefore(start) || now.isBefore(end)); // wraps midnight
                if (inWindow) return true;
            } catch (DateTimeParseException ignored) {
                // malformed window entry, skip it
            }
        }
        return false;
    }

    // ---------------------------------------------------------------
    // Triggering / intro / spin / announce
    // ---------------------------------------------------------------

    public boolean triggerRandomEvent() {
        EventDefinition def = registry.pickRandomReady();
        if (def == null) return false;
        return triggerEvent(def);
    }

    public boolean triggerEvent(EventDefinition def) {
        if (session != null) return false;
        schedulerState.setLastEventMillis(System.currentTimeMillis());
        runIntro(def);
        return true;
    }

    private void runIntro(EventDefinition def) {
        Sound announceSound = Msg.sound(plugin.getConfig(), "event-announce", Sound.ENTITY_ENDER_DRAGON_GROWL);
        Title title = Title.title(
                Component.empty(),
                Msg.legacy("&6&lRandom event starting..."),
                Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(900), Duration.ofMillis(200))
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(title);
            p.playSound(p.getLocation(), announceSound, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> runSpinAnimation(def, this::announceEvent), 24L);
    }

    private void runSpinAnimation(EventDefinition chosen, java.util.function.Consumer<EventDefinition> onDone) {
        List<EventDefinition> pool = new ArrayList<>(registry.all().values());
        if (pool.isEmpty()) pool = List.of(chosen);
        spinStep(chosen, pool, 0, 24, onDone);
    }

    private void spinStep(EventDefinition chosen, List<EventDefinition> pool, int tick, int totalTicks,
                           java.util.function.Consumer<EventDefinition> onDone) {
        // Names appear in the subtitle slot on purpose - noticeably bigger than an
        // action bar, but smaller than a full-size Minecraft title, so it stays
        // readable without dominating the whole screen or blocking PvP.
        if (tick >= totalTicks) {
            Sound landSound = Msg.sound(plugin.getConfig(), "spin-land", Sound.ENTITY_PLAYER_LEVELUP);
            Title landTitle = Title.title(
                    Component.empty(),
                    Component.text("\u25ba ").append(Msg.parseName(chosen.getDisplayName())).append(Component.text(" \u25c4")),
                    Title.Times.times(Duration.ofMillis(50), Duration.ofSeconds(2), Duration.ofMillis(300))
            );
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.showTitle(landTitle);
                p.playSound(p.getLocation(), landSound, 1f, 1.4f);
            }
            onDone.accept(chosen);
            return;
        }

        EventDefinition flash = pool.get(random.nextInt(pool.size()));
        Sound flashSound = Msg.sound(plugin.getConfig(), "spin-flash", Sound.UI_BUTTON_CLICK);
        Title flashTitle = Title.title(
                Component.empty(),
                Msg.parseName(flash.getDisplayName()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(180), Duration.ZERO)
        );
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.showTitle(flashTitle);
            p.playSound(p.getLocation(), flashSound, 0.6f, 1.2f);
        }

        long delay = tick < totalTicks - 8 ? 3L : (tick < totalTicks - 3 ? 6L : 10L);
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> spinStep(chosen, pool, tick + 1, totalTicks, onDone), delay);
    }

    private void announceEvent(EventDefinition def) {
        session = new EventSession(def);
        session.setState(EventState.WAITING);

        String name = Msg.plainName(def.getDisplayName());
        Sound announceSound = Msg.sound(plugin.getConfig(), "event-announce", Sound.BLOCK_NOTE_BLOCK_PLING);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), announceSound, 1f, 1f);

        switch (def.getType()) {
            case JACKPOT -> {
                Msg.broadcast(Component.text("\u2605 ", NamedTextColor.GOLD)
                        .append(Msg.parseName(def.getDisplayName()))
                        .append(Msg.legacy(" &7has been announced!")));
                Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "jackpot-join-hint", "time", formatTime(durationFor(def)))));
            }
            case KOTH -> {
                Msg.broadcast(Component.text("\u2694 ", NamedTextColor.RED)
                        .append(Msg.parseName(def.getDisplayName()))
                        .append(Msg.legacy(" &7has started - fly there NOW and hold the point!")));
                beginKoth(); // no countdown/join phase at all - it's live the instant it's announced
                return;
            }
            default -> Msg.broadcast(buildJoinPrompt(def, name));
        }

        startCountdown();
    }

    private Component buildJoinPrompt(EventDefinition def, String plainName) {
        String time = formatTime(durationFor(def));
        return Msg.legacy(Msg.get(plugin.getConfig(), "join-prompt", "event", plainName, "time", time))
                .appendSpace()
                .append(Msg.clickable(Msg.getRaw(plugin.getConfig(), "join-button"), "/events join"));
    }

    /** Whether this event type has a click-to-join step at all (KOTH and Jackpot don't). */
    private boolean isJoinable(EventType type) {
        return type != EventType.KOTH && type != EventType.JACKPOT;
    }

    // ---------------------------------------------------------------
    // Countdown
    // ---------------------------------------------------------------

    /** How long (seconds) this specific event's join/prepare window lasts - per-event override, else the global default. */
    private int durationFor(EventDefinition def) {
        return def.getCustomDurationSeconds() != null ? def.getCustomDurationSeconds()
                : plugin.getConfig().getInt("prepare-seconds", 180);
    }

    /** Filters the configured countdown-warnings down to whatever is actually less than this event's duration - never repeats the starting duration itself (that's already stated in the announcement), and naturally includes richer warnings like 2m/1m30s as long as they're configured. */
    private List<Integer> computeWarnings(int totalSeconds) {
        List<Integer> configured = plugin.getConfig().getIntegerList("countdown-warnings");
        if (configured.isEmpty()) configured = List.of(180, 150, 120, 90, 60, 30, 15, 10, 5, 4, 3, 2, 1);

        List<Integer> warnings = new ArrayList<>();
        for (int w : configured) {
            if (w < totalSeconds) warnings.add(w);
        }
        // Safety net for very short custom durations where none of the configured warnings apply.
        if (warnings.isEmpty()) {
            for (int s : new int[]{30, 15, 10, 5, 4, 3, 2, 1}) {
                if (s < totalSeconds) warnings.add(s);
            }
        }
        return warnings;
    }

    private void startCountdown() {
        EventDefinition def = session.getDefinition();
        int prepareSeconds = durationFor(def);
        List<Integer> warnings = computeWarnings(prepareSeconds);

        final Set<Integer> warnSet = new TreeSet<>(Collections.reverseOrder());
        warnSet.addAll(warnings);
        // Jackpot has no click-to-join button (you enter by typing "jackpot" in
        // chat) but should still get the same periodic reminders as everyone
        // else - only the button itself is what's skipped for it (and KOTH,
        // which never even reaches this method at all).
        boolean showJoinButton = isJoinable(def.getType());
        Sound tickSound = Msg.sound(plugin.getConfig(), "countdown-tick", Sound.BLOCK_NOTE_BLOCK_PLING);

        countdownTask = new BukkitRunnable() {
            int secondsLeft = prepareSeconds;

            @Override
            public void run() {
                if (session == null) { cancel(); return; }
                if (warnSet.contains(secondsLeft)) {
                    String time = formatTime(secondsLeft);
                    Component message;
                    if (session.getDefinition().getType() == EventType.JACKPOT) {
                        message = Msg.legacy(Msg.get(plugin.getConfig(), "jackpot-join-hint", "time", time));
                    } else {
                        String name = Msg.plainName(session.getDefinition().getDisplayName());
                        message = Msg.legacy(Msg.get(plugin.getConfig(), "countdown", "event", name, "time", time));
                        if (showJoinButton) {
                            message = message.appendSpace().append(Msg.clickable(Msg.getRaw(plugin.getConfig(), "join-button"), "/events join"));
                        }
                    }
                    Msg.broadcast(message);
                    for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), tickSound, 0.7f, 1f);
                }
                if (secondsLeft <= 0) { cancel(); beginEvent(); return; }
                secondsLeft--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private String formatTime(int seconds) {
        if (seconds < 60) return seconds + "s";
        int m = seconds / 60;
        int s = seconds % 60;
        return s == 0 ? m + "m" : m + "m" + s + "s";
    }

    private String formatMMSS(int seconds) {
        int m = Math.max(0, seconds) / 60;
        int s = Math.max(0, seconds) % 60;
        return String.format("%02d:%02d", m, s);
    }

    // ---------------------------------------------------------------
    // Joining
    // ---------------------------------------------------------------

    public JoinResult join(Player player) {
        if (session == null || session.getState() != EventState.WAITING) return JoinResult.NO_EVENT;
        EventDefinition def = session.getDefinition();

        if (def.getType() == EventType.JACKPOT) return JoinResult.USE_CHAT_JACKPOT;
        if (def.getType() == EventType.KOTH) return JoinResult.NOT_JOINABLE;
        if (session.isParticipant(player.getUniqueId())) return JoinResult.ALREADY_JOINED;

        boolean requireEmpty = plugin.getConfig().getBoolean("require-empty-inventory", true);
        if (requireEmpty && !isInventoryEmpty(player)) return JoinResult.INVENTORY_NOT_EMPTY;

        session.getParticipants().add(player.getUniqueId());
        // Immediately send them somewhere: a dedicated lobby if configured,
        // otherwise straight to the arena itself (which is what most events use as their waiting room too).
        if (def.getLobbyLocation() != null) player.teleport(def.getLobbyLocation());
        else if (def.getArenaLocation() != null) player.teleport(def.getArenaLocation());
        return JoinResult.JOINED;
    }

    public boolean jackpotEnter(Player player) {
        if (session == null || session.getState() != EventState.WAITING) return false;
        if (session.getDefinition().getType() != EventType.JACKPOT) return false;
        boolean isNew = session.getJackpotEntries().add(player.getUniqueId());
        if (isNew) {
            Msg.broadcast(Msg.legacy(Msg.rainbow(player.getName() + " entered the Jackpot!")));
        }
        return true;
    }

    private boolean isInventoryEmpty(Player p) {
        PlayerInventory inv = p.getInventory();
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        for (ItemStack item : inv.getArmorContents()) {
            if (item != null && item.getType() != Material.AIR) return false;
        }
        return true;
    }

    public enum JoinResult { JOINED, ALREADY_JOINED, INVENTORY_NOT_EMPTY, NO_EVENT, USE_CHAT_JACKPOT, NOT_JOINABLE }

    public enum LeaveResult { LEFT, NOT_IN_EVENT }

    /** Player-initiated leave (or used internally on disconnect): removes them, strips items, sends them to spawn. */
    public LeaveResult leave(Player player) {
        if (session == null || !session.isParticipant(player.getUniqueId())) return LeaveResult.NOT_IN_EVENT;

        boolean wasRunning = session.getState() == EventState.RUNNING;
        EventDefinition def = session.getDefinition();
        // Captured BEFORE the removals below wipe the team map: if this player was
        // mid-hold of the live ammo (never fired), stripTempItemsFrom() below just
        // deletes it from their inventory with no world drop - without recovering
        // here, ammoHolder would stay pointed at them forever and nobody could ever
        // pick up new ammo again for the rest of the match.
        boolean wasAmmoHolder = def.isTeamEvent() && player.getUniqueId().equals(session.getAmmoHolder());
        String team = session.getTeams().get(player.getUniqueId());

        stripTempItemsFrom(player);
        session.getParticipants().remove(player.getUniqueId());
        session.getDisqualified().remove(player.getUniqueId());
        session.getCheckpoints().remove(player.getUniqueId());
        session.getHiddenTogglers().remove(player.getUniqueId());
        session.getTeams().remove(player.getUniqueId());

        Location spawn = getReturnSpawn();
        if (spawn != null) player.teleport(spawn);

        if (wasRunning && (def.getType() == EventType.DODGEBOLT || def.getType() == EventType.STRAFE
                || def.getType() == EventType.PVP_TOURNAMENT || def.getType() == EventType.DODGEBALL
                || def.getType() == EventType.VOLLEY_CHARGE)) {
            Set<UUID> active = session.getActiveParticipants();
            if (active.size() == 1) {
                Player last = Bukkit.getPlayer(active.iterator().next());
                if (last != null) declareWinner(last);
            }
        }

        if (wasAmmoHolder && session != null && team != null) {
            beginAmmoRound(def, team); // hand their team a fresh pickup so the match isn't stuck
        }
        return LeaveResult.LEFT;
    }

    // ---------------------------------------------------------------
    // Starting the event
    // ---------------------------------------------------------------

    /** Cancels the current session for lack of players. Pushes everyone who joined back to spawn - except for Parkour and KOTH, where players were never moved away from where they already were. */
    private void cancelForNotEnoughPlayers(EventDefinition def) {
        String name = Msg.plainName(def.getDisplayName());
        Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "event-cancelled-empty", "event", name)));
        if (def.getType() != EventType.PARKOUR && def.getType() != EventType.KOTH) {
            returnAllParticipants();
        }
        endSession();
    }

    private void beginEvent() {
        if (session == null) return;
        EventDefinition def = session.getDefinition();

        if (def.getType() == EventType.JACKPOT) { beginJackpot(); return; }
        if (def.getType() == EventType.KOTH) { beginKoth(); return; }

        if (session.getParticipants().size() < 2) {
            cancelForNotEnoughPlayers(def);
            return;
        }

        session.setState(EventState.RUNNING);
        if (def.hasBarrier()) setBarrierBlocks(def, Material.AIR);

        if (def.isTeamEvent()) {
            assignTeamsAndSpawn(def);
        } else {
            for (UUID uuid : session.getParticipants()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null) continue;
                // Parkour is the one exception: players already got sent to the lobby/arena
                // on join and are meant to freely pick their own starting spot behind the
                // barrier wall - don't yank them back to the exact arena point on start.
                if (def.getType() != EventType.PARKOUR && def.getArenaLocation() != null) {
                    p.teleport(randomSpawnPoint(def));
                }
                if (def.getType() == EventType.PARKOUR) {
                    session.getCheckpoints().put(uuid, def.getArenaLocation());
                    giveVisibilityCompass(p, def);
                }
                giveTempItems(p, def);
            }
        }

        String name = Msg.plainName(def.getDisplayName());
        Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "event-started", "event", name)));
        Sound startSound = Msg.sound(plugin.getConfig(), "event-start", Sound.ENTITY_ENDER_DRAGON_GROWL);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), startSound, 0.6f, 1f);
    }

    /** Shuffles participants into two roughly-even teams and sends each to their side's spawn. */
    private void assignTeamsAndSpawn(EventDefinition def) {
        List<UUID> shuffled = new ArrayList<>(session.getParticipants());
        Collections.shuffle(shuffled, random);

        for (int i = 0; i < shuffled.size(); i++) {
            UUID uuid = shuffled.get(i);
            String team = (i % 2 == 0) ? "BLUE" : "RED";
            session.getTeams().put(uuid, team);

            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Location dest = randomTeamSpawnPoint(def, team);
            if (dest != null) p.teleport(dest);
            p.sendMessage(Msg.legacy(team.equals("BLUE") ? "&9&lYou are on the BLUE team!" : "&c&lYou are on the RED team!"));
            giveTempItems(p, def);
        }

        if (def.hasAmmoPoints()) {
            String starting = random.nextBoolean() ? "BLUE" : "RED";
            beginAmmoRound(def, starting);
        }
    }

    /** If a spawn area is configured for this team, picks a random X/Z within it (Y fixed at the team's spawn point height); otherwise just the fixed spawn point. */
    private Location randomTeamSpawnPoint(EventDefinition def, String team) {
        Location fixed = team.equals("BLUE") ? def.getBlueSpawnLocation() : def.getRedSpawnLocation();
        boolean hasArea = team.equals("BLUE") ? def.hasBlueSpawnArea() : def.hasRedSpawnArea();
        if (!hasArea || fixed == null) return fixed;

        Location a = team.equals("BLUE") ? def.getBlueSpawnAreaPos1() : def.getRedSpawnAreaPos1();
        Location b = team.equals("BLUE") ? def.getBlueSpawnAreaPos2() : def.getRedSpawnAreaPos2();
        double minX = Math.min(a.getBlockX(), b.getBlockX());
        double maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        double maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;

        double x = minX + random.nextDouble() * (maxX - minX);
        double z = minZ + random.nextDouble() * (maxZ - minZ);
        return new Location(fixed.getWorld(), x, fixed.getY(), z, fixed.getYaw(), fixed.getPitch());
    }

    // ---------------------------------------------------------------
    // Dodgeball / Volley Charge ammo economy
    // ---------------------------------------------------------------

    /** Spawns a fresh ammo pickup at the given team's own generator point and hands them entitlement to it. */
    public void beginAmmoRound(EventDefinition def, String team) {
        if (session == null) return;
        Location point = "BLUE".equals(team) ? def.getBlueAmmoPoint() : def.getRedAmmoPoint();
        if (point == null) return;
        session.setAmmoEntitledTeam(team);
        session.setAmmoHolder(null);
        spawnAmmoItem(def, point);
    }

    /** Spawns a fresh ammo pickup at an arbitrary location (used after a miss - it lands wherever it stopped). Pass null for entitledTeam to make it a free-for-all pickup - whichever team reaches it first gets it. */
    public void spawnAmmoAt(EventDefinition def, Location loc, String entitledTeam) {
        if (session == null) return;
        session.setAmmoEntitledTeam(entitledTeam);
        session.setAmmoHolder(null);
        spawnAmmoItem(def, loc);
    }

    private void spawnAmmoItem(EventDefinition def, Location loc) {
        // Guarantee only one live ammo entity ever exists at a time - leftover
        // duplicates (from a prior double-resolution, or simply never having
        // been picked up) are exactly what let two players simultaneously
        // become "holder", which is what silently breaks the tagging on a
        // later shot and makes it behave like a normal, untracked arrow.
        removeStrayAmmoEntities(def);

        // Volley Charge throws wind charges; Dodgeball uses bow+arrow (the bow is auto-given on pickup).
        Material mat = def.getType() == EventType.VOLLEY_CHARGE ? Material.WIND_CHARGE : Material.ARROW;
        ItemStack stack = new ItemStack(mat, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.ammoTag, PersistentDataType.STRING, def.getId());
            stack.setItemMeta(meta);
        }
        org.bukkit.entity.Item dropped = loc.getWorld().dropItem(loc, stack);
        dropped.setGlowing(true);
        dropped.setUnlimitedLifetime(true);
        dropped.setPickupDelay(0);
        dropped.getPersistentDataContainer().set(keys.ammoTag, PersistentDataType.STRING, def.getId());
    }

    /** Removes every world entity (ground pickup or in-flight projectile) tagged for this event's ammo - called before every new spawn, and when a session ends, so nothing ever lingers or piles up. */
    public void removeStrayAmmoEntities(EventDefinition def) {
        for (World world : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : world.getEntities()) {
                if (e instanceof org.bukkit.entity.Item item) {
                    if (isAmmoItem(item.getItemStack(), def.getId())) item.remove();
                } else if (e instanceof Projectile proj) {
                    if (isAmmoProjectile(proj, def.getId())) proj.remove();
                }
            }
        }
    }

    /**
     * Places picked-up ammo directly into fixed hotbar slots instead of
     * wherever vanilla pickup would put it: for an arrow (Dodgeball), a bow
     * goes in slot 1 and the arrow in slot 2, so the player can actually fire
     * it; a wind charge (Volley Charge) just goes in slot 1, ready to throw.
     * The bow is tagged the same as the arrow, so both disappear together the
     * instant it's fired - the bow is only "borrowed" while holding live ammo.
     */
    public void giveAmmoToPlayer(Player player, ItemStack ammoStack) {
        if (ammoStack.getType() == Material.ARROW) {
            ItemStack bow = new ItemBuilder(Material.BOW, 1).build();
            ItemMeta bowMeta = bow.getItemMeta();
            if (bowMeta != null) {
                bowMeta.getPersistentDataContainer().set(keys.ammoTag, PersistentDataType.STRING, session.getDefinition().getId());
                bow.setItemMeta(bowMeta);
            }
            player.getInventory().setItem(0, bow);
            player.getInventory().setItem(1, ammoStack);
        } else {
            player.getInventory().setItem(0, ammoStack);
        }
    }

    /** Removes every ammoTag-tagged item (the bow AND any leftover ammo item) from a player's main inventory - called the instant they fire, so nothing lingers as a duplicate. */
    public void clearAmmoItemsFrom(Player player, String eventId) {
        var inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            if (isAmmoItem(contents[i], eventId)) { contents[i] = null; changed = true; }
        }
        if (changed) inv.setContents(contents);
    }

    public boolean isAmmoItem(ItemStack stack, String eventId) {
        if (stack == null) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return eventId.equals(meta.getPersistentDataContainer().get(keys.ammoTag, PersistentDataType.STRING));
    }

    public boolean isAmmoProjectile(org.bukkit.entity.Entity entity, String eventId) {
        return eventId.equals(entity.getPersistentDataContainer().get(keys.ammoTag, PersistentDataType.STRING));
    }

    /** True the first time this is called for a given projectile, false on any repeat. Uses an in-memory UUID set rather than PersistentData on the entity, since that becomes unreliable the instant the entity is removed - which is exactly when both the hit-handler and the despawn-watchdog race to check it. */
    public boolean tryResolveAmmoOnce(org.bukkit.entity.Entity proj) {
        return resolvedAmmoEntities.add(proj.getUniqueId());
    }

    /** Shared resolution logic for both a normal hit-detection and the despawn-safety-net path. */
    public void resolveAmmoImpact(EventDefinition def, Location impact, String firedByTeam, boolean hitEnemy) {
        String other = otherTeam(firedByTeam);
        Sound sound = hitEnemy
                ? Msg.sound(plugin.getConfig(), "ammo-hit", Sound.ENTITY_PLAYER_HURT)
                : Msg.sound(plugin.getConfig(), "ammo-miss", Sound.BLOCK_ANVIL_LAND);
        // Only the players actually in this event should hear the hit/miss sound -
        // not every online player on the server (spectators watching from the
        // sidelines are fine too, since they're tracked as participants while eliminated).
        if (session != null) {
            for (UUID uuid : session.getParticipants()) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.playSound(p.getLocation(), sound, 0.5f, hitEnemy ? 1.4f : 0.8f);
            }
        }

        if (hitEnemy) {
            beginAmmoRound(def, other);
        } else {
            Location floorSpot = snapToFloor(impact);
            spawnAmmoAt(def, floorSpot, null); // free-for-all: whichever team gets there first
        }
    }

    public String otherTeam(String team) {
        return "BLUE".equals(team) ? "RED" : "BLUE";
    }

    /** Finds solid ground below a point and returns a resting spot just above it, so a missed shot's pickup never ends up floating in mid-air. */
    public Location snapToFloor(Location loc) {
        Location check = loc.clone();
        int minY = loc.getWorld().getMinHeight();

        // If the impact point is embedded INSIDE a solid block - exactly what
        // happens when a projectile sticks to the underside of a ceiling (a
        // barrier-block roof, for example) - step down out of it first.
        // Without this, the loop below would treat that ceiling block as "the
        // floor" on its very first check and place the pickup one block ON TOP
        // of it - outside a sealed arena and unreachable to players - instead
        // of correctly falling through to the real floor underneath.
        while (check.getY() > minY && check.getBlock().getType().isSolid()) {
            check.subtract(0, 1, 0);
        }

        for (int i = 0; i < 60 && check.getY() > minY; i++) {
            if (check.getBlock().getType().isSolid()) {
                return new Location(loc.getWorld(), check.getBlockX() + 0.5, check.getBlockY() + 1.0, check.getBlockZ() + 0.5);
            }
            check.subtract(0, 1, 0);
        }
        return loc;
    }

    /** If a spawn-area is configured, picks a random X/Z within it (Y fixed at arenaLocation's height for safety); otherwise just the arena point itself. */
    private Location randomSpawnPoint(EventDefinition def) {
        Location arena = def.getArenaLocation();
        if (!def.hasSpawnArea() || arena == null) return arena;

        Location a = def.getSpawnAreaPos1();
        Location b = def.getSpawnAreaPos2();
        double minX = Math.min(a.getBlockX(), b.getBlockX());
        double maxX = Math.max(a.getBlockX(), b.getBlockX()) + 1;
        double minZ = Math.min(a.getBlockZ(), b.getBlockZ());
        double maxZ = Math.max(a.getBlockZ(), b.getBlockZ()) + 1;

        double x = minX + random.nextDouble() * (maxX - minX);
        double z = minZ + random.nextDouble() * (maxZ - minZ);
        return new Location(arena.getWorld(), x, arena.getY(), z, arena.getYaw(), arena.getPitch());
    }

    private void beginKoth() {
        session.setState(EventState.RUNNING);
        if (session.getDefinition().hasBarrier()) setBarrierBlocks(session.getDefinition(), Material.AIR);
        Msg.broadcast(Msg.legacy("&c&lKOTH &7is live - hold the point for "
                + plugin.getConfig().getInt("koth.capture-seconds", 60) + " seconds to win!"));
    }

    private void giveVisibilityCompass(Player p, EventDefinition def) {
        ItemStack compass = new ItemBuilder(Material.COMPASS, 1)
                .name("&b&lToggle Player Visibility")
                .lore(List.of("&7Right-click to hide/show", "&7other parkour participants."))
                .tag(keys.tempItem, def.getId())
                .tag(keys.visibilityToggle, "true")
                .build();
        p.getInventory().addItem(compass);
    }

    private void giveTempItems(Player p, EventDefinition def) {
        for (ItemStack template : def.getTempItems()) {
            if (template == null || template.getType() == Material.AIR) continue;
            ItemStack clone = tagTempItem(template, def.getId());
            switch (clone.getType()) {
                case LEATHER_HELMET, CHAINMAIL_HELMET, IRON_HELMET, GOLDEN_HELMET, DIAMOND_HELMET, NETHERITE_HELMET, TURTLE_HELMET ->
                        p.getInventory().setHelmet(clone);
                case LEATHER_CHESTPLATE, CHAINMAIL_CHESTPLATE, IRON_CHESTPLATE, GOLDEN_CHESTPLATE, DIAMOND_CHESTPLATE, NETHERITE_CHESTPLATE, ELYTRA ->
                        p.getInventory().setChestplate(clone);
                case LEATHER_LEGGINGS, CHAINMAIL_LEGGINGS, IRON_LEGGINGS, GOLDEN_LEGGINGS, DIAMOND_LEGGINGS, NETHERITE_LEGGINGS ->
                        p.getInventory().setLeggings(clone);
                case LEATHER_BOOTS, CHAINMAIL_BOOTS, IRON_BOOTS, GOLDEN_BOOTS, DIAMOND_BOOTS, NETHERITE_BOOTS ->
                        p.getInventory().setBoots(clone);
                default -> p.getInventory().addItem(clone);
            }
        }

        // Explicit equipment slots - always equipped exactly where configured,
        // regardless of Material type, so custom gear (a skull as a helmet, a
        // nether star as an offhand item, etc.) equips correctly every time.
        if (def.getTempHelmet() != null) p.getInventory().setHelmet(tagTempItem(def.getTempHelmet(), def.getId()));
        if (def.getTempChestplate() != null) p.getInventory().setChestplate(tagTempItem(def.getTempChestplate(), def.getId()));
        if (def.getTempLeggings() != null) p.getInventory().setLeggings(tagTempItem(def.getTempLeggings(), def.getId()));
        if (def.getTempBoots() != null) p.getInventory().setBoots(tagTempItem(def.getTempBoots(), def.getId()));
        if (def.getTempOffhand() != null) p.getInventory().setItemInOffHand(tagTempItem(def.getTempOffhand(), def.getId()));
    }

    private ItemStack tagTempItem(ItemStack template, String eventId) {
        ItemStack clone = template.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(keys.tempItem, PersistentDataType.STRING, eventId);
            clone.setItemMeta(meta);
        }
        return clone;
    }

    /** Removes every temp item (main inventory + armor + offhand) tagged for the current event from one player. */
    public void stripTempItemsFrom(Player p) {
        if (session == null) return;
        String eventId = session.getDefinition().getId();
        PlayerInventory inv = p.getInventory();

        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isTaggedFor(contents[i], eventId)) contents[i] = null;
        }
        inv.setContents(contents);

        if (isTaggedFor(inv.getHelmet(), eventId)) inv.setHelmet(null);
        if (isTaggedFor(inv.getChestplate(), eventId)) inv.setChestplate(null);
        if (isTaggedFor(inv.getLeggings(), eventId)) inv.setLeggings(null);
        if (isTaggedFor(inv.getBoots(), eventId)) inv.setBoots(null);
        if (isTaggedFor(inv.getItemInOffHand(), eventId)) inv.setItemInOffHand(null);

        if (session.getHiddenTogglers().remove(p.getUniqueId())) {
            for (UUID other : session.getParticipants()) {
                Player o = Bukkit.getPlayer(other);
                if (o != null) p.showPlayer(plugin, o);
            }
        }
    }

    private boolean isTaggedFor(ItemStack stack, String eventId) {
        if (stack == null || stack.getType() == Material.AIR) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        String tempTag = meta.getPersistentDataContainer().get(keys.tempItem, PersistentDataType.STRING);
        if (eventId.equals(tempTag)) return true;
        String ammoTagValue = meta.getPersistentDataContainer().get(keys.ammoTag, PersistentDataType.STRING);
        return eventId.equals(ammoTagValue);
    }

    private void stripTempItems() {
        if (session == null) return;
        for (UUID uuid : session.getParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) stripTempItemsFrom(p);
        }
    }

    private void setBarrierBlocks(EventDefinition def, Material material) {
        Location a = def.getBarrierPos1();
        Location b = def.getBarrierPos2();
        if (a == null || b == null || a.getWorld() == null) return;

        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());

        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    a.getWorld().getBlockAt(x, y, z).setType(material);
    }

    // ---------------------------------------------------------------
    // Parkour: pressure plates & mud checkpoints
    // ---------------------------------------------------------------

    public void handleParkourPlate(Player player, Material plateType) {
        if (session == null || session.getState() != EventState.RUNNING) return;
        EventDefinition def = session.getDefinition();
        if (def.getType() != EventType.PARKOUR) return;
        if (!session.isParticipant(player.getUniqueId()) || session.getDisqualified().contains(player.getUniqueId())) return;

        if (plateType == Material.CHERRY_PRESSURE_PLATE) {
            // Nothing else runs before this - nobody else can win once this fires.
            declareWinner(player);
        } else if (plateType == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            session.getCheckpoints().put(player.getUniqueId(), player.getLocation().clone());
            player.playSound(player.getLocation(), Msg.sound(plugin.getConfig(), "checkpoint-save", Sound.BLOCK_NOTE_BLOCK_CHIME), 1f, 1.2f);
        }
    }

    public void handleMudTouch(Player player) {
        if (session == null || session.getState() != EventState.RUNNING) return;
        EventDefinition def = session.getDefinition();
        if (def.getType() != EventType.PARKOUR) return;
        if (!session.isParticipant(player.getUniqueId()) || session.getDisqualified().contains(player.getUniqueId())) return;

        Location checkpoint = session.getCheckpoints().getOrDefault(player.getUniqueId(), def.getArenaLocation());
        if (checkpoint != null) player.teleport(checkpoint);
        player.playSound(player.getLocation(), Msg.sound(plugin.getConfig(), "mud-trap", Sound.BLOCK_MUD_BREAK), 1f, 0.8f);
    }

    // ---------------------------------------------------------------
    // Jackpot
    // ---------------------------------------------------------------

    private void beginJackpot() {
        List<UUID> entries = new ArrayList<>(session.getJackpotEntries());
        if (entries.size() < 2) {
            String name = Msg.plainName(session.getDefinition().getDisplayName());
            Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "event-cancelled-empty", "event", name)));
            endSession();
            return;
        }
        session.setState(EventState.RUNNING);

        jackpotSpinTask = new BukkitRunnable() {
            int tick = 0;
            final int totalTicks = 20;

            @Override
            public void run() {
                if (tick >= totalTicks) {
                    UUID winner = entries.get(random.nextInt(entries.size()));
                    Player winnerPlayer = Bukkit.getPlayer(winner);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        p.sendActionBar(Msg.legacy("&6&l" + (winnerPlayer != null ? winnerPlayer.getName() : "???")));
                    }
                    if (winnerPlayer != null) declareWinner(winnerPlayer); else endSession();
                    cancel();
                    return;
                }
                UUID flash = entries.get(random.nextInt(entries.size()));
                Player flashPlayer = Bukkit.getPlayer(flash);
                String flashName = flashPlayer != null ? flashPlayer.getName() : "???";
                for (Player p : Bukkit.getOnlinePlayers()) {
                    p.sendActionBar(Msg.legacy("&e" + flashName));
                    p.playSound(p.getLocation(), Msg.sound(plugin.getConfig(), "jackpot-spin", Sound.UI_BUTTON_CLICK), 0.6f, 1.2f);
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 4L);
    }

    // ---------------------------------------------------------------
    // Disqualify / Win / End
    // ---------------------------------------------------------------

    public void disqualify(Player player) {
        if (session == null || !session.isParticipant(player.getUniqueId())) return;
        EventDefinition def = session.getDefinition();
        session.getDisqualified().add(player.getUniqueId());

        // Eliminated players go to the spectator area if one is set, so they can
        // watch the rest of the match - falling back to the pre-join lobby, then
        // the shared return spawn, if nothing more specific is configured.
        // Captured BEFORE touching their inventory below: if stripping temp gear
        // from an already-dead player ever throws, the respawn destination must
        // already be safely recorded regardless.
        Location dest = def.getSpectatorLocation() != null ? def.getSpectatorLocation()
                : (def.getLobbyLocation() != null ? def.getLobbyLocation() : getReturnSpawn());
        if (dest != null) pendingRespawns.put(player.getUniqueId(), dest);

        stripTempItemsFrom(player);

        if (dest != null && !player.isDead()) player.teleport(dest);

        String name = Msg.plainName(def.getDisplayName());
        Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "disqualified", "player", player.getName(), "event", name)));

        EventType type = def.getType();
        if ((type == EventType.DODGEBOLT || type == EventType.STRAFE || type == EventType.PVP_TOURNAMENT
                || type == EventType.DODGEBALL || type == EventType.VOLLEY_CHARGE)
                && session.getState() == EventState.RUNNING) {
            Set<UUID> active = session.getActiveParticipants();
            if (active.size() == 1) {
                Player last = Bukkit.getPlayer(active.iterator().next());
                if (last != null) declareWinner(last);
            } else if (def.isTeamEvent() && active.size() > 1) {
                // If one whole side just got wiped but the other still has 2+
                // survivors, they can't fight each other with friendly fire off -
                // re-divide everyone left into fresh Blue/Red teams and keep
                // going until only one player remains overall.
                long blueCount = active.stream().filter(u -> "BLUE".equals(session.getTeams().get(u))).count();
                long redCount = active.size() - blueCount;
                if (blueCount == 0 || redCount == 0) {
                    reshuffleTeams(def, active);
                }
            }
        }

        // If the disqualified player was mid-hold of the live ammo (never fired -
        // e.g. they died to fall damage, void, lag, or anything other than a live
        // shot), stripTempItemsFrom() above just deleted it from their inventory
        // with no world drop. Only fires if nothing above (declareWinner or a
        // reshuffle) already handed out a fresh round - both of those reset
        // ammoHolder themselves, so this check is naturally skipped when they did.
        if (session != null && def.isTeamEvent() && player.getUniqueId().equals(session.getAmmoHolder())) {
            String team = session.getTeams().get(player.getUniqueId());
            if (team != null) beginAmmoRound(def, team);
        }
    }

    /** Re-divides the surviving players into fresh Blue/Red teams after one side gets fully wiped, so the match keeps going until only one player remains. */
    private void reshuffleTeams(EventDefinition def, Set<UUID> activePlayers) {
        List<UUID> shuffled = new ArrayList<>(activePlayers);
        Collections.shuffle(shuffled, random);
        session.getTeams().clear();
        Msg.broadcast(Msg.legacy("&d&lOne team was wiped out! Survivors are being re-divided into new teams..."));

        for (int i = 0; i < shuffled.size(); i++) {
            UUID uuid = shuffled.get(i);
            String team = (i % 2 == 0) ? "BLUE" : "RED";
            session.getTeams().put(uuid, team);

            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            Location dest = randomTeamSpawnPoint(def, team);
            if (dest != null) p.teleport(dest);
            p.sendMessage(Msg.legacy(team.equals("BLUE")
                    ? "&9&lRe-split! You are now on the BLUE team!"
                    : "&c&lRe-split! You are now on the RED team!"));
        }

        if (def.hasAmmoPoints()) {
            String starting = random.nextBoolean() ? "BLUE" : "RED";
            beginAmmoRound(def, starting);
        }
    }

    /** Set by disqualify() when a player died mid-event; PlayerRespawnEvent consumes this to send them to the spectator/lobby area instead of the vanilla bed spawn. */
    public Location consumePendingRespawn(Player player) {
        return pendingRespawns.remove(player.getUniqueId());
    }

    /** Gives whatever is configured under Edit Kill Rewards to a killer during a PvP-style event. Permanent - not stripped like temp items. */
    public void giveKillReward(Player killer, EventDefinition def) {
        for (ItemStack reward : def.getKillRewards()) {
            if (reward == null || reward.getType() == Material.AIR) continue;
            Map<Integer, ItemStack> leftover = killer.getInventory().addItem(reward.clone());
            for (ItemStack over : leftover.values()) {
                killer.getWorld().dropItemNaturally(killer.getLocation(), over);
            }
        }
    }

    public void declareWinner(Player winner) {
        if (session == null) return;
        EventDefinition def = session.getDefinition();
        String name = Msg.plainName(def.getDisplayName());
        Msg.broadcast(Msg.legacy(Msg.get(plugin.getConfig(), "winner", "player", winner.getName(), "event", name)));
        Sound winSound = Msg.sound(plugin.getConfig(), "winner", Sound.ENTITY_PLAYER_LEVELUP);
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), winSound, 1f, 1f);

        List<ItemStack> rewards = def.getRewards();
        if (rewards.isEmpty()) {
            plugin.getLogger().warning("Event '" + def.getId() + "' has no rewards configured - " + winner.getName()
                    + " won but received nothing. Set rewards from /boxevents gui -> the event -> Edit Rewards.");
            Msg.broadcast(Msg.legacy("&8(No reward is configured for this event yet - an admin should set one in /boxevents gui.)"));
        } else {
            for (ItemStack reward : rewards) {
                if (reward == null || reward.getType() == Material.AIR) continue;
                Map<Integer, ItemStack> leftover = winner.getInventory().addItem(reward.clone());
                for (ItemStack over : leftover.values()) {
                    winner.getWorld().dropItemNaturally(winner.getLocation(), over);
                }
            }
        }

        // KOTH winners stay exactly where they are - no teleport at all. Every
        // other event type returns its participants to the shared spawn.
        if (def.getType() != EventType.KOTH) {
            returnAllParticipants();
        }
        endSession();
    }

    private void returnAllParticipants() {
        if (session == null) return;
        Location spawn = getReturnSpawn();
        if (spawn == null) return;
        for (UUID uuid : session.getParticipants()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;
            if (p.isDead()) {
                // Still on the death/respawn screen (e.g. the deciding kill just
                // happened) - teleporting now is a silent no-op. Overwrite their
                // pending respawn destination with the real end-of-match spawn
                // instead of wherever they'd otherwise land (the spectator area).
                pendingRespawns.put(uuid, spawn);
            } else {
                p.teleport(spawn);
            }
        }
    }

    public Location getReturnSpawn() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("return-spawn");
        if (sec == null) return null;

        World world = Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null && !Bukkit.getWorlds().isEmpty()) world = Bukkit.getWorlds().get(0);
        if (world == null) return null;

        // By default, always defer to the world's actual spawn point - which is
        // exactly what most /setspawn or /sethub plugins update - so this works
        // automatically without needing separate configuration. Clicking "Set
        // Return Spawn" in the GUI switches this off in favour of a custom point.
        if (sec.getBoolean("use-world-spawn", true)) {
            return world.getSpawnLocation();
        }
        return new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"),
                (float) sec.getDouble("yaw"), (float) sec.getDouble("pitch"));
    }

    public void endSession() {
        if (session != null) {
            stripTempItems();
            if (session.getDefinition().hasBarrier()) setBarrierBlocks(session.getDefinition(), Material.BARRIER);
            if (session.getDefinition().isTeamEvent()) {
                removeStrayAmmoEntities(session.getDefinition()); // guarantee a clean slate for the next match
                resolvedAmmoEntities.clear();
            }
        }
        if (countdownTask != null) countdownTask.cancel();
        if (jackpotSpinTask != null) jackpotSpinTask.cancel();
        session = null;
    }

    public void forceStop() {
        if (session != null) {
            if (session.getDefinition().getType() != EventType.KOTH) returnAllParticipants();
        }
        endSession();
    }

    public EventSession getSession() {
        return session;
    }

    /** Whether this player is currently standing in a no-commands zone for whatever event is active right now. */
    public boolean isInRestrictedZone(Player player) {
        if (session == null) return false;
        EventDefinition def = session.getDefinition();
        if (!def.hasRestrictedZone()) return false;
        return RegionUtil.contains(player.getLocation(), def.getRestrictedPos1(), def.getRestrictedPos2());
    }

    public void handleQuit(Player player) {
        leave(player);
    }

    // ---------------------------------------------------------------
    // Next-event boss bar + hologram + KOTH capture (1x/second)
    // ---------------------------------------------------------------

    private void initBossBar() {
        if (nextEventBar == null) {
            nextEventBar = BossBar.bossBar(Component.text("..."), 1f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
        // Deliberately not shown to anyone here - it's only visible while an
        // event is actually active (waiting or running); updateBossBar() below
        // shows/hides it dynamically every second based on session state.
    }

    public void addBossBarViewer(Player p) {
        if (nextEventBar != null && session != null && plugin.getConfig().getBoolean("next-event-timer.enabled", true)) {
            p.showBossBar(nextEventBar);
        }
    }

    private void secondTick() {
        tickKothCapture();
        updateBossBar();
        updateHologram();
    }

    private void tickKothCapture() {
        if (session == null || session.getState() != EventState.RUNNING) return;
        EventDefinition def = session.getDefinition();
        if (def.getType() != EventType.KOTH || !def.hasFinishRegion()) return;

        List<Player> inside = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (RegionUtil.contains(p.getLocation(), def.getFinishPos1(), def.getFinishPos2())) inside.add(p);
        }

        int captureSeconds = plugin.getConfig().getInt("koth.capture-seconds", 60);
        UUID currentHolder = session.getKothHolder();
        boolean holderStillInside = currentHolder != null
                && inside.stream().anyMatch(p -> p.getUniqueId().equals(currentHolder));

        if (holderStillInside) {
            // The original capturer keeps progressing regardless of anyone else stepping in.
            session.setKothHoldTicks(session.getKothHoldTicks() + 1);
        } else if (inside.size() == 1) {
            // No one was capturing yet (or the old holder left) - a lone player starts fresh.
            session.setKothHolder(inside.get(0).getUniqueId());
            session.setKothHoldTicks(1);
        } else {
            // Empty, or several players arrived with no established holder yet - nothing progresses.
            session.setKothHolder(null);
            session.setKothHoldTicks(0);
        }

        if (session.getKothHolder() != null && session.getKothHoldTicks() >= captureSeconds) {
            Player holder = Bukkit.getPlayer(session.getKothHolder());
            if (holder != null) { declareWinner(holder); return; }
        }

        // Periodic chat reminder every 10 seconds of progress.
        if (session.getKothHolder() != null && session.getKothHoldTicks() > 0 && session.getKothHoldTicks() % 5 == 0) {
            Player holder = Bukkit.getPlayer(session.getKothHolder());
            if (holder != null) {
                int left = Math.max(0, captureSeconds - session.getKothHoldTicks());
                Msg.broadcast(Msg.legacy("&e" + holder.getName() + " &7is capturing the KOTH &f(" + formatMMSS(left) + ")&7!"));
                Sound kothTick = Msg.sound(plugin.getConfig(), "koth-tick", Sound.BLOCK_NOTE_BLOCK_HAT);
                for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), kothTick, 0.5f, 1.5f);
            }
        }
    }

    private void updateBossBar() {
        if (nextEventBar == null || !plugin.getConfig().getBoolean("next-event-timer.enabled", true)) return;

        if (session == null) {
            // No active event at all - the bar should not be visible to anyone.
            for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(nextEventBar);
            return;
        }

        // An event is currently waiting/running - make sure everyone can see the bar.
        for (Player p : Bukkit.getOnlinePlayers()) p.showBossBar(nextEventBar);

        if (session.getDefinition().getType() == EventType.KOTH
                && session.getState() == EventState.RUNNING && session.getDefinition().hasFinishRegion()) {
            int captureSeconds = plugin.getConfig().getInt("koth.capture-seconds", 60);
            int left = Math.max(0, captureSeconds - session.getKothHoldTicks());
            UUID holder = session.getKothHolder();
            Player holderPlayer = holder != null ? Bukkit.getPlayer(holder) : null;
            if (holderPlayer != null) {
                nextEventBar.name(Msg.legacy("&e&l" + holderPlayer.getName() + " &7capturing... &e" + formatMMSS(left)));
                nextEventBar.progress(clampProgress(1f - ((float) left / captureSeconds)));
                nextEventBar.color(BossBar.Color.YELLOW);
            } else {
                nextEventBar.name(Msg.legacy("&c&lContested! &7Clear the point to capture."));
                nextEventBar.progress(0f);
                nextEventBar.color(BossBar.Color.RED);
            }
            return;
        }

        nextEventBar.name(Msg.legacy("&6&lAn event is currently active!"));
        nextEventBar.progress(1f);
        nextEventBar.color(BossBar.Color.PINK);
    }

    private float clampProgress(float f) {
        return Math.max(0f, Math.min(1f, f));
    }

    /** How long the join/prepare window is for whatever event is currently active - used for messages that need to state it after the fact. */
    public String getSessionDurationFormatted() {
        if (session == null) return "0s";
        return formatTime(durationFor(session.getDefinition()));
    }

    public String getNextEventSummary() {
        if (session != null) return "An event is currently active!";
        int intervalMinutes = plugin.getConfig().getInt("event-interval-minutes", 60);
        long intervalMillis = intervalMinutes * 60_000L;
        long remaining = Math.max(0, intervalMillis - (System.currentTimeMillis() - schedulerState.getLastEventMillis()));
        return "Next random event in " + formatTime((int) (remaining / 1000))
                + (isBlackedOut() ? " (currently paused - outside active hours)" : "");
    }

    // ---------------------------------------------------------------
    // Hologram (native ArmorStand, no dependency needed)
    // ---------------------------------------------------------------

    private void updateHologram() {
        if (!plugin.getConfig().getBoolean("hologram.enabled", false)) {
            if (hologram != null) { hologram.remove(); hologram = null; }
            return;
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("hologram.location");
        if (sec == null) return;
        World world = Bukkit.getWorld(sec.getString("world", "world"));
        if (world == null) return;
        Location loc = new Location(world, sec.getDouble("x"), sec.getDouble("y"), sec.getDouble("z"));

        if (hologram == null || hologram.isDead() || !hologram.getWorld().equals(world)) {
            hologram = findOrSpawnHologram(loc);
        }
        if (hologram != null) {
            hologram.customName(Msg.legacy(session != null
                    ? "&6&lEvent in progress!"
                    : "&b&lNext Event: &f" + formatTime((int) Math.max(0,
                        (plugin.getConfig().getInt("event-interval-minutes", 60) * 60_000L
                                - (System.currentTimeMillis() - schedulerState.getLastEventMillis())) / 1000))));
        }
    }

    private ArmorStand findOrSpawnHologram(Location loc) {
        for (Entity e : loc.getWorld().getEntitiesByClass(ArmorStand.class)) {
            if (e.getScoreboardTags().contains("bpe_hologram")) return (ArmorStand) e;
        }
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
        stand.setInvisible(true);
        stand.setMarker(true);
        stand.setGravity(false);
        stand.setCustomNameVisible(true);
        stand.addScoreboardTag("bpe_hologram");
        return stand;
    }

    public void removeHologram() {
        if (hologram != null) { hologram.remove(); hologram = null; }
    }

    // ---------------------------------------------------------------
    // Region-select wand
    // ---------------------------------------------------------------

    public void beginWandSelection(Player admin, String eventId, String target) {
        wandContext.put(admin.getUniqueId(), new String[]{eventId, target});
        ItemStack wand = new ItemBuilder(Material.STICK, 1)
                .name("&6&lBoxPvpEvents Region Wand")
                .lore(List.of("&7Left-click a block: &fPosition 1", "&7Right-click a block: &fPosition 2",
                        "&7Target: &f" + eventId + " (" + target + ")",
                        "&8Does not interfere with WorldEdit."))
                .tag(keys.wandTool, eventId + ":" + target)
                .build();
        admin.getInventory().addItem(wand);
    }

    public boolean hasWandContext(Player admin) {
        return wandContext.containsKey(admin.getUniqueId());
    }

    /** Identifies our wand purely by its own tag - never by material - so it can never collide with WorldEdit's axe. */
    public boolean isWandItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.STICK) return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(keys.wandTool, PersistentDataType.STRING);
    }

    public void handleWandClick(Player admin, boolean firstCorner, Location block) {
        String[] ctx = wandContext.get(admin.getUniqueId());
        if (ctx == null) return;
        EventDefinition def = registry.get(ctx[0]);
        if (def == null) {
            admin.sendMessage(Msg.legacy("&cThat event no longer exists."));
            wandContext.remove(admin.getUniqueId());
            return;
        }
        switch (ctx[1]) {
            case "barrier" -> { if (firstCorner) def.setBarrierPos1(block); else def.setBarrierPos2(block); }
            case "mud" -> { if (firstCorner) def.setMudPos1(block); else def.setMudPos2(block); }
            case "restricted" -> { if (firstCorner) def.setRestrictedPos1(block); else def.setRestrictedPos2(block); }
            case "spawn-area" -> { if (firstCorner) def.setSpawnAreaPos1(block); else def.setSpawnAreaPos2(block); }
            case "teamwall" -> { if (firstCorner) def.setTeamWallPos1(block); else def.setTeamWallPos2(block); }
            case "blue-spawn-area" -> { if (firstCorner) def.setBlueSpawnAreaPos1(block); else def.setBlueSpawnAreaPos2(block); }
            case "red-spawn-area" -> { if (firstCorner) def.setRedSpawnAreaPos1(block); else def.setRedSpawnAreaPos2(block); }
            case "no-hunger" -> { if (firstCorner) def.setNoHungerPos1(block); else def.setNoHungerPos2(block); }
            default -> { if (firstCorner) def.setFinishPos1(block); else def.setFinishPos2(block); }
        }
        registry.save();
        Sound wandSound = Msg.sound(plugin.getConfig(), "wand-select", Sound.BLOCK_NOTE_BLOCK_HAT);
        admin.playSound(admin.getLocation(), wandSound, 1f, firstCorner ? 1f : 1.5f);
        admin.sendMessage(Msg.legacy("&aPosition " + (firstCorner ? "1" : "2") + " set and &lsaved&a for '" + def.getId()
                + "' (" + ctx[1] + ") at " + block.getBlockX() + ", " + block.getBlockY() + ", " + block.getBlockZ() + "."));
    }

    /** Re-confirms the current saved state of whichever region the admin last selected with the wand. */
    public String describeWandTarget(Player admin) {
        String[] ctx = wandContext.get(admin.getUniqueId());
        if (ctx == null) return null;
        EventDefinition def = registry.get(ctx[0]);
        if (def == null) return null;
        boolean set = switch (ctx[1]) {
            case "barrier" -> def.hasBarrier();
            case "mud" -> def.hasMudRegion();
            case "restricted" -> def.hasRestrictedZone();
            case "spawn-area" -> def.hasSpawnArea();
            case "teamwall" -> def.hasTeamWall();
            case "blue-spawn-area" -> def.hasBlueSpawnArea();
            case "red-spawn-area" -> def.hasRedSpawnArea();
            case "no-hunger" -> def.hasNoHungerZone();
            default -> def.hasFinishRegion();
        };
        return def.getId() + " (" + ctx[1] + "): " + (set ? "both corners saved" : "waiting for both corners");
    }
}
