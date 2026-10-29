package com.mojang.blaze3d.vertex;
import java.util.*; import org.joml.Matrix4f; import org.joml.Matrix3f;import org.joml.Vector3f;
/** Minecraft 1.21 scale/normal flag behavior, backed by actual JOML. */
public class PoseStack {
 public static class Pose {private final Matrix4f pose;private final Matrix3f normal;private boolean trusted=true;Pose(Matrix4f p,Matrix3f n,boolean t){pose=p;normal=n;trusted=t;}public Matrix4f pose(){return pose;}public Matrix3f normal(){return normal;}public Vector3f transformNormal(float x,float y,float z,Vector3f out){normal.transform(x,y,z,out);return trusted?out:out.normalize();}}
 public static PoseStack CURRENT;public void forceTrustedForTest(){poses.peek().trusted=true;}
 private final Deque<Pose> poses=new ArrayDeque<>();public PoseStack(){poses.push(new Pose(new Matrix4f(),new Matrix3f(),true));}
 public void pushPose(){CURRENT=this;Pose t=poses.peek();poses.push(new Pose(new Matrix4f(t.pose),new Matrix3f(t.normal),t.trusted));}public void popPose(){if(poses.size()==1)throw new IllegalStateException("underflow");poses.pop();}
 public void scale(float x,float y,float z){CURRENT=this;Pose p=poses.peek();p.pose.scale(x,y,z);if(Math.abs(x)==Math.abs(y)&&Math.abs(y)==Math.abs(z)){if(x<0||y<0||z<0)p.normal.scale(Math.signum(x),Math.signum(y),Math.signum(z));}else{p.normal.scale(1/x,1/y,1/z);p.trusted=false;if(testing.Probe251.throwOnNextNonuniformScale){testing.Probe251.throwOnNextNonuniformScale=false;throw new IllegalStateException("controlled scale failure after normal flag");}}}
 public void translate(float x,float y,float z){CURRENT=this;poses.peek().pose.translate(x,y,z);}public void translate(double x,double y,double z){translate((float)x,(float)y,(float)z);}
 public void mulPose(org.joml.Quaternionf q){CURRENT=this;poses.peek().pose.rotate(q);poses.peek().normal.rotate(q);}public Matrix4f matrix(){return poses.peek().pose;}public Pose last(){return poses.peek();}public int depth(){return poses.size();}
}
