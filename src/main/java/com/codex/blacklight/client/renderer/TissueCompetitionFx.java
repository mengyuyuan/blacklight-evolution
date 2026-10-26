package com.codex.blacklight.client.renderer;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.TissueCompetition;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/** A short attached bridge carries the collapsing surface into the actual consumer. */
final class TissueCompetitionFx {
    private TissueCompetitionFx(){}
    static boolean active(BlacklightInfectedHost host){
        return host.cannibalStarted()>=0&&host.cannibalTargetId()>=0&&host.isAlive()
                &&host.level().getEntity(host.cannibalTargetId()) instanceof BlacklightInfectedHost peer
                &&peer.isAlive()&&peer.cannibalTargetId()==host.getId()&&peer.cannibalStarted()==host.cannibalStarted();
    }
    static float collapse(BlacklightInfectedHost host,float partial){
        float elapsed=host.level().getGameTime()-host.cannibalStarted()+partial;
        float t=Math.clamp((elapsed-TissueCompetition.COVER_TICKS)/(host.cannibalDuration()-TissueCompetition.COVER_TICKS),0,1);
        return t*t*(3-2*t);
    }
    static void apply(BlacklightInfectedHost host,float partial,PoseStack stack){
        if(!host.cannibalPrey()||!active(host))return;
        var peer=(BlacklightInfectedHost)host.level().getEntity(host.cannibalTargetId());
        float t=collapse(host,partial);float scale=1-.92F*t;
        Vec3 delta=peer.getPosition(partial).add(0,peer.getBbHeight()*.45,0).subtract(host.getPosition(partial).add(0,host.getBbHeight()*.5,0));
        stack.translate(delta.x*t,host.getBbHeight()*.5+delta.y*t,delta.z*t);
        stack.scale(scale,scale,scale);stack.translate(0,-host.getBbHeight()*.5,0);
    }
    static void render(BlacklightInfectedHost host,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        if(host.cannibalPrey()||!active(host)||host.isInvisible())return;
        var prey=(BlacklightInfectedHost)host.level().getEntity(host.cannibalTargetId());
        Vec3 base=host.getPosition(partial),start=new Vec3(0,host.getBbHeight()*.5,0);
        Vec3 end=prey.getPosition(partial).subtract(base).add(0,prey.getBbHeight()*.5,0);
        float age=host.level().getGameTime()-host.cannibalStarted()+partial;
        float cover=Math.clamp(age/TissueCompetition.COVER_TICKS,0,1),flow=collapse(prey,partial);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        for(int strand=0;strand<3;strand++){
            Vec3 previous=start;double phase=strand*Math.PI*2/3;
            for(int i=1;i<=12;i++){
                double t=i/12D;double wrap=Math.sin(t*Math.PI)*.16*cover;
                Vec3 next=start.lerp(end,t).add(Math.cos(phase+t*5+age*.04)*wrap,Math.sin(phase+t*5+age*.04)*wrap,Math.sin(phase+t*5)*wrap);
                ConsumedMobVisuals.tube(stack,out,light,previous,next,(.035+.035*Math.sin(t*Math.PI))*cover*(1-.4*flow),0xff25171e);
                previous=next;
            }
        }
    }
}
