package dev.settledlands.audio;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class AudioStorage {
    private static Path musicDir;
    private static Path soundsDir;
    private static boolean initialized;

    private AudioStorage() {}

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;
        Path baseDir = null;
        try {
            baseDir = net.neoforged.fml.loading.FMLPaths.GAMEDIR.get();
        } catch (Throwable ignored) {}
        if (baseDir == null) {
            baseDir = Path.of(".");
        }
        musicDir = baseDir.resolve("settledlands/music");
        soundsDir = baseDir.resolve("settledlands/sounds");
        try {
            Files.createDirectories(musicDir);
            Files.createDirectories(soundsDir);

            Path musicReadme = musicDir.resolve("README.txt");
            if (!Files.exists(musicReadme)) {
                String musicText = """
                    SettledLands Music (.minecraft/settledlands/music/)
                    ===================================================
                    Поместите сюда фоновую музыку (.mp3, .ogg, .wav).
                    Файлы из этой папки ЗАГЛУШАЮТ ванильную музыку Minecraft при воспроизведении!

                    Команда воспроизведения:
                      /play <имя_файла> [радиус] [громкость: 0.1-1.0] [loop: true/false] [fade_sec: 1-5] [pitch]
                      /play stop [fade_sec]

                    Примеры:
                      /play tavern.mp3 50 1.0 true 2.0
                      /play epic_battle 0 0.8 false 1.0
                      /play stop 2.0
                    """;
                Files.writeString(musicReadme, musicText, StandardCharsets.UTF_8);
            }

            Path soundsReadme = soundsDir.resolve("README.txt");
            if (!Files.exists(soundsReadme)) {
                String soundsText = """
                    SettledLands Sound Effects (.minecraft/settledlands/sounds/)
                    ============================================================
                    Поместите сюда звуковые эффекты и фоновые звуки (.mp3, .ogg, .wav).
                    Звуки из этой папки НЕ ЗАГЛУШАЮТ играющую музыку и воспроизводятся параллельно!

                    Примеры:
                      /play click.wav
                      /play door_open 15 0.9 false 0.0 1.0
                    """;
                Files.writeString(soundsReadme, soundsText, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {}
    }

    public static boolean isMusicFile(File file) {
        if (file == null) return false;
        init();
        if (musicDir == null) return false;
        try {
            Path filePath = file.toPath().toAbsolutePath().normalize();
            Path mDir = musicDir.toAbsolutePath().normalize();
            return filePath.startsWith(mDir);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static File findAudioFile(String name) {
        init();
        if (name == null || name.isBlank()) return null;
        String cleanName = name.replace('\\', '/').trim();
        String[] extensions = {"", ".ogg", ".wav", ".mp3"};

        if (cleanName.startsWith("music/")) {
            String sub = cleanName.substring("music/".length());
            return findInDir(musicDir, sub, extensions);
        } else if (cleanName.startsWith("sounds/")) {
            String sub = cleanName.substring("sounds/".length());
            return findInDir(soundsDir, sub, extensions);
        }

        // Поиск сначала в папке музыки, затем в папке звуков
        File musicFile = findInDir(musicDir, cleanName, extensions);
        if (musicFile != null) return musicFile;

        return findInDir(soundsDir, cleanName, extensions);
    }

    private static File findInDir(Path dir, String name, String[] extensions) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        for (String ext : extensions) {
            Path candidate = dir.resolve(name + ext);
            if (Files.isRegularFile(candidate)) {
                return candidate.toFile();
            }
        }
        return null;
    }

    public static List<String> listAvailableFiles() {
        init();
        Set<String> set = new HashSet<>();
        collectFromDir(set, musicDir, "");
        collectFromDir(set, soundsDir, "");
        collectFromDir(set, musicDir, "music/");
        collectFromDir(set, soundsDir, "sounds/");
        List<String> list = new ArrayList<>(set);
        Collections.sort(list);
        return list;
    }

    private static void collectFromDir(Set<String> set, Path dir, String prefix) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String fn = p.getFileName().toString();
                if (!fn.equalsIgnoreCase("README.txt") && (fn.endsWith(".ogg") || fn.endsWith(".wav") || fn.endsWith(".mp3"))) {
                    set.add(prefix + fn);
                }
            });
        } catch (Throwable ignored) {}
    }
}
