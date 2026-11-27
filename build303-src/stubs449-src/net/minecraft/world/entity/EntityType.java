package net.minecraft.world.entity;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Compile-time mirror (Build 449): extends Build 446 {@code EntityType} with {@link #getCategory()}
 * already called by the base JAR.
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

    public MobCategory getCategory() {
        return MobCategory.MISC;
    }
}
