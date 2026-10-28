package com.mojang.blaze3d.vertex;
import java.util.*; import org.joml.Matrix4f; import org.joml.Matrix3f;
public class PoseStack {
 public static class Pose { private final Matrix4f pose;private final Matrix3f normal;Pose(Matrix4f p,Matrix3f n){pose=p;normal=n;} public Matrix4f pose(){return pose;} public Matrix3f normal(){return normal;} }
 private final Deque<Pose> poses = new ArrayDeque<>();
 public PoseStack(){poses.push(new Pose(new Matrix4f(),new Matrix3f()));}
 public void pushPose(){Pose t=poses.peek();poses.push(new Pose(new Matrix4f(t.pose),new Matrix3f(t.normal)));}
 public void popPose(){if(poses.size()==1)throw new IllegalStateException("underflow");poses.pop();}
 public void translate(float x,float y,float z){poses.peek().pose.translate(x,y,z);}
 public void mulPose(org.joml.Quaternionf q){poses.peek().pose.rotate(q);poses.peek().normal.rotate(q);}
 public Matrix4f matrix(){return poses.peek().pose;}
 public Pose last(){return poses.peek();}
 public int depth(){return poses.size();}
}
