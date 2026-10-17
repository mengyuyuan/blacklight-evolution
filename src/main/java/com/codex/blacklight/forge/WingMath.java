package com.codex.blacklight.forge;
/** Java 17 equivalent of the clamp overloads used by shared wing math. */
public final class WingMath {
 public static double clamp(double v,double min,double max){return Math.max(min,Math.min(max,v));}
 public static float clamp(float v,float min,float max){return Math.max(min,Math.min(max,v));}
 public static int clamp(long v,int min,int max){return (int)Math.max(min,Math.min(max,v));}
 private WingMath(){}
}
