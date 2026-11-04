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

    /** Build 370: the stack a creative tab is handed - {@code new ItemStack(item)}. */
    public ItemStack(net.minecraft.world.level.ItemLike item) {}

    public ItemStack() {}

    public boolean isEmpty() {
        return true;
    }

    public Item getItem() {
        return null;
    }

    public boolean hasFoil() {
        return false;
    }

    /** Build 351: the same identity check the base jar makes on sticks and the coord stick. */
    public boolean is(Item item) { return false; }

    public ItemStack copy() {
        return this;
    }
}
