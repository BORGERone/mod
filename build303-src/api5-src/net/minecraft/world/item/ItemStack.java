package net.minecraft.world.item;

/**
 * Compile-time shape of an item stack (Build 348).
 *
 * <p>{@code isEmpty} / {@code getItem} / {@code hasFoil} / {@code copy} are the four members the
 * armour pass reads, all of them spelled exactly as vanilla's {@code HumanoidArmorLayer} spells them
 * in 1.21.1. {@code EMPTY} is the shared empty stack the game compares against.</p>
 */
public final class ItemStack {
    public static final ItemStack EMPTY = new ItemStack();

    public boolean isEmpty() {
        return true;
    }

    public Item getItem() {
        return null;
    }

    public boolean hasFoil() {
        return false;
    }

    public ItemStack copy() {
        return this;
    }
}
