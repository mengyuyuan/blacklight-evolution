package com.codex.blacklight.animation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

/** Own hierarchical pose evaluator; calibration data retains the existing motion coordinate system. */
public final class BlacklightSkeleton {
    public static final class Bone {
        private final String name;
        private final int id;
        private final Bone parent;
        private final Matrix4f local, inverseLocal;
        private final List<Bone> children = new ArrayList<>();
        private Bone(String name, int id, Bone parent, Matrix4f local) {
            this.name = name; this.id = id; this.parent = parent; this.local = new Matrix4f(local); this.inverseLocal = new Matrix4f(local).invert();
        }
        public String getName() { return name; }
        public int getId() { return id; }
        public Matrix4f getLocalTransform() { return new Matrix4f(local); }
        public Bone parent() { return parent; }
        public List<Bone> getSubJoints() { return List.copyOf(children); }
    }
    private final Map<String, Bone> bones = new LinkedHashMap<>();
    public final Bone rootJoint, head, torso, chest, armR, armL, handR, handL, thighR, thighL, legR, legL;
    private BlacklightSkeleton(JsonObject json) {
        var indices = new LinkedHashMap<String, Integer>();
        for (var item : json.getAsJsonArray("joints")) indices.put(item.getAsString(), indices.size());
        rootJoint = read(json.getAsJsonArray("hierarchy").get(0).getAsJsonObject(), null, indices);
        head = searchJointByName("Head"); torso = searchJointByName("Torso"); chest = searchJointByName("Chest");
        armR = searchJointByName("Arm_R"); armL = searchJointByName("Arm_L");
        handR = searchJointByName("Hand_R"); handL = searchJointByName("Hand_L");
        thighR = searchJointByName("Thigh_R"); thighL = searchJointByName("Thigh_L");
        legR = searchJointByName("Leg_R"); legL = searchJointByName("Leg_L");
    }
    private Bone read(JsonObject json, Bone parent, Map<String,Integer> indices) {
        String name = json.get("name").getAsString();
        float[] data = new float[16]; var values = json.getAsJsonArray("transform");
        for (int i = 0; i < data.length; i++) data[i] = values.get(i).getAsFloat();
        var local = new Matrix4f().set(data).transpose();
        if (parent == null) local = new Matrix4f().rotateX(-(float)Math.PI / 2).mul(local);
        var bone = new Bone(name, indices.get(name), parent, local);
        bones.put(name, bone);
        for (var child : json.getAsJsonArray("children")) bone.children.add(read(child.getAsJsonObject(), bone, indices));
        return bone;
    }
    private static final class Holder {
        static final BlacklightSkeleton INSTANCE = load();
        private static BlacklightSkeleton load() {
            try (var stream = BlacklightSkeleton.class.getResourceAsStream("/assets/blacklight/animation/player_bind.json")) {
                if (stream == null) throw new IllegalStateException("Missing Blacklight bind calibration");
                return new BlacklightSkeleton(JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (java.io.IOException e) { throw new IllegalStateException("Cannot load Blacklight skeleton", e); }
        }
    }
    public static BlacklightSkeleton get() { return Holder.INSTANCE; }
    public Bone searchJointByName(String name) { return bones.get(name); }
    public Bone rootJoint() { return rootJoint; }
    public List<Bone> bones() { return List.copyOf(bones.values()); }
    public int size() { return bones.size(); }
    public Matrix4f getBoundTransformFor(BlacklightPose pose, Bone bone) {
        if (bone == null) throw new IllegalArgumentException("Missing bone");
        var parent = bone.parent == null ? new Matrix4f() : getBoundTransformFor(pose, bone.parent);
        var delta = pose.get(bone.name);
        return parent.mul(bone.local).mul(delta == null ? new Matrix4f() : delta.toMatrix());
    }
    public Matrix4f[] getPoseMatrices(BlacklightPose pose) {
        var result = new Matrix4f[size()];
        // read() inserts parents before children; evaluate each chain link exactly once.
        var scratch = new Matrix4f();
        for (var bone : bones.values()) {
            var matrix = bone.parent == null ? new Matrix4f() : new Matrix4f(result[bone.parent.id]);
            matrix.mul(bone.local);
            var delta = pose.get(bone.name);
            if (delta != null) matrix.mul(scratch.translationRotateScale(delta.translation(), delta.rotation(), delta.scale()));
            result[bone.id] = matrix;
        }
        return result;
    }
    public BlacklightPose localPose(MatrixMotionClip clip, float seconds, boolean loop) {
        var pose = new BlacklightPose();
        for (String name : clip.joints()) {
            if (name.equals("Coord")) continue;
            var bone = searchJointByName(name); if (bone == null) continue;
            var authored = clip.sample(name, seconds, loop);
            if (bone.parent == null) authored = new Matrix4f().rotateX(-(float)Math.PI / 2).mul(authored);
            var delta = new Matrix4f(bone.inverseLocal).mul(authored);
            pose.getJointTransformData().put(name, BoneTransform.fromMatrix(delta));
        }
        return pose;
    }
}
