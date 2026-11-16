package net.neoforged.neoforge.common.extensions;

import net.minecraft.nbt.CompoundTag;

/**
 * Build 420: {@code IEntityExtension#getPersistentData} - the one call this mod makes on an entity's own
 * NBT.
 *
 * <p>The base jar already makes exactly this call from seventeen classes ({@code RevivalNpcHelper},
 * {@code PossessionSystem}, {@code PlayerCorpsePossession} and the rest); in NeoForge 1.21.1 the vanilla
 * {@code Entity} has no such method, and the cast to this interface is how the jar reaches it. The anchor
 * keeps its list in that same bag, so a copy tool that carries an entity's NBT carries the anchor.</p>
 */
public interface IEntityExtension {

    /** The entity's own NBT, writable - the bag every NeoForge entity has. */
    CompoundTag getPersistentData();
}
