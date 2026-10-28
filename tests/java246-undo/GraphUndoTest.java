import java.lang.reflect.*;import java.util.*;import dev.settledlands.npc.*;import dev.settledlands.npc.client.NpcGraphCanvas;
public class GraphUndoTest {
 static void check(boolean v,String m){if(!v)throw new AssertionError(m);}static Field f(Class<?> c,String n)throws Exception{Field x=c.getDeclaredField(n);x.setAccessible(true);return x;}static Method m(Class<?> c,String n,Class<?>...p)throws Exception{Method x=c.getDeclaredMethod(n,p);x.setAccessible(true);return x;}
 public static void main(String[] args)throws Exception {
  Class<?> c=Class.forName("dev.settledlands.npc.client.NpcDialogueGraphScreen");Object s=c.getConstructor().newInstance();NpcDialogueGraph g=new NpcDialogueGraph();NpcDialogueGraph.Node start=g.add(0,0,0);g.entry=start.id;check(NpcStatsGraph.ensure(g),"create");
  NpcGraphCanvas canvas=new NpcGraphCanvas();canvas.bounds(0,0,800,500);canvas.panX=canvas.panY=0;canvas.zoom=1;f(c,"graph").set(s,g);f(c,"graphs").set(s,new NpcDialogueGraph[]{g,null,null});f(c,"canvas").set(s,canvas);f(c,"undo").set(s,new ArrayDeque<NpcDialogueGraph>());f(c,"redo").set(s,new ArrayDeque<NpcDialogueGraph>());f(c,"summaries").set(s,new HashMap<>());f(c,"selected").setInt(s,-1);f(c,"importError").set(s,"");
  Method click=c.getMethod("mouseClicked",double.class,double.class,int.class),history=m(c,"history",boolean.class);NpcDialogueGraph.Node n=NpcStatsGraph.find(g);
  check((Boolean)click.invoke(s,canvas.screenX(n.x+260),canvas.screenY(n.y+38),0)&&NpcStatsGraph.health(g)==11,"spinner");check(f(c,"dirty").getBoolean(s)&&f(c,"revision").getInt(s)==1,"dirty");
  history.invoke(s,false);g=(NpcDialogueGraph)f(c,"graph").get(s);check(NpcStatsGraph.health(g)==10&&((NpcDialogueGraph[])f(c,"graphs").get(s))[0]==g,"real undo");history.invoke(s,true);g=(NpcDialogueGraph)f(c,"graph").get(s);check(NpcStatsGraph.health(g)==11,"real redo");
  history.invoke(s,false);g=(NpcDialogueGraph)f(c,"graph").get(s);n=NpcStatsGraph.find(g);click.invoke(s,canvas.screenX(n.x+260),canvas.screenY(n.y+38),0);check(((Deque<?>)f(c,"redo").get(s)).isEmpty(),"new click clears redo");
  for(int i=0;i<30;i++)click.invoke(s,canvas.screenX(n.x+260),canvas.screenY(n.y+38),0);check(((Deque<?>)f(c,"undo").get(s)).size()==24,"history limit");
  NpcDialogueGraph full=new NpcDialogueGraph();check(NpcStatsGraph.ensure(full),"reserved");for(int i=0;i<2047;i++)check(full.add(0,i,0)!=null,"ordinary "+i);
  f(c,"graph").set(s,full);((NpcDialogueGraph[])f(c,"graphs").get(s))[0]=full;Method add=m(c,"addAt",int.class,double.class,double.class);
  check(add.invoke(s,0,0.0,0.0)!=null&&full.nodes.size()==2049,"editor last ordinary slot");check(add.invoke(s,0,0.0,0.0)==null&&full.nodes.size()==2049,"editor overflow");
  NpcDialogueGraph oldFull=new NpcDialogueGraph();for(int i=0;i<2048;i++)check(oldFull.add(0,i,0)!=null,"legacy "+i);f(c,"graph").set(s,oldFull);((NpcDialogueGraph[])f(c,"graphs").get(s))[0]=oldFull;check(add.invoke(s,0,0.0,0.0)==null&&oldFull.nodes.size()==2048,"legacy limit");
  System.out.println("Real spinner, screen undo/redo and editor size limit: PASS");
 }
}
