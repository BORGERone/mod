package net.minecraft.client.model;

import net.minecraft.client.model.geom.ModelPart;

/**
 * Compile-time shape of the humanoid model the armour pass poses (Build 348).
 *
 * <p>The seven part names are vanilla's own: {@code LayerDefinitions} builds the player and both
 * armour meshes from the same {@code head} / {@code hat} / {@code body} / {@code right_arm} /
 * {@code left_arm} / {@code right_leg} / {@code left_leg} tree, which is why an {@code ArmorItem}
 * can be drawn on any humanoid by copying rotations onto this model.</p>
 */
public class HumanoidModel<T> extends EntityModel<T> {
    public ModelPart head;
    public ModelPart hat;
    public ModelPart body;
    public ModelPart rightArm;
    public ModelPart leftArm;
    public ModelPart rightLeg;
    public ModelPart leftLeg;
}
