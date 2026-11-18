package com.codex.blacklight.client.animation;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightMutant;
import com.codex.blacklight.registry.ModAttachments;
import software.bernie.geckolib.model.GeoModel;
import java.util.*;

/** Remember the last rendered pose, so packet arrival never restarts the old windup. */
public final class BruteHitPose {
    private static final Map<BlacklightMutant,State> STATES=new WeakHashMap<>();
    private static final class State {
        float[][] last,entry;long started=Long.MIN_VALUE;int kind=-1,motion=-1;
        double seen,release=-1,groundAt,safeStart;boolean reacting,attacking,physical;
        float groundContact=-1,groundAge,safeContact=-1,lastAttackAge;MutantMove lastMove=MutantMove.NONE;
    }
    public static float[][] current(BlacklightMutant entity){var s=STATES.get(entity);return s==null?null:s.last;}
    public static boolean apply(GeoModel<?> model,BlacklightMutant entity,float partial){
        if(entity.kind()!=BlacklightMutant.Kind.BRUTE)return false;
        var s=STATES.computeIfAbsent(entity,e->new State());var table=CreaturePoseTable.BRUTE;
        var base=new float[table.bones.length][];
        for(int i=0;i<base.length;i++){
            var b=model.getAnimationProcessor().getBone(table.bones[i]);
            if(b==null){STATES.remove(entity);return false;}
            base[i]=new float[]{b.getRotX(),b.getRotY(),b.getRotZ(),b.getPosX(),b.getPosY(),b.getPosZ(),b.getScaleX(),b.getScaleY(),b.getScaleZ()};
        }
        boolean attack=entity.committedAttack();double now=entity.level().getGameTime()+partial;
        var visual=ModAttachments.peekData(entity,ModAttachments.CONSUME_VISUAL,ConsumeVisual.EMPTY);
        var hit=ModAttachments.peekData(entity,ModAttachments.HIT_REACTION,HitReaction.EMPTY);
        if(visual.active()){
            STATES.remove(entity);return false;
        }
        if(com.codex.blacklight.client.renderer.AdaptiveRagdoll.active(entity)){
            // The solver captures this render's skin. Preserve the last displayed
            // pose on entry instead of inserting the generic human flinch first.
            if(!s.physical){s.entry=s.last==null?base:s.last;s.seen=now;s.physical=true;}
            var idle=table.sample("idle",(float)((entity.tickCount+partial)%(table.seconds("idle")*20)));
            var pose=entity.isAlive()?BruteKnockdown.controlled(hit)?BruteKnockdown.pose(hit,entity.level().getGameTime(),partial):BruteFlinch.blend(s.entry,idle,BruteFlinch.ease((float)(now-s.seen)/4)):s.entry;
            s.last=pose;s.reacting=false;s.attacking=false;s.release=-1;s.safeContact=-1;s.groundContact=-1;
            write(model,pose);return true;
        }
        s.physical=false;
        // Keep the last live pose if the death flag arrives before the body packet.
        if(!entity.isAlive())return false;
        if(BruteKnockdown.controlled(hit)){
            var pose=BruteKnockdown.pose(hit,entity.level().getGameTime(),partial);s.last=pose;s.reacting=false;s.attacking=false;s.release=-1;s.safeContact=-1;s.groundContact=-1;
            write(model,pose);return true;
        }
        boolean active=hit.weight(entity.level().getGameTime(),partial)>0;
        if(active&&!BruteFlinch.standing(hit.kind())){STATES.remove(entity);return false;}
        float[][] out=null;
        if(active){
            if(!s.reacting||s.started!=hit.started()||s.kind!=hit.kind()||s.motion!=hit.motion()){
                s.entry=s.last==null?base:s.last;s.seen=now;s.started=hit.started();s.kind=hit.kind();s.motion=hit.motion();
                retainGroundRecovery(s,entity,partial,now);
            }
            var idle=table.sample("idle",(float)((entity.tickCount+partial)%(table.seconds("idle")*20)));
            var desired=BruteFlinch.recoil(idle,hit.kind(),hit.motion(),hit.progress(entity.level().getGameTime(),partial));
            out=BruteFlinch.enter(s.entry,desired,(float)(now-s.seen));s.reacting=true;s.release=-1;
        }else{
            if(s.reacting||s.attacking&&!attack){s.entry=s.last==null?base:s.last;s.release=now;s.reacting=false;retainGroundRecovery(s,entity,partial,now);}
            if(s.release>=0){
                float weight=BruteFlinch.ease((float)(now-s.release)/4);
                // The controller may still be fading its canceled attack. Target the actual idle clip.
                var idle=table.sample("idle",(float)((entity.tickCount+partial)%(table.seconds("idle")*20)));
                out=BruteFlinch.blend(s.entry,idle,weight);
                if(weight>=1||attack){s.release=-1;if(attack)out=null;}
            }
        }
        if(s.safeContact>=0){
            float elapsed=(float)(now-s.safeStart);
            // A hit packet can precede the attack-cancellation entity data. Keep the
            // retained recovery during that overlap instead of discarding it as a new attack.
            if((!attack||active)&&elapsed<BruteRecovery.TICKS){
                out=BruteRecovery.pose(s.safeContact,elapsed);
                // The arms finish clearing the surface; the head still acknowledges the incoming hit.
                if(active){int head=Arrays.asList(table.bones).indexOf("head");out[head][0]-=.10F*hit.weight(entity.level().getGameTime(),partial)*BruteFlinch.ease((float)(now-s.seen)/2.5F);}
            }else{s.safeContact=-1;s.groundContact=-1;s.release=-1;}
        }
        // Explicitly reset every idle channel after an externally-owned pose. Gecko's old
        // attack snapshots can otherwise reappear once the custom release stops writing.
        if(out==null&&!attack&&entity.walkAnimation.speed()<.02F)out=table.sample("idle",(float)((entity.tickCount+partial)%(table.seconds("idle")*20)));
        if(entity.groundRecovery()){s.groundContact=entity.recoilStart();s.groundAge=entity.attackAge(partial)-s.groundContact;s.groundAt=now;}
        else if(attack)s.groundContact=-1;
        s.lastMove=entity.attackMove();s.lastAttackAge=entity.attackAge(partial);
        s.attacking=attack;s.last=out==null?base:out;
        if(out==null)return false;
        write(model,out);return true;
    }
    private static void write(GeoModel<?> model,float[][] out){
        var table=CreaturePoseTable.BRUTE;
        for(int i=0;i<out.length;i++){
            var b=model.getAnimationProcessor().getBone(table.bones[i]);var p=out[i];
            b.updateRotation(p[0],p[1],p[2]);b.setPosX(p[3]);b.setPosY(p[4]);b.setPosZ(p[5]);b.setScaleX(p[6]);b.setScaleY(p[7]);b.setScaleZ(p[8]);
        }
    }
    private static void retainGroundRecovery(State s,BlacklightMutant entity,float partial,double now){
        if(s.safeContact>=0)return;
        if(s.groundContact>=0){s.safeContact=s.groundContact;s.safeStart=s.groundAt-s.groundAge;return;}
        if(s.lastMove!=MutantMove.BRUTE_SLAM||s.lastAttackAge<19||s.lastAttackAge>28)return;
        // Contact and incoming-hit packets can arrive together. Reconstruct the same
        // local terrain stop that held the fist before the server's recoil packet.
        var shapes=new ArrayList<net.minecraft.world.phys.AABB>();
        for(var shape:entity.level().getBlockCollisions(entity,entity.getBoundingBox().inflate(2.5,2,2.5)))shapes.addAll(shape.toAabbs());
        var origin=entity.getPosition(partial);float yaw=entity.yBodyRot;
        var contact=HydraContact.obstacle(t->BruteContact.boxes(MutantMove.BRUTE_SLAM,(float)t,origin,yaw,true),19,28,shapes);
        if(contact!=null&&s.lastAttackAge>=contact.safeAge()){
            s.safeContact=contact.safeAge();s.safeStart=now;
        }
    }
    private BruteHitPose(){}
}
