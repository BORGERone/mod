package dev.settledlands.mixin;

import dev.settledlands.npc.GraphBlockCleanup446;
import java.util.ArrayDeque;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 446: observes every block destruction or replacement that goes through
 * {@code Level#setBlock(BlockPos, BlockState, int, int)} (manual breaks, explosions,
 * {@code /setblock}, pistons, Trial Spawner self-destruct, and mod edits).
 *
 * <p>Uses a zero-allocation depth counter plus sparse stack in a {@link ThreadLocal} so recursive
 * {@code setBlock} calls (e.g. door upper/lower halves breaking each other) pair cleanly while
 * 99.99% of terrain writes touch only a primitive counter.</p>
 */
@Mixin(targets = "net.minecraft.world.level.Level", remap = false)
public abstract class BlockDestroy446Mixin {

    private static final ThreadLocal<Object[]> STATE446 = new ThreadLocal<Object[]>();

    private static Object[] settledlands$state446() {
        Object[] s = STATE446.get();
        if (s == null) {
            // s[0] = Integer depth, s[1] = ArrayDeque<Object[]> pending frames
            s = new Object[] {Integer.valueOf(0), new ArrayDeque<Object[]>()};
            STATE446.set(s);
        }
        return s;
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"), remap = false)
    private void settledlands$beforeSetBlock446(BlockPos pos, BlockState newState, int flags,
            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Object[] st = settledlands$state446();
        int depth = ((Integer) st[0]).intValue() + 1;
        st[0] = Integer.valueOf(depth);
        if (!(((Object) this) instanceof ServerLevel sl) || pos == null || sl.isClientSide) return;
        try {
            if (!GraphBlockCleanup446.isTracked(sl, pos)) return;
            BlockState oldState = sl.getBlockState(pos);
            if (oldState == null) return;
            @SuppressWarnings("unchecked")
            ArrayDeque<Object[]> stack = (ArrayDeque<Object[]>) st[1];
            stack.push(new Object[] {Integer.valueOf(depth), sl, pos.immutable(), oldState});
        } catch (Throwable ignored) {
        }
    }

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("RETURN"), remap = false)
    private void settledlands$afterSetBlock446(BlockPos pos, BlockState newState, int flags,
            int recursionLeft, CallbackInfoReturnable<Boolean> cir) {
        Object[] st = settledlands$state446();
        int depth = ((Integer) st[0]).intValue();
        st[0] = Integer.valueOf(Math.max(0, depth - 1));
        @SuppressWarnings("unchecked")
        ArrayDeque<Object[]> stack = (ArrayDeque<Object[]>) st[1];
        if (stack.isEmpty()) return;
        Object[] top = stack.peek();
        if (top == null || ((Integer) top[0]).intValue() != depth) return;
        stack.pop();
        if (cir == null || !Boolean.TRUE.equals(cir.getReturnValue())) return;
        try {
            ServerLevel sl = (ServerLevel) top[1];
            BlockPos savedPos = (BlockPos) top[2];
            BlockState oldState = (BlockState) top[3];
            BlockState afterState = sl.getBlockState(savedPos);
            GraphBlockCleanup446.onBlockChanged(sl, savedPos, oldState, afterState);
        } catch (Throwable ignored) {
        }
    }
}
