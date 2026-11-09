import dev.settledlands.door.DoorKey368;
import dev.settledlands.door.Doors368;
import dev.settledlands.door.DoorStore368;
import dev.settledlands.door.NpcDoors368;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

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

        // Build 414: live JVM linkage + lifecycle test of NpcDoors368$Open.walkedTo:
        // keepOpen -> stays open while approaching -> markWalkedTo -> closes right behind in stopLeftBehind.
        try {
            ServerLevel level = new ServerLevel();
            BlockPos door = new BlockPos(62, 64, -237);
            Method keepOpen = NpcDoors368.class.getDeclaredMethod(
                    "keepOpen", ServerLevel.class, BlockPos.class, long.class);
            Method markWalkedTo = NpcDoors368.class.getDeclaredMethod(
                    "markWalkedTo", ServerLevel.class, UUID.class, double.class, double.class, double.class);
            Method stopLeftBehind = NpcDoors368.class.getDeclaredMethod("stopLeftBehind", long.class);
            keepOpen.setAccessible(true);
            markWalkedTo.setAccessible(true);
            stopLeftBehind.setAccessible(true);

            keepOpen.invoke(null, level, door, 10L);
            boolean opened1 = NpcDoors368.openDoors() == 1 && NpcDoors368.guideOpened(level, door);
            stopLeftBehind.invoke(null, 20L);
            boolean heldAhead = NpcDoors368.openDoors() == 1;
            markWalkedTo.invoke(null, level, UUID.randomUUID(), 62.5D, 64.5D, -236.5D);
            stopLeftBehind.invoke(null, 25L);
            boolean closedBehind = NpcDoors368.openDoors() == 0 && !NpcDoors368.guideOpened(level, door);
            System.out.println("GUIDE_PASS opened=" + opened1 + " heldAhead=" + heldAhead
                    + " closedBehind=" + closedBehind);
        } catch (Throwable failure) {
            System.out.println("GUIDE_PASS crashed=" + failure);
        }
    }
}
