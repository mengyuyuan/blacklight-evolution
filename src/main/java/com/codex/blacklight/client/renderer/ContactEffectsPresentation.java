package com.codex.blacklight.client.renderer;

import com.codex.blacklight.combat.ContactEffectsQueue;
import com.codex.blacklight.network.ContactEffectsPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import java.util.*;

/** Releases all visual/audio layers together when the displayed world reaches the contact tick. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ContactEffectsPresentation {
    private static final ContactEffectsQueue<ContactEffectsPayload> QUEUE=new ContactEffectsQueue<>();
    private static ClientLevel world;
    public record Receipt(long serial,long tick,double shown,int kind,int surfaceCount,int soundCount){}
    private static final ArrayDeque<Receipt> RECEIPTS=new ArrayDeque<>();
    private ContactEffectsPresentation(){}
    public static List<Receipt> receipts(){return List.copyOf(RECEIPTS);}
    public static void accept(ContactEffectsPayload packet){
        var mc=Minecraft.getInstance();if(mc.level==null||mc.player==null)return;world(mc.level);
        var p=packet.flash();if(mc.player.position().distanceToSqr(p.x(),p.y(),p.z())>96*96)return;
        QUEUE.add(mc.level.getGameTime(),packet.tick(),packet.serial(),packet);
    }
    private static void world(ClientLevel next){if(next!=world){world=next;QUEUE.clear();RECEIPTS.clear();ContactTerrainDebris.clear();}}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel()==world)world(null);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void beforeEffects(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();world(mc.level);if(world==null||mc.player==null)return;
        double time=ContactEffectsQueue.renderTime(world.getGameTime(),e.getPartialTick());
        QUEUE.drain(time,p->FlyerContactPresentation.ready(p,time,e.getPartialTick()),p->show(p,time));
    }
    @SubscribeEvent public static void debris(RenderLevelStageEvent e){
        if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        ContactTerrainDebris.render(e.getPoseStack(),e.getCamera().getPosition(),ContactEffectsQueue.renderTime(mc.level.getGameTime(),e.getPartialTick()));
    }
    private static void show(ContactEffectsPayload p,double time){
        var mc=Minecraft.getInstance();var level=mc.level;if(level==null)return;
        while(RECEIPTS.size()>=128)RECEIPTS.removeFirst();RECEIPTS.addLast(new Receipt(p.serial(),p.tick(),time,p.kind(),p.terrain().size(),p.sounds().size()));
        if(p.kind()==ContactEffectsPayload.GROUND||p.kind()==ContactEffectsPayload.GUARD)CombatImpactRenderer.accept(p.flash());
        if(p.tissue()!=null){
            InfectedImpactVisuals.accept(p.tissue());
            if(level.getEntity(p.tissue().victim()) instanceof com.codex.blacklight.entity.EncounterMob mob&&mob.kind()==com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE){
                if(mc.player!=null&&com.codex.blacklight.registry.ModAttachments.getData(mc.player,com.codex.blacklight.registry.ModAttachments.WEAPON_VISUAL).spec()==null&&p.tissue().attacker()==mc.player.getId()&&mc.player.position().distanceToSqr(p.flash().x(),p.flash().y(),p.flash().z())<64)
                    com.codex.blacklight.client.ClientPhysicalFeedback.accept(new com.codex.blacklight.network.PhysicalFeedbackPayload(com.codex.blacklight.network.PhysicalFeedbackPayload.Effect.CLAW_HIT,.25F));
            }
        }
        if(p.kind()==ContactEffectsPayload.GROUND)for(var s:p.terrain()){
            var state=Block.stateById(s.state());var at=new Vec3(s.x(),s.y(),s.z());
            double speed=.12+s.ring()*.055;
            level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),at.x,at.y+.05,at.z,Math.cos(s.angle())*speed,.18+s.ring()*.05,Math.sin(s.angle())*speed);
            if((s.index()&3)==0)for(int i=0;i<2;i++)level.addParticle(com.codex.blacklight.registry.ModParticles.CONTACT_DUST.get(),at.x,at.y+.08,at.z,0,.025,0);
            if(s.ring()==1&&s.index()%(p.heavy()?2:4)==0){
                float size=p.heavy()?.44F:.27F;
                ContactTerrainDebris.spawn(state,at.add(0,size*.6+.05,0),new Vec3(Math.cos(s.angle())*.15,p.heavy()?.36:.25,Math.sin(s.angle())*.15),size,p.heavy()?16:12,time);
            }
        }
        if(p.kind()==ContactEffectsPayload.OBSTACLE)for(var s:p.terrain()){
            var state=Block.stateById(s.state());
            for(int i=0;i<14;i++)level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK,state),s.x(),s.y(),s.z(),Math.cos(s.angle())*.2+(level.random.nextDouble()-.5)*.08,(level.random.nextDouble()-.3)*.18,Math.sin(s.angle())*.2+(level.random.nextDouble()-.5)*.08);
        }

        for(var sound:p.sounds()){
            var event=BuiltInRegistries.SOUND_EVENT.get(sound.id());
            if(event!=null)level.playLocalSound(p.flash().x(),p.flash().y(),p.flash().z(),event,SoundSource.HOSTILE,sound.volume(),sound.pitch(),false);
        }
    }
}
