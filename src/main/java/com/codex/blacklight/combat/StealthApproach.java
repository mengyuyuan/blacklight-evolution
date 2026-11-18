package com.codex.blacklight.combat;

/** Pure geometry shared by server admission and the standalone regression probe. */
public final class StealthApproach {
    private StealthApproach(){}
    public static boolean behind(double x,double y,double z,float yaw){
        if(!Double.isFinite(x)||!Double.isFinite(y)||!Double.isFinite(z)||!Float.isFinite(yaw))return false;
        double distance=Math.hypot(x,z),radians=Math.toRadians(yaw);
        return distance>=.25&&distance<=1.8&&Math.abs(y)<=.65
                &&(-x*Math.sin(radians)+z*Math.cos(radians))/distance<=-.65;
    }
}
