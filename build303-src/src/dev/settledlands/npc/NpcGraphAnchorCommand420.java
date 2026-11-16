package dev.settledlands.npc;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Build 420: {@code /settledlands anchor} - the anchor, said in words.
 *
 * <p>The automatic pass is the feature; this command is the handle on it. An author who wants the graphs
 * of the build he is standing in written down <b>now</b> (before saving a blueprint, say) runs
 * {@code /settledlands anchor seal}. {@code list} says what an anchor near him carries, {@code here} says
 * where it is and whether this build has one at all.</p>
 *
 * <p>Registered the way the base jar registers its own commands ({@code NpcEscortCommands},
 * {@code CutsceneCommands}): {@code RegisterCommandsEvent}, the dispatcher, {@link Commands#literal}. No
 * new registration path, nothing to keep in step with the loader.</p>
 */
@EventBusSubscriber(modid = "settledlands")
public final class NpcGraphAnchorCommand420 {

    /** A command needs the player: the anchor of a console is a place, and a console has none. */
    private NpcGraphAnchorCommand420() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (event == null || event.getDispatcher() == null) return;
        try {
            LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("settledlands")
                    .then(Commands.literal("anchor")
                            .then(Commands.literal("seal").executes(NpcGraphAnchorCommand420::seal))
                            .then(Commands.literal("here").executes(NpcGraphAnchorCommand420::here))
                            .then(Commands.literal("list").executes(NpcGraphAnchorCommand420::list)));
            event.getDispatcher().register(root);
        } catch (Throwable failure) {
            BlockTrigger351.fail(failure);
        }
    }

    private static int seal(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) return 0;
        BlockPos at = player.blockPosition();
        boolean done = NpcGraphAnchor420.sealHere(player.serverLevel(), at);
        NpcGraphAnchor420.tell(player, done
                ? "якорь: графы этой постройки записаны в маркер рядом с " + at.getX() + " " + at.getY()
                        + " " + at.getZ()
                : "якорь: рядом с " + at.getX() + " " + at.getY() + " " + at.getZ()
                        + " нет графов - записывать нечего");
        return done ? 1 : 0;
    }

    private static int here(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) return 0;
        BlockPos at = player.blockPosition();
        NpcGraphAnchor420.tell(player, "якорь: " + NpcGraphAnchor420.describe(player.serverLevel(), at));
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = player(context);
        if (player == null) return 0;
        for (String line : NpcGraphAnchor420.contents(player.serverLevel(), player.blockPosition())) {
            NpcGraphAnchor420.tell(player, line);
        }
        return 1;
    }

    private static ServerPlayer player(CommandContext<CommandSourceStack> context) {
        if (context == null || context.getSource() == null) return null;
        try {
            return context.getSource().getPlayerOrException();
        } catch (Throwable failure) {
            return null;
        }
    }

    /** Kept for the shape of the other commands: the source's level, when the game hands one out. */
    static ServerLevel level(CommandSourceStack source) {
        return source == null ? null : source.getLevel();
    }

    /** The one-line answer a console gets - no player, so no chat line to write into. */
    static Component line(String text) {
        return Component.literal(text);
    }
}
