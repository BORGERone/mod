package net.minecraft.core.registries;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * Build 420: the two registries the anchor reads - {@code BLOCK} for a block's fingerprint and
 * {@code ENTITY_TYPE} for the two ends of a marker (its type in the blueprint, its type in the world
 * after the copy).
 *
 * <p>{@code NpcItems} in the base jar already reads {@code BuiltInRegistries.ITEM} with
 * {@code getKey(...)}, and {@code WorldEvents} reads {@code ENTITY_TYPE} the same way, so both fields
 * are the game's own and this mirror adds nothing to their shape.</p>
 */
public final class BuiltInRegistries {

    /** Every block of the game, by id. */
    public static final Registry<Block> BLOCK = new Registry<Block>() {};

    /** Every entity type of the game, by id. */
    public static final Registry<EntityType> ENTITY_TYPE = new Registry<EntityType>() {};

    private BuiltInRegistries() {
    }

    /** Kept out of the way of the two fields: the id of a block, for a harness that cannot ask a registry. */
    public static ResourceLocation keyOf(Block block) {
        return null;
    }
}
