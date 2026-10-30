package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;

/** Small lit cuboids and tapered prisms; no billboard item sprites or new texture sheets. */
public final class GunplayGeometry {
    private static void vertex(PoseStack p,VertexConsumer out,Vec3 v,Vec3 n,int color,int light){out.addVertex(p.last(),(float)v.x,(float)v.y,(float)v.z).setColor(color).setUv(.5F,.5F).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p.last(),(float)n.x,(float)n.y,(float)n.z);}
    public static void rod(PoseStack pose,VertexConsumer out,Vec3 from,Vec3 to,double r0,double r1,int color,int light){
        Vec3 axis=to.subtract(from).normalize(),u=axis.cross(new Vec3(0,1,0));if(u.lengthSqr()<.01)u=new Vec3(1,0,0);u=u.normalize();var v=axis.cross(u);
        for(int i=0;i<4;i++){
            double a=i*Math.PI/2+Math.PI/4,b=(i+1)*Math.PI/2+Math.PI/4;
            var n=u.scale(Math.cos(a)).add(v.scale(Math.sin(a)));var m=u.scale(Math.cos(b)).add(v.scale(Math.sin(b)));var face=n.add(m).normalize();
            vertex(pose,out,from.add(n.scale(r0)),face,color,light);vertex(pose,out,to.add(n.scale(r1)),face,color,light);vertex(pose,out,to.add(m.scale(r1)),face,color,light);vertex(pose,out,from.add(m.scale(r0)),face,color,light);
        }
    }
    public static void box(PoseStack p,VertexConsumer out,Vec3 c,double x,double y,double z,int color,int light){
        var v=new Vec3[]{c.add(-x/2,-y/2,-z/2),c.add(x/2,-y/2,-z/2),c.add(x/2,y/2,-z/2),c.add(-x/2,y/2,-z/2),c.add(-x/2,-y/2,z/2),c.add(x/2,-y/2,z/2),c.add(x/2,y/2,z/2),c.add(-x/2,y/2,z/2)};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        for(var f:faces){var n=v[f[1]].subtract(v[f[0]]).cross(v[f[2]].subtract(v[f[0]])).normalize();for(int i:f)vertex(p,out,v[i],n,color,light);}
    }
    public static int alpha(int rgb,double opacity){return (Math.clamp((int)(opacity*255),0,255)<<24)|(rgb&0xffffff);}
    private GunplayGeometry(){}
}
