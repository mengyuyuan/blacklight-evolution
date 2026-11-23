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
    record Bounds(int left,int top,int right,int bottom) {
        boolean overlaps(int x,int y,int width,int height) {
            return x-3 < right && x+width+3 > left && y-3 < bottom && y+height+3 > top;
        }
    }

    /** Keep the selected column. The nearest free vertical slot starts or ends at an occupied edge. */
    static Position avoid(Position anchor,int width,int height,int screenHeight,java.util.List<Bounds> occupied) {
        if(clear(anchor.x,anchor.y,width,height,occupied))return anchor;
        int bestY=anchor.y, distance=Integer.MAX_VALUE;
        for(var panel:occupied){
            for(int side=0;side<2;side++){
                int y=side==0 ? panel.bottom+3 : panel.top-height-3;
                int delta=Math.abs(y-anchor.y);
                if(y>=4 && y+height<=screenHeight-4 && delta<distance && clear(anchor.x,y,width,height,occupied)){
                    distance=delta;
                    bestY=y;
                }
            }
        }
        return new Position(anchor.x,bestY);
    }
    private static boolean clear(int x,int y,int width,int height,java.util.List<Bounds> occupied) {
        for(var panel:occupied)if(panel.overlaps(x,y,width,height))return false;
        return true;
    }
    private HealthHudLayout(){}
}
