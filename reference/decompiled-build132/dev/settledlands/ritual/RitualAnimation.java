package dev.settledlands.ritual;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public final class RitualAnimation {
   private static final Map<String, Map<String, List<RitualAnimation.Key>>> CLIPS = load();

   private static Map<String, Map<String, List<RitualAnimation.Key>>> load() {
      try {
         Map var16;
         try (InputStream input = RitualAnimation.class.getResourceAsStream("/assets/settledlands/animations/cult_tracks.json")) {
            if (input == null) {
               throw new IllegalStateException("Missing ritual animation tracks");
            }

            JsonObject json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Map<String, List<RitualAnimation.Key>>> clips = new HashMap<>();

            for (Entry<String, JsonElement> clip : json.entrySet()) {
               Map<String, List<RitualAnimation.Key>> tracks = new HashMap<>();

               for (Entry<String, JsonElement> track : clip.getValue().getAsJsonObject().getAsJsonObject("tracks").entrySet()) {
                  List<RitualAnimation.Key> keys = new ArrayList<>();

                  for (JsonElement frame : track.getValue().getAsJsonArray()) {
                     JsonObject f = frame.getAsJsonObject();
                     JsonArray v = f.getAsJsonArray("value");
                     keys.add(
                        new RitualAnimation.Key(
                           f.get("time").getAsDouble(),
                           new double[]{v.get(0).getAsDouble(), v.get(1).getAsDouble(), v.get(2).getAsDouble()},
                           f.get("easing").getAsString()
                        )
                     );
                  }

                  keys.sort(Comparator.comparingDouble(RitualAnimation.Key::time));
                  tracks.put(track.getKey(), List.copyOf(keys));
               }

               clips.put(clip.getKey(), Map.copyOf(tracks));
            }

            var16 = Map.copyOf(clips);
         }

         return var16;
      } catch (IOException var15) {
         throw new ExceptionInInitializerError(var15);
      }
   }

   public static double[] sample(String bone, String channel, double ticks) {
      String clip = ticks < 40.0 ? "cult" : "cultIDE";
      double seconds = clip.equals("cult") ? Math.max(0.0, ticks) / 20.0 : (ticks - 40.0) / 20.0 % 1.0;
      List<RitualAnimation.Key> keys = CLIPS.get(clip).get(bone + "." + channel);
      if (keys != null && !keys.isEmpty()) {
         return interpolate(keys, seconds);
      } else {
         return channel.equals("scale") ? new double[]{1.0, 1.0, 1.0} : new double[3];
      }
   }

   public static double[] interpolate(List<RitualAnimation.Key> keys, double t) {
      if (t <= keys.getFirst().time) {
         return (double[])keys.getFirst().value.clone();
      } else {
         for (int i = 1; i < keys.size(); i++) {
            if (t <= keys.get(i).time) {
               RitualAnimation.Key a = keys.get(i - 1);
               RitualAnimation.Key b = keys.get(i);
               double alpha = ease(b.easing, (t - a.time) / (b.time - a.time));
               double[] v = new double[3];

               for (int c = 0; c < 3; c++) {
                  v[c] = a.value[c] + (b.value[c] - a.value[c]) * alpha;
               }

               return v;
            }
         }

         return (double[])keys.getLast().value.clone();
      }
   }

   public static double ease(String name, double t) {
      return switch (name) {
         case "linear" -> t;
         case "easeInBack" -> t * t * (2.70158 * t - 1.70158);
         case "easeInExpo" -> Math.pow(2.0, 10.0 * (t - 1.0));
         case "easeInElastic" -> 1.0 - Math.pow(Math.cos(t * Math.PI / 2.0), 3.0) * Math.cos(t * Math.PI);
         case "easeInBounce" -> Math.min(
            Math.min(7.5625 * t * t, 15.125 * Math.pow(t - 0.5454545454545454, 2.0) + 0.5),
            Math.min(30.25 * Math.pow(t - 0.8181818181818182, 2.0) + 0.75, 60.5 * Math.pow(t - 0.9545454545454546, 2.0) + 0.875)
         );
         default -> throw new IllegalArgumentException("Unsupported easing in imported track: " + name);
      };
   }

   private RitualAnimation() {
   }

   public record Key(double time, double[] value, String easing) {
   }
}
