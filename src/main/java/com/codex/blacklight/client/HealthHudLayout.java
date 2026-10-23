package com.codex.blacklight.client;

/** Relative positions survive GUI-scale and window-size changes. */
public final class HealthHudLayout {
    public record Position(int x,int y) {}
    public static int meterWidth(int width){return Math.max(20,Math.min(180,(width-20)/3));}
    public static Position position(int width,int height,int meterWidth,double horizontal,double vertical){
        int maxX=Math.max(10,width-10-meterWidth),maxY=Math.max(10,height-50);
        return new Position(10+(int)Math.round((maxX-10)*unit(horizontal)),
                10+(int)Math.round((maxY-10)*unit(vertical)));
    }
    public static double unit(double value){return Double.isFinite(value)?Math.max(0,Math.min(1,value)):0;}
    private HealthHudLayout(){}
}
