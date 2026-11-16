package dev.settledlands.npc.client;

import dev.settledlands.npc.BlockTalk358;
import dev.settledlands.npc.NpcNet;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Build 358, client half of a talking block: aim the head at the block the window is about.
 *
 * <p>The server sends the block's coordinates in a config view a moment before the dialogue window
 * ({@code BlockTalk358#say}). {@code NpcConfigScreen#open} is the one place the client turns a config
 * view into a screen, and {@code NpcBlockOpen351Mixin} already stands there for the block editor - it
 * now tells the two apart: the coordinates mean a talk, the absence of them means an edit.</p>
 *
 * <p>The aim is the screen's own arithmetic ({@code GuideDialogueScreen#face}) at a point instead of
 * an entity: a block is not an entity, so the window's own camera cannot find it, and this is the
 * stand-in. Both the current and the previous rotation are set, so the head does not sweep there.</p>
 */
public final class BlockTalkClient358 {

    private BlockTalkClient358() {}

    /** Is this config view a talking block rather than the editor of one? */
    public static boolean isTalk(NpcNet.CfgView view) {
        return view != null && BlockTalk358.readMarker(view.data()) != null;
    }

    /** Turn the head to the block, so the view is on it when the window appears. */
    public static void aim(NpcNet.CfgView view) {
        int[] where = view == null ? null : BlockTalk358.readMarker(view.data());
        if (where == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft == null ? null : minecraft.player;
        if (player == null) return;
        float[] angles = BlockTalk358.aimAngles(player.getX(), player.getEyeY(), player.getZ(),
                where[0], where[1], where[2]);
        player.setYRot(angles[0]);
        player.setXRot(angles[1]);
        player.yRotO = angles[0];
        player.xRotO = angles[1];
    }
}
