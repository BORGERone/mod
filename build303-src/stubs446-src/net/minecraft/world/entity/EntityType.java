package net.minecraft.world.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Compile-time mirror (Build 446): extends {@code EntityType} with {@link #getDescription()}
 * matching base JAR external refs.
 */
public final class EntityType {

    private EntityType() {
    }

    public Entity create(Level level) {
        return null;
    }

    public ResourceLocation id() {
        return null;
    }

    public Component getDescription() {
        return Component.empty();
    }
}
