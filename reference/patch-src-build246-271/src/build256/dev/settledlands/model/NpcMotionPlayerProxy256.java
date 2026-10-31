package dev.settledlands.model;

import com.mojang.authlib.GameProfile;
import dev.settledlands.npc.GuideNpc;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;

/**
 * Detached client-only player proxy bound to a GuideNpc for Fresh Animations:
 * Player Extension and Touch'n Motion retargeting. Never added to ClientLevel's
 * entity table, tab list, or networking.
 */
public final class NpcMotionPlayerProxy256 extends RemotePlayer {
    private static final AtomicInteger NEXT_PROXY_OFFSET = new AtomicInteger(1);

    private GuideNpc boundNpc;
    private final ClientLevel boundLevel;
    private int lastSyncedTick = Integer.MIN_VALUE;

    public NpcMotionPlayerProxy256(ClientLevel level, GuideNpc npc) {
        super(level, createProfile(npc));
        this.boundLevel = level;
        this.boundNpc = npc;
        int offset = Math.floorMod(NEXT_PROXY_OFFSET.getAndIncrement(), 0x000FFFFF) + 1;
        this.setId(NpcMotionBridge256.PROXY_ID_BASE - offset);
    }

    private static GameProfile createProfile(GuideNpc npc) {
        UUID uuid = npc != null && npc.getUUID() != null ? npc.getUUID() : UUID.randomUUID();
        String name = "slnpc" + Integer.toHexString(uuid.hashCode());
        if (name.length() > 16) name = name.substring(0, 16);
        return new GameProfile(uuid, name);
    }

    public boolean matches(GuideNpc npc, ClientLevel level) {
        return npc != null && level == this.boundLevel
            && npc.getUUID() != null && npc.getUUID().equals(this.getUUID());
    }

    public GuideNpc boundNpc() {
        return boundNpc;
    }

    public void syncFrom(GuideNpc npc) {
        this.boundNpc = npc;
        if (npc == null) return;
        this.xo = npc.xo;
        this.yo = npc.yo;
        this.zo = npc.zo;
        this.xOld = npc.xOld;
        this.yOld = npc.yOld;
        this.zOld = npc.zOld;
        this.setPosRaw(npc.getX(), npc.getY(), npc.getZ());
        this.setDeltaMovement(npc.getDeltaMovement());
        this.setOnGround(npc.onGround());

        this.yRotO = npc.yRotO;
        this.setYRot(npc.getYRot());
        this.xRotO = npc.xRotO;
        this.setXRot(npc.getXRot());
        this.yBodyRotO = npc.yBodyRotO;
        this.yBodyRot = npc.yBodyRot;
        this.yHeadRotO = npc.yHeadRotO;
        this.yHeadRot = npc.yHeadRot;

        this.tickCount = npc.tickCount;
        this.hurtTime = npc.hurtTime;
        this.hurtDuration = npc.hurtDuration;
        this.deathTime = npc.deathTime;
        this.swinging = npc.swinging;
        this.swingTime = npc.swingTime;
        this.oAttackAnim = npc.oAttackAnim;
        this.attackAnim = npc.attackAnim;
        this.swingingArm = npc.swingingArm != null ? npc.swingingArm : InteractionHand.MAIN_HAND;
        this.fallDistance = npc.fallDistance;
        this.horizontalCollision = npc.horizontalCollision;
        this.verticalCollision = npc.verticalCollision;
        this.setPose(isNpcCrouching(npc) ? Pose.CROUCHING : npc.getPose());

        if ((Object) this.walkAnimation instanceof WalkAnimationSyncAccess256 sync) {
            sync.settledlands$copyWalkStateFrom(npc.walkAnimation);
        } else if (this.lastSyncedTick != npc.tickCount) {
            this.lastSyncedTick = npc.tickCount;
            float prev = npc.walkAnimation.speed(0.0f);
            float curr = npc.walkAnimation.speed(1.0f);
            this.walkAnimation.setSpeed(prev);
            this.walkAnimation.update(curr, 1.0f);
        }
    }

    private static boolean isNpcCrouching(GuideNpc npc) {
        return npc != null && (npc.poseIndex() == 2 || npc.isCrouching() || npc.isShiftKeyDown());
    }

    @Override
    public ItemStack getItemBySlot(EquipmentSlot slot) {
        GuideNpc npc = this.boundNpc;
        if (npc == null || slot == null) return ItemStack.EMPTY;
        ItemStack item = npc.getItemBySlot(slot);
        return item != null ? item : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getMainHandItem() {
        GuideNpc npc = this.boundNpc;
        if (npc == null) return ItemStack.EMPTY;
        ItemStack item = npc.getMainHandItem();
        return item != null ? item : ItemStack.EMPTY;
    }

    @Override
    public ItemStack getOffhandItem() {
        GuideNpc npc = this.boundNpc;
        if (npc == null) return ItemStack.EMPTY;
        ItemStack item = npc.getOffhandItem();
        return item != null ? item : ItemStack.EMPTY;
    }

    @Override
    public HumanoidArm getMainArm() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.getMainArm() != null ? npc.getMainArm() : HumanoidArm.RIGHT;
    }

    @Override
    public boolean isCrouching() {
        return isNpcCrouching(this.boundNpc);
    }

    @Override
    public boolean isShiftKeyDown() {
        return isNpcCrouching(this.boundNpc);
    }

    @Override
    public boolean isSprinting() {
        GuideNpc npc = this.boundNpc;
        return npc != null && (npc.isSprinting() || npc.getTarget() != null && npc.walkAnimation.speed() > 0.68f);
    }

    @Override
    public boolean isSwimming() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.isSwimming();
    }

    @Override
    public boolean isVisuallySwimming() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.isVisuallySwimming();
    }

    @Override
    public boolean isFallFlying() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.isFallFlying();
    }

    @Override
    public boolean isInWater() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.isInWater();
    }

    @Override
    public boolean isAlive() {
        GuideNpc npc = this.boundNpc;
        return npc == null || npc.isAlive();
    }

    @Override
    public float getHealth() {
        GuideNpc npc = this.boundNpc;
        return npc != null ? npc.getHealth() : 20.0f;
    }

    @Override
    public boolean isUsingItem() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.isUsingItem();
    }

    @Override
    public ItemStack getUseItem() {
        GuideNpc npc = this.boundNpc;
        if (npc == null) return ItemStack.EMPTY;
        ItemStack item = npc.getUseItem();
        return item != null ? item : ItemStack.EMPTY;
    }

    @Override
    public int getUseItemRemainingTicks() {
        GuideNpc npc = this.boundNpc;
        return npc != null ? npc.getUseItemRemainingTicks() : 0;
    }

    @Override
    public InteractionHand getUsedItemHand() {
        GuideNpc npc = this.boundNpc;
        return npc != null && npc.getUsedItemHand() != null ? npc.getUsedItemHand() : InteractionHand.MAIN_HAND;
    }
}
