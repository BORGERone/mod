import dev.settledlands.npc.CutsceneFadeControl263;
import dev.settledlands.npc.CutsceneFadeGraphData263;
import dev.settledlands.npc.CutsceneStorage;
import dev.settledlands.npc.NpcDialogueGraph;
import java.util.UUID;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

public final class CutsceneFadeControlProbe263 {
    private static void check(String label, boolean condition) {
        if (!condition) throw new AssertionError(label);
    }

    private static CutsceneFadePayloadView packet(int index) {
        CustomPacketPayload payload = PacketDistributor.SENT_TO_CLIENT.get(index);
        if (payload == null || !payload.getClass().getName().equals("dev.settledlands.npc.CutsceneFadePayload263")) {
            throw new AssertionError("missing fade payload at " + index + ": " + payload);
        }
        try {
            return new CutsceneFadePayloadView((boolean) payload.getClass().getMethod("fadeOut").invoke(payload),
                    (boolean) payload.getClass().getMethod("fadeIn").invoke(payload));
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private record CutsceneFadePayloadView(boolean fadeOut, boolean fadeIn) {}

    public static void main(String[] args) {
        MinecraftServer server = new MinecraftServer();
        CutsceneStorage storage = CutsceneStorage.get(server);
        storage.add("opening");
        storage.add("middle");
        storage.add("ending");
        ServerPlayer player = new ServerPlayer(server, UUID.randomUUID());

        NpcDialogueGraph graph = new NpcDialogueGraph();
        NpcDialogueGraph.Node opening = graph.add(1, NpcDialogueGraph.CUTSCENE);
        NpcDialogueGraph.Node middle = graph.add(2, NpcDialogueGraph.CUTSCENE);
        NpcDialogueGraph.Node blank = graph.add(3, NpcDialogueGraph.CUTSCENE);
        NpcDialogueGraph.Node ending = graph.add(4, NpcDialogueGraph.CUTSCENE);
        opening.cutsceneName = "opening";
        opening.next = middle.id;
        middle.cutsceneName = "middle";
        middle.next = blank.id;
        blank.next = ending.id;
        ending.cutsceneName = "ending";
        CutsceneFadeGraphData263.setFadeOut(opening, true);
        CutsceneFadeGraphData263.setFadeIn(middle, true);
        CutsceneFadeGraphData263.setFadeOut(ending, true);
        CutsceneFadeGraphData263.setFadeIn(ending, true);

        CutsceneFadeControl263.prepareInitial(player, graph, opening);
        check("initial scene payload", packet(0).equals(new CutsceneFadePayloadView(true, false)));
        CutsceneFadeControl263.prepareChained(player, "middle");
        check("first chained scene payload", packet(1).equals(new CutsceneFadePayloadView(false, true)));
        CutsceneFadeControl263.prepareChained(player, "ending");
        check("blank cutscene node is skipped without misaligning chain", packet(2).equals(new CutsceneFadePayloadView(true, true)));
        check("one packet is sent per playable scene", PacketDistributor.SENT_TO_CLIENT.size() == 3);

        PacketDistributor.SENT_TO_CLIENT.clear();
        CutsceneFadeControl263.prepareInitial(player, graph, opening);
        CutsceneFadeControl263.prepareChained(player, "unexpected");
        CutsceneFadeControl263.prepareChained(player, "middle");
        check("mismatched chain clears stale settings", PacketDistributor.SENT_TO_CLIENT.size() == 1);

        PacketDistributor.SENT_TO_CLIENT.clear();
        storage.remove("ending");
        CutsceneFadeControl263.prepareInitial(player, graph, opening);
        CutsceneFadeControl263.prepareChained(player, "middle");
        CutsceneFadeControl263.prepareChained(player, "ending");
        check("missing chained scene receives no fade payload", PacketDistributor.SENT_TO_CLIENT.size() == 2);
        System.out.println("CUTSCENE FADE CONTROL 263: PASS");
    }
}
