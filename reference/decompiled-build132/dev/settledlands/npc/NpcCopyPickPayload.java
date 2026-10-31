package dev.settledlands.npc;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-authoritative NPC copy request (replaces vanilla client-side pick-block fabrication). */
public record NpcCopyPickPayload(int entityId) implements CustomPacketPayload {
    public static final Type<NpcCopyPickPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("settledlands", "npc_copy_pick"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NpcCopyPickPayload> CODEC = StreamCodec.of(
        (buf, p) -> buf.writeVarInt(p.entityId),
        buf -> new NpcCopyPickPayload(buf.readVarInt())
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToServer(TYPE, CODEC, (payload, ctx) ->
            ctx.enqueueWork(() -> {
                if (ctx.player() instanceof ServerPlayer player) {
                    handle(player, payload);
                }
            }));
    }

    private static void handle(ServerPlayer player, NpcCopyPickPayload payload) {
        if (!player.hasPermissions(2)) {
            player.displayClientMessage(Component.translatable("message.settledlands.cfg.noperm"), true);
            return;
        }
        if (!(player.level().getEntity(payload.entityId()) instanceof GuideNpc npc) || !npc.isAlive()) return;
        ItemStack stack = buildCopyEgg(npc);
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
        player.containerMenu.broadcastChanges();
        String npcName = npc.profile() != null && npc.profile().name != null && !npc.profile().name.isEmpty()
            ? npc.profile().name
            : npc.getType().getDescription().getString();
        player.displayClientMessage(Component.translatable("message.settledlands.npc.copied", Component.literal(npcName)), true);
    }

    public static ItemStack buildCopyEgg(GuideNpc npc) {
        ItemStack stack = new ItemStack(NpcContent.spawnEgg());
        CompoundTag guide = new CompoundTag();
        guide.putUUID("sl_copy_src", npc.getUUID());
        CompoundTag entityTag = new CompoundTag();
        entityTag.put("Guide", guide);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(entityTag));
        String npcName = npc.profile() != null && npc.profile().name != null && !npc.profile().name.isEmpty()
            ? npc.profile().name
            : npc.getType().getDescription().getString();
        stack.set(DataComponents.CUSTOM_NAME, Component.translatable("item.settledlands.guide_spawn_egg.copy", Component.literal(npcName)));
        return stack;
    }
}
