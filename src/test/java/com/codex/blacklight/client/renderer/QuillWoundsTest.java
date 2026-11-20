package com.codex.blacklight.client.renderer;

import java.util.*;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;

class QuillWoundsTest {
    private static List<Vertex> surface(Object owner,double z){
        var vertices=new ArrayList<Vertex>();
        for(double[] xy:new double[][]{{-1,-1},{1,-1},{1,1},{-1,1}})
            vertices.add(new Vertex(owner,null,new Vec3(xy[0],xy[1],z),new Vec3(0,0,1),(float)(xy[0]+1)/2,(float)(xy[1]+1)/2,-1,0,0));
        return vertices;
    }
    private static double edge(Vec3 a,Vec3 b,double x,double y){return (b.x-a.x)*(y-a.y)-(b.y-a.y)*(x-a.x);}
    private static boolean blocked(List<Vertex> mesh,double x,double y,double z){
        for(int i=0;i<mesh.size();i+=4)for(int k=1;k<=2;k++){
            var a=mesh.get(i).p;var b=mesh.get(i+k).p;var c=mesh.get(i+k+1).p;
            if(a.z!=z||b.z!=z||c.z!=z||Math.abs(edge(a,b,c.x,c.y))<1e-8)continue;
            double p=edge(a,b,x,y),q=edge(b,c,x,y),r=edge(c,a,x,y);
            if(Math.min(p,Math.min(q,r))>=-1e-8||Math.max(p,Math.max(q,r))<=1e-8)return true;
        }
        return false;
    }
    @Test void piercedExitReleasesOnlyTheSkinInsideTheFarRim(){
        var owner=new Object();var skin=new ArrayList<Vertex>();
        skin.addAll(surface(owner,0));skin.addAll(surface(owner,1));
        var cut=new ArrayList<Vertex>();var chip=new ArrayList<Vertex>();
        QuillWounds.perforate(skin,cut,owner,Vec3.ZERO,new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),.18,false,null,chip);
        assertTrue(chip.size()>=24);
        assertTrue(blocked(chip,0,0,1));
        assertFalse(blocked(cut,0,0,1));
        assertTrue(chip.stream().allMatch(v->v.p.z>.8 && v.p.z<=1 && v.key==owner));
        var outside=chip.stream().filter(v->v.color==-1).toList();
        assertTrue(outside.size()>=4);
        for(var v:outside){
            assertEquals((v.p.x+1)/2,v.u,1.0e-5);
            assertEquals((v.p.y+1)/2,v.v,1.0e-5);
        }
    }
    @Test void aBoreOpensBothSkinsAndPreservesNeighbouringFacesAndBones(){
        var owner=new Object();var neighbour=new Object();var skin=new ArrayList<Vertex>();
        skin.addAll(surface(owner,0));skin.addAll(surface(owner,1));var other=surface(neighbour,2);skin.addAll(other);
        var cut=new ArrayList<Vertex>();
        QuillWounds.perforate(skin,cut,owner,Vec3.ZERO,new Vec3(0,0,1),new Vec3(1,0,0),new Vec3(0,1,0),.18,false,null);
        for(double z:new double[]{0,1}){
            assertFalse(blocked(cut,0,0,z));assertFalse(blocked(cut,.075,-.035,z));
            assertTrue(blocked(cut,.25,.03,z));
        }
        assertTrue(cut.containsAll(other)); assertTrue(blocked(cut,0,0,2));
    }
}
