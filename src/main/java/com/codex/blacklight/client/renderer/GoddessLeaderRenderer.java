package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.GoddessAttacks;
import com.codex.blacklight.entity.EncounterMob;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Dorsal fibers lift on the same beat as Greene's signal and follow the living squad leader. */
final class GoddessLeaderRenderer {
    static void draw(EncounterMob boss,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var attack=boss.goddess.attacks;
        if(attack.move()!=GoddessAttacks.Move.CALL && attack.move()!=GoddessAttacks.Move.STRONG_CALL)return;
        var tag=boss.goddessData().getCompound("Offense");
        if(!tag.hasUUID("WaveLeader") || tag.getInt("LeaderSequence")!=attack.sequence())return;
        if(!(boss.level().getEntity(tag.getInt("LeaderId")) instanceof Mob leader) ||
                !leader.getUUID().equals(tag.getUUID("WaveLeader")))return;
        float yaw=Mth.rotLerp(partial,leader.yBodyRotO,leader.yBodyRot)*Mth.DEG_TO_RAD;
        var right=new Vec3(Math.cos(yaw),0,Math.sin(yaw));
        var back=new Vec3(Math.sin(yaw),0,-Math.cos(yaw));
        var torso=leader.getPosition(partial).subtract(boss.getPosition(partial))
                .add(0,leader.getBbHeight()*.7,0).add(back.scale(leader.getBbWidth()*.39));
        var out=buffers.getBuffer(RenderType.entityTranslucent(ConsumeTendrilSegmentMesh.TEXTURE));
        float beat=(float)Math.sin(attack.age(partial)*Math.PI/6);
        for(int lane=-1;lane<=1;lane++){
            var root=torso.add(right.scale(lane*.15));
            var crest=root.add(right.scale(lane*.32)).add(back.scale(.34)).add(0,1.3+beat*.18,0);
            var end=crest.add(right.scale(lane*.32)).add(back.scale(.40)).add(0,-.36+beat*.14,0);
            Vec3[] points=new Vec3[7];double[] widths=new double[7];
            for(int i=0;i<7;i++){
                double t=i/6.;points[i]=root.lerp(crest,t).lerp(crest.lerp(end,t),t);
                widths[i]=(.095+.055*Math.sin(Math.PI*t))*(1-.94*t);
            }
            ConsumeTendrilSegmentMesh.draw(stack,out,light,points,widths,right,lane==0?0xffba6157:0xff652634,false);
        }
    }
}
