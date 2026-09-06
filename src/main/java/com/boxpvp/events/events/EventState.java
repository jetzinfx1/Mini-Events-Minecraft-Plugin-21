package com.boxpvp.events.events;

public enum EventState {
    /** Countdown running, players may still click to join */
    WAITING,
    /** Countdown finished, players teleported in, event in progress */
    RUNNING,
    /** Event finished, about to be cleaned up */
    ENDED
}
