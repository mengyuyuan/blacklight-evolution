package com.codex.blacklight.client.audio;

import com.codex.blacklight.combat.HydraDeathAudioState;
import com.codex.blacklight.combat.HydraDeathAudioState.Cue;
import com.codex.blacklight.entity.BlacklightHydra;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;

/** Original event roles with project-authored Foley. No original waveforms.
 * StopExisting replaces only this actor's matching patch; polled debris stops
 * with its sink node. Finite unpolled voices may finish at their last position. */
public final class HydraDeathAudio {
    private static final int MAX_VOICES=24,RETRY_TICKS=20;
    private static final Map<BlacklightHydra,Entry> ACTORS=new IdentityHashMap<>();
    private static final Set<Voice> VOICES=Collections.newSetFromMap(new IdentityHashMap<>());
    private static Object world;
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(world!=mc.level){clear();world=mc.level;}
        for(var it=ACTORS.entrySet().iterator();it.hasNext();){var row=it.next();
            if(!tracked(row.getKey(),mc)){row.getValue().stopLoop();it.remove();}
        }
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        boolean audible=mc.options.getSoundSourceVolume(SoundSource.MASTER)>0&&mc.options.getSoundSourceVolume(SoundSource.HOSTILE)>0;
        for(var it=VOICES.iterator();it.hasNext();){var voice=it.next();
            // SoundEngine never ticks a rejected/missing sound. This grace
            // clock must be owned by the client observer, not by Voice.tick.
            voice.observations++;
            if(!audible||voice.isStopped()||voice.observations>=6&&!mc.getSoundManager().isActive(voice)
                    ||!voice.loopingVoice()&&voice.observations>180){
                voice.finish();it.remove();
            }
        }
        var actors=new ArrayList<BlacklightHydra>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlacklightHydra hydra&&tracked(hydra,mc))actors.add(hydra);
        var camera=mc.gameRenderer.getMainCamera().getPosition();
        actors.sort(Comparator.comparingDouble(h->h.position().distanceToSqr(camera)));
        for(var actor:actors){
            var entry=ACTORS.computeIfAbsent(actor,Entry::new);
            entry.tick(audible&&!actor.isSilent()&&actor.position().distanceToSqr(camera)<400*400);
        }
    }
    private static boolean tracked(BlacklightHydra actor,Minecraft mc){
        return mc.level!=null&&actor.level()==mc.level&&!actor.isRemoved()&&mc.level.getEntity(actor.getId())==actor;
    }
    private static int age(BlacklightHydra actor){return (int)actor.nativeDeathAge(0);}
    public static void clear(){for(var voice:VOICES)voice.finish();VOICES.clear();ACTORS.clear();world=null;}
    private static final class Entry {
        final BlacklightHydra actor;final HydraDeathAudioState state;
        final EnumMap<Cue,Voice> patches=new EnumMap<>(Cue.class);int retry;
        Entry(BlacklightHydra actor){
            this.actor=actor;
            // A newly tracked dead actor is a snapshot, not a new death event.
            state=new HydraDeathAudioState(actor.nativeDeathKind(),age(actor));
        }
        void tick(boolean audible){
            int kind=actor.nativeDeathKind(),age=age(actor);
            if(state.replaced(kind,age))for(var voice:patches.values())voice.finish();
            for(var event:state.observe(kind,age))if(audible)play(event.cue(),event.minimumPitch(),event.maximumPitch(),event.tick());
            if(!state.debris()||!audible||actor.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())>=48*48){stopLoop();return;}
            var loop=patches.get(Cue.DEBRIS);
            if(loop!=null&&!loop.isStopped()&&VOICES.contains(loop))return;
            if(retry>0){retry--;return;}
            play(Cue.DEBRIS,1,1,HydraDeathAudioState.DEBRIS_START);retry=RETRY_TICKS;
        }
        void play(Cue cue,float minimum,float maximum,int tick){
            // Native StopExisting on roar/whoosh, and one polled debris voice.
            var old=patches.remove(cue);if(old!=null){old.finish();VOICES.remove(old);}
            double range=cue==Cue.ROAR?400:cue==Cue.DEBRIS?48:24;
            if(actor.position().distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())>=range*range)return;
            if(VOICES.size()>=MAX_VOICES)return;
            long seed=actor.getUUID().getMostSignificantBits()^actor.getUUID().getLeastSignificantBits()^tick;
            var voice=new Voice(actor,cue,minimum+new Random(seed).nextFloat()*(maximum-minimum));
            patches.put(cue,voice);VOICES.add(voice);Minecraft.getInstance().getSoundManager().play(voice);
        }
        void stopLoop(){var voice=patches.remove(Cue.DEBRIS);if(voice!=null){voice.finish();VOICES.remove(voice);}retry=0;}
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        final BlacklightHydra actor;final Cue cue;final Object sourceWorld;int observations;
        Voice(BlacklightHydra actor,Cue cue,float pitch){
            super(SoundEvent.createVariableRangeEvent(new ResourceLocation("blacklight","hydra.death_"+cue.name().toLowerCase(Locale.ROOT))),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());
            this.actor=actor;this.cue=cue;sourceWorld=actor.level();this.pitch=pitch;
            looping=cue==Cue.DEBRIS;delay=0;relative=false;attenuation=SoundInstance.Attenuation.NONE;
            x=actor.getX();y=actor.getY();z=actor.getZ();update();
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !actor.isSilent();}
        boolean loopingVoice(){return looping;}
        private void update(){
            var mc=Minecraft.getInstance();
            if(tracked(actor,mc)){x=actor.getX();y=actor.getY();z=actor.getZ();}
            double distance=mc.gameRenderer.getMainCamera().getPosition().distanceTo(new net.minecraft.world.phys.Vec3(x,y,z));
            // Roar retains the FIG's100/400 distances. The two zero-distance
            // tracks defer to unported Patch attenuation;24/48 are MC mix caps.
            double near=cue==Cue.ROAR?100:0,far=cue==Cue.ROAR?400:cue==Cue.DEBRIS?48:24;
            volume=(float)((cue==Cue.DEBRIS?.25:.35)*Math.max(0,Math.min(1,(far-distance)/(far-near))));
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();if(mc.level!=sourceWorld||mc.player==null||actor.isSilent()){finish();return;}
            if(mc.isPaused())return;
            if(looping&&(!tracked(actor,mc)||!actor.nativeDying()||actor.nativeDeathKind()!=0||HydraDeathAudio.age(actor)<HydraDeathAudioState.DEBRIS_START
                    ||HydraDeathAudio.age(actor)>=com.codex.blacklight.combat.HydraDeath.TOTAL_TICKS)){
                finish();return;
            }
            update();
        }
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
    private HydraDeathAudio(){}
}
