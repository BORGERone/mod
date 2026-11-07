package net.minecraft.client.multiplayer;

/**
 * Compile-time mirror (build 400) of the client's interaction manager, narrowed to the one call this mod makes.
 *
 * <p>{@code useItemOn(LocalPlayer, InteractionHand, BlockHitResult)} is the method the game itself calls when the
 * player right-clicks a block; calling it from a screen is exactly "the player clicked this chest", which is the
 * opening path build 400 uses instead of opening a menu from the server. The descriptor is the real one - the
 * name of this method has not changed since 1.13 and the three arguments are the three the client handler
 * already holds.</p>
 */
public class MultiPlayerGameMode {

    public net.minecraft.world.InteractionResult useItemOn(
            net.minecraft.client.player.LocalPlayer player,
            net.minecraft.world.InteractionHand hand,
            net.minecraft.world.phys.BlockHitResult hit) {
        return null;
    }
}
