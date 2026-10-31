package com.codex.blacklight.client.vehicle;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Cuts only own-vehicle faces immediately around an authored first-person optic.
 * World depth, distant own geometry, passengers and every F5 draw are untouched. */
public final class VehicleOpticClipper extends com.codex.blacklight.forge.ForgeVertexConsumer {
    private final VertexConsumer out;
    private final Matrix4f inverse;
    private final Vec3 optic,forward,right,up;
    private final double[] low,high;
    private final float[][] quad=new float[4][11];
    private final Vector3f point=new Vector3f();
    private int index,color,overlay,light;
    public VehicleOpticClipper(VertexConsumer out,PoseStack stack,Vec3 optic,Vec3 forward) {
        this.out=out;inverse=new Matrix4f(stack.last().pose()).invert();
        this.optic=optic;this.forward=forward;right=forward.cross(new Vec3(0,1,0)).normalize();up=right.cross(forward).normalize();
        low=new double[]{-.6,-.45,-.15};high=new double[]{.6,.45,2};
    }
    @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz) {
        var vertex=quad[index];vertex[0]=x;vertex[1]=y;vertex[2]=z;vertex[3]=u;vertex[4]=v;
        vertex[5]=nx;vertex[6]=ny;vertex[7]=nz;inverse.transformPosition(x,y,z,point);
        var local=new Vec3(point.x,point.y,point.z).subtract(optic);
        vertex[8]=(float)local.dot(right);vertex[9]=(float)local.dot(up);vertex[10]=(float)local.dot(forward);
        this.color=color;this.overlay=overlay;this.light=light;
        if(++index==4){face();index=0;}
    }
    private void face() {
        for(int axis=0;axis<3;axis++) {
            double min=Double.POSITIVE_INFINITY,max=Double.NEGATIVE_INFINITY;
            for(var v:quad){min=Math.min(min,v[8+axis]);max=Math.max(max,v[8+axis]);}
            if(max<low[axis]||min>high[axis]){for(var v:quad)emit(v);return;}
        }
        List<float[]> remaining=new ArrayList<>(List.of(quad));
        for(int axis=0;axis<3&&!remaining.isEmpty();axis++)for(int side=0;side<2&&!remaining.isEmpty();side++) {
            double bound=side==0?low[axis]:high[axis];int sign=side==0?1:-1;
            polygon(clip(remaining,axis,bound,-sign));
            remaining=clip(remaining,axis,bound,sign);
        }
        // The remainder is inside this small optic volume and is the only discarded surface.
    }
    private static List<float[]> clip(List<float[]> polygon,int axis,double bound,int sign) {
        var result=new ArrayList<float[]>();if(polygon.isEmpty())return result;
        var previous=polygon.get(polygon.size()-1);double before=(previous[8+axis]-bound)*sign;
        for(var current:polygon) {
            double after=(current[8+axis]-bound)*sign;
            if((before>=0)!=(after>=0)) {
                float t=(float)(before/(before-after));var crossing=new float[11];
                for(int i=0;i<11;i++)crossing[i]=previous[i]+t*(current[i]-previous[i]);
                result.add(crossing);
            }
            if(after>=0)result.add(current);previous=current;before=after;
        }
        return result;
    }
    private void polygon(List<float[]> vertices) {
        for(int i=1;i+1<vertices.size();i++){emit(vertices.get(0));emit(vertices.get(i));emit(vertices.get(i+1));emit(vertices.get(i+1));}
    }
    private void emit(float[] v){out.vertex(v[0],v[1],v[2],((color>>16)&255)/255F,((color>>8)&255)/255F,(color&255)/255F,((color>>>24)&255)/255F,v[3],v[4],overlay,light,v[5],v[6],v[7]);}
    // MilitaryVehicleModel emits packed Cube faces. Preserve the chained interface as well.
    @Override public VertexConsumer addVertex(float x,float y,float z){out.vertex(x,y,z);return this;}
    @Override public VertexConsumer setColor(int r,int g,int b,int a){out.color(r,g,b,a);return this;}
    @Override public VertexConsumer setUv(float u,float v){out.uv(u,v);return this;}
    @Override public VertexConsumer setUv1(int u,int v){out.overlayCoords(u,v);return this;}
    @Override public VertexConsumer setUv2(int u,int v){out.uv2(u,v);return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){out.normal(x,y,z).endVertex();return this;}
}
