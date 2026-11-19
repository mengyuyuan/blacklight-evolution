package com.codex.blacklight.animation;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScapulaCycleTest {
    @Test void mirroredBodyReflectsTheLoadedShoulderAndTheSupportingLeg(){
        var rig=BlacklightSkeleton.get();
        var binds=rig.getPoseMatrices(new BlacklightPose());
        var mirror=new Matrix4f().scaling(-1,1,1);
        for(String move:List.of("light_sweep","light_lift","light_drive","light_burst","ground_return_draw","air_crush_charged")){
            var left="spines_"+move;var right=ScapulaCycle.actionKey(left,true);
            assertEquals(left,ScapulaCycle.sourceKey(right));
            for(float tick:new float[]{1,4,7.2F,10}){
                var a=rig.getPoseMatrices(MotionLibrary.pose(left,tick/20,false));
                var b=rig.getPoseMatrices(MotionLibrary.pose(right,tick/20,false));
                for(String[] pair:new String[][]{{"Root","Root"},{"Chest","Chest"},{"Arm_L","Arm_R"},{"Leg_R","Leg_L"}}){
                    int s=rig.searchJointByName(pair[0]).getId(),d=rig.searchJointByName(pair[1]).getId();
                    var expected=new Matrix4f(mirror).mul(a[s]).mul(new Matrix4f(binds[s]).invert()).mul(mirror);
                    var actual=new Matrix4f(b[d]).mul(new Matrix4f(binds[d]).invert());
                    assertTrue(actual.equals(expected,.006F),right+" "+tick+" "+pair[0]+" "+actual+" / "+expected);
                }
            }
        }
    }

    @Test void burstEmptiesOnlyTheFiringShoulderAndTheFanUsesBoth(){
        for(boolean leadRight:new boolean[]{false,true}){
            var key=ScapulaCycle.actionKey("spines_light_burst",leadRight);
            for(int tick:new int[]{4,10}){
                boolean firing=tick==4?leadRight:!leadRight;
                assertEquals(0,ScapulaCycle.chamber(key,tick,0,firing,1,0).growth());
                assertEquals(1,ScapulaCycle.chamber(key,tick,0,!firing,1,0).growth());
            }
            for(int n:new int[]{1,2}){
                int[] counts=new int[2];
                for(int lane=-n;lane<=n;lane++)counts[ScapulaCycle.rightForLane(leadRight,lane)?1:0]++;
                assertEquals(n+1,counts[leadRight?1:0]);
                assertEquals(n,counts[leadRight?0:1]);
            }
        }
    }

    @Test void detachedMirroredMeshMeetsTheSameVertexAsTheSocket(){
        var rig=BlacklightSkeleton.get();
        var chest=ScapulaMount.chest(rig.getPoseMatrices(MotionLibrary.pose("spines_air_r_crush_charged",.25F,false)));
        for(int lane=-2;lane<=2;lane++){
            boolean right=ScapulaCycle.rightForLane(true,lane);
            var mount=ScapulaCycle.seat(ScapulaMount.hinge(ScapulaMount.sided(chest,right)),lane,1);
            var rotation=ScapulaMount.sided(mount,right).getUnnormalizedRotation(new Quaternionf()).normalize();
            var vertex=ScapulaMount.bladeCenter().add(ScapulaMount.bladeDirection().mul(.19F));
            var held=mount.transformPosition(new Vector3f(vertex));
            var detached=vertex.sub(ScapulaMount.bladeCenter()).mul(ScapulaVolley.size(lane));
            if(right)detached.x=-detached.x;
            rotation.transform(detached).add(mount.transformPosition(ScapulaMount.bladeCenter()));
            assertTrue(held.distance(detached)<.0001F);
        }
    }

    @Test void bothLoadedBladesClearTheHeadAndTheirOwnSleeves() throws Exception{
        var rig=BlacklightSkeleton.get();var bind=rig.getPoseMatrices(new BlacklightPose());
        var verts=new ArrayList<List<Vector3f>>();
        try(var in=getClass().getResourceAsStream("/assets/blacklight/models/scapula.json")){
            var mesh=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in))).getAsJsonObject().getAsJsonObject("joints");
            for(int i=0;i<=16;i++){
                var unique=new HashSet<Vector3f>();
                for(var face:mesh.getAsJsonArray("quill"+i))for(var p:face.getAsJsonObject().getAsJsonArray("p")){
                    var a=p.getAsJsonArray();unique.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
                }
                verts.add(List.copyOf(unique));
            }
        }
        var moves=new ArrayList<>(List.of("scapula_idle","light_sweep","light_lift","light_drive","light_burst"));
        for(String stance:List.of("ground_","air_"))for(String branch:List.of("crush","return","sweep"))
            for(String stage:List.of("_draw","_charged","_throw"))moves.add(stance+branch+stage);
        var point=new Vector3f();
        for(String move:moves){
            String key="spines_"+move;var clip=MotionLibrary.clip(key);
            for(int frame=0;frame<=Math.round(clip.duration()*40);frame++){
                float t=frame/40F;
                var body=rig.getPoseMatrices(MotionLibrary.pose(key,t,false));
                var gear=clip.sample("Scapula",t,false).getTranslation(new Vector3f());
                for(boolean right:new boolean[]{false,true}){
                    var chamber=ScapulaCycle.chamber(key,t*20,0,right,gear.x,gear.y);
                    var hinge=ScapulaMount.hinge(ScapulaMount.sided(ScapulaMount.chest(body),right));
                    for(int lane:new int[]{0,right?-1:1,right?-2:2}){
                        boolean primary=chamber.primary()==lane;
                        if(!primary&&(!chamber.secondary()||lane==0))continue;
                        float scale=chamber.growth()*(primary?1:chamber.tissue().blade(lane));
                        if(scale<.01F)continue;
                        var socket=ScapulaCycle.seat(hinge,lane,primary?chamber.primarySpread():1).scale(scale);
                        for(var bone:List.of(rig.head,right?rig.armR:rig.armL,right?rig.handR:rig.handL)){
                            var local=new Matrix4f(bind[bone.getId()]).mul(new Matrix4f(body[bone.getId()]).invert()).mul(socket);
                            for(var v:verts.get(ScapulaMount.frame(chamber.open()))){
                                local.transformPosition(v,point);
                                float x=right?-point.x:point.x;
                                boolean inside=bone==rig.head?Math.abs(x)<.279F&&Math.abs(point.z)<.279F&&point.y>1.472F&&point.y<2.03F:
                                        x>-.511F&&x<-.239F&&Math.abs(point.z)<.136F&&(bone==rig.armR||bone==rig.armL?point.y>1.115F&&point.y<1.512F:point.y>.739F&&point.y<1.125F);
                                if(inside)fail(key+" @ "+t+" right="+right+" lane="+lane+" "+bone.getName()+" "+point);
                            }
                        }
                    }
                }
            }
        }
    }
}
