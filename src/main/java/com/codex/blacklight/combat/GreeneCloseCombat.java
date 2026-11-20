package com.codex.blacklight.combat;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.infection.InfectedSoundCue;
import com.codex.blacklight.registry.ModInfectedSounds;
import java.util.HashSet;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Independent source close-hit branch. Shooting continues while the selected
 * Slug partition strikes. Physical forces, terrain cover and removal of a broken
 * side are explicit Minecraft adapters; source timers and Hit windows are kept. */
public final class GreeneCloseCombat {
    private final EncounterMob owner;
    private final GreeneCloseAttack clock=new GreeneCloseAttack();
    private final HashSet<UUID> receipts=new HashSet<>();
    private UUID target;
    private int cooldown;
    private long observed=Long.MIN_VALUE;
    private boolean facingLatched,opening;
    private int openingHold;
    public void prepareOpening(){opening=true;openingHold=12;}
    public boolean opening(){return opening;}
    public boolean counterOpening(int slot){
        if(!opening || slot!=GreeneCloseAttack.Side.LEFT.segment() || clock.seconds()<1.9F || clock.seconds()>=2.2F) return false;
        truckImpact(true);
        opening=false;cancel();cooldown=40;
        return true;
    }

    private void truckImpact(boolean interrupted){
        if(owner.level() instanceof ServerLevel level) owner.plazaTruck().ifPresent(station ->
                com.codex.blacklight.world.PumpStation.openingImpact(level,station,interrupted));
    }
    public GreeneCloseCombat(EncounterMob owner){this.owner=owner;}
    public void cancel(){owner.trackPlazaTruck(false);clock.clear();target=null;receipts.clear();owner.syncGreeneClose(null,0);}
    private double distance(LivingEntity victim){return victim.position().subtract(owner.position()).horizontalDistance()/GreeneMotion.table().sourceUnitBlocks();}
    private boolean valid(LivingEntity victim){
        return owner.enemy(victim)&&owner.isWithinRestriction(victim.blockPosition())
                &&GreeneWeaponTarget.body(victim)==victim&&owner.level().hasChunkAt(victim.blockPosition());
    }
    public void tick(){
        if(!(owner.level() instanceof ServerLevel level))return;
        long now=level.getGameTime();if(observed==now)return;observed=now;
        if(cooldown>0)cooldown--;
        var candidate=owner.getTarget();
        if(valid(candidate)&&distance(candidate)>=GreeneCloseAttack.RESET_DISTANCE)cooldown=0;
        if(!owner.greeneBattle().canAttack()||owner.move()==EncounterMove.HOWL){cancel();return;}
        if(opening && owner.broken(GreeneCloseAttack.Side.LEFT.segment())) {opening=false;cancel();}
        if(opening && owner.plazaTruck().isEmpty()) owner.trackPlazaTruck(true);
        if(opening && !clock.active()) {
            clock.begin(GreeneCloseAttack.Side.LEFT);
            target=null;receipts.clear();facingLatched=true;
            owner.greeneBattle().attackStarted();
            ModInfectedSounds.play(owner,InfectedSoundCue.TENSION);
        }
        if(!clock.active()){
            if(cooldown>0||!valid(candidate)||!GreeneWeaponTarget.visible(owner,candidate)
                    ||!GreeneCloseAttack.aiEligible(distance(candidate),!owner.broken(0),!owner.broken(1))) return;
            var offset=candidate.position().subtract(owner.position()).yRot((float)Math.toRadians(owner.yBodyRot));
            var side=Math.abs(offset.x)<.75 ? GreeneCloseAttack.choose(owner.getRandom().nextFloat())
                    : offset.x>0 ? GreeneCloseAttack.Side.LEFT : GreeneCloseAttack.Side.RIGHT;
            if(owner.broken(side.segment())) return;
            clock.begin(side);target=candidate.getUUID();receipts.clear();cooldown=40;facingLatched=false;
            ModInfectedSounds.play(owner,InfectedSoundCue.TENSION);
        }
        var entity=target==null?null:level.getEntity(target);
        var victim=entity instanceof LivingEntity living?living:null;
        if(!opening&&!valid(victim)||owner.broken(clock.side().segment())){cancel();return;}
        if(opening && clock.seconds()>=1.95F && openingHold>0) {
            openingHold--;
            owner.syncGreeneClose(clock.side(),clock.seconds()*20);
            if(openingHold==11 && owner.level() instanceof ServerLevel server){
                var tip=owner.hurtRegions(0).get(clock.side().segment()).bounds().getCenter();
                server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.CRACKED_STONE_BRICKS.defaultBlockState()),tip.x,owner.getY()+.15,tip.z,14,.6,.08,.6,.1);
                server.playSound(null,tip.x,owner.getY(),tip.z,net.minecraft.sounds.SoundEvents.DEEPSLATE_BREAK,net.minecraft.sounds.SoundSource.HOSTILE,2F,.65F);
            }
            return;
        }
        var frame=clock.advance();
        if(frame.finished()){if(opening){owner.trackPlazaTruck(false);opening=false;cooldown=40;}owner.syncGreeneClose(null,0);target=null;receipts.clear();return;}
        owner.syncGreeneClose(frame.side(),frame.elapsedSeconds()*20);
        if(opening && frame.elapsedSeconds()>=2.2F && frame.elapsedSeconds()<2.2F+GreeneCloseAttack.STEP){
            PlazaLash.strike(owner,level);
            truckImpact(false);
        }
        if(!opening) turn(victim,frame.elapsedSeconds());
        // This same composed pose drives incoming parts, the renderer and all
        // outgoing contact origins. Never use a frozen tip or target-root guess.
        owner.updateParts();var pose=GreeneMotion.pose(owner,0);
        for(var track:frame.tracks()){
            var sphere=GreeneCloseContact.sphere(frame.side(),track,pose,owner.position(),owner.yBodyRot);
            if(!GreeneCloseContact.connected(level,owner,frame.side(),track,pose,owner.position(),owner.yBodyRot,sphere))continue;
            var seen=new HashSet<UUID>();
            for(var candidateBody:level.getEntities(owner,sphere.bounds().inflate(1e-7),e->!e.isRemoved())){
                if(!clock.isCurrent(frame)||!owner.isAlive()||!owner.greeneBattle().canAttack()||owner.broken(frame.side().segment()))return;
                var receiver=CombatTargeting.resolveTarget(candidateBody);
                if(receiver==null||!seen.add(receiver.getUUID())||receipts.contains(receiver.getUUID())||!owner.enemy(receiver)
                        ||com.codex.blacklight.firearms.FirearmsCombat.protectedTarget(owner,receiver))continue;
                var body=AnimatedHurtBody.of(receiver);ProjectileSphereContact.Hit hit=null;String bone=null;
                if(body!=null){
                    for(var region:body.hurtRegions(0))for(var box:region.boxes()){
                        var at=GreeneCloseContact.contact(sphere,box);
                        if(at!=null&&GreeneCloseContact.unobstructed(level,owner,sphere,at.point())){hit=at;bone=region.bone();break;}
                    }
                }else for(var box:CombatTargeting.hitboxes(receiver)){
                    var at=GreeneCloseContact.contact(sphere,box);
                    if(at!=null&&GreeneCloseContact.unobstructed(level,owner,sphere,at.point())){hit=at;break;}
                }
                if(hit==null)continue;
                // Native collision owner marks before dispatch and shares this
                // receipt across overlapping TIP/INNER/OUTER tracks.
                receipts.add(receiver.getUUID());
                var contact=new HydraHurtContact.Contact(bone,hit.point(),hit.normal(),InfectedAttackStyle.CRUSH);
                boolean accepted=owner.damageGreeneClose(receiver,contact,track.damage()/500F);
                if(!clock.isCurrent(frame)||!owner.isAlive()||!owner.greeneBattle().canAttack()||owner.broken(frame.side().segment()))return;
                if(accepted){
                    var push=hit.point().subtract(sphere.center()).multiply(1,0,1).normalize();
                    receiver.knockback(.85*track.damage()/500F,-push.x,-push.z);
                    if(!clock.isCurrent(frame)||!owner.isAlive()||!owner.greeneBattle().canAttack()||owner.broken(frame.side().segment()))return;
                }
            }
        }
        if(clock.isCurrent(frame)&&frame.activeAfter()==0)receipts.clear();
    }
    private void turn(LivingEntity victim,float seconds){
        if(seconds>=2||owner.move()!=EncounterMove.NONE)return;
        var delta=victim.position().subtract(owner.position());
        float wanted=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        float angle=Math.abs(Mth.wrapDegrees(wanted-owner.getYRot()));
        if(facingLatched){if(angle<=15)return;facingLatched=false;}
        if(angle<10){facingLatched=true;return;}
        float speed=angle<30?3:angle<120?70:150;
        float yaw=Mth.approachDegrees(owner.getYRot(),wanted,speed/20);
        owner.setYRot(yaw);owner.yBodyRot=yaw;owner.yHeadRot=yaw;
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.put("Clock",clock.save());tag.putBoolean("PlazaOpening",opening);tag.putInt("PlazaHold",openingHold);tag.putInt("Cooldown",cooldown);tag.putBoolean("FacingLatched",facingLatched);
        if(target!=null)tag.putUUID("Target",target);
        var list=new ListTag();for(var id:receipts)list.add(StringTag.valueOf(id.toString()));tag.put("Receipts",list);return tag;
    }
    public void load(CompoundTag tag){
        cancel();cooldown=Math.max(0,Math.min(40,tag.getInt("Cooldown")));facingLatched=tag.getBoolean("FacingLatched");
        opening=tag.getBoolean("PlazaOpening");openingHold=Math.max(0,Math.min(12,tag.getInt("PlazaHold")));
        if(!opening&&!tag.hasUUID("Target"))return;clock.load(tag.getCompound("Clock"));if(!clock.active())return;
        target=tag.hasUUID("Target")?tag.getUUID("Target"):null;var list=tag.getList("Receipts",Tag.TAG_STRING);
        for(int i=0;i<Math.min(256,list.size());i++)try{receipts.add(UUID.fromString(list.getString(i)));}catch(IllegalArgumentException ignored){}
        owner.syncGreeneClose(clock.side(),clock.seconds()*20);
    }
}
