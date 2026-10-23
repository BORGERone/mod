package com.hazelism.nomansland.registry;

import com.hazelism.nomansland.NoMansLand;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NoMansLand.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + NoMansLand.MOD_ID))
                    .icon(() -> ModItems.FIRE_POT.get().getDefaultInstance())
                    .displayItems((params, output) -> {
                        output.accept(ModItems.FIRE_POT.get());
                        output.accept(ModItems.SMOKE_POT.get());
                        output.accept(ModItems.RESIN_POT.get());
                        output.accept(ModItems.RATTLE.get());
                        output.accept(ModItems.SPRUCE_RESIN.get());
                        output.accept(ModItems.WOODEN_BARRICADE.get());
                        output.accept(ModItems.UNDEAD_SPAWN_EGG.get());
                        output.accept(ModItems.BANNER_BEARER_SPAWN_EGG.get());
                    })
                    .build());

    private ModCreativeTabs() {}
}
