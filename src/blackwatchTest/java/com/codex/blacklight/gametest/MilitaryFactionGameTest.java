package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.combat.ConsumeVisual;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.combat.ImpactControl;
import com.codex.blacklight.combat.ImpactProfile;
import com.codex.blacklight.disguise.DisguiseIdentity;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.MilitaryRank;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryFactionGameTest {
    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(x, 1, z, Blocks.STONE);
            for (int y = 2; y < 7; y++) h.setBlock(x, y, z,
                    x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.BEDROCK : Blocks.AIR);
        }
    }
    private static MilitarySoldier soldier(GameTestHelper h, int x, int z, boolean army, boolean heavy) {
        MilitarySoldier member = army ? (heavy ? ModEntities.MILITARY_HEAVY : ModEntities.MILITARY_SOLDIER).get().create(h.getLevel())
                : (heavy ? ModEntities.BLACKWATCH_HEAVY : ModEntities.BLACKWATCH_SOLDIER).get().create(h.getLevel());
        member.setPersistenceRequired(); member.setPos(h.absoluteVec(new Vec3(x + .5, 2, z + .5)));
        h.assertTrue(h.getLevel().addFreshEntity(member), "real registered military entity enters the level");
        return member;
    }
    private static com.codex.blacklight.entity.BlacklightInfectedHost target(GameTestHelper h) {
        var entity = ModEntities.INFECTED_HOST.get().create(h.getLevel());
        entity.inherit(EntityType.COW.create(h.getLevel())); entity.setNoAi(true); entity.setPersistenceRequired();
        entity.setPos(h.absoluteVec(new Vec3(6.5, 2, 12.5)));
        entity.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000); entity.setHealth(1000);
        entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        h.assertTrue(h.getLevel().addFreshEntity(entity), "mature stable infection target enters the real level");
        return entity;
    }
    private static void firingCell(GameTestHelper h) {
        for (int x = 5; x <= 7; x++) for (int z = 2; z <= 4; z++) {
            h.setBlock(x, 4, z, Blocks.BEDROCK);
            if (x != 6 || z != 3) h.setBlock(x, 2, z, Blocks.BEDROCK);
            if (x == 5 || x == 7 || z == 2) h.setBlock(x, 3, z, Blocks.BEDROCK);
        }
    }
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper h, MilitarySoldier member, boolean disguised) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "military-fixture"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(member.position().add(0, 0, -2));
        player.setYRot(0); player.setXRot(0); player.yBodyRot = 0; player.setOnGround(true);
        var state = player.getData(ModAttachments.PLAYER_STATE); state.infect(); state.setPrototypeMode(true);
        player.setData(ModAttachments.PLAYER_STATE, state);
        if (disguised) {
            DisguiseSystem.captured(player, DisguiseIdentity.capture(member));
            state = player.getData(ModAttachments.PLAYER_STATE); state.setDisguised(true); player.setData(ModAttachments.PLAYER_STATE, state);
        }
        h.getLevel().addNewPlayer(player); return player;
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void bothFactionsLoadTheirOwnCombatAttributes(GameTestHelper h) {
        arena(h);
        var regular = soldier(h, 4, 5, true, false); var heavy = soldier(h, 8, 5, true, true);
        var watch = soldier(h, 5, 9, false, false); var watchHeavy = soldier(h, 9, 9, false, true);
        h.assertTrue(regular.faction() == MilitaryFaction.MILITARY && watch.faction() == MilitaryFaction.BLACKWATCH, "factions are distinct");
        h.assertTrue(regular.getMaxHealth() == 20 && heavy.getMaxHealth() == 48, "military infantry and heavy profiles load");
        h.assertTrue(regular.getArmorValue() == 2 && heavy.getArmorValue() == 6, "military armour profiles load");
        h.assertTrue(watch.getMaxHealth() == 24 && watchHeavy.getMaxHealth() == 60 && watch.getArmorValue() == 4 && watchHeavy.getArmorValue() == 10,
                "legacy Blackwatch profile is preserved");
        h.succeed();
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void ranksAndUnitIdentityRoundTripWithoutChangingCombatAttributes(GameTestHelper h) {
        arena(h); var member = soldier(h, 5, 5, true, false); UUID unit = UUID.randomUUID(); member.setUnitId(unit);
        for (var rank : MilitaryRank.values()) {
            member.setRank(rank); var saved = member.saveWithoutId(new CompoundTag());
            var copy = ModEntities.MILITARY_SOLDIER.get().create(h.getLevel()); copy.load(saved);
            h.assertTrue(copy.rank() == rank && unit.equals(copy.unitId()), "actual entity NBT retains rank and leaf identity");
            h.assertTrue(copy.getMaxHealth() == 20, "rank does not buff combat attributes");
        }
        var legacy = member.saveWithoutId(new CompoundTag()); legacy.remove("MilitaryRank"); legacy.remove("MilitaryUnit");
        member.load(legacy); h.assertTrue(member.rank() == MilitaryRank.PRIVATE && member.unitId() == null, "legacy entity safely defaults");
        legacy.putString("MilitaryRank", "unknown_future_rank"); member.load(legacy);
        h.assertTrue(member.rank() == MilitaryRank.PRIVATE, "unknown rank is safe"); h.succeed();
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void uninfectedFactionsAreAlliesAndCanEngageInfection(GameTestHelper h) {
        arena(h); var army = soldier(h, 5, 5, true, false); var watch = soldier(h, 9, 5, false, false); var infected = target(h);
        h.assertTrue(army.isAlliedTo(watch) && watch.isAlliedTo(army), "both directions recognize friendly military");
        h.assertTrue(!army.validTarget(watch) && !watch.validTarget(army), "neither faction attacks its friendly counterpart");
        h.assertTrue(army.validTarget(infected) && watch.validTarget(infected), "both can engage the actual infected host"); h.succeed();
    }
    private static void cadence(GameTestHelper h, boolean heavy) {
        arena(h); firingCell(h); var member = soldier(h, 6, 3, true, heavy); var infected = target(h); member.setTarget(infected);
        float initialHealth=infected.getHealth(); // Production CombatScale may multiply infected health on level join.
        var starts = new ArrayList<Long>(); int[] previous = {-1}; float[] beforeLast = {-1};
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime(); int phase = member.phase();
            if (phase == 1 && previous[0] != 1) starts.add(now);
            previous[0] = phase;
            if (starts.size() == 1) {
                long age = now - starts.get(0);
                if (age < 16) h.assertTrue(infected.getHealth() == initialHealth, "military first shot waits sixteen aiming ticks; age=" + age
                        + " health=" + infected.getHealth() + " max=" + infected.getMaxHealth() + " phase=" + phase + " aim=" + member.profile().aimTicks());
                if (age == 31) beforeLast[0] = infected.getHealth();
                if (age == 32) {
                    h.assertTrue(phase == 2 && beforeLast[0] > infected.getHealth(), "military permission covers the actual third shot at32");
                }
            }
        });
        h.runAtTickTime(190, () -> {
            h.assertTrue(starts.size() >= 2 && starts.get(1) - starts.get(0) == (heavy ? 84 : 72), "actual military cycle matches its profile");
            h.assertTrue(beforeLast[0] > 0, "last-shot window was actually observed"); h.succeed();
        });
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 210)
    public static void militaryInfantryUsesSixteenTickAimAndSeventyTwoTickCycle(GameTestHelper h) { cadence(h, false); }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 210)
    public static void militaryHeavyUsesEightyFourTickCycleAndCompleteBurst(GameTestHelper h) { cadence(h, true); }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void capturedMilitaryRankIsAppearanceAndFoolsBothFriendlyFactions(GameTestHelper h) {
        arena(h); var army = soldier(h, 6, 5, true, false); var watch = soldier(h, 9, 5, false, false);
        army.setRank(MilitaryRank.LIEUTENANT); army.setUnitId(UUID.randomUUID());
        var identity = DisguiseIdentity.capture(army);
        h.assertTrue(identity.valid() && identity.family().equals("military"), "military has a capturable faction identity");
        h.assertTrue(identity.appearance().getString("MilitaryRank").equals("lieutenant") && !identity.appearance().hasUUID("MilitaryUnit"),
                "rank appearance is copied while organization ownership is not");
        var player = player(h, army, true);
        h.assertTrue(DisguiseSystem.fooled(army, player) && DisguiseSystem.fooled(watch, player), "both factions evaluate a legitimate unexposed disguise");
        player.discard(); h.succeed();
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void actualGrabClaimsMilitaryAndImmediatelyPausesItsGun(GameTestHelper h) {
        arena(h); var army = soldier(h, 6, 5, true, false); army.setTarget(target(h)); var player = player(h, army, false);
        h.runAtTickTime(3, () -> {
            ConsumeSystem.request(player);
            h.assertTrue(army.getData(ModAttachments.CONSUME_VISUAL).active(), "real military infantry can be grabbed");
            h.assertTrue(army.phase() == 0 && !army.tacticsAvailable(), "grab immediately suspends military gun");
            ConsumeSystem.cancel(player); player.discard(); h.succeed();
        });
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void actualImpactImmediatelyPausesMilitaryAim(GameTestHelper h) {
        arena(h); var army = soldier(h, 6, 3, true, false); army.setTarget(target(h));
        h.runAtTickTime(3, () -> {
            ImpactControl.begin(army, new ImpactProfile(HitReaction.STAGGER, ImpactProfile.BODY, 8, 0, 0, false), 0);
            h.assertTrue(ImpactControl.stunned(army) && army.phase() == 0 && !army.tacticsAvailable(), "real military stagger pauses gun and tactics");
            ImpactControl.cancel(army); h.succeed();
        });
    }
    @GameTest(template = "blackwatch_arena", timeoutTicks = 60)
    public static void otherFactionEnteringTheLineBeforeTheShotIsProtected(GameTestHelper h) {
        arena(h); firingCell(h); var army = soldier(h, 6, 3, true, false); var friend = soldier(h, 11, 7, false, false);
        friend.setNoAi(true); var infected = target(h); army.setTarget(infected); long[] aim = {-1};
        float initialHealth=infected.getHealth();
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (aim[0] < 0 && army.phase() == 1) aim[0] = now;
            if (aim[0] < 0) return;
            if (now - aim[0] == 15) friend.setPos(h.absoluteVec(new Vec3(6.5, 2, 7.5)));
            if (now - aim[0] == 17) {
                h.assertTrue(friend.getHealth() == friend.getMaxHealth() && infected.getHealth() == initialHealth,
                        "friendly counterpart entering just before firing blocks that shot without damage; friend=" + friend.getHealth()
                                + " target=" + infected.getHealth() + " max=" + infected.getMaxHealth()); h.succeed();
            }
        });
    }
}
