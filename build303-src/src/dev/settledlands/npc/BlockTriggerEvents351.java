package dev.settledlands.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Build 351: the two things a wand click and an empty hand can do to a block.
 *
 * <p>The event is the one the base jar already listens to for the coord stick
 * ({@code GuideDialogue#onRightClickBlock}), so this is a proven path, not a new one: the server is
 * told about the click before the block reacts, and cancelling here stops the block from also doing
 * its own thing.</p>
 *
 * <ul>
 *   <li><b>Жезл настройки НПС по блоку</b> - opens that block's graph in the editor. The event is
 *       cancelled: the wand is a tool, a click with it must not also open a chest or press a
 *       button.</li>
 *   <li><b>Пустая рука по блоку с графом</b> - runs the chain. The event is <em>not</em> cancelled:
 *       the block keeps doing what it always did, the graph is an addition, never a replacement.</li>
 * </ul>
 *
 * <p>Both are server side only; the client half of the click never reaches here and every block of
 * code below is a hash lookup.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class BlockTriggerEvents351 {

    /**
     * The event is fired once per hand, so one physical click can arrive twice (empty main hand and
     * empty offhand). A click is answered once per tick and block; a real second click in a later
     * tick is not affected.
     */
    private static final java.util.Map<ServerPlayer, Object[]> LAST = new java.util.WeakHashMap<>();

    private BlockTriggerEvents351() {}

    private static boolean alreadyHandled(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        String key = BlockTriggerStore351.key(level, pos);
        Object[] last = LAST.get(player);
        if (last != null && Long.valueOf(now).equals(last[0]) && key.equals(last[1])) return true;
        LAST.put(player, new Object[] {now, key});
        return false;
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event == null || event.getLevel() == null || event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (pos == null) return;
        // Build 382: a door is not a block for this hook. The wand on a door opens the door's own
        // graph (DoorGraph382Events, which runs first), and a door that is not a door for the block
        // editor must never grow a block graph it would never run.
        if (dev.settledlands.door.Doors368.isDoor(level.getBlockState(pos))) return;
        if (alreadyHandled(player, level, pos)) return;

        ItemStack stack = event.getItemStack();
        if (stack != null && !stack.isEmpty() && NpcContent.wand() != null
                && stack.is(NpcContent.wand())) {
            if (BlockTrigger351.open(player, level, pos)) {
                event.setCancellationResult(
                        net.minecraft.world.InteractionResult.sidedSuccess(false));
                event.setCanceled(true);
            }
            return;
        }

        // Only an empty hand triggers a block: anything held means the player is doing something else.
        // getItemStack is the stack the interaction was made with, so empty here means empty hand.
        if (stack != null && !stack.isEmpty()) return;
        BlockTrigger351.run(level, pos, player);
    }
}
