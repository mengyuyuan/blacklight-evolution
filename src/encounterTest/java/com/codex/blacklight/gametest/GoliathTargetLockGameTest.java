package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.EncounterEntities;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoliathTargetLockGameTest {
    private record Fixture(GameTestHelper h, EncounterMob mob, ServerPlayer player) implements AutoCloseable {
        static Fixture open(GameTestHelper h) {
            var level = h.getLevel();
            var mob = EncounterEntities.CREATURES.get(EncounterKind.GOLIATH).get().create(level);
            mob.setPos(h.absoluteVec(new Vec3(8, 24, 8)));
            mob.setNoAi(true);
            mob.setNoGravity(true);
            level.addFreshEntity(mob);
            mob.yBodyRot = mob.yBodyRotO = 0;
            mob.updateParts();
            var player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "GoliathLockQA"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(mob.position().add(0, 2, -20));
            level.addNewPlayer(player);
            var rifle = FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance();
            FirearmItem.store(rifle, new AmmoState(30, 0, 0));
            player.setItemInHand(InteractionHand.MAIN_HAND, rifle);
            return new Fixture(h, mob, player);
        }

        public void close() {
            CombatTargeting.requestLock(player, -1);
            h.getLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            mob.discard();
        }
    }

    @GameTest(template="blackwatch_arena", batch="goliath_lock_regions", timeoutTicks=50)
    public static void threeSourceJointsRetainIndependentServerAndWeaponAim(GameTestHelper h) {
        try (var f = Fixture.open(h)) {
            var mob = f.mob;
            var player = f.player;
            var choices = TargetSelection.choices(player, mob);
            h.assertTrue(choices.size() == 3, "three original subtargets on one living owner");
            var focused = new HashSet<Vec3>();
            var skin = mob.hurtRegions(0);
            for (var selected : choices) {
                var part = selected.goliathPart();
                CombatTargeting.requestLock(player, mob.getId(), selected.region());
                h.assertTrue(selected.equals(CombatTargeting.lockedSelection(player)), "server accepts " + part);
                var point = selected.point(player);
                focused.add(point);
                h.assertTrue(skin.stream().filter(r -> GoliathLockPart.forSkin(r.bone()) == part)
                        .flatMap(r -> r.boxes().stream()).anyMatch(b -> b.contains(point)), "aim inside animated " + part);
                h.assertTrue(CombatTargeting.aimPoint(player, player.position(), mob).distanceToSqr(point) < 1e-7,
                        "melee approaches the selected joint");
                var start = player.getEyePosition().add(0, -.18, 0);
                var direction = FirearmAim.direction(player, WeaponCatalog.RIFLE);
                h.assertTrue(direction.dot(point.subtract(start).normalize()) > .99999, "server rifle aims at " + part);
                h.assertTrue(selected.nextVisible(player, 64).equals(choices.get((selected.region()+1)%3)), "cycle wraps");
                mob.yBodyRot = 90;
                h.assertTrue(selected.point(player).distanceToSqr(point) > .1, "joint follows body rotation");
                mob.yBodyRot = 0;
            }
            h.assertTrue(focused.size() == 3, "three distinct focus points");
            Set<GoliathLockPart> direct = new HashSet<>();
            for (int i=0; i<mob.getParts().length; i++) {
                var selection = TargetSelection.fromHit(mob.getParts()[i]);
                var bone = skin.get(i).bone();
                if (bone.equals("head") || bone.equals("right_shin") || bone.equals("left_forearm")) {
                    var expected = bone.equals("head") ? GoliathLockPart.HEAD : bone.equals("right_shin")
                            ? GoliathLockPart.RIGHT_LEG : GoliathLockPart.RIGHT_ARM;
                    h.assertTrue(selection.target()==mob && selection.goliathPart()==expected, "crosshair owner of " + bone);
                    direct.add(expected);
                } else if (bone.equals("right_forearm") || bone.equals("weak_1"))
                    h.assertTrue(!selection.selectable(player), "opposing limb cannot map to a right-side lock");
            }
            h.assertTrue(direct.size()==3, "all bindings exercised");
            for (int invalid : new int[]{-1, 3, Integer.MAX_VALUE}) {
                CombatTargeting.requestLock(player, mob.getId(), invalid);
                h.assertTrue(CombatTargeting.lockedEntity(player)==null, "reject invalid region "+invalid);
            }
        }
        h.succeed();
    }

    @GameTest(template="blackwatch_arena", batch="goliath_lock_rays", timeoutTicks=50)
    public static void longRangeCrosshairCoverAndBrokenLegUseTheSelectedPart(GameTestHelper h) {
        try (var f = Fixture.open(h)) {
            var mob=f.mob;
            var player=f.player;
            for(var selected : TargetSelection.choices(player,mob)) {
                boolean acquired=false;
                for(int i=0; i<16 && !acquired; i++) {
                    double angle=i*Math.PI/8;
                    player.setPos(mob.position().add(20*Math.cos(angle),3,20*Math.sin(angle)));
                    var from=player.getEyePosition();
                    var end=from.add(selected.point(player).subtract(from).normalize().scale(40));
                    var hit=HydraHurtContact.ray(mob.hurtRegions(0),from,end,InfectedAttackStyle.PIERCE);
                    if(hit==null || GoliathLockPart.forSkin(hit.bone())!=selected.goliathPart())continue;
                    acquired=selected.equals(TargetSelection.aimedGoliath(player,List.of(mob),from,end,0,64));
                }
                h.assertTrue(acquired,"real distant crosshair picks "+selected.goliathPart());
            }
            var leg=new TargetSelection(mob,GoliathLockPart.RIGHT_LEG.ordinal());
            CombatTargeting.requestLock(player,mob.getId(),leg.region());
            h.assertTrue(leg.equals(CombatTargeting.lockedSelection(player)),"leg lock before plate break");
            h.assertTrue(mob.hurtPart(0,h.getLevel().damageSources().generic(),100),"physical right-leg defense breaks");
            h.assertTrue(CombatTargeting.lockedEntity(player)==null && TargetSelection.choices(player,mob).size()==2,
                    "broken right leg releases without changing selected actor or stealing another joint");
            var head=new TargetSelection(mob,GoliathLockPart.HEAD.ordinal());
            h.assertTrue(head.visibleWithinRange(player,64),"head remains selectable");
            var eye=BlockPos.containing(player.getEyePosition());
            for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                if(x!=0 || y!=0 || z!=0)h.getLevel().setBlock(eye.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
            try {
                CombatTargeting.requestLock(player,mob.getId(),head.region());
                h.assertTrue(CombatTargeting.lockedEntity(player)==null,"cover rejects the same head");
            } finally {
                for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)for(int z=-1;z<=1;z++)
                    if(x!=0 || y!=0 || z!=0)h.getLevel().setBlock(eye.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
        }
        h.succeed();
    }
}
