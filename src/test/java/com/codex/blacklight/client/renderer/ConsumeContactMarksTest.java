package com.codex.blacklight.client.renderer;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import com.codex.blacklight.client.renderer.ConsumeFracture.V;

class ConsumeContactMarksTest {
    private static final Vec3 X=new Vec3(1,0,0),Y=new Vec3(0,1,0),Z=new Vec3(0,0,1);
    private static void close(Vec3 expected,Vec3 actual) {
        assertTrue(expected.distanceTo(actual)<1e-6,expected+" vs "+actual);
    }

    @Test void heldToMobileMaintainsContactIdentityWhileVictimsAndOwnersRemainIndependent() {
        var heldVictim=new ConsumeContactMarks.SceneHits();
        var otherVictim=new ConsumeContactMarks.SceneHits();
        assertTrue(heldVictim.accept(80,2,"dent",0));
        assertFalse(heldVictim.accept(80,2,"dent",0));
        assertTrue(otherVictim.accept(80,2,"dent",0));
        assertTrue(heldVictim.accept(80,2,"dent",1));
        assertTrue(heldVictim.accept(80,7,"dent",0));
        assertTrue(heldVictim.accept(90,7,"dent",0));
    }

    @Test void surfacePointAndStrokeFollowTheSameTriangleAfterTurnScaleAndTranslation() {
        Vec3 a=new Vec3(1,2,3),b=a.add(X.scale(3)),c=a.add(Y.scale(2));
        Vec3 exact=a.add(X.scale(.45)).add(Y.scale(.52));
        Vec3 hit=ConsumeContactMarks.barycentric(exact,a,b,c);
        Vec3 stroke=MountedConsumeWound.tangentWeights(X.scale(1.5).add(Y.scale(.4)).add(Z),a,b,c);
        close(exact,ConsumeContactMarks.weighted(hit,a,b,c));
        Vec3 next=new Vec3(-22,0,7),b2=next.add(Y.scale(6)),c2=next.add(Z);
        close(next.add(Y.scale(.9)).add(Z.scale(.26)),ConsumeContactMarks.weighted(hit,next,b2,c2));
        close(Y.scale(3).add(Z.scale(.2)),ConsumeContactMarks.weighted(stroke,next,b2,c2));
    }

    private static List<V> skin(int region,boolean quads){
        var vs=new ArrayList<V>();
        for(Vec3 p:List.of(new Vec3(-1,-1,0),new Vec3(1,-1,0),new Vec3(1,1,0),new Vec3(-1,1,0))){
            V v=new V();v.p=p.toVector3f();v.weights=new float[7];v.weights[region]=1;
            v.u=(float)(p.x+1)*.5f;v.v=(float)(p.y+1)*.5f;v.nz=1;vs.add(v);
        }
        if(!quads){var q=List.copyOf(vs);vs.clear();for(int i:new int[]{0,1,2,0,2,3})vs.add(q.get(i));}
        return vs;
    }
    @Test void nativeStrongReceiverMissesAndOtherRegionsKeepOriginalGeometry(){
        var vs=skin(1,true);var original=List.copyOf(vs);
        assertFalse(MountedConsumeWound.patch(vs,true,1,.24,"crush",Z,Z.scale(-1),X,null,null));
        assertFalse(MountedConsumeWound.patch(vs,true,0,.24,"tear",Vec3.ZERO,Z.scale(-1),X,null,null));
        assertEquals(original,vs);
    }

    @Test void nativeStrongReceiverRecessIsUnderTheOffCentreHitForBothQuadsAndTriangles(){
        Vec3 p=new Vec3(.54,-.36,0);
        for(boolean quads:List.of(true,false)){
            var vs=skin(1,quads);
            assertTrue(MountedConsumeWound.patch(vs,quads,1,.23,"crush",p,Z.scale(-1),Y,null,null));
            assertEquals(0,vs.size()%(quads?4:3));
            int inner=0;
            for(V v:vs){
                assertEquals(1.0,v.weights[1],.0001);
                if(v.p.z<-.04){
                    inner++;assertTrue(Math.hypot(v.p.x-p.x,v.p.y-p.y)<.32);
                }
            }
            assertTrue(inner>=3);
        }
    }

    @Test void aTearIsNarrowAndFollowsTheContactTangent(){
        for(boolean horizontal:List.of(true,false)){
            var vs=skin(0,true);
            assertTrue(MountedConsumeWound.patch(vs,true,0,.28,"tear",Vec3.ZERO,Z.scale(-1),horizontal?X:Y,null,null));
            var inner=vs.stream().filter(v->v.p.z<-.03).toList();
            assertFalse(inner.isEmpty());
            double dx=inner.stream().mapToDouble(v->v.p.x).max().getAsDouble()-inner.stream().mapToDouble(v->v.p.x).min().getAsDouble();
            double dy=inner.stream().mapToDouble(v->v.p.y).max().getAsDouble()-inner.stream().mapToDouble(v->v.p.y).min().getAsDouble();
            assertTrue(horizontal?dx>dy*2:dy>dx*2);
        }
    }
}
