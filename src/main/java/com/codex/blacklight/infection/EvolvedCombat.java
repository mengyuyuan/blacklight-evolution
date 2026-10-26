package com.codex.blacklight.infection;

import com.codex.blacklight.combat.EvolvedMotions;
import com.codex.blacklight.combat.EvolvedAttack;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.HashSet;
import java.util.Set;

/** Server collision/navigation plus the original P2 attack displacement curves. */
public final class EvolvedCombat {
    private final BlacklightInfectedHost host;
    private final Set<Integer> contacts=new HashSet<>();
    private final Set<Integer> cues=new HashSet<>();
    private final EvolvedPoise poise=new EvolvedPoise();
    private Vec3 committedDirection=Vec3.ZERO,anchor,lastChasePosition;
    private int stalledChaseTicks;
    private float committedYaw,travelScale=1;
    private int cooldown=8,dodgeCooldown=40,graspCooldown=60,leapCooldown,pathCooldown,counterCooldown,tauntCooldown=180,sequence,wallTicks;
    public EvolvedCombat(BlacklightInfectedHost host){this.host=host;}
    public boolean acceptImpact(){return poise.accept(host.level().getGameTime());}
    public boolean controlProtected(){return host.isEvolved()&&poise.protectedAt(host.level().getGameTime());}
    public void interruptForHit(){interrupt();cooldown=4;}
    public void interrupt(){host.setEvolvedAction("");host.setEvolvedEngaged(false);host.setSprinting(false);host.getNavigation().stop();contacts.clear();anchor=null;pathCooldown=0;cooldown=Math.max(cooldown,12);}
    public boolean evading(){float t=host.evolvedActionElapsed(0);return host.isEvolved()&&host.evolvedAction().startsWith("dodge_")&&t>=3&&t<10;}
    private boolean valid(LivingEntity target){return target!=null&&target.isAlive()&&target!=host&&target.level()==host.level()&&!host.isAlliedTo(target)
            &&!(target instanceof Player p&&(p.isCreative()||p.isSpectator()));}
    private double gap(LivingEntity target){return Math.max(0,host.distanceTo(target)-(host.getBbWidth()+target.getBbWidth())*.5);}
    public boolean tick(){
        if(!host.isEvolved()||host.level().isClientSide||host.isNoAi())return false;
        if(!host.isAlive()||host.isPassenger()){interrupt();return false;}
        if(cooldown>0)cooldown--;if(dodgeCooldown>0)dodgeCooldown--;if(graspCooldown>0)graspCooldown--;
        if(leapCooldown>0)leapCooldown--;if(pathCooldown>0)pathCooldown--;if(counterCooldown>0)counterCooldown--;
        if(tauntCooldown>0)tauntCooldown--;
        var target=host.getTarget();
        if(!valid(target)){if(!host.evolvedAction().isEmpty()||host.evolvedEngaged())interrupt();host.setTarget(null);return false;}
        host.setEvolvedEngaged(true);host.getLookControl().setLookAt(target,30,30);
        if(host.organActionCore()!=null)return host.organAbilities().tick();
        double gap=gap(target);
        if(host.evolvedAction().startsWith("taunt_")&&(gap<2.8||gap>8||threatening(target)||!host.hasLineOfSight(target))){
            host.setEvolvedAction("");anchor=null;cooldown=2;pathCooldown=0;
        }
        if(host.evolvedAction().isEmpty()){
            face(target);
            if(target instanceof BlacklightInfectedHost prey&&TissueCompetition.tryStart(host,prey))return true;
            if(!host.hasLineOfSight(target)){chase(target,gap);return true;}
            boolean pressure=threatening(target);
            if(cooldown>0){if(gap>2.2)chase(target,gap);else circle(target,gap);return true;}
            if(pressure&&dodgeCooldown==0&&gap<4.5&&host.onGround()&&host.spendTissueNutrition(1)){
                start((sequence++&1)==0?"dodge_left":"dodge_right",target);dodgeCooldown=75;counterCooldown=Math.max(counterCooldown,75);
            }else if(pressure&&counterCooldown==0&&gap<1.4&&host.onGround()){
                start("slash",target);counterCooldown=100;dodgeCooldown=Math.max(dodgeCooldown,75);
            }else if(tauntCooldown==0&&gap>3.5&&gap<7&&host.onGround()&&!pressure){
                start((sequence++&1)==0?"taunt_sharpen":"taunt_beckon",target);tauntCooldown=260+host.getId()%100;
            }else if(gap>4.5){
                if(gap<11&&leapCooldown==0&&host.onGround()&&host.spendTissueNutrition(3)){start("leap",target);leapCooldown=70;}
                else {chase(target,gap);return true;}
            }else if(gap>1.7&&host.onGround())start("rush",target);
            else if(gap>1.7){chase(target,gap);return true;}
            else if(graspCooldown==0&&sequence%5==4&&gap>1.15&&gap<=TissueOrganCycle.reach(TissueCore.HYDROSTAT)-.2
                    &&EvolvedForm.grasp(host.tissueInheritance(),host.nativeTissueCore(),host.tissueAge())
                    &&host.spendTissueNutrition(TissueOrganCycle.cycle(TissueCore.HYDROSTAT).cost())){
                sequence++;graspCooldown=120;host.setSprinting(false);host.setOrganAction(TissueCore.HYDROSTAT,target.getId(),toward(target));return true;
            }else start((sequence++&1)==0?"thrust_left":"thrust_right",target);
        }
        String move=host.evolvedAction();if(move.isEmpty())return true;
        var clip=EvolvedMotions.clip(move);float elapsed=host.evolvedActionElapsed(0);
        // Release a source thrust into mobile recovery after its attack/retraction.
        float end=clip==null?0:move.startsWith("thrust")?Math.min(34,clip.seconds()*20):move.equals("slash")?Math.min(46,clip.seconds()*20):clip.seconds()*20;
        if(clip==null||elapsed>=end){finish(move,target);return true;}
        host.getNavigation().stop();host.setXxa(0);host.setZza(0);host.setSprinting(false);
        host.setYRot(committedYaw);host.yBodyRot=committedYaw;host.yHeadRot=committedYaw;
        if(!travel(move,elapsed)){finish(move,target);return true;}
        int window=EvolvedAttack.contact(move,elapsed);
        if(window>=0){if(cues.add(window))EvolvedCombatEffects.sweep(host);contact(target,window,move.equals("leap")?1.3F:move.startsWith("thrust")?1.2F:window==1?1.15F:1);}
        if(move.equals("leap")&&elapsed>25&&host.onGround()&&cues.add(4))EvolvedCombatEffects.ground(host,true);
        return true;
    }
    private Vec3 toward(LivingEntity target){var v=target.position().subtract(host.position()).multiply(1,0,1);return v.lengthSqr()<.001?host.getLookAngle().multiply(1,0,1).normalize():v.normalize();}
    private void face(LivingEntity target){var d=toward(target);float desired=(float)Math.toDegrees(Math.atan2(-d.x,d.z));host.setYRot(Mth.approachDegrees(host.getYRot(),desired,24));host.yBodyRot=host.getYRot();}
    private boolean threatening(LivingEntity target){
        if(target.getAttackAnim(0)>.1F||host.hurtTime>0)return true;
        if(target instanceof Player p){var v=p.getData(ModAttachments.WEAPON_VISUAL);long age=p.level().getGameTime()-v.started();return v.move()>=0&&age>=0&&(v.charging()||age<80);}
        return false;
    }
    private void chase(LivingEntity target,double gap){
        host.setSprinting(gap>4);
        if(pathCooldown==0){host.getNavigation().moveTo(target,gap>4?1.4:1.05);pathCooldown=6;}
        var path=host.getNavigation().getPath();
        var next=path!=null&&!path.isDone()?path.getNextEntityPos(host).subtract(host.position()).multiply(1,0,1):toward(target);
        var projected=next.lengthSqr()>.0001?next.normalize().scale(.4):Vec3.ZERO;
        boolean stepDown=path!=null&&!path.isDone()&&path.getNextNode().y<host.getY()-.5&&path.getNextNode().y>=host.getY()-1.25;
        if(host.onGround()&&projected.lengthSqr()>.0001&&!supported(projected,stepDown)){
            host.getNavigation().stop();host.getMoveControl().setWantedPosition(host.getX(),host.getY(),host.getZ(),0);
            host.setXxa(0);host.setZza(0);host.setDeltaMovement(0,host.getDeltaMovement().y,0);pathCooldown=0;return;
        }
        stalledChaseTicks=lastChasePosition!=null&&host.position().distanceToSqr(lastChasePosition)<.0004?stalledChaseTicks+1:0;
        lastChasePosition=host.position();
        // Ground navigation may return a partial path before actual attack reach.
        // Close a visible, supported gap with ordinary collision movement; never teleport.
        if(gap>1.7&&host.onGround()&&host.hasLineOfSight(target)&&(host.getNavigation().isDone()||stalledChaseTicks>=10)){
            var step=toward(target).scale(.16);var box=host.getBoundingBox().move(step);
            if(!supported(step)||!host.level().noCollision(host,box))return;
            host.setDeltaMovement(step.x,host.getDeltaMovement().y,step.z);
        }
    }
    private boolean supported(Vec3 step){
        return supported(step,false);
    }
    private boolean supported(Vec3 step,boolean stepDown){
        var box=host.getBoundingBox().move(step);
        for(double x:new double[]{box.minX+.05,box.maxX-.05})for(double z:new double[]{box.minZ+.05,box.maxZ-.05}){
            var floor=net.minecraft.core.BlockPos.containing(x,box.minY-.2,z);
            if(!host.level().hasChunkAt(floor))return false;
            if(host.level().getBlockState(floor).getCollisionShape(host.level(),floor).isEmpty()
                    &&(!stepDown||host.level().getBlockState(floor.below()).getCollisionShape(host.level(),floor.below()).isEmpty()))return false;
        }
        return true;
    }
    private void circle(LivingEntity target,double gap){
        host.getNavigation().stop();host.setSprinting(false);
        var forward=toward(target);double side=((host.getUUID().getLeastSignificantBits()+sequence)&1)==0?1:-1;
        Vec3 lateral=new Vec3(forward.z,0,-forward.x).scale(side*.085);
        Vec3 step=lateral.add(forward.scale(gap<.8?-.12:gap>1.6?.045:0));
        var floor=net.minecraft.core.BlockPos.containing(host.position().add(step.scale(2)).add(0,-.15,0));
        if(host.level().hasChunkAt(floor)&&!host.level().getBlockState(floor).isAir()&&host.level().noCollision(host,host.getBoundingBox().move(step)))
            host.setDeltaMovement(step.x,host.getDeltaMovement().y,step.z);
        else {pathCooldown=0;chase(target,gap);}
    }
    private void start(String move,LivingEntity target){
        contacts.clear();cues.clear();anchor=host.position();wallTicks=0;committedDirection=toward(target);
        committedYaw=(float)Math.toDegrees(Math.atan2(-committedDirection.x,committedDirection.z));travelScale=1;
        if(move.equals("rush")||move.equals("leap")||move.startsWith("thrust")){
            float extent=EvolvedMotions.forwardExtent(move);
            if(extent>.01)travelScale=(float)Math.clamp((host.distanceTo(target)-1.15)/extent,move.startsWith("thrust")?.10:.3,move.equals("leap")?1.9:1.4);
        }else if(move.equals("slash"))travelScale=.55F;
        else if(move.startsWith("dodge"))travelScale=.65F;
        host.setEvolvedAction(move);host.setSprinting(false);host.getNavigation().stop();
        if(move.equals("leap")||move.startsWith("dodge")||move.equals("rush"))EvolvedCombatEffects.ground(host,false);
    }
    private boolean travel(String move,float elapsed){
        if(anchor==null){anchor=host.position();committedDirection=host.getLookAngle().multiply(1,0,1).normalize();committedYaw=host.getYRot();}
        if(host.horizontalCollision&&elapsed>2)wallTicks++;else wallTicks=0;
        if(wallTicks>=2)return false;
        var root=EvolvedMotions.root(move,elapsed+1);var left=new Vec3(committedDirection.z,0,-committedDirection.x);
        var desired=anchor.add(left.scale(root.x*travelScale)).add(committedDirection.scale(root.z*travelScale));
        var delta=desired.subtract(host.position()).multiply(1,0,1);double max=1.25;
        if(delta.length()>max)delta=delta.normalize().scale(max);
        var next=net.minecraft.core.BlockPos.containing(host.position().add(delta));if(!host.level().hasChunkAt(next))return false;
        if(!move.equals("leap")&&host.onGround()){
            var support=net.minecraft.core.BlockPos.containing(host.position().add(delta).add(0,-.2,0));
            if(host.level().getBlockState(support).getCollisionShape(host.level(),support).isEmpty()){
                host.setDeltaMovement(0,host.getDeltaMovement().y,0);return false;
            }
        }
        double y=move.equals("leap")?Math.clamp(anchor.y+root.y-host.getY(),-.8,.8):host.getDeltaMovement().y;
        host.setDeltaMovement(delta.x,y,delta.z);if(move.equals("leap")&&y>.03)host.setOnGround(false);
        host.hurtMarked=true;return true;
    }
    private void finish(String move,LivingEntity target){
        host.setEvolvedAction("");anchor=null;contacts.clear();pathCooldown=0;cooldown=move.startsWith("dodge")?6:move.equals("rush")?0:7;
        if(move.equals("rush")&&valid(target)&&host.hasLineOfSight(target)&&gap(target)<2)
            start((sequence++&1)==0?"thrust_left":"thrust_right",target);
    }
    private double reach(){return EvolvedForm.blade(host.tissueInheritance(),host.nativeTissueCore(),TissueOrgans.seed(host.getUUID()),host.tissueAge())?2.0:1.1;}
    private void contact(LivingEntity target,int key,float factor){
        if(contacts.contains(key)||!host.hasLineOfSight(target)
                ||!host.getBoundingBox().inflate(reach(),.65,reach()).intersects(target.getBoundingBox()))return;
        var toward=target.position().subtract(host.position()).multiply(1,0,1);
        if(toward.lengthSqr()>.01&&toward.normalize().dot(committedDirection)<.1)return;
        contacts.add(key);float damage=(float)host.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)*factor;
        if(target.hurt(host.damageSources().mobAttack(host),damage)){EvolvedCombatEffects.impact(host,target);var d=target.position().subtract(host.position());target.knockback(.38,-d.x,-d.z);}
    }
}
