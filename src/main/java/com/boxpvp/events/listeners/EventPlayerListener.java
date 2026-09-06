package com.boxpvp.events.listeners;

import com.boxpvp.events.events.EventManager;
import com.boxpvp.events.events.EventSession;
import com.boxpvp.events.events.EventState;
import com.boxpvp.events.events.EventType;
import com.boxpvp.events.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class EventPlayerListener implements Listener {

    private final JavaPlugin plugin;
    private final EventManager eventManager;

    public EventPlayerListener(JavaPlugin plugin, EventManager eventManager) {
        this.plugin = plugin;
        this.eventManager = eventManager;
    }

    // ---------------------------------------------------------------
    // Dodgebolt: getting shot by another participant = instant DQ (no death)
    // ---------------------------------------------------------------
    @EventHandler
    public void onProjectileHit(EntityDamageByEntityEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        if (session.getDefinition().getType() != EventType.DODGEBOLT) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!(e.getDamager() instanceof Arrow arrow)) return;
        if (!(arrow.getShooter() instanceof Player shooter)) return;

        if (!session.isParticipant(victim.getUniqueId()) || !session.isParticipant(shooter.getUniqueId())) return;
        if (victim.getUniqueId().equals(shooter.getUniqueId())) return;

        eventManager.disqualify(victim); // handles the spectator/lobby/spawn teleport itself
    }

    // ---------------------------------------------------------------
    // Dodgeball / Volley Charge: any arrow or wind charge hit from another
    // participant is an instant kill regardless of armor/damage, and
    // friendly fire between teammates is disabled.
    // ---------------------------------------------------------------
    @EventHandler
    public void onTeamProjectileHit(EntityDamageByEntityEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        EventType type = session.getDefinition().getType();
        if (type != EventType.DODGEBALL && type != EventType.VOLLEY_CHARGE) return;
        if (!(e.getEntity() instanceof Player victim)) return;
        if (!session.isParticipant(victim.getUniqueId()) || session.getDisqualified().contains(victim.getUniqueId())) return;

        Player shooter = null;
        if (e.getDamager() instanceof Arrow arrow && arrow.getShooter() instanceof Player p) shooter = p;
        else if (e.getDamager() instanceof org.bukkit.entity.WindCharge wc && wc.getShooter() instanceof Player p) shooter = p;
        if (shooter == null || !session.isParticipant(shooter.getUniqueId())) return;
        if (shooter.getUniqueId().equals(victim.getUniqueId())) return;

        String shooterTeam = session.getTeams().get(shooter.getUniqueId());
        String victimTeam = session.getTeams().get(victim.getUniqueId());
        if (shooterTeam != null && shooterTeam.equals(victimTeam)) {
            e.setCancelled(true); // friendly fire off
            return;
        }

        e.setDamage(1000); // guaranteed lethal in one hit, regardless of armor
    }

    // ---------------------------------------------------------------
    // Dodgeball / Volley Charge ammo economy: only the currently-entitled
    // team can pick up the generated ammo, and only one player at a time.
    // ---------------------------------------------------------------
    @EventHandler
    public void onAmmoPickup(EntityPickupItemEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        var def = session.getDefinition();
        if (!def.isTeamEvent()) return;
        ItemStack stack = e.getItem().getItemStack();
        if (!eventManager.isAmmoItem(stack, def.getId())) return;

        String playerTeam = session.getTeams().get(player.getUniqueId());
        String entitled = session.getAmmoEntitledTeam();
        // A fresh spawn at a team's own generator is exclusive to that team.
        // A pickup that landed after a miss has no team restriction at all -
        // whichever team reaches it first gets it (entitled == null).
        if (playerTeam == null || (entitled != null && !playerTeam.equals(entitled)) || session.getAmmoHolder() != null) {
            e.setCancelled(true);
            return;
        }

        e.setCancelled(true); // place it manually so a Dodgeball pickup also gets a bow, in exact slots
        e.getItem().remove();
        eventManager.giveAmmoToPlayer(player, stack.clone());
        session.setAmmoHolder(player.getUniqueId());
        player.playSound(player.getLocation(), Msg.sound(plugin.getConfig(), "ammo-pickup", Sound.ITEM_ARMOR_EQUIP_IRON), 1f, 1.2f);
    }

    // The bow/arrow/wind charge are only "borrowed" while holding live ammo -
    // never allow them to be manually dropped (they still disappear correctly
    // on their own once fired, or if the player leaves/dies/gets disqualified).
    @EventHandler
    public void onDropAmmo(PlayerDropItemEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null) return;
        if (eventManager.isAmmoItem(e.getItemDrop().getItemStack(), session.getDefinition().getId())) {
            e.setCancelled(true);
        }
    }

    // Tags the actual fired projectile the instant it's launched by whoever is
    // currently holding the ammo, so the resolution logic below can recognize
    // it (and know which team fired it) - and immediately clears the bow +
    // any leftover ammo item from their inventory, since both are only
    // "borrowed" for the duration of holding live ammo.
    @EventHandler
    public void onAmmoLaunch(ProjectileLaunchEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        var def = session.getDefinition();
        if (!def.isTeamEvent()) return;
        if (!(e.getEntity() instanceof Arrow arrow) && !(e.getEntity() instanceof org.bukkit.entity.WindCharge)) return;
        if (!(e.getEntity().getShooter() instanceof Player shooter)) return;
        if (session.getAmmoHolder() == null || !session.getAmmoHolder().equals(shooter.getUniqueId())) return;

        // IMPORTANT: tag with the SHOOTER's own team, not session.getAmmoEntitledTeam().
        // The "entitled" team is null during any free-for-all round (i.e. right after a
        // miss), and PersistentDataContainer#set() throws an NPE on a null value - which
        // silently aborted the rest of this method (no glow, no pickup-lock, ammoHolder
        // never cleared) every time someone re-fired ammo that had landed from a miss.
        // The shooter's team is always non-null for a participant, so this is always safe.
        String shooterTeam = session.getTeams().get(shooter.getUniqueId());
        if (shooterTeam == null) return;

        e.getEntity().getPersistentDataContainer().set(eventManager.getKeys().ammoTag, PersistentDataType.STRING, def.getId());
        e.getEntity().getPersistentDataContainer().set(eventManager.getKeys().ammoTeam, PersistentDataType.STRING, shooterTeam);
        e.getEntity().setGlowing(true); // stays glowing the whole flight, same as it glowed on the ground

        if (e.getEntity() instanceof Arrow a) {
            a.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED); // never lets it become a normal stuck-in-wall pickup
        }

        eventManager.clearAmmoItemsFrom(shooter, def.getId()); // removes the bow (and any leftover duplicate) right away
        session.setAmmoHolder(null); // it's in the air now, no longer "held"

        // Safety net: an arrow that embeds in a block (a barrier wall, for
        // example) is still isValid() and not "dead" - it's just stationary -
        // so waiting for removal/death alone never catches that case, leaving
        // it stuck as a normal, unpickable arrow forever. Wind charges can
        // also vanish via their own explosion logic without a hit event ever
        // firing. Watching velocity directly catches BOTH: the instant the
        // projectile stops moving (landed/embedded) or disappears entirely,
        // we resolve the ammo economy and force it into our own glowing
        // pickup representation - it should never look or behave like a
        // normal Minecraft arrow at any point.
        final org.bukkit.entity.Entity watched = e.getEntity();
        final String firedByTeam = shooterTeam;
        new BukkitRunnable() {
            Location lastLoc = watched.getLocation().clone();
            int ticks = 0;
            int stationaryTicks = 0;

            @Override
            public void run() {
                if (eventManager.getSession() != session || ticks > 400) { cancel(); return; }

                if (!watched.isValid()) {
                    cancel();
                    if (eventManager.tryResolveAmmoOnce(watched)) {
                        eventManager.resolveAmmoImpact(def, lastLoc, firedByTeam, false);
                    }
                    return;
                }

                lastLoc = watched.getLocation().clone();
                double speedSq = watched.getVelocity().lengthSquared();
                stationaryTicks = speedSq < 0.01 ? stationaryTicks + 1 : 0;

                if (stationaryTicks >= 2) {
                    cancel();
                    if (eventManager.tryResolveAmmoOnce(watched)) {
                        watched.remove(); // force it gone even if no hit event ever fired for it
                        eventManager.resolveAmmoImpact(def, lastLoc, firedByTeam, false);
                    }
                    return;
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    // Resolves what happens next once the tagged ammo actually lands: a hit on
    // an enemy hands the OTHER team a fresh spawn at their own generator; a
    // miss leaves it as a pickup exactly where it stopped, snapped down to the
    // nearest floor so it never ends up floating.
    @EventHandler
    public void onAmmoResolve(ProjectileHitEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        var def = session.getDefinition();
        if (!def.isTeamEvent()) return;
        Projectile proj = e.getEntity();
        String tag = proj.getPersistentDataContainer().get(eventManager.getKeys().ammoTag, PersistentDataType.STRING);
        if (!def.getId().equals(tag)) return;
        if (!eventManager.tryResolveAmmoOnce(proj)) return; // the despawn watchdog above already handled it

        String firedByTeam = proj.getPersistentDataContainer().get(eventManager.getKeys().ammoTeam, PersistentDataType.STRING);
        if (firedByTeam == null) return;
        Location impact = proj.getLocation().clone();
        proj.remove();

        boolean hitEnemy = false;
        if (e.getHitEntity() instanceof Player victim && session.isParticipant(victim.getUniqueId())) {
            String victimTeam = session.getTeams().get(victim.getUniqueId());
            hitEnemy = victimTeam != null && !victimTeam.equals(firedByTeam);
        }

        eventManager.resolveAmmoImpact(def, impact, firedByTeam, hitEnemy);
    }

    // ---------------------------------------------------------------
    // PvP Tournament / Dodgeball / Volley Charge: dying eliminates you.
    // Last one standing auto-wins (handled inside disqualify()). Drops are
    // cleared so temp gear never ends up scattered on the ground, respawn
    // is redirected to the spectator area via PlayerRespawnEvent below, and
    // whoever landed the kill gets their configured kill reward.
    // ---------------------------------------------------------------
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player player = e.getPlayer();
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        EventType type = session.getDefinition().getType();
        if (type != EventType.PVP_TOURNAMENT && type != EventType.DODGEBALL && type != EventType.VOLLEY_CHARGE) return;
        if (!session.isParticipant(player.getUniqueId()) || session.getDisqualified().contains(player.getUniqueId())) return;

        e.getDrops().clear();
        e.setDroppedExp(0);

        Player killer = player.getKiller();
        if (killer != null && session.isParticipant(killer.getUniqueId()) && !killer.getUniqueId().equals(player.getUniqueId())) {
            eventManager.giveKillReward(killer, session.getDefinition());
        }

        eventManager.disqualify(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location dest = eventManager.consumePendingRespawn(e.getPlayer());
        if (dest == null) return;
        e.setRespawnLocation(dest);

        // Safety net: force a real teleport a couple ticks after respawn too, in
        // case the respawn-location alone doesn't stick for any reason.
        Player player = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) player.teleport(dest);
        }, 2L);
    }

    // ---------------------------------------------------------------
    // Dodgeball / Volley Charge: an invisible wall that blocks player
    // movement only - projectiles are never touched, so arrows and wind
    // charges pass through it freely. Enforced purely by cancelling the
    // move, no real blocks are ever placed.
    // ---------------------------------------------------------------
    @EventHandler
    public void onTeamWallMove(PlayerMoveEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        var def = session.getDefinition();
        if (!def.isTeamEvent() || !def.hasTeamWall()) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }
        if (com.boxpvp.events.util.RegionUtil.contains(e.getTo(), def.getTeamWallPos1(), def.getTeamWallPos2())) {
            e.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------
    // Parkour: win plate (cherry) and checkpoint plate (light weighted/gold).
    // Uses Action.PHYSICAL - Minecraft's own dedicated "a plate was pressed"
    // event - instead of inferring it from movement, which is far more
    // reliable than checking block-at-location on every tiny move.
    // ---------------------------------------------------------------
    @EventHandler
    public void onPlatePress(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL || e.getClickedBlock() == null) return;

        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        if (session.getDefinition().getType() != EventType.PARKOUR) return;

        Material type = e.getClickedBlock().getType();
        if (type == Material.CHERRY_PRESSURE_PLATE || type == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            eventManager.handleParkourPlate(e.getPlayer(), type);
        }
    }

    // ---------------------------------------------------------------
    // Parkour: mud traps - either real mud blocks OR an admin-marked mud
    // region. Not an interactive block, so this stays movement-based.
    // ---------------------------------------------------------------
    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        // Only bother once the player actually changes block (perf: avoid running every micro-movement)
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.RUNNING) return;
        var def = session.getDefinition();
        if (def.getType() != EventType.PARKOUR) return;

        Player player = e.getPlayer();
        Material standingOn = player.getLocation().clone().subtract(0, 1, 0).getBlock().getType();
        boolean inMudRegion = def.hasMudRegion()
                && com.boxpvp.events.util.RegionUtil.contains(player.getLocation(), def.getMudPos1(), def.getMudPos2());
        if (standingOn == Material.MUD || inMudRegion) {
            eventManager.handleMudTouch(player);
        }
    }

    // ---------------------------------------------------------------
    // Visibility toggle compass (parkour) - identified ONLY by our own
    // PersistentDataContainer tag, never by name/material matching, and the
    // interaction is fully cancelled so it can never trigger a teleport from
    // another plugin's compass-based warp system.
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        ItemStack item = e.getItem();
        if (item != null && isVisibilityToggle(item)) {
            e.setCancelled(true);
            e.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            e.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
            if (e.getAction().name().contains("RIGHT")) {
                toggleVisibility(e.getPlayer());
            }
            return;
        }

        // Region-select wand (identified by its own tag, never by material - avoids any WorldEdit collision)
        if (item != null && eventManager.isWandItem(item) && eventManager.hasWandContext(e.getPlayer())
                && e.getClickedBlock() != null) {
            e.setCancelled(true);
            boolean firstCorner = e.getAction().name().startsWith("LEFT");
            eventManager.handleWandClick(e.getPlayer(), firstCorner, e.getClickedBlock().getLocation());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        ItemStack item = e.getPlayer().getInventory().getItemInMainHand();
        if (eventManager.isWandItem(item) && eventManager.hasWandContext(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    private boolean isVisibilityToggle(ItemStack item) {
        if (item.getType() != Material.COMPASS) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        String tag = meta.getPersistentDataContainer().get(eventManager.getKeys().visibilityToggle, PersistentDataType.STRING);
        return "true".equals(tag);
    }

    private void toggleVisibility(Player player) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getDefinition().getType() != EventType.PARKOUR) return;
        if (!session.isParticipant(player.getUniqueId())) return;

        boolean nowHidden = session.getHiddenTogglers().add(player.getUniqueId());
        if (!nowHidden) session.getHiddenTogglers().remove(player.getUniqueId());

        for (var otherUuid : session.getParticipants()) {
            Player o = plugin.getServer().getPlayer(otherUuid);
            if (o == null || o.equals(player)) continue;
            if (nowHidden) player.hidePlayer(plugin, o); else player.showPlayer(plugin, o);
        }
        player.sendMessage(Msg.legacy(nowHidden
                ? "&7You can no longer see other parkour participants."
                : "&7You can now see other parkour participants."));
    }

    // ---------------------------------------------------------------
    // Jackpot: typing "jackpot" in chat during the join window enters you
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent e) {
        EventSession session = eventManager.getSession();
        if (session == null || session.getState() != EventState.WAITING) return;
        if (session.getDefinition().getType() != EventType.JACKPOT) return;

        String text = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        if (!text.equalsIgnoreCase("jackpot")) return;

        e.setCancelled(true);
        Player player = e.getPlayer();
        boolean entered = eventManager.jackpotEnter(player);
        if (entered) player.sendMessage(Msg.legacy(Msg.get(plugin.getConfig(), "jackpot-joined")));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        eventManager.addBossBarViewer(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        eventManager.handleQuit(e.getPlayer());
    }

    // ---------------------------------------------------------------
    // No-commands zone: block every command except /events leave for anyone
    // physically standing in a marked zone while that event is active. Chat
    // (anything not starting with '/') is never touched by this event at all.
    // Admins are exempt so staff can still moderate from inside the zone.
    // ---------------------------------------------------------------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();
        if (player.hasPermission("boxpvpevents.admin")) return;
        if (!eventManager.isInRestrictedZone(player)) return;

        // Token-based check, and strip any "pluginname:" prefix Bukkit adds when
        // another installed plugin also happens to register an "events" command -
        // a plain substring/exact-string match would wrongly block the real
        // /events leave in that situation.
        String raw = e.getMessage().substring(1).trim().toLowerCase();
        int colon = raw.indexOf(':');
        int firstSpace = raw.indexOf(' ');
        if (colon >= 0 && (firstSpace == -1 || colon < firstSpace)) raw = raw.substring(colon + 1);
        String[] parts = raw.split("\\s+");
        if (parts.length >= 2 && parts[0].equals("events") && parts[1].equals("leave")) return; // always allowed

        e.setCancelled(true);
        player.sendMessage(Msg.legacy("&cCommands are disabled here. Type &f/events leave &cto exit the event."));
    }

    // ---------------------------------------------------------------
    // No-hunger zone: inside a marked region, food level can never decrease
    // while an event session is active. Eating (an increase) is still fine.
    // ---------------------------------------------------------------
    @EventHandler
    public void onFoodChange(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        EventSession session = eventManager.getSession();
        if (session == null) return;
        var def = session.getDefinition();
        if (!def.hasNoHungerZone()) return;
        if (e.getFoodLevel() >= player.getFoodLevel()) return; // allow increases (eating)
        if (com.boxpvp.events.util.RegionUtil.contains(player.getLocation(), def.getNoHungerPos1(), def.getNoHungerPos2())) {
            e.setCancelled(true);
        }
    }
}
