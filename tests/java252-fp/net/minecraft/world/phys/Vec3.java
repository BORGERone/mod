package net.minecraft.world.phys;
public class Vec3 {
 public static final Vec3 ZERO=new Vec3(0,0,0);
 public final double x,y,z;
 public Vec3(double a,double b,double c){x=a;y=b;z=c;}
 public double lengthSqr(){return x*x+y*y+z*z;}
}
