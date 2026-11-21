package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.FirearmsCombat;
import com.codex.blacklight.firearms.WeaponCatalog;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BossInteractionGameTest {
    private static Vec3 arena(GameTestHelper h) {
        var origin = h.absolutePos(new BlockPos(8, 1, 5));
        return new Vec3(origin.getX() + .5, 220, origin.getZ() + .5);
    }

    @GameTest(template="lost_convoy", batch="boss_rifle", timeoutTicks=60)
    public static void actualRifleHitFromLedgeDoesNotDeflectShooter(GameTestHelper h) {
        var level = h.getLevel();
        var shooter = SurvivalEncounterGameTest.join(h, "ledge-shooter");
        var boss = ModEntities.SUPREME_HUNTER.get().create(level);
        try {
            var at = arena(h);
            boss.setPos(at.add(0, 0, 19));
            boss.setNoAi(true);
            level.addFreshEntity(boss);
            level.setBlockAndUpdate(BlockPos.containing(at).above(2), Blocks.STONE.defaultBlockState());
            shooter.setPos(at.add(0, 3, 0));
            shooter.setOnGround(true);
            shooter.setDeltaMovement(Vec3.ZERO);
            float before = boss.getHealth();
            Vec3 muzzle = shooter.getEyePosition().add(0, -.18, 0);
            FirearmsCombat.fire(shooter, WeaponCatalog.RIFLE, boss.getBoundingBox().getCenter().subtract(muzzle).normalize(), 1);
            h.assertTrue(boss.getHealth() < before && shooter.getDeltaMovement().lengthSqr() < 1e-8,
                    "rifle deals damage at 19 blocks without pushing its grounded shooter");
            shooter.setPos(boss.position().add(1, 2, 0));
            shooter.setOnGround(false);
            h.assertTrue(boss.deflects(shooter.damageSources().playerAttack(shooter)), "nearby airborne melee still triggers a counter");
            h.assertTrue(!boss.deflects(shooter.damageSources().mobProjectile(shooter,shooter)), "an airborne rifle round is still a projectile");
        } finally {
            boss.discard();
            level.getServer().getPlayerList().remove(shooter);
        }
        h.succeed();
    }

    @GameTest(template="lost_convoy", batch="boss_skill", timeoutTicks=60)
    public static void devastatorContactAdvancesOnlyTheActualOrgan(GameTestHelper h) {
        var level = h.getLevel();
        var player = SurvivalEncounterGameTest.join(h, "organ-strike");
        var boss = EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(level);
        try {
            boss.setPos(arena(h));
            boss.setNoAi(true);
            level.addFreshEntity(boss);
            for (int i=0;i<2;i++) {
                boss.invulnerableTime=0;
                boss.hurt(level.damageSources().generic(),500);
            }
            h.assertTrue(boss.goliathWindow()==GoliathWindow.HEAD,"head is exposed");
            float damage=200*CombatScale.units(boss);
            boss.invulnerableTime=0;
            h.assertTrue(DevastatorDamage.hurt(player,boss,damage) && boss.goliathWindow()==GoliathWindow.HEAD,
                    "unlocated damage cannot advance an organ");
            var jaw = boss.hurtRegions(0).stream().filter(region->region.bone().equals("jaw")).findFirst().orElseThrow();
            Vec3 center = jaw.boxes().get(0).center;
            var contact = DevastatorCollision.skinContact(boss, center, center, .25, point->true);
            h.assertTrue(contact!=null && GoliathLockPart.forSkin(contact.bone())==GoliathLockPart.HEAD,"sphere hits the visible head");
            h.assertTrue(DevastatorCollision.skinContact(boss,center,center,.25,point->false)==null,"occluded surface is not hit");
            boss.invulnerableTime=0;
            h.assertTrue(DevastatorDamage.hurt(player,boss,damage,contact) && boss.goliathWindow()==GoliathWindow.KNEE,
                    "same devastator damage at the head advances to the knee window");
        } finally {
            boss.discard();
            level.getServer().getPlayerList().remove(player);
        }
        h.succeed();
    }

    @GameTest(template="lost_convoy", batch="hive_reward", timeoutTicks=160)
    public static void markedHivePaysExactlyOneTissueOnRecall(GameTestHelper h) {
        var level = h.getLevel();
        var player = SurvivalEncounterGameTest.join(h,"hive-absorber");
        var state = player.getData(ModAttachments.PLAYER_STATE);
        state.infect();
        state.setPrototypeMode(true);
        var at = arena(h);
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(at).offset(-3,-1,-3),BlockPos.containing(at).offset(3,-1,9)))
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        player.setPos(at);
        var hive = ModEntities.BLACKLIGHT_HIVE.get().create(level);
        hive.setPos(at.add(0,0,6));
        hive.setNoAi(true);
        level.addFreshEntity(hive);
        hive.setHealth(1);
        QuillHarvest.plant(player,hive,hive.getBoundingBox().getCenter(),0);
        h.assertTrue(QuillHarvest.available(player,hive),"wounded marked hive is available for recall");
        QuillHarvest.recall(player);
        h.startSequence().thenWaitUntil(()->{
            var drops = level.getEntitiesOfClass(ItemEntity.class,hive.getBoundingBox().inflate(8),
                    item->item.getItem().is(ModItems.LIVING_HIVE_TISSUE.get()));
            h.assertTrue(!hive.isAlive() && drops.stream().mapToInt(item->item.getItem().getCount()).sum()==1
                    && SurvivalEncounterGameTest.done(player,"hive_breaker"), "recall publishes the hive death, tissue and milestone");
        }).thenExecute(()->level.getServer().getPlayerList().remove(player)).thenSucceed();
    }
}
