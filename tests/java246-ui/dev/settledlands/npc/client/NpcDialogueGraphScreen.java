package dev.settledlands.npc.client;
import dev.settledlands.npc.NpcDialogueGraph;import net.minecraft.client.gui.Font;import net.minecraft.client.gui.GuiGraphics;
public class NpcDialogueGraphScreen {
 public NpcDialogueGraph graph;public NpcGraphCanvas canvas;public int remembered,changed,original,hidden,drawOriginal;public boolean menu,inspector;public NpcUi.NpcDropdown dropdown;
 public NpcDialogueGraphScreen(NpcDialogueGraph g,NpcGraphCanvas c){graph=g;canvas=c;}
 public NpcDialogueGraph settledlands$graph(){return graph;}public NpcGraphCanvas settledlands$canvas(){return canvas;}public Font settledlands$font(){return new Font();}public boolean settledlands$isSelected(int id){return true;}
 public void settledlands$drawOriginal(GuiGraphics g,NpcDialogueGraph.Node n){drawOriginal++;}public void settledlands$changed(){changed++;}public void settledlands$remember(){remembered++;}
 public boolean settledlands$contextOpen(){return menu;}public boolean settledlands$insideInspector(double x,double y){return inspector;}public NpcUi.NpcDropdown activeDropdown(){return dropdown;}
 public boolean settledlands$mouseClickedOriginal(double x,double y,int b){original++;return true;}public void settledlands$hideStatsInspector(){hidden++;}
}
