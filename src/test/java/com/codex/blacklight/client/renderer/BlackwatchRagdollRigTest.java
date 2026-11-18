package com.codex.blacklight.client.renderer;

import com.codex.blacklight.client.model.BlackwatchModel;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.client.model.geom.ModelPart;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BlackwatchRagdollRigTest {
    @Test void equipmentUsesItsRealLimbAndLeavesRoomForSeveredFragments() {
        for (boolean military : new boolean[]{false, true}) for (boolean heavy : new boolean[]{false, true}) {
            var root = BlackwatchModel.layer(heavy, military).bakeRoot();
            var model = new BlackwatchModel(root, military);
            var owners = model.ragdollOwners();
            var rigid = Collections.newSetFromMap(new IdentityHashMap<ModelPart, Boolean>());
            int meshParts = 0;
            for (var part : root.getAllParts().toList()) if (!part.isEmpty()) {
                meshParts++;
                var owner = owners.get(part);
                assertNotNull(owner, "Every clothing, equipment and weapon mesh keeps an anatomical owner");
                rigid.add(owner);
            }
            assertTrue(meshParts > AdaptiveRagdoll.MAX_BASE_PARTS, "The production layered mesh exercises the old per-piece limit");
            assertEquals(13, rigid.size(), "Head, torso, ten limb segments and the carried weapon form the authored rig");
            assertTrue(rigid.size() * 2 < AdaptiveRagdoll.MAX_PARTS, "Even a cut crossing every anatomical body has fragment capacity");
            var arm = root.getChild("right_arm"); var forearm = arm.getChild("forearm");
            assertSame(arm, owners.get(arm.getChild("uniform_sleeve")));
            assertSame(forearm, owners.get(forearm.getChild("uniform_sleeve")));
            assertNotSame(owners.get(arm), owners.get(forearm), "Elbow articulation is not merged with its clothing");
            var shin = root.getChild("right_leg").getChild("shin"); var foot = shin.getChild("foot");
            assertSame(shin, owners.get(shin.getChild("uniform_trouser")));
            assertSame(foot, owners.get(foot.getChild("uniform_boot")));
            assertNotSame(owners.get(shin), owners.get(foot), "Ankle articulation survives");
            var carry = root.getChild("body").getChild("carry");
            carry.getAllParts().forEach(part -> assertSame(carry, owners.get(part), "One gun is not a chain of loose physics joints"));
        }
    }
    @Test void unknownDetailedRigsRetainTheOriginalBoneBudgetAndGainBoundedCutCapacity() {
        assertEquals(48, AdaptiveRagdoll.MAX_BASE_PARTS, "Do not erase extra bones from unrelated models");
        assertEquals(16, AdaptiveRagdoll.MAX_PARTS - AdaptiveRagdoll.MAX_BASE_PARTS, "An intact full-budget rig can still be cut");
    }
}
