package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.firearms.*;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehiclePermissionGameTest {
    private static ServerPlayer prototype(GameTestHelper h,MilitaryVehicleGameTest.Fixture f){
        var p=MilitaryVehicleGameTest.mockPlayer(h);f.entities.add(p);p.setPos(f.x+.5,81,-4);
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);return p;
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6permissions",timeoutTicks=100)
    public static void prototypeFirearmsReachRealHullWithoutUnlockingManualVanillaAttack(GameTestHelper h){
        var f=new MilitaryVehicleGameTest.Fixture(h,210000);
        VehicleTestReadiness.run(h,f,()->{
            var tank=ModEntities.MILITARY_TANK.get().create(f.level);tank.setPos(f.x+.5,81,7);f.level.addFreshEntity(tank);f.entities.add(tank);var p=prototype(h,f);
            boolean queryable=f.level.getEntitiesOfClass(com.codex.blacklight.entity.MilitaryVehicle.class,tank.getBoundingBox()).contains(tank);
            h.assertTrue(p.getY()==81 && queryable,"prototype shooter on loaded road and hull queryable: position="+p.position()+" queryable="+queryable);
            float before=tank.health();FirearmsCombat.fire(p,WeaponCatalog.RIFLE,new Vec3(0,0,1),1);
            h.assertTrue(Math.abs((before-tank.health())-.4F)<.001,"actual prototype firearm hit can damage a mechanical chassis through the scoped protection event");
            var event=new net.minecraftforge.event.entity.player.AttackEntityEvent(p,tank);
            com.codex.blacklight.mode.PrototypeSurvivalLock.onVanillaAttack(event);
            h.assertTrue(event.isCanceled(),"leaving scoped fire does not unlock a forged or manual ordinary attack");
        });
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6permissions",timeoutTicks=100)
    public static void actualPrototypeGunBreaksGlassWhileManualBlockBreakingStaysLocked(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,211000)){
            var p=prototype(h,f);var at=new BlockPos(f.x,82,4);f.level.setBlock(at,Blocks.GLASS.defaultBlockState(),3);
            FirearmBlockDamage.bullet(p,WeaponCatalog.RIFLE,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false));
            h.assertTrue(f.level.getBlockState(at).isAir(),"real prototype firearm block impact must pass its own narrow event gate");
            var event=new net.minecraftforge.event.level.BlockEvent.BreakEvent(f.level,at,Blocks.STONE.defaultBlockState(),p);
            com.codex.blacklight.mode.PrototypeSurvivalLock.onBreakBlock(event);
            h.assertTrue(event.isCanceled(),"manual survival mining remains locked after the actual firearm impact");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6permissions",timeoutTicks=100)
    public static void realAiFireRespectsMobGriefingForBothBulletsAndVehicleExplosives(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,212000)){
            var actor=ModEntities.MILITARY_SOLDIER.get().create(f.level);actor.setNoAi(true);actor.setPos(f.x,81,0);f.level.addFreshEntity(actor);f.entities.add(actor);
            var rule=f.level.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);boolean before=rule.get();
            try{
                rule.set(false,f.level.getServer());var at=new BlockPos(f.x,82,4);f.level.setBlock(at,Blocks.GLASS.defaultBlockState(),3);
                FirearmBlockDamage.bullet(actor,WeaponCatalog.RIFLE,new BlockHitResult(Vec3.atCenterOf(at),Direction.NORTH,at,false));
                FirearmBlockDamage.blast(f.level,actor,com.codex.blacklight.military.vehicle.VehicleWeaponDefinition.require("tank_he").weapon(),Vec3.atCenterOf(at));
                h.assertTrue(f.level.getBlockState(at).is(Blocks.GLASS),"actual AI bullet and vehicle blast cannot destroy a block when mobGriefing is false");
            }finally{rule.set(before,f.level.getServer());}
        }h.succeed();
    }
}
