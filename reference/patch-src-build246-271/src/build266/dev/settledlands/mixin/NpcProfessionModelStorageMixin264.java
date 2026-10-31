package dev.settledlands.mixin;

import dev.settledlands.npc.client.NpcProfessionModel264;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "dev.settledlands.model.BbModelStorage", remap = false)
public abstract class NpcProfessionModelStorageMixin264 {
    @Inject(method = "findModelFile(Ljava/lang/String;)Ljava/io/File;", at = @At("HEAD"), cancellable = true, remap = false)
    private static void settledlands$professionModelFile(String name, CallbackInfoReturnable<File> cir) {
        File file = NpcProfessionModel264.materialize(name);
        if (name != null && name.startsWith("ivy_oleander_prof264_p")) {
            System.out.println("[SL-PROFESSION-266] resolve requested=" + name
                    + " result=" + (file == null ? "null" : file.getAbsolutePath())
                    + " exists=" + (file != null && file.isFile())
                    + " bytes=" + (file != null && file.isFile() ? file.length() : -1L));
        }
        if (file != null) cir.setReturnValue(file);
    }
}
