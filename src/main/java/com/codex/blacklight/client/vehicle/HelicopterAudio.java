package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.aircraft.HelicopterAudioState;
import com.codex.blacklight.military.vehicle.VehicleSounds;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Source Blackhawk patch layers and curves, played through project-authored Foley. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class HelicopterAudio {
    private static final Map<BlackwatchHelicopter,Group> GROUPS=new IdentityHashMap<>();
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var mc=Minecraft.getInstance();
        for(var it=GROUPS.entrySet().iterator();it.hasNext();){var entry=it.next();var craft=entry.getKey();
            if(mc.level!=craft.level()||craft.isRemoved()||craft.isSilent()||mc.player==null||craft.distanceToSqr(mc.player)>100*100){
                entry.getValue().finish();it.remove();
            }
        }
        if(mc.level==null||mc.player==null||mc.isPaused())return;
        var candidates=new ArrayList<BlackwatchHelicopter>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof BlackwatchHelicopter craft&&!craft.isRemoved()
                &&!craft.isSilent()&&craft.audioRunning()&&craft.distanceToSqr(mc.player)<96*96)candidates.add(craft);
        candidates.sort(Comparator.comparingDouble(c->c.distanceToSqr(mc.player)));
        for(var craft:candidates)if(!GROUPS.containsKey(craft)&&GROUPS.size()<4)GROUPS.put(craft,new Group(craft));
        for(var it=GROUPS.entrySet().iterator();it.hasNext();){var group=it.next().getValue();group.tick();
            if(group.finished()){group.finish();it.remove();}
        }
    }
    private static final class Group {
        final BlackwatchHelicopter craft;
        final HelicopterAudioState state=new HelicopterAudioState(HelicopterAudioState.BLACKHAWK);
        HelicopterAudioState.Mix mix;
        List<Voice> loops=List.of();Voice transition;
        int age,loopAge;
        Group(BlackwatchHelicopter craft){this.craft=craft;}
        void tick(){
            mix=state.tick(craft.audioRunning(),craft.rotor(),craft.audioVelocity(),craft.audioThrottle(),50);age++;
            if(mix.cue()!=HelicopterAudioState.Cue.NONE){
                if(transition!=null)transition.finish();
                boolean start=mix.cue()==HelicopterAudioState.Cue.STARTUP;
                transition=new Voice(this,start?3:4);Minecraft.getInstance().getSoundManager().play(transition);
            }
            var manager=Minecraft.getInstance().getSoundManager();
            if(!state.silent()&&(loops.isEmpty()||age-loopAge>40&&loops.stream().anyMatch(v->v.isStopped()||!manager.isActive(v)))){
                loops.forEach(Voice::finish);loops=List.of(new Voice(this,0),new Voice(this,1),new Voice(this,2));
                loopAge=age;loops.forEach(manager::play);
            }
            if(state.silent()&&!loops.isEmpty()){loops.forEach(Voice::finish);loops=List.of();}
        }
        boolean finished(){return state.silent()&&(transition==null||transition.isStopped());}
        void finish(){loops.forEach(Voice::finish);if(transition!=null)transition.finish();}
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        final Group group;final int layer;int ticks;
        Voice(Group group,int layer){
            super(VehicleSounds.event(switch(layer){case 0->"helicopter_rotor";case 1->"helicopter_turbine";
                case 2->"helicopter_jet";case 3->"helicopter_startup";default->"helicopter_winddown";}),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());
            this.group=group;this.layer=layer;looping=layer<3;delay=0;update();
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !group.craft.isSilent();}
        void update(){
            var craft=group.craft;var mix=group.mix;x=craft.getX();y=craft.getY()+3;z=craft.getZ();
            volume=switch(layer){case 0->mix.rotorVolume();case 1->mix.engineVolume();case 2->mix.jetVolume();default->1;};
            pitch=layer==0?mix.rotorPitch():layer==1?mix.enginePitch():1;
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();var craft=group.craft;
            if(craft.isRemoved()||craft.isSilent()||craft.level()!=mc.level){finish();return;}
            if(mc.isPaused())return;
            update();ticks++;
            // Non-looping samples also have a bounded lifetime after resource reload/mute.
            if(!looping&&ticks>Math.ceil((layer==3?HelicopterAudioState.STARTUP_SECONDS:HelicopterAudioState.WINDDOWN_SECONDS)*20)+2)finish();
        }
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
    private HelicopterAudio(){}
}
