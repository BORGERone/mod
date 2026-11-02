package dev.settledlands.npc.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.lang.reflect.Method;
import java.util.function.Consumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 340: Touch'n Motion's own motion for the body you are possessing.
 *
 * <p>Touch'n Motion does not animate on its own: at the head of {@code LivingEntityRenderer.render}
 * it works out the gesture for that player - the hand going to a lever, the stance, the lean - and
 * stores it; the pose is then written onto the player model when Entity Model Features copies its
 * animation onto that model. The possession mod draws the NPC's body instead of the player, and it
 * cancels the player's render in {@code RenderPlayerEvent.Pre}, which is fired <em>before</em> that
 * head injection ever runs. So Touch'n Motion never worked the gesture out for a possessed player,
 * and the body the mod retargets from the player's animation had nothing of Touch'n Motion in it.</p>
 *
 * <p>This listener runs at {@link EventPriority#HIGHEST}, ahead of the possession renderer that
 * cancels the pass, and calls Touch'n Motion's own pose entry point for that player with the same
 * {@code PoseStack} and partial tick the renderer would have given it. The gesture is then stored
 * exactly as usual, and the mod's existing Fresh Animations / Touch'n Motion retarget picks it up onto
 * the NPC's bones - the body reaches for the lever because Touch'n Motion decided it should.</p>
 *
 * <p>Nothing is reimplemented and nothing is guessed: the call is Touch'n Motion's public
 * {@code ModelSpace#before}. It is resolved once and only used for a possessed player, so a client
 * without Touch'n Motion pays one class lookup at most, and an unexpected version is reported once and
 * then ignored - the body simply renders as it did before this build.</p>
 */
@EventBusSubscriber(modid = "settledlands", value = Dist.CLIENT)
public final class TouchNMotionPose340 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/TouchNMotion340");

    /** Touch'n Motion only nudges the PoseStack here; the gesture is what this build is after. */
    private static final Consumer<Object> NO_NUDGE = value -> {
    };

    private static Method poseBefore;
    private static boolean resolved;
    private static boolean reported;

    private TouchNMotionPose340() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        if (event == null) return;
        try {
            if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
            PoseStack poseStack = event.getPoseStack();
            if (poseStack == null) return;
            if (FirstPersonPossession256.possessedModelName(player).isEmpty()) return;
            pose(player, poseStack, event.getPartialTick());
        } catch (Throwable ignored) {
            // A player the mod is not drawing must never fail the render pass.
        }
    }

    /** Asks Touch'n Motion to work out this frame's gesture for the possessed player. */
    private static void pose(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        Method before = poseBefore();
        if (before == null) return;
        try {
            before.invoke(null, player, poseStack, partialTick, NO_NUDGE);
        } catch (Throwable failure) {
            report(failure);
        }
    }

    private static Method poseBefore() {
        if (resolved) return poseBefore;
        resolved = true;
        try {
            Class<?> modelSpace = Class.forName("strm.touchnmotion.ModelSpace");
            poseBefore = modelSpace.getMethod("before", LivingEntity.class, PoseStack.class,
                    float.class, Consumer.class);
        } catch (ClassNotFoundException absent) {
            poseBefore = null;
        } catch (Throwable drift) {
            poseBefore = null;
            report(drift);
        }
        return poseBefore;
    }

    private static void report(Throwable failure) {
        if (reported) return;
        reported = true;
        LOGGER.warn("Touch'n Motion pose entry point unavailable ({}); possessed bodies render "
                + "without its interaction motion.", failure.toString());
    }
}
