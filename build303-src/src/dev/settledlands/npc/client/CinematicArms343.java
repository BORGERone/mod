package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 343: the hands Cinematic Interactions draws in a scene are the possessed NPC's hands.
 *
 * <p>Cinematic draws the scene arms itself. {@code Hands.drawArm} builds its own arm cubes around the
 * station and textures them with {@code LocalPlayer.getSkin()}, so a possessed player - whose body is an
 * Ivy rig - was handed the player's own arm skin: the player's arm hanging at the crafting table, not the
 * NPC's. Cinematic also has a model-based path for that arm ({@code Hands.drawModelArm}, used with Yes
 * Steve Model), which places the arm at the station and lets {@code PlayerRenderer.renderRightHand} draw
 * it. That is the very method Build 339 already hands to the NPC, so a possessed player only has to be
 * moved onto that path: the station placement, the reach, the bob and the item in hand all stay
 * Cinematic's own, and the arm that is drawn is the NPC's.</p>
 *
 * <p>Build 342 reached that private method through a Mixin {@code @Invoker} declared in an interface
 * mixin, and Mixin refuses that shape outright - an interface mixin may only target an interface, so the
 * whole client died in PREPARE with {@code @Mixin target type mismatch: Hands is not an interface}. This
 * build does not ask Mixin to reshape Cinematic at all beyond the one injection: the method is looked up
 * once, the first time a scene hand needs it, and the result - a handle or a refusal - is kept. Nothing
 * is resolved per frame, the handle call allocates nothing, and a Cinematic build whose method has
 * another shape simply keeps its own arm instead of failing at startup.</p>
 *
 * <p>This class decides nothing about how an arm looks: it answers whether a scene hand belongs to a
 * possessed player, and hands the frame to Cinematic's own placement when it does. Whenever the answer
 * is uncertain - not possessed, an arm Cinematic itself would not draw, no method, an exception - the
 * hand stays Cinematic's own and a scene never loses an arm.</p>
 */
public final class CinematicArms343 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/CinematicArms343");

    /** Cinematic's scene-hands class, named as text so it is never needed when the mod is absent. */
    private static final String HANDS = "dev.dyslexic.cinematic.client.Hands";

    /** The model-arm pass of Cinematic: it places the arm and calls PlayerRenderer's hand methods. */
    private static final String MODEL_ARM = "drawModelArm";

    /** Cinematic refuses to draw an arm shorter than this, and so do we: nothing would be drawn. */
    private static final double MIN_ARM_SQR = 1.0E-8D;

    /** Bounded report: the first hand-overs and at most one failure line per session. */
    private static final int REPORT_LIMIT = 4;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    /** Resolved on first use on the render thread, and then never again. */
    private static MethodHandle drawModelArm;
    private static boolean drawModelArmLooked;

    private CinematicArms343() {
    }

    /**
     * The player whose hand this scene arm belongs to, or {@code null} when the arm has to stay
     * Cinematic's own cube: no client, no player, an arm too short to draw, or a player who is not
     * driving an NPC body.
     */
    public static LocalPlayer handOwner(Vec3 hand, Vec3 shoulder) {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            LocalPlayer player = minecraft == null ? null : minecraft.player;
            if (player == null || armTooShort(hand, shoulder) || !possessed(player)) return null;
            noteHandOver(FirstPersonPossession256.possessedModelName(player));
            return player;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** The same two ends Cinematic gives up between; kept here so the guard can be checked alone. */
    public static boolean armTooShort(Vec3 hand, Vec3 shoulder) {
        if (hand == null || shoulder == null) return true;
        double dx = hand.x - shoulder.x;
        double dy = hand.y - shoulder.y;
        double dz = hand.z - shoulder.z;
        return dx * dx + dy * dy + dz * dz < MIN_ARM_SQR;
    }

    /** True while this player is driving an NPC body. */
    public static boolean possessed(LocalPlayer player) {
        try {
            return player != null && !FirstPersonPossession256.possessedModelName(player).isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Lets Cinematic place the arm and draw it through the hand pass Build 339 gives to the NPC.
     *
     * @return true when Cinematic drew the arm, so the caller must not draw its own cube
     */
    public static boolean drawModelArm(PoseStack poseStack, MultiBufferSource buffers, Vec3 camera,
            Vec3 hand, Vec3 shoulder, boolean right, int packedLight, Vec3 forward, LocalPlayer player) {
        MethodHandle handle = drawModelArm();
        if (handle == null) return false;
        try {
            handle.invokeExact(poseStack, buffers, camera, hand, shoulder, right, packedLight, forward,
                    player);
            return true;
        } catch (Throwable failure) {
            noteFailure(failure);
            return false;
        }
    }

    /** Cinematic's private model-arm pass, resolved once; null when this Cinematic does not have it. */
    private static MethodHandle drawModelArm() {
        if (drawModelArmLooked) return drawModelArm;
        drawModelArmLooked = true;
        try {
            Class<?> hands = handsClass();
            if (hands == null) return null;
            Method method = hands.getDeclaredMethod(MODEL_ARM, PoseStack.class, MultiBufferSource.class,
                    Vec3.class, Vec3.class, Vec3.class, boolean.class, int.class, Vec3.class,
                    LocalPlayer.class);
            method.setAccessible(true);
            drawModelArm = MethodHandles.lookup().unreflect(method);
        } catch (Throwable failure) {
            noteFailure(failure);
            drawModelArm = null;
        }
        return drawModelArm;
    }

    /** Cinematic may sit in another loader; try ours, then the thread's, then the plain lookup. */
    private static Class<?> handsClass() {
        ClassLoader[] loaders = {CinematicArms343.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader(), ClassLoader.getSystemClassLoader()};
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try {
                return Class.forName(HANDS, false, loader);
            } catch (ClassNotFoundException | LinkageError ignored) {
                // next loader
            }
        }
        return null;
    }

    /** One line for the first rigs a scene hands its arms to, so a live log says which. */
    public static void noteHandOver(String model) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String name = model == null || model.isEmpty() ? "?" : model;
        if (REPORTED.add("rig:" + name)) {
            LOGGER.warn("scene hands handed to the NPC rig '{}'", name);
        }
    }

    /** One line if a hand-over ever fails: Cinematic's own arm is drawn and stays visible. */
    public static void noteFailure(Throwable failure) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String kind = failure == null ? "?" : failure.getClass().getSimpleName();
        if (REPORTED.add("fail:" + kind)) {
            LOGGER.warn("scene hand stayed Cinematic's own: {}", kind);
        }
    }
}
