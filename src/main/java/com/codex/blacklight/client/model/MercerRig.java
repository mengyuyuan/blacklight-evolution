package com.codex.blacklight.client.model;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.animation.PlayerMotionController;
import com.google.gson.JsonArray;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Independent character rig. Existing Blacklight poses are sources, not this model's bind skeleton. */
public final class MercerRig {
    private record Joint(String name,int parent,int driver,Vector3f pivot) { }
    public record Frame(Matrix4f[] skin,Matrix4f[] attachments) { }
    private final List<Joint> joints=new ArrayList<>();
    private final Map<String,Integer> indices=new LinkedHashMap<>();
    private final Matrix4f[] sourceBind=PlayerMotionController.skeleton().getPoseMatrices(new BlacklightPose());
    private final Matrix4f[] sourceInverse=new Matrix4f[sourceBind.length];
    private static final Map<String,String> ATTACHMENTS=Map.ofEntries(
            Map.entry("Root","AllBody"),Map.entry("Torso","UpBody"),Map.entry("Chest","UpBody"),Map.entry("Head","Head"),
            Map.entry("Arm_R","RightArm"),Map.entry("Arm_L","LeftArm"),
            Map.entry("Hand_R","RightForeArm"),Map.entry("Hand_L","LeftForeArm"),
            Map.entry("Tool_R","RightHandLocator"),Map.entry("Tool_L","LeftHandLocator"),
            Map.entry("Thigh_R","RightLeg"),Map.entry("Thigh_L","LeftLeg"),
            Map.entry("Leg_R","RightLowerLeg"),Map.entry("Leg_L","LeftLowerLeg"),
            Map.entry("Knee_R","RightFoot"),Map.entry("Knee_L","LeftFoot"));
    public MercerRig(JsonArray bones) {
        for(int i=0;i<sourceBind.length;i++)sourceInverse[i]=new Matrix4f(sourceBind[i]).invert();
        var skeleton=PlayerMotionController.skeleton();
        for(var entry:bones) {
            var b=entry.getAsJsonObject();String name=b.get("name").getAsString();
            int parent=b.get("parent").isJsonNull()?-1:index(b.get("parent").getAsString());
            var driver=b.get("driver").isJsonNull()?null:skeleton.searchJointByName(b.get("driver").getAsString());
            var p=b.getAsJsonArray("pivot");
            indices.put(name,joints.size());joints.add(new Joint(name,parent,driver==null?-1:driver.getId(),
                    new Vector3f(p.get(0).getAsFloat(),p.get(1).getAsFloat(),p.get(2).getAsFloat())));
        }
    }
    public int index(String name) {
        Integer id=indices.get(name);if(id==null)throw new IllegalArgumentException("Unknown Mercer joint "+name);return id;
    }
    public Frame sample(Matrix4f[] source,boolean pinGrip,float armorWeight,float displayScale) {
        var sourceDelta=new Matrix4f[source.length];
        for(int i=0;i<source.length;i++)sourceDelta[i]=new Matrix4f(source[i]).mul(sourceInverse[i]);
        var skin=new Matrix4f[joints.size()];var rotations=new Matrix4f[joints.size()];
        var positions=new Vector3f[joints.size()];var effectiveDrivers=new int[joints.size()];
        for(int i=0;i<joints.size();i++) {
            var joint=joints.get(i);int parent=joint.parent;
            int driver=joint.driver>=0?joint.driver:parent>=0?effectiveDrivers[parent]:-1;
            effectiveDrivers[i]=driver;
            var rotation=driver>=0?new Matrix4f(sourceDelta[driver]).setTranslation(0,0,0):new Matrix4f();
            Vector3f position;
            if(parent<0)position=new Vector3f(joint.pivot);
            else {
                var offset=new Vector3f(joint.pivot).sub(joints.get(parent).pivot);
                position=rotations[parent].transformDirection(offset).add(positions[parent]);
                int parentDriver=effectiveDrivers[parent];
                if(driver>=0&&parentDriver<0)position.add(source[driver].getTranslation(new Vector3f()))
                        .sub(sourceBind[driver].getTranslation(new Vector3f()));
                else if(driver>=0&&driver!=parentDriver) {
                    // Preserve local translation/IK corrections while replacing only rest lengths.
                    var expected=sourceDelta[parentDriver].transformPosition(sourceBind[driver].getTranslation(new Vector3f()));
                    position.add(source[driver].getTranslation(new Vector3f())).sub(expected);
                }
            }
            if(pinGrip&&(joint.name.endsWith("ForeArm")||joint.name.endsWith("HandLocator")))
                position.set(source[driver].getTranslation(new Vector3f())).div(displayScale);
            // Armour has its own approved proportions. Its growth smoothly returns attachment
            // anchors to that rig while covered clothing is hidden by the existing armour masks.
            if(joint.driver>=0&&armorWeight>0)position.lerp(source[driver].getTranslation(new Vector3f()),armorWeight);
            rotations[i]=rotation;positions[i]=position;
            skin[i]=new Matrix4f(rotation).setTranslation(new Vector3f(position)
                    .sub(rotation.transformDirection(new Vector3f(joint.pivot))));
        }
        var attachments=new Matrix4f[source.length];
        for(int i=0;i<source.length;i++)attachments[i]=new Matrix4f(source[i]);
        var skeleton=PlayerMotionController.skeleton();
        ATTACHMENTS.forEach((nativeName,customName)->{
            var bone=skeleton.searchJointByName(nativeName);
            attachments[bone.getId()].setTranslation(positions[index(customName)]);
        });
        return new Frame(skin,attachments);
    }
}
