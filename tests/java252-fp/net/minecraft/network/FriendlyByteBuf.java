package net.minecraft.network;
import java.util.*;
public class FriendlyByteBuf {
 private final List<Object> values=new ArrayList<>();
 private int next;
 public FriendlyByteBuf writeUUID(UUID v){values.add(v);return this;}
 public UUID readUUID(){return (UUID)values.get(next++);}
 public FriendlyByteBuf writeUtf(String v){values.add(v);return this;}
 public String readUtf(){return (String)values.get(next++);}
}
