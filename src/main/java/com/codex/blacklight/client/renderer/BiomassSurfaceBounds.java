package com.codex.blacklight.client.renderer;

/** Bounds of the currently posed surface, independent of gameplay collision boxes. */
public final class BiomassSurfaceBounds {
    private float minX=Float.POSITIVE_INFINITY,minY=minX,minZ=minX;
    private float maxX=Float.NEGATIVE_INFINITY,maxY=maxX,maxZ=maxX;
    public void include(float x,float y,float z){
        if(!Float.isFinite(x)||!Float.isFinite(y)||!Float.isFinite(z))return;
        minX=Math.min(minX,x);minY=Math.min(minY,y);minZ=Math.min(minZ,z);
        maxX=Math.max(maxX,x);maxY=Math.max(maxY,y);maxZ=Math.max(maxZ,z);
    }
    public boolean empty(){return !Float.isFinite(minY);}
    public float height(){return empty()?1:Math.max(.001F,maxY-minY);}
    public float width(){return empty()?1:Math.max(.001F,maxX-minX);}
    public float depth(){return empty()?1:Math.max(.001F,maxZ-minZ);}
    public float heightFraction(float y){return empty()?0:(y-minY)/height();}
    /** Both species converge on the same liquid envelope before the player unfolds. */
    public org.joml.Vector3f reshape(org.joml.Vector3f p,float amount){
        if(empty()||amount<=0)return p;
        float x=(p.x-(minX+maxX)*.5F)*2/width();
        float y=heightFraction(p.y)*2-1;
        float z=(p.z-(minZ+maxZ)*.5F)*2/depth();
        float length=(float)Math.sqrt(x*x+y*y+z*z);
        if(length<.00001F){x=0;y=-1;z=0;length=1;}
        float t=Math.max(0,Math.min(1,amount));
        return p.lerp(new org.joml.Vector3f(.30F*x/length,.675F+.675F*y/length,.30F*z/length),t);
    }
    public static float ease(float value){float t=Math.max(0,Math.min(1,value));return t*t*(3-2*t);}
}
