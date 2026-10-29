package net.minecraft.network.codec;
public interface StreamCodec<B,V> {
 void encode(B b, V v);
 V decode(B b);
 static <B,V> StreamCodec<B,V> of(StreamEncoder<B,V> e, StreamDecoder<B,V> d) {
  return new StreamCodec<>() {
   public void encode(B b, V v) { e.encode(b, v); }
   public V decode(B b) { return d.decode(b); }
  };
 }
}
