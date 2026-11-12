package com.codex.blacklight.client.renderer;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the actual production clip/cap path, including successive cuts. */
class SectionMeshTest {
    static Class<?> nested(String name){return Arrays.stream(AdaptiveRagdoll.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals(name)).findFirst().orElseThrow();}
    static Object construct(Class<?> type,Object... args)throws Exception{
        var c=Arrays.stream(type.getDeclaredConstructors()).filter(x->x.getParameterCount()==args.length).findFirst().orElseThrow();c.setAccessible(true);return c.newInstance(args);
    }
    static Object value(Object object,String field)throws Exception{var f=object.getClass().getDeclaredField(field);f.setAccessible(true);return f.get(object);}
    static List<Object> skin(List<List<Vec3>> faces)throws Exception{
        var result=new ArrayList<Object>();Object key=new Object();
        for(var face:faces){var n=face.get(1).subtract(face.getFirst()).cross(face.get(2).subtract(face.getFirst())).normalize();
            for(var p:face)result.add(construct(nested("Vertex"),key,null,p,n,(float)p.x,(float)p.y,-1,0,0));}
        return result;
    }
    @SuppressWarnings("unchecked") static List<Object> clip(List<Object> skin,Vec3 point,Vec3 normal,int side)throws Exception{
        Object part=construct(nested("Part"),value(skin.getFirst(),"key"),skin,null);
        Object plane=construct(nested("Boundary"),point,normal,1);
        var method=AdaptiveRagdoll.class.getDeclaredMethod("clip",nested("Part"),List.class,nested("Boundary"),int.class,net.minecraft.client.renderer.RenderType.class);method.setAccessible(true);
        return (List<Object>)method.invoke(null,part,skin,plane,side,null);
    }
    static List<List<Vec3>> faces(List<Object> skin)throws Exception{
        var result=new ArrayList<List<Vec3>>();
        for(int i=0;i<skin.size();i+=4){var f=new ArrayList<Vec3>();for(int j=0;j<4;j++)f.add((Vec3)value(skin.get(i+j),"p"));result.add(f);}
        return result;
    }
    @Test void diagonalSectionProducesTwoClosedSolidsWithConservedVolumeAndUv()throws Exception{
        var original=skin(SolidBladeCutTest.box());var normal=new Vec3(1,2,3).normalize();var point=new Vec3(.1,-.2,0);
        double volume=0;
        for(int side:new int[]{-1,1}){
            var half=clip(original,point,normal,side);var faces=faces(half);
            assertTrue(FiniteMeshCut.closed(faces,SolidBladeCutTest.V));volume+=SolidBladeCut.volume(faces,SolidBladeCutTest.V);
            int caps=0;for(var v:half){var p=(Vec3)value(v,"p");assertTrue(p.subtract(point).dot(normal)*side>=-1e-7);
                if((Integer)value(v,"color")==0xff70231f){caps++;assertEquals(0,p.subtract(point).dot(normal),1e-7);}
                else{assertEquals(p.x,((Float)value(v,"u")).doubleValue(),1e-6);assertEquals(p.y,((Float)value(v,"v")).doubleValue(),1e-6);}}
            assertTrue(caps>0);
        }
        assertEquals(8,volume,1e-6);
    }
    @Test void fourDistinctClawPlanesMakeFiveClosedSlicesInsideOneBone()throws Exception{
        List<List<Object>> pieces=List.of(skin(SolidBladeCutTest.box()));
        for(double x:new double[]{-.6,-.2,.2,.6}){
            var next=new ArrayList<List<Object>>();
            for(var p:pieces){
                var front=clip(p,new Vec3(x,0,0),new Vec3(1,0,0),1);var back=clip(p,new Vec3(x,0,0),new Vec3(1,0,0),-1);
                if(!front.isEmpty())next.add(front);if(!back.isEmpty())next.add(back);
            }pieces=next;
        }
        assertEquals(5,pieces.size());double volume=0;
        for(var piece:pieces){var f=faces(piece);assertTrue(FiniteMeshCut.closed(f,SolidBladeCutTest.V));volume+=SolidBladeCut.volume(f,SolidBladeCutTest.V);}
        assertEquals(8,volume,1e-6);
    }
    @Test void planeThroughCubeEdgesStillClosesTheSection()throws Exception{
        var skin=skin(SolidBladeCutTest.box());
        for(int sign:new int[]{-1,1})assertTrue(FiniteMeshCut.closed(faces(clip(skin,Vec3.ZERO,new Vec3(1,1,0).normalize(),sign)),SolidBladeCutTest.V));
    }
    @Test void anOpenWingDoesNotAcquireAFakeCap()throws Exception{
        var skin=skin(List.of(ContactSectionTest.face()));
        for(int sign:new int[]{-1,1})for(var v:clip(skin,Vec3.ZERO,new Vec3(1,0,0),sign))assertNotEquals(0xff70231f,value(v,"color"));
    }
}
