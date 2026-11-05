package net.neoforged.neoforge.network.handling;

/**
 * Compile-time mirror of what a payload handler is given.
 *
 * <p>Build 377 needs the sender of a payload: the base mod's handlers already ask
 * {@code context.player()} and compare it with {@code instanceof ServerPlayer}.</p>
 */
public interface IPayloadContext {

    net.minecraft.world.entity.player.Player player();

    void enqueueWork(Runnable runnable);
}
