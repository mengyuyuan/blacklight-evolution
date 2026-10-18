package com.codex.blacklight.client.renderer;
import com.codex.blacklight.power.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
/** Visual motion follows actual displacement; it never writes player rotation or input. */
public final class BiomassPlayerRendering {
    private static final java.util.Map<Player,BiomassFluidSurface> FLOWS=new java.util.WeakHashMap<>();
    private BiomassPlayerRendering(){}
    public static void render(Player p,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var state=BiomassSystem.state(p);
        if(!state.active()||state.consumed()){FLOWS.remove(p);return;}
        double now=p.level().getGameTime()+partial,time=p.tickCount+partial;
        float size=BiomassEvolution.scale(p);
        var flow=FLOWS.computeIfAbsent(p,k->new BiomassFluidSurface());
        flow.update(p.getPosition(partial),time,size);
        var victim=p.level().getEntity(state.victim());
        boolean absorbing=state.phase()==BiomassState.ABSORBING&&victim!=null;
        double f=absorbing?state.progress(now,BiomassState.ABSORB_TICKS):0;
        Vec3 delta=absorbing?victim.getPosition(partial).subtract(p.getPosition(partial)):Vec3.ZERO;
        double transfer=Math.min(1,f/.68),remaining=1-transfer;
        if(state.phase()==BiomassState.SHATTERING)remaining=Math.max(0,(state.progress(now,BiomassState.SHATTER_TICKS)-.55)/.45);
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        if(remaining>.005){
            stack.pushPose();stack.translate(delta.x*transfer,delta.y*transfer,delta.z*transfer);
            float scale=size*(float)Math.sqrt(remaining);stack.scale(scale,size*(float)remaining,scale);
            flow.render(stack,out,light,time);
            stack.popPose();
        }
        if(absorbing&&f>.02&&f<.72){
            // Ground transfer ends at the prey; the posed surface renderer handles all climbing.
            Vec3 source=delta.scale(transfer),path=delta.subtract(source);
            int cells=Math.max(1,Math.min(40,(int)Math.ceil(path.length()/.08)));
            for(int i=0;i<=cells;i++){var at=source.add(path.scale(i/(double)cells));double r=.075*size*(1-transfer*.6);
                BiomassGeometry.box(stack,out,light,at.x-r,at.y+.018,at.z-r,at.x+r,at.y+.085,at.z+r,0xff29262b);
            }
        }else if(state.phase()==BiomassState.CRAWLING){
            // A small surface lobe points at nearby edible prey before contact.
            var candidates=p.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class,p.getBoundingBox().inflate(2),v->!(v instanceof com.codex.blacklight.entity.BiomassHusk)&&com.codex.blacklight.combat.ConsumeSystem.eligibleBiomass(v)&&com.codex.blacklight.combat.CombatTargeting.damageable(p,v)&&p.hasLineOfSight(v));
            var prey=candidates.stream().min(java.util.Comparator.comparingDouble(p::distanceToSqr)).orElse(null);
            if(prey!=null){var direction=prey.position().subtract(p.position()).multiply(1,0,1).normalize();
                for(int i=0;i<4;i++){double reach=size*(.22+i*.06),r=size*(.075-i*.012);var at=direction.scale(reach);
                    BiomassGeometry.box(stack,out,light,at.x-r,.02,at.z-r,at.x+r,.05+.015*Math.sin(time*.2-i),at.z+r,0xff4a2931);}
            }
        }
    }
}
