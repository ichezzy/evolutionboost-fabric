package com.ichezzy.evolutionboost.quest;

/**
 * Status einer Quest für einen Spieler.
 */
public enum QuestStatus {
    /**
     * Quest ist noch nicht verfügbar (Prerequisites nicht erfüllt).
     */
    LOCKED,

    /**
     * Quest ist verfügbar aber noch nicht angenommen.
     */
    AVAILABLE,

    /**
     * Quest ist aktiv und wird getrackt.
     */
    ACTIVE,

    /**
     * Alle Objectives erfüllt, aber Rewards noch nicht abgeholt.
     */
    READY_TO_COMPLETE,

    /**
     * Quest ist abgeschlossen und Rewards wurden vergeben.
     */
    COMPLETED;

    public boolean canProgress() {
        return this == ACTIVE;
    }

    public boolean isFinished() {
        return this == COMPLETED;
    }
}
