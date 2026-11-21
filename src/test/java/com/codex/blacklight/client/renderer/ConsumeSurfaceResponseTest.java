package com.codex.blacklight.client.renderer;

import java.util.*;
import com.codex.blacklight.client.renderer.AdaptiveRagdoll.Vertex;
import com.codex.blacklight.client.renderer.ConsumeImpactResponse.Impact;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsumeSurfaceResponseTest {
    static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    static List<Vertex> quad(){
        var mesh=new ArrayList<Vertex>();
        for(var p:List.of(new Vec3(-.45,-.45,0),new Vec3(.45,-.45,0),new Vec3(.45,.45,0),new Vec3(-.45,.45,0)))
            mesh.add(new Vertex(null,null,p,Z,0,0,-1,0,0));
        return mesh;
    }
    @Test void allSixContactFieldsProduceDifferentShapesInSubmittedSubdivisions(){
        Set<String> unique=new HashSet<>();
        for(var mode:ConsumeSurfaceResponse.values()){
            var field=ContactCompression.grip(Vec3.ZERO,Z,Y,.36,0,mode,2,10);
            var vertices=field.surface(quad());
            double peak=vertices.stream().mapToDouble(v->field.point(v.p).distanceTo(v.p)).max().orElseThrow();
            assertTrue(vertices.size()>4);
            assertTrue(peak>.05&&peak<.22,mode+" "+peak);
            assertEquals(X,field.point(X));
            Vec3 middle=field.point(Vec3.ZERO),normal=field.normal(new Vec3(.03,-.07,0),Z);
            assertEquals(1,normal.length(),1e-6);
            assertTrue(unique.add(String.format(Locale.ROOT,"%.3f,%.3f,%.3f",middle.x,middle.y,middle.z)));
        }
    }
    @Test void twoSeparateArmsDeformBothSkinPatchesAndDoNotDoubleCountACoincidentPress(){
        Vec3 a=X.scale(.21),b=a.scale(-1);
        var one=ContactCompression.grip(a,Z,Y,.24,0,ConsumeSurfaceResponse.BEARHUG,2,10);
        Vec3 atOne=one.point(a);
        one.addGrip(ContactCompression.grip(a,Z,Y,.24,0,ConsumeSurfaceResponse.BEARHUG,2,10));
        assertEquals(atOne,one.point(a));
        one.addGrip(ContactCompression.grip(b,Z,Y,.24,0,ConsumeSurfaceResponse.BEARHUG,2,10));
        var vs=one.surface(quad());assertTrue(vs.stream().anyMatch(v->v.p.x<0&&one.point(v.p).z>.02));
        assertTrue(vs.stream().anyMatch(v->v.p.x>0&&one.point(v.p).z>.02));
    }
    @Test void kneeLiftsJawNelsonRaisesElbowsAndGuillotineFoldsTheHead(){
        var knee=ConsumeImpactResponse.heldMotion(Impact.KNEE,1,2,1,Z,X);
        var nelson=ConsumeImpactResponse.heldMotion(Impact.NELSON,2,2,1,Z,X);
        var chin=ConsumeImpactResponse.heldMotion(Impact.NELSON,1,2,1,Z,X);
        var fold=ConsumeImpactResponse.heldMotion(Impact.GUILLOTINE,1,2,1,Z,X);
        assertTrue(knee.y>.11&&nelson.y>.13&&chin.y<0&&fold.y<-.12);
        var left=ConsumeImpactResponse.heldMotion(Impact.NECK,1,2,-1,Z,X);
        var right=ConsumeImpactResponse.heldMotion(Impact.NECK,1,2,1,Z,X);
        assertEquals(-left.x,right.x,1e-8);
        for(var mode:Impact.values())assertEquals(0,ConsumeImpactResponse.heldMotion(mode,1,14,1,Z,X).length(),1e-8);
    }
    @Test void nativeMountReactionsHaveUniqueSignedContactLoadAndLocalRelease(){
        Set<String> different=new HashSet<>();
        for(var mode:MountedSurfaceResponse.values()){
            var first=mode.delta(Vec3.ZERO,Vec3.ZERO,Z,Y,.3,.5);
            assertTrue(different.add(String.format(Locale.ROOT,"%.3f,%.3f",first.y,first.z)),mode.name());
            assertEquals(0,mode.delta(X.scale(5),Vec3.ZERO,Z,Y,.3,1).length(),1e-8);
            assertEquals(0,mode.delta(Vec3.ZERO,Vec3.ZERO,Z,Y,.3,9).length(),1e-8);
        }
        assertTrue(MountedSurfaceResponse.HAMMER.delta(Vec3.ZERO,Vec3.ZERO,Z,Y,.3,.5).z>0);
        assertTrue(MountedSurfaceResponse.MUSCLE.delta(Vec3.ZERO,Vec3.ZERO,Z,Y,.3,.5).z<0);
    }
}
