#!/usr/bin/env python3
"""Execute actual BodilessEffect on a synthetic 1.21 attribute template."""
import argparse,pathlib,subprocess,tempfile,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
SRC={
'net/minecraft/resources/ResourceLocation.java':'''package net.minecraft.resources;public record ResourceLocation(String namespace,String path){public static ResourceLocation fromNamespaceAndPath(String a,String b){return new ResourceLocation(a,b);}}''',
'net/minecraft/core/Holder.java':'''package net.minecraft.core;public interface Holder<T>{}''',
'net/minecraft/world/entity/ai/attributes/Attribute.java':'''package net.minecraft.world.entity.ai.attributes;public class Attribute{}''',
'net/minecraft/world/entity/ai/attributes/Attributes.java':'''package net.minecraft.world.entity.ai.attributes;import net.minecraft.core.Holder;public class Attributes{public static final Holder<Attribute> GRAVITY=new Holder<>(){};}''',
'net/minecraft/world/entity/ai/attributes/AttributeModifier.java':'''package net.minecraft.world.entity.ai.attributes;public class AttributeModifier{public enum Operation{ADD_VALUE,ADD_MULTIPLIED_BASE,ADD_MULTIPLIED_TOTAL}}''',
'net/minecraft/world/entity/ai/attributes/AttributeMap.java':'''package net.minecraft.world.entity.ai.attributes;public class AttributeMap{public double total;public int amplifier=-99;}''',
'net/minecraft/world/effect/MobEffectCategory.java':'''package net.minecraft.world.effect;public enum MobEffectCategory{BENEFICIAL,HARMFUL,NEUTRAL}''',
'net/minecraft/world/effect/MobEffect.java':'''package net.minecraft.world.effect;import net.minecraft.core.Holder;import net.minecraft.resources.ResourceLocation;import net.minecraft.world.entity.ai.attributes.*;public class MobEffect{public Holder<Attribute> target;public ResourceLocation id;public double amount;public AttributeModifier.Operation op;public MobEffect(MobEffectCategory c,int color){}public MobEffect addAttributeModifier(Holder<Attribute> a,ResourceLocation id,double amount,AttributeModifier.Operation op){target=a;this.id=id;this.amount=amount;this.op=op;return this;}public void addAttributeModifiers(AttributeMap m,int amplifier){m.total=amount*(amplifier+1);m.amplifier=amplifier;}}''',
'GravityTest.java':'''import dev.settledlands.npc.BodilessEffect;import net.minecraft.world.effect.MobEffect;import net.minecraft.world.entity.ai.attributes.*;public class GravityTest{static void ck(boolean x,String m){if(!x)throw new AssertionError(m);}public static void main(String[]a){BodilessEffect e=new BodilessEffect();MobEffect m=e;ck(m.target==Attributes.GRAVITY&&m.amount==-0.5&&m.op==AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL&&m.id.toString().contains("bodiless_gravity"),"registered gravity modifier");for(int amp:new int[]{0,1,5,255}){AttributeMap map=new AttributeMap();e.addAttributeModifiers(map,amp);ck(map.amplifier==0&&map.total==-0.5,"fixed half gravity amplifier="+amp);}System.out.println("Actual BodilessEffect registers -50% total gravity at every amplifier: PASS");}}''',
}
def main():
 q=argparse.ArgumentParser();q.add_argument('--java',required=True,type=pathlib.Path);q.add_argument('--jar',required=True,type=pathlib.Path);a=q.parse_args()
 with tempfile.TemporaryDirectory(prefix='gravity246-') as d:
  root=pathlib.Path(d);src=root/'src';out=root/'classes'
  for name,content in SRC.items():p=src/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content+'\n')
  subprocess.run([str(a.java.resolve()),'-jar',str(ROOT/'tools/vendor/ecj-3.46.100.jar'),'-21','-g:none','-nowarn','-cp',str(a.jar.resolve()),'-d',str(out),*map(str,sorted(src.rglob('*.java')))],check=True)
  with zipfile.ZipFile(a.jar) as z:
   p='dev/settledlands/npc/BodilessEffect.class';(out/p).parent.mkdir(parents=True,exist_ok=True);(out/p).write_bytes(z.read(p))
  subprocess.run([str(a.java.resolve()),'-Xshare:off','-Xverify:all','-cp',str(out),'GravityTest'],check=True)
 print('Synthetic effect/attribute APIs; no actual effect application or game physics.')
if __name__=='__main__':main()
