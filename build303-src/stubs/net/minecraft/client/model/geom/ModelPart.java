package net.minecraft.client.model.geom;

/**
 * Compile-time shape of the one game class Build 315 touches.
 *
 * <p>Only the nine transform fields are declared: the real class carries the same public floats,
 * and the base jar already writes every one of them from {@code NpcMotionBridge256.Pose#set}, so
 * nothing here invents an API. A stub is needed because the profile this project compiles against
 * does not ship the client model package.</p>
 */
public class ModelPart {
    public float x;
    public float y;
    public float z;
    public float xRot;
    public float yRot;
    public float zRot;
    public float xScale;
    public float yScale;
    public float zScale;
}
