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
            Vec3 normal=new Vec3(-s.x(),-s.y(),-s.z());
            Vec3 contact=player.position().add(s.hitX(),s.hitY(),s.hitZ()).add(normal.scale(s.shield()?.55:.18));
            int count=Math.min(28,12+(int)(s.strength()*10));
            for(int i=0;i<count;i++){
                var random=player.getRandom();
                Vec3 scatter=normal.scale(.14+.10*Math.min(1.5,s.strength()))
                    .add((random.nextDouble()-.5)*.32,(random.nextDouble()-.35)*.28,(random.nextDouble()-.5)*.32);
                var spark=mc.particleEngine.createParticle(ParticleTypes.ELECTRIC_SPARK,contact.x,contact.y,contact.z,scatter.x,scatter.y,scatter.z);
                if(spark!=null){
                    boolean white=i%3==0;spark.setColor(1,white?.95F:.66F,white?.78F:.13F);
                    spark.scale(white?.85F:1.25F);spark.setLifetime(8+random.nextInt(7));
                }
            }
        }
    }
}
