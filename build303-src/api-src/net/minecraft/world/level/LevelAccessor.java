package net.minecraft.world.level;

/**
 * Compile-time mirror (Build 401) of the interface a block event hands back.
 *
 * <p>{@code BlockEvent#getLevel()} is declared as {@code LevelAccessor} in the game, and this mod's mirrors
 * said {@code Level} for four builds: two handlers compiled against that asked for a descriptor no class has,
 * threw {@code NoSuchMethodError} inside their own {@code try}, and stopped halfway - the chest's break guard
 * and the door's were never reached (see {@code tools/event_getter_check.py}). Nothing here is called: the
 * interface exists so that {@code event.getLevel() instanceof ServerLevel} compiles the way the game does.</p>
 */
public interface LevelAccessor {
}
