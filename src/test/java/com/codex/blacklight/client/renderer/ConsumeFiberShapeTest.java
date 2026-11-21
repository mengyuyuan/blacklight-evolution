package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.ConsumeAtlasUv;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeFiberShapeTest {
    private record Face(int span,boolean fork,int side,Vec3 a,Vec3 b,Vec3 c,Vec3 d) {}
    private static List<Face> mesh(Vec3[] path,double[] widths){
        var faces=new ArrayList<Face>();
        ConsumeFiberShape.draw(path,widths,new Vec3(1,0,0),(s,f,k,a,b,c,d,v,w)->faces.add(new Face(s,f,k,a,b,c,d)));
        return faces;
    }
    private static Vec3 center(Face f){return f.a.add(f.b).add(f.c).add(f.d).scale(.25);}
    @Test void mainFibreHasContinuousSquareJoinsAndOnlySelectedSpansBranch(){
        var path=new Vec3[11];var widths=new double[11];
        for(int i=0;i<path.length;i++){path[i]=new Vec3(0,i*.14,0);widths[i]=.035;}
        var faces=mesh(path,widths);assertEquals(82,faces.stream().filter(f->!f.fork).count());
        assertEquals(24,faces.stream().filter(f->f.fork).count());
        assertEquals(List.of(1,4,8),faces.stream().filter(Face::fork).map(Face::span).distinct().toList());
        for(var f:faces){
            Vec3 normal=f.b.subtract(f.a).cross(f.c.subtract(f.a));
            assertTrue(normal.lengthSqr()>1e-13);
            if(!f.fork&&f.side<4){
                Vec3 middle=center(f),radial=new Vec3(middle.x,0,middle.z);
                assertTrue(normal.dot(radial)>0,"inside-out square face");
            }
        }
        for(int i=0;i<9;i++){
            int span=i;
            var before=faces.stream().filter(f->!f.fork&&f.side==0&&f.span==span).toList().get(1);
            var after=faces.stream().filter(f->!f.fork&&f.side==0&&f.span==span+1).findFirst().orElseThrow();
            assertEquals(before.c,after.b);assertEquals(before.d,after.a);
        }
        assertEquals(path[0],center(faces.get(0)));
        assertTrue(center(faces.get(faces.size()-1)).distanceTo(path[10])<1e-9);
    }

    @Test void coincidentGrowthAndVerticalReversalRemainFinite(){
        Vec3 o=Vec3.ZERO,u=new Vec3(0,.2,0);
        assertTrue(mesh(new Vec3[]{o,o,o},new double[]{.05,.05,.05}).isEmpty());
        assertTrue(mesh(new Vec3[]{o,u},new double[]{0,0}).isEmpty());
        var faces=mesh(new Vec3[]{o,o,u,o,new Vec3(0,-.3,0)},new double[]{.03,.03,.04,.03,.02});
        assertFalse(faces.isEmpty());
        for(var f:faces)for(var v:List.of(f.a,f.b,f.c,f.d))assertTrue(Double.isFinite(v.x+v.y+v.z));
    }

    @Test void authoredAtlasIsOpaquePixelArtAndBoxUvsJoinAtFrontEdge() throws Exception {
        try(InputStream resource=getClass().getResourceAsStream("/assets/blacklight/textures/entity/consume_charcoal_fiber.png")){
            assertNotNull(resource);var image=ImageIO.read(resource);
            assertEquals(32,image.getWidth());assertEquals(32,image.getHeight());var colors=new java.util.HashSet<Integer>();
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){
                int rgb=image.getRGB(x,y);assertEquals(255,rgb>>>24);colors.add(rgb);
            }
            assertEquals(8,colors.size());
        }
        Vec3 edge=new Vec3(.265,1.22,.14);
        float[] front=ConsumeAtlasUv.sample(ConsumeAnatomy.BODY,edge.toVector3f(),new Vector3f(0,0,1),false);
        float[] side=ConsumeAtlasUv.sample(ConsumeAnatomy.BODY,edge.toVector3f(),new Vector3f(1,0,0),false);
        assertArrayEquals(front,side,.0001F);
    }
}
