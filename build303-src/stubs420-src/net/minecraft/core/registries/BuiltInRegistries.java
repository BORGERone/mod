package net.minecraft.core.registries;

import net.minecraft.core.DefaultedRegistry;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

/**
 * Build 420: the two registries the anchor reads - {@code BLOCK} for a block's fingerprint and
 * {@code ENTITY_TYPE} for the marker's own type.
 *
 * <p>Both are typed exactly as the base jar types the registries it already reads
 * ({@code BuiltInRegistries.ITEM}, {@code BuiltInRegistries.ENTITY_TYPE}:
 * {@code Lnet/minecraft/core/DefaultedRegistry;}), so a call through them is the same call the game
 * already answers for items and entity types.</p>
 */
public final class BuiltInRegistries {

    /** Every block of the game, by id. */
    public static final DefaultedRegistry<Block> BLOCK = new DefaultedRegistry<Block>();

    /** Every entity type of the game, by id. */
    public static final DefaultedRegistry<EntityType> ENTITY_TYPE = new DefaultedRegistry<EntityType>();

    private BuiltInRegistries() {
    }
}
