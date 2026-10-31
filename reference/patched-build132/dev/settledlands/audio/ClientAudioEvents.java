package dev.settledlands.audio;

import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;

@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class ClientAudioEvents {
    private ClientAudioEvents() {}

    @SubscribeEvent
    public static void onSelectMusic(SelectMusicEvent event) {
        if (ClientAudioService.isCustomMusicPlaying()) {
            event.setMusic(null);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlaySound(PlaySoundEvent event) {
        if (ClientAudioService.isCustomMusicPlaying()) {
            SoundInstance sound = event.getSound();
            if (sound != null && sound.getSource() == SoundSource.MUSIC) {
                event.setSound(null);
            }
        }
    }
}
