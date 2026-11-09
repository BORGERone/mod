package net.minecraft.world.entity.ai.navigation;

import net.minecraft.world.level.pathfinder.NodeEvaluator;
import net.minecraft.world.level.pathfinder.Path;

public class PathNavigation {
    public void stop() {}
    public boolean isDone() { return true; }
    public boolean isInProgress() { return false; }
    public void setSpeedModifier(double speed) {}
    public boolean moveTo(double x, double y, double z, double speed) { return false; }
    public boolean moveTo(double x, double y, double z, int offset, double speed) { return false; }

    /**
     * Build 403: the node maker this navigator asks about doors. Its {@code setCanOpenDoors(true)} is the
     * switch that makes the path finder treat a closed wooden door as a doorway instead of a wall - the
     * switch the mod used to look for on the mob, where this game version no longer keeps it.
     */
    public NodeEvaluator getNodeEvaluator() { return null; }

    /** Build 403: the path this navigator is walking; its nodes are the doors a guide is going through. */
    public Path getPath() { return null; }

    /** Build 403: recompute the current path to the same target - after a door on it was opened. */
    public void recomputePath() {}

    /**
     * Build 404: where this navigator is taking the mob right now ({@code public BlockPos getTargetPos()}).
     * The door pass reads it to open the doors <b>ahead</b>, not only the ones at the mob's feet.
     */
    public net.minecraft.core.BlockPos getTargetPos() { return null; }

    /**
     * Build 407: {@code public void setCanPassDoors(boolean)}. On {@code PathNavigation} in 1.21 it forwards
     * to the same node maker {@link #getNodeEvaluator()} hands out - the mappings list it next to
     * {@code setCanOpenDoors} (m_26443_ / m_148212_ family), and {@code WalkNodeEvaluator} publishes both.
     * With pass-doors on, a wooden door that is <b>standing open</b> is typed {@code DOOR_OPEN} and is
     * simply floor: no opening step, no delay between the opening and the step.
     */
    public void setCanPassDoors(boolean canPassDoors) {}
}
