package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** The shell can roll onto its rim. Clip its baked support hull against actual block collision tops. */
final class SpiderBellySupport {
    private static final Vector3f[] VERTICES;
    private static final int[] FACES;
    static {
        try(var in=SpiderBellySupport.class.getResourceAsStream("/assets/blacklight/models/greene_abdomen_support.json");
            var reader=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)){
            var json=JsonParser.parseReader(reader).getAsJsonObject();
            var points=json.getAsJsonArray("vertices");
            VERTICES=new Vector3f[points.size()];
            for(int i=0;i<VERTICES.length;i++){
                var v=points.get(i).getAsJsonArray();
                VERTICES[i]=new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat());
            }
            var triangles=json.getAsJsonArray("triangles");
            FACES=new int[triangles.size()*3];
            for(int i=0;i<FACES.length;i++)FACES[i]=triangles.get(i/3).getAsJsonArray().get(i%3).getAsInt();
        }catch(Exception ex){throw new ExceptionInInitializerError(ex);}
    }
    private final EncounterMob boss;
    private final Matrix4f last=new Matrix4f();
    private final Vector3f[] vertices=new Vector3f[VERTICES.length];
    private final double[] first=new double[30],second=new double[30];
    private long poseTick=Long.MIN_VALUE,terrainTick=Long.MIN_VALUE;
    private Vec3 lastOrigin,terrainOrigin;
    private AABB terrainBounds;
    private float lift;
    private final List<AABB> tops=new ArrayList<>();
    SpiderBellySupport(EncounterMob boss){
        this.boss=boss;
        for(int i=0;i<vertices.length;i++)vertices[i]=new Vector3f();
    }
    Matrix4f seat(Matrix4f root,float partial){
        if(!boss.spider.live())return root;
        var origin=boss.getPosition(partial);
        var shell=boss.spider.jointMatrix(root,"carapace",partial);
        long now=boss.level().getGameTime();
        if(now!=poseTick || !origin.equals(lastOrigin) || !last.equals(shell)){
            last.set(shell);lastOrigin=origin;poseTick=now;
            lift=clearance(shell,origin,now);
        }
        return root.m31(root.m31()+lift);
    }
    private float clearance(Matrix4f shell,Vec3 origin,long now){
        Vector3f bottom=vertices[0];
        double minX=Double.POSITIVE_INFINITY,minZ=minX,maxX=-minX,maxZ=-minX;
        for(int i=0;i<vertices.length;i++){
            var v=shell.transformPosition(VERTICES[i],vertices[i]);
            if(v.y<bottom.y)bottom=v;
            minX=Math.min(minX,v.x);maxX=Math.max(maxX,v.x);
            minZ=Math.min(minZ,v.z);maxZ=Math.max(maxZ,v.z);
        }
        double lowest=bottom.y;
        if(lowest>2.16)return 0;
        // This band follows the feet, not the cave roof. Cache the static block boxes for the round
        // of client and server pose requests; a block edit is observed on the next game tick.
        var footprint=new AABB(origin.x+minX,origin.y-4,origin.z+minZ,origin.x+maxX,origin.y+2.13,origin.z+maxZ);
        if(now!=terrainTick || Math.abs(origin.y-terrainOrigin.y)>.001 || !terrainBounds.contains(footprint.minX,origin.y,footprint.minZ) || !terrainBounds.contains(footprint.maxX,origin.y,footprint.maxZ)){
            terrainTick=now;terrainOrigin=origin;tops.clear();
            var bounds=footprint.inflate(1,0,1);terrainBounds=bounds;
            for(var shape:boss.level().getBlockCollisions(boss,bounds))
                for(var block:shape.toAabbs())if(block.maxY<=bounds.maxY+.0001)tops.add(block);
        }
        double raise=0;
        double bx=origin.x+bottom.x,bz=origin.z+bottom.z;
        for(var top:tops)
            if(bx>=top.minX && bx<=top.maxX && bz>=top.minZ && bz<=top.maxZ)
                raise=Math.max(raise,top.maxY-origin.y+.025-lowest);
        for(var top:tops){
            double y=top.maxY-origin.y+.025;
            if(y-lowest<=raise)continue;
            double x0=top.minX-origin.x,x1=top.maxX-origin.x,z0=top.minZ-origin.z,z1=top.maxZ-origin.z;
            for(int k=0;k<FACES.length;k+=3){
                var a=vertices[FACES[k]];var b=vertices[FACES[k+1]];var c=vertices[FACES[k+2]];
                if(y-Math.min(a.y,Math.min(b.y,c.y))<=raise || Math.max(a.x,Math.max(b.x,c.x))<x0 ||
                    Math.min(a.x,Math.min(b.x,c.x))>x1 || Math.max(a.z,Math.max(b.z,c.z))<z0 ||
                    Math.min(a.z,Math.min(b.z,c.z))>z1)continue;
                // CCW hull: the top face of a block can only meet downward-facing shell facets.
                if((b.z-a.z)*(c.x-a.x)-(b.x-a.x)*(c.z-a.z)>=0)continue;
                put(0,a);put(3,b);put(6,c);
                int n=clip(first,second,3,0,x0,1);
                n=clip(second,first,n,0,x1,-1);
                n=clip(first,second,n,2,z0,1);
                n=clip(second,first,n,2,z1,-1);
                for(int i=0;i<n;i++)raise=Math.max(raise,y-first[3*i+1]);
                if(y-lowest<=raise+1e-5)break;
            }
        }
        return (float)raise;
    }
    private void put(int i,Vector3f v){first[i]=v.x;first[i+1]=v.y;first[i+2]=v.z;}
    private static int clip(double[] src,double[] dst,int count,int axis,double edge,int sign){
        int out=0;
        for(int cur=0,prev=(count-1)*3;cur<count*3;prev=cur,cur+=3){
            double d0=(src[prev+axis]-edge)*sign,d1=(src[cur+axis]-edge)*sign;
            if((d0>=0)!=(d1>=0)){
                double u=d0/(d0-d1);
                for(int k=0;k<3;k++)dst[out++]=src[prev+k]+u*(src[cur+k]-src[prev+k]);
            }
            if(d1>=0)for(int k=0;k<3;k++)dst[out++]=src[cur+k];
        }
        return out/3;
    }
}
