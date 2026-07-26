package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Resolves the configured End Poem music and owns its screen-specific lifecycle.
 */
public final class EndPoemBackgroundMusic {
    private static final Music CREDITS = immediateMusic(SoundEvents.MUSIC_CREDITS);
    private static final Music END = immediateMusic(SoundEvents.MUSIC_END);
    private static final Music DRAGON = immediateMusic(SoundEvents.MUSIC_DRAGON);
    private static final Music MENU = immediateMusic(SoundEvents.MUSIC_MENU);

    private EndPoemBackgroundMusic() {
    }

    public static Music getConfiguredMusic() {
        return switch (EndpoemConfigManager.get().backgroundMusic) {
            case EndpoemConfig.BACKGROUND_MUSIC_END -> END;
            case EndpoemConfig.BACKGROUND_MUSIC_DRAGON -> DRAGON;
            case EndpoemConfig.BACKGROUND_MUSIC_MENU -> MENU;
            // A non-null screen track keeps the End Poem music volume at full strength.
            // CustomEndPoemMusic suppresses this sentinel before it can be played.
            case EndpoemConfig.BACKGROUND_MUSIC_CUSTOM -> CREDITS;
            default -> CREDITS;
        };
    }

    public static void stopForEndPoem() {
        Music music = getConfiguredMusic();
        if (music != null) {
            Minecraft.getInstance().getMusicManager().stopPlaying(music);
        }
    }

    private static Music immediateMusic(Holder<SoundEvent> sound) {
        return new Music(sound, 0, 0, true);
    }
}
