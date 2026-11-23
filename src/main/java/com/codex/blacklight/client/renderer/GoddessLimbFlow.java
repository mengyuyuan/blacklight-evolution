package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessActor;
import com.codex.blacklight.combat.GoddessEncounter;
import com.codex.blacklight.combat.GoddessRig;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.IdentityHashMap;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Advect the authored skin into the glove. All strips of the same surface use one continuous distance field. */
final class GoddessLimbFlow {
    private record Surface(float[] vertices,int[] colors,float[] order){}
    record Profile(int part,Vector3f center,float radius,IdentityHashMap<GreeneGoddessRenderer.Group,Surface> surfaces){}
    private final Profile profile;
    private final float time;
    private final Vector3f palm,entry,axis,bend;
    private final Vector3f[] quad={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
    private final Vector3f normal=new Vector3f(), face=new Vector3f(), edge=new Vector3f(), guide=new Vector3f();
    private final float[] amount=new float[4];

    private static float edgeLength(float[] v,int a,int b){
        float x=v[a]-v[b],y=v[a+1]-v[b+1],z=v[a+2]-v[b+2];
        return (float)Math.sqrt(x*x+y*y+z*z);
    }
    private static float bilerp(float a,float b,float c,float d,float u,float v){
        float top=a+(b-a)*u,bottom=d+(c-d)*u;
        return top+(bottom-top)*v;
    }
    private static int blendColor(int[] colors,int at,float u,float v){
        int out=0;
        for(int shift=0;shift<32;shift+=8){
            int a=(colors[at]>>>shift)&255,b=(colors[at+1]>>>shift)&255;
            int c=(colors[at+2]>>>shift)&255,d=(colors[at+3]>>>shift)&255;
            out|=Math.round(bilerp(a,b,c,d,u,v))<<shift;
        }
        return out;
    }
    // Only long plates need extra samples where a broad sheet buckles into the narrow mouth.
    private static Surface surface(GreeneGoddessRenderer.Group group){
        float[] src=group.vertices();int[] colors=group.colors();
        if(group.skin()>=0)return new Surface(src,colors,new float[colors.length]);
        int[] layout=new int[colors.length/4];int vertices=0;
        for(int i=0;i<colors.length;i+=4){
            int k=i*8;
            int u=Math.max(1,(int)Math.ceil(Math.max(edgeLength(src,k,k+8),edgeLength(src,k+16,k+24))/.77));
            int v=Math.max(1,(int)Math.ceil(Math.max(edgeLength(src,k,k+24),edgeLength(src,k+8,k+16))/.77));
            u=Math.min(u,8);v=Math.min(v,8);layout[i/4]=u|(v<<4);vertices+=4*u*v;
        }
        if(vertices==colors.length)return new Surface(src,colors,new float[vertices]);
        float[] dst=new float[vertices*8];int[] tint=new int[vertices];int at=0;
        for(int i=0;i<colors.length;i+=4){
            int k=i*8,u=layout[i/4]&15,v=layout[i/4]>>4;
            for(int y=0;y<v;y++)for(int x=0;x<u;x++)for(int c=0;c<4;c++,at++){
                float a=(x+(c==1||c==2?1:0))/(float)u,b=(y+(c>=2?1:0))/(float)v;
                for(int ch=0;ch<8;ch++)dst[at*8+ch]=bilerp(src[k+ch],src[k+8+ch],src[k+16+ch],src[k+24+ch],a,b);
                tint[at]=blendColor(colors,i,a,b);
            }
        }
        return new Surface(dst,tint,new float[vertices]);
    }
    static Profile bind(GreeneGoddessRenderer.Group[] groups,int stage){
        int part=1<<stage;
        Vector3f port=GoddessActor.framePoint(stage,1,GoddessRig.flowStart(stage)).toVector3f();
        var surfaces=new IdentityHashMap<GreeneGoddessRenderer.Group,Surface>();
        var center=new Vector3f();var vertex=new Vector3f();int count=0;
        float min=Float.POSITIVE_INFINITY,max=0;
        for(var group:groups){
            if(GoddessRig.severedPart(group.carrier())!=part)continue;
            var local=group.transforms();
            var bind=GoddessRig.fullLimbOffset(stage,group.carrier()).translate(local[0],local[1],local[2])
                    .rotate(new Quaternionf(local[3],local[4],local[5],local[6]));
            var surface=surface(group);surfaces.put(group,surface);
            var vertices=surface.vertices;var distance=surface.order;
            for(int i=0;i<distance.length;i++){
                int at=i*8;
                bind.transformPosition(vertices[at],vertices[at+1],vertices[at+2],vertex);
                float radius=vertex.distance(port);distance[i]=radius;
                min=Math.min(min,radius);max=Math.max(max,radius);
                if(group.skin()<0){center.add(vertex);count++;}
            }
        }
        center.div(Math.max(1,count));
        for(var surface:surfaces.values()){
            var values=surface.order;
            for(int i=0;i<values.length;i++)values[i]=(values[i]-min)/Math.max(.01F,max-min);
        }
        return new Profile(part,center,Math.max(max,.05F),surfaces);
    }

    static GoddessLimbFlow active(EncounterMob boss,float partial,Profile[] profiles){
        if(boss==null || boss.goddess.action()!=GoddessEncounter.Action.EXTRACTING)return null;
        int stage=Integer.bitCount(boss.goddess.poseMask()&3);
        float frame=80+1.2F*boss.goddess.age(partial),start=GoddessRig.flowStart(stage),end=GoddessRig.flowEnd(stage);
        if(frame<=start || frame>=end)return null;
        Vector3f palm=GoddessActor.localPoint(boss,1,partial).toVector3f();
        Vector3f wrist=GoddessActor.localPoint(boss,2,partial).toVector3f();
        return new GoddessLimbFlow(profiles[stage],(frame-start)/(end-start),palm,wrist);
    }
    private GoddessLimbFlow(Profile profile,float time,Vector3f palm,Vector3f wrist){
        this.profile=profile;this.time=time;this.palm=palm;
        axis=new Vector3f(profile.center).sub(palm);
        if(axis.lengthSquared()<.0001F)axis.set(0,1,0);else axis.normalize();
        entry=new Vector3f(palm).sub(wrist);
        if(entry.lengthSquared()<.0001F)entry.set(axis);else entry.normalize().mul(.56F).fma(.65F,axis).normalize();
        bend=new Vector3f(axis).cross(entry);
        if(bend.lengthSquared()>.0001F)bend.normalize();else bend.zero();
    }
    boolean receives(int carrier){return GoddessRig.severedPart(carrier)==profile.part;}
    private static float ease(float x){x=Math.max(0,Math.min(1,x));return x*x*(3-2*x);}
    private void channel(float r,Vector3f out){
        float h=ease(r/profile.radius);
        out.set(palm).fma(r*(1-h),entry).fma(r*h,axis).fma(r*.55F*h*(1-h),bend);
    }
    private float carry(Vector3f p,float order){
        float start=.55F*order;
        float progress=ease((time-start)/(.49F-.04F*order));
        if(progress<=0)return 0;
        if(progress>=1){p.set(palm);return 1;}
        float radius=p.distance(palm);
        channel(radius,guide);p.sub(guide);
        float left=1-progress;
        float width=left*left*(1-.89F*ease(progress/.72F));
        channel(radius*left,guide);p.mul(width).add(guide);
        return progress;
    }
    private static int tint(int packed,float amount,float order,float time){
        if(amount<=0)return packed;
        float dark=ease(amount*2.1F);
        float wave=(float)Math.cos(14*order-16*time);
        float ridge=Math.max(0,wave);ridge*=ridge;
        int r=packed>>16&255,g=packed>>8&255,b=packed&255;
        return packed&0xff000000 | (int)(r+(83+39*ridge-r)*dark)<<16
                | (int)(g+(15+7*ridge-g)*dark)<<8 | (int)(b+(25+8*ridge-b)*dark);
    }
    void draw(GreeneGoddessRenderer.Group group,Matrix4f source,PoseStack.Pose root,VertexConsumer out,int light,int overlay){
        var surface=profile.surfaces.get(group);
        var v=surface.vertices;var colors=surface.colors;var order=surface.order;
        for(int i=0;i<colors.length;i+=4){
            boolean consumed=true;
            for(int j=0;j<4;j++){
                int k=(i+j)*8;
                source.transformPosition(v[k],v[k+1],v[k+2],quad[j]);
                amount[j]=carry(quad[j],order[i+j]);consumed&=amount[j]>=1;
            }
            if(consumed)continue;
            face.set(quad[1]).sub(quad[0]).cross(edge.set(quad[2]).sub(quad[0]));
            boolean hasFace=face.lengthSquared()>1.0e-12F;
            if(hasFace)face.normalize();
            for(int j=0;j<4;j++){
                int k=(i+j)*8;float f=amount[j];
                source.transformDirection(v[k+5],v[k+6],v[k+7],normal);
                if(normal.lengthSquared()>.000001F)normal.normalize();
                if(hasFace && f>0){
                    float mix=ease(f*4);
                    float direction=normal.dot(face)<0?-mix:mix;
                    normal.mul(1-mix).fma(direction,face);
                    if(normal.lengthSquared()>.000001F)normal.normalize();
                }
                int color=tint(colors[i+j],f,order[i+j],time);var p=quad[j];
                out.vertex(root.pose(),p.x,p.y,p.z).color(color).uv(v[k+3],v[k+4])
                        .overlayCoords(overlay).uv2(light).normal(root.normal(),normal.x,normal.y,normal.z).endVertex();
            }
        }
    }
}
