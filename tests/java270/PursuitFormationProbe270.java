import dev.settledlands.npc.NpcPursuit269;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class PursuitFormationProbe270 {
    private static final class Target extends Entity {
        final double x, y, z;
        final float yaw;
        Target(double x, double y, double z, float yaw) {
            this.x = x; this.y = y; this.z = z; this.yaw = yaw;
        }
        @Override public double getX() { return x; }
        @Override public double getY() { return y; }
        @Override public double getZ() { return z; }
        @Override public float getYRot() { return yaw; }
    }

    private static void near(double actual, double expected, String label) {
        if (Math.abs(actual - expected) > 1.0E-6) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    public static void main(String[] args) {
        if (!NpcPursuit269.isPursuitAction("trigger:pursuit:start:npc:guide_01"))
            throw new AssertionError("NPC start action not recognized");
        if (!NpcPursuit269.isPursuitAction("trigger:pursuit:start:player:PlayerName"))
            throw new AssertionError("player start action not recognized");
        if (!NpcPursuit269.isPursuitAction("trigger:pursuit:stop"))
            throw new AssertionError("stop action not recognized");
        if (NpcPursuit269.isPursuitAction("cmd:say hello"))
            throw new AssertionError("ordinary command classified as pursuit");

        Target target = new Target(10.0, 64.0, -3.0, 0.0F);
        Vec3 left = NpcPursuit269.slotPosition(target, 0);
        Vec3 center = NpcPursuit269.slotPosition(target, 1);
        Vec3 right = NpcPursuit269.slotPosition(target, 2);
        Vec3 secondRow = NpcPursuit269.slotPosition(target, 3);
        near(left.x, 8.6, "front-left x"); near(left.z, -1.6, "front-left z");
        near(center.x, 10.0, "front-center x"); near(center.z, -1.6, "front-center z");
        near(right.x, 11.4, "front-right x"); near(right.z, -1.6, "front-right z");
        near(secondRow.x, 8.6, "second-row x"); near(secondRow.z, -0.1, "second-row z");

        Target rotated = new Target(0.0, 0.0, 0.0, 90.0F);
        Vec3 rotatedFront = NpcPursuit269.slotPosition(rotated, 1);
        near(rotatedFront.x, -1.4, "rotated front x");
        near(rotatedFront.z, 0.0, "rotated front z");
        System.out.println("PURSUIT 270 FIXTURE: PASS (commands, 3-wide front row, 1.5-block row spacing, target-facing orientation)");
    }
}
