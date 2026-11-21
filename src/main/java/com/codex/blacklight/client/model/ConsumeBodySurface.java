package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.client.renderer.ConsumeAnatomy;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/** Skinned relief for the authored charcoal material. All UVs, joint weights and ridge heights
 * are in bind space, so a sleeve's weave follows the elbow when the grip opens. */
public final class ConsumeBodySurface {
    private record Point(float x,float y,float z,float nx,float ny,float nz,int a,int b,float weight,
                         float u,float v,float distance) {}
    private record Panel(int region,Point[] points,int columns,float[] posed) {
        Panel(int region,Point[] points,int columns){this(region,points,columns,new float[points.length*7]);}
    }
    private final List<Panel> panels=new ArrayList<>();
    private static final String MAP="/assets/blacklight/textures/entity/consume_charcoal_skin.png";
    private static final int SIZE=256;
    private static final float[] HEIGHT=heightMap();

    private static float[] heightMap() {
        float[] data=new float[SIZE*SIZE];
        try(var stream=ConsumeBodySurface.class.getResourceAsStream(MAP)){
            if(stream!=null){
                BufferedImage image=ImageIO.read(stream);
                for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){
                    int rgba=image.getRGB(x*image.getWidth()/SIZE,y*image.getHeight()/SIZE);
                    data[y*SIZE+x]=(rgba>>>24)/255F*(.016F+.035F*Math.min(1F,(rgba>>8&255)/56F));
                }
            }
        } catch(java.io.IOException ignored){ /* the material still draws flat */ }
        return data;
    }
    private static float relief(float u,float v){
        int x=(int)Math.floor(u*SIZE)&(SIZE-1), y=(int)Math.floor(v*SIZE)&(SIZE-1);
        return HEIGHT[y*SIZE+x];
    }
    private static Point point(int region,Vector3f p,Vector3f n,int a,int b,float weight,float u,float v){
        // Approximate skin distance from Spine_2 on each retargeted mesh. The diffuse alpha
        // and normal map supply the fibers over the whole advancing front.
        float distance=region==ConsumeAnatomy.BODY
                ?.66F*Math.abs(p.y-1.27F)+.26F*Math.abs(p.x)+.025F*(1-n.z)
                :.16F+.41F*Math.max(0,1.51F-p.y)+.035F*(1-n.z);
        return new Point(p.x,p.y,p.z,n.x,n.y,n.z,a,b,weight,2.5F*u,2.5F*v,distance);
    }

    /** The independent Mercer mesh supplies its actual posed weights and clothing surfaces. */
    void addMercerFace(int region,Vector3f[] p,Vector3f[] n,int[] a,int[] b,float[] w){
        Point[] corners=new Point[4];
        for(int i=0;i<4;i++){
            float[] uv=ConsumeAtlasUv.sample(region,p[i],n[0],true);
            corners[i]=point(region,p[i],n[i],a[i],b[i],w[i],uv[0],uv[1]);
        }
        panels.add(new Panel(region,new Point[]{corners[0],corners[1],corners[3],corners[2]},2));
    }

    public static ConsumeBodySurface player(boolean slim){
        var mesh=new ConsumeBodySurface();
        mesh.box(ConsumeAnatomy.BODY,0,4,2);
        float half=slim?1.5F:2;
        mesh.box(ConsumeAnatomy.RIGHT_ARM,4+half,half,2);
        mesh.box(ConsumeAnatomy.LEFT_ARM,-4-half,half,2);
        return mesh;
    }
    private void box(int region,float center,float halfWidth,float halfDepth){
        var bones=PlayerMotionController.skeleton();
        // Rounded outward normals close the rim between faces when the ridges lift off the skin.
        for(int face=0;face<6;face++){
            int cols=face<2?16:face<4?8:12, rows=face<4?22:8;
            Point[] points=new Point[(cols+1)*(rows+1)];
            Vector3f axis=switch(face){
                case 0->new Vector3f(0,0,1);case 1->new Vector3f(0,0,-1);
                case 2->new Vector3f(1,0,0);case 3->new Vector3f(-1,0,0);
                case 4->new Vector3f(0,1,0);default->new Vector3f(0,-1,0);};
            for(int j=0;j<=rows;j++)for(int i=0;i<=cols;i++){
                float s=2F*i/cols-1,t=2F*j/rows-1;
                float x=face<2?s:face<4?axis.x:s;
                float z=face<2?axis.z:face<4?s:t;
                float y=face<4?-t:axis.y;
                Vector3f normal=new Vector3f(x*powEdge(x),y*powEdge(y),z*powEdge(z)).normalize();
                float modelY=(1-y)*6;
                Vector3f pos=new Vector3f((center+x*(halfWidth+.32F))/16,1.501F-(modelY-y*.32F)/16,z*(halfDepth+.32F)/16);
                var weights=BlacklightSkinWeights.sample(region,modelY,false);
                float[] tex=ConsumeAtlasUv.sample(region,pos,axis,false);
                points[j*(cols+1)+i]=point(region,pos,normal,bones.searchJointByName(weights.first()).getId(),
                        bones.searchJointByName(weights.second()).getId(),weights.secondWeight(),tex[0],tex[1]);
            }
            panels.add(new Panel(region,points,cols+1));
        }
    }
    private static float powEdge(float n){float q=n*n;return q*q*q;}

    public void draw(PoseStack stack,VertexConsumer out,int light,Matrix4f[] skin,int side,float reach,float seconds,float textureClock){
        Vector3f p=new Vector3f(),other=new Vector3f(),n=new Vector3f(),otherN=new Vector3f(),base=new Vector3f();
        // Each lattice vertex is skinned once, even where four neighbouring cells share it.
        for(var panel:panels){
            if(side!=-2&&(panel.region==ConsumeAnatomy.BODY||side>=0&&panel.region!=side+2))continue;
            Point[] grid=panel.points;int columns=panel.columns;
            float[] posed=panel.posed;
            float du=.002F*textureClock,dv=.22F*textureClock;
            for(int i=0;i<grid.length;i++){
                Point v=grid[i];
                float local=Math.max(0,Math.min(1,(reach-v.distance)/.11F));
                if(local<=0){posed[7*i+6]=0;continue;}
                local=local*local*(3-2*local);
                float pulse=1+.14F*(float)Math.sin(seconds*8-18*v.distance);
                float lift=(.003F+relief(v.u+du,v.v+dv)*pulse)*local;
                base.set(v.x+v.nx*lift,v.y+v.ny*lift,v.z+v.nz*lift);
                skin[v.a].transformPosition(base,p);skin[v.b].transformPosition(base,other);p.lerp(other,v.weight);
                base.set(v.nx,v.ny,v.nz);
                skin[v.a].transformDirection(base,n);skin[v.b].transformDirection(base,otherN);n.lerp(otherN,v.weight).normalize();
                int k=7*i;posed[k]=p.x;posed[k+1]=p.y;posed[k+2]=p.z;posed[k+3]=n.x;posed[k+4]=n.y;posed[k+5]=n.z;posed[k+6]=local;
            }
            for(int j=0;j+columns<grid.length;j++){
                if((j+1)%columns==0)continue;
                int k=j+columns;
                if(posed[7*j+6]+posed[7*(j+1)+6]+posed[7*k+6]+posed[7*(k+1)+6]==0)continue;
                emit(stack,out,light,grid,posed,j,skin,p,other,n,du,dv);
                emit(stack,out,light,grid,posed,j+1,skin,p,other,n,du,dv);
                emit(stack,out,light,grid,posed,k+1,skin,p,other,n,du,dv);
                emit(stack,out,light,grid,posed,k,skin,p,other,n,du,dv);
            }
        }
    }
    private static void emit(PoseStack stack,VertexConsumer out,int light,Point[] grid,float[] posed,int index,
                             Matrix4f[] skin,Vector3f p,Vector3f other,Vector3f n,float du,float dv){
        Point v=grid[index];int k=index*7;
        if(posed[k+6]==0){
            p.set(v.x,v.y,v.z);other.set(p);
            skin[v.a].transformPosition(p).lerp(skin[v.b].transformPosition(other),v.weight);
            skin[v.a].transformDirection(n.set(v.nx,v.ny,v.nz)).normalize();
        }else{p.set(posed[k],posed[k+1],posed[k+2]);n.set(posed[k+3],posed[k+4],posed[k+5]);}
        int color=((int)(255*posed[k+6])<<24)|0xffffff;
        out.addVertex(stack.last(),p.x,p.y,p.z).setColor(color).setUv(v.u+du,v.v+dv)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(stack.last(),n.x,n.y,n.z);
    }
}
