package com.codex.blacklight.client.audio;

import com.codex.blacklight.combat.GreeneProjectileAudioState;
import com.codex.blacklight.entity.GreeneProjectile;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;

/** Client-local polled loops; server effects own the one-shot impact sound.
 * The assets are credited project Foley adapters, never original P1 audio. */
public final class GreeneProjectileAudio {
    private static final Map<GreeneProjectile,Entry> VOICES=new IdentityHashMap<>();
    private static Object world;
    /** Caller runs this before the ordinary client null/paused early returns. */
    public static void tick(){
        var mc=Minecraft.getInstance();
        if(world!=mc.level){clear();world=mc.level;}
        var listener=mc.gameRenderer.getMainCamera().getPosition();
        boolean audible=mc.options.getSoundSourceVolume(SoundSource.MASTER)>0&&mc.options.getSoundSourceVolume(SoundSource.HOSTILE)>0;
        // Identity check also catches untracking/replaced entities with reused IDs.
        for(var it=VOICES.entrySet().iterator();it.hasNext();){var e=it.next();var p=e.getKey();var entry=e.getValue();
            if(!valid(p,mc)||entry.state.profile()!=GreeneProjectileAudioState.of(p.ammo())||p.position().distanceToSqr(listener)>=entry.state.profile().maximumDistance()*entry.state.profile().maximumDistance()){
                entry.finish();it.remove();
            }
        }
        if(mc.level==null||mc.player==null)return;
        // Retain native pause/resume of the same SoundManager handle. Do not
        // advance envelope/backoff or treat a paused channel as missing.
        if(!mc.isPaused()){
            var candidates=new ArrayList<GreeneProjectile>();
            for(var actor:mc.level.entitiesForRendering())if(actor instanceof GreeneProjectile p&&valid(p,mc)&&!p.finished()){
                var profile=GreeneProjectileAudioState.of(p.ammo());
                if(p.position().distanceToSqr(listener)<profile.maximumDistance()*profile.maximumDistance())candidates.add(p);
            }
            candidates.sort(Comparator.<GreeneProjectile>comparingDouble(p->p.position().distanceToSqr(listener)).thenComparing(GreeneProjectile::getUUID));
            var chosen=Collections.newSetFromMap(new IdentityHashMap<GreeneProjectile,Boolean>());
            // Retiring fire tails consume the same budget until stop/removal.
            int slots=GreeneProjectileAudioState.MAX_VOICES-(int)VOICES.keySet().stream().filter(GreeneProjectile::finished).count();
            for(var p:candidates){if(chosen.size()>=slots)break;chosen.add(p);}
            for(var it=VOICES.entrySet().iterator();it.hasNext();){var e=it.next();if(!e.getKey().finished()&&!chosen.contains(e.getKey())){e.getValue().finish();it.remove();}}
            for(var p:chosen)VOICES.computeIfAbsent(p,Entry::new);
        }
        for(var it=VOICES.entrySet().iterator();it.hasNext();){var entry=it.next().getValue();entry.advance(mc,audible);
            if(entry.state.dead()){entry.finish();it.remove();}
        }
    }
    private static boolean valid(GreeneProjectile p,Minecraft mc){return mc.level!=null&&mc.player!=null&&p.level()==mc.level&&!p.isRemoved()&&!p.isSilent()&&mc.level.getEntity(p.getId())==p;}
    public static void clear(){VOICES.values().forEach(Entry::finish);VOICES.clear();world=null;}
    private static final class Entry {
        final GreeneProjectile projectile;final GreeneProjectileAudioState state;final float pitch;Voice voice;
        Entry(GreeneProjectile projectile){
            this.projectile=projectile;state=new GreeneProjectileAudioState(GreeneProjectileAudioState.of(projectile.ammo()));
            // Stable UUID seed preserves per-instance pitch through reload and
            // retracking, without claiming the original native RNG sequence.
            var id=projectile.getUUID();pitch=state.pitch(new Random(id.getMostSignificantBits()^id.getLeastSignificantBits()).nextDouble());
        }
        void advance(Minecraft mc,boolean audible){
            var action=state.tick(valid(projectile,mc),projectile.finished(),mc.isPaused(),audible,voice!=null&&!voice.isStopped()&&mc.getSoundManager().isActive(voice));
            if(action==GreeneProjectileAudioState.Action.STOP)finish();
            else if(action==GreeneProjectileAudioState.Action.START){finish();voice=new Voice(this);mc.getSoundManager().play(voice);}
            if(voice!=null)voice.update();
        }
        void finish(){if(voice!=null){voice.finish();voice=null;}}
    }
    private static final class Voice extends AbstractTickableSoundInstance {
        final Entry entry;
        Voice(Entry entry){
            super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("blacklight","greene.projectile_"+entry.state.profile().cue()+"_loop")),SoundSource.HOSTILE,SoundInstance.createUnseededRandom());
            this.entry=entry;looping=true;delay=0;relative=false;attenuation=SoundInstance.Attenuation.NONE;pitch=entry.pitch;update();
        }
        @Override public boolean canStartSilent(){return true;}
        @Override public boolean canPlaySound(){return !entry.projectile.isSilent();}
        void update(){
            var p=entry.projectile;var mc=Minecraft.getInstance();x=p.getX();y=p.getY();z=p.getZ();
            volume=entry.state.gain(p.position().distanceTo(mc.gameRenderer.getMainCamera().getPosition()));
        }
        @Override public void tick(){
            var mc=Minecraft.getInstance();
            if(!valid(entry.projectile,mc)||entry.state.dead()){finish();return;}
            if(!mc.isPaused())update();
        }
        void finish(){stop();Minecraft.getInstance().getSoundManager().stop(this);}
    }
    private GreeneProjectileAudio(){}
}
