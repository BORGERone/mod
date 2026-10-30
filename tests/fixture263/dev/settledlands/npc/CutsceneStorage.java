package dev.settledlands.npc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;

public final class CutsceneStorage {
    private final Map<String, List<double[]>> scenes = new HashMap<>();
    public static CutsceneStorage get(MinecraftServer server) { return server.cutscenes; }
    public List<double[]> scene(String name) { return scenes.get(name); }
    public void add(String name) { scenes.put(name, List.of(new double[3], new double[3])); }
    public void remove(String name) { scenes.remove(name); }
}
