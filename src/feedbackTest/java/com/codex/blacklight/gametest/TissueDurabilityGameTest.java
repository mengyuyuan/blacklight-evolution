package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TissueDurabilityGameTest {
    private static BlacklightInfectedHost host(GameTestHelper h,TissueCore core,long age){
        var mob=ModEntities.INFECTED_HOST.get().create(h.getLevel());mob.inherit(EntityType.PIG.create(h.getLevel()));
        long seed=0;while(TissueCore.forSeed(seed)!=core)seed++;
        long identity=UUID.randomUUID().getMostSignificantBits();mob.setUUID(new UUID(identity,identity^seed));
        mob.setNoAi(true);mob.setPos(h.absolutePos(new BlockPos(5,25,5)).getCenter());
        h.getLevel().addFreshEntity(mob);mob.advanceTissue(age);mob.setHealth(mob.getMaxHealth());return mob;
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_durability")
    public static void matureAndOldOrgansSurviveRealPlayerCombosButStillDie(GameTestHelper h){
        var player=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"HostDurability"));
        var state=player.getData(ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);state.activate(PowerForm.CLAWS);
        for(var core:TissueCore.values()){
            int previous=0;
            for(long age:new long[]{12000,240000,816000}){
                var mob=host(h,core,age);
                try{
                    int hits=0;float initial=mob.getMaxHealth();
                    while(mob.isAlive()&&hits<100){
                        WeaponMove move=new WeaponMove[]{WeaponMove.CLAW_AUTO1,WeaponMove.CLAW_AUTO2,WeaponMove.CLAW_AUTO3}[hits%3];
                        mob.invulnerableTime=0;mob.hurt(mob.damageSources().playerAttack(player),move.damageFor(PowerForm.CLAWS));hits++;
                    }
                    h.assertTrue(hits>=4&&hits>previous&&hits<40&&!mob.isAlive(),"age and primary organs must support a longer, finite fight; core="+core+", age="+age+", hits="+hits);
                    System.out.println("TISSUE_DURABILITY core="+core+" age="+age+" hp="+initial+" comboHits="+hits);
                    previous=hits;
                }finally{mob.discard();}
            }
        }
        var ordinary=EntityType.PIG.create(h.getLevel());h.assertTrue(ordinary.getMaxHealth()==10,"uninfected survival mobs retain vanilla health");ordinary.discard();
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40,batch="tissue_durability")
    public static void strongerOldSavesPreserveInjuryAndAdministrativeKillsStillWork(GameTestHelper h){
        var mob=host(h,TissueCore.CARAPACE,816000);
        try{
            mob.setHealth(mob.getMaxHealth()*.35F);var tag=new CompoundTag();mob.saveWithoutId(tag);
            var restored=ModEntities.INFECTED_HOST.get().create(h.getLevel());
            try{
                restored.load(tag);restored.load(tag);
                h.assertTrue(Math.abs(restored.getHealth()/restored.getMaxHealth()-.35F)<.00001,"stronger tissue never resets saved injury");
                restored.hurt(restored.damageSources().genericKill(),Float.MAX_VALUE);
                h.assertTrue(!restored.isAlive(),"durability is ordinary health/armour, not unkillable interception");
            }finally{restored.discard();}
        }finally{mob.discard();}h.succeed();
    }
}
