package net.minecraft.client.gui.components;

/**
 * Compile-time signature only; never bundled into the mod JAR.
 *
 * <p>Build 382: the base mod's own {@code NpcUi$NpcSlider} extends this, so the door rows of build 382
 * need its shape to place a slider and to read the state of one. Only the members the mod already uses
 * are mirrored.</p>
 */
public abstract class AbstractSliderButton extends AbstractWidget {
    public double value;

    public void setValue(double value) {
    }

    protected void updateMessage() {
    }
}
