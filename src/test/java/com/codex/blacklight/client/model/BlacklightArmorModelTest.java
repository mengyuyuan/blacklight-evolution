package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.builders.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class BlacklightArmorModelTest {
    private static BlacklightPlayerModel body(BlacklightPose pose) throws Exception {
        var body=new BlacklightPlayerModel(LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE,false),64,64).bakeRoot(),false);
        var field=BlacklightPlayerModel.class.getDeclaredField("matrices");field.setAccessible(true);field.set(body,PlayerMotionController.skeleton().getPoseMatrices(pose));return body;
    }
    private static BlacklightArmorModel armor(BlacklightPlayerModel body) {
        return new BlacklightArmorModel(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1),0),64,32).bakeRoot(),body);
    }
    private static Capture render(HumanoidModel<?> model) {
        model.young=false; // Vanilla client players use the adult armour render group.
        var out=new Capture();model.renderToBuffer(new PoseStack(),out,15728880,0,0x3A/255F,0x72/255F,0xB9/255F,1);return out;
    }
    @Test void neutralArmourRetainsVanillaDimensionsWindingColourAndUvRange() throws Exception {
        var vanilla=new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(new CubeDeformation(1),0),64,32).bakeRoot());
        var before=render(vanilla);var after=render(armor(body(new BlacklightPose())));
        assertTrue(after.points.size()>before.points.size(),"Long armour faces need subdivisions to bend with elbows and knees");
        for(int axis=0;axis<3;axis++) {
            int a=axis;assertEquals(before.points.stream().mapToDouble(v->v.get(a)).min().orElseThrow(),after.points.stream().mapToDouble(v->v.get(a)).min().orElseThrow(),1e-5);
            assertEquals(before.points.stream().mapToDouble(v->v.get(a)).max().orElseThrow(),after.points.stream().mapToDouble(v->v.get(a)).max().orElseThrow(),1e-5);
        }
        assertTrue(after.uvValid);assertTrue(after.colourValid);assertTrue(after.normalValid);
    }
    @Test void bootFollowsTheBentKneeInsteadOfTheStraightVanillaThigh() throws Exception {
        var pose=new BlacklightPose();pose.orElseEmpty("Leg_R").rotation().rotateX(1.35F);
        var actual=render(armor(body(pose)));var rig=PlayerMotionController.skeleton();int leg=rig.searchJointByName("Leg_R").getId();
        var motion=rig.getPoseMatrices(pose);var bind=rig.getPoseMatrices(new BlacklightPose());
        var point=new Vector3f(-4.9F/16,25F/16,-3F/16);var expected=new Vector3f(-point.x,1.501F-point.y,point.z);
        new Matrix4f(motion[leg]).mul(new Matrix4f(bind[leg]).invert()).transformPosition(expected);expected.set(-expected.x,1.501F-expected.y,expected.z);
        assertTrue(expected.distance(point)>.15F,"Regression needs a visibly bent knee");
        assertTrue(actual.points.stream().anyMatch(p->p.distance(expected)<1e-5F),"Boot did not follow the lower leg skin transform");
    }
    @Test void biologicalArmSuppressesOnlyItsArmourAndDoesNotMutateEquipmentVisibility() throws Exception {
        var body=body(new BlacklightPose());var armor=armor(body);int normal=render(armor).points.size();
        body.rightArm.visible=false;int transformed=render(armor).points.size();assertTrue(transformed<normal);assertTrue(transformed>normal/2);
        assertTrue(armor.rightArm.visible,"Slot visibility belongs to the vanilla armour layer");
        body.rightArm.visible=true;assertEquals(normal,render(armor).points.size());
    }
    private static class Capture extends com.codex.blacklight.forge.ForgeVertexConsumer {
        final List<Vector3f> points=new ArrayList<>();boolean uvValid=true,colourValid=true,normalValid=true;
        public VertexConsumer addVertex(float x,float y,float z){assertTrue(Float.isFinite(x+y+z));points.add(new Vector3f(x,y,z));return this;}
        public VertexConsumer setColor(int r,int g,int b,int a){colourValid&=r==0x3A&&g==0x72&&b==0xB9&&a==255;return this;}
        public VertexConsumer setUv(float u,float v){uvValid&=Float.isFinite(u+v)&&u>=0&&u<=1&&v>=0&&v<=1;return this;}
        public VertexConsumer setUv1(int u,int v){return this;}public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){normalValid&=Float.isFinite(x+y+z)&&Math.abs(x*x+y*y+z*z-1)<1e-4;return this;}
    }
}
