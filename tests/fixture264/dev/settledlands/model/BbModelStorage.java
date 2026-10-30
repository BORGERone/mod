package dev.settledlands.model;

import java.nio.file.Path;

/** Test-only stand-in; the in-game loader resolves the same method from the mod. */
public final class BbModelStorage {
    private BbModelStorage() {}

    public static Path getModelsDir() {
        return Path.of(System.getProperty("settledlands.test.models"));
    }
}
