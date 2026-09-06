# BoxPvpEvents

Random mini-events plugin for BoxPvP servers. Built for **Paper 26.2**.

Every `event-interval-minutes` (default: 1 hour), outside any configured
blackout window, the plugin picks a random event and announces it with a
short "Random event starting..." title and a spinning fortune-wheel of
names. Most event types then give players a join window with a clickable
chat button; **KOTH goes live immediately with no wait at all**, and
**Jackpot** only ever happens in chat.

Includes: **Parkour, KOTH, Dodgebolt, Strafe, PvP Tournament, Jackpot**, plus
a GUI to add your own custom events - no coding required for anything.

## Commands

Two separate commands on purpose, so regular players never see admin options:

- **`/events`** (public) - `join`, `leave`, `list`, `next`.
- **`/boxevents`** (admin only, hidden from players entirely via the
  permission on the command itself) - `gui`, `rename`, `wand`, `start <id|random>`,
  `stop`, `forcewin`, `sethologram`, `removehologram`, `reload`.

## Getting the .jar (GitHub Actions)

1. Push this folder to a GitHub repo.
2. `.github/workflows/build.yml` compiles it automatically. Actions tab ->
   latest run -> download the `BoxPvpEvents-jar` artifact.
3. Push a tag like `v1.0.0` to get a GitHub Release with the jar attached.

Building locally: `mvn clean package` (JDK 21+), jar lands in `target/`.

## Configuring events - `/boxevents gui`

Click any event to open its settings menu:

- **Set Arena Location / Set Lobby Location** - stand where you want, click.
  Shift-click to clear. Joining teleports players to the lobby if one is
  set, otherwise straight to the arena itself (the arena doubles as the
  waiting room unless you set up a separate lobby - useful for events like
  PvP Tournament where you want a holding room before the real arena).
  Not shown for Jackpot or KOTH, which don't use a location at all.
- **Region Wand: Barrier Wall** - click to get the wand (a plain stick,
  identified only by its own internal tag - it will never conflict with or
  get intercepted by WorldEdit). Left-click a block = corner 1, right-click
  = corner 2, each click saves immediately. That box fills with barrier
  blocks while players are waiting and clears to air the instant the event
  starts (works for any event type).
- **Region Wand: KOTH Capture Zone** - same wand, defines the KOTH hill.
- **Region Wand: Mud Trap Zone** (Parkour) - optional alternative to placing
  real mud blocks; mark a zone instead.
- **Get Checkpoint Plates / Get Win Plate** (Parkour) - see below.
- **Set Join Duration** - override the global countdown length for just this
  event (e.g. a shorter Jackpot window). Shift-click to reset to default.
- **Edit Temporary Items** - a chest GUI pre-filled with the event's current
  temp items. Drag items from your own inventory in; whatever's left when
  you close it is saved. Armor pieces are auto-equipped on players instead
  of sitting in their inventory. Everything here is automatically stripped
  the moment the event ends or a player leaves early - nothing survives.
- **Edit Rewards** - permanent items the winner keeps. **This is how you
  match your Crates system's key exactly**: open a crate, take the real key
  item it gives you, and drag that into this editor. Since it's a literal
  clone (identical NBT/meta), your Crates system recognizes it - no
  guessing involved. There is no built-in fallback item anymore; whatever
  you put here is exactly what winners get, for every event independently.
- **Rename / Recolor** - a palette of solid colours, or "Custom / Gradient"
  to type a name in chat. Both legacy `&codes` and MiniMessage gradients
  work, e.g. `<gradient:red:blue>Parkour</gradient>` - and now actually
  render correctly in the GUI's own icons too, not just in-game titles.
- **Delete This Event** - shift-click to confirm.

The main GUI's bottom row also has **Set Return Spawn** (shared by every
event) and **+ Add New Event** for custom event types.

If anything in the GUI ever misbehaves, `/boxevents rename <id> <name...>`
sets a name/colour directly from the command line as a guaranteed fallback.

## Parkour mechanics

- **Win**: place a **Winner Checkpoint** plate (a cherry pressure plate,
  given from the GUI) at the finish. The instant a player steps on it, they
  win - reward given, chat announcement, and everyone else in the event
  teleported to spawn, all synchronously with no delay.
- **Checkpoints**: place **Checkpoint Saver** plates (gold pressure plates,
  given from the GUI) along the course. Stepping on one silently saves it
  as that player's checkpoint (sound cue only, no chat/hotbar spam).
- **Mud**: place actual **mud blocks**, or mark a zone with the Mud Trap
  wand - either sends a player back to their last checkpoint (or the start
  if they haven't hit one yet). Fully separate code path from the win/
  checkpoint plates, so they never interfere with each other.
- **Visibility compass**: automatically given to every participant,
  right-click to hide/show other participants. Identified only by its own
  internal tag (never by name or material), and the interaction is fully
  cancelled - it cannot be mistaken for, or hijacked into, a teleport item
  by another plugin. Auto-removed, with visibility auto-restored, the
  moment the event ends or a player leaves/disconnects.

## KOTH

KOTH has **no arena, no lobby, no join step at all**. The moment it's
picked, it's announced and immediately live - no countdown, no waiting -
players just need to rush to the arena you built. Whoever holds the
capture zone (set with the KOTH wand) **alone** wins after
`koth.capture-seconds` (default 60) of uninterrupted holding; a second
player stepping in resets the progress, but the boss bar and a chat
reminder every 5 seconds keep everyone posted on who's capturing and how
long is left. The winner receives their reward and stays exactly where
they are - no teleport.

## Jackpot

No teleporting, no click-to-join button - the announcement just states the
event started. Players type `jackpot` in chat during the join window; each
entry is broadcast in bold rainbow colours. When the timer hits zero, names
spin in everyone's action bar and a random entrant wins.

## Scheduling

- `event-interval-minutes` - how often a random event fires.
- `blackout-windows` - a list of `{start, end}` 24h time ranges (e.g.
  `00:00`-`08:00` for night) during which no events fire. Add as many as
  you like.
- The countdown to the next event **persists across restarts** - it's saved
  to `data.yml` on every event trigger (and autosaved periodically as an
  extra safety net), so it picks up exactly where it left off rather than
  resetting to a full interval.

## Seeing the countdown

- A boss bar is shown to everyone automatically (toggle with
  `next-event-timer.enabled` in config.yml).
- `/events next` - on-demand text answer.
- Optional floating hologram: stand where you want it and run
  `/boxevents sethologram` (a plain, native armor stand - no extra plugin
  needed). `/boxevents removehologram` to take it down.

## Sounds

Every notable moment (announcement, spin, countdown ticks, event start,
winner, jackpot spin, checkpoint save, mud trap, wand selection, KOTH
capture tick) has a configurable sound under `sounds:` in config.yml - just
use any vanilla `Sound` enum name. Leave a value blank or invalid to fall
back to the plugin's default for that moment.

## Notes on what's automatic per event type

- **Dodgebolt / Strafe / PvP Tournament**: getting shot by another
  participant's arrow (Dodgebolt) or being the last active participant
  (all three) auto-declares a winner. `forcewin` is there for anything your
  own arena rules need to decide manually.
- `/boxevents start random` triggers a genuinely random event right now
  (same selection logic the scheduler uses) - handy for testing variety
  without waiting for the timer. Note: only events with their location
  fully configured (or KOTH/Jackpot, which don't need one) are eligible, so
  if only one event is set up so far, that's the only one that can be
  picked - configure more in the GUI for real variety.
