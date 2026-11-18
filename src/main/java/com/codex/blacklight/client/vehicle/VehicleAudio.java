package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent.ClientTickEvent;

/** Entity-positioned loops. One pair per nearby vehicle, never one new sound per tick. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleAudio {
    private static final Map<MilitaryVehicle,Pair> LOOPS=new IdentityHashMap<>();
    private static Object scene;
    private record Pair(Loop engine,Loop rolling,long started) {
        void stop(){engine.finish();rolling.finish();}
    }
    @SubscribeEvent public static void tick(ClientTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();
        if(scene!=mc.level){LOOPS.values().forEach(Pair::stop);LOOPS.clear();scene=mc.level;}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        var candidates=new ArrayList<MilitaryVehicle>();
        for(var e:mc.level.entitiesForRendering())if(e instanceof MilitaryVehicle v&&!v.isRemoved()&&!v.isSilent()&&v.engineRunning()&&v.distanceToSqr(mc.player)<64*64)candidates.add(v);
        candidates.sort(Comparator.comparingDouble(v->v.distanceToSqr(mc.player)));
        var wanted=Collections.newSetFromMap(new IdentityHashMap<MilitaryVehicle,Boolean>());
        wanted.addAll(candidates.subList(0,Math.min(8,candidates.size())));
        var manager=mc.getSoundManager();long now=mc.level.getGameTime();
        for(var it=LOOPS.entrySet().iterator();it.hasNext();){var entry=it.next();var pair=entry.getValue();
            if(!wanted.contains(entry.getKey())){pair.engine.released=true;pair.rolling.released=true;}
            if(pair.engine.isStopped()&&pair.rolling.isStopped()||now-pair.started>40&&(!manager.isActive(pair.engine)||!manager.isActive(pair.rolling))){pair.stop();it.remove();}
        }
        for(var v:wanted)if(!LOOPS.containsKey(v)){
            var pair=new Pair(new Loop(v,false),new Loop(v,true),now);LOOPS.put(v,pair);manager.play(pair.engine);manager.play(pair.rolling);
        }
    }
    private static final class Loop extends AbstractTickableSoundInstance {
        final MilitaryVehicle vehicle;final boolean rolling;boolean released;
        Loop(MilitaryVehicle v,boolean rolling){
            super(VehicleSounds.event(rolling?v.chassisId().equals("truck")?"wheels":"tracks":v.chassisId()+"_engine"),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());
            vehicle=v;this.rolling=rolling;looping=true;delay=0;volume=0;position();
        }
        void position(){x=vehicle.getX();y=vehicle.getY()+vehicle.definition().height()*.4;z=vehicle.getZ();}
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !vehicle.isSilent();}
        @Override public void tick(){
            var mc=Minecraft.getInstance();
            if(vehicle.isRemoved()||vehicle.health()<=0||vehicle.level()!=mc.level||vehicle.isSilent()){finish();return;}
            position();boolean running=!released&&vehicle.engineRunning();
            var mix=VehicleAudioState.mix(running,vehicle.driveLoad(),vehicle.chassisId());
            volume=VehicleAudioState.approach(volume,rolling?mix.rolling():mix.engine());
            pitch=VehicleAudioState.approach(pitch,rolling?mix.rollingPitch():mix.enginePitch());
            if(!running&&volume<=.003F)finish();
        }
    }
    private VehicleAudio(){}
}
