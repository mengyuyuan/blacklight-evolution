package com.codex.blacklight.animation;
import com.codex.blacklight.client.animation.WeaponMorphTimeline;
import com.codex.blacklight.client.animation.WeaponMorphTiming;
import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScapulaVolleyTest {
    @Test void satellitesOccupyDistinctSocketsAndLeaveWithTheCenterBlade(){
        var up=new Vec3(0,1,0);var right=new Vec3(1,0,0);
        for(boolean charged:new boolean[]{false,true}){
            int n=ScapulaVolley.pairs(true,charged);
            float at=1;
            var key="spines_air_crush_"+(charged?"charged":"throw");
            var before=ScapulaVolley.strain(key,at-.02F,0);
            var after=ScapulaVolley.strain(key,at,0);
            var center=ScapulaMount.bladeCenter();
            for(int slot=-n;slot<=n;slot++){
                Vec3 heading=ScapulaVolley.aim(up,right,slot,charged);
                assertEquals(1,heading.length(),.00001);
                assertEquals(1,before.blade(slot));
                if(slot==0)continue;
                assertEquals(0,after.blade(slot));
                assertEquals(Integer.signum(slot),Math.signum(heading.x));
                assertTrue(ScapulaVolley.socket(new Matrix4f(),slot).transformPosition(ScapulaMount.bladeCenter()).distance(center)>.12);
            }
        }
        assertEquals(0,ScapulaVolley.pairs(false,true));
        assertEquals(1,ScapulaVolley.pairs(true,false));
        assertEquals(2,ScapulaVolley.pairs(true,true));
    }
    @Test void chargeWaveTravelsFromBackToSocketWhileGravityContinues(){
        var tap=ScapulaVolley.strain("spines_air_crush_draw",3,3);
        var full=ScapulaVolley.strain("spines_air_crush_draw",10,15);
        assertEquals(1,tap.inner());assertEquals(0,tap.outer());assertEquals(1,full.outer());
        assertNotEquals(full.beat(),ScapulaVolley.strain("spines_air_crush_draw",10,21).beat());
        double rootPeak=2.3, tipPeak=rootPeak+3.6;
        assertTrue(ScapulaVolley.vascularPulse(rootPeak,0)>.95F);
        assertTrue(ScapulaVolley.vascularPulse(rootPeak,1)<.1F);
        assertTrue(ScapulaVolley.vascularPulse(tipPeak,1)>.95F);
        for(double vertical:new double[]{-.8,0,.42}){
            var velocity=new Vec3(.52,vertical,.14);
            var recoil=ScapulaVolley.breakAway(velocity,new Vec3(0,-.8,.6),Vec3.ZERO,true);
            assertEquals(vertical,recoil.y);
            assertTrue(recoil.x>.45);
        }
        assertEquals(1,ScapulaVolley.strain("spines_light_burst",4,0).kick());
        assertEquals(1,ScapulaVolley.strain("spines_light_burst",10,0).kick());
    }
    @Test void flightLightsKeepTheirHeightVelocityAndCannotFarmSpeed(){
        var old=new Vec3(.46,-.31,.71);var left=new Vec3(-1,0,0);
        assertEquals(old,ScapulaVolley.bankLight(old,Vec3.ZERO));
        var turn=ScapulaVolley.bankLight(old,left);
        assertTrue(turn.x<old.x&&turn.z>0);assertEquals(old.y,turn.y);
        for(int i=0;i<20;i++)turn=ScapulaVolley.bankLight(turn,left);
        assertTrue(turn.horizontalDistance()<=old.horizontalDistance()+1e-6);assertEquals(old.y,turn.y);
        for(String name:java.util.List.of("sweep","lift","drive","burst"))for(boolean right:new boolean[]{false,true}){
            String key=ScapulaCycle.actionKey("spines_light_flight_"+name,right);
            assertTrue(MotionLibrary.find(key).isPresent());assertEquals(name.equals("lift")||name.equals("burst")?4:5,ScapulaCycle.shotTick(key,right));
            assertFalse(ScapulaCycle.chamber(key,3,3,right,1,1).secondary());
        }
    }
    @Test void heavyBreakawayHonorsStrafeAndNeutralRecoil(){
        var f=new Vec3(0,0,1);var v=new Vec3(0,-.15,.1);
        var neutral=ScapulaVolley.breakAway(v,f,Vec3.ZERO,true);
        var left=ScapulaVolley.breakAway(v,f,new Vec3(-1,0,0),true);
        var tap=ScapulaVolley.breakAway(v,f,new Vec3(-1,0,0),false);
        assertTrue(neutral.z<-.35&&left.x<-.45&&tap.x>left.x&&left.z>0);
        assertEquals(left.y,neutral.y);assertTrue(left.y<0);
    }
    @Test void extendedFanClearsTheHeadAndSleeve() throws Exception {
        var rig=BlacklightSkeleton.get();
        var bind=rig.getPoseMatrices(new BlacklightPose());
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/models/scapula.json")){
            var mesh=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream)).getAsJsonObject().getAsJsonObject("joints");
            for(String kind:new String[]{"crush","return","sweep"}){
                String key="spines_air_"+kind+"_draw";
                var clip=MotionLibrary.clip(key);
                for(int tick=2;tick<=10;tick++){
                    var pose=rig.localPose(clip,tick/20F,false);
                    var bones=rig.getPoseMatrices(pose);
                    var hinge=ScapulaMount.hinge(ScapulaMount.chest(bones));
                    var state=clip.sample("Scapula",tick/20F,false).getTranslation(new org.joml.Vector3f());
                    var strain=ScapulaVolley.strain(key,tick,0);
                    for(int slot:new int[]{-2,-1,1,2}){
                        float growth=strain.blade(slot)*state.y;
                        if(growth<.02F)continue;
                        for(var bone:java.util.List.of(rig.head,rig.armL,rig.handL)){
                            int id=bone.getId();
                            var local=new Matrix4f(bind[id]).mul(new Matrix4f(bones[id]).invert())
                                    .mul(ScapulaMount.rootedGrowth(ScapulaVolley.socket(hinge,slot),ScapulaMount.frame(state.x),growth));
                            for(var face:mesh.getAsJsonArray("quill"+ScapulaMount.frame(state.x)))for(var vertex:face.getAsJsonObject().getAsJsonArray("p")){
                                var a=vertex.getAsJsonArray();var v=local.transformPosition(new org.joml.Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
                                boolean inside=bone==rig.head?Math.abs(v.x)<.279F&&Math.abs(v.z)<.279F&&v.y>1.472F&&v.y<2.03F:
                                     v.x>-.511F&&v.x<-.239F&&Math.abs(v.z)<.136F&&(bone==rig.armL?v.y>1.115F&&v.y<1.512F:v.y>.739F&&v.y<1.125F);
                                assertFalse(inside,key+" tick "+tick+" slot "+slot+" "+bone.getName());
                            }
                        }
                    }
                }
            }
        }
    }
    @Test void interruptedShoulderMorphRetainsGrowthWithoutCoveringHands(){
        int i=PowerForm.BACKSPINES.ordinal();
        var timeline=new WeaponMorphTimeline(PowerForm.NONE,0);
        timeline.retarget(PowerForm.BACKSPINES,0,0,16,false);
        float rising=timeline.weights(8)[i];
        timeline.retarget(PowerForm.BLADE,8,8,18,false);
        assertEquals(rising,timeline.weights(8)[i]);
        float falling=timeline.weights(11)[i];assertTrue(falling>0&&falling<rising);
        timeline.retarget(PowerForm.BACKSPINES,11,11,12,false);
        assertEquals(falling,timeline.weights(11)[i]);assertEquals(1,timeline.weights(24)[i]);
        assertFalse(WeaponMorphTiming.arm(PowerForm.BACKSPINES,true));
        assertFalse(WeaponMorphTiming.arm(PowerForm.BACKSPINES,false));
    }
}
