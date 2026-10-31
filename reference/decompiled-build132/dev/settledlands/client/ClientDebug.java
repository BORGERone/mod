package dev.settledlands.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.settledlands.Cell;
import dev.settledlands.DebugPayload;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font.DisplayMode;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage;

@EventBusSubscriber(
   modid = "settledlands",
   value = {Dist.CLIENT}
)
public final class ClientDebug {
   private static DebugPayload latest;
   private static long received;

   public static void receive(DebugPayload packet) {
      latest = packet;
      received = System.nanoTime();
   }

   @SubscribeEvent
   public static void logout(LoggingOut e) {
      latest = null;
   }

   @SubscribeEvent
   public static void render(RenderLevelStageEvent e) {
      if (e.getStage() == Stage.AFTER_PARTICLES) {
         Minecraft mc = Minecraft.getInstance();
         DebugPayload data = latest;
         if (data != null
            && mc.level != null
            && mc.player != null
            && System.nanoTime() - received <= 5000000000L
            && mc.level.dimension().location().toString().equals(data.dimension())) {
            PoseStack pose = e.getPoseStack();
            Vec3 camera = e.getCamera().getPosition();
            BufferSource buffers = mc.renderBuffers().bufferSource();
            pose.pushPose();
            pose.translate(-camera.x, -camera.y, -camera.z);
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());

            for (DebugPayload.Entry cell : data.cells()) {
               BlockPos p = Cell.min(cell.key());
               AABB box = new AABB(p.getX() + 0.04, p.getY() + 0.04, p.getZ() + 0.04, p.getX() + 15.96, p.getY() + 7.96, p.getZ() + 15.96);
               if (e.getFrustum().isVisible(box)) {
                  float ratio = Math.clamp(cell.score() / data.tempMax(), 0.0F, 1.0F);
                  float r = cell.permanent() ? 0.15F : 1.0F - ratio;
                  float g = cell.permanent() ? 0.8F : Math.max(0.2F, ratio);
                  float b = cell.permanent() ? 1.0F : 0.15F;
                  if (cell.banners() > 0 && !cell.permanent()) {
                     r = 0.8F;
                     g = 0.3F;
                     b = 1.0F;
                  }

                  LevelRenderer.renderLineBox(pose, lines, box, r, g, b, 0.8F);
               }
            }

            pose.popPose();
            buffers.endBatch(RenderType.lines());
            int labels = 0;

            for (DebugPayload.Entry cellx : data.cells()) {
               BlockPos p = Cell.min(cellx.key());
               double x = p.getX() + 8;
               double y = p.getY() + 4;
               double z = p.getZ() + 8;
               if (!(camera.distanceToSqr(x, y, z) > 2304.0)
                  && labels < 100
                  && e.getFrustum().isVisible(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 16, p.getY() + 8, p.getZ() + 16))) {
                  labels++;
                  String text = cellx.permanent()
                     ? "PERMANENT"
                     : String.format(
                        Locale.ROOT,
                        "T %.0f/%d | P %d/%d | H %d%%",
                        cellx.score(),
                        data.tempMax(),
                        cellx.kills(),
                        data.permanentMax(),
                        Math.min(100L, 100L * cellx.habitation() / data.habitationMax())
                     );
                  if (cellx.banners() > 0) {
                     text = text + " | Святость I x" + cellx.banners();
                  }

                  if (cellx.fireBanners() > 0) {
                     text = text + " | Святость II (огонь) x" + cellx.fireBanners();
                  }

                  pose.pushPose();
                  pose.translate(x - camera.x, y - camera.y, z - camera.z);
                  pose.mulPose(e.getCamera().rotation());
                  pose.scale(0.025F, -0.025F, 0.025F);
                  mc.font
                     .drawInBatch(
                        text,
                        -mc.font.width(text) / 2.0F,
                        0.0F,
                        cellx.permanent() ? -11149825 : -1,
                        false,
                        pose.last().pose(),
                        buffers,
                        DisplayMode.SEE_THROUGH,
                        -2013265920,
                        15728880
                     );
                  pose.popPose();
               }
            }

            buffers.endBatch();
         }
      }
   }
}
