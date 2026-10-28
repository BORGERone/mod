package traben.entity_texture_features.utils;
import java.util.*;public class ETFLruCache<X,Y> extends LinkedHashMap<X,Y> {
 public static class UUIDInteger extends ETFLruCache<UUID,Integer> { @Override public Integer get(Object k){return getOrDefault(k,-1);} }
}
