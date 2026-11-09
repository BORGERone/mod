package net.minecraft.world.level.pathfinder;

/**
 * Compile-time mirror (Build 403) of the path finder's node maker, narrowed to the door question.
 *
 * <p>{@code setCanOpenDoors(boolean)} is public on {@code NodeEvaluator} in 1.21 (the yarn and NeoForge
 * documentation both list it next to {@code canPassDoors}, {@code canFloat} and
 * {@code canWalkOverFences}); a closed wooden door is walkable for a mob only when this flag is set.</p>
 */
public class NodeEvaluator {
    public void setCanOpenDoors(boolean canOpenDoors) {}
    public boolean canOpenDoors() { return false; }

    /**
     * Build 414: {@code setCanPassDoors(boolean)} and {@code canPassDoors()} live on {@code NodeEvaluator}
     * itself in Minecraft 1.21.1 (not on {@code PathNavigation}, where build 407's call threw
     * {@code NoSuchMethodError} at runtime in the author's 413 log).
     */
    public void setCanPassDoors(boolean canPassDoors) {}
    public boolean canPassDoors() { return false; }
}
