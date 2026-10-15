package com.codex.blacklight.client.renderer;

import com.codex.blacklight.power.DefenseImpact;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** One bounded bright spark burst for each authoritative contact, outside the rendered plate. */
@Mod.EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class DefenseImpactEffects {
    private static final Map<AbstractClientPlayer,Long> seen=new WeakHashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel level;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.level!=level){seen.clear();level=mc.level;}
        if(level==null)return;
        for(var player:level.players()){
            DefenseImpact s=ModAttachments.getData(player,ModAttachments.DEFENSE_IMPACT);
            Long before=seen.get(player);if(before!=null&&before==s.at())continue;
            long age=level.getGameTime()-s.at();if(age<0)continue;seen.put(player,s.at());
            if(s.strength()<=0||age>6)continue;
            Vec3 incoming=new Vec3(s.x(),s.y(),s.z()).normalize();
            Vec3 normal=s.shield()?com.codex.blacklight.power.UtilityPowerSystem.shieldForward(player)
                :new Vec3(s.hitX(),s.hitY()-player.getBbHeight()*.5,s.hitZ()).normalize();
            if(normal.lengthSqr()<.1||incoming.dot(normal)>-.08)normal=incoming.scale(-1);
            Vec3 outgoing=incoming.subtract(normal.scale(2*incoming.dot(normal))).normalize();
            Vec3 side=outgoing.cross(new Vec3(0,1,0));
            if(side.lengthSqr()<.01)side=new Vec3(1,0,0);else side=side.normalize();
            Vec3 up=side.cross(outgoing).normalize();
            Vec3 contact=player.position().add(s.hitX(),s.hitY(),s.hitZ()).add(normal.scale(s.shield()?.45:.12));
            float force=Math.min(1.5F,s.strength());
            int count=4+(int)(force*4); // 4-10 small sparks, instead of an overlapping cloud.
            for(int i=0;i<count;i++){
                var random=player.getRandom();
                double spread=.035+.035*force;
                Vec3 scatter=outgoing.scale(.10+.14*force+random.nextDouble()*.10)
                    .add(side.scale((random.nextDouble()-.5)*spread*2))
                    .add(up.scale((random.nextDouble()-.5)*spread));
                var spark=mc.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK,contact.x,contact.y,contact.z,scatter.x,scatter.y,scatter.z);
                if(spark!=null){
                    boolean white=i%3==0;spark.setColor(1,white?.95F:.65F,white?.82F:.12F);
                    spark.scale(.22F+force*.14F);spark.setLifetime(3+random.nextInt(3)+(int)force);
                }
            }
            // A tiny two-tick white contact flash makes the impact readable without hiding the plate.
            var flash=mc.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK,contact.x,contact.y,contact.z,0,0,0);
            if(flash!=null){flash.setColor(1,.97F,.86F);flash.scale(.35F+force*.12F);flash.setLifetime(2);}
        }
    }
}
