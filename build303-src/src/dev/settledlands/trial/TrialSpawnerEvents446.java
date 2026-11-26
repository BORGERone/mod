package dev.settledlands.trial;

import dev.settledlands.door.DoorGraph382Events;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Build 446: intercepts NPC-wand right-clicks on {@code trialtuner:trial_spawner} before Trial
 * Tuner's {@code useWithoutItem}/{@code useItemOn}, and handles compat callbacks from
 * {@code TrialSpawnerBlockEntity} / {@code TrialTriggers}.
 */
@EventBusSubscriber(modid = "settledlands")
public final class TrialSpawnerEvents446 {

    private static final Map<ServerPlayer, Object[]> LAST_CLICK = new WeakHashMap<>();

    private TrialSpawnerEvents446() {}

    private static boolean alreadyHandled(ServerPlayer player, ServerLevel level, BlockPos pos) {
        long now = level.getGameTime();
        String key = TrialGraph446Store.key(level, pos);
        Object[] prev = LAST_CLICK.get(player);
        if (prev != null && Long.valueOf(now).equals(prev[0]) && key.equals(prev[1])) return true;
        LAST_CLICK.put(player, new Object[] {now, key});
        return false;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event == null) return;
        Level rawLevel = event.getLevel();
        BlockPos pos = event.getPos();
        if (rawLevel == null || pos == null) return;
        if (!TrialSpawnerBridge446.isSpawnerAt(rawLevel, pos)) return;
        if (!DoorGraph382Events.isWand(event.getItemStack())) return;

        // Cancel on BOTH client and server so TrialTuner neither sends OpenRequest nor opens its own screen.
        event.setCancellationResult(InteractionResult.sidedSuccess(rawLevel.isClientSide));
        event.setCanceled(true);

        if (rawLevel.isClientSide) return;
        if (!(rawLevel instanceof ServerLevel level)) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (alreadyHandled(player, level, pos)) return;

        TrialGraph446.open(player, level, pos);
    }

    /** Called from {@code TrialSpawnerEntityMixin446} when a wave starts. */
    public static void handleWaveStart(Object blockEntity, ServerLevel level) {
        if (level == null || blockEntity == null) return;
        BlockPos pos = TrialSpawnerBridge446.readBlockPos(blockEntity);
        if (pos == null) return;
        List<UUID> participants = TrialSpawnerBridge446.readParticipants(blockEntity);
        TrialGraph446.onStartWave(level, pos, participants);
    }

    /** Called from {@code TrialSpawnerEntityMixin446} at the head of {@code finishWave}. */
    public static void handleWaveClear(Object blockEntity, ServerLevel level) {
        if (level == null || blockEntity == null) return;
        BlockPos pos = TrialSpawnerBridge446.readBlockPos(blockEntity);
        if (pos == null) return;
        List<UUID> participants = TrialSpawnerBridge446.readParticipants(blockEntity);
        TrialGraph446.onWaveCleared(level, pos, participants);
    }

    /** Called from {@code TrialTriggers.onDestroyed} BEFORE block entity removal. */
    public static void handleSpawnerDestroyed(Level rawLevel, BlockPos pos) {
        if (!(rawLevel instanceof ServerLevel level) || pos == null || rawLevel.isClientSide) return;
        BlockEntity be = level.getBlockEntity(pos);
        List<UUID> participants = TrialSpawnerBridge446.readParticipants(be);
        TrialGraph446.onSpawnerDestroyed(level, pos, participants, null, true);
    }
}
