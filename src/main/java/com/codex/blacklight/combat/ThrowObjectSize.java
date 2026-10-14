package com.codex.blacklight.combat;
/** Shared by terrain cargo and future large-item adapters; material is deliberately irrelevant. */
public final class ThrowObjectSize {
 private ThrowObjectSize(){}
 public static boolean twoHanded(double width,double height,double depth){return Math.max(width,Math.max(height,depth))>1.25;}
 public static int terrainLimit(int tier){return tier>=3?27:tier==2?8:4;}
 public static double speedFactor(int blocks){return 1/Math.pow(Math.max(1,blocks),.10);}
 public static float damageFactor(int blocks){return (float)(1+.15*Math.sqrt(Math.max(0,blocks-1)));}
}
