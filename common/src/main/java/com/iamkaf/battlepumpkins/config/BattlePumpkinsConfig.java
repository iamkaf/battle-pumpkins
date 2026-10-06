package com.iamkaf.battlepumpkins.config;

import com.iamkaf.battlepumpkins.Constants;
import com.iamkaf.battlepumpkins.spawn.HalloweenEvent;
import com.iamkaf.konfig.api.v1.ConfigBuilder;
import com.iamkaf.konfig.api.v1.ConfigHandle;
import com.iamkaf.konfig.api.v1.ConfigScope;
import com.iamkaf.konfig.api.v1.ConfigValue;
import com.iamkaf.konfig.api.v1.ImageOptions;
import com.iamkaf.konfig.api.v1.InfoPanelBuilder;
import com.iamkaf.konfig.api.v1.Konfig;
import com.iamkaf.konfig.api.v1.SyncMode;
import net.minecraft.resources.Identifier;

import java.util.Locale;
import java.util.Optional;

/**
 * Server settings for wild spawns and the Halloween events. Every value carries an info panel with an in-game picture
 * of what it changes.
 */
public final class BattlePumpkinsConfig {
    private static final String NOT_FORCED = "off";
    private static final ImageOptions PICTURE = ImageOptions.builder()
            .size(256, 144)
            .padding(0)
            .align(ImageOptions.Align.CENTER)
            .captionPosition(ImageOptions.CaptionPosition.NONE)
            .build();

    public static final ConfigHandle HANDLE;
    public static final ConfigValue<Boolean> OFF_SEASON_SPAWNS;
    public static final ConfigValue<Boolean> HALLOWEEN_EVENTS;
    private static final ConfigValue<String> FORCE_EVENT;

    static {
        ConfigBuilder builder = Konfig.builder(Constants.MOD_ID, "server")
                .scope(ConfigScope.SERVER)
                .syncMode(SyncMode.LOGIN_AND_RELOAD)
                .comment("Battle Pumpkins server settings.")
                .info(info -> info
                        .headerKey(key("info.title"))
                        .image(picture("showcase"), PICTURE)
                        .inlineTextKey(key("info.body"))
                        .inlineTextKey(key("info.hint")));

        builder.push("spawning");
        builder.categoryInfo(info -> picturePanel(info, "spawning", "spawning"));
        OFF_SEASON_SPAWNS = builder.bool("off_season_spawns", true)
                .comment("Wild Battle Pumpkins spawn rarely at night outside Halloween week.")
                .info(info -> picturePanel(info, "off_season_spawns", "off_season"))
                .build();
        HALLOWEEN_EVENTS = builder.bool("halloween_events", true)
                .comment("More wild pumpkins during Halloween week (October 25 to 30) and Lantern Night on October 31, by the server's clock.")
                .info(info -> picturePanel(info, "halloween_events", "halloween_week"))
                .build();
        FORCE_EVENT = builder.dropdown("force_event", NOT_FORCED, options -> options
                        .option(NOT_FORCED, option -> option
                                .labelKey(key("option.force_event.off"))
                                .info(info -> picturePanel(info, "force_event.off", "off_season")))
                        .option(optionValue(HalloweenEvent.WEEK), option -> option
                                .labelKey(key("option.force_event.week"))
                                .info(info -> picturePanel(info, "force_event.week", "halloween_week")))
                        .option(optionValue(HalloweenEvent.LANTERN_NIGHT), option -> option
                                .labelKey(key("option.force_event.lantern_night"))
                                .info(info -> picturePanel(info, "force_event.lantern_night", "lantern_night"))))
                .comment("Run Halloween week or Lantern Night regardless of the date: off, week, or lantern_night.")
                .info(info -> picturePanel(info, "force_event", "lantern_night"))
                .build();
        builder.pop();

        HANDLE = builder.build();
    }

    private BattlePumpkinsConfig() {
    }

    public static void init() {
    }

    public static Optional<HalloweenEvent> forcedEvent() {
        String forced = FORCE_EVENT.get();
        for (HalloweenEvent event : HalloweenEvent.values()) {
            if (event != HalloweenEvent.OFF_SEASON && optionValue(event).equals(forced)) {
                return Optional.of(event);
            }
        }
        return Optional.empty();
    }

    private static String optionValue(HalloweenEvent event) {
        return event.name().toLowerCase(Locale.ROOT);
    }

    private static void picturePanel(InfoPanelBuilder info, String name, String picture) {
        info.headerKey(key("option." + name))
                .image(picture(picture), PICTURE)
                .inlineTextKey(key("option." + name + ".info"));
    }

    private static Identifier picture(String name) {
        return Constants.id("gui/config/" + name);
    }

    private static String key(String path) {
        return Constants.MOD_ID + ".config." + path;
    }
}
