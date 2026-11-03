package dev.settledlands.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 348 - the armour an entity wears, drawn on the body the mod renders.
 *
 * <p>The mod draws its own body ({@code BbModel}) instead of the vanilla player model, so nothing ever
 * asked the game for armour: a chestplate in the chest slot changed nothing on screen. This pass draws
 * it, in the model space the body is already drawn in.</p>
 *
 * <h2>Why the vanilla armour model and not our own boxes</h2>
 * <p>Armour textures are painted for the vanilla humanoid UV layout, so the only thing that can wear
 * them correctly is the vanilla humanoid model. {@code PLAYER_OUTER_ARMOR} and
 * {@code PLAYER_INNER_ARMOR} are baked once and re-used for every entity; the body's own bone rotations
 * are copied onto that model right before the draw, which is what makes the plate follow the walk
 * cycle, the arm swing and the head turn of the {@code BbModel}.</p>
 *
 * <h2>Space</h2>
 * <p>The call has to land where the body's root part was just drawn: the harness there is the game's
 * own entity model space ({@code scale(-1,-1,1)} + {@code translate(0,-1.501,0)}, exactly what
 * {@code LivingEntityRenderer} does), which is the space the armour model was built for. No extra
 * transform is applied, so a plate cannot drift away from the body it belongs to.</p>
 *
 * <h2>Never a crash</h2>
 * <p>Armour is a visual extra, so this pass is not allowed to take the client down: a wrong or missing
 * member of the game's model API is caught, reported once, and the pass switches itself off for the
 * session. Build 348 did not do that and died on the first frame - {@code EntityModelSet#bakeLayer}
 * returns the layer's root part, not a model, and the {@code NoSuchMethodError} went straight out of
 * the render pass.</p>
 *
 * <h2>Cost</h2>
 * <p>No allocation per frame: the two baked models are static, the bone map is a {@link WeakHashMap}
 * keyed by model instance, and a frame with no armour on the entity returns after four empty checks.
 * Only a real equipment change allocates - and that happens server side.</p>
 */
public final class BodyArmor348 {
    /** Draw order and lookup order at the same time: the four armour slots, as vanilla walks them. */
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** One baked armour model per side; {@code null} until the client's model set is reachable. */
    private static HumanoidModel<?> outerArmor;
    private static HumanoidModel<?> innerArmor;
    /** Attempts left before the pass stops trying to bake; an unready model set is not a failure. */
    private static int bakeAttempts = 8;
    /** Set by the first failure: from then on the pass costs one boolean test per frame. */
    private static boolean disabled;
    /** Says the one line once, whatever took the pass down first. */
    private static boolean reported;
    private static final Logger LOG = LoggerFactory.getLogger("SettledLands/Armor348");

    /** BbModel instance -> the bones the armour model copies its rotations from. */
    private static final Map<BbModel, Rig> RIGS = new WeakHashMap<>();

    private BodyArmor348() {
    }

    // ------------------------------------------------------------------ entry points

    /**
     * Draws every armour piece the entity wears on the body that was just rendered.
     *
     * @param model  the body model whose bones are posed for this frame
     * @param entity the entity being drawn (player body or NPC)
     * @param pose   the pose stack, still inside the body's model space
     * @param buffer the buffer source of the current render pass
     * @param light  packed light of the entity
     */
    public static void draw(BbModel model, LivingEntity entity, PoseStack pose, MultiBufferSource buffer,
                            int light) {
        if (disabled || model == null || entity == null || pose == null || buffer == null) {
            return;
        }
        try {
            drawUnguarded(model, entity, pose, buffer, light);
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    private static void drawUnguarded(BbModel model, LivingEntity entity, PoseStack pose,
                                      MultiBufferSource buffer, int light) {
        HumanoidModel<?> outer = outer();
        HumanoidModel<?> inner = inner();
        if (outer == null || inner == null) {
            return;
        }
        int worn = 0;
        for (int i = 0; i < SLOTS.length; i++) {
            ItemStack stack = entity.getItemBySlot(SLOTS[i]);
            if (stack != null && !stack.isEmpty()) {
                worn++;
            }
        }
        if (worn == 0) {
            return;
        }
        Rig rig = rig(model);
        if (rig == null) {
            return;
        }
        for (int i = 0; i < SLOTS.length; i++) {
            EquipmentSlot slot = SLOTS[i];
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            if (!(stack.getItem() instanceof ArmorItem)) {
                continue;
            }
            ArmorItem item = (ArmorItem) stack.getItem();
            if (item.getEquipmentSlot() != slot) {
                continue;
            }
            drawPiece(slot == EquipmentSlot.LEGS ? inner : outer, rig, slot, stack, item, pose, buffer, light);
        }
    }

    // ------------------------------------------------------------------ the pieces

    private static void drawPiece(HumanoidModel<?> model, Rig rig, EquipmentSlot slot, ItemStack stack,
                                  ArmorItem item, PoseStack pose, MultiBufferSource buffer, int light) {
        List<ArmorMaterial.Layer> layers = layersOf(item);
        if (layers == null) {
            return;
        }
        boolean inner = slot == EquipmentSlot.LEGS;
        int dye = -1;
        int size = layers.size();
        for (int i = 0; i < size; i++) {
            ArmorMaterial.Layer layer = layers.get(i);
            if (layer == null) {
                continue;
            }
            boolean dyeable = layer.dyeable();
            if (dyeable && dye == -1) {
                dye = DyedItemColor.getOrDefault(stack, DyedItemColor.LEATHER_COLOR) | 0xFF000000;
            }
            ResourceLocation texture = layer.texture(inner);
            if (texture == null) {
                continue;
            }
            drawLayer(model, rig, slot, buffer.getBuffer(RenderType.armorCutoutNoCull(texture)), pose, light,
                    dyeable ? dye : -1);
        }
        if (stack.hasFoil()) {
            drawLayer(model, rig, slot, buffer.getBuffer(RenderType.armorEntityGlint()), pose, light, -1);
        }
    }

    private static List<ArmorMaterial.Layer> layersOf(ArmorItem item) {
        if (item.getMaterial() == null) {
            return null;
        }
        ArmorMaterial material = item.getMaterial().value();
        return material == null ? null : material.layers();
    }

    /**
     * Poses the armour model like the body and draws it: the same visibility rule vanilla's
     * {@code HumanoidArmorLayer} uses per slot, the same {@code usesInnerModel} split (leggings only).
     */
    private static void drawLayer(HumanoidModel<?> model, Rig rig, EquipmentSlot slot, VertexConsumer vertex,
                                  PoseStack pose, int light, int tint) {
        pose(model, rig, slot);
        model.renderToBuffer(pose, vertex, light, OverlayTexture.NO_OVERLAY, tint);
    }

    // ------------------------------------------------------------------ posing

    /**
     * Copies the body's bone rotations onto the armour model and shows only the parts of this slot.
     *
     * <p>The body's torso bone is {@code waist} under an empty {@code body} bone, while the armour
     * model's {@code body} is the parent of both arms and both legs - the game's own layout. So the
     * torso takes the sum of the two body bones and the limbs keep their own rotation: with the same
     * hierarchy on both sides the composition comes out identical, not approximated. The head is a
     * child of {@code waist} in the body but a root part in the armour model, so it takes the torso
     * rotation as well.</p>
     */
    private static void pose(HumanoidModel<?> model, Rig rig, EquipmentSlot slot) {
        ModelPart head = model.head;
        ModelPart hat = model.hat;
        ModelPart body = model.body;
        ModelPart rightArm = model.rightArm;
        ModelPart leftArm = model.leftArm;
        ModelPart rightLeg = model.rightLeg;
        ModelPart leftLeg = model.leftLeg;

        boolean showHead = slot == EquipmentSlot.HEAD;
        boolean showBody = slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS;
        boolean showArms = slot == EquipmentSlot.CHEST;
        boolean showLegs = slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET;
        if (head != null) {
            head.visible = showHead;
        }
        if (hat != null) {
            hat.visible = showHead;
        }
        if (body != null) {
            body.visible = showBody;
        }
        if (rightArm != null) {
            rightArm.visible = showArms;
        }
        if (leftArm != null) {
            leftArm.visible = showArms;
        }
        if (rightLeg != null) {
            rightLeg.visible = showLegs;
        }
        if (leftLeg != null) {
            leftLeg.visible = showLegs;
        }

        float torsoX = rotationX(rig.torso) + rotationX(rig.upper);
        float torsoY = rotationY(rig.torso) + rotationY(rig.upper);
        float torsoZ = rotationZ(rig.torso) + rotationZ(rig.upper);
        if (body != null) {
            body.xRot = torsoX;
            body.yRot = torsoY;
            body.zRot = torsoZ;
        }
        copyRotation(rightArm, rig.rightArm);
        copyRotation(leftArm, rig.leftArm);
        copyRotation(rightLeg, rig.rightLeg);
        copyRotation(leftLeg, rig.leftLeg);
        if (head != null) {
            head.xRot = torsoX + rotationX(rig.head);
            head.yRot = torsoY + rotationY(rig.head);
            head.zRot = torsoZ + rotationZ(rig.head);
            if (hat != null) {
                hat.xRot = head.xRot;
                hat.yRot = head.yRot;
                hat.zRot = head.zRot;
            }
        }
    }

    private static void copyRotation(ModelPart target, ModelPart source) {
        if (target == null) {
            return;
        }
        target.xRot = rotationX(source);
        target.yRot = rotationY(source);
        target.zRot = rotationZ(source);
    }

    private static float rotationX(ModelPart part) {
        return part == null ? 0.0F : part.xRot;
    }

    private static float rotationY(ModelPart part) {
        return part == null ? 0.0F : part.yRot;
    }

    private static float rotationZ(ModelPart part) {
        return part == null ? 0.0F : part.zRot;
    }

    // ------------------------------------------------------------------ bones

    /** The six bones of a body model the armour follows. Any of them may be missing in a given model. */
    private static final class Rig {
        ModelPart upper;
        ModelPart torso;
        ModelPart head;
        ModelPart rightArm;
        ModelPart leftArm;
        ModelPart rightLeg;
        ModelPart leftLeg;
    }

    private static Rig rig(BbModel model) {
        Rig rig = RIGS.get(model);
        if (rig != null) {
            return rig;
        }
        rig = new Rig();
        rig.upper = model.getPart("body");
        rig.torso = firstPart(model, "waist", "chest", "torso", "body");
        rig.head = model.getPart("head");
        rig.rightArm = firstPart(model, "rightarm", "right_arm", "armright");
        rig.leftArm = firstPart(model, "leftarm", "left_arm", "armleft");
        rig.rightLeg = firstPart(model, "rightleg", "right_leg", "legright");
        rig.leftLeg = firstPart(model, "leftleg", "left_leg", "legleft");
        if (rig.torso == null && rig.head == null && rig.rightArm == null && rig.leftArm == null
                && rig.rightLeg == null && rig.leftLeg == null) {
            return null;
        }
        RIGS.put(model, rig);
        return rig;
    }

    private static ModelPart firstPart(BbModel model, String... names) {
        for (int i = 0; i < names.length; i++) {
            ModelPart part = model.getPart(names[i]);
            if (part != null) {
                return part;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ baked models

    private static HumanoidModel<?> outer() {
        if (outerArmor == null && bakeAttempts > 0) {
            outerArmor = bake(false);
        }
        return outerArmor;
    }

    private static HumanoidModel<?> inner() {
        if (innerArmor == null && bakeAttempts > 0) {
            innerArmor = bake(true);
        }
        return innerArmor;
    }

    /**
     * Bakes one of the two player armour layers. The attempt counter is spent only by a call that
     * actually reached the model set, so an early pass (the client still loading) costs nothing while
     * a genuinely missing layer stops being asked for after eight tries instead of every frame.
     */
    private static HumanoidModel<?> bake(boolean inner) {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client == null) {
                return null;
            }
            EntityModelSet models = client.getEntityModels();
            if (models == null) {
                return null;
            }
            bakeAttempts--;
            ModelPart root = models.bakeLayer(inner ? ModelLayers.PLAYER_INNER_ARMOR
                    : ModelLayers.PLAYER_OUTER_ARMOR);
            return root == null ? null : new HumanoidModel<LivingEntity>(root);
        } catch (Throwable failure) {
            fail(failure);
            return null;
        }
    }

    /**
     * Switches the pass off for the session and says so once.
     *
     * <p>One line, with the failure's own {@code toString}: enough to name a wrong API call from the log
     * (that is how 348's {@code NoSuchMethodError} would have been readable instead of fatal) and cheap
     * enough that a broken client keeps rendering bodies.</p>
     */
    private static void fail(Throwable failure) {
        disabled = true;
        bakeAttempts = 0;
        if (!reported) {
            reported = true;
            LOG.warn("armour pass switched off for this session: {}", failure.toString());
        }
    }
}
