package dev.settledlands.npc.client;

import dev.settledlands.model.BbModelStorage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Build 254 client helpers:
 *   1. Renders open NpcUi.NpcDropdown popups at Z = +500.0f with flushed GUI batches
 *      so model/pose/action dropdown menus appear above widgets, drop-shadow text,
 *      3D entity previews (Z = +50..+95), item icons (Z = +150), and tooltips (+400).
 *   2. Extracts bundled /assets/settledlands/dragon_default/ivy_oleander.bbmodel
 *      into BbModelStorage.getModelsDir() on client setup if not already present.
 */
public final class NpcDropdownTop254 {
    public static final float POPUP_Z = 500.0f;

    private NpcDropdownTop254() {}

    public static void renderPopupTop(NpcUi.NpcDropdown dropdown, GuiGraphics g, int mouseX, int mouseY) {
        if (dropdown == null || g == null) return;
        g.flush();
        g.pose().pushPose();
        g.pose().translate(0.0f, 0.0f, POPUP_Z);
        try {
            dropdown.renderPopup(g, mouseX, mouseY);
            g.flush();
        } finally {
            g.pose().popPose();
        }
    }

    public static void installDefaultModelAsset() {
        try {
            Path dir = BbModelStorage.getModelsDir();
            if (dir == null) return;
            Files.createDirectories(dir);
            Path target = dir.resolve("ivy_oleander.bbmodel");
            if (!Files.exists(target)) {
                try (InputStream in = NpcDropdownTop254.class.getResourceAsStream(
                        "/assets/settledlands/dragon_default/ivy_oleander.bbmodel")) {
                    if (in != null) {
                        Files.copy(in, target);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
    }
}
