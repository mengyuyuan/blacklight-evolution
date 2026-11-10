package com.codex.blacklight.client.camera;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.animation.PlayerMotionController;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** One source animation clock for body pose, camera events, impact pause and consume recovery. */
final class SourceActionCamera {
    record Values(double dolly,double zoom,double reAim,Vec3 offset) {
        static final Values NEUTRAL=new Values(1,1,0,Vec3.ZERO);
        Values blend(Values next,double weight){return new Values(Mth.lerp(weight,dolly,next.dolly),Mth.lerp(weight,zoom,next.zoom),Mth.lerp(weight,reAim,next.reAim),offset.lerp(next.offset,weight));}
    }
    private record Clock(String key,long started,double seconds,float facing) {}
    private Clock previous;
    private Values current=Values.NEUTRAL,leaving=Values.NEUTRAL;
    private Values entry=Values.NEUTRAL;
    private double exitTime,exitDuration;
    private double entryAt,entryOut,highWater;
    private float facing;
    private LivingEntity cachedVictim;
    private long victimTick=Long.MIN_VALUE;
    private final SourceCameraShake shake=new SourceCameraShake();
    void reset(){previous=null;current=leaving=entry=Values.NEUTRAL;exitTime=exitDuration=0;entryAt=entryOut=0;highWater=-.000001;cachedVictim=null;victimTick=Long.MIN_VALUE;shake.reset();}
    Values update(LocalPlayer player,float partial,double dt,Vec3 camera) {
        Clock clock=clock(player,partial);
        if(clock==null) {
            if(previous!=null){leaving=current;exitTime=0;exitDuration=Math.max(.001,remainingOut());previous=null;}
            exitTime+=dt;current=leaving.blend(Values.NEUTRAL,Math.clamp(exitTime/Math.max(.001,exitDuration),0,1));
        }else {
            boolean same=previous!=null&&clock.key.equals(previous.key)&&clock.started==previous.started;
            if(!same) {
                double out=previous==null?Math.max(0,exitDuration-exitTime):remainingOut();
                entry=current;entryAt=clock.seconds;entryOut=out;
                highWater=-.000001;
            }
            var events=SourceCameraEvents.events(clock.key);
            for(var event:events) {
                if(event.start()>clock.seconds)break;
                if(event.kind().equals("cameraShakeRequest")&&event.start()>highWater
                        &&clock.seconds-event.start()<.2)shake.request(event.size(),event.range(),player.position(),camera);
            }
            highWater=Math.max(highWater,clock.seconds);
            SourceCameraEvents.Event active=null;Values from=Values.NEUTRAL;
            for(var event:events) {
                if(!event.kind().equals("punchinCamera")||event.start()>clock.seconds)continue;
                from=active==null?entryValue(event.start()):sample(active,from,event.start());active=event;
            }
            current=active==null?entryValue(clock.seconds):sample(active,from,clock.seconds);
            previous=clock;facing=clock.facing;leaving=current;exitTime=0;
        }
        shake.update(dt);return current;
    }
    private Values entryValue(double time) {
        if(time<entryAt-.001)return Values.NEUTRAL;
        return entry.blend(Values.NEUTRAL,Math.clamp((time-entryAt)/Math.max(.001,entryOut),0,1));
    }
    private static Values sample(SourceCameraEvents.Event e,Values from,double time) {
        double age=time-e.start();var target=new Values(e.dolly(),e.zoom(),e.reAim()?1:0,e.offset());
        double in=inSeconds(e.inTime());
        if(age<in)return from.blend(target,ease(age/in));
        if(time<e.end()-e.outTime())return target;
        return target.blend(Values.NEUTRAL,ease(Math.clamp((time-e.end()+e.outTime())/Math.max(.000001,e.outTime()),0,1)));
    }
    /** Source punch-ins ramp in 0.07-0.17 s at constant speed; on a third-person chase rig that is
     * a 1.5-block cut with a hard start and stop. Keep the authored target, ease both ends, and give
     * the push at least {@link #MIN_IN_SECONDS}. */
    static final double MIN_IN_SECONDS=.18;
    static double inSeconds(double authored){return Math.max(MIN_IN_SECONDS,authored);}
    static double ease(double t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    private static double lastOut(Clock clock) {
        double out=0;
        for(var e:SourceCameraEvents.events(clock.key))if(e.kind().equals("punchinCamera")&&e.start()<=clock.seconds)out=e.outTime();
        return out;
    }
    private double remainingOut(){return Math.max(lastOut(previous),Math.max(0,entryOut-(previous.seconds-entryAt)));}
    float facing(){return facing;}
    Vec3 rotation(){return shake.rotation();}
    Vec3 translation(){return shake.translation();}
    void impact(com.codex.blacklight.network.PhysicalFeedbackPayload payload,LocalPlayer player,Vec3 camera) {
        var move=player.getData(ModAttachments.WEAPON_VISUAL).spec();
        if(move!=null&&SourceCameraEvents.events(move.id).stream().anyMatch(e->e.kind().equals("cameraShakeRequest")))return;
        // Minecraft contact/landing events bridge to source common/light/medium/heavy presets.
        // Traversal starts have no matching source shake event, so they do not invent a pulse.
        int size=switch(payload.effect()) {
            case LIGHT_LANDING,CLAW_HIT,BLADE_CUT,BLADE_RETURN -> 0;
            case HEAVY_LANDING,CLAW_HEAVY,BLADE_HEAVY,GROUND_SPIKE,SPRINT_COLLISION -> 1;
            case DEVASTATING_LANDING -> 2;
            default -> -1;
        };
        if(size>=0&&payload.intensity()>0)shake.request(size,size,player.position(),camera);
    }
    boolean hint(String name) {
        if(previous==null)return false;
        for(var e:SourceCameraEvents.events(previous.key))if(e.kind().equals("cameraHint")&&e.hint().equals(name)&&previous.seconds>=e.start()&&previous.seconds<e.end())return true;
        return false;
    }
    private Clock clock(LocalPlayer player,float partial) {
        var recovery=player.getData(ModAttachments.CONSUME_RECOVERY);
        if(recovery.active()&&!player.getData(ModAttachments.CONSUMING)&&player.getData(ModAttachments.WEAPON_VISUAL).spec()==null
                &&recovery.phaseProgress(player.level().getGameTime(),partial)<1)return consumeClock(player,recovery,partial);
        if(player.getData(ModAttachments.GRAB_STAGE)!=ConsumeVisual.NONE) {
            long tick=player.level().getGameTime();
            if(victimTick!=tick) {
                victimTick=tick;
                cachedVictim=player.level().getEntitiesOfClass(LivingEntity.class,player.getBoundingBox().inflate(18),
                        e->e.getData(ModAttachments.CONSUME_VISUAL).consumerId()==player.getId()).stream().findFirst().orElse(null);
            }
            if(cachedVictim!=null&&!cachedVictim.isRemoved())return consumeClock(player,cachedVictim.getData(ModAttachments.CONSUME_VISUAL),partial);
        }
        var visual=player.getData(ModAttachments.WEAPON_VISUAL);var move=visual.spec();
        if(move==null)return null;
        String key=visual.charging()?LocalWeaponMotion.find(move.id).flatMap(LocalWeaponMotion.Definition::charge).map(LocalWeaponMotion.Charge::animation).orElse(""):move.id;
        if(SourceCameraEvents.events(key).isEmpty())return null;
        if(!PlayerMotionController.isPlaying(player,key))return null;
        double seconds=PlayerMotionController.elapsed(player,partial);
        return new Clock(key,visual.started(),seconds,visual.yaw());
    }
    private Clock consumeClock(LocalPlayer player,ConsumeVisual visual,float partial) {
        if(!visual.consuming())return null;
        String key="consume_"+GrappleMotion.sceneKey(player,visual)+"_actor";
        float duration=PlayerMotionController.duration(key);
        if(duration<=0||SourceCameraEvents.events(key).isEmpty())return null;
        double max=Math.max(0,duration-.001F);
        double time=visual.stage()==ConsumeVisual.HUNTER_CONSUME?Math.min(HunterConsume.seconds(visual,player.level().getGameTime(),partial),max)
                :GrappleMotion.sceneProgress(player,visual,partial)*max;
        return new Clock(key,visual.started(),time,visual.facing());
    }
}
