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
    private static final java.util.Map<Player,Flow> FLOWS=new java.util.WeakHashMap<>();
    private static final class Flow {double stamp,speed;float heading;Flow(double t,float h){stamp=t;heading=h;}}
    private BiomassPlayerRendering(){}
    public static void render(Player p,float partial,PoseStack stack,MultiBufferSource buffers,int light){
        var state=BiomassSystem.state(p);
        if(!state.active()||state.phase()==BiomassState.REFORMING){FLOWS.remove(p);return;}
        double now=p.level().getGameTime()+partial,time=p.tickCount+partial;
        var flow=FLOWS.computeIfAbsent(p,k->new Flow(time,p.getYRot()));
        double dt=Math.max(0,Math.min(2,time-flow.stamp));flow.stamp=time;
        double dx=p.getX()-p.xo,dz=p.getZ()-p.zo,velocity=Math.sqrt(dx*dx+dz*dz);
        double desired=velocity>1?0:Math.min(1,velocity*7);
        flow.speed+=(desired-flow.speed)*(1-Math.exp(-dt*.35));
        if(velocity>.002&&velocity<1)flow.heading+=Mth.wrapDegrees((float)Math.toDegrees(Math.atan2(-dx,dz))-flow.heading)*(float)(1-Math.exp(-dt*.24));
        var victim=p.level().getEntity(state.victim());
        boolean absorbing=state.phase()==BiomassState.ABSORBING&&victim!=null;
        double f=absorbing?state.progress(now,BiomassState.ABSORB_TICKS):0;
        Vec3 delta=absorbing?victim.getPosition(partial).subtract(p.getPosition(partial)):Vec3.ZERO;
        double transfer=Math.min(1,f/.68),remaining=1-transfer;
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(ConsumedMobVisuals.WHITE));
        float size=BiomassEvolution.scale(p);
        if(remaining>.005){
            stack.pushPose();stack.translate(delta.x*transfer,delta.y*transfer,delta.z*transfer);
            stack.mulPose(Axis.YP.rotationDegrees(-flow.heading));
            float scale=size*(float)Math.sqrt(remaining);stack.scale(scale,size*(float)remaining,scale);
            BiomassGeometry.crawler(stack,out,light,now,absorbing?1:flow.speed,state.fatal(),null,p.getPosition(partial),flow.heading,1+Math.sin(transfer*Math.PI)*.3);
            stack.popPose();
        }
        if(absorbing&&f>.02&&f<.72){
            // Connected pixel ribbons reach the host's feet, then climb its real silhouette.
            // The existing coat renderer takes over behind this leading edge.
            double front=Math.min(1,f/.68),width=victim.getBbWidth()*.48;
            Vec3 source=delta.scale(transfer),path=delta.subtract(source);
            int cells=Math.max(1,Math.min(40,(int)Math.ceil(path.length()/.08)));
            for(int i=0;i<=cells;i++){var at=source.add(path.scale(i/(double)cells));double r=.075*size*(1-transfer*.6);
                BiomassGeometry.box(stack,out,light,at.x-r,at.y+.018,at.z-r,at.x+r,at.y+.085,at.z+r,0xff29262b);
            }
            for(int arm=0;arm<5;arm++){
                double angle=arm*Math.PI*2/5;
                int steps=Math.max(2,(int)Math.ceil(victim.getBbHeight()*front/.09));
                for(int i=0;i<=steps;i++){
                    double y=victim.getBbHeight()*front*i/steps;
                    double curl=.025*Math.sin(time*.15-i*.6+arm),r=.065+(1-i/(double)steps)*.025;
                    double x=delta.x+Math.cos(angle)*(width+curl),z=delta.z+Math.sin(angle)*(width+curl);
                    BiomassGeometry.box(stack,out,light,x-r,delta.y+y,z-r,x+r,delta.y+y+.10,z+r,i%5==0?0xff63303a:0xff29262b);
                }
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
