package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.ConsumePlacementTrack;
import com.codex.blacklight.mixin.client.ModelPartChildrenAccessor;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.Map;

/** Adapts the receiver's joint rotations to species sockets; never copies human limb positions. */
public final class ReceiverModelPose {
    private ReceiverModelPose() {}

    public static ModelPart root(EntityModel<?> model) {
        if (model instanceof HierarchicalModel<?> hierarchy) return hierarchy.root();
        if(model instanceof VictimModelRoot receiver)return receiver.blacklight$victimRoot();
        // Preserve the established vanilla humanoid/ageable paths. Foreign
        // EntityModels often retain only private root/limb fields.
        if(model instanceof net.minecraft.client.model.HumanoidModel<?>
                ||model instanceof com.codex.blacklight.mixin.client.AgeableModelAccessor)return null;
        return NativeModelRoots.root(model);
    }
    public static void reloadRoots(){NativeModelRoots.clear();}

    public static int apply(EntityModel<?> model, Map<ModelPart, PartPose> saved,
            double[] frame, boolean rigid, boolean mounted) {
        ModelPart root = root(model);
        if (root == null) return 0;
        return apply(root, "root", new Quaternionf(), new Quaternionf(), saved, frame, rigid, mounted);
    }

    private static int apply(ModelPart part, String name, Quaternionf parentBind, Quaternionf parentDelta,
            Map<ModelPart, PartPose> saved, double[] frame, boolean rigid, boolean mounted) {
        PartPose bind = part.getInitialPose();
        Quaternionf bindWorld = new Quaternionf(parentBind).mul(new Quaternionf().rotationZYX(bind.zRot, bind.yRot, bind.xRot));
        int joint = joint(name);
        Quaternionf delta = new Quaternionf(parentDelta);
        int count = 0;
        if (joint >= 0) {
            delta = ConsumePlacementTrack.quaternion(frame, 7 + joint * 4);
            // Whole-body rendering already carries the chest rotation for rigid receivers.
            if (rigid) delta = ConsumePlacementTrack.quaternion(frame, 11).invert().mul(delta);
            float weight = mounted ? (joint == 4 || joint == 5 || joint == 9 || joint == 10 ? 0 : .6F) : 1;
            delta = new Quaternionf().slerp(delta, weight);
            Quaternionf parentWorld = new Quaternionf(parentDelta).mul(parentBind);
            Quaternionf local = parentWorld.invert().mul(delta).mul(bindWorld);
            Vector3f angles = local.getEulerAnglesZYX(new Vector3f());
            saved.putIfAbsent(part, part.storePose());
            part.xRot = angles.x; part.yRot = angles.y; part.zRot = angles.z;
            // Preserve sockets, young-model proportions and species-specific bind orientation.
            part.x = bind.x; part.y = bind.y; part.z = bind.z;
            count++;
        }
        for (var child : ((ModelPartChildrenAccessor)(Object)part).blacklight$children().entrySet())
            count += apply(child.getValue(), child.getKey(), bindWorld, delta, saved, frame, rigid, mounted);
        return count;
    }

    private static int joint(String name) {
        String n = name.toLowerCase(java.util.Locale.ROOT);
        if (n.equals("head") || n.equals("head_parts") || n.equals("beak") || n.equals("red_thing")
                || n.equals("nose") || n.endsWith("_ear")) return 0;
        if (n.equals("body") || n.equals("upper_body") || n.equals("chest") || n.equals("ribcage")) return 1;
        if (n.equals("arms")) return 2;
        boolean left = n.contains("left"), right = n.contains("right");
        if (left || right) {
            if (n.contains("wing") || n.contains("arm") || n.contains("front")) return left ? 3 : 2;
            if (n.contains("leg") || n.contains("hind") || n.contains("foot") || n.contains("haunch")) return left ? 5 : 4;
        }
        if (n.startsWith("tentacle") || n.matches("leg[0-9]+")) {
            int i = Character.digit(n.charAt(n.length()-1), 10);
            return i < 0 ? 2 : 2 + i % 4;
        }
        if (n.contains("tail") || n.startsWith("body")) return 6;
        return -1;
    }
}
