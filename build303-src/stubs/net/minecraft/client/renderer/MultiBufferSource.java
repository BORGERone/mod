package net.minecraft.client.renderer;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Compile-time shape of the buffer source a render pass hands out. */
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType renderType);
}
