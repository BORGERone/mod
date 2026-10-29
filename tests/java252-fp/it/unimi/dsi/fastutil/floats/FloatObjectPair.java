package it.unimi.dsi.fastutil.floats;
public interface FloatObjectPair<V> {
 float leftFloat();
 V right();
 static <V> FloatObjectPair<V> of(float l, V r) {
  return new FloatObjectPair<>() {
   public float leftFloat() { return l; }
   public V right() { return r; }
  };
 }
}
