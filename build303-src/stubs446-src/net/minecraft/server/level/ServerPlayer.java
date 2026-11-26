package net.minecraft.server.level;

import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Compile-time mirror (Build 446): preserves Build 443 {@code ServerPlayer} plus {@link #isCreative()}. */
public class ServerPlayer extends Player {
    public float fallDistance;

    public boolean isSpectator() { return false; }

    public boolean isCreative() { return false; }

    public MinecraftServer getServer() { return null; }

    public ServerLevel serverLevel() { return null; }

    public void clearFire() {}

    public void setDeltaMovement(Vec3 movement) {}

    public float getYRot() { return 0.0F; }

    public float getXRot() { return 0.0F; }

    public boolean teleportTo(ServerLevel level, double x, double y, double z,
            Set<?> relatives, float yaw, float pitch) {
        return false;
    }

    public java.util.OptionalInt openMenu(net.minecraft.world.MenuProvider provider) { return null; }

    public ItemEntity drop(ItemStack stack, boolean dropAround) { return null; }
}
