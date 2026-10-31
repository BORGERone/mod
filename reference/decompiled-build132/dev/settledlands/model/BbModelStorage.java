package dev.settledlands.model;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BbModelStorage {
    private static Path modelsDir;
    private static boolean initialized;

    private BbModelStorage() {}

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
        modelsDir = baseDir.resolve("settledlands/models");
        try {
            Files.createDirectories(modelsDir);
            Path readme = modelsDir.resolve("README.txt");
            if (!Files.exists(readme)) {
                String text = """
                    SettledLands Custom Blockbench Models (.minecraft/settledlands/models/)
                    ========================================================================
                    Поместите сюда ваши файлы 3D-моделей Blockbench (.bbmodel).

                    Поддерживаемые возможности:
                      1. Полная 3D-геометрия (кости, группы, кубы, размеры, пивоты).
                      2. Встроенные текстуры (base64 PNG внутри .bbmodel) или одноименный .png рядом.
                      3. Анимации ключевых кадров:
                         - "idle" (покой, дыхание);
                         - "walk" (ходьба при движении или следовании за игроком);
                         - "talk" (разговор при открытом окне диалога).

                    Выбор модели осуществляется в окне настройки NPC прямо из выпадающего списка!
                    """;
                Files.writeString(readme, text, StandardCharsets.UTF_8);
            }
        } catch (Throwable ignored) {}
    }

    public static Path getModelsDir() {
        init();
        return modelsDir;
    }

    public static File findModelFile(String name) {
        init();
        if (name == null || name.isBlank() || modelsDir == null) return null;
        String cleanName = name.trim();
        Path direct = modelsDir.resolve(cleanName);
        if (Files.isRegularFile(direct)) return direct.toFile();

        if (!cleanName.toLowerCase().endsWith(".bbmodel")) {
            Path withExt = modelsDir.resolve(cleanName + ".bbmodel");
            if (Files.isRegularFile(withExt)) return withExt.toFile();
        }
        return null;
    }

    public static List<String> listAvailableModels() {
        init();
        List<String> list = new ArrayList<>();
        if (modelsDir == null || !Files.isDirectory(modelsDir)) return list;
        try (var stream = Files.list(modelsDir)) {
            stream.filter(Files::isRegularFile).forEach(p -> {
                String fn = p.getFileName().toString();
                if (fn.toLowerCase().endsWith(".bbmodel")) {
                    list.add(fn);
                }
            });
        } catch (Throwable ignored) {}
        Collections.sort(list, String.CASE_INSENSITIVE_ORDER);
        return list;
    }
}
