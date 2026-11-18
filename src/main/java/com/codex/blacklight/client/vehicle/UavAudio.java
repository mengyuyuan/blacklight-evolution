package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.MilitaryUav;
import com.codex.blacklight.military.vehicle.VehicleSounds;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** P1 state roles: idle loop; seeing loop + entry servo; alert entry + idle loop.
 * Recognition timing is still the synchronized Minecraft detector state. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class UavAudio {
    private static final Map<MilitaryUav,Voice> VOICES=new IdentityHashMap<>();
    private static Object world;
    private static void clear(){VOICES.values().forEach(Voice::finish);VOICES.clear();world=null;}
    @SubscribeEvent public static void unload(net.neoforged.neoforge.event.level.LevelEvent.Unload event){if(event.getLevel()==world)clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();if(world!=mc.level){clear();world=mc.level;}
        for(var it=VOICES.entrySet().iterator();it.hasNext();){var e=it.next();var drone=e.getKey();if(mc.player==null||!drone.isAlive()||drone.isRemoved()||drone.isSilent()||drone.distanceToSqr(mc.player)>44*44){e.getValue().finish();it.remove();}}
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        for(var actor:mc.level.entitiesForRendering())if(actor instanceof MilitaryUav drone&&drone.isAlive()&&!drone.isRemoved()&&!drone.isSilent()&&drone.distanceToSqr(mc.player)<40*40&&!VOICES.containsKey(drone)&&VOICES.size()<4)VOICES.put(drone,new Voice(drone));
        VOICES.values().forEach(Voice::advance);
    }
    private static final class Voice {
        final MilitaryUav drone;final ArrayDeque<Cue> entries=new ArrayDeque<>();Cue loop;int state=-1;long loopStarted;
        Voice(MilitaryUav drone){this.drone=drone;}
        void advance(){
            var mc=Minecraft.getInstance();long now=drone.level().getGameTime();int next=drone.warningLamp();
            for(var it=entries.iterator();it.hasNext();){var cue=it.next();if(cue.isStopped()||now>=cue.until||now<cue.started){cue.finish();it.remove();}else cue.position();}
            String wanted=next==1?"uav_seeing":"uav_rotor";
            boolean audible=mc.options.getSoundSourceVolume(SoundSource.MASTER)>0&&mc.options.getSoundSourceVolume(SoundSource.HOSTILE)>0;
            if(loop==null||!loop.name.equals(wanted)||audible&&now-loopStarted>40&&!mc.getSoundManager().isActive(loop)){
                if(loop!=null)loop.finish();loop=new Cue(drone,wanted,true,0);loopStarted=now;mc.getSoundManager().play(loop);
            }
            loop.position();
            if(next!=state){
                // Non-polled original entry clips may finish after a branch change.
                // Stop them with the machine and bound rapid-reacquisition overlap.
                if(next==1)entry("uav_scan",11);else if(next==2)entry("uav_alert",50);
                state=next;
            }
        }
        void entry(String name,int ticks){while(entries.size()>=2)entries.removeFirst().finish();var cue=new Cue(drone,name,false,ticks);entries.addLast(cue);Minecraft.getInstance().getSoundManager().play(cue);}
        void finish(){if(loop!=null)loop.finish();entries.forEach(Cue::finish);entries.clear();}
    }
    private static final class Cue extends AbstractTickableSoundInstance {
        final MilitaryUav drone;final String name;final long started,until;
        Cue(MilitaryUav drone,String name,boolean repeat,int ticks){
            super(VehicleSounds.event(name),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());this.drone=drone;this.name=name;
            started=drone.level().getGameTime();until=repeat?Long.MAX_VALUE:started+ticks;looping=repeat;delay=0;volume=repeat?.45F:.65F;pitch=1;position();
        }
        void position(){x=drone.getX();y=drone.getY()+.6;z=drone.getZ();}
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !drone.isSilent();}
        @Override public void tick(){position();if(!drone.isAlive()||drone.isRemoved()||drone.isSilent()||drone.level()!=Minecraft.getInstance().level||drone.level().getGameTime()>=until)finish();}
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
    private UavAudio(){}
}
