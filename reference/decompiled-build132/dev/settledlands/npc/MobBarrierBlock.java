package dev.settledlands.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class MobBarrierBlock extends Block {
   public MobBarrierBlock(Properties var1) {
      super(var1);
   }

   protected VoxelShape getCollisionShape(BlockState var1, BlockGetter var2, BlockPos var3, CollisionContext var4) {
      if (!(var4 instanceof EntityCollisionContext var5)) {
         return Shapes.block();
      } else {
         Entity var6 = var5.getEntity();
         return var6 != null && !(var6 instanceof Enemy) ? Shapes.empty() : Shapes.block();
      }
   }

   protected RenderShape getRenderShape(BlockState var1) {
      return RenderShape.INVISIBLE;
   }

   protected float getShadeBrightness(BlockState var1, BlockGetter var2, BlockPos var3) {
      return 1.0F;
   }

   protected boolean propagatesSkylightDown(BlockState var1, BlockGetter var2, BlockPos var3) {
      return true;
   }

   public void animateTick(BlockState var1, Level var2, BlockPos var3, RandomSource var4) {
      for (Player var7 : var2.players()) {
         if (var7.getMainHandItem().is(this.asItem()) || var7.getOffhandItem().is(this.asItem())) {
            var2.addParticle(new BlockParticleOption(ParticleTypes.BLOCK_MARKER, var1), var3.getX() + 0.5, var3.getY() + 0.5, var3.getZ() + 0.5, 0.0, 0.0, 0.0);
            break;
         }
      }
   }
}
