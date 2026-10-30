package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.disguise.DisguiseIdentity;
import com.codex.blacklight.disguise.DisguiseSystem;
import com.codex.blacklight.entity.BlackwatchSoldier;
import com.codex.blacklight.entity.BlackwatchSquad;
import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Disposable real-server scenarios; no client, mock entity, or production test hook. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BlackwatchSquadGameTest {
    private static void arena(GameTestHelper h) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            h.setBlock(x, 1, z, Blocks.STONE);
            for (int y = 2; y < 7; y++) h.setBlock(x, y, z,
                    x == 0 || x == 15 || z == 0 || z == 15 ? Blocks.BEDROCK : Blocks.AIR);
        }
    }

    private static BlackwatchSoldier soldier(GameTestHelper h, int x, int z, boolean heavy) {
        var entity = (heavy ? ModEntities.BLACKWATCH_HEAVY : ModEntities.BLACKWATCH_SOLDIER).get().create(h.getLevel());
        entity.setPersistenceRequired();
        entity.setPos(h.absoluteVec(new Vec3(x + .5, 2, z + .5)));
        h.assertTrue(h.getLevel().addFreshEntity(entity), "fixture soldier enters the real level");
        return entity;
    }

    private static com.codex.blacklight.entity.BlacklightInfectedHost infected(GameTestHelper h, int x, int z) {
        var source = EntityType.COW.create(h.getLevel());
        var target = ModEntities.INFECTED_HOST.get().create(h.getLevel());
        target.inherit(source);
        target.setNoAi(true);
        target.setPersistenceRequired(); target.setPos(h.absoluteVec(new Vec3(x + .5, 2, z + .5)));
        h.assertTrue(h.getLevel().addFreshEntity(target) && BlacklightInfectionSystem.isInfected(target),
                "fixture is a real mature infected host, not a cow replaced after 48 ticks");
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
        target.setHealth(target.getMaxHealth());
        return target;
    }

    private static void stationaryFiringCell(GameTestHelper h) {
        // Exercise uninterrupted cadence separately from the open-arena repositioning tests.
        for (int x = 5; x <= 7; x++) for (int z = 2; z <= 4; z++) {
            h.setBlock(x, 4, z, Blocks.BEDROCK);
            if (x != 6 || z != 3) h.setBlock(x, 2, z, Blocks.BEDROCK);
            if (x == 5 || x == 7 || z == 2) h.setBlock(x, 3, z, Blocks.BEDROCK);
        }
    }

    private static ServerPlayer player(GameTestHelper h, BlackwatchSoldier identity, boolean disguise) {
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "squad-fixture"));
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(identity.position().add(0, 0, -2));
        player.setYRot(0); player.setXRot(0); player.yBodyRot = 0; player.setOnGround(true);
        var state = player.getData(ModAttachments.PLAYER_STATE);
        state.infect(); state.setPrototypeMode(true);
        player.setData(ModAttachments.PLAYER_STATE, state);
        if (disguise) {
            DisguiseSystem.captured(player, DisguiseIdentity.capture(identity));
            state = player.getData(ModAttachments.PLAYER_STATE); state.setDisguised(true);
            player.setData(ModAttachments.PLAYER_STATE, state);
        }
        h.getLevel().addNewPlayer(player);
        return player;
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 60)
    public static void fiveSoldiersFormFourAndOneWithoutDuplicateMembership(GameTestHelper h) {
        arena(h);
        var soldiers = List.of(soldier(h, 3, 3, false), soldier(h, 5, 3, false),
                soldier(h, 7, 3, true), soldier(h, 9, 3, false), soldier(h, 11, 3, false));
        h.runAtTickTime(25, () -> {
            var groups = new HashSet<BlackwatchSquad>();
            for (var member : soldiers) groups.add(BlackwatchSquadSystem.squad(member));
            h.assertTrue(groups.size() == 2, "five nearby soldiers split into two teams");
            var unique = new HashSet<UUID>();
            for (var group : groups) {
                h.assertTrue(group.members().size() <= 4, "team cannot overflow");
                for (var id : group.members()) h.assertTrue(unique.add(id), "soldier belongs to only one team");
            }
            h.assertTrue(unique.size() == 5 && groups.stream().anyMatch(g -> g.members().size() == 4), "four plus one, not five singletons");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 220)
    public static void followerKeepsItsOriginalHomeAfterMovingWithTheTeam(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 4, 4, false); var b = soldier(h, 11, 11, false);
        var originalA = a.blockPosition(); var originalB = b.blockPosition();
        // Home persistence needs deliberate movement, not a random patrol point that
        // can happen to lie near the follower's start in a heavily populated batch.
        h.runAtTickTime(8, () -> {
            var unit=a.unitId();h.assertTrue(unit!=null,"fixture has an actual organization");
            var destination=h.absoluteVec(new Vec3(8.5,2,8.5));
            com.codex.blacklight.military.MilitaryOrganizationSystem.transaction(h.getLevel(),org->org.order(unit,
                    new com.codex.blacklight.military.MilitaryOrder(com.codex.blacklight.military.MilitaryOrder.Mode.GUARD,
                            new com.codex.blacklight.military.MilitaryOrder.Point(destination.x,destination.y,destination.z))));
        });
        h.runAtTickTime(80, () -> {
            var group = BlackwatchSquadSystem.squad(a);
            var commander = com.codex.blacklight.military.MilitaryOrganizationSystem.commander(h.getLevel(),a.unitId()).orElse(group.leader());
            var follower = commander.equals(a.getUUID()) ? b : a;
            var original = follower == a ? originalA : originalB;
            h.assertTrue(follower.position().distanceTo(Vec3.atBottomCenterOf(original)) > 1, "fixture follower actually moved before save/home access");
            h.assertTrue(follower.home().equals(original), "joining a team cannot defer or move the follower's own first-tick home");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 40)
    public static void cachedVisionCannotRevealANewPositionAfterCoverAppears(GameTestHelper h) {
        arena(h);
        var member = soldier(h, 5, 3, false);
        var target = infected(h, 5, 12); member.setTarget(target);
        h.runAtTickTime(8, () -> {
            BlackwatchSquadSystem.tick(member);
            var before = BlackwatchSquadSystem.contact(member).orElseThrow();
            h.assertTrue(member.getSensing().hasLineOfSight(target), "fixture primes the native sensing cache while target is visible");
            for (int x = 0; x < 16; x++) for (int y = 2; y < 7; y++) h.setBlock(x, y, 7, Blocks.BEDROCK);
            target.setPos(h.absoluteVec(new Vec3(10.5, 2, 12.5)));
            h.assertTrue(!member.hasLineOfSight(target), "new cover genuinely hides the moved target");
            BlackwatchSquadSystem.tick(member);
            var after = BlackwatchSquadSystem.contact(member).orElseThrow();
            h.assertTrue(after.position().equals(before.position()) && after.observedAt() == before.observedAt(),
                    "a cached old sight result cannot reveal a target's new hidden position");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", batch="isolatedPatrol", timeoutTicks = 220)
    public static void patrolActuallyMovesUsingContinuousNavigation(GameTestHelper h) {
        arena(h);
        for(int x=0;x<32;x++)for(int z=0;z<32;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<7;y++)h.setBlock(x,y,z,Blocks.AIR);}
        var member = soldier(h, 15, 15, false);
        var own=UUID.randomUUID();var start=member.position();
        com.codex.blacklight.military.MilitaryOrganizationSystem.transaction(h.getLevel(),org->{
            if(!org.create(own,member.faction(),com.codex.blacklight.military.MilitaryUnitType.TACTICAL_SQUAD,"patrol-fixture-"+own,1,
                    new com.codex.blacklight.military.MilitaryOrder.Point(start.x,start.y,start.z)))return false;
            return org.assign(own,member.getUUID(),member.rank());
        });member.setUnitId(own);member.setAutomaticUnitJoin(false);
        var origin = member.position();
        var previous = new Vec3[]{origin};
        var greatestDistance = new double[]{0};
        h.onEachTick(() -> {
            h.assertTrue(member.position().distanceTo(previous[0]) < 1, "patrol never teleports to a formation slot");
            greatestDistance[0] = Math.max(greatestDistance[0], member.position().distanceTo(origin));
            previous[0] = member.position();
        });
        h.runAtTickTime(180, () -> {
            h.assertTrue(greatestDistance[0] > .5, "patrol produces actual movement over its route: " + greatestDistance[0]);
            h.assertTrue(member.home().distSqr(member.blockPosition()) < 17 * 17, "patrol remains near its home");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 220)
    public static void pairAlternatesActualAimAndBurstWhileReloading(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 5, 3, false); var b = soldier(h, 8, 3, true);
        a.setAutomaticUnitJoin(false); b.setAutomaticUnitJoin(false);
        var unit = UUID.randomUUID(); var origin = a.position();
        h.assertTrue(com.codex.blacklight.military.MilitaryOrganizationSystem.transaction(h.getLevel(), org -> {
            org.removeMember(a.getUUID()); org.removeMember(b.getUUID());
            return org.create(unit, a.faction(), com.codex.blacklight.military.MilitaryUnitType.TACTICAL_SQUAD,
                    "pair-fire-" + unit, 2, new com.codex.blacklight.military.MilitaryOrder.Point(origin.x, origin.y, origin.z))
                    && org.assign(unit, a.getUUID(), a.rank()) && org.assign(unit, b.getUUID(), b.rank())
                    && org.appoint(unit, a.getUUID());
        }), "cadence fixture creates and appoints a real two-person tactical unit");
        com.codex.blacklight.military.MilitaryOrganizationSystem.manual(h.getLevel(), unit);
        com.codex.blacklight.military.MilitaryOrganizationSystem.data(h.getLevel()).setCommanderManual(unit, true);
        final com.codex.blacklight.entity.BlacklightInfectedHost[] target = {null};
        h.startSequence().thenWaitUntil(() -> {
            h.assertTrue(a.tickCount >= 2 && b.tickCount >= 2, "both actual actors tick before checking their coordinator");
            h.assertTrue(unit.equals(a.unitId()) && unit.equals(b.unitId()), "both actual actors retain their assigned tactical unit");
            var group = BlackwatchSquadSystem.squad(a);
            h.assertTrue(group == BlackwatchSquadSystem.squad(b) && group.members().size() == 2
                    && group.members().contains(a.getUUID()) && group.members().contains(b.getUUID()),
                    "the real ticked coordinator contains exactly the two firing members before a threat appears");
            h.assertTrue(com.codex.blacklight.military.MilitaryOrganizationSystem.organization(h.getLevel()).unit(unit).orElseThrow().commander().equals(a.getUUID())
                    && com.codex.blacklight.military.MilitaryOrganizationSystem.data(h.getLevel()).commanderManual(unit),
                    "the two-person firing fixture retains its real manual appointment");
        }).thenExecute(() -> {
            // Spawn the threat only after readiness: native target selection must not fire on it earlier.
            target[0] = infected(h, 6, 12); a.setTarget(target[0]); b.setTarget(target[0]);
        });
        var fired = new HashSet<UUID>(); var reloaded = new HashSet<UUID>();
        boolean[] finished = {false};
        h.onEachTick(() -> {
            if (finished[0] || target[0] == null) return;
            String unitDiagnostic = "both firing actors stay in the actual fixture unit";
            if (!unit.equals(a.unitId()) || !unit.equals(b.unitId())) {
                var roster = com.codex.blacklight.military.MilitaryOrganizationSystem.organization(h.getLevel());
                unitDiagnostic += " now=" + h.getLevel().getGameTime() + " expectedUnit=" + unit
                        + " actualRoster=" + roster.unit(unit) + " targetAlive=" + target[0].isAlive()
                        + " targetRemoved=" + target[0].getRemovalReason();
                for (var member : List.of(a, b)) {
                    unitDiagnostic += " actor=" + member.getUUID() + " ticks=" + member.tickCount
                            + " unit=" + member.unitId() + " rosterOwner=" + roster.leafOf(member.getUUID())
                            + " indexed=" + (h.getLevel().getEntity(member.getUUID()) == member)
                            + " automaticJoin=" + member.automaticUnitJoin() + " persistent=" + member.isPersistenceRequired()
                            + " alive=" + member.isAlive() + " health=" + member.getHealth()
                            + " removed=" + member.getRemovalReason() + " infected=" + BlacklightInfectionSystem.isInfected(member)
                            + " effects=" + member.getActiveEffects() + " lastAttacker=" + member.getLastHurtByMob()
                            + " position=" + member.position() + " phase=" + member.phase();
                }
            }
            h.assertTrue(unit.equals(a.unitId()) && unit.equals(b.unitId()), unitDiagnostic);
            int shooters = 0;
            for (var member : List.of(a, b)) {
                if (member.phase() == 1 || member.phase() == 2) shooters++;
                if (member.phase() == 2) fired.add(member.getUUID());
                if (member.phase() == 3) reloaded.add(member.getUUID());
            }
            String diagnostic="pair never simultaneously occupies both aim/burst slots";
            if(shooters>1){
                long now=h.getLevel().getGameTime();
                var aSquad=BlackwatchSquadSystem.squad(a);var bSquad=BlackwatchSquadSystem.squad(b);
                diagnostic+=" now="+now+" aPhase="+a.phase()+" bPhase="+b.phase()+" aUnit="+a.unitId()+" bUnit="+b.unitId()
                        +" sameSquad="+(aSquad==bSquad)+" aMembers="+aSquad.members()+" bMembers="+bSquad.members()
                        +" aPermit="+aSquad.hasFirePermit(a.getUUID(),now)+" bPermit="+bSquad.hasFirePermit(b.getUUID(),now)
                        +" aPos="+a.position()+" bPos="+b.position()+" expectedTarget="+target[0].getUUID()
                        +" aTarget="+(a.getTarget()==null?"null":a.getTarget().getUUID())
                        +" bTarget="+(b.getTarget()==null?"null":b.getTarget().getUUID());
            }
            h.assertTrue(shooters <= 1, diagnostic);
        });
        h.runAtTickTime(180, () -> {
            h.assertTrue(target[0] != null, "same-squad readiness must activate the real firing scenario before tick180");
            h.assertTrue(fired.size() == 2 && reloaded.size() == 2, "both members receive a turn and visibly reload");
            h.assertTrue(target[0].isAlive() && target[0].getHealth() < target[0].getMaxHealth(), "real burst raycasts damage the same persistent target");
            finished[0] = true;
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 150)
    public static void singleSoldierKeepsTheExistingSixtyFourTickBurstCycle(GameTestHelper h) {
        arena(h); stationaryFiringCell(h);
        var member = soldier(h, 6, 3, false);
        var target = infected(h, 6, 12); member.setTarget(target);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var starts = new java.util.ArrayList<Long>();
        var previous = new int[]{0};
        h.onEachTick(() -> {
            h.assertTrue(member.getTarget() == target && member.hasLineOfSight(target), "cadence fixture: target=" + (member.getTarget() == target)
                    + " sight=" + member.hasLineOfSight(target) + " actor=" + member.position() + " victim=" + target.position()
                    + " phase=" + member.phase() + " starts=" + starts + " victimAlive=" + target.isAlive() + " removed=" + target.isRemoved());
            if (member.phase() == 1 && previous[0] != 1) starts.add(h.getLevel().getGameTime());
            previous[0] = member.phase();
        });
        h.runAtTickTime(140, () -> {
            h.assertTrue(starts.size() >= 3, "single soldier completes at least two whole cycles: " + starts);
            h.assertTrue(starts.get(1) - starts.get(0) == 64 && starts.get(2) - starts.get(1) == 64,
                    "coordinator must retain the original 64 tick interval: " + starts);
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 200)
    public static void heavySoldierKeepsTheExistingSeventySixTickBurstCycle(GameTestHelper h) {
        arena(h); stationaryFiringCell(h);
        var member = soldier(h, 6, 3, true);
        var target = infected(h, 6, 12); member.setTarget(target);
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        var starts = new java.util.ArrayList<Long>();
        var previous = new int[]{0};
        h.onEachTick(() -> {
            h.assertTrue(member.getTarget() == target && member.hasLineOfSight(target), "heavy cadence fixture: target=" + (member.getTarget() == target)
                    + " sight=" + member.hasLineOfSight(target) + " actor=" + member.position() + " victim=" + target.position()
                    + " phase=" + member.phase() + " starts=" + starts + " victimAlive=" + target.isAlive() + " removed=" + target.isRemoved());
            if (member.phase() == 1 && previous[0] != 1) starts.add(h.getLevel().getGameTime());
            previous[0] = member.phase();
        });
        h.runAtTickTime(165, () -> {
            h.assertTrue(starts.size() >= 3, "heavy soldier completes at least two whole cycles");
            h.assertTrue(starts.get(1) - starts.get(0) == 76 && starts.get(2) - starts.get(1) == 76,
                    "heavy soldier retains its original 76 tick interval: " + starts);
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 80)
    public static void reloadingSoldierCanActuallyWalkToAnotherFiringPosition(GameTestHelper h) {
        arena(h);
        var member = soldier(h, 6, 3, false);
        var target = infected(h, 6, 12); member.setTarget(target);
        var start = new Vec3[]{member.position()};
        h.runAtTickTime(32, () -> start[0] = member.position());
        h.runAtTickTime(62, () -> {
            h.assertTrue(member.position().distanceTo(start[0]) > .8, "reload positioning must not be canceled by the next AI tick");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 150)
    public static void wallStopsShotsAndStaleSharedPositionExpires(GameTestHelper h) {
        arena(h);
        var member = soldier(h, 5, 3, false);
        var target = infected(h, 5, 12);
        for (int x = 0; x < 16; x++) for (int y = 2; y < 7; y++) h.setBlock(x, y, 7, Blocks.BEDROCK);
        var lastSeen = h.absoluteVec(new Vec3(5.5, 2, 6.5));
        var now = h.getLevel().getGameTime();
        BlackwatchSquadSystem.tick(member);
        BlackwatchSquadSystem.squad(member).observe(new BlackwatchSquad.Contact(member.getUUID(), target.getUUID(),
                new BlackwatchSquad.Position(lastSeen.x, lastSeen.y, lastSeen.z),
                new BlackwatchSquad.Position(member.getX(), member.getY(), member.getZ()), now, false));
        member.setTarget(target);
        h.runAtTickTime(15, () -> {
            var contact = BlackwatchSquadSystem.contact(member).orElseThrow();
            h.assertTrue(contact.observedAt() == now && contact.position().z() == lastSeen.z, "occluded target cannot refresh or move the contact");
            var destination = member.getNavigation().getTargetPos();
            h.assertTrue(destination == null || destination.getZ() <= BlockPos.containing(lastSeen).getZ(), "search navigates toward last sighting, not live position behind wall");
        });
        h.runAtTickTime(120, () -> {
            h.assertTrue(target.isAlive() && target.getHealth() == target.getMaxHealth(), "same living target takes no damage through solid wall");
            h.assertTrue(BlackwatchSquadSystem.contact(member).isEmpty() && member.getTarget() == null, "unseen contact expires rather than being relayed forever");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 40)
    public static void effectiveDisguiseRejectsAnOtherwiseSharedTarget(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 5, 5, false); var b = soldier(h, 8, 5, false);
        var player = player(h, a, true);
        h.assertTrue(DisguiseSystem.fooled(a, player) && DisguiseSystem.fooled(b, player), "real civilian/blackwatch identity fools both observers");
        a.setTarget(player); b.setTarget(player);
        h.runAtTickTime(10, () -> {
            h.assertTrue(a.getTarget() == null && b.getTarget() == null, "shared pursuit cannot bypass per-recipient disguise check");
            h.assertTrue(a.phase() == 0 && b.phase() == 0, "fooled soldiers stop combat animation");
            player.discard();
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 50)
    public static void newlyEffectiveDisguiseInvalidatesAnExistingLegalSighting(GameTestHelper h) {
        arena(h);var a=soldier(h,5,5,false);var b=soldier(h,8,5,false);var p=player(h,a,false);
        a.setTarget(p);
        h.runAtTickTime(5,()->{
            BlackwatchSquadSystem.tick(a);
            h.assertTrue(BlackwatchSquadSystem.contact(a).isPresent(),"a real visible infected player produced a legal shared sighting first");
            h.assertTrue(BlackwatchSquadSystem.squad(a).contact(h.getLevel().getGameTime()).isPresent(),"underlying sighting is still within its TTL");
            DisguiseSystem.captured(p,DisguiseIdentity.capture(a));var state=p.getData(ModAttachments.PLAYER_STATE);state.setDisguised(true);p.setData(ModAttachments.PLAYER_STATE,state);
            h.assertTrue(DisguiseSystem.fooled(a,p)&&DisguiseSystem.fooled(b,p),"new identity is effective under the real global disguise rules");
            h.assertTrue(BlackwatchSquadSystem.contact(a).isEmpty()&&BlackwatchSquadSystem.contact(b).isEmpty(),"existing legal memory cannot bypass a newly fooled recipient");
            BlackwatchSquadSystem.tick(a);BlackwatchSquadSystem.tick(b);
            h.assertTrue(a.getTarget()==null&&b.getTarget()==null,"both actual recipients clear pursuit after their own legality check");p.discard();h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 45)
    public static void humanAttackerRetainsTheExistingRetaliationPath(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 5, 5, false); var b = soldier(h, 8, 5, false);
        var player = FakePlayerFactory.get(h.getLevel(), new GameProfile(UUID.randomUUID(), "human-attacker"));
        player.setGameMode(GameType.SURVIVAL); player.setPos(a.position().add(0, 0, -3));
        h.getLevel().addNewPlayer(player);
        h.assertTrue(!BlacklightInfectionSystem.isInfected(player), "attacker fixture is an ordinary human");
        h.runAtTickTime(5, () -> {
            h.assertTrue(a.getTarget() == null && b.getTarget() == null, "uninfected bystander is not automatically hostile");
            h.assertTrue(a.hurt(a.damageSources().playerAttack(player), 1), "real attack damages soldier and triggers retaliation");
        });
        h.runAtTickTime(20, () -> {
            h.assertTrue(a.getTarget() == player && b.getTarget() == player, "existing hurt retaliation and local assistance remain available");
            player.discard();
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 45)
    public static void friendInTheBulletLineProtectsBothFriendAndTarget(GameTestHelper h) {
        arena(h);
        var shooter = soldier(h, 6, 2, false);
        var blocker = soldier(h, 6, 6, false); blocker.setNoAi(true);
        var target = infected(h, 6, 12); shooter.setTarget(target);
        float allyHealth = blocker.getHealth();
        h.runAtTickTime(26, () -> {
            h.assertTrue(blocker.getHealth() == allyHealth, "friendly body is never damaged");
            h.assertTrue(target.getHealth() == target.getMaxHealth(), "shot cannot travel through friendly body");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 45)
    public static void realGrabTakesControlAndReleasesTheFiringSlot(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 6, 5, false); var b = soldier(h, 9, 5, false);
        var target = infected(h, 7, 12); a.setTarget(target); b.setTarget(target);
        var player = player(h, a, false);
        h.runAtTickTime(3, () -> {
            ConsumeSystem.request(player);
            h.assertTrue(a.getData(ModAttachments.CONSUME_VISUAL).active(), "production grab actually claims the front soldier");
            h.assertTrue(a.phase() == 0, "claim immediately clears the previous aim/burst phase");
            h.assertTrue(!BlackwatchSquadSystem.squad(a).hasFirePermit(a.getUUID(), h.getLevel().getGameTime()), "claim immediately releases shared fire permission");
        });
        h.runAtTickTime(5, () -> {
            h.assertTrue(a.getData(ModAttachments.CONSUME_VISUAL).active() ? a.phase() == 0 : a.phase() != 2,
                    "held soldier stays idle; any legitimate early release restarts a full aim");
            ConsumeSystem.cancel(player);
        });
        h.runAtTickTime(9, () -> {
            h.assertTrue(!a.getData(ModAttachments.CONSUME_VISUAL).active() && a.phase() != 2, "released soldier does not replay an accumulated burst");
            player.discard();
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 40)
    public static void pursuitCannotCarryATargetOrFirePermitBeyondHomeBoundary(GameTestHelper h) {
        arena(h);
        var member = soldier(h, 6, 3, false);
        var target = infected(h, 6, 12); member.setTarget(target);
        member.home();
        h.runAtTickTime(8, () -> {
            var group = BlackwatchSquadSystem.squad(member);
            group.requestFire(member.getUUID(), h.getLevel().getGameTime());
            member.setPos(member.position().add(50, 0, 0));
            BlackwatchSquadSystem.tick(member);
            h.assertTrue(member.getTarget() == null, "pursuit outside the 48 block home boundary clears target");
            h.assertTrue(!group.hasFirePermit(member.getUUID(), h.getLevel().getGameTime()), "retreat frees the firing slot");
            member.discard(); h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 40)
    public static void stunnedQueueHeadReleasesTheNextHealthyMember(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 4, 5, false); var b = soldier(h, 6, 5, false);
        var c = soldier(h, 8, 5, false); var d = soldier(h, 10, 5, false);
        h.runAtTickTime(30, () -> {
            var group = BlackwatchSquadSystem.squad(a); long now = h.getLevel().getGameTime();
            h.assertTrue(group.members().size() == 4, "four real soldiers share a group");
            h.assertTrue(group.requestFire(a.getUUID(), now) && group.requestFire(b.getUUID(), now), "two real permissions are occupied");
            h.assertTrue(!group.requestFire(c.getUUID(), now) && !group.requestFire(d.getUUID(), now), "queue head and successor wait");
            com.codex.blacklight.combat.ImpactControl.begin(c, new com.codex.blacklight.combat.ImpactProfile(
                    com.codex.blacklight.combat.HitReaction.STAGGER, com.codex.blacklight.combat.ImpactProfile.BODY, 12, 0, 0, false), 0);
            h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(c), "production impact actually controls queue head");
            group.releaseFire(a.getUUID());
            h.assertTrue(group.requestFire(d.getUUID(), now), "stunned queue head cannot block a healthy successor");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 110)
    public static void shortStaggerClearsAimAndRecoveryStartsAFullAim(GameTestHelper h) {
        arena(h); stationaryFiringCell(h);
        var member = soldier(h, 6, 3, false); var target = infected(h, 6, 12);
        member.setTarget(target);
        boolean[] interrupted = {false}; long[] resumedAt = {-1};
        float health = target.getHealth();
        h.onEachTick(() -> {
            long now = h.getLevel().getGameTime();
            if (!interrupted[0] && member.phase() == 1) {
                interrupted[0] = true;
                com.codex.blacklight.combat.ImpactControl.begin(member, new com.codex.blacklight.combat.ImpactProfile(
                        com.codex.blacklight.combat.HitReaction.STAGGER, com.codex.blacklight.combat.ImpactProfile.BODY, 4, 0, 0, false), 0);
                h.assertTrue(com.codex.blacklight.combat.ImpactControl.stunned(member), "production short stagger is active");
                h.assertTrue(member.phase() == 0, "stagger clears aim immediately between entity ticks");
                return;
            }
            if (!interrupted[0]) return;
            if (com.codex.blacklight.combat.ImpactControl.stunned(member)) {
                h.assertTrue(member.phase() == 0 && target.getHealth() == health, "controlled soldier cannot keep aiming or fire");
                return;
            }
            if (member.phase() == 1 && resumedAt[0] < 0) resumedAt[0] = now;
            if (member.phase() == 2) {
                h.assertTrue(resumedAt[0] >= 0 && now - resumedAt[0] >= 12, "recovery performs twelve new aiming ticks");
                h.assertTrue(target.getHealth() < health, "recovered soldier actually fires after a complete aim");
                h.succeed();
            }
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 40)
    public static void removedLeaderCannotKeepMembersOrFirePermission(GameTestHelper h) {
        arena(h);
        var a = soldier(h, 5, 5, false); var b = soldier(h, 8, 5, true);
        h.runAtTickTime(10, () -> {
            var group = BlackwatchSquadSystem.squad(a);
            var leader = group.leader().equals(a.getUUID()) ? a : b;
            var survivor = leader == a ? b : a;
            group.requestFire(leader.getUUID(), h.getLevel().getGameTime());
            leader.discard();
            h.runAfterDelay(3, () -> {
                var updated = BlackwatchSquadSystem.squad(survivor);
                h.assertTrue(updated.members().equals(List.of(survivor.getUUID())), "removed member is pruned");
                h.assertTrue(updated.leader().equals(survivor.getUUID()), "survivor becomes leader");
                h.assertTrue(updated.requestFire(survivor.getUUID(), h.getLevel().getGameTime()), "dead leader cannot reserve a slot forever");
                h.succeed();
            });
        });
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 30)
    public static void homeLoadsSafelyFromNewLegacyAndForeignDimensionData(GameTestHelper h) {
        arena(h);
        var original = soldier(h, 5, 5, false);
        var home = original.home(); var tag = original.saveWithoutId(new CompoundTag());
        var copy = ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());
        copy.load(tag);
        h.assertTrue(copy.home().equals(home), "home survives actual entity serialization");
        tag.remove("BlackwatchHome"); tag.remove("BlackwatchHomeDimension");
        copy.load(tag); copy.setPos(h.absoluteVec(new Vec3(9, 2, 9)));
        h.assertTrue(copy.home().equals(copy.blockPosition()), "legacy data initializes from current position");
        tag = original.saveWithoutId(new CompoundTag()); tag.putString("BlackwatchHomeDimension", "minecraft:the_nether");
        copy.load(tag); copy.setPos(h.absoluteVec(new Vec3(10, 2, 10)));
        h.assertTrue(copy.home().equals(copy.blockPosition()), "foreign-dimension home does not send soldier to unrelated coordinates");
        h.succeed();
    }

    @GameTest(template = "blackwatch_arena", timeoutTicks = 35)
    public static void separateDimensionsAndUnloadCannotLeakSquads(GameTestHelper h) {
        arena(h);
        var otherLevel = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(otherLevel != null, "actual Nether is loaded as an independent second dimension");
        var a = soldier(h, 5, 5, false);
        otherLevel.getChunk(0, 0); // Fixture preparation only; production coordination never requests chunks.
        otherLevel.setBlockAndUpdate(new BlockPos(3, 63, 3), Blocks.STONE.defaultBlockState());
        var b = ModEntities.BLACKWATCH_SOLDIER.get().create(otherLevel);
        b.setUUID(a.getUUID()); b.setPos(3.5, 64, 3.5); b.setPersistenceRequired();
        h.assertTrue(otherLevel.addFreshEntity(b), "second dimension accepts its own entity UUID");
        BlackwatchSquadSystem.tick(a); BlackwatchSquadSystem.tick(b);
        var groupA = BlackwatchSquadSystem.squad(a); var groupB = BlackwatchSquadSystem.squad(b);
        h.assertTrue(groupA != groupB, "same UUID in distinct dimensions cannot alias shared state");
        BlackwatchSquadSystem.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(otherLevel));
        h.assertTrue(BlackwatchSquadSystem.squad(a) == groupA && BlackwatchSquadSystem.squad(b) != groupB, "unload cleans exactly the intended dimension");
        b.discard(); h.succeed();
    }
}
