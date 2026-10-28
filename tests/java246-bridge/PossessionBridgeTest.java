import java.util.UUID;import dev.settledlands.npc.*;import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.ServerPlayer;import net.minecraft.world.level.Level;import net.minecraft.core.Holder;import net.minecraft.world.entity.ai.attributes.*;
public class PossessionBridgeTest{
 static void ck(boolean x,String m){if(!x)throw new AssertionError(m);}static void near(double a,double b,String m){if(Math.abs(a-b)>0.0001)throw new AssertionError(m+" "+a+" != "+b);}
 public static void main(String[]a){
  Level l=new Level(false,new MinecraftServer());ServerPlayer p=new ServerPlayer(UUID.randomUUID(),l,20);GuideNpc n=new GuideNpc(l);n.profile=new NpcProfile();n.profile.dialogueGraph.h=11;n.profile.dialogueGraph.d=12;n.profile.dialogueGraph.s=9;
  p.getPersistentData().putInt("settledlands_body_wear_count",3);p.getPersistentData().putBoolean("settledlands_body_wear_active",true);
  AttributeInstance hp=p.getAttribute(Attributes.MAX_HEALTH);hp.addOrReplacePermanentModifier(new AttributeModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("othermod","bonus"),10,AttributeModifier.Operation.ADD_VALUE));BodyWear.sync(p);near(p.getMaxHealth(),14,"before");
  ck(PossessionSystem.cult(n)==0,"unmarked local cult is zero");GuideDialogue.addVarValue(p,n,"культ",false,true,1);ck(PossessionSystem.cult(n)==1,"graph-set local cult is one");
  PossessionSystem.settledlands$possess(p,n);ck(n.removed&&p.getPersistentData().getBoolean("settledlands_possess"),"real bridge possesses");ck(p.getPersistentData().getInt("settledlands_body_wear_count")==0,"new NPC resets wear");
  ck(p.getPersistentData().getInt("settledlands_body_stats_health")==11&&p.getPersistentData().getInt("settledlands_body_stats_damage")==12&&p.getPersistentData().getInt("settledlands_body_stats_speed")==9,"NPC stats copied");near(p.getMaxHealth(),32,"health incl foreign modifier");near(p.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),9,"speed");ck(hp.getModifier(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("othermod","bonus"))!=null,"foreign health preserved");
  System.out.println("Real possession bridge: local cult source, reset wear, apply selected NPC stats: PASS");
 }}
