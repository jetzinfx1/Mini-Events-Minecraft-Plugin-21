package com.boxpvp.events.events;

/**
 * The mechanic behind an event definition.
 * CUSTOM events (added by admins through the GUI) just get the generic
 * teleport + countdown + starter-items behaviour with no special logic.
 */
public enum EventType {
    PARKOUR,
    KOTH,
    DODGEBOLT,
    STRAFE,
    PVP_TOURNAMENT,
    DODGEBALL,
    VOLLEY_CHARGE,
    JACKPOT,
    CUSTOM
}
