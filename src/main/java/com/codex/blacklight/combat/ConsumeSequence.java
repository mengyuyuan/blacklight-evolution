package com.codex.blacklight.combat;

import com.codex.blacklight.power.PowerForm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.phys.Vec3;

/** Shared phase clock for both participants. Geometry adapts source scenes to Minecraft bodies. */
public final class ConsumeSequence {
    private ConsumeSequence() {}
    public static String key(PowerForm form) {
        return switch(form) {
            case CLAWS -> "claws"; case BLADE -> "blade"; case HAMMERFISTS -> "hammer";
            case WHIPFIST -> "whip"; case MUSCLE_MASS -> "muscle"; default -> "normal";
        };
    }
    public static int duration(PowerForm form,boolean large) {
        int animation=LocalVisualMotion.find("consume_"+clipKey(form,large)+"_actor")
                .map(LocalVisualMotion.Definition::durationTicks).orElse(large?100:ConsumeVisual.legacyDuration(form));
        var scene=ConsumeReceiverScene.profile(form,large);
        return scene==null?animation:Math.max(animation,(int)Math.ceil((scene.finishSeconds()+.10F)*20));
    }
    /** Minecraft choreography: finish the return flow before the actor's recovery tail.
     * This is not a recovered Prototype completion event. */
    public static float completion(PowerForm form,boolean large) {
        var scene=ConsumeReceiverScene.profile(form,large);
        if(scene!=null)return scene.finishSeconds()*20/duration(form,large);
        return Math.max(.88F,contact(form,large)+.12F);
    }
    public static int completionTick(PowerForm form,boolean large) {
        var scene=ConsumeReceiverScene.profile(form,large);
        if(scene!=null)return Math.min(duration(form,large)-1,(int)Math.ceil(scene.finishSeconds()*20-.0001F));
        return Math.min(duration(form,large)-1,(int)Math.ceil(duration(form,large)*completion(form,large)));
    }
    public static boolean paired(PowerForm form,boolean large) {
        String prefix="consume_"+clipKey(form,large);
        return LocalVisualMotion.find(prefix+"_actor").isPresent()&&LocalVisualMotion.find(prefix+"_victim").isPresent();
    }
    public static String clipKey(PowerForm form,boolean large) {
        if(!large)return key(form);
        return switch(form){case HAMMERFISTS->"large_hammer";case MUSCLE_MASS->"large_muscle";case WHIPFIST->"large_whip";case CLAWS->"claws";case BLADE->"blade";default->"large";};
    }
    public static float contact(PowerForm form,boolean large){return large?switch(form){case HAMMERFISTS->.42F;case WHIPFIST->.36F;case NONE->.38F;default->strike(form);}:strike(form);}
    /** Receiver release markers are Minecraft adaptation cues, not recovered original event metadata. */
    public static float release(PowerForm form){return switch(form){case HAMMERFISTS->.32F;case WHIPFIST->.20F;case BLADE->.47F;case MUSCLE_MASS->.49F;case CLAWS->.48F;default->.43F;};}
    public static boolean large(LivingEntity target) {
        return target instanceof com.codex.blacklight.entity.BlacklightHunter || target instanceof EnderDragon || target instanceof WitherBoss || target.getBbHeight()>=2.35F
                || target.getBbWidth()*target.getBbWidth()*target.getBbHeight()>=3.2F;
    }
    public static boolean boss(LivingEntity target) {
        return target instanceof EnderDragon||target instanceof WitherBoss
                ||target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&(host.giantBoss()||host.encounterBoss());
    }
    public static boolean humanoid(LivingEntity target) {
        if(target instanceof com.codex.blacklight.entity.BlackwatchSoldier)return true;
        if(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host)return host.humanoid();
        return target instanceof net.minecraft.world.entity.monster.Zombie
                ||target instanceof net.minecraft.world.entity.monster.AbstractSkeleton
                ||target instanceof net.minecraft.world.entity.monster.AbstractIllager
                ||target instanceof net.minecraft.world.entity.monster.piglin.AbstractPiglin
                ||target instanceof net.minecraft.world.entity.npc.AbstractVillager;
    }
    public static boolean quadruped(LivingEntity target){
        if(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host)return host.quadruped();
        return target instanceof net.minecraft.world.entity.animal.Pig||target instanceof net.minecraft.world.entity.animal.Cow
                ||target instanceof net.minecraft.world.entity.animal.Sheep||target instanceof net.minecraft.world.entity.animal.PolarBear
                ||target instanceof net.minecraft.world.entity.animal.Panda||target instanceof net.minecraft.world.entity.animal.goat.Goat
                ||target instanceof net.minecraft.world.entity.animal.horse.AbstractHorse
                ||target instanceof net.minecraft.world.entity.animal.Wolf||target instanceof net.minecraft.world.entity.animal.Cat
                ||target instanceof net.minecraft.world.entity.animal.Ocelot||target instanceof net.minecraft.world.entity.animal.Fox
                ||target instanceof net.minecraft.world.entity.animal.Rabbit||target instanceof net.minecraft.world.entity.animal.Turtle
                ||target instanceof net.minecraft.world.entity.monster.hoglin.Hoglin||target instanceof net.minecraft.world.entity.monster.Zoglin;
    }
    public static boolean nativeReceiver(LivingEntity target){return humanoid(target)||quadruped(target)||target instanceof net.minecraft.world.entity.monster.Creeper||(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.creeper());}
    public static Vec3 contact(LivingEntity target) {
        if(target instanceof EnderDragon dragon)return dragon.head.getBoundingBox().getCenter();
        if(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.hasMultipartBody())
            for(var part:host.getParts())if(part.active()&&part.partName().equals("head"))return part.getBoundingBox().getCenter();
        return target.position().add(0,target.getBbHeight()*.72,0);
    }
    public static Vec3 mount(LivingEntity target) {
        Vec3 forward=Vec3.directionFromRotation(0,target.getYRot());
        if(target instanceof EnderDragon dragon)return dragon.head.position().subtract(forward.scale(.85)).add(0,.3,0);
        if(target instanceof com.codex.blacklight.entity.BlacklightInfectedHost host&&host.hasMultipartBody())
            for(var part:host.getParts())if(part.active()&&part.partName().equals("head"))
                return part.position().subtract(forward.scale(part.getBbWidth()*.5+.35)).add(0,Math.max(.3,part.getBbHeight()*.7),0);
        return target.position().subtract(forward.scale(target.getBbWidth()*.5+.32))
                .add(0,Math.max(.25,target.getBbHeight()-1.65),0);
    }
    public static boolean mountedStage(int stage) {
        return stage==ConsumeVisual.MOUNT||stage==ConsumeVisual.MOUNT_HOLD||stage==ConsumeVisual.MOUNT_CONSUME||stage==ConsumeVisual.HUNTER_CONSUME;
    }
    public static float strike(PowerForm form) {
        return switch(form) {case HAMMERFISTS->.78F;case BLADE->.43F;case WHIPFIST->.80F;case MUSCLE_MASS->.42F;default->.35F;};
    }
    /** Both participants and the contact solver pause together for two ticks at impact. */
    public static float motionProgress(PowerForm form,float t,int duration,boolean large) {
        // Source event profiles run in seconds. Extending an effect tail must not
        // stretch the paired skeleton clips or replay their final motion.
        if(ConsumeReceiverScene.profile(form,large)!=null)
            return Math.clamp(t*duration/(20*ConsumeReceiverScene.clipSeconds(form)),0,1);
        float strike=contact(form,large),half=1F/Math.max(20,duration),a=strike-half,b=strike+half;
        return t<a?t*strike/a:t<=b?strike:strike+(t-b)*(1-strike)/(1-b);
    }
}
