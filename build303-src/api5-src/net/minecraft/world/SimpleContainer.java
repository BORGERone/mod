package net.minecraft.world;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/**
 * Compile-time shape of the game's plain container (Build 348).
 *
 * <p>The NPC inventory is a {@code SimpleContainer} of 27 slots, so this is the type its own
 * constructor calls and the type the armour sync reads through {@link Container}. Only the two reads
 * are declared - the pass never writes into an NPC's inventory.</p>
 */
public class SimpleContainer implements Container {
    private final List<ItemStack> items = new ArrayList<>();

    public SimpleContainer(int size) {
        for (int i = 0; i < size; i++) {
            this.items.add(ItemStack.EMPTY);
        }
    }

    @Override
    public ItemStack getItem(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    public void setChanged() {
    }
}
