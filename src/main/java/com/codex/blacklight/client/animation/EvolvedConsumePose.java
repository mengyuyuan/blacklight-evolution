package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.*;
import com.codex.blacklight.combat.EvolvedMotions;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import java.util.HashMap;
import org.joml.Matrix4f;

/** Use the actor's actual paired receiver pose for the evolved's MC-proportioned mesh.
 * Its unrelated P2 defeat clip must not keep moving underneath severed body pieces. */
public final class EvolvedConsumePose {
    private static final Matrix4f MODEL=new Matrix4f().translation(0,1.501F,0).scale(-1,-1,1);
    private static final Matrix4f INVERSE=new Matrix4f(MODEL).invert();
    private EvolvedConsumePose(){}
    public static Matrix4f[] skin(BlacklightInfectedHost host,float partial){
        var pose=NativeConsumePose.sample(host,com.codex.blacklight.registry.ModAttachments.getData(host, ModAttachments.CONSUME_VISUAL),"victim",partial);
        if(pose==null)return null;
        var rig=BlacklightSkeleton.get();var current=rig.getPoseMatrices(pose);var bind=rig.getPoseMatrices(new BlacklightPose());
        var mapped=new HashMap<String,Matrix4f>();
        for(var bone:rig.bones()){
            var skin=new Matrix4f(INVERSE).mul(current[bone.getId()]).mul(new Matrix4f(bind[bone.getId()]).invert()).mul(MODEL);
            skin.m30(skin.m30()*16).m31(skin.m31()*16).m32(skin.m32()*16);mapped.put(bone.getName(),skin);
        }
        var bones=EvolvedMotions.bones();var result=new Matrix4f[bones.size()];
        for(int i=0;i<bones.size();i++){
            var b=bones.get(i);String name=switch(b.name()){
                case "Motion_Root","Pelvis"->"Root";
                case "Spine_0","Spine_1"->"Torso";
                case "Spine_2","Spine_3"->"Chest";
                case "Neck","Head"->"Head";
                case "Clavicle_R"->"Shoulder_R";case "Clavicle_L"->"Shoulder_L";
                case "Shoulder_R"->"Arm_R";case "Shoulder_L"->"Arm_L";
                case "Elbow_R"->"Hand_R";case "Elbow_L"->"Hand_L";
                case "Wrist_R"->"Tool_R";case "Wrist_L"->"Tool_L";
                case "Hip_R"->"Thigh_R";case "Hip_L"->"Thigh_L";
                case "Knee_R","Ankle_R"->"Leg_R";case "Knee_L","Ankle_L"->"Leg_L";
                default->null;
            };
            result[i]=name!=null?mapped.get(name):b.parent()<0?mapped.get("Root"):result[b.parent()];
        }
        return result;
    }
}
