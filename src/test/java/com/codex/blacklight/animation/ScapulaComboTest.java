package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScapulaComboTest {
    private static Vector3f organ(MatrixMotionClip clip,float t) {
        return clip.sample("Scapula",t,false).getTranslation(new Vector3f());
    }
    private static void release(String key,int tick) {
        var clip=MotionLibrary.clip("spines_"+key);
        var before=organ(clip,tick/20F-.001F);
        assertEquals(ScapulaMount.OPEN_FRAME,ScapulaMount.frame(before.x),key);
        assertEquals(1F,before.y,.001F,key);
        assertEquals(0F,organ(clip,tick/20F).y,.001F,key);
        assertEquals(1F,organ(clip,clip.duration()).y,.001F,key);
    }
    private static double bend(String move,float time) {
        var rig=BlacklightSkeleton.get();
        var pose=rig.getPoseMatrices(MotionLibrary.pose("spines_"+move,time,false));
        var bind=rig.getPoseMatrices(new BlacklightPose());
        var up=new Matrix4f(pose[rig.torso.getId()]).mul(new Matrix4f(bind[rig.torso.getId()]).invert())
                .transformDirection(new Vector3f(0,1,0)).normalize();
        return Math.toDegrees(Math.acos(up.y));
    }
    private static Matrix4f deformation(String move,String bone,float ticks) {
        var rig=BlacklightSkeleton.get();int index=rig.searchJointByName(bone).getId();
        var pose=rig.getPoseMatrices(MotionLibrary.pose("spines_"+move,ticks/20,false));
        return new Matrix4f(pose[index]).mul(rig.getPoseMatrices(new BlacklightPose())[index].invert());
    }
    private static double yaw(String move,String bone,float tick){
        var v=deformation(move,bone,tick).transformDirection(new Vector3f(0,0,-1));
        return Math.toDegrees(Math.atan2(-v.x,-v.z));
    }
    @Test void hipLeadsShoulderAndFrontFootStaysDownThroughRelease() {
        String move="ground_crush_charged";
        assertTrue(yaw(move,"Root",3.4F)<-16);
        assertTrue(yaw(move,"Chest",3.4F)>42);
        assertTrue(yaw(move,"Chest",5)<-28);
        assertTrue(bend("light_sweep",.25F)<18);
        assertTrue(bend(move,.36F)>34);
        // The boot pivots about its toe, which the two-bone solver plants at this authored point.
        var toe=new Vector3f(-.123F,0,-.115F);
        var planted=new Vector3f(-.232F,.014F,-.355F);
        for(int sample=0;sample<=9.5F*8;sample++) {
            float tick=sample/8F;
            var contact=deformation(move,"Leg_L",tick).transformPosition(new Vector3f(toe));
            assertTrue(planted.distance(contact)<.018F,()->"Front toe at tick "+tick+": "+contact);
        }
    }
    @Test void lightBranchesStartOnTheOutgoingPose() {
        for(String bone:List.of("Root","Head","Torso","Chest","Hand_L","Hand_R","Leg_L","Leg_R")){
            assertTrue(deformation("light_sweep",bone,7).equals(deformation("ground_return_draw",bone,0),.0002F),bone);
            assertTrue(deformation("light_sweep",bone,9).equals(deformation("light_lift",bone,0),.0002F),bone);
        }
    }
    @Test void detachedEntityAndHeldBladeShareTheReleaseFrame() {
        release("light_sweep",5);
        release("light_lift",4);
        release("light_drive",5);
        release("light_burst",4);
        release("light_burst",10);
        assertEquals(1F,organ(MotionLibrary.clip("spines_light_burst"),.45F).y,.001F);
        for(String stance:List.of("ground_","air_"))for(String kind:List.of("return","sweep","crush")){
            release(stance+kind+"_throw",stance.equals("air_")?1:4);
            release(stance+kind+"_charged",stance.equals("air_")?1:5);
            var draw=MotionLibrary.clip("spines_"+stance+kind+"_draw");
            assertEquals(new Vector3f(1,1,0),organ(draw,.50F));
        }
    }
    @Test void raisedAndRegrowingBladeClearTheHatAndThrowingSleeve() throws Exception {
        var rig=BlacklightSkeleton.get();
        var bind=rig.getPoseMatrices(new BlacklightPose());
        var vertices=new Vector3f[ScapulaMount.OPEN_FRAME+1][];
        try(var in=getClass().getResourceAsStream("/assets/blacklight/models/scapula.json");
                var reader=new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)){
            var joints=JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("joints");
            for(int i=0;i<vertices.length;i++){
                var unique=new HashSet<Vector3f>();
                for(var face:joints.getAsJsonArray("quill"+i))for(var point:face.getAsJsonObject().getAsJsonArray("p")){
                    var p=point.getAsJsonArray();
                    unique.add(new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat()));
                }
                vertices[i]=unique.toArray(Vector3f[]::new);
            }
        }
        var moves=new LinkedHashSet<String>();
        for(String base:List.of("scapula_deploy","scapula_idle","light_sweep","light_lift","light_drive","light_burst","scapula_land","scapula_land_ready"))
            moves.add("spines_"+base);
        for(String stance:List.of("ground_","air_"))for(String kind:List.of("return","sweep","crush"))
            for(String stage:List.of("_draw","_throw","_charged"))moves.add("spines_"+stance+kind+stage);
        for(String flight:List.of("sweep","lift","drive","burst"))moves.add("spines_light_flight_"+flight);
        for(String key:List.copyOf(moves))moves.add(ScapulaCycle.actionKey(key,true));
        for(String key:moves) {
            var clip=MotionLibrary.clip(key);
            // NativeHeadLook yields to authored actions. Only braceLean adds head pitch during a draw.
            float[] headPitches=key.endsWith("_draw")?new float[]{-.09F,0,.09F}:new float[]{0};
            // Eighth ticks sample the spherical interpolation between authored quarter-tick keys.
            for(int sample=0;sample<=Math.round(clip.duration()*160);sample++) {
                float tick=sample/8F, seconds=tick/20;
                var state=organ(clip,seconds);
                for(float pitch:headPitches) {
                    var pose=MotionLibrary.pose(key,seconds,false);
                    pose.orElseEmpty("Head").rotation().rotateX(pitch);
                    var matrices=rig.getPoseMatrices(pose);
                    var chest=ScapulaMount.chest(matrices);
                    for(boolean right:new boolean[]{false,true}) {
                        var chamber=ScapulaCycle.chamber(key,tick,tick,right,state.x,state.y);
                        if(chamber.growth()<=.001F)continue;
                        var hinge=ScapulaMount.hinge(ScapulaMount.sided(chest,right));
                        var seat=ScapulaMount.rootedGrowth(ScapulaCycle.seat(hinge,chamber.primary(),chamber.primarySpread()),ScapulaMount.frame(chamber.open()),chamber.growth());
                        for(var bone:List.of(rig.head,rig.armL,rig.handL,rig.armR,rig.handR)) {
                            int id=bone.getId();
                            var local=new Matrix4f(bind[id]).mul(new Matrix4f(matrices[id]).invert()).mul(seat);
                            var point=new Vector3f();
                            for(var v:vertices[ScapulaMount.frame(chamber.open())]) {
                                local.transformPosition(v,point);
                                if(insidePlayerLayer(rig,bone,point))
                                    fail(key+" tick "+tick+", right="+right+", pitch="+pitch+" intersected "+bone.getName()+" at "+point);
                            }
                        }
                    }
                }
            }
        }
    }
    private static boolean insidePlayerLayer(BlacklightSkeleton rig,BlacklightSkeleton.Bone bone,Vector3f point) {
        if(bone==rig.head) {
            return Math.abs(point.x)<.279F&&Math.abs(point.z)<.279F&&point.y>1.472F&&point.y<2.030F;
        }
        float leftX=bone==rig.armR||bone==rig.handR?-point.x:point.x;
        boolean upperArm=bone==rig.armL||bone==rig.armR;
        return leftX>-.511F&&leftX<-.239F&&Math.abs(point.z)<.136F
                &&(upperArm?point.y>1.115F&&point.y<1.512F:point.y>.739F&&point.y<1.125F);
    }
}
