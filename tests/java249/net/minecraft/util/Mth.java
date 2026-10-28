package net.minecraft.util;public class Mth {public static float wrapDegrees(float a){float x=a%360;if(x>=180)x-=360;if(x< -180)x+=360;return x;}}
