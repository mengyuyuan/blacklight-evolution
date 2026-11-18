package com.codex.blacklight.combat;
import com.codex.blacklight.entity.EncounterKind;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class EncounterMoveTest {
    @Test void goliathFrontSlamUsesSourceSlicesAndOneStrongestSphere(){
        var action=SourceBossAction.get(EncounterKind.GOLIATH,EncounterMove.HAMMER);
        assertNotNull(action);assertEquals("goliath_slam",action.mode);assertEquals(38,action.contact());assertEquals(78,action.duration());
        assertFalse(action.impact(37));assertTrue(action.impact(38));assertFalse(action.impact(39));
        assertEquals(48/30F*20,action.sourceTick(38),.001F);
        var inner=SourceRadialWave.contact(action,38,net.minecraft.world.phys.Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-1,0,.5,0,2,1.5));
        var outer=SourceRadialWave.contact(action,38,net.minecraft.world.phys.Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-1,0,4.5,0,2,5.5));
        assertNotNull(inner);assertNotNull(outer);assertEquals(1,inner.damageScale());assertEquals(400/750F,outer.damageScale(),.0001F);
        assertTrue(inner.origin().y>1.5&&inner.origin().y<1.52,"decoded Motion_Root transform and model scale keep the hit above ground");
        assertNull(SourceRadialWave.contact(action,38,net.minecraft.world.phys.Vec3.ZERO,0,new net.minecraft.world.phys.AABB(-1,0,6.2,0,2,7)));
    }
    @Test void goliathReboundWristsMatchDecodedSourceContactPositions(){
        var pose=GoliathMotion.attack(EncounterMove.HAMMER,38);var table=GoliathMotion.table();
        var left=table.point(pose,"left_hand");var right=table.point(pose,"right_hand");
        assertEquals(-.8156144,left.x,.002);assertEquals(3.0527516,left.y,.002);assertEquals(2.9828632,left.z,.002);
        assertEquals(-1.4267103,right.x,.002);assertEquals(.3602344,right.y,.002);assertEquals(-8.870415,right.z,.002);
        var regions=GoliathContact.regions(pose,net.minecraft.world.phys.Vec3.ZERO,0,0);
        assertEquals("weak_0",regions.get(0).bone());assertEquals("weak_1",regions.get(1).bone());
        assertEquals(99,regions.stream().mapToInt(r->r.boxes().size()).sum(),"all authored Goliath skin cubes receive contact");
        var torso=regions.stream().filter(r->r.bone().startsWith("torso")).collect(
                java.util.stream.Collectors.toMap(r->r.bone(),r->r.boxes().size()));
        assertEquals(java.util.Map.of("torso",11,"torso_middle",5,"torso_waist",3,"torso_lower",1),torso);
        assertTrue(regions.stream().anyMatch(r->r.bone().equals("left_hand")&&!r.boxes().isEmpty()));
        var broken=GoliathContact.regions(pose,net.minecraft.world.phys.Vec3.ZERO,0,1);
        assertTrue(broken.get(0).boxes().isEmpty());assertFalse(broken.get(1).boxes().isEmpty());
    }
    @Test void sourceRotationInterpolationPreservesLargeTurns(){
        for(float[] a:new float[][]{{-2.99F,.1F,2.73F,0,2,3},{-1.9F,-1.22F,2.2F,2,1,0},{.4F,1.5F,.2F,0,0,0}}){
            var b=SourceBossMotion.blend(a,a,.5F);
            var expected=new org.joml.Quaternionf().rotationZYX(a[2],a[1],a[0]);
            var actual=new org.joml.Quaternionf().rotationZYX(b[2],b[1],b[0]);
            assertEquals(1,Math.abs(expected.dot(actual)),.00001,"same pose cannot invert while interpolating");
        }
    }
    @Test void sourceBossEventsPreserveOriginalWindowsAndPlaybackRate(){
        var claw=SourceBossAction.get(EncounterKind.LEADER_HUNTER,EncounterMove.CLAW);
        assertEquals(3.75,claw.range,.0001);assertEquals("horizontal",claw.rangeMode);
        assertEquals(7,claw.contact());assertFalse(claw.impact(6));assertTrue(claw.impact(7));assertFalse(claw.impact(14));
        assertEquals(18,claw.turnStep(1,3),.001);assertEquals(0,claw.turnStep(3,3));
        var roar=SourceBossAction.get(EncounterKind.ELIZABETH_GREENE,EncounterMove.HOWL);
        assertEquals(67,roar.contact());assertFalse(roar.impact(66));assertTrue(roar.impact(67));
        assertEquals(8,roar.radius(67));assertEquals(40,roar.radius(132));
        assertEquals(20F/3,claw.sourceTick(20F/3),.001); // original frame 10 at 30 fps
        assertEquals(47,SourceBossAction.get(EncounterKind.ELIZABETH_GREENE,EncounterMove.VOLLEY).contact());
    }
    @Test void sourceBossPosesAndSweptHandsAreFiniteAcrossEveryFrame(){
        for(var kind:new EncounterKind[]{EncounterKind.LEADER_HUNTER,EncounterKind.ELIZABETH_GREENE})for(var move:EncounterMove.values()){
            var action=SourceBossAction.get(kind,move);if(action==null)continue;
            var table=SourceBossMotion.get(kind);
            for(float age=0;age<=action.duration();age+=.25F){
                var pose=kind==EncounterKind.ELIZABETH_GREENE?GreeneMotion.action(action,age):table.actionPose(action,age);
                for(var bone:pose)for(float value:bone)assertTrue(Float.isFinite(value),kind+" "+move+" "+age);
                for(var name:table.bones){var point=table.point(pose,name);assertTrue(point.isFinite());assertTrue(point.length()/(kind==EncounterKind.ELIZABETH_GREENE?GreenePresentation.SCALE:1)<30,kind+" unbounded retarget "+name);}
            }
        }
    }
    @Test void motherSpineKeepsAllOriginalJointPositions() throws Exception {
        // The old three-segment retarget used stretched cuboids with artificial
        // endpoints. The full native chain must retain all five source joints.
        try(var in=java.util.Objects.requireNonNull(getClass().getResourceAsStream("/greene-skin-reference.json"));var reader=new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)){
            var fixtures=com.google.gson.JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("spine");var table=GreeneMotion.table();
            for(var value:fixtures){var f=value.getAsJsonObject();String clip=f.get("clip").getAsString(),bone=f.get("bone").getAsString();float frame=f.get("frame").getAsFloat();
                var pose=GreeneMotion.sample(clip,frame*20/table.clip(clip).fps(),false);var p=f.getAsJsonArray("pointModelBlocks");
                assertTrue(table.point(pose,bone).distance(new org.joml.Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()).mul(GreenePresentation.SCALE))<.0002,bone+" frame="+frame);
            }assertEquals(90,fixtures.size());
        }
    }
    @Test void motherExposedHeadReachesGroundLevelAndReturns(){
        var table=SourceBossMotion.get(EncounterKind.ELIZABETH_GREENE);var state=SourceBossAction.get(EncounterKind.ELIZABETH_GREENE,"EXPOSED");
        float standing=table.point(GreeneMotion.sample("idle",0,false),"head").y;
        float down=table.point(GreeneMotion.action(state,160),"head").y;
        float restored=table.point(GreeneMotion.action(state,state.duration()),"head").y;
        assertTrue(down>0&&down/GreenePresentation.SCALE<2,"exposed head must be reachable from the ground: "+down);
        assertTrue((standing-down)/GreenePresentation.SCALE>3);assertEquals(standing,restored,.3F*GreenePresentation.SCALE);
    }
    @Test void tacticalProfilesRespectReadinessAndSpeciesMoveSet(){
        for(var kind:EncounterKind.values())for(int phase=0;phase<3;phase++)for(double distance:new double[]{2,5,9,18})for(boolean threat:new boolean[]{false,true}){
            var situation=new EncounterTactics.Situation(distance,phase,.5F,threat,3,4,EncounterMove.DOUBLE);
            var next=EncounterTactics.choose(kind,situation,kind::permits);
            assertTrue(next==EncounterMove.NONE||kind.permits(next),kind+" invalid tactic "+next);
            assertEquals(EncounterMove.NONE,EncounterTactics.choose(kind,situation,m->false),kind+" ignores recharge");
        }
    }
    @Test void tacticsRespondToThreatCrowdsBroodAndBossPhase(){
        var calm=new EncounterTactics.Situation(2,0,1,false,1,0,EncounterMove.NONE);
        var threatened=new EncounterTactics.Situation(2,0,.8F,true,1,0,EncounterMove.NONE);
        var crowd=new EncounterTactics.Situation(2,0,1,false,3,0,EncounterMove.NONE);
        assertEquals(EncounterMove.HAMMER,EncounterTactics.choose(EncounterKind.JUGGERNAUT,calm,m->true));
        assertEquals(EncounterMove.GUARD,EncounterTactics.choose(EncounterKind.JUGGERNAUT,threatened,m->true));
        assertEquals(EncounterMove.SLAM,EncounterTactics.choose(EncounterKind.JUGGERNAUT,crowd,m->true));
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(EncounterKind.ALEX_MERCER,calm,m->true));
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(EncounterKind.ALEX_MERCER,new EncounterTactics.Situation(2,1,.5F,false,1,0,EncounterMove.NONE),m->true));
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(EncounterKind.ALEX_MERCER,new EncounterTactics.Situation(2,2,.2F,false,1,0,EncounterMove.NONE),m->true));
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(EncounterKind.LEADER_HUNTER,new EncounterTactics.Situation(6,0,1,false,1,0,EncounterMove.NONE),m->true));
        assertEquals(EncounterMove.NONE,EncounterTactics.choose(EncounterKind.LEADER_HUNTER,new EncounterTactics.Situation(6,0,1,false,1,4,EncounterMove.NONE),m->true));
        assertTrue(EncounterTactics.recharge(EncounterMove.HOWL)>EncounterTactics.recharge(EncounterMove.CLAW));
        assertEquals(EncounterMove.DIVE,EncounterTactics.choose(EncounterKind.FLYER,new EncounterTactics.Situation(10,0,1,false,1,0,EncounterMove.VOLLEY),m->m==EncounterMove.DIVE));
    }
    @Test void anatomicalCombatKeepsTorsoUprightAndReturnsToRest(){
        for(var kind:new EncounterKind[]{EncounterKind.LEADER_HUNTER,EncounterKind.JUGGERNAUT}){
            var moves=new java.util.HashSet<EncounterMove>();
            for(int n=0;n<12;n++)for(double distance:new double[]{2,6,12})moves.add(kind.choose(distance,0,n));
            for(var move:moves){
                float[] previous=null;
                for(float t=0;t<=move.duration;t+=.25F){
                    var pose=EncounterAnatomyPose.sample(kind,move,t);
                    assertTrue(Math.cos(pose.torso().x())*Math.cos(pose.torso().z())>.7,kind+" inverted "+move+" at "+t);
                    var joints=new EncounterAnatomyPose.Joint[]{pose.torso(),pose.head(),pose.rightArm(),pose.leftArm(),pose.rightForearm(),pose.leftForearm(),pose.rightHand(),pose.leftHand()};
                    float[] values=new float[24];int index=0;
                    for(var joint:joints)for(float angle:new float[]{joint.x(),joint.y(),joint.z()}){
                        assertTrue(Float.isFinite(angle));values[index]=angle;
                        if(previous!=null)assertTrue(Math.abs(angle-previous[index])<.4,kind+" discontinuity "+move+" at "+t);
                        if(t==0||t==move.duration)assertEquals(0,angle,.0001,kind+" rest "+move);
                        index++;
                    }
                    previous=values;
                }
            }
        }
    }
    @Test void goliathSourceFrontSlamLowersTheGiantFistOnItsActualWeightedArm(){
        var table=GoliathMotion.table();
        var before=table.point(GoliathMotion.attack(EncounterMove.HAMMER,30),"right_hand");
        var after=table.point(GoliathMotion.attack(EncounterMove.HAMMER,38),"right_hand");
        assertTrue(before.y-after.y>10,"source frame 42 anticipation descends into frame 48 contact");
        assertEquals(.3602344,after.y,.002,"authored giant right fist follows source Wrist_L, confirmed by P2 skin weights");
    }
    @Test void leaderDoubleSwipeUsesBothHandsAtTheirOwnContact()throws Exception{
        var first=EncounterAnatomyPose.sample(EncounterKind.LEADER_HUNTER,EncounterMove.DOUBLE,12);
        var second=EncounterAnatomyPose.sample(EncounterKind.LEADER_HUNTER,EncounterMove.DOUBLE,25);
        var rest=EncounterAnatomyPose.sample(EncounterKind.LEADER_HUNTER,EncounterMove.NONE,0);
        for(var side:new String[]{"right","left"}){
            var active=handPosition(EncounterKind.LEADER_HUNTER,side.equals("right")?first:second,side);
            var idle=handPosition(EncounterKind.LEADER_HUNTER,rest,side);
            assertTrue(active.y>idle.y+4,side+" swipe never left the ground");
            assertTrue(active.z<idle.z-2,side+" swipe never reached forward");
        }
    }
    private org.joml.Vector3f handPosition(EncounterKind kind,EncounterAnatomyPose.Pose pose,String side)throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/blacklight/geo/"+kind.id+"_mc.geo.json")){
            var bones=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");
            var pivots=new java.util.HashMap<String,org.joml.Vector3f>();
            for(var value:bones){var bone=value.getAsJsonObject();var p=bone.getAsJsonArray("pivot");pivots.put(bone.get("name").getAsString(),new org.joml.Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));}
            var point=new org.joml.Vector3f(pivots.get(side+"_hand"));boolean right=side.equals("right");
            String[] chain={side+"_forearm",side+"_arm","torso"};
            var angles=new EncounterAnatomyPose.Joint[]{right?pose.rightForearm():pose.leftForearm(),right?pose.rightArm():pose.leftArm(),pose.torso()};
            for(int i=0;i<chain.length;i++){var pivot=pivots.get(chain[i]);var a=angles[i];point.sub(pivot);new org.joml.Quaternionf().rotationZYX(a.z(),a.y(),a.x()).transform(point);point.add(pivot);}
            return point;
        }
    }
    @Test void sourceRetargetPreservesAuthoredNeutralPose(){var rig=com.codex.blacklight.client.model.SupremeHunterBlockRig.get();var rest=rig.sample("idle",0);for(String[] pair:new String[][]{{"Spine_3",""},{"Head","Spine_3"},{"Shoulder_R","Spine_3"},{"Elbow_R","Shoulder_R"},{"Hip_R",""}}){var q=com.codex.blacklight.client.model.EncounterSourceMotion.localDelta(rig,rest,rest,pair[0],pair[1]);assertEquals(0,q.x,.0001F);assertEquals(0,q.y,.0001F);assertEquals(0,q.z,.0001F);assertEquals(1,Math.abs(q.w),.0001F);}}
    @Test void authoredGeometryHasFiniteCubesAndInBoundsUv()throws Exception{
        var ids=new java.util.ArrayList<String>();for(var kind:EncounterKind.values())ids.add(kind.id);ids.add("recon_uav");
        for(String id:ids)try(var in=getClass().getResourceAsStream("/assets/blacklight/geo/"+id+"_mc.geo.json")){
            var geo=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            int width=geo.getAsJsonObject("description").get("texture_width").getAsInt(),height=geo.getAsJsonObject("description").get("texture_height").getAsInt();
            var names=new java.util.HashSet<String>();for(var value:geo.getAsJsonArray("bones"))assertTrue(names.add(value.getAsJsonObject().get("name").getAsString()),id+" duplicate bone");
            for(var value:geo.getAsJsonArray("bones")){var bone=value.getAsJsonObject();if(bone.has("parent"))assertTrue(names.contains(bone.get("parent").getAsString()),id+" missing parent");if(!bone.has("cubes"))continue;
                for(var item:bone.getAsJsonArray("cubes")){var cube=item.getAsJsonObject();for(var n:cube.getAsJsonArray("origin"))assertTrue(Float.isFinite(n.getAsFloat()),id);for(var n:cube.getAsJsonArray("size"))assertTrue(n.getAsFloat()>0&&Float.isFinite(n.getAsFloat()),id);
                    assertEquals(6,cube.getAsJsonObject("uv").size(),id);for(var face:cube.getAsJsonObject("uv").entrySet()){var uv=face.getValue().getAsJsonObject();var p=uv.getAsJsonArray("uv");var s=uv.getAsJsonArray("uv_size");assertTrue(p.get(0).getAsFloat()>=0&&p.get(1).getAsFloat()>=0,id);assertTrue(p.get(0).getAsFloat()+s.get(0).getAsFloat()<=width&&p.get(1).getAsFloat()+s.get(1).getAsFloat()<=height,id+" UV outside atlas");}
                }
            }
        }
    }
    @Test void goliathWeakpointBonesKeepTheirReboundParents()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/blacklight/geo/goliath_mc.geo.json")){
            var geo=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            int count=0;
            for(var value:geo.getAsJsonArray("bones")){var bone=value.getAsJsonObject();String name=bone.get("name").getAsString();if(!name.startsWith("weak_"))continue;int index=Integer.parseInt(name.substring(5));count++;
                assertEquals(index==0?"right_leg":"left_leg",bone.get("parent").getAsString());var pivot=bone.getAsJsonArray("pivot");
                for(var coordinate:pivot)assertTrue(Float.isFinite(coordinate.getAsFloat()));
                var regions=GoliathContact.regions(GoliathMotion.attack(EncounterMove.HAMMER,38),net.minecraft.world.phys.Vec3.ZERO,0,0);
                assertEquals(name,regions.get(index).bone());assertFalse(regions.get(index).boxes().isEmpty());
                assertFalse(bone.getAsJsonArray("cubes").isEmpty());
            }
            assertEquals(2,count);
        }
    }
    @Test void brawlerReusesExistingModelTextureAndAnimationVerbatim()throws Exception{
        for(String[] pair:new String[][]{{"geo/brawler_mc.geo.json","geo/blacklight_hunter_mc.geo.json"},{"textures/entity/brawler_mc.png","textures/entity/hunter_mc.png"},{"animations/brawler.animation.json","animations/blacklight_hunter.animation.json"}}){
            try(var alias=getClass().getResourceAsStream("/assets/blacklight/"+pair[0]);var original=getClass().getResourceAsStream("/assets/blacklight/"+pair[1])){
                assertNotNull(alias);assertNotNull(original);assertArrayEquals(original.readAllBytes(),alias.readAllBytes(),pair[0]);
            }
        }
        assertFalse(com.codex.blacklight.client.model.EncounterSourceMotion.supports(EncounterKind.BRAWLER));
    }
    @Test void restoredBrawlerMotionRetainsItsContactClock(){
        assertEquals(12,com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(EncounterMove.DOUBLE,12).tick(),.001F);
        assertEquals(12,com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(EncounterMove.DOUBLE,25).tick(),.001F);
        assertEquals(16,com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(EncounterMove.SLAM,30).tick(),.001F);
        assertEquals(22,com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(EncounterMove.POUNCE,22).tick(),.001F);
        for(var move:new EncounterMove[]{EncounterMove.DOUBLE,EncounterMove.SLAM,EncounterMove.POUNCE})for(float t=0;t<=move.duration;t+=.25F){
            var pose=com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(move,t);
            assertTrue(CreaturePoseTable.HUNTER.has(pose.clip()));assertTrue(pose.weight()>=0&&pose.weight()<=1);
            for(var bone:CreaturePoseTable.HUNTER.sample(pose.clip(),pose.tick()))for(float n:bone)assertTrue(Float.isFinite(n));
        }
        assertEquals(0,com.codex.blacklight.client.model.EncounterBrawlerMotion.sample(EncounterMove.DOUBLE,20).weight());
    }
    @Test void originalBrawlerMotionBonesExistInRestoredGeometry()throws Exception{
        try(var in=getClass().getResourceAsStream("/assets/blacklight/geo/brawler_mc.geo.json")){
            var geo=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();
            var names=new java.util.HashSet<String>();for(var bone:geo.getAsJsonArray("bones"))names.add(bone.getAsJsonObject().get("name").getAsString());
            for(String bone:CreaturePoseTable.HUNTER.bones)assertTrue(names.contains(bone),bone);
        }
    }
    @Test void retargetedMotionKeepsEveryContactAnchor(){for(var move:new EncounterMove[]{EncounterMove.CLAW,EncounterMove.DOUBLE,EncounterMove.HAMMER,EncounterMove.SLAM,EncounterMove.POUNCE,EncounterMove.CHARGE,EncounterMove.HOWL,EncounterMove.ERUPTION}){var timing=com.codex.blacklight.client.model.EncounterSourceMotion.timing(move);assertEquals(timing.first(),timing.sourceTick(move,move.contact),.001F,move.name());if(move==EncounterMove.DOUBLE)assertEquals(timing.second(),timing.sourceTick(move,25),.001F);assertEquals(timing.length(),timing.sourceTick(move,move.duration),.001F);float last=-1;for(float t=0;t<=move.duration;t+=.25F){float source=timing.sourceTick(move,t);assertTrue(Float.isFinite(source)&&source>=last,move.name());last=source;}}}
    @Test void originalReferenceClipsActuallyExist(){var rig=com.codex.blacklight.client.model.SupremeHunterBlockRig.get();for(var move:EncounterMove.values()){var timing=com.codex.blacklight.client.model.EncounterSourceMotion.timing(move);assertTrue(rig.has(timing.clip()),timing.clip());for(float n:rig.sample(timing.clip(),timing.sourceTick(move,move.contact)))assertTrue(Float.isFinite(n));}}
    @Test void detailedHumanoidsHaveIndependentElbowsWristsAndKnees()throws Exception{for(var kind:EncounterKind.values())if(kind.humanoid()){try(var in=getClass().getResourceAsStream("/assets/blacklight/geo/"+kind.id+"_mc.geo.json")){var root=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject();assertEquals(512,root.getAsJsonObject("description").get("texture_width").getAsInt());var names=new java.util.HashSet<String>();for(var bone:root.getAsJsonArray("bones"))names.add(bone.getAsJsonObject().get("name").getAsString());for(String side:new String[]{"left","right"})for(String joint:new String[]{"forearm","hand","shin","finger_0","finger_3"})assertTrue(names.contains(side+"_"+joint),kind.id+" "+side+"_"+joint);}}}
    @Test void everySpeciesHasDistinctFiniteActions(){for(var kind:EncounterKind.values()){
        var actions=new java.util.HashSet<EncounterMove>();for(int p=0;p<3;p++)for(int n=0;n<12;n++)for(double d:new double[]{2,6,12,22})actions.add(kind.choose(d,p,n));
        if(kind==EncounterKind.FLYER){assertEquals(java.util.Set.of(EncounterMove.DIVE),actions);assertFalse(kind.permits(EncounterMove.VOLLEY));}
        else assertTrue(actions.size()>=2,kind.id);for(var action:actions){assertNotEquals(EncounterMove.NONE,action);assertTrue(action.duration>action.contact&&action.recovery>0);}
    }}
    @Test void noEarlyOrRecoveryDamage(){for(var move:EncounterMove.values()){for(int t=0;t<move.contact;t++)assertFalse(move.impact(t),move+" early "+t);assertFalse(move.impact(move.duration),move+" finished");for(float a=0;a<move.duration;a+=.25F){assertTrue(Float.isFinite(move.anticipation(a)));assertTrue(Float.isFinite(move.followThrough(a)));}}}
    @Test void mercerPhasesChangeWeaponsAndRecoveryRemains(){assertEquals(EncounterMove.DOUBLE,EncounterKind.ALEX_MERCER.choose(2,0,1));assertEquals(EncounterMove.SLAM,EncounterKind.ALEX_MERCER.choose(2,1,1));assertEquals(EncounterMove.BLADE,EncounterKind.ALEX_MERCER.choose(2,2,1));}
    @Test void multipartAndFactionProfiles(){assertEquals(3,EncounterKind.ELIZABETH_GREENE.weakParts());assertEquals(2,EncounterKind.GOLIATH.weakParts());assertTrue(EncounterKind.SUPER_SOLDIER.military);assertFalse(EncounterKind.ALEX_MERCER.military);assertTrue(EncounterKind.FLYER.flying);}
    @Test void shippedRigHasAnimationAndWeakpointBones()throws Exception{for(var kind:EncounterKind.values()){
        try(var input=getClass().getResourceAsStream("/assets/blacklight/geo/"+kind.id+"_mc.geo.json")){
            assertNotNull(input,kind.id);var json=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var bones=json.getAsJsonArray("minecraft:geometry").get(0).getAsJsonObject().getAsJsonArray("bones");var names=new java.util.HashSet<String>();for(var value:bones)names.add(value.getAsJsonObject().get("name").getAsString());
            for(String name:new String[]{"root","torso","head","left_arm","right_arm"})assertTrue(names.contains(name),kind.id+" "+name);
            for(int i=0;i<kind.weakParts();i++){
                String group="weak_"+i;
                assertTrue(names.contains(group)||kind==EncounterKind.ELIZABETH_GREENE&&names.stream().anyMatch(n->n.startsWith(group+"_Slug_")),kind.id+" weak part "+i);
            }
            if(kind==EncounterKind.ALEX_MERCER)for(String name:new String[]{"claws","hammer","blade"})assertTrue(names.contains(name));
        }
    }}
}

