package dev.settledlands.model;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BbModelManager {
    private static final Map<String, BbModel> CACHE = new ConcurrentHashMap<>();

    private BbModelManager() {}

    public static void init() {
        BbModelStorage.init();
    }

    public static BbModel getModel(String name) {
        if (name == null || name.isBlank() || "default".equalsIgnoreCase(name)) {
            return null;
        }
        String clean = name.trim();
        BbModel cached = CACHE.get(clean);
        if (cached != null) return cached;

        File file = BbModelStorage.findModelFile(clean);
        if (file != null && file.exists()) {
            try {
                BbModel model = BbModelParser.parse(file);
                CACHE.put(clean, model);
                CACHE.put(file.getName(), model);
                return model;
            } catch (Throwable t) {
                System.err.println("[SettledLands] Failed to parse model " + file.getName() + ": " + t);
                t.printStackTrace();
            }
        }
        return null;
    }

    public static void clearCache() {
        CACHE.clear();
    }

    public static List<String> listAvailableModels() {
        return BbModelStorage.listAvailableModels();
    }
}
