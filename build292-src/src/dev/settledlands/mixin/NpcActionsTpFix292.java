package dev.settledlands.mixin;

import dev.settledlands.npc.NpcSelfTarget292;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Build 292: a trigger command written with the mod's own {@code @npc} token was turned into
 * a broken command.
 *
 * <p>{@code NpcActions#command} fills the placeholders first ({@code @npc} becomes the NPC's
 * uuid) and then decides, by looking at the first character after {@code tp}, whether the
 * author meant "teleport myself here" and an {@code @s} has to be inserted. A uuid starts
 * with a hex digit, so {@code tp @npc ~ ~5 ~} ended up as {@code tp @s <uuid> ~ ~5 ~} and the
 * game answered with "Неверный аргумент для команды".</p>
 *
 * <p>The substitution stays as it is; the shortcut now asks for a real coordinate.
 * {@code @s}, {@code @p} and {@code @npc} are untouched: they never looked like a coordinate
 * and still run as before.</p>
 */
@Mixin(targets = "dev.settledlands.npc.NpcActions", remap = false)
public abstract class NpcActionsTpFix292 {

    /** @Inject, no: the same replaceFirst the stock code would call, with a stricter guard. */
    @Redirect(method = "command(Lnet/minecraft/server/level/ServerPlayer;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/String;replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
            remap = false)
    private static String settledlands$selfTarget(String command, String regex, String replacement) {
        return NpcSelfTarget292.apply(command, regex, replacement);
    }

    /** The same shortcut for a command that runs from a generic entity. */
    @Redirect(method = "commandForEntity(Lnet/minecraft/world/entity/Entity;Ldev/settledlands/npc/GuideNpc;ILjava/lang/String;)Z",
            at = @At(value = "INVOKE",
                    target = "Ljava/lang/String;replaceFirst(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"),
            remap = false)
    private static String settledlands$selfTargetForEntity(String command, String regex, String replacement) {
        return NpcSelfTarget292.apply(command, regex, replacement);
    }
}
