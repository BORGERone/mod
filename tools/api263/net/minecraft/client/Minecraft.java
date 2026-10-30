package net.minecraft.client;

import java.util.ArrayDeque;
import java.util.Queue;

/** Test-only signature fixture; never bundled into the mod JAR. */
public class Minecraft {
    private static final Minecraft INSTANCE = new Minecraft();
    private final Queue<Runnable> queued = new ArrayDeque<>();
    public static Minecraft getInstance() { return INSTANCE; }
    public void execute(Runnable task) { queued.add(task); }
    public void runQueuedTasks() {
        while (!queued.isEmpty()) queued.remove().run();
    }
}
