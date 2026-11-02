package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.model.BbModel;
import dev.settledlands.model.BbModelManager;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 344: the legs Cinematic Interactions folds at a campfire are the possessed NPC's legs.
 *
 * <p>The campfire scene is the one Cinematic scene with a body: {@code CampfireStation.renderBody}
 * lays out both legs as two segments - hip to knee, knee to ankle - and hands each one to
 * {@code LegMesh.draw}, which builds its own thigh and shin cubes and textures them with
 * {@code LocalPlayer.getSkin()}. A possessed player is drawn as an Ivy rig everywhere else, so the
 * fireplace gave them the player's own bare legs crossed under the NPC's hands.</p>
 *
 * <p>Cinematic's joints are exactly what a leg needs, so nothing here recomputes a pose: the same hip,
 * knee and ankle points place the NPC model's own leg bone, stretched to each segment and turned onto
 * it. The NPC's geometry and texture are used as they are, so the legs match the body that owns them -
 * the thigh and the shin are the one leg bone of the model, placed twice, which is what a knee bend is
 * with a single-bone leg.</p>
 *
 * <p>Cinematic's armour pass, which draws the player's leggings and boots around those same segments,
 * is Cinematic's own public static method and is called through a handle so boots do not vanish. Like
 * the hands of Build 343, that method and the leg guard are reached by name: nothing links Cinematic at
 * compile time, the handle is resolved once, and any failure leaves the scene to Cinematic - a campfire
 * never loses its legs, and never stops the game.</p>
 */
