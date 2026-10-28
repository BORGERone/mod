import dev.settledlands.npc.*;import dev.settledlands.npc.client.*;import net.minecraft.client.gui.GuiGraphics;
public class GraphUiTest {
 static void check(boolean v,String msg){if(!v)throw new AssertionError(msg);}
 static boolean click(NpcDialogueGraphScreen s,NpcDialogueGraph.Node n,int x,int y,int button){return NpcStatsGraphUi.mouseClicked(s,s.canvas.screenX(n.x+x),s.canvas.screenY(n.y+y),button);}
 public static void main(String[] args){
  NpcDialogueGraph g=new NpcDialogueGraph();NpcDialogueGraph.Node line=g.add(0,0,0);g.entry=line.id;
  NpcGraphCanvas c=new NpcGraphCanvas();c.bounds(0,0,800,500);c.panX=c.panY=0;c.zoom=1;
  NpcDialogueGraphScreen s=new NpcDialogueGraphScreen(g,c);NpcStatsGraphUi.ensure(s);NpcDialogueGraph.Node n=NpcStatsGraph.find(g);check(s.changed==1&&n!=null,"ensure");
  check(c.hit(g,c.screenX(n.x+290),c.screenY(n.y+130))==n.id,"large hit");check(c.hitPort(g,c.screenX(n.x+220),c.screenY(n.y+50),true)==-1,"no port");
  check(click(s,n,260,38,0)&&n.varValue==11&&s.remembered==1&&s.changed==2,"health plus");check(click(s,n,190,72,0)&&n.checkRadius==9&&s.remembered==2,"damage minus");check(click(s,n,260,106,0)&&n.checkCooldown==11,"speed plus");
  n.varValue=100;int saved=s.changed;check(click(s,n,260,38,0)&&n.varValue==100&&s.changed==saved,"max bound");n.varValue=1;check(click(s,n,190,38,0)&&n.varValue==1&&s.changed==saved,"min bound");
  check(click(s,n,10,10,1)&&s.original==0,"right click protected");check(click(s,n,10,10,0)&&s.original==1&&s.hidden==1,"drag delegates");
  s.menu=true;check(click(s,n,260,38,0)&&n.varValue==1&&s.original==2,"menu priority");s.menu=false;
  GuiGraphics gfx=new GuiGraphics();NpcStatsGraphUi.draw(s,gfx,n);check(gfx.rectangles==12&&gfx.words.size()==13&&gfx.pose().depth==0,"draw three rows");NpcStatsGraphUi.draw(s,gfx,line);check(s.drawOriginal==1,"ordinary drawing unchanged");
  c.focus(n);check(Math.abs(c.screenX(n.x+150)-400)<0.001&&Math.abs(c.screenY(n.y+70)-250)<0.001,"focus");c.frameAll(g);check(c.zoom>0&&c.zoom<=1&&c.visible(line)&&c.visible(n),"frame all");
  System.out.println("Real canvas and UI spinners, drawing, selection, limits: PASS");
 }
}
