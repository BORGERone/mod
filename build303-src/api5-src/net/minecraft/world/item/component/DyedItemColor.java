package net.minecraft.world.item.component;

import net.minecraft.world.item.ItemStack;

/**
 * Compile-time shape of the dyed-colour component (Build 348), used for leather.
 *
 * <p>{@code LEATHER_COLOR} carries the game's own value (-6265536) so an undyed piece keeps the
 * colour vanilla gives it; {@code getOrDefault} is the pair the armour layer calls before it tints a
 * dyeable layer.</p>
 */
public record DyedItemColor(int rgb) {
    public static final int LEATHER_COLOR = -6265536;

    public static int getOrDefault(ItemStack stack, int fallback) {
        return fallback;
    }
}
