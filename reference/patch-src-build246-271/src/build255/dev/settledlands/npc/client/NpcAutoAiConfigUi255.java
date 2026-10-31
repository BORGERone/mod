package dev.settledlands.npc.client;

import dev.settledlands.npc.NpcAutoAi255;
import dev.settledlands.npc.NpcProfile;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.network.chat.Component;

/**
 * Adds the "Автономный ИИ" configuration section (checkboxes + 1..64 radius sliders)
 * to NpcConfigScreen ("Персонаж" and "Подвижность" tabs), kept in two-way sync with
 * NpcDialogueGraphScreen.
 */
public final class NpcAutoAiConfigUi255 {
    private NpcAutoAiConfigUi255() {}

    public static int appendSection(NpcConfigScreen screen, int startY) {
        if (screen == null) return startY;
        NpcProfile profile = screen.settledlands$profile();
        if (profile == null) return startY;

        int left = screen.settledlands$left();
        int right = screen.settledlands$contentRight();
        int fieldX = screen.settledlands$fieldX();
        int fieldW = screen.settledlands$fieldW();
        int rowH = screen.settledlands$rowH();
        Font font = screen.settledlands$font();

        int block = screen.settledlands$blockStart("gui.settledlands.block.auto_ai", startY);
        int y = screen.settledlands$contentTop(block);

        Component wanderLabel = Component.translatable("gui.settledlands.field.auto_wander");
        Component attackLabel = Component.translatable("gui.settledlands.field.auto_attack");

        int x = left;
        y = addCheck(screen, font, left, right, x, y, wanderLabel, NpcAutoAi255.isAutoWander(profile),
                (btn, checked) -> {
                    NpcAutoAi255.setAutoWander(screen.settledlands$profile(), checked);
                    screen.settledlands$field("autoWander", checked ? 1 : 0);
                });
        x += (font.width(wanderLabel.getString()) + 26) + 14;
        int w2 = font.width(attackLabel.getString()) + 26;
        if (x > left && x + w2 > right) {
            x = left;
            y += 22;
        }
        screen.settledlands$addWidget(
                NpcUi.checkbox(x, y, attackLabel, NpcAutoAi255.isAutoAttackHostiles(profile),
                        (btn, checked) -> {
                            NpcAutoAi255.setAutoAttackHostiles(screen.settledlands$profile(), checked);
                            screen.settledlands$field("autoAttackHostiles", checked ? 1 : 0);
                        })
        );
        y += 26;

        screen.settledlands$rowLabel("gui.settledlands.field.wander_radius", y);
        screen.settledlands$addWidget(
                NpcUi.slider(
                        fieldX,
                        y,
                        fieldW,
                        NpcAutoAi255.MIN_RADIUS,
                        NpcAutoAi255.MAX_RADIUS,
                        NpcAutoAi255.wanderRadius(profile),
                        v -> Component.translatable("gui.settledlands.hint.blocks", new Object[]{v}),
                        v -> {
                            NpcAutoAi255.setWanderRadius(screen.settledlands$profile(), v);
                            screen.settledlands$field("wanderRadius", v);
                        }
                )
        );
        y += rowH;

        screen.settledlands$rowLabel("gui.settledlands.field.aggro_radius", y);
        screen.settledlands$addWidget(
                NpcUi.slider(
                        fieldX,
                        y,
                        fieldW,
                        NpcAutoAi255.MIN_RADIUS,
                        NpcAutoAi255.MAX_RADIUS,
                        NpcAutoAi255.aggroRadius(profile),
                        v -> Component.translatable("gui.settledlands.hint.blocks", new Object[]{v}),
                        v -> {
                            NpcAutoAi255.setAggroRadius(screen.settledlands$profile(), v);
                            screen.settledlands$field("aggroRadius", v);
                        }
                )
        );
        y += rowH;

        return screen.settledlands$blockEnd(block, y);
    }

    private static int addCheck(NpcConfigScreen screen, Font font, int left, int right, int x, int y,
                                Component label, boolean checked, Checkbox.OnValueChange onChange) {
        int w = font.width(label.getString()) + 26;
        if (x > left && x + w > right) {
            x = left;
            y += 22;
        }
        screen.settledlands$addWidget(NpcUi.checkbox(x, y, label, checked, onChange));
        return y;
    }

    public static void onLocalFieldEdit(NpcConfigScreen screen, String key, String value) {
        if (screen == null) return;
        NpcAutoAi255.applyFieldEdit(screen.settledlands$profile(), key, value);
    }
}
