package dev.settledlands.npc;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.List;
import java.util.TreeSet;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Route lookup is a local loaded-entity query performed only on command/suggestion. */
@EventBusSubscriber(modid = "settledlands")
public final class NpcEscortCommands {
    private static final WeakHashMap<ServerPlayer, Integer> LAST_START = new WeakHashMap<>();
    private NpcEscortCommands() {}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("go")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .then(Commands.argument("route", StringArgumentType.word())
                .suggests((context, builder) -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    String prefix = builder.getRemaining();
                    TreeSet<String> codes = new TreeSet<>();
                    for (GuideNpc npc : nearby(player)) {
                        String code = npc.profile().escortCode;
                        if (EscortRules.validCode(code) && code.startsWith(prefix)) codes.add(code);
                    }
                    codes.forEach(builder::suggest);
                    return builder.buildFuture();
                })
                .executes(context -> startNearby(context.getSource().getPlayerOrException(),
                    StringArgumentType.getString(context, "route")) ? 1 : 0)));
    }

    private static List<GuideNpc> nearby(ServerPlayer player) {
        return player.serverLevel().getEntitiesOfClass(GuideNpc.class, player.getBoundingBox().inflate(8.0),
            npc -> npc.isAlive() && !npc.isRemoved() && player.distanceToSqr(npc) <= EscortRules.MAX_DISTANCE_SQ);
    }

    public static boolean startNearby(ServerPlayer player, String code) {
        if (!validRequest(player, code)) return false;
        GuideNpc match = null;
        for (GuideNpc candidate : nearby(player)) {
            if (!code.equals(candidate.profile().escortCode)) continue;
            if (match != null) return fail(player, "ambiguous", code);
            match = candidate;
        }
        return match == null ? fail(player, "notfound", code) : startValidated(player, match, code);
    }

    /** Dialogue commands/triggers pass their actual NPC, not a nearest-NPC guess. */
    public static boolean start(ServerPlayer player, GuideNpc npc, String code) {
        if (!validRequest(player, code)) return false;
        return startValidated(player, npc, code);
    }

    private static boolean validRequest(ServerPlayer player, String code) {
        if (player == null || !player.isAlive() || player.isSpectator()) return false;
        if (!EscortRules.validCode(code)) return fail(player, "badcode");
        int now = player.server.getTickCount();
        Integer last = LAST_START.get(player);
        if (last != null && now - last < 5) return false; // At most four command starts/player/second.
        LAST_START.put(player, now);
        return true;
    }

    private static boolean startValidated(ServerPlayer player, GuideNpc npc, String code) {
        if (npc == null || npc.level() != player.level() || !npc.isAlive() || npc.isRemoved()
            || player.distanceToSqr(npc) > EscortRules.MAX_DISTANCE_SQ) return fail(player, "notfound", code);
        if (!code.equals(npc.profile().escortCode)) return fail(player, "mismatch", code);
        if (npc.isEscorting()) {
            if (npc.escort().belongsTo(player) && npc.escort().route().equals(code)) return true; // Idempotent, no timer reset.
            return fail(player, "busy");
        }
        var target = NpcEscort.parseCoordinates(npc.profile().escortCoordinates,
            npc.level().getMinBuildHeight(), npc.level().getMaxBuildHeight());
        if (target == null || !npc.level().getWorldBorder().isWithinBounds(target)) return fail(player, "badcoords");
        npc.beginEscort(player, code, target);
        player.displayClientMessage(Component.translatable("message.settledlands.escort.started", npc.getName(), code), true);
        return true;
    }

    private static boolean fail(ServerPlayer player, String key, Object... arguments) {
        if (player != null) player.displayClientMessage(Component.translatable("message.settledlands.escort." + key, arguments), true);
        return false;
    }
}
