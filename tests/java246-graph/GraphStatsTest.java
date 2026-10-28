import dev.settledlands.npc.*;import net.minecraft.nbt.CompoundTag;
public class GraphStatsTest {
 static void check(boolean x,String s){if(!x)throw new AssertionError(s);}
 public static void main(String[] args){
  NpcDialogueGraph g=new NpcDialogueGraph();NpcDialogueGraph.Node line=g.add(0,30,40);g.entry=line.id;g.enabled=true;
  check(NpcStatsGraph.health(null)==10&&NpcStatsGraph.damage(null)==10&&NpcStatsGraph.speed(null)==10,"legacy defaults");
  check(NpcStatsGraph.clamp(-1)==1&&NpcStatsGraph.clamp(101)==100,"stat bounds");
  check(NpcStatsGraph.ensure(g),"created node");NpcDialogueGraph.Node n=NpcStatsGraph.find(g);
  check(n!=null&&n.x==318&&n.y==40&&n.varValue==10&&n.checkRadius==10&&n.checkCooldown==10,"position and defaults");
  check(g.error().isEmpty(),"valid graph "+g.error());check(!NpcStatsGraph.ensure(g)&&g.add(16,0,0)==null,"one node only");
  check(!g.remove(n.id)&&g.get(n.id)==n,"not deletable");check(!g.connect(n.id,line.id,0)&&!g.connect(line.id,n.id,0),"no wires");
  n.varValue=11;n.checkRadius=9;n.checkCooldown=13;NpcDialogueGraph copy=g.copy();
  check(NpcStatsGraph.health(copy)==11&&NpcStatsGraph.damage(copy)==9&&NpcStatsGraph.speed(copy)==13,"copy snapshot");
  n.varValue=12;check(NpcStatsGraph.health(copy)==11,"copy independent");NpcDialogueGraph loaded=NpcDialogueGraph.load(copy.save());
  check(loaded!=null&&loaded.error().isEmpty()&&NpcStatsGraph.health(loaded)==11,"roundtrip");
  CompoundTag bad=copy.save();bad.getList("Nodes",10).getCompound(1).putInt("VarValue",101);check(NpcDialogueGraph.load(bad)==null,"bad health rejected");
  bad=copy.save();bad.getList("Nodes",10).getCompound(1).putInt("CheckRadius",0);check(NpcDialogueGraph.load(bad)==null,"raw low radius rejected");
  bad=copy.save();bad.getList("Nodes",10).getCompound(1).remove("CheckCooldown");check(NpcDialogueGraph.load(bad)==null,"missing stat rejected");
  bad=copy.save();bad.getList("Nodes",10).getCompound(0).putInt("Next",n.id);check(NpcDialogueGraph.load(bad)==null,"incoming link rejected");
  bad=copy.save();bad.putInt("Entry",n.id);check(NpcDialogueGraph.load(bad)==null,"entry rejected");
  bad=copy.save();bad.getList("Nodes",10).getCompound(1).putInt("Special",1);check(NpcDialogueGraph.load(bad)==null,"special rejected");
  bad=copy.save();CompoundTag clone=copy.save().getList("Nodes",10).getCompound(1);clone.putInt("Id",99);bad.getList("Nodes",10).add(clone);check(NpcDialogueGraph.load(bad)==null,"duplicate rejected");
  NpcDialogueGraph unfinished=new NpcDialogueGraph();unfinished.enabled=true;unfinished.entry=-1;check(NpcDialogueGraph.load(unfinished.save())!=null,"unfinished legacy loads");
  NpcDialogueGraph other=new NpcDialogueGraph();check(NpcStatsGraph.ensure(other)&&NpcStatsGraph.health(other)==10&&NpcStatsGraph.health(copy)==11,"individual per NPC");
  NpcDialogueGraph full=new NpcDialogueGraph();for(int i=0;i<2048;i++)check(full.add(0,i,0)!=null,"legacy node "+i);
  check(full.add(0,0,0)==null&&NpcStatsGraph.ensure(full)&&full.nodes.size()==2049,"reserved slot");
  check(full.error().isEmpty()&&NpcDialogueGraph.load(full.save())!=null,"full plus stats roundtrip");
  check(full.add(0,0,0)==null&&full.add(16,0,0)==null,"full graph limited");
  NpcDialogueGraph statsFirst=new NpcDialogueGraph();check(NpcStatsGraph.ensure(statsFirst),"stats first");for(int i=0;i<2048;i++)check(statsFirst.add(0,i,0)!=null,"ordinary "+i);
  check(statsFirst.nodes.size()==2049&&statsFirst.add(0,0,0)==null&&statsFirst.error().isEmpty(),"ordinary plus stat limit");
  NpcDialogueGraph oversized=new NpcDialogueGraph();for(int i=0;i<2048;i++)check(oversized.add(0,i,0)!=null,"ordinary-only "+i);
  oversized.nodes.put(2048,new NpcDialogueGraph.Node(2048,0,0,0));check(!oversized.error().isEmpty()&&NpcDialogueGraph.load(oversized.save())==null,"ordinary-only overflow rejected");
  System.out.println("Real graph and NBT roundtrip, singleton, security, 2048 limit: PASS");
 }
}
