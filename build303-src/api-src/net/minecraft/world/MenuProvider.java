package net.minecraft.world;

/**
 * Build 397: compile-time mirror of the thing a container block hands the game so it can open its
 * window - a chest's own menu, without this mod knowing a single class of it.
 *
 * <p>Two uses, both of them the game's own: {@code BlockState#getMenuProvider(Level, BlockPos)} gives the
 * provider of the block at a position, and {@code ServerPlayer#openMenu(MenuProvider)} opens it. Both
 * descriptors are in the base jar's constant pool
 * ({@code BlockState#getMenuProvider:(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/MenuProvider;}
 * and {@code ServerPlayer#openMenu:(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;}), so the
 * shape is proven by a jar that runs in game - the lesson of builds 373 and 381.</p>
 */
public interface MenuProvider {
}