public final class CinematicLegs344 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/CinematicLegs344");

    /** Cinematic's scene-armour class, named as text so it is never needed when the mod is absent. */
    private static final String ARMOUR = "dev.dyslexic.cinematic.client.CampfireArmor";

    /** The scene pass that draws leggings and boots around the leg segments. */
    private static final String ARMOUR_DRAW = "drawInScene";

    /** Cinematic draws no segment shorter than this, and neither do we: the cube would be nothing. */
    private static final double MIN_SEGMENT = 1.0E-4D;

    /** One leg bone of the shipped rigs is 12 model units long, which is this many blocks. */
    private static final float BONE_LENGTH = 0.75F;

    /** The thigh is stretched a little past the knee so the two segments do not part at the joint. */
    private static final float KNEE_OVERLAP = 0.07F;

    /** Bounded report: the first rigs whose legs are drawn, and at most one failure line. */
    private static final int REPORT_LIMIT = 4;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Leg state kept aside per model and side; rebuilt only when the model is reloaded. */
    private static final Map<String, LegCache[]> CACHE = new ConcurrentHashMap<>();

    /** Fields per bone: x, y, z, xRot, yRot, zRot, xScale, yScale, zScale, visible, skipDraw. */
    private static final int FIELDS = 11;

    /** A leg bone hangs down its own +Y; these are the render thread's working vectors. */
    private static final Vector3f DOWN = new Vector3f(0.0F, 1.0F, 0.0F);
    private static final Vector3f ALONG = new Vector3f();
    private static final Quaternionf TURN = new Quaternionf();

    /** Resolved on first use on the render thread, and then never again. */
    private static MethodHandle armourDraw;
    private static boolean armourLooked;

    private CinematicLegs344() {
    }

    /**
     * Draws the possessed NPC's leg where Cinematic is about to draw the player's own, and keeps
     * Cinematic's armour on top of it.
     *
     * @return true when the NPC legs were submitted, in which case the caller must not draw its own
     */
    public static boolean drawLegs(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
            LocalPlayer player, boolean right, Vec3 hip, Vec3 knee, Vec3 ankle, Vec3 reference,
            int packedLight) {
        if (poseStack == null || buffers == null || camera == null || player == null) return false;
        try {
            if (!CinematicArms343.possessed(player)) return false;
            String modelName = FirstPersonPossession256.possessedModelName(player);
            if (modelName.isEmpty()) return false;
            BbModel npc = BbModelManager.getModel(modelName);
            if (npc == null) return false;
            ResourceLocation texture = npc.getTextureLocation();
            if (texture == null) return false;
            LegCache cache = cacheFor(npc, right);
            if (cache == null) return false;
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
            if (vertices == null) return false;

            Integer lodLevel = BbModel.CURRENT_LOD_LEVEL.get();
            Boolean lod = BbModel.CURRENT_LOD.get();
            boolean drawn;
            try {
                BbModel.CURRENT_LOD_LEVEL.set(0);
                BbModel.CURRENT_LOD.set(Boolean.FALSE);
                cache.save();
                cache.reset();
                boolean thigh = segment(poseStack, vertices, camera, cache.leg, hip, knee, packedLight,
                        KNEE_OVERLAP);
                boolean shin = segment(poseStack, vertices, camera, cache.leg, knee, ankle, packedLight,
                        0.0F);
                drawn = thigh || shin;
            } finally {
                cache.restore();
                BbModel.CURRENT_LOD_LEVEL.set(lodLevel != null ? lodLevel : 0);
                BbModel.CURRENT_LOD.set(lod != null ? lod : Boolean.FALSE);
            }
            if (!drawn) return false;
            noteLegs(modelName);
            drawArmour(poseStack, buffers, camera, player, right, hip, knee, ankle, reference,
                    packedLight);
            return true;
        } catch (Throwable failure) {
            noteFailure(failure);
            return false;
        }
    }

    /** True when Cinematic would not draw this segment at all, because its two ends are the same. */
    public static boolean segmentTooShort(Vec3 from, Vec3 to) {
        if (from == null || to == null) return true;
        return distance(from, to) < MIN_SEGMENT;
    }

    /** One segment of the NPC leg: the model's own leg bone, stretched onto the segment and turned. */
    private static boolean segment(PoseStack poseStack, VertexConsumer vertices, Vec3 camera,
            ModelPart leg, Vec3 from, Vec3 to, int packedLight, float overlap) {
        double length = distance(from, to);
        if (length < MIN_SEGMENT) return false;
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        float inverse = (float) (1.0D / length);
        ALONG.set((float) dx * inverse, (float) dy * inverse, (float) dz * inverse);
        TURN.rotationTo(DOWN, ALONG);
        poseStack.pushPose();
        try {
            poseStack.translate(from.x - camera.x, from.y - camera.y, from.z - camera.z);
            poseStack.mulPose(TURN);
            poseStack.scale(1.0F, (float) ((length + overlap) / BONE_LENGTH), 1.0F);
            leg.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY, -1);
        } finally {
            poseStack.popPose();
        }
        return true;
    }

    /** Cinematic's own armour pass, so leggings and boots stay on the legs we just drew. */
    private static void drawArmour(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
            LocalPlayer player, boolean right, Vec3 hip, Vec3 knee, Vec3 ankle, Vec3 reference,
            int packedLight) {
        MethodHandle handle = armourDraw();
        if (handle == null) return;
        try {
            handle.invokeExact(poseStack, buffers, camera, (AbstractClientPlayer) player, right, hip,
                    knee, ankle, reference, packedLight);
        } catch (Throwable failure) {
            noteFailure(failure);
        }
    }

    /** Cinematic's armour pass, resolved once; null when this Cinematic does not have it. */
    private static MethodHandle armourDraw() {
        if (armourLooked) return armourDraw;
        armourLooked = true;
        try {
            Class<?> armour = named(ARMOUR);
            if (armour == null) return null;
            Method method = armour.getMethod(ARMOUR_DRAW, PoseStack.class, MultiBufferSource.class,
                    Vec3.class, AbstractClientPlayer.class, boolean.class, Vec3.class, Vec3.class,
                    Vec3.class, Vec3.class, int.class);
            armourDraw = MethodHandles.lookup().unreflect(method);
        } catch (Throwable failure) {
            noteFailure(failure);
            armourDraw = null;
        }
        return armourDraw;
    }

    /** Cinematic may sit in another loader; try ours, then the thread's, then the plain lookup. */
    private static Class<?> named(String name) {
        ClassLoader[] loaders = {CinematicLegs344.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()};
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                return Class.forName(name, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // next loader
            }
        }
        return null;
    }

    /** The leg bone the leg is drawn from, under the names bbmodel exports use. */
    private static ModelPart legPart(BbModel npc, boolean right) {
        ModelPart part = npc.getPart(right ? "rightleg" : "leftleg");
        return part != null ? part : npc.getPart(right ? "right_leg" : "left_leg");
    }

    private static LegCache cacheFor(BbModel npc, boolean right) {
        String key = npc.getName();
        if (key == null) return null;
        LegCache[] slots = CACHE.computeIfAbsent(key, unused -> new LegCache[2]);
        int slot = right ? 0 : 1;
        ModelPart leg = legPart(npc, right);
        if (leg == null) {
            slots[slot] = null;
            return null;
        }
        LegCache cache = slots[slot];
        if (cache == null || cache.model != npc || cache.leg != leg) {
            List<ModelPart> parts = leg.getAllParts().toList();
            cache = new LegCache(npc, leg, parts.toArray(new ModelPart[0]), parts.size());
            slots[slot] = cache;
        }
        return cache;
    }

    private static double distance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The bones of one leg with their state kept aside: built once per model, reused every frame. */
    private static final class LegCache {
        private final BbModel model;
        private final ModelPart leg;
        private final ModelPart[] parts;
        private final float[] saved;

        private LegCache(BbModel model, ModelPart leg, ModelPart[] parts, int count) {
            this.model = model;
            this.leg = leg;
            this.parts = parts;
            this.saved = new float[count * FIELDS];
        }

        private void save() {
            for (int i = 0; i < parts.length; i++) {
                ModelPart part = parts[i];
                int at = i * FIELDS;
                saved[at] = part.x;
                saved[at + 1] = part.y;
                saved[at + 2] = part.z;
                saved[at + 3] = part.xRot;
                saved[at + 4] = part.yRot;
                saved[at + 5] = part.zRot;
                saved[at + 6] = part.xScale;
                saved[at + 7] = part.yScale;
                saved[at + 8] = part.zScale;
                saved[at + 9] = part.visible ? 1.0F : 0.0F;
                saved[at + 10] = part.skipDraw ? 1.0F : 0.0F;
            }
        }

        /** The leg at rest around its own pivot: the caller's PoseStack supplies the placement. */
        private void reset() {
            for (ModelPart part : parts) {
                part.x = 0.0F;
                part.y = 0.0F;
                part.z = 0.0F;
                part.xRot = 0.0F;
                part.yRot = 0.0F;
                part.zRot = 0.0F;
                part.xScale = 1.0F;
                part.yScale = 1.0F;
                part.zScale = 1.0F;
                part.visible = true;
                part.skipDraw = false;
            }
        }

        private void restore() {
            for (int i = 0; i < parts.length; i++) {
                ModelPart part = parts[i];
                int at = i * FIELDS;
                part.x = saved[at];
                part.y = saved[at + 1];
                part.z = saved[at + 2];
                part.xRot = saved[at + 3];
                part.yRot = saved[at + 4];
                part.zRot = saved[at + 5];
                part.xScale = saved[at + 6];
                part.yScale = saved[at + 7];
                part.zScale = saved[at + 8];
                part.visible = saved[at + 9] != 0.0F;
                part.skipDraw = saved[at + 10] != 0.0F;
            }
        }
    }

    /** One line for the first rigs whose legs a fire is lit under, so a live log says which. */
    private static void noteLegs(String model) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String name = model == null || model.isEmpty() ? "?" : model;
        if (REPORTED.add("rig:" + name)) {
            LOGGER.warn("scene legs handed to the NPC rig '{}'", name);
        }
    }

    /** One line if a leg-over ever fails: Cinematic's own legs are drawn and stay visible. */
    private static void noteFailure(Throwable failure) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String kind = failure == null ? "?" : failure.getClass().getSimpleName();
        if (REPORTED.add("fail:" + kind)) {
            LOGGER.warn("scene legs stayed Cinematic's own: {}", kind);
        }
    }
}
