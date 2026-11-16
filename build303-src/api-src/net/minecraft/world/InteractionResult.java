package net.minecraft.world;

/** Compile-time signature only (Build 351): the one factory the base jar calls. */
public class InteractionResult {
    public static final InteractionResult SUCCESS = new InteractionResult();
    public static final InteractionResult PASS = new InteractionResult();
    public static final InteractionResult FAIL = new InteractionResult();

    public static InteractionResult sidedSuccess(boolean clientSide) { return SUCCESS; }
}
