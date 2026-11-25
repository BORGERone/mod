package net.neoforged.fml.event.lifecycle;

import java.util.concurrent.CompletableFuture;

public class FMLClientSetupEvent {
    public CompletableFuture<Void> enqueueWork(Runnable work) {
        if (work != null) work.run();
        return CompletableFuture.completedFuture(null);
    }
}
