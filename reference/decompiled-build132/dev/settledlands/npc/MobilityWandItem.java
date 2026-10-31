package dev.settledlands.npc;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.level.Level;

public class MobilityWandItem extends Item {
   public MobilityWandItem(Properties properties) {
      super(properties);
   }

   @Override
   public InteractionResult useOn(UseOnContext context) {
      Level level = context.getLevel();
      Player player = context.getPlayer();
      if (level.isClientSide()) {
         return InteractionResult.sidedSuccess(true);
      }
      if (!(player instanceof ServerPlayer serverPlayer)) {
         return InteractionResult.CONSUME;
      }
      if (!serverPlayer.hasPermissions(2)) {
         serverPlayer.displayClientMessage(Component.translatable("message.settledlands.mobility.no_permission"), true);
         return InteractionResult.CONSUME;
      }

      ItemStack stack = context.getItemInHand();
      BlockPos target = context.getClickedPos().above();
      NpcMobilityGroups catalog = NpcMobilityGroups.get(serverPlayer.getServer());
      NpcMobilityGroups.AddResult result = catalog.addPoint(stack, target);
      switch (result) {
         case ADDED -> {
            NpcMobilityGroups.Group group = catalog.ensureWandGroup(stack);
            int count = group == null ? 0 : group.points().size();
            serverPlayer.displayClientMessage(
               Component.translatable("message.settledlands.mobility.point_added", target.getX(), target.getY(), target.getZ(), count), true
            );
         }
         case DUPLICATE -> serverPlayer.displayClientMessage(
            Component.translatable("message.settledlands.mobility.point_duplicate"), true
         );
         case FULL -> serverPlayer.displayClientMessage(
            Component.translatable("message.settledlands.mobility.point_limit", NpcProfile.MAX_MOBILITY_POINTS), true
         );
         case NO_GROUP -> serverPlayer.displayClientMessage(
            Component.translatable("message.settledlands.mobility.group_limit"), true
         );
      }
      serverPlayer.containerMenu.broadcastChanges();
      return InteractionResult.CONSUME;
   }

   @Override
   public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
      ItemStack stack = player.getItemInHand(hand);
      if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
         if (!serverPlayer.hasPermissions(2)) {
            serverPlayer.displayClientMessage(Component.translatable("message.settledlands.mobility.no_permission"), true);
         } else {
            NpcMobilityGroups catalog = NpcMobilityGroups.get(serverPlayer.getServer());
            if (catalog.ensureWandGroup(stack) == null) {
               serverPlayer.displayClientMessage(Component.translatable("message.settledlands.mobility.group_limit"), true);
            } else {
               catalog.sendWandView(serverPlayer, stack, hand);
            }
         }
      }
      return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
   }

   public static void assignToNpc(ServerPlayer player, InteractionHand hand, GuideNpc npc) {
      if (!player.hasPermissions(2)) {
         player.displayClientMessage(Component.translatable("message.settledlands.mobility.no_permission"), true);
         return;
      }
      ItemStack stack = player.getItemInHand(hand);
      NpcMobilityGroups catalog = NpcMobilityGroups.get(player.getServer());
      NpcMobilityGroups.Group group = catalog.ensureWandGroup(stack);
      if (group == null) {
         player.displayClientMessage(Component.translatable("message.settledlands.mobility.group_limit"), true);
         return;
      }
      NpcProfile updated = npc.profile().copy();
      if (!catalog.assignProfile(updated, group.id())) {
         return;
      }
      npc.setProfile(updated.sanitized());
      player.displayClientMessage(Component.translatable("message.settledlands.mobility.assigned", group.name()), true);
      npc.sendConfigView(player);
      player.containerMenu.broadcastChanges();
   }

   @Override
   public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
      tooltip.add(Component.translatable("tooltip.settledlands.mobility_wand.use").withStyle(ChatFormatting.GRAY));
      tooltip.add(Component.translatable("tooltip.settledlands.mobility_wand.op").withStyle(ChatFormatting.DARK_GRAY));
   }
}
