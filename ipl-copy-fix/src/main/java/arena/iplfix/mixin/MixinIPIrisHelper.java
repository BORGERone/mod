package arena.iplfix.mixin;

import arena.iplfix.IplCopyFixMod;
import arena.iplfix.RenderTargetCopyShim;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reroutes Immersive Portals' Iris compatibility framebuffer copies through the format
 * tolerant shim. Every injection is cancellable and only cancels when the shim succeeded,
 * so the original implementation still runs whenever the shim is unavailable or disabled.
 */
@Mixin(targets = "qouteall.imm_ptl.core.compat.iris_compatibility.IPIrisHelper", remap = false)
public class MixinIPIrisHelper {

    @Inject(
        method = "newCopyDepthStencil(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/pipeline/RenderTarget;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void iplfix$copyDepthStencil(RenderTarget from, RenderTarget to, CallbackInfo ci) {
        if (RenderTargetCopyShim.copyDepthStencil(from, to, true, true)) {
            logFirstUse();
            ci.cancel();
        }
    }

    @Inject(
        method = "copyDepthStencil(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/pipeline/RenderTarget;ZZ)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void iplfix$copyDepthStencilMasked(RenderTarget from, RenderTarget to, boolean copyDepth, boolean copyStencil, CallbackInfo ci) {
        if (RenderTargetCopyShim.copyDepthStencil(from, to, copyDepth, copyStencil)) {
            logFirstUse();
            ci.cancel();
        }
    }

    @Inject(
        method = "copyColor(Lcom/mojang/blaze3d/pipeline/RenderTarget;Lcom/mojang/blaze3d/pipeline/RenderTarget;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void iplfix$copyColor(RenderTarget from, RenderTarget to, CallbackInfo ci) {
        if (RenderTargetCopyShim.copyColor(from, to)) {
            logFirstUse();
            ci.cancel();
        }
    }

    private static boolean logged = false;

    private static void logFirstUse() {
        if (!logged) {
            logged = true;
            IplCopyFixMod.LOGGER.info("Copy shim active: Immersive Portals framebuffer copies rerouted (color via named blit, depth via blit or gl_FragDepth pass).");
        }
    }
}
