package com.codex.blacklight.combat;

import java.util.*;
import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GreeneMotionTest {
    private static org.joml.Vector3f vector(JsonArray a){return new org.joml.Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat());}
    @Test void liveBattlePhasesKeepTheIndependentNativeSkinFixtures() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/greene-skin-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var fixtures=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("fixtures");int checked=0;
            for(var value:fixtures){
                var f=value.getAsJsonObject();String clip=f.get("clip").getAsString();float frame=f.get("frame").getAsFloat();
                int stage;float age;
                if(clip.equals("exposed")&&frame==32){stage=GreeneBattleCycle.COLLAPSING;age=(32-15)/.36F;}
                else if(clip.equals("exposed")&&(frame==72||frame==90)){stage=GreeneBattleCycle.DOWN;age=(frame-60)/1.2F+150;}
                else if(clip.equals("idle")&&frame==0){stage=GreeneBattleCycle.UNDERGROUND;age=20;}
                else continue;
                var pose=GreeneMotion.battle(stage,age,0,-1);String bone=f.get("bone").getAsString();
                var expected=new org.joml.Matrix4f().rotateY((float)Math.PI).transformPosition(vector(f.getAsJsonArray("centerModelBlocks")).mul(GreenePresentation.SCALE));
                var region=GreeneContact.regions(pose,Vec3.ZERO,0,0).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();
                assertTrue(region.boxes().get(f.get("cube").getAsInt()).center.distanceTo(new Vec3(expected.x,expected.y+.01,expected.z))<.0002,"live phase uses original joint fixture "+stage+"/"+frame+"/"+bone);checked++;
            }
            assertEquals(16,checked);
        }
    }
    @Test void actualSkinAndMouthFollowIndependentOriginalJointFixtures() throws Exception {
        // Expected positions are evaluated directly from original P1 local
        // matrices and the preserved authored bind, not exported pose rows.
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/greene-skin-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var source=JsonParser.parseReader(reader).getAsJsonObject();var table=GreeneMotion.table();var origin=new Vec3(13,71,-17);int count=0;
            for(var value:source.getAsJsonArray("fixtures")){
                var f=value.getAsJsonObject();String clip=f.get("clip").getAsString(),bone=f.get("bone").getAsString();float frame=f.get("frame").getAsFloat();
                var pose=GreeneMotion.sample(clip,frame*20/table.clip(clip).fps(),false);
                for(float yaw:new float[]{0,93,-131}){
                    var point=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(vector(f.getAsJsonArray("centerModelBlocks")).mul(GreenePresentation.SCALE));
                    var expected=origin.add(point.x,point.y+.01,point.z);
                    var region=GreeneContact.regions(pose,origin,yaw,0).stream().filter(r->r.bone().equals(bone)).findFirst().orElseThrow();
                    assertTrue(region.boxes().get(f.get("cube").getAsInt()).center.distanceTo(expected)<.0002,"native skin center "+bone+" frame="+frame);
                }count++;
            }
            assertEquals(60,count);
            for(var value:source.getAsJsonArray("sockets")){
                var f=value.getAsJsonObject();String clip=f.get("clip").getAsString();float frame=f.get("frame").getAsFloat();
                var pose=GreeneMotion.sample(clip,frame*20/table.clip(clip).fps(),false);
                for(float yaw:new float[]{0,93,-131}){
                    var point=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(vector(f.getAsJsonArray("pointModelBlocks")).mul(GreenePresentation.SCALE));
                    assertTrue(GreeneMouth.position(pose,origin,yaw,f.get("slot").getAsInt()).distanceTo(origin.add(point.x,point.y+.01,point.z))<.0002,"source muzzle shares the visible native Mouth transform");
                }
            }
            assertEquals(8,source.getAsJsonArray("sockets").size());
        }
    }
    @Test void hostParentScaleCompensationIsRecomputedAfterFractionalSamplingAndCycleBlends(){
        var names=Arrays.asList(GreeneMotion.table().bones);int parent=names.indexOf("native_Body_Root"),inverse=names.indexOf("native_Body_parent_scale");
        assertTrue(parent>=0&&inverse>=0);float maximum=1;
        for(String clip:GreeneMotion.table().names())for(float frame=0;frame<GreeneMotion.table().clip(clip).frames().length;frame+=.5F){
            var pose=GreeneMotion.sample(clip,frame*20/GreeneMotion.table().clip(clip).fps(),false);
            maximum=Math.max(maximum,pose[parent][6]);
            for(int axis=6;axis<9;axis++)assertEquals(1,pose[parent][axis]*pose[inverse][axis],.00002,"native basis compensation must not be interpolated independently");
        }
        assertTrue(maximum>1.6,"retain original embedded-host expansion");
        for(float age:new float[]{1.5F,124.5F,198.5F,526.5F,573.5F}){
            var pose=GreeneMotion.cycle(age,400,6);
            for(int axis=6;axis<9;axis++)assertEquals(1,pose[parent][axis]*pose[inverse][axis],.00002);
        }
    }
    @Test void threeHealthChildrenFollowTheirTenOriginalSlugJoints() throws Exception {
        try(var in=Objects.requireNonNull(getClass().getResourceAsStream("/greene-skin-reference.json"));var reader=new InputStreamReader(in,StandardCharsets.UTF_8)){
            var fixtures=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("segments");
            var table=GreeneMotion.table();var names=Arrays.asList(table.bones);int checked=0;
            assertFalse(names.contains("weak_0"),"the former independent ring bone must not drive the attached source segments");
            for(var value:fixtures){
                var f=value.getAsJsonObject();String clip=f.get("clip").getAsString(),leaf=f.get("bone").getAsString(),region=f.get("region").getAsString();
                float frame=f.get("frame").getAsFloat();int cube=f.get("regionCube").getAsInt();
                assertTrue(names.contains(leaf));assertTrue(leaf.startsWith(region+"_Slug_"));
                var pose=GreeneMotion.sample(clip,frame*20/table.clip(clip).fps(),false);
                for(float yaw:new float[]{0,93,-131}){
                    var origin=new Vec3(13,71,-17);var point=new org.joml.Matrix4f().rotateY((float)Math.toRadians(180-yaw)).transformPosition(vector(f.getAsJsonArray("centerModelBlocks")).mul(GreenePresentation.SCALE));
                    var target=GreeneContact.regions(pose,origin,yaw,0).stream().filter(r->r.bone().equals(region)).findFirst().orElseThrow();
                    assertEquals(10,target.boxes().size(),"one child retains all ten articulated source regions");
                    assertTrue(target.boxes().get(cube).center.distanceTo(origin.add(point.x,point.y+.01,point.z))<.0002,"source Slug joint receiver "+leaf+" frame="+frame);
                }checked++;
            }
            assertEquals(450,checked,"fifteen native frames sample all thirty segment leaves");
            var pose=GreeneMotion.sample("idle",0,false);
            for(int child=0;child<3;child++){
                var regions=GreeneContact.regions(pose,Vec3.ZERO,0,1<<child);
                for(int i=0;i<3;i++)assertEquals(i==child?0:10,regions.get(i).boxes().size(),"breaking one health child hides every associated Slug leaf only");
            }
        }
    }
    @Test void radialWaveUsesDecodedHitOriginAndSphereContactInsteadOfRootDistance(){
        var action=SourceBossAction.get(com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE,EncounterMove.HOWL);
        double scale=GreeneMotion.table().sourceUnitBlocks(),first=8*scale,y=2*scale;
        assertEquals(8,action.radius(67),"raw FIG radius remains auditable");assertEquals(40,action.range);
        assertEquals(scale,action.spatialScale(),.0000001);assertEquals(40*scale,action.worldRange(),.0000001);
        assertEquals(first,action.worldRadius(67),.0000001);
        var edge=new net.minecraft.world.phys.AABB(first-.05,y-.1,-.5,first+2,y+.1,.5);
        assertNull(SourceRadialWave.contact(action,66,Vec3.ZERO,0,edge),"windup cannot hurt");
        assertNull(SourceRadialWave.bounds(action,66,Vec3.ZERO,0),"windup has no receiving broadphase");
        var hit=SourceRadialWave.contact(action,67,Vec3.ZERO,0,edge);
        assertNotNull(hit);assertEquals(new Vec3(0,y,0),hit.origin());assertEquals(new Vec3(first-.05,y,0),hit.point());assertEquals(1,hit.damageScale());
        assertNotNull(SourceRadialWave.contact(action,67,Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-.5,y+first-.01,-.5,.5,y+first+.5,.5)),"raised target touches the converted sphere above root-based range");
        assertNull(SourceRadialWave.contact(action,67,Vec3.ZERO,0,new net.minecraft.world.phys.AABB(first+.01,y-.1,-.5,first+1,y+.1,.5)),"just beyond the world-space radius cannot be hit");
        assertNull(SourceRadialWave.contact(action,67,Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-.5,8,-.5,.5,9,.5)),"eight Minecraft blocks high is not eight P1 units");
        var bounds=SourceRadialWave.bounds(action,67,Vec3.ZERO,0);
        assertNotNull(bounds);assertEquals(y-first,bounds.minY,.000001);assertEquals(y+first,bounds.maxY,.000001);assertEquals(first,bounds.maxX,.000001);
        var tangent=new net.minecraft.world.phys.AABB(first,y-.1,-.2,first+1,y+.1,.2);
        assertNotNull(SourceRadialWave.contact(action,67,Vec3.ZERO,0,tangent),"the closed sphere accepts an exact tangent");
        assertTrue(bounds.intersects(tangent),"strict Minecraft broadphase must retain that exact tangent for the final sphere test");
        var second=SourceRadialWave.contact(action,80,Vec3.ZERO,0,new net.minecraft.world.phys.AABB(9*scale,y-.1,-.5,10*scale,y+.1,.5));
        assertNotNull(second);assertEquals(.8F,second.damageScale(),.0001F);
        assertEquals(.8F,action.waveDamageScale(80,9*scale),.0001F,"world-distance utility uses the same converted ring");
        assertEquals(0,action.waveDamageScale(67,first+.01),"raw radius cannot leak through an auxiliary spatial API");
        var alreadyWorld=SourceBossAction.get(com.codex.blacklight.entity.EncounterKind.GOLIATH,EncounterMove.HAMMER);
        assertEquals(1,alreadyWorld.spatialScale(),"already converted action resources must not be scaled a second time");
        assertEquals(alreadyWorld.range,alreadyWorld.worldRange(),.0000001);
    }
    @Test void legacyExposureKeepsItsSavedCollapseDownRiseMapping(){
        assertEquals(575,GreeneMotion.duration(400));assertEquals(275,GreeneMotion.duration(100));
        assertEquals(GreeneMotion.COLLAPSING,GreeneMotion.stage(124,400));assertEquals(GreeneMotion.DOWN,GreeneMotion.stage(125,400));
        assertEquals(GreeneMotion.DOWN,GreeneMotion.stage(524,400));assertEquals(GreeneMotion.RISING,GreeneMotion.stage(525,400));assertEquals(GreeneMotion.GUARDED,GreeneMotion.stage(575,400));
        assertEquals(0,GreeneMotion.regrowth(559,400));assertTrue(GreeneMotion.regrowth(570,400)>.5);assertEquals(1,GreeneMotion.regrowth(575,400));
    }
    @Test void downPoseActuallyLoopsWithoutAdvancingIntoRise(){
        for(float age:new float[]{125,139,174,198})assertPoseNear(GreeneMotion.cycle(age,400,100),GreeneMotion.cycle(age+75,400,100),.0001);
        assertTrue(GreeneMotion.table().point(GreeneMotion.cycle(375,400,100),"head").y/GreenePresentation.SCALE<4,"still down after the former one-shot recovery ended");
    }
    @Test void loopAndStateBoundariesHaveNoPositionalJump(){
        for(float age:new float[]{125,200,275,350,425,500,525,575})assertPoseNear(GreeneMotion.cycle(age-.001F,400,100),GreeneMotion.cycle(age+.001F,400,100),.005);
    }
    @Test void receivedHitReturnsToTheOngoingDownLoop(){
        var base=GreeneMotion.cycle(220,400,100);assertPoseNear(base,GreeneMotion.cycle(220,400,0),.0001);assertPoseNear(base,GreeneMotion.cycle(220,400,20),.0001);
        var hit=GreeneMotion.cycle(220,400,6);double displacement=GreeneMotion.table().point(base,"head").distance(GreeneMotion.table().point(hit,"head"));
        assertTrue(displacement>.001&&displacement/GreenePresentation.SCALE<2,"local hit overlays ongoing collapsed pose: "+displacement);
    }
    @Test void allAuthoredBodyCubesHaveFiniteRegionsThroughoutTheCycle(){
        int count=GreeneContact.surfaces(GreeneMotion.table().sample("idle",0,false),Vec3.ZERO,0,0).size();assertEquals(138,count);
        for(float age:new float[]{0,30,124,125,199,400,525,545,574})for(float yaw:new float[]{0,47,180}){
            var regions=GreeneContact.regions(GreeneMotion.cycle(age,400,6),new Vec3(130,81,-400),yaw,7);
            assertEquals(GreeneContact.regionBones().size(),regions.size());assertTrue(regions.subList(0,3).stream().allMatch(r->r.boxes().isEmpty()));
            for(var r:regions.subList(3,regions.size()))for(var box:r.boxes()){
                assertTrue(Double.isFinite(box.bounds.minX+box.bounds.maxY+box.bounds.maxZ));
                for(var point:box.points){assertTrue(box.contains(point),"scaled rotated skin stays inside its receiver: "+r.bone());assertTrue(box.nearest(point).distanceTo(point)<.00001);}
            }
        }
        for(int stage=GreeneBattleCycle.COLLAPSING;stage<=GreeneBattleCycle.EMERGING;stage++)for(float age:new float[]{0,1.5F,3.5F,26.5F,58.5F}){
            var pose=GreeneMotion.battle(stage,age,32,-1);
            var regions=GreeneContact.regions(pose,new Vec3(13,81,-40),47,7);
            assertTrue(regions.subList(0,3).stream().allMatch(r->r.boxes().isEmpty()),"broken appendages do not visually regrow during rise or sinking");
            for(var r:regions.subList(3,regions.size()))for(var box:r.boxes())assertTrue(Double.isFinite(box.bounds.minX+box.bounds.maxY+box.bounds.maxZ));
        }
    }
    @Test void skewedCubeUsesItsActualFaceForRayAndNearestContact(){
        var box=HydraContact.affine(Vec3.ZERO,new Vec3(1,Math.sqrt(2),1),new Vec3(1,0,0),new Vec3(1,1,0).normalize(),new Vec3(0,0,1));
        assertTrue(box.contains(new Vec3(1.9,.9,0)));assertFalse(box.contains(new Vec3(1.5,0,0)));
        assertTrue(box.nearest(new Vec3(1.5,0,0)).distanceTo(new Vec3(1.25,.25,0))<1e-8);
        assertTrue(box.clip(new Vec3(3,0,0),Vec3.ZERO).orElseThrow().distanceTo(new Vec3(1,0,0))<1e-8);
        assertFalse(box.touches(new net.minecraft.world.phys.AABB(1.49,-.01,-.01,1.51,.01,.01)));
        var touching=HydraContact.oriented(new Vec3(1.9,.9,0),new Vec3(.02,.02,.02),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        assertTrue(box.touches(touching)&&touching.touches(box));
    }
    @Test void breakingOneAppendageRemovesOnlyItsOwnReceivingSurface(){
        var pose=GreeneMotion.table().sample("idle",0,false);var intact=GreeneContact.regions(pose,Vec3.ZERO,0,0);var broken=GreeneContact.regions(pose,Vec3.ZERO,0,2);
        assertFalse(intact.get(1).boxes().isEmpty());assertTrue(broken.get(1).boxes().isEmpty());
        assertEquals(intact.get(0).boxes().size(),broken.get(0).boxes().size());assertEquals(intact.get(2).boxes().size(),broken.get(2).boxes().size());
        for(var region:intact)for(var box:region.boxes())assertTrue(box.contains(box.center));
    }
    private static void assertPoseNear(float[][] a,float[][] b,double tolerance){
        var table=GreeneMotion.table();var ta=table.transforms(a);var tb=table.transforms(b);
        for(int i=0;i<a.length;i++){
            var p=new org.joml.Vector3f(table.pivots[i][0]/16,table.pivots[i][1]/16,table.pivots[i][2]/16);
            assertTrue(ta[i].transformPosition(new org.joml.Vector3f(p)).distance(tb[i].transformPosition(p))/GreenePresentation.SCALE<tolerance,"joint="+table.bones[i]);
            assertEquals(1,Math.abs(new org.joml.Quaternionf().rotationZYX(a[i][2],a[i][1],a[i][0]).dot(new org.joml.Quaternionf().rotationZYX(b[i][2],b[i][1],b[i][0]))),tolerance);
        }
    }
}
