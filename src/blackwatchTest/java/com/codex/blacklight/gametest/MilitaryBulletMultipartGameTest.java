package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.military.vehicle.VehicleDamageBridge;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryBulletMultipartGameTest {
    @GameTest(template="blackwatch_arena", timeoutTicks=40, batch="ordinary_bullet_pvp")
    public static void ordinaryBulletHonorsServerPvpWithoutRemovingPlayerCollision(GameTestHelper h) {
        var shooter=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BulletPvpShooter"));
        var target=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"BulletPvpTarget"));
        shooter.setGameMode(GameType.SURVIVAL);target.setGameMode(GameType.SURVIVAL);
        shooter.setPos(h.absoluteVec(new Vec3(5,4,3)));target.setPos(h.absoluteVec(new Vec3(5,4,8)));
        shooter.setNoGravity(true);target.setNoGravity(true);
        h.getLevel().addNewPlayer(shooter);h.getLevel().addNewPlayer(target);
        var server=h.getLevel().getServer();boolean pvp=server.isPvpAllowed();
        try {
            h.assertTrue(FirearmsCombat.bulletCandidate(shooter,target),"PvP-disabled players remain physical bullet blockers");
            float before=target.getHealth();server.setPvpAllowed(false);
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,new Vec3(0,0,1),1);
            h.assertTrue(target.getHealth()==before,"Server PvP flag prevents player damage");
            server.setPvpAllowed(true);target.invulnerableTime=0;
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,new Vec3(0,0,1),1);
            h.assertTrue(target.getHealth()<before,"Same real ray can damage a lawful PvP target");
        } finally {
            server.setPvpAllowed(pvp);
            h.getLevel().removePlayerImmediately(shooter,Entity.RemovalReason.DISCARDED);
            h.getLevel().removePlayerImmediately(target,Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }
    private record Ray(Vec3 from, Vec3 direction, HydraHurtContact.Contact contact) {}
    private static Ray ray(AnimatedHurtBody body, String bonePrefix) {
        var regions = body.hurtRegions(0);
        for (var region : regions) if (region.bone().startsWith(bonePrefix)) for (var box : region.boxes())
            for (var direction : new Vec3[]{new Vec3(0,0,1), new Vec3(0,0,-1), new Vec3(1,0,0), new Vec3(-1,0,0)}) {
                var from = box.center.subtract(direction.scale(10));
                var hit = HydraHurtContact.ray(regions, from, box.center.add(direction.scale(10)), InfectedAttackStyle.PIERCE);
                if (hit != null && hit.bone().equals(region.bone())) return new Ray(from, direction, hit);
            }
        throw new AssertionError("No exposed real surface for " + bonePrefix);
    }
    private static void placeShooter(LivingEntity shooter, Ray ray) {
        shooter.setPos(ray.from.x, ray.from.y - shooter.getEyeHeight() + .18, ray.from.z);
        shooter.setNoGravity(true);
    }
    private static MilitarySoldier soldier(GameTestHelper h, Ray ray) {
        var soldier = ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());
        soldier.setNoAi(true); placeShooter(soldier, ray); h.getLevel().addFreshEntity(soldier); return soldier;
    }
    private static BlacklightHydra hydra(GameTestHelper h) {
        var hydra = ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());
        hydra.setNoAi(true); hydra.setNoGravity(true); hydra.setPos(h.absoluteVec(new Vec3(8,4,8)));
        hydra.updateParts(); h.getLevel().addFreshEntity(hydra); return hydra;
    }
    private static void assertPartPicked(GameTestHelper h, LivingEntity shooter, LivingEntity parent, Ray ray) {
        var end = ray.from.add(ray.direction.scale(WeaponCatalog.RIFLE.range()));
        var hit = ProjectileUtil.getEntityHitResult(h.getLevel(), shooter, ray.from, end, new AABB(ray.from,end).inflate(.3),
                e -> FirearmsCombat.bulletCandidate(shooter,e));
        h.assertTrue(hit != null && hit.getEntity() != parent && CombatTargeting.resolveTarget(hit.getEntity()) == parent,
                "Real ordinary-bullet candidate query selects the visible part and resolves its living parent");
        h.assertTrue(hit.getLocation().distanceTo(ray.contact.point()) < .002, "Proxy AABB is narrowed to the visible surface");
    }
    @GameTest(template="blackwatch_arena", timeoutTicks=40, batch="military_hydra_bullet")
    public static void soldierBulletReachesHydrasActualAnimatedSurface(GameTestHelper h) {
        var target = hydra(h); var ray = ray(target,""); var shooter = soldier(h,ray);
        try {
            assertPartPicked(h,shooter,target,ray);
            float before = target.getHealth(); int effects = target.incomingEffects();
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,ray.direction,1);
            h.assertTrue(target.getHealth()<before && target.incomingEffects()>effects, "Actual soldier bullet hurts Hydra and confirms a receiving contact");
            h.assertTrue(target.incomingPoint().distanceTo(ray.contact.point())<.002, "Damage keeps the actual hitscan point");
        } finally { shooter.discard(); target.discard(); }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena", timeoutTicks=40, batch="military_greene_bullet")
    public static void soldierBulletDamagesOnlyTheContactedGreeneAppendage(GameTestHelper h) {
        var target = EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        // Explicit existing encounter fixture; fresh opening has its own regression.
        var standing=target.greeneBattle().save();standing.putBoolean("OpeningPending",false);target.greeneBattle().load(standing);

        target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(8,4,8)));
        target.updateParts(); h.getLevel().addFreshEntity(target); var ray = ray(target,"weak_"); var shooter = soldier(h,ray);
        try {
            assertPartPicked(h,shooter,target,ray);
            var before = target.saveWithoutId(new net.minecraft.nbt.CompoundTag()); float health=target.getHealth();
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,ray.direction,1);
            var after = target.saveWithoutId(new net.minecraft.nbt.CompoundTag()); int slot=Integer.parseInt(ray.contact.bone().substring(5));
            for(int i=0;i<3;i++)h.assertTrue(i==slot?after.getFloat("EncounterArmor"+i)<before.getFloat("EncounterArmor"+i):
                    after.getFloat("EncounterArmor"+i)==before.getFloat("EncounterArmor"+i), "Only contacted appendage takes bullet damage");
            h.assertTrue(target.getHealth()==health, "Protected core health is not substituted for appendage damage");
        } finally { shooter.discard(); target.discard(); }
        h.succeed();
    }
    @GameTest(template="blackwatch_arena", timeoutTicks=40, batch="multipart_occludes_vehicle")
    public static void multipartAndAlliedParentStopBulletsBeforeRearVehicle(GameTestHelper h) {
        var target=hydra(h); var ray=ray(target,"");
        ServerPlayer shooter=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"MultipartBulletQA"));
        shooter.setGameMode(GameType.SURVIVAL); placeShooter(shooter,ray); h.getLevel().addNewPlayer(shooter);
        var tank=ModEntities.BLACKWATCH_TANK.get().create(h.getLevel());
        Vec3 rear=ray.contact.point().add(ray.direction.scale(16)); tank.setPos(rear.x,ray.from.y-1.5,rear.z);
        tank.setNoGravity(true); h.getLevel().addFreshEntity(tank);
        for(int distance=0;distance<=40;distance+=4)h.getLevel().getChunkAt(BlockPos.containing(ray.from.add(ray.direction.scale(distance))));
        var scoreboard=h.getLevel().getScoreboard(); var team=scoreboard.addPlayerTeam("mb"+UUID.randomUUID().toString().substring(0,10));
        try {
            var hull=VehicleDamageBridge.nearest(h.getLevel(),shooter,null,ray.from,ray.from.add(ray.direction.scale(WeaponCatalog.RIFLE.range())));
            h.assertTrue(hull!=null&&hull.vehicle()==tank,"Rear vehicle is a real intersected mechanical hull");
            float tankHealth=tank.health(),bodyHealth=target.getHealth();
            h.assertTrue(!VehicleDamageBridge.interceptOrdinaryBullet(shooter,WeaponCatalog.RIFLE,ray.direction,1),"Earlier boss surface prevents rear hull interception");
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,ray.direction,1);
            h.assertTrue(target.getHealth()<bodyHealth&&tank.health()==tankHealth,"Enemy boss takes the first bullet and rear hull stays intact");
            scoreboard.addPlayerToTeam(shooter.getScoreboardName(),team); scoreboard.addPlayerToTeam(target.getScoreboardName(),team);
            target.invulnerableTime=0;bodyHealth=target.getHealth();
            h.assertTrue(FirearmsCombat.protectedTarget(shooter,target),"Friendly policy is resolved on the parent");
            FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,ray.direction,1);
            h.assertTrue(target.getHealth()==bodyHealth&&tank.health()==tankHealth,"Friendly multipart parent stops the shot without damage or penetration");
            target.discard(); FirearmsCombat.fire(shooter,WeaponCatalog.RIFLE,ray.direction,1);
            h.assertTrue(tank.health()<tankHealth,"Removing the blocker makes the same rear hull shot reachable");
        } finally {
            scoreboard.removePlayerTeam(team); target.discard(); tank.discard();
            h.getLevel().removePlayerImmediately(shooter,Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }
}
