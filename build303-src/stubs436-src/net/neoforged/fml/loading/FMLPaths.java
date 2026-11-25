package net.neoforged.fml.loading;

import java.nio.file.Path;

/** Compile-time mirror (Build 436): already invoked as CONFIGDIR.get() in the base jar. */
public enum FMLPaths {
    CONFIGDIR,
    GAMEDIR;

    public Path get() {
        return null;
    }
}
