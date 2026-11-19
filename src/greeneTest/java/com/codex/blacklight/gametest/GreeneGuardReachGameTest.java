package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.EncounterEntities;
import com.mojang.authlib.GameProfile;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneGuardReachGameTest {
    private static EncounterMob spawn(GameTestHelper h){
        var level=h.getLevel();
        var mother=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        var guard=mother.greeneBattle().save();
        guard.putBoolean("OpeningPending",false);
        mother.greeneBattle().load(guard);
        mother.setNoAi(true);
        mother.setNoGravity(true);
        mother.setPos(h.absoluteVec(new Vec3(8,24,8)));
        mother.yBodyRot=mother.yBodyRotO=0;
        level.addFreshEntity(mother);
        mother.updateParts();
        return mother;
    }
    private static float armor(EncounterMob mother,int slot){
        var tag=new CompoundTag();
        mother.saveWithoutId(tag);
        return tag.getFloat("EncounterArmor"+slot);
    }
    @GameTest(template="blackwatch_arena",batch="greene_guard_reach",timeoutTicks=45)
    public static void gapsInMotherDoNotPushAnApproachingGroundActor(GameTestHelper h){
        var mother=spawn(h);
        var pig=EntityType.PIG.create(h.getLevel());
        var point=mother.position().add(3.7,0,3.7);
        pig.setPos(point);
        pig.setNoAi(true);pig.setNoGravity(true);h.getLevel().addFreshEntity(pig);
        try{
            h.assertTrue(mother.getBoundingBox().intersects(pig.getBoundingBox()),"ground actor is inside the vanilla root");
            h.assertTrue(mother.hurtRegions(0).stream().flatMap(r->r.boxes().stream()).noneMatch(b->b.touches(pig.getBoundingBox())),"walkable corner has no authored flesh");
            pig.setDeltaMovement(Vec3.ZERO);
            mother.aiStep();
            h.assertTrue(pig.getDeltaMovement().horizontalDistanceSqr()<1e-10,"vanilla crowd step must not push from empty air");
        }finally{mother.discard();pig.discard();}
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="greene_guard_aim",timeoutTicks=45)
    public static void everyLastSlugCanBeSelectedAndStruckFromGroundHeight(GameTestHelper h){
        var level=h.getLevel();
        var player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"GreeneReach"));
        player.setGameMode(GameType.SURVIVAL);
        level.addNewPlayer(player);
        player.setItemInHand(InteractionHand.MAIN_HAND,FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance());
        try{
            for(int slot=0;slot<3;slot++){
                var mother=spawn(h);
                try{
                    for(int i=0;i<3;i++)if(i!=slot)h.assertTrue(mother.hurtPart(i,level.damageSources().generic(),1e7F),"fixture detached competing Slug");
                    mother.updateParts();
                    var selected=new TargetSelection(mother,slot);
                    h.assertTrue(TargetSelection.choices(player,mother).equals(List.of(selected)),"only remaining guard is selectable: "+slot);
                    boolean struck=false;
                    for(int i=0;i<20&&!struck;i++){
                        double angle=Math.PI*i/10;
                        player.setPos(mother.position().add(3.8*Math.cos(angle),0,3.8*Math.sin(angle)));
                        if(!selected.visibleWithinRange(player,3))continue;
                        Vec3 eye=player.getEyePosition(),aim=selected.point(player);
                        var direction=aim.subtract(eye).normalize();
                        var end=eye.add(direction.scale(3));
                        var skin=mother.hurtRegions(0);
                        var ray=HydraHurtContact.ray(skin,eye,end,InfectedAttackStyle.PIERCE);
                        h.assertTrue(ray!=null&&ray.bone().equals("weak_"+slot),"advertised close focus intercepts this Slug first: "+slot+" yawStep="+i+" distance="+eye.distanceTo(aim)+" actual="+(ray==null?"miss":ray.bone()));
                        h.assertTrue(selected.equals(TargetSelection.aimedBoss(player,List.of(mother),eye,end,0,3)),"real crosshair pick owns same slot");
                        var partHit=ProjectileUtil.getEntityHitResult(player,eye,end,player.getBoundingBox().expandTowards(direction.scale(3)).inflate(1),Entity::isPickable,9);
                        if(partHit==null||!(partHit.getEntity() instanceof GreenePart part)||part.slot!=slot)continue;
                        player.setYRot((float)Math.toDegrees(Math.atan2(-direction.x,direction.z)));
                        player.setXRot((float)-Math.toDegrees(Math.asin(direction.y)));
                        CombatTargeting.requestLock(player,mother.getId(),slot);
                        h.assertTrue(selected.equals(CombatTargeting.lockedSelection(player)),"accepted explicit server lock");
                        float before=armor(mother,slot);
                        h.assertTrue(part.hurt(player.damageSources().playerAttack(player),3)&&armor(mother,slot)<before,"native melee damages the actual selected receiver");
                        // Exercise the same swept fist/hammer contact used by a charged weapon session.
                        var bounds=new LocalWeaponMotion.ContactBounds(-.55,.15,.1,.55,2.3,3);
                        var hit=HydraHurtContact.weapon(mother,player,WeaponCutContact.Report.NONE,false,bounds,player.position(),
                                player.getYRot(),player.getYRot(),false,3,direction,100);
                        h.assertTrue(hit!=null&&hit.bone().equals("weak_"+slot),"ground heavy sweep reaches same guard: "+slot);
                        struck=true;
                    }
                    h.assertTrue(struck,"native melee acquisition within three blocks for final Slug "+slot);
                }finally{CombatTargeting.requestLock(player,-1);mother.discard();}
            }
        }finally{level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.succeed();
    }
}
