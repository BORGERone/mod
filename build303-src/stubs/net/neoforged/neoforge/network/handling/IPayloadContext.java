package net.neoforged.neoforge.network.handling;

/**
 * Compile-time mirror of what a payload handler is given.
 *
 * <p>Build 377 needs the sender of a payload: the base mod's handlers already ask
 * {@code context.player()} and compare it with {@code instanceof ServerPlayer}.</p>
 */
public interface IPayloadContext {

    net.minecraft.world.entity.player.Player player();

    /**
     * Build 380: the real return type. The base jar's own handlers call
     * {@code enqueueWork(Runnable)Ljava/util/concurrent/CompletableFuture;} - a stub that declared
     * {@code void} compiled an invokeinterface that could never link, and build 380's gate caught it.
     */
    java.util.concurrent.CompletableFuture<?> enqueueWork(Runnable runnable);
}
