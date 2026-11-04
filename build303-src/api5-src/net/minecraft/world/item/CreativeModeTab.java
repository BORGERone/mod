package net.minecraft.world.item;

/**
 * Compile-time mirror of the creative tab (build 370): only what the mod already uses - the
 * {@code TabVisibility} constant its own {@code PurgatoryManager} passes and the {@code Output} call it
 * makes - so the door key is added to a tab exactly the way the revival altar already is.
 */
public class CreativeModeTab {

    public enum TabVisibility {
        PARENT_AND_SEARCH_TABS
    }

    public interface Output {
        void accept(ItemStack stack, TabVisibility visibility);
    }
}
