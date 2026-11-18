package com.codex.blacklight.combat;
import com.codex.blacklight.city.HydraMotion;
import net.minecraft.world.phys.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class HydraContactTest {
    @Test void gunRaySelectsNearestSkinRegardlessOfRegionOrderAndStopsAtCoverEndpoint(){
        var near=HydraContact.oriented(new Vec3(2,0,0),new Vec3(.5,.5,.5),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        var far=HydraContact.oriented(new Vec3(5,0,0),new Vec3(.5,.5,.5),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        var regions=java.util.List.of(new HydraContact.Region("far",java.util.List.of(far),far.bounds),
                new HydraContact.Region("near",java.util.List.of(near),near.bounds));
        var style=com.codex.blacklight.infection.InfectedAttackStyle.PIERCE;
        var hit=HydraHurtContact.ray(regions,Vec3.ZERO,new Vec3(10,0,0),style);
        assertNotNull(hit);assertEquals("near",hit.bone());assertEquals(1.5,hit.point().x,1e-6);assertEquals(new Vec3(-1,0,0),hit.normal());
        assertNull(HydraHurtContact.ray(regions,Vec3.ZERO,new Vec3(1,0,0),style),"a wall-truncated ray cannot reach the body");
    }
    @Test void gunRayDoesNotDamageTheEmptyCornerOfAnAnimatedProxy(){
        double k=Math.sqrt(.5);var box=HydraContact.oriented(Vec3.ZERO,new Vec3(2,.2,.12),new Vec3(k,0,k),new Vec3(0,1,0),new Vec3(-k,0,k));
        var from=new Vec3(1,-1,-1);var to=new Vec3(1,1,-1);
        assertTrue(box.bounds.clip(from,to).isPresent());
        assertNull(HydraHurtContact.ray(java.util.List.of(new HydraContact.Region("limb",java.util.List.of(box),box.bounds)),
                from,to,com.codex.blacklight.infection.InfectedAttackStyle.PIERCE));
    }
    @Test void physicalTerrainUnionRemovesSeamsWithoutBridgingHoles(){
        var floor=FlyerPhysics.coalesce(java.util.List.of(new AABB(0,0,0,1,1,1),new AABB(1,0,0,2,1,1),new AABB(0,0,1,1,1,2),new AABB(1,0,1,2,1,2)));
        assertEquals(java.util.List.of(new AABB(0,0,0,2,1,2)),floor);
        var gap=FlyerPhysics.coalesce(java.util.List.of(new AABB(0,0,0,1,1,1),new AABB(2,0,0,3,1,1),new AABB(0,1,0,.5,1.5,1)));
        assertEquals(3,gap.size());assertTrue(gap.stream().noneMatch(b->b.contains(new Vec3(1.5,.5,.5))));
    }
    @Test void terrainSeparationUsesRotatedSkinRatherThanBroadBounds(){
        double k=Math.sqrt(.5);var box=HydraContact.oriented(new Vec3(0,.1,0),new Vec3(2,.2,.12),new Vec3(k,0,k),new Vec3(0,1,0),new Vec3(-k,0,k));
        var floor=new AABB(-3,-1,-3,3,0,3);var shift=box.separation(floor);assertNotNull(shift);assertEquals(.10001,shift.y,1e-6);
        var resolved=HydraContact.oriented(box.center.add(shift),new Vec3(2,.2,.12),new Vec3(k,0,k),new Vec3(0,1,0),new Vec3(-k,0,k));assertNull(resolved.separation(floor));
        var corner=new AABB(.9,.05,-1.1,1.1,.15,-.9);assertTrue(box.bounds.intersects(corner));assertNull(box.separation(corner));
    }
    @Test void orientedReceiverRejectsEmptyRayAndBladeCorners(){
        double k=Math.sqrt(.5);var box=HydraContact.oriented(Vec3.ZERO,new Vec3(2,.2,.12),new Vec3(k,0,k),new Vec3(0,1,0),new Vec3(-k,0,k));
        var a=new Vec3(1,-1,-1);var b=new Vec3(1,1,-1);
        assertTrue(box.bounds.clip(a,b).isPresent());assertTrue(box.clip(a,b).isEmpty());
        assertTrue(box.triangle(new Vec3(.9,0,-1.1),new Vec3(1.1,0,-1.1),new Vec3(1,0,-.9)).isEmpty());
        var entry=box.clip(new Vec3(1,1,1),new Vec3(1,-1,1)).orElseThrow();assertEquals(.2,entry.y,1e-6);
        assertTrue(box.triangle(new Vec3(-.2,0,-.2),new Vec3(.2,0,-.2),new Vec3(0,0,.2)).isPresent());
    }
    @Test void orientedBluntContactDoesNotFillRotatedReceiverCorners(){
        double k=Math.sqrt(.5);var target=HydraContact.oriented(Vec3.ZERO,new Vec3(2,.2,.12),new Vec3(k,0,k),new Vec3(0,1,0),new Vec3(-k,0,k));
        var miss=HydraContact.oriented(new Vec3(1,0,-1),new Vec3(.05,.05,.05),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        assertTrue(target.bounds.intersects(miss.bounds));assertFalse(target.touches(miss));
        var hit=HydraContact.oriented(new Vec3(1,0,1),new Vec3(.05,.05,.05),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,0,1));
        assertTrue(target.touches(hit));var at=target.contact(hit);assertTrue(target.nearest(at).distanceToSqr(at)<1e-10&&hit.nearest(at).distanceToSqr(at)<1e-10);
    }
    @Test void everyVisibleReceiverRegionTracksScaleYawAndPose(){
        for(float size:new float[]{1.6F,2.4F})for(float yaw:new float[]{0,77,179})for(int move:new int[]{0,3,9,10}){
            var regions=HydraContact.regions(HydraMotion.INSTANCE.sample(move,30),new Vec3(100,80,-60),yaw,size);
            assertEquals(HydraContact.regionBones().size(),regions.size());
            assertTrue(regions.stream().anyMatch(r->r.bone().equals("head"))&&regions.stream().anyMatch(r->r.bone().equals("jaw")));
            for(var r:regions)for(var b:r.boxes()){
                assertTrue(r.bounds().inflate(.00001).contains(b.center));
                assertTrue(b.clip(b.center.add(0,20,0),b.center).isPresent());
                assertEquals(0,b.nearest(b.clip(b.center.add(0,20,0),b.center).orElseThrow()).distanceTo(b.clip(b.center.add(0,20,0),b.center).orElseThrow()),1e-6);
            }
        }
    }
    @Test void idleHornTipsAndRecoveryStayClearOfTheCloserWall(){
        var wall=new AABB(-30,.2,6,30,24,7);int corrected=0;
        for(float age=0;age<35;age+=.25F){
            var raw=HydraMotion.INSTANCE.sample(0,age);var adjusted=HydraContact.restPose(raw,Vec3.ZERO,0,2,java.util.List.of(wall));
            if(raw!=adjusted)corrected++;
            assertTrue(HydraContact.body(adjusted,Vec3.ZERO,0,2).stream().noneMatch(b->b.touches(wall)),"idle age="+age);
        }assertTrue(corrected>0,"covers an actual penetrating idle pose");
    }
    @Test void wallStopsTheVisibleMeshAndRecoilRetracesTheClearPath(){
        for(int move:new int[]{3,9}){
            var wall=new AABB(-30,.2,7,30,24,8);
            java.util.function.DoubleFunction<java.util.List<HydraContact.Box>> pose=t->HydraContact.body(HydraMotion.INSTANCE.sample(move,(float)t),Vec3.ZERO,0,2);
            var hit=HydraContact.obstacle(pose,0,55,java.util.List.of(wall));
            assertNotNull(hit);assertTrue(hit.safeAge()>10&&hit.safeAge()<50,"move="+move+" stop="+hit.safeAge());
            assertTrue(pose.apply(hit.safeAge()).stream().noneMatch(b->b.touches(wall)),"stop before solid mesh");
            assertTrue(pose.apply(hit.safeAge()+.1).stream().anyMatch(b->b.touches(wall)),"actual incoming shape reaches obstacle");
            for(float t=0;t<hit.safeAge()*2+3;t+=.25F){
                float age=HydraContact.recoilPoseAge(t,hit.safeAge());
                assertTrue(pose.apply(age).stream().noneMatch(b->b.touches(wall)),"forward and reverse must stay on clear side");
            }
        }
    }
    @Test void recoilIsContinuousMonotoneAndEndsAtEntryPose(){
        assertEquals(42,HydraContact.recoilPoseAge(42,42));
        assertEquals(42,HydraContact.recoilPoseAge(42,-1));
        float last=42;for(float clock=42;clock<90;clock+=.0625F){float p=HydraContact.recoilPoseAge(clock,42);assertTrue(p>=0&&p<=last);last=p;}
        assertEquals(0,last);
    }
    @Test void emptyTerrainDoesNotChangeSourcePlayback(){
        assertNull(HydraContact.obstacle(t->HydraContact.body(HydraMotion.INSTANCE.sample(3,(float)t),Vec3.ZERO,0,2),40,55,java.util.List.of()));
    }
    @Test void sourceSlicesAndAllSevenSidesweepRecordsAreRetained(){
        var sweep=HydraCombatAction.get(9);assertEquals(57,sweep.duration);assertEquals(2,sweep.windows.length);
        assertEquals("combat_sweep",HydraMotion.clip(9));assertEquals(24,sweep.windows[0].from(),.001);assertEquals(32.66667,sweep.windows[0].to(),.001);
        var side=HydraCombatAction.get(10);assertEquals(7,side.windows.length);assertEquals(83,side.duration);
        assertEquals("Neck_3",side.windows[5].joint());assertEquals("full_rig_body1",side.windows[6].joint());
        assertEquals(148,HydraCombatAction.get(3).duration);
    }
    @Test void geometryFollowsActualClawAndRejectsOwnerCentredRadius(){
        var claw=HydraContact.boxes(9,30,"Claw_L",Vec3.ZERO,0,2).get(1);
        var target=new AABB(claw.center,claw.center).inflate(.08);
        assertTrue(claw.touches(target));
        assertFalse(claw.touches(new AABB(-.4,0,2,.4,1.8,2.8)));
        assertTrue(HydraContact.sweep(9,29,30,Vec3.ZERO,0,0,2).stream().anyMatch(s->s.box().touches(target)));
        assertTrue(HydraContact.sweep(9,1,2,Vec3.ZERO,0,0,2).isEmpty());
        assertTrue(HydraContact.sweep(9,34,35,Vec3.ZERO,0,0,2).isEmpty());
    }
    @Test void retargetedHeadDescendsAndHornsReachTheGroundBand(){
        var high=HydraContact.boxes(3,40,"Head",Vec3.ZERO,0,2).get(0);
        var low=HydraContact.boxes(3,52,"Head",Vec3.ZERO,0,2).get(0);
        assertTrue(high.center.y-low.center.y>6);
        assertTrue(low.bounds.minY<.3,"slam head bottom="+low.bounds.minY);
        var claw=HydraContact.boxes(9,30,"Claw_L",Vec3.ZERO,0,2).get(0);
        assertTrue(claw.bounds.minY<2.1&&claw.center.z>10,"active horn is in front at ground combat height");
    }
    @Test void yawOriginAndScaleTransformTheSameContactSurface(){
        var a=HydraContact.boxes(10,42,"Head",Vec3.ZERO,0,1.6F).get(0);
        Vec3 origin=new Vec3(100,80,-60);float yaw=75;
        var b=HydraContact.boxes(10,42,"Head",origin,yaw,2.4F).get(0);
        var rotated=a.center.scale(1.5).toVector3f().rotateY((float)Math.toRadians(-yaw));
        Vec3 expected=origin.add(rotated.x,rotated.y,rotated.z);
        assertEquals(0,expected.distanceTo(b.center),.00001);
        assertTrue(b.touches(new AABB(expected,expected).inflate(.05)));
    }
    @Test void exactOrientedSurfacesDoNotUseTheirEntireEnclosingBox(){
        var boxes=HydraContact.boxes(10,45,"Claw_L",Vec3.ZERO,25,2);int misses=0;
        for(var b:boxes)for(double x:new double[]{b.bounds.minX,b.bounds.maxX})for(double y:new double[]{b.bounds.minY,b.bounds.maxY})for(double z:new double[]{b.bounds.minZ,b.bounds.maxZ}){
            var small=new AABB(x-.005,y-.005,z-.005,x+.005,y+.005,z+.005);
            if(!b.touches(small))misses++;
        }assertTrue(misses>=4);
    }
    @Test void contactPointStaysInBothIntersectingShapes(){
        for(int move:new int[]{3,9,10})for(var w:HydraCombatAction.get(move).windows){if(w.wave())continue;
            for(var b:HydraContact.boxes(move,(w.from()+w.to())/2,w.joint(),Vec3.ZERO,35,2)){
                var target=new AABB(b.center,b.center).inflate(.2);var p=b.contact(target);
                assertEquals(0,HunterContactPose.nearest(target,p).distanceTo(p),1e-6);
                assertEquals(0,b.nearest(p).distanceTo(p),1e-5);
            }
        }
    }
    @Test void sourceTurningWindowsStopAndBodyWavesAreSeparate(){
        assertTrue(HydraCombatAction.get(9).turningSpeed(0)>0);assertEquals(0,HydraCombatAction.get(9).turningSpeed(25));
        assertEquals(3,java.util.Arrays.stream(HydraCombatAction.get(3).windows).filter(HydraCombatAction.Window::wave).count());
        assertTrue(HydraContact.sweep(3,63,64,Vec3.ZERO,0,0,2).isEmpty());
    }
}
