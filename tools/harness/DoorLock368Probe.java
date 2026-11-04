import dev.settledlands.door.DoorKey368;
import net.minecraft.core.BlockPos;
import dev.settledlands.door.Doors368;
import dev.settledlands.door.DoorStore368;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Build 368 probe: the rules of the door lock that do not need a world.
 *
 * <pre>
 *   FORMAT keys=2 round=2 has=true empty=0      the lock file survives a write and a read
 *   DOOR   isDoor=false isOpen=false parts=0    a missing block is not a door and opens nothing
 *   KEY    isKey=false                          an empty hand is not a key
 * </pre>
 */
public final class DoorLock368Probe {

    public static void main(String[] args) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add("minecraft:overworld@12,64,-30");
        keys.add("minecraft:overworld@12,66,-30");
        String text = DoorStore368.encode(keys);
        Set<String> back = DoorStore368.decode(text);
        System.out.println("FORMAT keys=" + keys.size() + " round=" + back.size()
                + " has=" + back.contains("minecraft:overworld@12,66,-30")
                + " empty=" + DoorStore368.decode("").size());

        System.out.println("DOOR isDoor=" + Doors368.isDoor(null)
                + " isOpen=" + Doors368.isOpen(null)
                + " parts=" + Doors368.parts(null, null).size());

        System.out.println("KEY isKey=" + DoorKey368.isKey(null));

        BlockPos pos = DoorStore368.positionOf("minecraft:overworld@12,64,-30");
        System.out.println("PARSE dim=" + DoorStore368.dimensionOf("minecraft:overworld@12,64,-30")
                + " x=" + (pos == null ? "-" : pos.getX())
                + " y=" + (pos == null ? "-" : pos.getY())
                + " z=" + (pos == null ? "-" : pos.getZ())
                + " junk=" + (DoorStore368.positionOf("nonsense") == null));
    }
}
