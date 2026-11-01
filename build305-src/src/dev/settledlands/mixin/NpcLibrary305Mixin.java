package dev.settledlands.mixin;

import dev.settledlands.npc.client.NpcGraphCanvas;
import dev.settledlands.npc.client.NpcNodeLibrary305;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Build 305: the node library panel on the graph screen.
 *
 * <p>The panel is painted last, on top of the canvas, so no card can cover it. Its search field
 * is a real {@code EditBox} in the widget list, kept invisible: that is what gives the field
 * proper text input - keyboard layouts, IME, key repeat - while the panel paints the box and the
 * text on top, in the workspace colours. A click inside the panel never reaches the canvas, so
 * it cannot pan, select or drop a cable.</p>
 *
 * <p>Adding a node goes through the screen's own {@code add(int)}: same undo snapshot, same
 * selection, same dirty flag as the stock add buttons.</p>
 */
@Mixin(targets = "dev.settledlands.npc.client.NpcDialogueGraphScreen", remap = false)
public abstract class NpcLibrary305Mixin {

    /** Our own field - merged into the screen, invisible on purpose. */
    @Unique
    private EditBox settledlands$search305;

    /** The screen's own add - the path the stock buttons use. */
    @Shadow
    public abstract void add(int type);

    @Shadow
    public abstract NpcGraphCanvas settledlands$canvas();

    @Shadow
    public abstract Font settledlands$font();

    @Unique
    private int settledlands$canvasTop() {
        NpcGraphCanvas canvas = settledlands$canvas();
        return canvas == null ? 38 : canvas.top;
    }

    @Unique
    private int settledlands$canvasBottom() {
        NpcGraphCanvas canvas = settledlands$canvas();
        return canvas == null ? 240 : canvas.bottom + 4;
    }

    @Inject(method = "widgets()V", at = @At("TAIL"), remap = false)
    private void settledlands$libraryWidgets(CallbackInfo ci) {
        Font font = settledlands$font();
        if (font == null) return;
        int top = settledlands$canvasTop();
        // The list is rebuilt on every structural change and drops the widgets, so the box is
        // recreated here with the search text the panel is holding.
        EditBox box = new EditBox(font, NpcNodeLibrary305.searchX(),
                NpcNodeLibrary305.searchY(top), NpcNodeLibrary305.searchWidth(),
                NpcNodeLibrary305.searchHeight(),
                (Component) (Object) Component.translatable("gui.settledlands.workspace.search"));
        box.setMaxLength(24);
        box.setValue(NpcNodeLibrary305.query());
        box.setResponder(NpcNodeLibrary305::setQuery);
        box.visible = false;
        settledlands$search305 = box;
        // The box is invisible and never renders itself - the panel paints the field - so it
        // only has to be in the child list for the screen to hand it the keyboard. Adding it
        // this way keeps the widget list of the stock screen untouched.
        Screen screen = (Screen) (Object) this;
        if (!screen.children().contains(box)) {
            screen.children().add(box);
        }
        if (NpcNodeLibrary305.isSearchFocused()) {
            screen.setFocused(box);
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("TAIL"),
            remap = false)
    private void settledlands$libraryRender(GuiGraphics graphics, int mouseX, int mouseY,
            float partialTick, CallbackInfo ci) {
        NpcNodeLibrary305.setMouse(mouseX, mouseY);
        NpcNodeLibrary305.draw(graphics, settledlands$font(), settledlands$canvasBottom(),
                settledlands$canvasTop());
    }

    @Inject(method = "mouseClicked(DDI)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$libraryClick(double mouseX, double mouseY, int button,
            CallbackInfoReturnable<Boolean> cir) {
        int top = settledlands$canvasTop();
        int bottom = settledlands$canvasBottom();
        if (!NpcNodeLibrary305.contains(mouseX, mouseY, bottom, top)) {
            NpcNodeLibrary305.setSearchFocused(false);
            return;
        }
        if (button == 0) {
            if (NpcNodeLibrary305.inSearchField(mouseX, mouseY, top)) {
                NpcNodeLibrary305.setSearchFocused(true);
                if (settledlands$search305 != null) {
                    settledlands$search305.setValue(NpcNodeLibrary305.query());
                    ((Screen) (Object) this).setFocused(settledlands$search305);
                }
            } else {
                int type = NpcNodeLibrary305.entryAt(mouseX, mouseY, bottom, top);
                if (type >= 0) {
                    add(type);
                }
                NpcNodeLibrary305.setSearchFocused(false);
                ((Screen) (Object) this).setFocused(null);
            }
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseScrolled(DDDD)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$libraryScroll(double mouseX, double mouseY, double scrollX,
            double scrollY, CallbackInfoReturnable<Boolean> cir) {
        if (NpcNodeLibrary305.scroll(mouseX, mouseY, scrollY, settledlands$canvasBottom(),
                settledlands$canvasTop())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "keyPressed(III)Z", at = @At("HEAD"), cancellable = true, remap = false)
    private void settledlands$libraryKeys(int keyCode, int scanCode, int modifiers,
            CallbackInfoReturnable<Boolean> cir) {
        if (NpcNodeLibrary305.isSearchFocused()) {
            // While the field has the keyboard, the screen must not act on the key - Delete
            // would otherwise delete the selected node instead of a letter in the search.
            if (keyCode == 256) { // escape
                NpcNodeLibrary305.setQuery("");
                NpcNodeLibrary305.setSearchFocused(false);
                if (settledlands$search305 != null) {
                    settledlands$search305.setValue("");
                }
                ((Screen) (Object) this).setFocused(null);
                cir.setReturnValue(true);
                return;
            }
            if (keyCode == 257 || keyCode == 335) { // enter
                NpcNodeLibrary305.setSearchFocused(false);
                ((Screen) (Object) this).setFocused(null);
                cir.setReturnValue(true);
                return;
            }
            if (settledlands$search305 != null) {
                settledlands$search305.keyPressed(keyCode, scanCode, modifiers);
            }
            cir.setReturnValue(true);
            return;
        }
        if (keyCode == 76) { // L
            NpcNodeLibrary305.toggle();
            cir.setReturnValue(true);
        }
    }
}
