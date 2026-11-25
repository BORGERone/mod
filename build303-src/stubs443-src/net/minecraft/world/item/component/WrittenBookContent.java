package net.minecraft.world.item.component;

import java.util.Collections;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;

/** Compile-time mirror (Build 443) verified via ForgeJavaDocs-NG 1.21.x-neoforge. */
public final class WrittenBookContent {
    private final Filterable<String> title;
    private final String author;
    private final int generation;
    private final List<Filterable<Component>> pages;
    private final boolean resolved;

    public WrittenBookContent(Filterable<String> title, String author, int generation,
            List<Filterable<Component>> pages, boolean resolved) {
        this.title = title;
        this.author = author == null ? "" : author;
        this.generation = generation;
        this.pages = pages == null ? Collections.emptyList() : pages;
        this.resolved = resolved;
    }

    public Filterable<String> title() {
        return title;
    }

    public String author() {
        return author;
    }

    public int generation() {
        return generation;
    }

    public List<Filterable<Component>> pages() {
        return pages;
    }

    public boolean resolved() {
        return resolved;
    }
}
