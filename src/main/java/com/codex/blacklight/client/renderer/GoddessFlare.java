package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Thin additive edges over the opaque blades. Vanilla lightning keeps these readable in a shaded cavern. */
final class GoddessFlare {
    static void stroke(PoseStack stack,VertexConsumer out,Vec3 start,Vec3 mid,Vec3 end,Vec3 edge,
                       double width,int root,int crest,int tail){
        var wing=edge.scale(width);
        var l=mid.subtract(wing);
        var r=mid.add(wing);
        point(stack,out,start,root);point(stack,out,l,crest);point(stack,out,end,tail);point(stack,out,r,crest);
        point(stack,out,r,crest);point(stack,out,end,tail);point(stack,out,l,crest);point(stack,out,start,root);
    }
    static void volley(PoseStack stack,MultiBufferSource buffers,boolean wave,boolean high,float age){
        var out=buffers.getBuffer(RenderType.lightning());
        if(wave){
            float sign=high?-1:1;
            // Keep every crest inside its own 1.6-block lane. Overlapping crests would erase the escape route.
            for(int i=-1;i<=1;i++){
                float x=.46F*i, rise=sign*(.34F+.12F*Mth.sin(age*.32F+i));
                var nose=new Vec3(x,0,.72);
                var comb=new Vec3(x,rise,-.48);
                var tail=new Vec3(x*.78,-rise*.5,-4.3-.55*i);
                stroke(stack,out,nose,comb,tail,new Vec3(1,0,0),.18,0xbffff1cd,0xe6ff7c8a,0x117c144e);
            }
            stroke(stack,out,new Vec3(-.75,0,.12),new Vec3(0,.13*sign,.70),new Vec3(.75,0,.12),
                    new Vec3(0,sign*.6,-.8),.24,0xbaff405a,0xffffede1,0xbaff405a);
        }else{
            for(int i=0;i<2;i++){
                Vec3 edge=i==0?new Vec3(1,0,0):new Vec3(0,1,0);
                stroke(stack,out,new Vec3(0,0,.65),new Vec3(0,0,-.75),new Vec3(0,0,-7.2),
                        edge,.31,0xfaffecdc,0xeaff7f89,0x077e214a);
            }
        }
    }
    static void petals(PoseStack stack, MultiBufferSource buffers,Vec3 source,Vec3 target,
                       float expansion,float radius,float alpha,int count){
        var axis=target.subtract(source).normalize();
        if(axis.lengthSqr()<.5 || alpha<.08)return;
        var right=axis.cross(Math.abs(axis.y)>.96?new Vec3(1,0,0):new Vec3(0,1,0)).normalize();
        var up=axis.cross(right);
        var out=buffers.getBuffer(RenderType.lightning());
        for(int i=0;i<count;i++){
            double angle=(i+.38)*Math.PI*2/count+.26*expansion;
            var radial=right.scale(Math.cos(angle)).add(up.scale(Math.sin(angle)));
            var wing=axis.cross(radial);
            var base=source.add(radial.scale(.55));
            var crest=source.add(radial.scale(radius*(.52+.16*expansion))).add(axis.scale(radius*.22));
            var end=source.add(radial.scale(radius*(.72+.3*expansion))).add(axis.scale(radius*(.48+.58*expansion)));
            stroke(stack,out,base,crest,end,wing,radius*.072,
                    (int)(198*alpha)<<24|0xe95975,(int)(235*alpha)<<24|0xffafb5,(int)(22*alpha)<<24|0xfa385f);
        }
    }
    private static void point(PoseStack stack,VertexConsumer out,Vec3 p,int color){
        out.vertex(stack.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color).endVertex();
    }
    private GoddessFlare(){}
}
