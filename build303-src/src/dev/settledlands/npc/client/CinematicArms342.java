package dev.settledlands.npc.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 342: the hands Cinematic Interactions draws in a scene are the possessed NPC's hands.
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
 * <p>This class decides nothing about how an arm looks. It answers one question per scene hand - does
 * this hand belong to a possessed player - and refuses whenever the answer is uncertain, in which case
 * Cinematic's own cube is drawn exactly as before. Nothing is cached per frame and nothing allocates
 * beyond the model name the possession state already resolves.</p>
 */
public final class CinematicArms342 {

    private static final Logger LOGGER = LoggerFactory.getLogger("SettledLands/CinematicArms342");

    /** Cinematic refuses to draw an arm shorter than this, and so do we: nothing would be drawn. */
    private static final double MIN_ARM_SQR = 1.0E-8D;

    /** Bounded report: the first hand-overs and at most one failure line per session. */
    private static final int REPORT_LIMIT = 4;

    private static final Set<String> REPORTED = ConcurrentHashMap.newKeySet();

    private CinematicArms342() {
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

    /** One line for the first rigs a scene hands its arms to, so a live log says which. */
    public static void noteHandOver(String model) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String name = model == null || model.isEmpty() ? "?" : model;
        if (REPORTED.add("rig:" + name)) {
            LOGGER.warn("scene hands handed to the NPC rig '{}'", name);
        }
    }

    /** One line if a hand-over ever throws: Cinematic's own arm is drawn and stays visible. */
    public static void noteFailure(Throwable failure) {
        if (REPORTED.size() >= REPORT_LIMIT) return;
        String kind = failure == null ? "?" : failure.getClass().getSimpleName();
        if (REPORTED.add("fail:" + kind)) {
            LOGGER.warn("scene hand stayed Cinematic's own: {}", kind);
        }
    }
}
