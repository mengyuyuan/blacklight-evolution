package com.codex.blacklight.animation;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.joml.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BackspineFitTest {
    private static final String[] PARTS={"Head","Chest","Arm_R","Hand_R","Arm_L","Hand_L"};
    // Coordinates of ordinary wide skin, including sleeve and hat layers, in the bind pose.
    private static final float[][] BOX={{-.282F,1.469F,-.282F,.282F,2.033F,.282F},{-.266F,.88F,-.141F,.266F,1.517F,.141F},
            {.234F,1.11F,-.141F,.516F,1.517F,.141F},{.234F,.734F,-.141F,.516F,1.13F,.141F},
            {-.516F,1.11F,-.141F,-.234F,1.517F,.141F},{-.516F,.734F,-.141F,-.234F,1.13F,.141F}};
    private static float[][] vertices(JsonObject mesh,String piece){
        var points=new HashSet<Vector3f>();
        for(var face:mesh.getAsJsonArray(piece))for(var xyz:face.getAsJsonObject().getAsJsonArray("p")){
            var v=xyz.getAsJsonArray();points.add(new Vector3f(v.get(0).getAsFloat(),v.get(1).getAsFloat(),v.get(2).getAsFloat()));
        }
        return points.stream().map(v->new float[]{v.x,v.y,v.z}).toArray(float[][]::new);
    }
    private static boolean enters(float[][] vertices,Matrix4f local,float[] box){
        var point=new Vector3f();
        for(var v:vertices){
            local.transformPosition(point.set(v[0],v[1],v[2]));
            if(point.x>box[0]&&point.x<box[3]&&point.y>box[1]&&point.y<box[4]&&point.z>box[2]&&point.z<box[5])return true;
        }
        return false;
    }
    @Test void crossThrowKeepsTheSteppingFootPlantedUntilTheBladeClears(){
        var rig=BlacklightSkeleton.get();
        var bind=rig.getPoseMatrices(new BlacklightPose());
        for(int side=0;side<2;side++){
            var clip=MotionLibrary.clip("spines_ground_cross_"+(side==0?"r":"l"));
            int shin=rig.searchJointByName(side==0?"Leg_R":"Leg_L").getId();
            var heel=new Matrix4f(bind[shin]).invert().transformPosition(new Vector3f(side==0?.119F:-.119F,.018F,0));
            var plant=rig.getPoseMatrices(rig.localPose(clip,0,false))[shin].transformPosition(new Vector3f(heel));
            for(int frame=1;frame<=12;frame++){
                float t=frame/40F;
                var foot=rig.getPoseMatrices(rig.localPose(clip,t,false))[shin].transformPosition(new Vector3f(heel));
                assertEquals(plant.x,foot.x,.025F,"foot slid sideways at "+t);
                assertEquals(plant.z,foot.z,.025F,"foot slid forward at "+t);
            }
            assertTrue(clip.sample("Barb"+side,.125F,false).getScale(new Vector3f()).x>.95F);
            assertTrue(clip.sample("Barb"+side,.15F,false).getScale(new Vector3f()).x<.01F);
        }
    }
    @Test void foldingEdgesClearHeadChestAndSleevesAcrossReleaseAndRecovery() throws Exception{
        var rig=BlacklightSkeleton.get();
        var bind=rig.getPoseMatrices(new BlacklightPose());
        var keys=new ArrayList<>(List.of("idle","deploy","land","light_l","light_r"));
        for(String stance:List.of("air_","ground_"))for(String kind:List.of("cross","heavy","snap","draw","cross_draw"))
            for(String side:List.of("_l","_r"))keys.add(stance+kind+side);
        JsonObject mesh;
        try(var stream=getClass().getResourceAsStream("/assets/blacklight/models/backspines.json")){
            mesh=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonObject("joints");
        }
        var blades=new float[2][25][][];
        for(int side=0;side<2;side++)for(int frame=1;frame<=24;frame++)
            blades[side][frame]=vertices(mesh,frame==24?"Falx"+side:"Feed"+side+"_"+frame);
        for(String key:keys){
            var clip=MotionLibrary.clip("spines_"+key);
            for(int halfTick=0;halfTick<=java.lang.Math.round(clip.duration()*40);halfTick++){
                float t=halfTick/40F;
                for(float pitch:new float[]{0,-.80F,.80F}){
                    var pose=rig.localPose(clip,t,false);
                    pose.orElseEmpty("Head").rotation().rotateX(pitch);
                    var posed=rig.getPoseMatrices(pose);
                    var body=new Matrix4f[PARTS.length];
                    for(int j=0;j<body.length;j++){
                        int id=rig.searchJointByName(PARTS[j]).getId();
                        body[j]=new Matrix4f(bind[id]).mul(new Matrix4f(posed[id]).invert()).mul(posed[rig.chest.getId()]);
                    }
                    var fit=new BackspineFit(posed);
                    for(int slot=0;slot<2;slot++){
                        var attachment=fit.attachment(clip,t,slot);
                        var rack=attachment.blade();var barb=clip.sample("Barb"+slot,t,false);
                        var socket=attachment.socket().getTranslation(new Vector3f());
                        assertTrue(socket.distance(rack.getTranslation(new Vector3f()))<.0001F,"floating hinge at "+t);
                        int frame=java.lang.Math.round(barb.getScale(new Vector3f()).x*24);
                        if(frame==0||rack.getScale(new Vector3f()).x<.04F)continue;
                        for(int j=0;j<body.length;j++){
                            var m=new Matrix4f(body[j]).mul(rack);
                            if(enters(blades[slot][frame],m.translate(0,0,.027F),BOX[j]))
                                fail(key+" at "+t+" slot "+slot+" intersects "+PARTS[j]);
                        }
                    }
                }
            }
        }
    }
}
