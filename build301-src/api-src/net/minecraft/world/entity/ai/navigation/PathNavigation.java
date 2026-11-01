package net.minecraft.world.entity.ai.navigation;

public class PathNavigation {
    public void stop() {}
    public boolean isDone() { return true; }
    public void setSpeedModifier(double speed) {}
    public boolean moveTo(double x, double y, double z, double speed) { return false; }
    public boolean moveTo(double x, double y, double z, int offset, double speed) { return false; }
}
