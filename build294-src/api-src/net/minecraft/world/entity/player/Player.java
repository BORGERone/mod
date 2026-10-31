package net.minecraft.world.entity.player;

import net.minecraft.world.entity.LivingEntity;

public class Player extends LivingEntity {
    /** Declared on Player in 1.21.1; ServerPlayer inherits it. */
    public boolean isSpectator() { return false; }
}
