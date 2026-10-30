package com.codex.blacklight.client.animation;

import com.google.gson.JsonParser;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import static org.junit.jupiter.api.Assertions.*;

/** Exercise the real flat vanilla parts with every packaged paired receiver scene. */
class ConsumeHumanoidSocketsTest {
    private static Vector3f point(ModelPart part,boolean young,boolean head) {
        return NativeReceiverPose.renderPosition(part.x,part.y,part.z,young,head);
    }
    private static double[] row(com.google.gson.JsonArray array) {
        var result=new double[array.size()];for(int i=0;i<result.length;i++)result[i]=array.get(i).getAsDouble();return result;
    }
    @Test void everyWeaponCaptureCarryAndConsumeKeepsActualMinecraftJointsConnected() throws Exception {
        var data=JsonParser.parseString(Files.readString(Path.of("art/motion-profile/consume-placement.data"))).getAsJsonObject();
        int frames=0;
        for(boolean slim:new boolean[]{false,true})for(boolean young:new boolean[]{false,true}) {
            var root=LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,slim),64,64).bakeRoot();
            var h=new PlayerModel<>(root,slim);
            h.young=young;
            for(var scene:data.getAsJsonObject("projections").entrySet()) {
                var rotations=scene.getValue().getAsJsonArray();var contacts=data.getAsJsonObject("contacts").getAsJsonArray(scene.getKey());
                for(int i=0;i<rotations.size();i+=3) {
                    root.getAllParts().forEach(p->p.loadPose(p.getInitialPose()));
                    boolean mounted=scene.getKey().startsWith("large_");
                    ConsumeVictimPose.projectHumanoid(h,new IdentityHashMap<ModelPart,PartPose>(),row(rotations.get(i).getAsJsonArray()),
                            mounted?null:row(contacts.get(i).getAsJsonArray()),mounted);
                    var label=scene.getKey()+" frame "+i+" slim="+slim+" young="+young;
                    var body=point(h.body,young,false);var head=point(h.head,young,true);
                    assertTrue(body.distance(head)<.001F,label+" detached neck by "+body.distance(head)+" pixels");
                    var bodyRotation=new Quaternionf().rotationZYX(h.body.zRot,h.body.yRot,h.body.xRot);
                    float scale=young?.5F:1;
                    for(var arm:new ModelPart[]{h.rightArm,h.leftArm}) {
                        var bind=arm.getInitialPose();var expected=bodyRotation.transform(new Vector3f(bind.x,bind.y,bind.z).mul(scale)).add(body);
                        assertTrue(expected.distance(point(arm,young,false))<.001F,label+" detached shoulder");
                    }
                    var waist=bodyRotation.transform(new Vector3f(0,12*scale,0)).add(body);
                    assertTrue(waist.distance(point(h.rightLeg,young,false).add(point(h.leftLeg,young,false)).mul(.5F))<.001F,label+" detached waist");
                    assertEquals(h.body.x,h.jacket.x,.001F,label+" jacket detached");
                    assertEquals(h.rightArm.y,h.rightSleeve.y,.001F,label+" sleeve detached");
                    frames++;
                }
            }
        }
        assertTrue(frames>1000,"Expected all authored pair scenes, checked "+frames);
        System.out.println("Connected receiver frames checked: "+frames);
    }
}
