package com.codex.blacklight.client.animation;

import java.util.IdentityHashMap;
import java.util.List;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerRecoilClothingTest {
    @Test void recoilMovesAndRestoresEveryOuterLayerOnBothSkinModels() {
        for (boolean slim : List.of(false, true)) {
            var root = LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, slim), 64, 64).bakeRoot();
            var model = new PlayerModel<>(root, slim);
            var clothing = List.of(model.jacket, model.leftSleeve, model.rightSleeve, model.leftPants, model.rightPants);
            var body = List.of(model.body, model.leftArm, model.rightArm, model.leftLeg, model.rightLeg);
            model.jacket.zRot = .12F;
            model.leftSleeve.visible = false;
            var initial = clothing.stream().map(PlayerRecoilClothingTest::transform).toList();
            var saved = new IdentityHashMap<ModelPart, PartPose>();
            ConsumeVictimPose.strikePose(model, saved, new FleshRecoil.Pose(.3F, -.2F, .1F, .2F, .5F, 1));
            for (int i = 0; i < clothing.size(); i++) {
                assertArrayEquals(transform(body.get(i)), transform(clothing.get(i)), 1e-5F);
            }
            assertFalse(model.leftSleeve.visible);
            saved.forEach(ModelPart::loadPose);
            for (int i = 0; i < clothing.size(); i++) {
                assertArrayEquals(initial.get(i), transform(clothing.get(i)), 1e-5F);
            }
        }
    }

    private static float[] transform(ModelPart part) {
        return new float[]{part.x, part.y, part.z, part.xRot, part.yRot, part.zRot};
    }
}
