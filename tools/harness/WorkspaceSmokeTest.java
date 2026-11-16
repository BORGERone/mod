import dev.settledlands.npc.NpcDialogueGraph;
import dev.settledlands.npc.NpcProfile;
import dev.settledlands.npc.client.NpcWorkspace306;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

public class WorkspaceSmokeTest {
    static int failures = 0;

    static void step(String what, Runnable body) {
        try {
            body.run();
            System.out.println("ok    " + what);
        } catch (Throwable t) {
            failures++;
            System.out.println("FAIL  " + what + " -> " + t);
            t.printStackTrace(System.out);
        }
    }

    public static void main(String[] args) throws Exception {
        Minecraft.getInstance().font = new Font();

        NpcProfile profile = new NpcProfile();
        NpcDialogueGraph graph = new NpcDialogueGraph();
        profile.dialogueGraph = graph;
        NpcDialogueGraph.Node[] nodes = new NpcDialogueGraph.Node[15];
        for (int type = 0; type <= 14; type++) {
            nodes[type] = graph.add(type, 60 * type, 40 * (type % 4));
        }
        graph.connect(0, 1);
        graph.connect(1, 2);
        System.out.println("nodes: " + graph.nodes.size() + " entry=" + graph.entry);
        if (nodes[3] != null) {
            nodes[3].falseNext = 4;
            nodes[3].varInputNode = 0;
        }
        if (nodes[13] != null) {
            nodes[13].randomBranches.clear();
            for (int i = 0; i < 3; i++) nodes[13].randomBranches.add(5 + i);
        }
        nodes[1].choices.add(6);
        nodes[2].triggers.add(7);
        nodes[0].text = "Здравствуй, путник. Я ждал тебя очень долго и хочу рассказать историю.";
        nodes[11].moveCoords = "~10 ~ ~5";

        final NpcWorkspace306[] screen = new NpcWorkspace306[1];
        step("construct", () -> screen[0] = new NpcWorkspace306(42, profile, null, -1));
        Screen s = screen[0];
        s.width = 1280;
        s.height = 720;
        Method init = Screen.class.getDeclaredMethod("init");
        init.setAccessible(true);
        step("init", () -> {
            try {
                init.invoke(screen[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        step("init again (resize)", () -> {
            try {
                init.invoke(screen[0]);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        GuiGraphics graphics = new GuiGraphics();
        step("render", () -> screen[0].render(graphics, 400, 300, 0.0F));
        step("render over library", () -> screen[0].render(graphics, 60, 200, 0.0F));
        step("render over top bar", () -> screen[0].render(graphics, 200, 10, 0.0F));
        step("render far off canvas", () -> screen[0].render(graphics, -50, 2000, 0.0F));

        step("graph.save", () -> {
            CompoundTag tag = screen[0] == null ? null : new CompoundTag();
            if (tag != null) tag.put("DialogueGraph", graph.save());
        });

        step("click empty canvas", () -> screen[0].mouseClicked(700.0, 500.0, 0));
        step("drag box", () -> screen[0].mouseDragged(760.0, 560.0, 0, 0.0, 0.0));
        step("release box", () -> screen[0].mouseReleased(760.0, 560.0, 0));
        step("click node", () -> screen[0].mouseClicked(160.0, 130.0, 0));
        step("drag node", () -> screen[0].mouseDragged(240.0, 200.0, 0, 0.0, 0.0));
        step("release node", () -> screen[0].mouseReleased(240.0, 200.0, 0));
        step("pan middle", () -> screen[0].mouseClicked(700.0, 400.0, 2));
        step("pan middle drag", () -> screen[0].mouseDragged(760.0, 460.0, 2, 0.0, 0.0));
        step("pan release", () -> screen[0].mouseReleased(760.0, 460.0, 2));
        step("scroll canvas", () -> screen[0].mouseScrolled(700.0, 400.0, 0.0, 1.0));
        step("scroll library", () -> screen[0].mouseScrolled(60.0, 200.0, 0.0, -1.0));
        step("click library row", () -> screen[0].mouseClicked(60.0, 78.0, 0));
        step("click search field", () -> screen[0].mouseClicked(70.0, 52.0, 0));
        step("type in search", () -> screen[0].charTyped('у', 0));
        step("backspace in search", () -> screen[0].keyPressed(259, 0, 0));
        step("escape search", () -> screen[0].keyPressed(256, 0, 0));
        step("key L", () -> screen[0].keyPressed(76, 0, 0));
        step("key L again", () -> screen[0].keyPressed(76, 0, 0));
        step("undo", () -> screen[0].keyPressed(90, 0, 2));
        step("redo", () -> screen[0].keyPressed(89, 0, 2));
        step("delete key", () -> screen[0].keyPressed(261, 0, 0));
        step("tab walk", () -> screen[0].mouseClicked(200.0, 12.0, 0));
        step("render walk tab", () -> screen[0].render(graphics, 400, 300, 0.0F));
        step("click empty tab button", () -> screen[0].mouseClicked(640.0, 360.0, 0));
        step("render after create", () -> screen[0].render(graphics, 400, 300, 0.0F));
        step("tab anim", () -> screen[0].mouseClicked(300.0, 12.0, 0));
        step("tab main", () -> screen[0].mouseClicked(160.0, 12.0, 0));
        step("render main again", () -> screen[0].render(graphics, 400, 300, 0.0F));
        step("right click canvas", () -> screen[0].mouseClicked(700.0, 400.0, 1));

        System.out.println(failures == 0 ? "ALL OK" : ("FAILURES: " + failures));
        if (failures != 0) System.exit(1);
    }
}
