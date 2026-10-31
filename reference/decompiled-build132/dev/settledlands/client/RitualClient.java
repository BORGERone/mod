package dev.settledlands.client;

import dev.settledlands.ritual.RitualAnimation;
import dev.settledlands.ritual.RitualPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.ClientTickEvent.Post;
import org.joml.Vector3f;

@EventBusSubscriber(
   modid = "settledlands",
   value = {Dist.CLIENT}
)
public final class RitualClient {
   private static RitualPayload current;
   private static long receivedTick;

   public static void receive(RitualPayload payload) {
      ClientLevel level = Minecraft.getInstance().level;
      if (level != null) {
         if (!payload.active()) {
            if (current != null && current.zombie().equals(payload.zombie())) {
               current = null;
            }
         } else if (level.dimension().location().toString().equals(payload.dimension())) {
            current = payload;
            receivedTick = level.getGameTime();
         }
      }
   }

   public static double age(Entity entity, float renderAge) {
      ClientLevel level = Minecraft.getInstance().level;
      if (current != null && level != null && current.zombie().equals(entity.getUUID()) && current.dimension().equals(level.dimension().location().toString())) {
         long elapsed = level.getGameTime() - receivedTick;
         if (elapsed >= 0L && elapsed <= 60L) {
            double t = (float)(current.age() + elapsed) + Math.clamp(renderAge - entity.tickCount, 0.0F, 1.0F);
            return t < 140.0 ? t : -1.0;
         } else {
            return -1.0;
         }
      } else {
         return -1.0;
      }
   }

   public static void reset(HumanoidModel<?> m) {
      m.head.resetPose();
      m.hat.resetPose();
      m.body.resetPose();
      m.rightArm.resetPose();
      m.leftArm.resetPose();
      m.rightLeg.resetPose();
      m.leftLeg.resetPose();
   }

   public static void apply(HumanoidModel<?> model, Entity entity, float renderAge) {
      double t = age(entity, renderAge);
      if (!(t < 0.0)) {
         reset(model);
         part(model.head, "head", t);
         part(model.body, "body", t);
         part(model.rightArm, "right_arm", t);
         part(model.leftArm, "left_arm", t);
         part(model.rightLeg, "right_leg", t);
         part(model.leftLeg, "left_leg", t);
         model.hat.copyFrom(model.head);
      }
   }

   private static void part(ModelPart part, String name, double t) {
      double[] r = RitualAnimation.sample(name, "rotation", t);
      double[] p = RitualAnimation.sample(name, "position", t);
      double[] s = RitualAnimation.sample(name, "scale", t);
      part.xRot = (float)Math.toRadians(-r[0]);
      part.yRot = (float)Math.toRadians(r[1]);
      part.zRot = (float)Math.toRadians(-r[2]);
      part.x = (float)(part.x + p[0]);
      part.y = (float)(part.y - p[1]);
      part.z = (float)(part.z + p[2]);
      part.xScale = (float)s[0];
      part.yScale = (float)s[1];
      part.zScale = (float)s[2];
   }

   @SubscribeEvent
   public static void logout(LoggingOut e) {
      current = null;
   }

   @SubscribeEvent
   public static void tick(Post e) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.level != null && current != null && !mc.isPaused()) {
         if (mc.level.dimension().location().toString().equals(current.dimension()) && mc.level.getGameTime() - receivedTick <= 60L) {
            Entity zombie = mc.level.getEntity(current.entityId());
            if (zombie != null && zombie.getUUID().equals(current.zombie())) {
               double age = age(zombie, zombie.tickCount);
               if (!(age < 0.0) && !(age < 40.0)) {
                  Vec3 center = zombie.position().add(0.0, 0.9, 0.0);
                  Vec3 end = Vec3.atCenterOf(current.banner());
                  double phase = age * 0.23;

                  for (int i = 0; i < 6; i++) {
                     double a = phase + i * Math.PI / 3.0;
                     double y = 0.35 + 0.6 * Math.sin(phase * 0.65 + i);
                     DustParticleOptions dust = new DustParticleOptions(new Vector3f(i % 2 == 0 ? 0.55F : 0.2F, 0.35F, 1.0F), 0.8F);
                     mc.level.addParticle(dust, center.x + Math.cos(a) * 0.85, center.y + y, center.z + Math.sin(a) * 0.85, 0.0, 0.01, 0.0);
                  }

                  Vec3 start = center.add(0.0, 0.6, 0.0);
                  Vec3 delta = end.subtract(start);
                  if (mc.level.getGameTime() % 4L == 0L) {
                     int steps = Math.min(48, Math.max(2, (int)(delta.length() * 2.0)));

                     for (int i = 0; i <= steps; i++) {
                        Vec3 point = start.add(delta.scale((double)i / steps));
                        mc.level.addParticle(new DustParticleOptions(new Vector3f(0.35F, 0.7F, 1.0F), 0.65F), point.x, point.y, point.z, 0.0, 0.0, 0.0);
                     }
                  }

                  for (int i = 0; i < 2; i++) {
                     double f = (age * 0.055 + i * 0.5) % 1.0;
                     Vec3 point = start.add(delta.scale(f));
                     mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK, point.x, point.y, point.z, 0.0, 0.0, 0.0);
                  }
               }
            }
         } else {
            current = null;
         }
      }
   }
}
