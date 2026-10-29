package net.minecraft.client;
public class Options {
 public CameraType cameraType=CameraType.FIRST_PERSON;
 public CameraType getCameraType(){return cameraType;}
 public void setCameraType(CameraType c){cameraType=c;}
}
