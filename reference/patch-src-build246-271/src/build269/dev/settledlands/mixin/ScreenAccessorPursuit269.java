package dev.settledlands.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(targets = "net.minecraft.client.gui.screens.Screen", remap = false)
public interface ScreenAccessorPursuit269 {
    @Invoker("addRenderableWidget")
    <T extends AbstractWidget> T settledlands$addRenderableWidget(T widget);
}
