package com.codex.blacklight.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceLocation;

/** Faceted Minecraft-style biomass with fixed pixel UVs and continuously bending limbs. */
public final class BiomassGeometry {
    public static final ResourceLocation TEXTURE = new ResourceLocation("blacklight", "textures/entity/claw_biomass.png");
    private BiomassGeometry(){}
    public static void blob(PoseStack stack,VertexConsumer out,int light,double time,double movement,double stretch,double rise){
        crawler(stack,out,light,time,movement,false,null,Vec3.ZERO,0,stretch);
    }
    public static void crawler(PoseStack s,VertexConsumer out,int light,double time,double movement,boolean fatal,
                               TentacleGait gait,Vec3 worldBody,float yaw,double stretch){
        // A single connected stepped liquid surface. Motion deforms its volume;
        // these are broad pixel lobes, not eight independently walking tentacles.
        double length=(1+movement*.65)*stretch,width=1/Math.sqrt(length);
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
            double nx=x/4.4,nz=z/4.4,edge=nx*nx+nz*nz;
            double ripple=.06*Math.sin(time*.10+x*.8+z*.55);
            if(edge>1+ripple)continue;
            double height=.045+.22*Math.pow(Math.max(0,1-edge),.65)+.012*Math.sin(time*.16-z*.7+x*.4);
            double cx=x*.085*width,cz=z*.085*length-movement*.055;
            int color=(x+z)%3==0?0xff343034:(x-z)%4==0?0xff29272b:0xff201e23;
            box(s,out,light,cx-.044*width,.015,cz-.044*length,cx+.044*width,height,cz+.044*length,color);
            if(edge<.65&&(x*7+z*3)%11==0)
                box(s,out,light,cx-.018,height,cz-.035,cx+.018,height+.009,cz+.035,0xff80313b);
        }
        // Short overlapping tail cells flatten as the body accelerates and fold back when stopped.
        for(int i=0;movement>.01&&i<5;i++){
            double tail=movement*(1-i*.14),z=-.28*length-i*.075*movement;
            double half=.11*tail+.025,side=Math.sin(time*.11-i*.6)*.045*movement;
            box(s,out,light,side-half,.016,z-.06,side+half,.035+tail*.035,z+.06,0xff282329);
        }
    }
    public static void ellipsoid(PoseStack s,VertexConsumer out,int light,Vec3 c,double rx,double ry,double rz,double time,int color){
        Vec3[] previous=null;
        final int sides=8,bands=4;
        for(int j=0;j<=bands;j++){
            double lat=-Math.PI/2+Math.PI*j/bands;Vec3[] ring=new Vec3[sides];
            for(int i=0;i<sides;i++){
                double a=i*Math.PI*2/sides+Math.PI/8,ripple=1+.025*Math.sin(a*3+lat*4-time*.12)*Math.cos(lat);
                double belt=Math.pow(Math.max(0,Math.cos(lat)),.55);
                ring[i]=c.add(Math.cos(a)*belt*rx*ripple,Math.sin(lat)*ry,Math.sin(a)*belt*rz*ripple);
            }
            if(previous!=null)for(int i=0;i<sides;i++){
                texturedQuad(s,out,light,previous[i],ring[i],ring[(i+1)%sides],previous[(i+1)%sides],color,
                        i/(float)sides*.5F,(j-1)/(float)bands*.5F,(i+1)/(float)sides*.5F,j/(float)bands*.5F);
            }
            previous=ring;
        }
    }
    public static void strand(PoseStack stack,VertexConsumer out,int light,Vec3[] points,double rootRadius,double tipRadius){
        final int sides=6;
        Vec3[] previous=null,first=null;Vec3 lastSide=null;
        for(int step=0;step<points.length;step++){
            Vec3 direction=points[Math.min(points.length-1,step+1)].subtract(points[Math.max(0,step-1)]).normalize();
            Vec3 side=lastSide==null?direction.cross(Math.abs(direction.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize()
                    :lastSide.subtract(direction.scale(lastSide.dot(direction))).normalize();
            if(side.lengthSqr()<.001)side=direction.cross(new Vec3(1,0,0)).normalize();
            Vec3 up=side.cross(direction).normalize();lastSide=side;
            double t=step/(points.length-1D),radius=tipRadius+(rootRadius-tipRadius)*Math.pow(1-t,.8);
            Vec3[] ring=new Vec3[sides];
            for(int edge=0;edge<sides;edge++){
                double a=edge*Math.PI*2/sides;
                ring[edge]=points[step].add(side.scale(Math.cos(a)*radius)).add(up.scale(Math.sin(a)*radius));
            }
            if(previous==null)first=ring;
            else for(int edge=0;edge<sides;edge++){
                int next=(edge+1)%sides;
                texturedQuad(stack,out,light,previous[edge],ring[edge],ring[next],previous[next],0xffffffff,
                        edge/(float)sides*.25F,(step-1)/(float)(points.length-1),(edge+1)/(float)sides*.25F,step/(float)(points.length-1));
            }
            previous=ring;
        }
        for(int i=1;i<sides-1;i++){
            quad(stack,out,light,first[0],first[i],first[i+1],first[i+1],0xffffffff);
            quad(stack,out,light,previous[0],previous[i+1],previous[i],previous[i],0xffffffff);
        }
    }
    public static void tendril(PoseStack stack,VertexConsumer out,int light,Vec3 root,Vec3 end,double age,double extent){
        if(end.distanceToSqr(root)<.000001||extent<.001)return;
        Vec3[] points=new Vec3[21];
        for(int j=0;j<points.length;j++){
            double t=j/(points.length-1D);
            points[j]=root.lerp(end,t).add(0,Math.sin(t*Math.PI)*(.65+.3*Math.sin(age*.65)*(1-extent)),0);
        }
        strand(stack,out,light,points,.20*extent,.055*extent);
    }
    // UVs are attached to the mesh, not world position or animation time.
    // Flat normals preserve broad faces while the connected rings bend without gaps.
    private static void texturedQuad(PoseStack s,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color,float u0,float v0,float u1,float v1){
        Vec3 normal=b.subtract(a).cross(c.subtract(a));
        if(normal.lengthSqr()<1e-12)normal=c.subtract(a).cross(d.subtract(a));
        normal=normal.normalize();
        vertexUv(s,out,light,a,normal,color,u0,v0);vertexUv(s,out,light,b,normal,color,u0,v1);
        vertexUv(s,out,light,c,normal,color,u1,v1);vertexUv(s,out,light,d,normal,color,u1,v0);
    }
    private static void quad(PoseStack s,VertexConsumer out,int light,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int color){
        Vec3 normal=b.subtract(a).cross(c.subtract(a)).normalize();
        vertex(s,out,light,a,normal,color);vertex(s,out,light,b,normal,color);vertex(s,out,light,c,normal,color);vertex(s,out,light,d,normal,color);
    }
    public static void liquidCell(PoseStack s,VertexConsumer out,int light,double x,double z,double cell,double base,double h,int color,double west,double east,double north,double south){
        double y=base+h;
        quad(s,out,light,new Vec3(x,y,z),new Vec3(x,y,z+cell),new Vec3(x+cell,y,z+cell),new Vec3(x+cell,y,z),color);
        if(h>west)quad(s,out,light,new Vec3(x,base+west,z),new Vec3(x,base+west,z+cell),new Vec3(x,y,z+cell),new Vec3(x,y,z),color);
        if(h>east)quad(s,out,light,new Vec3(x+cell,base+east,z+cell),new Vec3(x+cell,base+east,z),new Vec3(x+cell,y,z),new Vec3(x+cell,y,z+cell),color);
        if(h>north)quad(s,out,light,new Vec3(x+cell,base+north,z),new Vec3(x,base+north,z),new Vec3(x,y,z),new Vec3(x+cell,y,z),color);
        if(h>south)quad(s,out,light,new Vec3(x,base+south,z+cell),new Vec3(x+cell,base+south,z+cell),new Vec3(x+cell,y,z+cell),new Vec3(x,y,z+cell),color);
    }
    public static void box(PoseStack s,VertexConsumer out,int light,double x0,double y0,double z0,double x1,double y1,double z1,int color){
        Vec3[] v={new Vec3(x0,y0,z0),new Vec3(x1,y0,z0),new Vec3(x1,y1,z0),new Vec3(x0,y1,z0),new Vec3(x0,y0,z1),new Vec3(x1,y0,z1),new Vec3(x1,y1,z1),new Vec3(x0,y1,z1)};
        int[][] faces={{0,3,2,1},{4,5,6,7},{0,4,7,3},{1,2,6,5},{3,7,6,2},{0,1,5,4}};
        for(var face:faces){Vec3 normal=v[face[1]].subtract(v[face[0]]).cross(v[face[2]].subtract(v[face[0]])).normalize();for(int i:face)vertex(s,out,light,v[i],normal,color);}
    }
    private static void vertex(PoseStack s,VertexConsumer out,int light,Vec3 p,Vec3 n,int color){
        vertexUv(s,out,light,p,n,color,.5F,.5F);
    }
    private static void vertexUv(PoseStack s,VertexConsumer out,int light,Vec3 p,Vec3 n,int color,float u,float v){
        out.vertex(s.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(color).uv(u,v)
            .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(s.last().normal(),(float)n.x,(float)n.y,(float)n.z).endVertex();
    }
}
