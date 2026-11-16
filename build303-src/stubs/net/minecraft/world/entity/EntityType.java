package net.minecraft.world.entity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Build 420: an entity type, seen by the anchor in two directions - the id of a type
 * ({@code BuiltInRegistries.ENTITY_TYPE.getKey(type)}) and the type behind an id
 * ({@code BuiltInRegistries.ENTITY_TYPE.get(id)}).
 *
 * <p>The name is the base jar's own: {@code RevivalNpcHelper} already calls
 * {@code EntityType.create(Level)} on a type and reads the entity back, so {@code create} is settled by
 * the jar. Nothing else here is called by this build.</p>
 */
public final class EntityType {

    private EntityType() {
    }

    /** The entity this type builds in a level - null when the type cannot exist there. */
    public Entity create(Level level) {
        return null;
    }

    /** The id of this type, or null. */
    public ResourceLocation id() {
        return null;
    }
}
