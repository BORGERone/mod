import java.util.UUID;import dev.settledlands.npc.*;import net.minecraft.server.MinecraftServer;import net.minecraft.server.level.*;import net.minecraft.resources.*;import net.minecraft.world.entity.*;import net.minecraft.world.entity.ai.attributes.*;import net.minecraft.world.damagesource.DamageSource;import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;import net.neoforged.neoforge.event.entity.player.PlayerEvent;
public class BodyStatsTest{
 static void ck(boolean x,String m){if(!x)throw new AssertionError(m);}static void near(double a,double b,String m){if(Math.abs(a-b)>0.0001)throw new AssertionError(m+" "+a+" != "+b);}
 static ResourceLocation id(String path){return ResourceLocation.fromNamespaceAndPath("othermod",path);}static void add(AttributeInstance a,String k,double v,AttributeModifier.Operation op){a.addOrReplacePermanentModifier(new AttributeModifier(id(k),v,op));}
 public static void main(String[] args){
  ServerLevel l=new ServerLevel(new ResourceKey(),new MinecraftServer());ServerPlayer p=new ServerPlayer(UUID.randomUUID(),l,20);GuideNpc n=new GuideNpc();
  AttributeInstance hp=p.getAttribute(Attributes.MAX_HEALTH),speed=p.getAttribute(Attributes.MOVEMENT_SPEED);near(p.getMaxHealth(),20,"baseline hearts");
  NpcBodyStats.activate(p,n);near(p.getMaxHealth(),20,"health 10 equals 20 HP");near(speed.getValue(),10,"speed 10 no change");NpcBodyStats.deactivate(p);
  n.profile.dialogueGraph.h=11;n.profile.dialogueGraph.d=11;n.profile.dialogueGraph.s=11;add(hp,"flat",10,AttributeModifier.Operation.ADD_VALUE);add(speed,"flat",5,AttributeModifier.Operation.ADD_VALUE);add(speed,"double",1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
  NpcBodyStats.activate(p,n);PossessionSystem.possessed=true;near(p.getMaxHealth(),32,"health 11 adds 2 to final");near(speed.getValue(),33,"speed 11 multiplies final incl bonus");
  LivingIncomingDamageEvent e=new LivingIncomingDamageEvent(p,new DamageSource(p),40);NpcBodyStats.onIncomingDamage(e);near(e.amount,44,"damage 11 incl weapon bonus");
  e=new LivingIncomingDamageEvent(p,new DamageSource(new Entity()),40);NpcBodyStats.onIncomingDamage(e);near(e.amount,40,"other attacker unchanged");
  p.getPersistentData().putInt("settledlands_body_wear_count",1);p.getPersistentData().putBoolean("settledlands_body_wear_active",true);BodyWear.sync(p);near(p.getMaxHealth(),28,"wear after stats");
  add(hp,"multiplier",0.5,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);NpcBodyStats.attributeDirty(p,Attributes.MAX_HEALTH);near(p.getMaxHealth(),43,"external total bonus and absolute wear");
  hp.removeModifier(id("flat"));NpcBodyStats.attributeDirty(p,Attributes.MAX_HEALTH);near(p.getMaxHealth(),28,"stat and wear recompute on foreign change");
  p.setHealth(25);NpcBodyStats.deactivate(p);near(p.getMaxHealth(),26,"self exit removes only health stat");near(speed.getValue(),30,"self exit removes speed stat");ck(hp.getModifier(id("multiplier"))!=null&&speed.getModifier(id("double"))!=null,"foreign modifiers survive");
  e=new LivingIncomingDamageEvent(p,new DamageSource(p),40);NpcBodyStats.onIncomingDamage(e);near(e.amount,40,"inactive damage unchanged");
  BodyWear.reset(p);n.profile.dialogueGraph.h=9;n.profile.dialogueGraph.d=9;n.profile.dialogueGraph.s=9;NpcBodyStats.activate(p,n);near(p.getMaxHealth(),28,"health 9 minus two from final");near(speed.getValue(),27,"speed 9 90 percent final");e=new LivingIncomingDamageEvent(p,new DamageSource(p),40);NpcBodyStats.onIncomingDamage(e);near(e.amount,36,"damage 9 90 percent");
  ServerPlayer respawn=new ServerPlayer(p.getUUID(),l,20);NpcBodyStats.clonePlayer(new PlayerEvent.Clone(p,respawn));ck(respawn.getPersistentData().getInt("settledlands_body_stats_health")==9&&!respawn.getPersistentData().getBoolean("settledlands_body_stats_active"),"death clone stores inactive values");near(respawn.getMaxHealth(),20,"dead body stats inactive");NpcBodyStats.resume(respawn);near(respawn.getMaxHealth(),18,"paid return applies health");near(respawn.getAttribute(Attributes.MOVEMENT_SPEED).getValue(),9,"paid return applies speed");
  NpcBodyStats.deactivate(respawn);near(respawn.getMaxHealth(),20,"clone self exit clears");
  System.out.println("Actual NPC body stats: final damage/speed, hearts, wear, external modifiers and clone: PASS");
 }}
