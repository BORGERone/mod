package net.minecraft.nbt;
import java.util.*;
public class CompoundTag implements Tag {
 private final Map<String,Object> data=new HashMap<>();
 public boolean contains(String k){return data.containsKey(k);} public boolean contains(String k,int t){return data.containsKey(k);}
 public Tag put(String k,Tag v){return (Tag)data.put(k,v);} public void remove(String k){data.remove(k);}
 public void putInt(String k,int v){data.put(k,v);} public void putBoolean(String k,boolean v){data.put(k,v);}
 public void putDouble(String k,double v){data.put(k,v);} public void putString(String k,String v){data.put(k,v);} public void putIntArray(String k,int[] v){data.put(k,v);}
 public int getInt(String k){Object v=data.get(k);return v instanceof Number?((Number)v).intValue():0;}
 public boolean getBoolean(String k){return Boolean.TRUE.equals(data.get(k));} public double getDouble(String k){Object v=data.get(k);return v instanceof Number?((Number)v).doubleValue():0;}
 public String getString(String k){Object v=data.get(k);return v instanceof String?(String)v:"";} public int[] getIntArray(String k){Object v=data.get(k);return v instanceof int[]?(int[])v:new int[0];}
 public ListTag getList(String k,int t){Object v=data.get(k);return v instanceof ListTag?(ListTag)v:new ListTag();}
}
