package com.codex.blacklight.client.renderer;

import com.codex.blacklight.network.RagdollImpactPayload.BladeSpan;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FiniteMeshCutTest {
    static final FiniteMeshCut.Vertices<Vec3> VERTICES=new FiniteMeshCut.Vertices<>() {
        public Vec3 position(Vec3 vertex){return vertex;}
        public Vec3 mix(Vec3 a,Vec3 b,double t){return a.lerp(b,t);}
    };
    static SweptBladeSurface plane(double x0,double x1,double z0,double z1){
        return new SweptBladeSurface(List.of(new BladeSpan(new Vec3(x0,.17,z0),new Vec3(x1,.17,z0)),new BladeSpan(new Vec3(x0,.17,z1),new Vec3(x1,.17,z1))));
    }
    @Test void capturedCurvedBladeDistinguishesAShallowStrokeFromACompleteSection()throws Exception{
        com.google.gson.JsonObject fixture;
        try(var in=getClass().getResourceAsStream("/ragdoll/curved-blade-torso.json")){
            assertNotNull(in);fixture=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        }
        var mesh=new ArrayList<List<Vec3>>();var path=new ArrayList<BladeSpan>();
        for(var face:fixture.getAsJsonArray("faces")){var points=new ArrayList<Vec3>();for(var p:face.getAsJsonArray())points.add(jsonVector(p));mesh.add(points);}
        for(var span:fixture.getAsJsonArray("path")){var points=new ArrayList<Vec3>();for(var p:span.getAsJsonArray())points.add(jsonVector(p));path.add(new BladeSpan(points));}
        var surface=new SweptBladeSurface(path);var coarse=new ArrayList<List<Vec3>>();for(var face:mesh)coarse.addAll(grid(face,4));
        assertEquals("surface-still-connected",FiniteMeshCut.trace(coarse,VERTICES,surface).reason());
        var refined=FiniteMeshCut.refine(mesh,VERTICES,surface,.075,6144);assertFalse(refined.isEmpty());
        // Eight uniform subdivisions falsely disconnect this stroke; sixteen and
        // thirty-two retain its narrow uncut bridge. Adaptive coverage must too.
        assertTrue(FiniteMeshCut.trace(refined,VERTICES,surface).pieces().isEmpty());
        assertEquals(area(mesh),area(refined),1e-7);
        path.clear();for(var span:fixture.getAsJsonArray("completePath")){var points=new ArrayList<Vec3>();for(var p:span.getAsJsonArray())points.add(jsonVector(p));path.add(new BladeSpan(points));}
        surface=new SweptBladeSurface(path);refined=FiniteMeshCut.refine(mesh,VERTICES,surface,.075,6144);
        var result=FiniteMeshCut.trace(refined,VERTICES,surface);assertEquals(2,result.pieces().size(),result.reason());
        assertEquals(area(mesh),result.pieces().stream().mapToDouble(p->area(p.faces())).sum(),1e-7);
        for(var piece:result.pieces()){
            assertEquals(1,piece.caps().size());assertFalse(piece.caps().getFirst().triangles().isEmpty());
            for(var t:piece.caps().getFirst().triangles())for(var p:t)assertTrue(surface.covers(p));
            var closed=new ArrayList<>(piece.faces());for(var cap:piece.caps())for(var triangle:cap.triangles())closed.add(Arrays.asList(triangle));
            assertTrue(FiniteMeshCut.closed(closed,VERTICES),"Captured curved cap must stay welded: "+FiniteMeshCut.closureError(closed,VERTICES));
        }
    }
    private static Vec3 jsonVector(com.google.gson.JsonElement element){var a=element.getAsJsonArray();return new Vec3(a.get(0).getAsDouble(),a.get(1).getAsDouble(),a.get(2).getAsDouble());}
    @Test void finerSkinPreservesTissueBehindTheFiniteBladeAndLeavesRemoteSkinCoarse(){
        var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),1);var shallow=plane(-1.1,1.1,-1.1,0);
        var refined=FiniteMeshCut.refine(mesh,VERTICES,shallow,.075,6144);
        assertFalse(refined.isEmpty());assertEquals(area(mesh),area(refined),1e-8);
        assertTrue(FiniteMeshCut.cut(refined,VERTICES,shallow).isEmpty());
        var distant=box(new Vec3(5,5,5),new Vec3(6,6,6),1);
        assertEquals(distant,FiniteMeshCut.refine(distant,VERTICES,shallow,.075,6144));
    }
    @Test void refinementBudgetRejectsTheWholeAttemptWithoutMutatingItsSkin(){
        var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),1);var copy=List.copyOf(mesh);
        assertTrue(FiniteMeshCut.refine(mesh,VERTICES,plane(-1.1,1.1,-1.1,1.1),.075,6).isEmpty());
        assertEquals(copy,mesh);assertEquals(6,mesh.size());
    }
    @Test void completeFiniteSectionMakesTwoClosedHalvesWithoutLosingSkin(){
        var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),4);
        var cut=FiniteMeshCut.cut(mesh,VERTICES,plane(-1.1,1.1,-1.1,1.1));
        assertEquals(2,cut.size());
        for(var piece:cut){assertEquals(1,piece.caps().size());assertTrue(piece.caps().getFirst().points().size()>=4);}
        assertEquals(area(mesh),cut.stream().mapToDouble(p->area(p.faces())).sum(),1e-8);
        assertEquals(-1,cut.get(0).caps().getFirst().side()*cut.get(1).caps().getFirst().side());
    }
    @Test void detachedSkinCanBeCutAgainAtADifferentPositionAndAngle(){
        var original=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),4);
        var first=FiniteMeshCut.cut(original,VERTICES,plane(-1.1,1.1,-1.1,1.1));assertEquals(2,first.size());
        var fragment=new ArrayList<>(first.getFirst().faces());
        for(var cap:first.getFirst().caps())for(var triangle:cap.triangles())fragment.add(Arrays.asList(triangle));
        // A new oblique blade passes through the fragment, including the first cap.
        var next=new SweptBladeSurface(List.of(
                new BladeSpan(new Vec3(-.67,-1.2,-1.2),new Vec3(.77,1.2,-1.2)),
                new BladeSpan(new Vec3(-.67,-1.2,1.2),new Vec3(.77,1.2,1.2))));
        var split=FiniteMeshCut.trace(fragment,VERTICES,next);
        assertEquals(2,split.pieces().size(),split.reason());
        assertEquals(area(fragment),split.pieces().stream().mapToDouble(p->area(p.faces())).sum(),1e-7);
        for(var piece:split.pieces()){assertFalse(piece.caps().isEmpty());for(var cap:piece.caps())for(var triangle:cap.triangles())for(var point:triangle)assertTrue(next.covers(point));}
    }
    @Test void openCutLoopCannotProduceAnUncappedSolidFragment(){
        var mesh=new ArrayList<>(box(new Vec3(-1,-1,-1),new Vec3(1,1,1),1));mesh.remove(0);
        var result=FiniteMeshCut.trace(mesh,VERTICES,plane(-1.1,1.1,-1.1,1.1));
        assertTrue(result.pieces().isEmpty());assertFalse(result.incisions().isEmpty());
    }
    @Test void shallowStrokeDoesNotDetachTissueConnectedBehindTheBlade(){
        var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),6);
        var surface=plane(-1.1,1.1,-1.1,0);var result=FiniteMeshCut.trace(mesh,VERTICES,surface);
        assertTrue(result.pieces().isEmpty());assertFalse(result.incisions().isEmpty());
        for(var line:result.incisions()){assertTrue(surface.covers(line.a(),line.b()));assertTrue(line.a().z<=.045&&line.b().z<=.045);}
    }
    @Test void missedSkinHasNoIncisionOrSever(){
        var result=FiniteMeshCut.trace(box(new Vec3(-1,-1,-1),new Vec3(1,1,1),3),VERTICES,plane(2,3,-1.1,1.1));
        assertTrue(result.pieces().isEmpty());assertTrue(result.incisions().isEmpty());
    }
    @Test void cuttingOnlyThePerimeterCannotSeverAnIntactInterior(){
        var path=new ArrayList<BladeSpan>();
        for(var p:List.of(new Vec3(-1,0,-1),new Vec3(1,0,-1),new Vec3(1,0,1),new Vec3(-1,0,1),new Vec3(-1,0,-1)))
            path.add(new BladeSpan(p.scale(.90).add(0,.17,0),p.scale(1.10).add(0,.17,0)));
        var surface=new SweptBladeSurface(path);var result=FiniteMeshCut.trace(box(new Vec3(-1,-1,-1),new Vec3(1,1,1),6),VERTICES,surface);
        assertFalse(result.incisions().isEmpty());assertTrue(result.pieces().isEmpty());
    }
    @Test void curvedSeverCapsStayOnTheActualRibbon(){
        var path=new ArrayList<BladeSpan>();
        for(int i=0;i<=32;i++){double z=-1.1+2.2*i/32,y=.2*Math.sin(Math.PI*z);path.add(new BladeSpan(new Vec3(-1.1,y,z),new Vec3(1.1,y,z)));}
        var surface=new SweptBladeSurface(path);var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),8);
        var trace=FiniteMeshCut.trace(mesh,VERTICES,surface);var result=trace.pieces();
        if(result.isEmpty()){
            // A near-vertex S-curve can exceed the conforming surface-cap budget.
            // The production fallback must still cut it into two closed solids;
            // accepting an open fast cap to satisfy a piece count is incorrect.
            assertEquals("cap-mesh-budget",trace.reason());
            List<List<List<Vec3>>> solids=List.of(mesh);
            for(int i=1;i<path.size();i++){
                var ribbon=new SweptBladeSurface(path.subList(i-1,i+1));var next=new ArrayList<List<List<Vec3>>>();
                for(var solid:solids){var cut=SolidBladeCut.subtract(solid,SolidBladeCutTest.V,ribbon.triangles(),6144);
                    assertFalse(cut.reason().startsWith("budget")||cut.reason().startsWith("open"),cut.reason());
                    if(cut.changed())next.addAll(cut.pieces());else next.add(solid);}
                solids=next;
            }
            assertEquals(2,solids.size());for(var solid:solids)assertTrue(FiniteMeshCut.closed(solid,VERTICES));
            double volume=solids.stream().mapToDouble(s->SolidBladeCut.volume(s,VERTICES)).sum();assertTrue(volume>7.95&&volume<8);
            return;
        }
        assertEquals(2,result.size(),trace.reason());
        assertEquals(area(mesh),result.stream().mapToDouble(p->area(p.faces())).sum(),1e-7);
        for(var piece:result){assertEquals(1,piece.caps().size());for(var triangle:piece.caps().getFirst().triangles())for(var p:triangle){assertTrue(surface.covers(p));
                // This deliberately coarse skin has straight .25-long boundary
                // segments. Keep their chord attached; only the cap interior
                // follows the continuous curve (the old projection left gaps).
                boolean boundary=Math.abs(Math.abs(p.x)-1)<1e-7||Math.abs(Math.abs(p.z)-1)<1e-7;
                assertEquals(.2*Math.sin(Math.PI*p.z),p.y,boundary?.016:.003);}
            var closed=new ArrayList<>(piece.faces());for(var cap:piece.caps())for(var triangle:cap.triangles())closed.add(Arrays.asList(triangle));assertTrue(FiniteMeshCut.closed(closed,VERTICES),"Curved cap gap="+FiniteMeshCut.closureError(closed,VERTICES));}
    }
    @Test void seamOnAnExistingModelEdgeIsNotWeldedBackTogether(){
        var mesh=box(new Vec3(-1,-.83,-1),new Vec3(1,1.17,1),4);
        var result=FiniteMeshCut.cut(mesh,VERTICES,plane(-1.1,1.1,-1.1,1.1));
        assertEquals(2,result.size());for(var piece:result)assertEquals(1,piece.caps().size());
    }
    @Test void untouchedDisconnectedDecorationStaysWholeOnTheNearestHalf(){
        var mesh=new ArrayList<>(box(new Vec3(-1,-1,-1),new Vec3(1,1,1),4));
        // Same bone contains a decorative cube. Its extrapolated section is outside
        // the ribbon and must neither veto the valid cut nor become another gib.
        var decor=box(new Vec3(1.2,-.1,-.2),new Vec3(1.5,.4,.2),2);mesh.addAll(decor);
        var cut=FiniteMeshCut.cut(mesh,VERTICES,plane(-1.05,1.05,-1.05,1.05));
        assertEquals(2,cut.size());
        assertEquals(area(mesh),cut.stream().mapToDouble(p->area(p.faces())).sum(),1e-8);
        assertEquals(1,cut.stream().filter(p->p.faces().stream().flatMap(List::stream).anyMatch(v->v.x>1.1)).count());
    }
    @Test void neighbouringFacesWithDifferentSubdivisionsRemainConnected(){
        var mesh=box(new Vec3(-1,-1,-1),new Vec3(1,1,1),1);
        var detailed=new ArrayList<List<Vec3>>();
        for(int i=0;i<mesh.size();i++)detailed.addAll(grid(mesh.get(i),i%2==0?3:5));
        assertTrue(FiniteMeshCut.cut(detailed,VERTICES,plane(-1.1,1.1,-1.1,0)).isEmpty());
        assertEquals(2,FiniteMeshCut.cut(detailed,VERTICES,plane(-1.1,1.1,-1.1,1.1)).size());
    }
    static List<List<Vec3>> box(Vec3 min,Vec3 max,int steps){
        var faces=new ArrayList<List<Vec3>>();
        var p=List.of(new Vec3(min.x,min.y,min.z),new Vec3(max.x,min.y,min.z),new Vec3(max.x,max.y,min.z),new Vec3(min.x,max.y,min.z),new Vec3(min.x,min.y,max.z),new Vec3(max.x,min.y,max.z),new Vec3(max.x,max.y,max.z),new Vec3(min.x,max.y,max.z));
        for(var face:new int[][]{{0,3,2,1},{4,5,6,7},{0,1,5,4},{3,7,6,2},{0,4,7,3},{1,2,6,5}})faces.addAll(grid(Arrays.stream(face).mapToObj(p::get).toList(),steps));
        return faces;
    }
    static List<List<Vec3>> grid(List<Vec3> face,int steps){
        var out=new ArrayList<List<Vec3>>();var a=face.get(0);var b=face.get(1);var c=face.get(2);var d=face.get(3);
        for(int x=0;x<steps;x++)for(int y=0;y<steps;y++){
            var l0=a.lerp(d,y/(double)steps);var r0=b.lerp(c,y/(double)steps);var l1=a.lerp(d,(y+1)/(double)steps);var r1=b.lerp(c,(y+1)/(double)steps);
            out.add(List.of(l0.lerp(r0,x/(double)steps),l0.lerp(r0,(x+1)/(double)steps),l1.lerp(r1,(x+1)/(double)steps),l1.lerp(r1,x/(double)steps)));
        }return out;
    }
    static double area(List<List<Vec3>> mesh){double sum=0;for(var face:mesh)for(int i=1;i+1<face.size();i++)sum+=face.get(i).subtract(face.getFirst()).cross(face.get(i+1).subtract(face.getFirst())).length()*.5;return sum;}
}
