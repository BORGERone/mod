package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Compile-time shape of the player model Build 338 reads.
 *
 * <p>Only the two arms are declared: Punchy! hands its own player model to the arm renderer, and the
 * bridge reads which arms are visible and copies their pose onto the NPC arm. A stub is needed
 * because the profile this project compiles against does not ship the client model package.
 */
public class PlayerModel<T> {
    public ModelPart rightArm;
    public ModelPart leftArm;
}
