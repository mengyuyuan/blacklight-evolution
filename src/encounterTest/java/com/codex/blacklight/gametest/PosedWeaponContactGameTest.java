package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class PosedWeaponContactGameTest {
    private static ServerPlayer fighter(GameTestHelper h) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"WeaponSurfaceQA"));
        p.setGameMode(GameType.SURVIVAL);
        p.setPos(h.absoluteVec(new Vec3(8,22,8)));
        h.getLevel().addNewPlayer(p);
        return p;
    }

    private static void swing(ServerPlayer p, PowerForm form, Vec3 from, Vec3 aim) {
        WeaponCombatSystem.cancel(p);
        var state=p.getData(ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);state.unlock(form);state.activate(form);
        p.setData(ModAttachments.PLAYER_STATE,state);
        p.setPos(from);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);
        var delta=aim.subtract(p.position());
        float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
        p.setYRot(yaw);p.setYHeadRot(yaw);p.yBodyRot=p.yBodyRotO=yaw;p.setXRot(0);
        WeaponCombatSystem.request(p,false);
        var move=p.getData(ModAttachments.WEAPON_VISUAL).spec();
        if(move==null)throw new IllegalStateException("ground weapon request failed for "+form);
        for(int i=0;i<=move.duration+4 && WeaponCombatSystem.busy(p);i++){
            p.tickCount++;
            WeaponCombatSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
        }
        WeaponCombatSystem.cancel(p);
    }

    private static EncounterMob down(GameTestHelper h,float yaw) {
        var m=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        m.setPos(h.absoluteVec(new Vec3(8,22,8)));
        m.setYRot(yaw);m.yBodyRot=m.yBodyRotO=yaw;
        var record=new CompoundTag();m.saveWithoutId(record);
        record.putInt("EncounterBroken",7);
        var cycle=record.getCompound("GreeneBattle");
        cycle.putBoolean("OpeningPending",false);cycle.putInt("Stage",GreeneBattleCycle.DOWN);
        cycle.putInt("Age",25);cycle.putInt("RecoveryRemaining",200);
        cycle.putFloat("MinimumHealth",0);cycle.putFloat("InitialStunnedHealth",m.getMaxHealth());
        cycle.putBoolean("CoreDamageable",true);
        if(yaw==0)cycle.putFloat("CollapseYaw",yaw);
        else cycle.remove("CollapseYaw"); // Existing DOWN save from before the heading was persisted.
        m.load(record);m.setNoAi(true);m.setNoGravity(true);
        m.setYRot(yaw);m.yBodyRot=m.yBodyRotO=yaw;
        h.getLevel().addFreshEntity(m);
        m.updateParts();
        return m;
    }

    private static void downedSwings(GameTestHelper h,float yaw) {
        var player=fighter(h);
        var mob=down(h,yaw);
        var skin=mob.hurtRegions(0);
        // Local body/look control can turn toward a passing player during DOWN.
        // Recreate the disagreement that used to put the receiving head behind the visible one.
        mob.yBodyRot=mob.yBodyRotO=yaw+180;
        var head=skin.stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow();
        var after=mob.hurtRegions(0).stream().filter(r->r.bone().equals("head")).findFirst().orElseThrow();
        h.assertTrue(head.bounds().getCenter().distanceToSqr(after.bounds().getCenter())<.00001,
                "a local look turn cannot move collision away from the fallen head");
        var misses=new ArrayList<String>();
        try {
            for(var form:List.of(PowerForm.BLADE,PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.MUSCLE_MASS,PowerForm.WHIPFIST)){
                for(var side:List.of("front","side","rear")){
                    var outward=switch(side){case "front"->new Vec3(0,0,1);case "side"->new Vec3(1,0,0);default->new Vec3(0,0,-1);};
                    outward=outward.yRot((float)Math.toRadians(-yaw));
                    Vec3 local=switch(side){case "front"->new Vec3(1.2,1.0,24);case "side"->new Vec3(24,1.0,6.5);default->new Vec3(.8,1.0,-24);};
                    Vec3 eye=mob.position().add(local.yRot((float)Math.toRadians(-yaw)));
                    var contact=HydraHurtContact.ray(skin,eye,eye.subtract(outward.scale(48)),com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
                    h.assertTrue(contact!=null,"fixture ray hits visible "+side+" skin");
                    var feet=contact.point().add(outward.scale(form==PowerForm.WHIPFIST?3.5:1.05));
                    feet=new Vec3(feet.x,mob.getY(),feet.z);
                    mob.invulnerableTime=0;float before=mob.getHealth();
                    swing(player,form,feet,contact.point());
                    if(mob.getHealth()>=before)misses.add(side+"/"+form);
                    mob.setHealth(mob.getMaxHealth());
                }
            }
        }finally{
            WeaponCombatSystem.cancel(player);mob.discard();h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
        }
        h.assertTrue(misses.isEmpty(),"grounded weapon contacts missed "+misses+" at yaw "+yaw);
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="posed_weapon_receivers",timeoutTicks=100)
    public static void weaponsHitPosedSkinOnHydraFlyerAndGoliath(GameTestHelper h){
        var player=fighter(h);
        var misses=new ArrayList<String>();
        try{
            for(EntityType<? extends Mob> type:List.of(ModEntities.BLACKLIGHT_HYDRA.get(),
                    EncounterEntities.CREATURES.get(EncounterKind.FLYER).get(),
                    EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get())){
                var mob=type.create(h.getLevel());
                try{
                    mob.setPos(h.absoluteVec(new Vec3(8,22,8)));
                    mob.setNoAi(true);mob.setNoGravity(true);mob.setYRot(0);mob.yBodyRot=mob.yBodyRotO=0;
                    if(mob instanceof BlacklightHydra hydra)hydra.setBodySize(1.6F);
                    h.getLevel().addFreshEntity(mob);
                    var body=(AnimatedHurtBody)mob;
                    var skin=body.hurtRegions(0);
                    // Stand outside the foremost cube at its actual height.
                    var front=skin.stream().flatMap(r->r.boxes().stream())
                            .max(Comparator.comparingDouble(b->b.center.z)).orElseThrow().center;
                    var hit=HydraHurtContact.ray(skin,front.add(0,0,20),front.add(0,0,-2),
                            com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
                    h.assertTrue(hit!=null,"exposed posed skin exists");
                    for(var form:List.of(PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST)){
                        float before=mob.getHealth();mob.invulnerableTime=0;
                        swing(player,form,hit.point().add(0,-1.1,form==PowerForm.WHIPFIST?3.5:1.0),hit.point());
                        if(mob.getHealth()>=before)misses.add(type.getDescriptionId()+"/"+form);
                        mob.setHealth(mob.getMaxHealth());
                    }
                }finally{mob.discard();}
            }
        }finally{WeaponCombatSystem.cancel(player);h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.assertTrue(misses.isEmpty(),"missed posed surface "+misses);h.succeed();
    }

    @GameTest(template="blackwatch_arena",batch="greene_weapon_cover",timeoutTicks=90)
    public static void wallAndClearAirCannotDamageDownedGreene(GameTestHelper h){
        var player=fighter(h);var mob=down(h,0);
        var front=mob.position().add(1.2,1,24);
        var surface=HydraHurtContact.ray(mob.hurtRegions(0),front,front.add(0,0,-40),
                com.codex.blacklight.infection.InfectedAttackStyle.TEAR).point();
        var wall=net.minecraft.core.BlockPos.containing(surface.add(0,0,1.5));
        var replaced=new HashMap<net.minecraft.core.BlockPos,net.minecraft.world.level.block.state.BlockState>();
        try{
            for(int x=-5;x<=5;x++)for(int y=-3;y<=5;y++){
                var pos=wall.offset(x,y,0);replaced.put(pos,h.getLevel().getBlockState(pos));
                h.getLevel().setBlock(pos,net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),2);
            }
            var from=new Vec3(surface.x,mob.getY(),wall.getZ()+1.6);
            for(var form:List.of(PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST)){
                var hp=mob.getHealth();swing(player,form,from,surface);
                h.assertTrue(mob.getHealth()==hp,"solid wall stops "+form);
                swing(player,form,from.add(0,0,64),surface);
                h.assertTrue(mob.getHealth()==hp,"out of reach "+form);
            }
        }finally{
            replaced.forEach((pos,state)->h.getLevel().setBlock(pos,state,2));
            WeaponCombatSystem.cancel(player);mob.discard();h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    @GameTest(template="blackwatch_arena",batch="mixed_weapon_receivers",timeoutTicks=100)
    public static void weaponInputsHitHumanoidAnimalAndCustomMobBoxes(GameTestHelper h){
        var player=fighter(h);
        Vec3 home=h.absoluteVec(new Vec3(8,22,8));
        var misses=new ArrayList<String>();
        try{
            for(EntityType<? extends Mob> type:List.of(EntityType.ZOMBIE,EntityType.COW,EntityType.IRON_GOLEM,
                    ModEntities.BLACKLIGHT_HUNTER.get(),ModEntities.BLACKLIGHT_STALKER.get(),ModEntities.BLACKLIGHT_BRUTE.get())){
                for(var form:List.of(PowerForm.BLADE,PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.WHIPFIST)){
                    var mob=type.create(h.getLevel());
                    try{
                        mob.setPos(home);mob.setNoAi(true);mob.setNoGravity(true);
                        mob.setYRot(47);mob.yBodyRot=mob.yBodyRotO=47;
                        h.getLevel().addFreshEntity(mob);
                        float before=mob.getHealth();
                        double gap=form==PowerForm.WHIPFIST?3.5:1.0;
                        var from=home.add(0,0,mob.getBbWidth()*.5+gap);
                        swing(player,form,from,home);
                        if(mob.getHealth()>=before)misses.add(type.getDescriptionId()+"/"+form);
                    }finally{mob.discard();}
                }
            }
        }finally{WeaponCombatSystem.cancel(player);h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);}
        h.assertTrue(misses.isEmpty(),"missed actual grounded weapon inputs "+misses);
        h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="greene_contact_faces",timeoutTicks=100)
    public static void downedGreeneTakesGroundWeaponHitsAcrossItsPosedBody(GameTestHelper h){downedSwings(h,0);}
    @GameTest(template="blackwatch_arena",batch="greene_contact_turn",timeoutTicks=100)
    public static void turningDownedGreeneRotatesItsReachableSkin(GameTestHelper h){downedSwings(h,90);}
}
