package com.codex.blacklight.client.compat;

import com.codex.blacklight.client.model.MercerRig;
import com.google.gson.JsonArray;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts the live final pose to local Bedrock channels, without a second animation clock. */
public final class YsmPoseFrame {
    public record Channels(String name, Vector3f rotation, Vector3f position) { }
    private final String[] names;
    private final int[] parents;
    private final Vector3f[] pivots;
    private final MercerRig rig;

    public YsmPoseFrame(JsonArray bones) {
        rig = new MercerRig(bones);
        names = new String[bones.size()]; parents = new int[bones.size()]; pivots = new Vector3f[bones.size()];
        for (int i = 0; i < bones.size(); i++) {
            var b = bones.get(i).getAsJsonObject(); names[i] = b.get("name").getAsString();
            parents[i] = b.get("parent").isJsonNull() ? -1 : rig.index(b.get("parent").getAsString());
            var p = b.getAsJsonArray("pivot");
            pivots[i] = new Vector3f(p.get(0).getAsFloat(), p.get(1).getAsFloat(), p.get(2).getAsFloat());
        }
    }

    public MercerRig.Frame frame(Matrix4f[] source, boolean grip) { return rig.sample(source, grip, 0, 1.5F); }

    public Channels[] channels(MercerRig.Frame frame) {
        var globals = new Matrix4f[names.length]; var out = new Channels[names.length];
        for (int i = 0; i < names.length; i++) {
            globals[i] = new Matrix4f(frame.skin()[i]).translate(pivots[i]);
            var local = parents[i] < 0 ? new Matrix4f(globals[i])
                    : new Matrix4f(globals[parents[i]]).invert().mul(globals[i]);
            var rest = new Vector3f(pivots[i]);
            if (parents[i] >= 0) rest.sub(pivots[parents[i]]);
            // JOML 1.10.5's quaternion ZYX extraction has the wrong Y-squared
            // sign in its X denominator. Extract from an orthonormal matrix so
            // compound flight/combat rotations round-trip correctly.
            var rotation = new Matrix4f().rotation(local.getUnnormalizedRotation(new Quaternionf()).normalize());
            float horizontal = (float) Math.hypot(rotation.m00(), rotation.m01());
            var angles = new Vector3f(
                    (float) (horizontal > 1e-6F ? Math.atan2(rotation.m12(), rotation.m22()) : Math.atan2(-rotation.m21(), rotation.m11())),
                    (float) Math.atan2(-rotation.m02(), horizontal),
                    horizontal > 1e-6F ? (float) Math.atan2(rotation.m01(), rotation.m00()) : 0);
            angles.mul((float) (180 / Math.PI)); angles.x = -angles.x; angles.z = -angles.z;
            var position = local.getTranslation(new Vector3f()).sub(rest).mul(16);
            out[i] = new Channels(names[i], angles, position);
        }
        return out;
    }
}
