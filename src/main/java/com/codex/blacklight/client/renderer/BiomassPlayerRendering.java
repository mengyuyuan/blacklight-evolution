package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.BiomassSystem;
import com.codex.blacklight.power.BiomassState;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

public final class BiomassPlayerRendering {
    private BiomassPlayerRendering(){}
    public static void render(Player p,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var state=BiomassSystem.state(p);
        // During reformation all biomass is already on the host. Never draw a
        // second puddle underneath the emerging human, nor after returning HUMAN.
        if(!state.active()||state.phase()==BiomassState.REFORMING){BiomassCrawlContacts.clear(p);return;}
        double now=p.level().getGameTime()+partial,time=p.tickCount+partial;
        double move=Math.min(1,p.walkAnimation.speed(partial)*3),f=0;
        var victim=p.level().getEntity(state.victim());
        boolean absorbing=state.phase()==BiomassState.ABSORBING&&victim!=null;
        Vec3 delta=absorbing?victim.getPosition(partial).subtract(p.getPosition(partial)):Vec3.ZERO;
        if(absorbing)f=state.progress(now,BiomassState.ABSORB_TICKS);
        double transfer=Math.min(1,f/.68),remaining=1-transfer;
        float yaw=Mth.rotLerp(partial,p.yRotO,p.getYRot());
        var contacts=absorbing?null:BiomassCrawlContacts.update(p,partial,yaw,state.fatal());
        if(absorbing)BiomassCrawlContacts.clear(p);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(BiomassGeometry.TEXTURE));
        if(remaining>.005){
            stack.pushPose();
            // Move the source body into the host's feet as its volume becomes
            // the coating on the actual creature model (ConsumedMobVisuals).
            stack.translate(delta.x*transfer,delta.y*transfer,delta.z*transfer);
            stack.mulPose(Axis.YP.rotationDegrees(-yaw));
            float scale=(float)Math.sqrt(remaining);stack.scale(scale,(float)remaining,scale);
            BiomassGeometry.crawler(stack,out,light,now,absorbing?1:move,state.fatal(),contacts,p.getPosition(partial),yaw,1+Math.sin(transfer*Math.PI)*.35);
            stack.popPose();
        }
        if(absorbing&&f<.72){
            // Rounded leading lobes crawl up the victim, connected to the
            // coating front. Their source volume drains instead of duplicating.
            double front=Math.min(1,f/.68),width=victim.getBbWidth()*.48;
            for(int arm=0;arm<5;arm++){
                double angle=arm*Math.PI*2/5+Math.sin(time*.12+arm)*.12;
                double y=delta.y+victim.getBbHeight()*front;
                double size=(.07+.035*Math.sin(time*.2+arm))*(1-Math.max(0,(f-.5)/.22));
                double x=delta.x+Math.cos(angle)*width,z=delta.z+Math.sin(angle)*width;
                BiomassGeometry.ellipsoid(stack,out,light,new Vec3(x,y,z),size,size*1.4,size,time,0xffffffff);
            }
        }
    }
}
