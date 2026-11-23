package com.codex.blacklight.client.animation;

import com.codex.blacklight.animation.BlacklightPose;
import com.codex.blacklight.combat.GoddessStruggle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Feet search for the palm, or fold over the hair loop while the upper body swings free. */
public final class GoddessFatalActor {
    private static float ramp(float t,float n){float x=Mth.clamp(t/n,0,1);return x*x*(3-2*x);}
    private static void turn(BlacklightPose p,String joint,float x,float y,float z){p.orElseEmpty(joint).rotation().rotationXYZ(x,y,z);}
    public static BlacklightPose pose(GoddessStruggle q,float partial){
        var p=new BlacklightPose();
        var step=q.step();float t=q.age(partial);
        float lift=step==GoddessStruggle.Step.LIFT?ramp(t,15):1;
        float escape=step==GoddessStruggle.Step.ESCAPE?ramp(t,17):0;
        float slip=step==GoddessStruggle.Step.SLIP?ramp(t,19):0;
        float tension=lift*(1-escape);
        if(q.hair()){
            float hand=step==GoddessStruggle.Step.RIGHT || step==GoddessStruggle.Step.REPRISE && q.remainingKnot()==GoddessStruggle.RIGHT?1:-1;
            float reach=q.prompt()?ramp(t,8)*(1-.45F*ramp(t-16,7)):0;
            float swing=(float)Math.sin(q.swing(partial))*.24F*tension;
            turn(p,"Root",.22F*tension,0,(float)Math.PI*tension+swing);
            p.orElseEmpty("Root").translation().set(0,1.55F*tension,0);
            turn(p,"Torso",-.32F*tension,hand*.38F*reach,swing*.6F);
            turn(p,"Head",.25F*tension,-hand*.27F*reach,0);
            turn(p,"Thigh_L",.55F*tension,0,-.16F*tension);turn(p,"Leg_L",-1.05F*tension,0,0);
            turn(p,"Thigh_R",.22F*tension,0,.17F*tension);turn(p,"Leg_R",-.6F*tension,0,0);
            for(int side:new int[]{-1,1}){
                String suffix=side<0?"_L":"_R";float catchKnot=side==hand?reach:0;
                turn(p,"Arm"+suffix,(.32F+1.2F*catchKnot+.5F*slip)*tension,-side*.3F*catchKnot,side*(.72F-.55F*catchKnot)*tension);
                turn(p,"Hand"+suffix,(.42F+.7F*catchKnot)*tension,0,0);
            }
        }else{
            float pry=step==GoddessStruggle.Step.PUSH?(float)q.effort(partial):step==GoddessStruggle.Step.JUMP?1:0;
            float kick=(step==GoddessStruggle.Step.ESCAPE?(float)Math.sin(Math.PI*escape):0);
            turn(p,"Root",-.30F*tension+.9F*kick,.22F*tension,.28F*tension);
            turn(p,"Torso",-.36F*tension-.32F*pry+.75F*slip,.24F*pry,0);
            turn(p,"Head",.34F*pry-.47F*slip,0,0);
            for(int side:new int[]{-1,1}){
                String suffix=side<0?"_L":"_R";float offset=(side<0?.16F:-.08F)*tension;
                turn(p,"Arm"+suffix,1.18F*tension+.65F*pry-.74F*slip,side*.24F*tension,side*(.38F*tension+.33F*pry));
                turn(p,"Hand"+suffix,.9F*tension-.64F*pry+.5F*slip,0,0);
                turn(p,"Thigh"+suffix,.93F*tension+offset-.85F*kick-.58F*slip,0,side*.23F*tension);
                turn(p,"Leg"+suffix,-1.41F*tension+offset+1.30F*kick+.65F*slip,0,0);
            }
        }
        return p;
    }
    public static void hand(PoseStack stack,HumanoidArm arm,GoddessStruggle q,float partial){
        int side=arm==HumanoidArm.RIGHT?1:-1;
        float t=q.age(partial);
        boolean extend= q.hair() && q.prompt() && (side<0)==(q.step()==GoddessStruggle.Step.LEFT || q.step()==GoddessStruggle.Step.REPRISE && q.remainingKnot()==GoddessStruggle.LEFT);
        float reach=q.hair()?(extend?ramp(t,8):.15F):q.step()==GoddessStruggle.Step.JUMP?1:(float)q.effort(partial);
        float collapse=q.step()==GoddessStruggle.Step.SLIP?ramp(t,17):0;
        stack.translate(-side*(.09F+.10F*reach),.12F+.20F*reach-.16F*collapse,-.22F-.21F*reach);
        stack.mulPose(Axis.ZP.rotationDegrees(side*(14-32*reach)));stack.mulPose(Axis.XP.rotationDegrees(-9-12*reach+20*collapse));
    }
    private GoddessFatalActor(){}
}
