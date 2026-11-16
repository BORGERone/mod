package net.minecraft.world.item;

import java.util.List;
import net.minecraft.resources.ResourceLocation;

/**
 * Compile-time shape of an armour material (Build 348).
 *
 * <p>{@code layers()} and {@code Layer#texture(boolean)} / {@code Layer#dyeable()} are exactly the
 * three calls vanilla's armour layer makes in 1.21.1 - the NeoForge patch of
 * {@code HumanoidArmorLayer} carries the unpatched line
 * {@code this.renderModel(..., armormaterial$layer.texture(flag))} next to it.</p>
 */
public record ArmorMaterial(List<Layer> layers) {
    public record Layer(boolean dyeable) {
        public ResourceLocation texture(boolean inner) {
            return null;
        }
    }
}
