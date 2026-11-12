package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.military.vehicle.VehicleDeformation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Subdivides only impacted skin faces; moves real vertices, UVs and lighting normals together. */
public final class VehicleDentMesh implements VertexConsumer {
    private final VertexConsumer out;
    private final Matrix4f inverse,pose;
    private final org.joml.Matrix3f normal;
    private final List<VehicleDeformation.Dent> dents;
    private final float[][] quad=new float[4][8];
    private final Vector3f scratch=new Vector3f();
    private int index,color,overlay,light;
    public VehicleDentMesh(VertexConsumer out,PoseStack stack,List<VehicleDeformation.Dent> dents){
        this.out=out;this.dents=dents;pose=new Matrix4f(stack.last().pose());inverse=new Matrix4f(pose).invert();normal=new org.joml.Matrix3f(stack.last().normal());
    }
    @Override public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
        var p=quad[index];inverse.transformPosition(x,y,z,scratch);p[0]=scratch.x;p[1]=scratch.y;p[2]=scratch.z;p[3]=u;p[4]=v;p[5]=nx;p[6]=ny;p[7]=nz;
        this.color=color;this.overlay=overlay;this.light=light;
        if(++index==4){face();index=0;}
    }
    private Vec3 point(int i){return new Vec3(quad[i][0],quad[i][1],quad[i][2]);}
    private void face(){
        Vec3 a=point(0),b=point(1),c=point(2),d=point(3);
        double minX=Math.min(Math.min(a.x,b.x),Math.min(c.x,d.x)),minY=Math.min(Math.min(a.y,b.y),Math.min(c.y,d.y)),minZ=Math.min(Math.min(a.z,b.z),Math.min(c.z,d.z));
        double maxX=Math.max(Math.max(a.x,b.x),Math.max(c.x,d.x)),maxY=Math.max(Math.max(a.y,b.y),Math.max(c.y,d.y)),maxZ=Math.max(Math.max(a.z,b.z),Math.max(c.z,d.z));
        boolean affected=false;
        for(var dent:dents){var p=dent.point();double r=dent.radius()+.15;
            if(p.x+r>=minX&&p.x-r<=maxX&&p.y+r>=minY&&p.y-r<=maxY&&p.z+r>=minZ&&p.z-r<=maxZ){affected=true;break;}}
        if(!affected){for(var q:quad)emit(new Vec3(q[0],q[1],q[2]),q[3],q[4],q[5],q[6],q[7],color);return;}
        int cols=Math.clamp((int)Math.ceil(a.distanceTo(b)/.16),1,64),rows=Math.clamp((int)Math.ceil(a.distanceTo(d)/.16),1,64);
        // A small radius needs interior vertices even when the source is one huge six-face cuboid.
        for(int y=0;y<rows;y++)for(int x=0;x<cols;x++){
            Sample v0=sample((double)x/cols,(double)y/rows,a,b,c,d),v1=sample((double)(x+1)/cols,(double)y/rows,a,b,c,d);
            Sample v2=sample((double)(x+1)/cols,(double)(y+1)/rows,a,b,c,d),v3=sample((double)x/cols,(double)(y+1)/rows,a,b,c,d);
            var n=v1.p.subtract(v0.p).cross(v3.p.subtract(v0.p)).normalize();
            normal.transform((float)n.x,(float)n.y,(float)n.z,scratch);
            if(scratch.x*quad[0][5]+scratch.y*quad[0][6]+scratch.z*quad[0][7]<0)scratch.negate();
            for(var vtx:new Sample[]{v0,v1,v2,v3})emit(vtx.p,vtx.u,vtx.v,scratch.x,scratch.y,scratch.z,tint(vtx.depth));
        }
    }
    private record Sample(Vec3 p,float u,float v,double depth){}
    private Sample sample(double x,double y,Vec3 a,Vec3 b,Vec3 c,Vec3 d){
        Vec3 p=a.lerp(b,x).lerp(d.lerp(c,x),y),shift=VehicleDeformation.offset(dents,p);
        return new Sample(p.add(shift),bilinear(3,x,y),bilinear(4,x,y),shift.length());
    }
    private float bilinear(int i,double x,double y){return (float)((quad[0][i]*(1-x)+quad[1][i]*x)*(1-y)+(quad[3][i]*(1-x)+quad[2][i]*x)*y);}
    private int tint(double depth){double shade=1-Math.min(.38,depth*.8);return (color&0xff000000)|((int)(((color>>>16)&255)*shade)<<16)|((int)(((color>>>8)&255)*shade)<<8)|(int)((color&255)*shade);}
    private void emit(Vec3 p,float u,float v,float nx,float ny,float nz,int color){
        // Do not reuse the normal scratch: callers emit four vertices with one computed face normal.
        var position=pose.transformPosition((float)p.x,(float)p.y,(float)p.z,new Vector3f());
        out.addVertex(position.x,position.y,position.z,color,u,v,overlay,light,nx,ny,nz);
    }
    // The authored ModelPart path uses the packed overload above.
    @Override public VertexConsumer addVertex(float x,float y,float z){out.addVertex(x,y,z);return this;}
    @Override public VertexConsumer setColor(int r,int g,int b,int a){out.setColor(r,g,b,a);return this;}
    @Override public VertexConsumer setUv(float u,float v){out.setUv(u,v);return this;}
    @Override public VertexConsumer setUv1(int u,int v){out.setUv1(u,v);return this;}
    @Override public VertexConsumer setUv2(int u,int v){out.setUv2(u,v);return this;}
    @Override public VertexConsumer setNormal(float x,float y,float z){out.setNormal(x,y,z);return this;}
}
