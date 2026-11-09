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
}
