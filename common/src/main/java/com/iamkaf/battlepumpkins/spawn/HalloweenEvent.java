package com.iamkaf.battlepumpkins.spawn;

import com.iamkaf.battlepumpkins.config.BattlePumpkinsConfig;

import java.time.Month;
import java.time.MonthDay;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * The part of the Halloween calendar it is now, by the server's clock. Each event sets how often wild pumpkins try
 * to spawn near a player during the night, and how many wild ones may gather near one player.
 */
public enum HalloweenEvent {
    OFF_SEASON(0.005F, 2),
    WEEK(0.05F, 4),
    LANTERN_NIGHT(0.15F, 10);

    private static final MonthDay WEEK_START = MonthDay.of(Month.OCTOBER, 25);
    private static final MonthDay HALLOWEEN = MonthDay.of(Month.OCTOBER, 31);

    /** Chance per spawn attempt; {@link PumpkinSpawner} makes one attempt per player every 10 seconds of night. */
    private final float spawnChance;
    private final int nearbyCap;

    HalloweenEvent(float spawnChance, int nearbyCap) {
        this.spawnChance = spawnChance;
        this.nearbyCap = nearbyCap;
    }

    public float spawnChance() {
        return spawnChance;
    }

    public int nearbyCap() {
        return nearbyCap;
    }

    /** The current event, or empty when wild pumpkins should not spawn at all. */
    public static Optional<HalloweenEvent> current() {
        Optional<HalloweenEvent> forced = BattlePumpkinsConfig.forcedEvent();
        if (forced.isPresent()) {
            return forced;
        }
        HalloweenEvent event = BattlePumpkinsConfig.HALLOWEEN_EVENTS.get() ? onDay(MonthDay.from(ZonedDateTime.now())) : OFF_SEASON;
        if (event == OFF_SEASON && !BattlePumpkinsConfig.OFF_SEASON_SPAWNS.get()) {
            return Optional.empty();
        }
        return Optional.of(event);
    }

    static HalloweenEvent onDay(MonthDay day) {
        if (day.equals(HALLOWEEN)) {
            return LANTERN_NIGHT;
        }
        return !day.isBefore(WEEK_START) && day.isBefore(HALLOWEEN) ? WEEK : OFF_SEASON;
    }
}
