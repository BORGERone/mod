package dev.settledlands.audio;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ClientAudioService {
    private static volatile AudioStreamPlayer activeMusicPlayer = null;
    private static final Map<Long, AudioStreamPlayer> ACTIVE_SOUNDS = new ConcurrentHashMap<>();
    private static final AtomicLong SOUND_ID_GEN = new AtomicLong(0);
    private static volatile long lastVanillaMuteCheck = 0;

    private ClientAudioService() {}

    public static void handlePacket(PlayAudioPayload payload) {
        if (payload.stop()) {
            stopAll(payload.fadeSec());
            return;
        }
        File file = AudioStorage.findAudioFile(payload.file());
        if (file == null || !file.exists()) {
            return;
        }

        boolean isMusic = AudioStorage.isMusicFile(file);
        Vec3 origin = new Vec3(payload.originX(), payload.originY(), payload.originZ());

        if (isMusic) {
            // МУЗЫКА:
            // 1. Останавливаем предыдущую кастомную музыку ровно с тем fadeSec, который указан в команде (0.0 = мгновенно без затухания)
            stopMusic(payload.fadeSec());

            // 2. Немедленно глушим ванильную фоновую музыку Minecraft
            stopVanillaMusic();

            // 3. Запускаем новый музыкальный поток
            AudioStreamPlayer player = new AudioStreamPlayer(
                file, origin, payload.radius(), payload.volume(),
                payload.loop(), payload.fadeSec(), payload.pitch(),
                true, 0L
            );
            activeMusicPlayer = player;
            player.start();
        } else {
            // ЗВУКОВОЙ ЭФФЕКТ:
            // Звуки из папки sounds не заглушают текущую музыку (ни ванильную, ни кастомную)
            // Воспроизводятся параллельно
            long soundId = SOUND_ID_GEN.incrementAndGet();
            AudioStreamPlayer player = new AudioStreamPlayer(
                file, origin, payload.radius(), payload.volume(),
                payload.loop(), payload.fadeSec(), payload.pitch(),
                false, soundId
            );
            ACTIVE_SOUNDS.put(soundId, player);
            player.start();
        }
    }

    public static void stopMusic(float fadeSec) {
        AudioStreamPlayer music = activeMusicPlayer;
        if (music != null) {
            music.stopPlaying(fadeSec);
            activeMusicPlayer = null;
        }
    }

    public static void stopAll(float fadeSec) {
        stopMusic(fadeSec);
        for (AudioStreamPlayer player : ACTIVE_SOUNDS.values()) {
            player.stopPlaying(fadeSec);
        }
        ACTIVE_SOUNDS.clear();
    }

    public static void onPlayerFinished(AudioStreamPlayer player) {
        if (player == null) return;
        if (player == activeMusicPlayer) {
            activeMusicPlayer = null;
        }
        if (player.getSoundId() != 0) {
            ACTIVE_SOUNDS.remove(player.getSoundId());
        }
    }

    public static boolean isCustomMusicPlaying() {
        AudioStreamPlayer music = activeMusicPlayer;
        if (music == null || !music.isActivelyPlaying()) {
            return false;
        }
        if (music.getRadius() > 0.0f) {
            return music.computeEffectiveVolume() > 0.001f;
        }
        return true;
    }

    public static void stopVanillaMusic() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                Runnable action = () -> {
                    try {
                        if (mc.getMusicManager() != null) {
                            mc.getMusicManager().stopPlaying();
                        }
                        if (mc.getSoundManager() != null) {
                            mc.getSoundManager().stop(null, SoundSource.MUSIC);
                        }
                    } catch (Throwable ignored) {}
                };
                if (mc.isSameThread()) {
                    action.run();
                } else {
                    mc.execute(action);
                }
            }
        } catch (Throwable ignored) {}
    }

    public static void ensureVanillaMusicSuppressed() {
        long now = System.currentTimeMillis();
        if (now - lastVanillaMuteCheck > 1000) {
            lastVanillaMuteCheck = now;
            stopVanillaMusic();
        }
    }

    public static Vec3 getClientPlayerPos() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                return mc.player.position();
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static int activeTrackCount() {
        int count = (activeMusicPlayer != null && activeMusicPlayer.isActivelyPlaying()) ? 1 : 0;
        count += ACTIVE_SOUNDS.size();
        return count;
    }

    public static AudioStreamPlayer getActiveMusicPlayer() {
        return activeMusicPlayer;
    }
}
