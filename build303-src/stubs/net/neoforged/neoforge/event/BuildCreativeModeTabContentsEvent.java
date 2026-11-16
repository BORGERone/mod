package net.neoforged.neoforge.event;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.Event;

/** Compile-time shape of the event a tab is filled from (build 370). */
public class BuildCreativeModeTabContentsEvent extends Event implements CreativeModeTab.Output {

    public ResourceKey<CreativeModeTab> getTabKey() {
        return null;
    }

    @Override
    public void accept(ItemStack stack, CreativeModeTab.TabVisibility visibility) {}
}
