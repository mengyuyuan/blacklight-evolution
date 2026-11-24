package com.codex.blacklight.client;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/** A copied world-camera matrix pair projects into GUI dimensions before GUI switches to orthographic. */
public final class WorldHudProjection {
    public record Point(float x,float y,float depth,boolean inFront,boolean onScreen) { }
    private WorldHudProjection() { }
    /** Normalized HUD position. A target behind the camera stays on the nearest screen edge. */
    public static org.joml.Vector2f waypointBearing(Vector4f clip) {
        float edge = Math.max(Math.abs(clip.x), Math.abs(clip.y));
        if (clip.w <= 0 && edge < .0001F) return new org.joml.Vector2f(0, -1);
        float scale = Math.max(.0001F, Math.max(clip.w, edge));
        return new org.joml.Vector2f(clip.x / scale, clip.y / scale);
    }

    public static Point project(Matrix4f view,Matrix4f projection,double cameraX,double cameraY,double cameraZ,
                                double x,double y,double z,int width,int height) {
        var clip=new Vector4f((float)(x-cameraX),(float)(y-cameraY),(float)(z-cameraZ),1);
        view.transform(clip);projection.transform(clip);
        if(!Float.isFinite(clip.w)||clip.w<=.00001F)return new Point(Float.NaN,Float.NaN,Float.NaN,false,false);
        float nx=clip.x/clip.w,ny=clip.y/clip.w,nz=clip.z/clip.w;
        boolean visible=Float.isFinite(nx)&&Float.isFinite(ny)&&Float.isFinite(nz)
                &&Math.abs(nx)<=1&&Math.abs(ny)<=1&&nz>=-1&&nz<=1;
        return new Point((nx+1)*width*.5F,(1-ny)*height*.5F,nz,true,visible);
    }
}
