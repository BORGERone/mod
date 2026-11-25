package net.minecraft.world.item.component;

import java.util.Collections;
import java.util.List;
import net.minecraft.server.network.Filterable;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public final class WritableBookContent {
    private final List<Filterable<String>> pages;

    public WritableBookContent(List<Filterable<String>> pages) {
        this.pages = pages == null ? Collections.emptyList() : pages;
    }

    public List<Filterable<String>> pages() {
        return pages;
    }
}
