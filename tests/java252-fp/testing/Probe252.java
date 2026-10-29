package testing;
import java.util.*;
public final class Probe252 {
 public static boolean sneaking, invisible, spectator, firstPersonPackClobber=true, throwInAnimate, coreCrouchEnabled=true;
 public static final List<String> hookEvents=new ArrayList<>();
 public static final Set<String> visibleDrawnParts=new LinkedHashSet<>();
 public static void reset(){
  sneaking=invisible=spectator=throwInAnimate=false;
  firstPersonPackClobber=true;coreCrouchEnabled=true;
  hookEvents.clear();visibleDrawnParts.clear();
 }
}
