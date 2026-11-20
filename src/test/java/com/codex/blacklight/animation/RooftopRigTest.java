package com.codex.blacklight.animation;

import java.security.MessageDigest;
import java.util.HexFormat;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RooftopRigTest {
    @Test void spentSocketOpensItsOwnValvesAndFeedsAlongTheLaunchAxis() {
        var rig = RooftopRig.INSTANCE;
        int slot = 3;
        int valve = rig.id("Valve.3.1"), sheath = rig.id("Sheath.3");
        assertEquals(slot, rig.socketSlot(valve));
        assertEquals(slot, rig.socketSlot(sheath));
        assertEquals(-1, rig.socketSlot(rig.rib[slot]));
        var identity = new org.joml.Matrix4f();
        assertTrue(rig.refillFrame(new org.joml.Matrix4f(), valve, 1, 9, false).equals(identity, .00001F));
        var open = rig.refillFrame(new org.joml.Matrix4f(), valve, .3F, 3, false);
        var reflected = rig.refillFrame(new org.joml.Matrix4f(), valve, .3F, 3, true);
        assertTrue(open.transformDirection(new Vector3f(0, 1, 0)).x > .2F);
        assertTrue(reflected.transformDirection(new Vector3f(0, 1, 0)).x < -.2F);
        assertTrue(rig.refillFrame(new org.joml.Matrix4f(), sheath, .5F, 4, false).m00() > 1.1F);
        var ready = rig.refillFrame(new org.joml.Matrix4f(), rig.quill[slot], 1, 8, false);
        assertTrue(ready.equals(new org.joml.Matrix4f().translate(0, .057F, 0), .00001F));
    }
    @Test void groundVolleyKeepsBothSolesPlantedAsTheKneesCarryRecoil() {
        var rig=BlacklightSkeleton.get();
        var rest=rig.getPoseMatrices(new BlacklightPose());
        for (String side : new String[]{"L","R"}) {
            int shin=rig.searchJointByName("Leg_"+side).getId();
            int sole=rig.searchJointByName("Knee_"+side).getId();
            var inverse=new org.joml.Matrix4f(rest[sole]).invert();
            Vector3f firstSole=null, firstKnee=null;
            float swing=0;
            for(int frame=0;frame<32;frame++) {
                var bones=rig.getPoseMatrices(MotionLibrary.pose("spines_ground_crush_throw",frame/40F,false));
                RooftopRig.plantGroundShot(bones);
                var heel=new org.joml.Matrix4f(bones[sole]).mul(inverse)
                        .transformPosition(new Vector3f(side.equals("L")?-.125F:.125F,.052F,0));
                var knee=bones[shin].getTranslation(new Vector3f());
                if(firstSole==null){firstSole=heel;firstKnee=knee;}
                assertEquals(0,firstSole.distance(heel),.001F);
                swing=Math.max(swing,firstKnee.distance(knee));
            }
            assertTrue(swing>.025F,side+" knee should articulate");
        }
    }

    @Test void loadsBothArticulatedShoulderCovers() throws Exception {
        try(var data=RooftopRig.class.getResourceAsStream(RooftopRig.ASSET)){
            assertNotNull(data);
            assertEquals("642177ff2be677f961378e06626ecb10159d67c626ada36b4e6e691ffd8386f7",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.readAllBytes())));
        }
        var rig=RooftopRig.INSTANCE;
        assertEquals(21,rig.geometry.length);
        assertThrows(IllegalStateException.class,()->rig.id("Mercer skinned mesh"));
        for(int i=0;i<6;i++){
            assertTrue(rig.attached(rig.quill[i]));
            assertEquals(rig.mesh[rig.quill[i]],rig.mesh[rig.id("Free quill."+i)]);
            assertEquals(rig.id("Launch."+i),rig.parent[rig.quill[i]]);
        }
    }
    @Test void everyReleaseMatchesTheAuthoredSocketAndAxis(){
        float[][] roots={{0.4068610F, 1.4155499F, -0.2133764F},
            {-0.4316409F, 1.3272191F, -0.3135860F},
            {0.3798128F, 1.3841530F, -0.0864084F},
            {-0.5450853F, 1.0925498F, -0.0794779F},
            {0.3680468F, 1.2048523F, 0.0893869F},
            {-0.4652688F, 0.9471649F, -0.0500588F}},axes={{-0.0385964F, 0.0728714F, -0.9965942F},
            {0.0393216F, 0.0772845F, -0.9962334F},
            {-0.0519023F, 0.0458535F, -0.9975989F},
            {0.0545736F, 0.0578175F, -0.9968344F},
            {-0.0692734F, 0.0387011F, -0.9968467F},
            {0.0693288F, 0.0385902F, -0.9968472F}};
        var rig=RooftopRig.INSTANCE;
        for(int i=0;i<6;i++){
            String move=i==0?"spines_light_r_sweep":i==1?"spines_light_lift":"spines_ground_r_sweep_charged";
            int age=i==0?5:i==1?4:i+3;
            int index=RooftopRig.index(move,i%2==0,0,age);
            assertEquals(i,index);
            var launch=rig.exit(move,age,index,null);
            assertTrue(launch.getTranslation(new Vector3f()).distance(new Vector3f(roots[i]))<.00004F);
            assertTrue(launch.transformDirection(new Vector3f(0,1,0)).distance(new Vector3f(axes[i]))<.00004F);
        }
    }
    @Test void transferKeepsTheOrdinaryPlayersSkeletonAndShoulderWidth(){
        var rig=RooftopRig.INSTANCE;
        var skeleton=BlacklightSkeleton.get();
        var rest=skeleton.getPoseMatrices(new BlacklightPose());
        var pose=skeleton.getPoseMatrices(new BlacklightPose());
        var src=rig.sample(3.34F);
        rig.projectPlayer(src,false,pose);
        var left=pose[skeleton.armL.getId()].getTranslation(new Vector3f());
        var right=pose[skeleton.armR.getId()].getTranslation(new Vector3f());
        float originalWidth=rest[skeleton.armL.getId()].getTranslation(new Vector3f()).distance(rest[skeleton.armR.getId()].getTranslation(new Vector3f()));
        assertEquals(originalWidth,left.distance(right),.14F);
        assertFalse(pose[skeleton.chest.getId()].equals(rest[skeleton.chest.getId()],.01F));
        for(int i=0;i<6;i++){
            String key=i==0?"spines_light_r_sweep":i==1?"spines_light_lift":"spines_ground_r_sweep_charged";
            int age=i==0?5:i==1?4:i+3;
            var sample=rig.sample(RooftopRig.time(key,age));
            rig.projectPlayer(sample,false,pose);
            var chest=ScapulaMount.chest(pose);
            var seated=rig.frame(sample,rig.parent[rig.quill[i]],key,chest).translate(0,.057F,0);
            assertTrue(seated.equals(rig.exit(key,age,i,chest),.00001F));
        }
    }
    @Test void overlappingScalesLeaveTheSpineClearAtRest(){
        var rig=RooftopRig.INSTANCE;
        var ready=rig.sample(1);
        var chestInverse=new org.joml.Matrix4f(ready[rig.chest]).invert();
        for(int i=0;i<6;i++){
            float sign=i%2==0?1:-1;
            var rib=new org.joml.Matrix4f(chestInverse).mul(ready[rig.rib[i]]);
            var vertices=rig.geometry[rig.mesh[rig.rib[i]]].pos();
            for(int j=0;j<vertices.length;j+=3){
                var p=rib.transformPosition(new Vector3f(vertices[j],vertices[j+1],vertices[j+2]));
                assertTrue(p.x*sign>.123F);
            }
            assertTrue(rig.attached(rig.id("Outer scale."+i)));
        }
    }
    @Test void shoulderRootsStayBuriedOnTheirOwnSideThroughTheVolley(){
        var rig=RooftopRig.INSTANCE;
        var skeleton=BlacklightSkeleton.get();
        int torso=skeleton.searchJointByName("Torso").getId();
        var bind=skeleton.getPoseMatrices(new BlacklightPose());
        var torsoInverseBind=new org.joml.Matrix4f(bind[torso]).invert();
        for(int side:new int[]{-1,1}){
            int root=rig.id("Scapular root."+side);
            assertEquals(rig.rib[side==1?4:5],rig.parent[root]);
            assertThrows(IllegalStateException.class,()->rig.id("Tendril."+side+".0"));
            float[] vertices=rig.geometry[rig.mesh[root]].pos();
            for(int tick=0;tick<=110;tick++){
                var sample=rig.sample(tick/20F);
                var inBody=rig.frame(sample,root,"",new org.joml.Matrix4f());
                var insertion=inBody.transformPosition(new Vector3f());
                assertTrue(insertion.distance(new Vector3f(side*.148F,1.118F,.049F))<.0001F);
                var pose=skeleton.getPoseMatrices(new BlacklightPose());
                rig.projectPlayer(sample,false,pose);
                var inTorso=new org.joml.Matrix4f(pose[torso]).mul(torsoInverseBind).invert()
                        .mul(ScapulaMount.chest(pose)).transformPosition(new Vector3f(insertion));
                assertTrue(Math.abs(inTorso.x)<.25F&&Math.abs(inTorso.z)<.125F&&inTorso.y>.751F&&inTorso.y<1.501F);
                boolean buried=false,exposed=false;
                for(int i=0;i<vertices.length;i+=3){
                    var p=inBody.transformPosition(new Vector3f(vertices[i],vertices[i+1],vertices[i+2]));
                    assertTrue(side*p.x>.096F);
                    if(p.distance(insertion)<.001F)buried=true;
                    if(p.z>.15F)exposed=true;
                }
                assertTrue(buried);
                if(tick>=23&&tick<=79)assertTrue(exposed);
            }
        }
    }
    @Test void weightTransferFollowsTheSourcePelvisAndTheHeels(){
        var rig=RooftopRig.INSTANCE;
        var sk=BlacklightSkeleton.get();
        var rest=sk.getPoseMatrices(new BlacklightPose());
        for(float at:new float[]{.42F,.82F,1.52F,2.88F,3.34F,3.62F,3.80F,4.18F,4.95F}){
            var world=rig.sample(at);
            var pose=sk.getPoseMatrices(new BlacklightPose());
            rig.projectPlayer(world,false,pose);
            var skin=new org.joml.Matrix4f(pose[sk.rootJoint.getId()]).mul(new org.joml.Matrix4f(rest[sk.rootJoint.getId()]).invert());
            var source=world[rig.id("DownBody")].getUnnormalizedRotation(new org.joml.Quaternionf()).normalize();
            assertEquals(1,Math.abs(source.dot(skin.getUnnormalizedRotation(new org.joml.Quaternionf()).normalize())),.0001);
            for(boolean right:new boolean[]{false,true}){
                int id=sk.searchJointByName(right?"Knee_R":"Knee_L").getId();
                var bind=new Vector3f(right?.125F:-.125F,.052F,0);
                var ankle=new org.joml.Matrix4f(pose[id]).mul(new org.joml.Matrix4f(rest[id]).invert()).transformPosition(bind);
                var sourceAnkle=world[rig.id(right?"RightFoot":"LeftFoot")].getTranslation(new Vector3f());
                assertEquals(.052F+sourceAnkle.y-.1665F,ankle.y,.045F);
            }
        }
    }
    @Test void onlyFiredSocketsRegrowAndEachCompletesWithinEightTicks(){
        int[][] expected={{-1,-1,5,6,7,8},{-1,-1,5,5,-1,-1},{4,10,-1,-1,-1,-1},{1,-1,1,1,1,1},{1,-1,1,1,-1,-1}};
        String[] keys={"spines_ground_r_sweep_charged","spines_ground_r_crush_charged","spines_ground_r_crush_throw",
                "spines_air_r_crush_charged","spines_air_r_crush_throw"};
        for(int k=0;k<keys.length;k++)for(int q=0;q<6;q++){
            assertEquals(expected[k][q],RooftopRig.releaseTick(keys[k],q));
            assertEquals(expected[k][q],RooftopRig.releaseTick(keys[k].replace("_r_","_"),q));
            assertEquals(1,RooftopRig.refill(-.1,q));
            assertEquals(0,RooftopRig.refill(0,q));
            assertTrue(RooftopRig.refill(4,q)>.25&&RooftopRig.refill(4,q)<.8);
            assertEquals(1,RooftopRig.refill(8,q));
        }
    }
    @Test void aLeftLeadingSweepReflectsTheWholeAttachment(){
        for(int i=2;i<6;i++){
            int age=3+i;
            var right=RooftopRig.INSTANCE.exit("spines_ground_r_sweep_charged",age,i,null);
            var left=RooftopRig.INSTANCE.exit("spines_ground_sweep_charged",age,i,null);
            assertTrue(left.equals(RooftopRig.mirror(right),.00001F));
        }
    }
}
