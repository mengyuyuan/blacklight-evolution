package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Exercises natural acquisition/selection and real socket release, not begin(program). */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GreeneDistanceGameTest {
    // Greene's real target goal scans 175 blocks. Do not run another fixture's
    // targets alongside this one or replace the production acquisition goal.
    @GameTest(template="blackwatch_arena",batch="greene_distance_shotgun",timeoutTicks=100)
    public static void middleDistanceChoosesSourceShotgunInsteadOfSlowSwarm(GameTestHelper h){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<20;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        var battle=boss.greeneBattle().save();battle.putBoolean("OpeningPending",false);boss.greeneBattle().load(battle);
        boss.setPos(h.absoluteVec(new Vec3(1,2,1)));boss.setNoGravity(true);boss.setPersistenceRequired();
        h.getLevel().addFreshEntity(boss);
        var target=h.spawn(EntityType.PIG,new Vec3(15,2,15));target.setNoAi(true);target.setNoGravity(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.assertTrue(boss.getTarget()==null,"fixture must start before natural acquisition");
        double blocks=target.position().subtract(boss.position()).horizontalDistance();
        h.assertTrue(blocks>19&&blocks<20,"fixture lies in the old erroneous swarm band");
        double sourceDistance=blocks/GreeneMotion.table().sourceUnitBlocks();
        h.assertTrue(sourceDistance>25&&sourceDistance<40,"actual source-unit band is shotgun");
        boolean[] selected={false},done={false};
        h.onEachTick(()->{
            if(done[0])return;
            if(boss.move()==EncounterMove.NONE)return;
            h.assertTrue(boss.move()==EncounterMove.VOLLEY&&boss.greeneProgram()==GreeneWeaponProgram.SHOTGUN,
                    "natural FightGoal must convert world distance before choosing source program");
            h.assertTrue(boss.getTarget()==target,"natural acquisition must retain the fixture target; actual="+boss.getTarget()
                    +", actualBlocks="+(boss.getTarget()==null?"none":boss.distanceTo(boss.getTarget()))
                    +", fixtureBlocks="+boss.distanceTo(target));
            selected[0]=true;
            if(boss.greeneShots()==0){h.assertTrue(boss.moveAge(0)<=8,"shotgun must release at its source first-fire window");return;}
            h.assertTrue(selected[0]&&boss.moveAge(0)<=8,"real socket release follows shotgun timing");
            done[0]=true;
            for(var shot:h.getLevel().getEntitiesOfClass(GreeneProjectile.class,boss.getBoundingBox().inflate(40),p->p.getOwner()==boss))shot.discard();
            boss.discard();target.discard();h.succeed();
        });
    }
}
