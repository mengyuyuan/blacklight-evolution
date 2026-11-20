package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.QuillOrgan;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A low, split blister. Pigment and vessels are drawn along the same face as the wound. */
final class QuillLesion {
    private static final Vec3 UP=new Vec3(0,1,0);
    private static final int SIDES=14;
    private static final int VEIN=0xff512937;
    private static final net.minecraft.resources.ResourceLocation TISSUE=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("blacklight","textures/entity/tissue_anatomy.png");
    private final QuillSkinContact.Surface face;
    private final double angle, radius, growth, beat;

    private QuillLesion(QuillOrgan q,QuillSkinContact.Surface face,double hostScale,float partial){
        this.face=face;
        angle=q.offset();
        growth=Mth.clamp((q.layers()-1)/9.,0,1);
        beat=Math.pow(Math.max(0,Math.sin((q.tickCount+partial)*.17+angle)),5);
        radius=(.115+.12*Math.sqrt(growth))*hostScale;
    }
    static void draw(QuillOrgan organ,QuillSkinContact.Surface face,double hostScale,float partial,
                     PoseStack pose,MultiBufferSource buffers,int light,double filling,double plunge){
        var lesion=new QuillLesion(organ,face,hostScale,partial);
        var stain=buffers.getBuffer(RenderType.entityTranslucent(ConsumedMobVisuals.WHITE));
        lesion.bruise(pose,stain,light);
        var mesh=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        lesion.vessels(pose,mesh,light);
        lesion.blister(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(TISSUE)),light,filling,plunge);
    }
    /** Coordinates in the long and short axes of this particular lesion, then clipped to the host face. */
    private Vec3 point(double x,double z,double height){
        double c=Math.cos(angle),s=Math.sin(angle);
        Vec3 p=new Vec3((x*c-z*s)*radius,height*radius,(x*s+z*c)*radius);
        return face!=null?face.fit(p):p;
    }
    private static void vertex(PoseStack stack,VertexConsumer out,int light,Vec3 p,Vec3 n,int color){
        ConsumedMobVisuals.vertex(stack,out,light,p,n,color,.5F,.5F);
    }
    private Vec3 rim(double a,double scale,double h){
        double ripple=1+.12*Math.sin(3*a+.8)+.07*Math.cos(5*a+angle);
        return point(Math.cos(a)*1.13*ripple*scale,Math.sin(a)*.73*ripple*scale,h);
    }
    private void bruise(PoseStack pose,VertexConsumer out,int light){
        for(int i=0;i<SIDES;i++){
            double a=i*Math.PI*2/SIDES,b=(i+1)*Math.PI*2/SIDES;
            for(int band=0;band<2;band++){
                double r=band==0?.43:1.03,outer=band==0?1.03:1.63;
                int inside=band==0?0xb4512432:0x824c2334, outside=band==0?0x824c2334:0x00372133;
                vertex(pose,out,light,rim(a,r,.017),UP,inside);
                vertex(pose,out,light,rim(b,r,.017),UP,inside);
                vertex(pose,out,light,rim(b,outer,.017),UP,outside);
                vertex(pose,out,light,rim(a,outer,.017),UP,outside);
            }
        }
    }
    private void vessel(PoseStack pose,VertexConsumer out,int light,Vec3[] path,double width,int color){
        for(int i=1;i<path.length;i++){
            var a=path[i-1];var b=path[i];var edge=b.subtract(a).cross(UP).normalize();
            if(edge.lengthSqr()<.1)continue;
            double start=width*(path.length-i)/(path.length-1),end=width*(path.length-i-1)/(path.length-1);
            vertex(pose,out,light,bound(a.add(edge.scale(start))),UP,color);
            vertex(pose,out,light,bound(b.add(edge.scale(end))),UP,color);
            vertex(pose,out,light,bound(b.subtract(edge.scale(end))),UP,color);
            vertex(pose,out,light,bound(a.subtract(edge.scale(start))),UP,color);
        }
    }
    private Vec3 bound(Vec3 p){return face==null?p:face.fit(p);}
    private void vessels(PoseStack pose,VertexConsumer out,int light){
        int count=3+(int)Math.round(growth*3);
        for(int i=0;i<count;i++){
            double a=i*2.39996+.34*Math.sin(i+angle);
            double c=Math.cos(a),s=Math.sin(a);
            double length=(1.15+.95*growth)*(1+.21*Math.sin(i*2.73+angle));
            double bend=.27*Math.sin(angle+i*1.9)+.09;
            Vec3 root=point(.53*c,.4*s,.084);
            Vec3 mid=point(length*.61*c-bend*s,length*.61*s+bend*c,.055);
            Vec3 fork=point(length*.89*c-.6*bend*s,length*.89*s+.6*bend*c,.04);
            Vec3 end=point(length*1.22*c+.38*bend*s,length*1.22*s-.38*bend*c,.028);
            vessel(pose,out,light,new Vec3[]{root,mid,fork,end},radius*(.087+.018*beat),VEIN);
            if(growth>.18 || i==0){
                Vec3 twig=point(length*(.92*c-.33*s),length*(.92*s+.33*c),.033);
                vessel(pose,out,light,new Vec3[]{mid,mid.lerp(twig,.54).add(0,.0005,0),twig},radius*.043,0xff69404b);
            }
            vessel(pose,out,light,new Vec3[]{root.add(0,.001,0),mid.add(0,.001,0),fork.add(0,.001,0)},
                    radius*.027,beat>.48?0xffb05858:0xff8a454b);
        }
    }
    /** Two unequal lips share a dark seam. They swell only over the advancing draw pulse. */
    private void blister(PoseStack pose,VertexConsumer mesh,int light,double filling,double plunge){
        double rise=(.27+.28*growth+.09*beat+.49*filling)*(1-.24*plunge);
        for(int side:new int[]{-1,1}){
            double width=side<0?.42:.75;
            for(int i=0;i<8;i++){
                double a=-1+2.*i/8,b=-1+2.*(i+1)/8;
                Vec3[] start=section(a,side,width,rise),end=section(b,side,width,rise);
                for(int k=0;k<3;k++){
                    Vec3 normal=end[k].subtract(start[k]).cross(start[k+1].subtract(start[k])).normalize();
                    if(normal.y<0)normal=normal.reverse();
                    int tint=k==0?0xff977a82:k==1?0xffedd4d4:0xffba8f97;
                    float u=(float)(.031+.17*(1+a)),v=(float)(.49+side*(.43-k*.12));
                    float ub=(float)(.031+.17*(1+b)),vb=v-side*.12F;
                    ConsumedMobVisuals.vertex(pose,mesh,light,start[k],normal,tint,u,v);
                    ConsumedMobVisuals.vertex(pose,mesh,light,end[k],normal,tint,ub,v);
                    ConsumedMobVisuals.vertex(pose,mesh,light,end[k+1],normal,tint,ub,vb);
                    ConsumedMobVisuals.vertex(pose,mesh,light,start[k+1],normal,tint,u,vb);
                }
                Vec3 innerA=start[3],innerB=end[3];
                int seam=0xffb58c99+(int)(beat*30)*0x010000;
                vertex(pose,mesh,light,innerA,UP,seam);vertex(pose,mesh,light,innerB,UP,seam);
                vertex(pose,mesh,light,point(b*1.02,crease(b),.035),UP,0xff2b141e);
                vertex(pose,mesh,light,point(a*1.02,crease(a),.035),UP,0xff2b141e);
            }
        }
    }
    private double crease(double x){return .14*Math.sin(2.7*x+.4)+.09*x;}
    private Vec3[] section(double x,int side,double width,double rise){
        double belly=Math.pow(Math.max(0,1-x*x),.67)*(1+.22*Math.sin(x*6+angle+side)+.13*side*x);
        double z=crease(x);
        return new Vec3[]{point(x*1.08,z+side*width*1.40*belly,-.035),
                point(x*1.04,z+side*width*.86*belly,rise*.55*belly+.028),
                point(x*1.02,z+side*width*.40*belly,rise*(side>0?1.08:.65)*belly+.04),
                point(x*1.02,z+side*.078*belly,rise*.50*belly+.035)};
    }
}
