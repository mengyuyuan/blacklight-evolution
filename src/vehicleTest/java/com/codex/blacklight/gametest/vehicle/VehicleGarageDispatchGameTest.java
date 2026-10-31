package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import java.lang.reflect.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;

/** Opt-in real-world dispatch QA. Never fabricates a personnel slot or a crew entity. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class VehicleGarageDispatchGameTest {
    @GameTest(template="blackwatch_arena", batch="vehicle6garage_dispatch_military", timeoutTicks=2800)
    public static void realSixteenPersonGarrisonWalksIntoThreeInitialVehiclesAndUnloadedOrLostSlotsNeverRefill(GameTestHelper h) {
        begin(h, 251000, false, true);
    }

    @GameTest(template="blackwatch_arena", batch="vehicle6garage_dispatch_blackwatch", timeoutTicks=2000)
    public static void realTwentyPersonGarrisonKeepsItsPersonnelSlotsWhenFivePeopleCrewThreeVehicles(GameTestHelper h) {
        begin(h, 255000, true, false);
    }

    private static void begin(GameTestHelper helper, int x, boolean blackwatch, boolean exerciseUnload) {
        var fixture = Fixture.create(helper.getLevel(), x, blackwatch);
        int[] stage = {0}, changedAt = {0};
        for (int elapsed = 1; elapsed <= (exerciseUnload ? 2700 : 1900); elapsed++) {
            final int tick = elapsed;
            helper.runAtTickTime(tick, () -> {
                if (stage[0] == 4) return;
                try {
                    if (stage[0] == 0 && fixture.muster(tick)) {
                        fixture.attach();
                        stage[0] = 1;
                    }
                    if (stage[0] == 1) {
                        fixture.observeWalking();
                        if (tick % 200 == 0) fixture.logDiagnostic("WAIT tick=" + tick);
                        if (fixture.dispatched()) {
                            fixture.verifyDispatch();
                            if (!exerciseUnload) { fixture.close(); stage[0] = 4; helper.succeed(); return; }
                            fixture.driveApcOutAndReleaseChunks();
                            changedAt[0] = tick;
                            stage[0] = 2;
                        }
                    } else if (stage[0] == 2) {
                        fixture.verifyIdentityLedger();
                        if (fixture.apcActuallyUnloaded()) {
                            fixture.vehicle("tank").hurt(fixture.level.damageSources().generic(), 10000);
                            changedAt[0] = tick;
                            stage[0] = 3;
                        } else require(tick - changedAt[0] < 800, "Distant APC did not actually unload: " + fixture.diagnostic());
                    } else if (stage[0] == 3 && tick - changedAt[0] >= 80) {
                        fixture.verifyIdentityLedger();
                        require(fixture.garage().slots().get("apc").state() == VehicleGarageData.State.ISSUED,
                                "An actually unloaded APC retains its issued slot");
                        require(fixture.garage().slots().get("tank").state() == VehicleGarageData.State.LOST,
                                "Actual tank destruction must persist a lost slot");
                        fixture.close(); stage[0] = 4; helper.succeed(); return;
                    }
                    require(tick < (exerciseUnload ? 2700 : 1900), "Dispatch timed out: " + fixture.diagnostic());
                } catch (Throwable failure) {
                    fixture.logDiagnostic("FAIL tick=" + tick + " error=" + failure.getMessage());
                    fixture.close(); stage[0] = 4;
                    throw failure instanceof RuntimeException runtime ? runtime : new IllegalStateException(failure);
                }
            });
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    /** Shared only by opt-in QA sources; a normal production JAR excludes this entire package. */
    public static final class Fixture implements AutoCloseable {
        public final ServerLevel level;
        public final int x;
        public final boolean blackwatch;
        public final CityMilitaryPlan.Site site;
        public final BlockPos garageOrigin;
        public final String baseId;
        public String garageId;
        public final Map<UUID, MilitarySoldier> people = new LinkedHashMap<>();
        public final Map<String, UUID> vehicles = new LinkedHashMap<>();
        private final Map<UUID, Vec3> born = new LinkedHashMap<>(), previous = new LinkedHashMap<>();
        private final Set<UUID> boarded = new HashSet<>();
        private final Map<Long, Boolean> forced = new LinkedHashMap<>();
        private final boolean population;
        private final int garrisonLimit;
        private UUID[] originalSlots;
        private ChunkPos apcChunk;
        private boolean closed;

        public static Fixture create(ServerLevel level, int x, boolean blackwatch) {
            int maximum=VehicleGarageService.data(level).garages().stream().mapToInt(g->g.site().x()).max().orElse(x-4096);
            try {
                Method get=CityMilitaryData.class.getDeclaredMethod("get",ServerLevel.class);get.setAccessible(true);
                Object data=get.invoke(null,level);Field field=CityMilitaryData.class.getDeclaredField("bases");field.setAccessible(true);
                for(Object base:((Map<?,?>)field.get(data)).values()) {
                    var site=(CityMilitaryPlan.Site)base.getClass().getField("site").get(base);maximum=Math.max(maximum,site.x());
                }
            } catch(ReflectiveOperationException failure){throw new IllegalStateException("Cannot inspect prior QA bases without resetting their slots",failure);}
            // A repeated QA run gets a new real base. Old finite issuance/loss
            // records remain intact; reopen below always uses the saved location.
            return new Fixture(level, Math.max(x,maximum+4096), blackwatch, null, null, true);
        }

        public static Fixture reopen(ServerLevel level, int x, boolean blackwatch, String base, String garage) {
            return new Fixture(level, x, blackwatch, base, garage, false);
        }

        private Fixture(ServerLevel level, int x, boolean blackwatch, String existingBase, String existingGarage, boolean create) {
            this.level = level; this.x = x; this.blackwatch = blackwatch;
            site = new CityMilitaryPlan.Site(x, 0, 80, 0, blackwatch);
            garageOrigin = new BlockPos(x + 80, 80, site.templateDepth() - 31);
            population = CityConfig.SPAWNS.get(); garrisonLimit = CityConfig.MILITARY_GARRISON_LIMIT.get();
            CityConfig.SPAWNS.set(true); CityConfig.MILITARY_GARRISON_LIMIT.set(200);
            for (int cx = (x - 16) >> 4; cx <= (x + 128) >> 4; cx++) {
                for (int cz = -1; cz <= 13; cz++) {
                    long key = ChunkPos.asLong(cx, cz);
                    forced.put(key, level.getForcedChunks().contains(key));
                    level.getChunk(cx, cz); // Deliberate disposable-QA setup, never a production lookup.
                    level.setChunkForced(cx, cz, true);
                }
            }
            if (create) {
                flat(0, 0, 64, site.templateDepth() - 1, 17);
                flat(70, site.templateDepth() - 41, 120, site.templateDepth() + 15, 10);
                flat(28, site.templateDepth() - 3, 114, site.templateDepth() + 8, 8);
                flat(100, site.templateDepth() + 5, 111, 192, 8);
                baseId = CityMilitaryPlacement.place(level, site);
                require(!baseId.isEmpty(), "Production base placement returned no base");
            } else {
                baseId = Objects.requireNonNull(existingBase);
                garageId = Objects.requireNonNull(existingGarage);
                require(MilitaryBaseAccess.find(level, baseId).isPresent(), "Saved base must exist before verification");
                originalSlots = slots();
            }
        }

        private void flat(int minX, int minZ, int maxX, int maxZ, int clearance) {
            for (int dx = minX; dx <= maxX; dx++) for (int z = minZ; z <= maxZ; z++) {
                for (int y = 77; y <= 80; y++) level.setBlock(new BlockPos(x + dx, y, z), Blocks.STONE.defaultBlockState(), 2);
                for (int y = 81; y <= 80 + clearance; y++) level.setBlock(new BlockPos(x + dx, y, z), Blocks.AIR.defaultBlockState(), 2);
            }
        }

        public UUID[] slots() {
            try {
                Method get = CityMilitaryData.class.getDeclaredMethod("get", ServerLevel.class); get.setAccessible(true);
                Object data = get.invoke(null, level);
                Field bases = CityMilitaryData.class.getDeclaredField("bases"); bases.setAccessible(true);
                Object base = ((Map<?, ?>) bases.get(data)).get(baseId);
                require(base != null, "Production base record vanished");
                return ((UUID[]) base.getClass().getField("slots").get(base)).clone();
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }

        public boolean muster(int elapsed) {
            if (elapsed % 20 != 0) return false;
            var first = LargeBaseBlueprint.get(blackwatch).guards().get(0);
            int[] point = site.world(first.x(), first.z());
            if (!level.isPositionEntityTicking(new BlockPos(point[0], site.floor() + first.y(), point[1]))) return false;
            try {
                Method maintain = CityMilitaryService.class.getDeclaredMethod("maintain", ServerLevel.class, List.class);
                maintain.setAccessible(true);
                maintain.invoke(null, level, List.of(new Vec3(x + site.width() / 2., 81, site.depth() / 2.)));
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Real maintain entry failed", failure); }
            UUID[] issued = slots();
            require(issued.length == (blackwatch ? 20 : 16), "Original personnel capacity changed");
            for (UUID id : issued) if (id != null) {
                require(level.getEntity(id) instanceof MilitarySoldier, "Issued native slot has no real soldier: " + id);
                var soldier = (MilitarySoldier) level.getEntity(id);
                people.putIfAbsent(id, soldier); born.putIfAbsent(id, soldier.position()); previous.putIfAbsent(id, soldier.position());
            }
            if (Arrays.stream(issued).anyMatch(Objects::isNull)) return false;
            originalSlots = issued;
            previous.replaceAll((id, old) -> people.get(id).position());
            require(people.size() == issued.length, "Native production slots must map one-to-one to real people");
            return true;
        }

        public void attach() {
            var preview = VehicleGarageService.preview(level, baseId, garageOrigin, 0);
            require(preview.allowed(), "Real garage preview failed: " + preview.reason());
            garageId = VehicleGarageService.attach(level, baseId, garageOrigin, 0, preview.token());
            require(!garageId.isEmpty(), "Confirmed garage was not attached");
            require(garage().slots().values().stream().allMatch(s -> s.state() == VehicleGarageData.State.UNISSUED),
                    "Attachment itself must not fabricate vehicles or personnel");
        }

        public VehicleGarageData.Garage garage() { return VehicleGarageService.data(level).garage(garageId).orElseThrow(); }
        public MilitaryVehicle vehicle(String chassis) {
            UUID id = garage().slots().get(chassis).vehicle();
            require(id != null && level.getEntity(id) instanceof MilitaryVehicle, "Expected loaded real " + chassis);
            return (MilitaryVehicle) level.getEntity(id);
        }
        public boolean dispatched() { return garage().slots().values().stream().allMatch(s -> s.state() == VehicleGarageData.State.ISSUED); }

        public void observeWalking() {
            for (var entry : people.entrySet()) {
                var soldier = entry.getValue();
                require(level.getEntity(entry.getKey()) == soldier, "Dispatch must keep the same actual soldier object and UUID");
                if (!boarded.contains(entry.getKey())) {
                    require(soldier.position().distanceToSqr(previous.get(entry.getKey())) <= 36,
                            "Crew moved more than six blocks in one observed tick instead of walking: " + entry.getKey());
                    if (soldier.getVehicle() instanceof MilitaryVehicle vehicle && vehicle.nativeOrigin().equals(baseId)) {
                        require(born.get(entry.getKey()).distanceToSqr(soldier.position()) > 16 * 16,
                                "Real native crew must traverse the outside approach before boarding");
                        boarded.add(entry.getKey());
                    }
                }
                previous.put(entry.getKey(), soldier.position());
            }
        }

        public void verifyDispatch() {
            require(Arrays.equals(originalSlots, slots()), "Garage dispatch rewrote the original 16/20 personnel ledger");
            Set<UUID> crew = new HashSet<>();
            for (String chassis : VehicleGarageData.CHASSIS) {
                var vehicle = vehicle(chassis); vehicles.put(chassis, vehicle.getUUID());
                require(vehicle.chassisId().equals(chassis) && vehicle.nativeOrigin().equals(baseId)
                        && vehicle.nativeDimension().equals(level.dimension().location().toString())
                        && vehicle.currentHome().equals(garageId), "Native vehicle identity/home changed");
                require(vehicle.getPassengers().size() == (chassis.equals("truck") ? 1 : 2), "Vehicle lacks its real driver/gunner");
                for (var passenger : vehicle.getPassengers()) {
                    require(passenger instanceof MilitarySoldier && people.get(passenger.getUUID()) == passenger
                            && crew.add(passenger.getUUID()) && boarded.contains(passenger.getUUID()), "Crew duplicated or did not walk from the native roster");
                    require(vehicle.seatOf(passenger.getUUID()) != null, "Actual passenger has no actual seat");
                }
                require(vehicle.occupant(VehicleDefinition.Job.DRIVER) instanceof MilitarySoldier, "Driver seat is not occupied");
                if (!chassis.equals("truck")) require(vehicle.occupant(VehicleDefinition.Job.GUNNER) instanceof MilitarySoldier, "Gunner seat is not occupied");
            }
            require(crew.size() == 5 && new HashSet<>(vehicles.values()).size() == 3, "Three vehicles must use five distinct existing people");
            verifyIdentityLedger();
        }

        public void verifyIdentityLedger() {
            require(Arrays.equals(originalSlots, slots()), "Native personnel slots changed after departure/removal");
            for (var entry : vehicles.entrySet()) require(entry.getValue().equals(garage().slots().get(entry.getKey()).vehicle()), "A garage slot was reopened/reissued");
            Set<UUID> expected = new HashSet<>(Arrays.asList(originalSlots));
            for (var entity : level.getAllEntities()) {
                if (entity instanceof MilitarySoldier soldier && baseId.equals(soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG)))
                    require(expected.contains(soldier.getUUID()), "Garage manufactured an extra soldier outside native slots");
                if (entity instanceof MilitaryVehicle vehicle && baseId.equals(vehicle.nativeOrigin()))
                    require(vehicles.containsValue(vehicle.getUUID()), "Garage manufactured a replacement vehicle UUID");
            }
        }

        public void driveApcOutAndReleaseChunks() {
            var apc = vehicle("apc");
            for (int step = 0; step < 560; step++) require(apc.moveVehicle(.2, 0).reason() == VehicleKinematics.StopReason.NONE, "APC must physically drive down the supported outside road");
            apc.setDeltaMovement(Vec3.ZERO);
            apcChunk = new ChunkPos(apc.blockPosition());
            for (long key : forced.keySet()) { var chunk = new ChunkPos(key); if (chunk.z >= 7) level.setChunkForced(chunk.x, chunk.z, false); }
        }
        public boolean apcActuallyUnloaded() {
            return apcChunk != null && level.getChunkSource().getChunkNow(apcChunk.x, apcChunk.z) == null
                    && level.getEntity(vehicles.get("apc")) == null;
        }
        public void restoreSettings() { CityConfig.SPAWNS.set(population); CityConfig.MILITARY_GARRISON_LIMIT.set(garrisonLimit); }
        public String diagnostic() { return "base=" + baseId + " personnel=" + people.size() + "/" + slots().length
                + " garage=" + (garageId == null ? "not-attached" : garage().slots()) + "; see GARAGE_DISPATCH_QA lines"; }
        public void logDiagnostic(String phase) {
            var log = org.slf4j.LoggerFactory.getLogger(VehicleGarageDispatchGameTest.class);
            try {
                Set<UUID> eligible = new HashSet<>();
                MilitaryBaseAccess.idleCrew(level, baseId, 32, Set.of()).forEach(s -> eligible.add(s.getUUID()));
                log.info("GARAGE_DISPATCH_QA {} base={} garage={} core={} eligible={}", phase, baseId, garageId,
                        MilitaryBaseAccess.find(level, baseId).map(v -> v.loaded() + "/" + v.operational()).orElse("MISSING"), eligible);
                if(garageId!=null){
                    log.info("GARAGE_DISPATCH_QA service {}",VehicleLogisticsService.diagnostic(level,garageId));
                    for(var slot:garage().slots().values()){
                        if(slot.vehicle()!=null&&level.getEntity(slot.vehicle()) instanceof MilitaryVehicle vehicle)log.info("GARAGE_DISPATCH_QA actualHull {} {}",slot.chassis(),VehicleGarageService.diagnostic(level,vehicle,garageId));
                        else if(slot.state()==VehicleGarageData.State.UNISSUED){
                            Method center=VehicleGarageService.class.getDeclaredMethod("center",VehicleGarageData.Site.class,String.class);center.setAccessible(true);
                            var point=(Vec3)center.invoke(null,garage().site(),slot.chassis());
                            var probe=VehicleRegistry.type(garage().faction(),slot.chassis()).create(level);probe.setPos(point);probe.setYRot(-garage().site().rotation()*90);
                            var bounds=VehicleHullShape.from(probe.definition(),0,0).bounds(new VehicleDefinition.Vec3(point.x,point.y,point.z),probe.getYRot());
                            log.info("GARAGE_DISPATCH_QA unissuedPlacementProbe {} pos={} placement={} envelope={}",slot.chassis(),point,VehicleKinematics.placementReady(probe),VehicleKinematics.checkEnvelope(probe,bounds));
                        }
                    }
                }
                UUID[] nativeSlots = slots();
                for (int slot = 0; slot < nativeSlots.length; slot++) {
                    UUID id = nativeSlots[slot]; var soldier = people.get(id); if (soldier == null) continue;
                    var path = soldier.getNavigation().getPath();
                    var target = soldier.getTarget();
                    var post = BlockPos.of(soldier.getPersistentData().getLong(CityMilitaryService.POST_TAG));
                    log.info("GARAGE_DISPATCH_QA person slot={} uuid={} pos={} originalPost={} role={} noAI={} target={} crewOperational={} eligible={} vehicle={} navigation={}",
                            slot, id, soldier.position(), post.toShortString(), soldier.commandRole().id(), soldier.isNoAi(),
                            target == null ? "NONE" : target.getUUID(), MilitaryVehicle.crewOperational(soldier), eligible.contains(id),
                            soldier.getVehicle() == null ? "NONE" : soldier.getVehicle().getUUID(),
                            path == null ? "NONE" : "canReach=" + path.canReach() + ", next=" + path.getNextNodeIndex() + "/" + path.getNodeCount());
                    log.info("GARAGE_DISPATCH_QA home uuid={} {}", id, MilitaryHomeSystem.describe(soldier));
                }
                Field field = VehicleGarageService.class.getDeclaredField("RECRUITS"); field.setAccessible(true);
                Object raw = ((Map<?, ?>) field.get(null)).get(level);
                if (raw instanceof Map<?, ?> recruits) for (var entry : recruits.entrySet()) {
                    if (garageId == null || !entry.getKey().toString().startsWith(garageId + ":")) continue;
                    Object recruit = entry.getValue();
                    Field peopleField = recruit.getClass().getDeclaredField("people"); peopleField.setAccessible(true);
                    Field expiresField = recruit.getClass().getDeclaredField("expires"); expiresField.setAccessible(true);
                    log.info("GARAGE_DISPATCH_QA recruit key={} people={} expires={} now={}", entry.getKey(), peopleField.get(recruit), expiresField.get(recruit), level.getGameTime());
                    Field goalsField = recruit.getClass().getDeclaredField("goals"); goalsField.setAccessible(true);
                    for (var goalEntry : ((Map<?, ?>) goalsField.get(recruit)).entrySet()) {
                        Object goal = goalEntry.getValue();
                        Field destinationField = goal.getClass().getDeclaredField("destination"); destinationField.setAccessible(true);
                        Field routeField = goal.getClass().getDeclaredField("route"); routeField.setAccessible(true);
                        var route = (Deque<?>) routeField.get(goal);
                        log.info("GARAGE_DISPATCH_QA goal person={} destination={} routeSize={} nextWaypoints={}", goalEntry.getKey(),
                                destinationField.get(goal), route.size(), route.stream().limit(4).toList());
                    }
                }
            } catch (Throwable diagnosticFailure) { log.warn("GARAGE_DISPATCH_QA diagnostics unavailable base={}", baseId, diagnosticFailure); }
        }
        @Override public void close() {
            if (closed) return; closed = true;
            for (UUID id : vehicles.values()) { var entity = level.getEntity(id); if (entity != null) entity.discard(); }
            for (var soldier : people.values()) if (!soldier.isRemoved()) soldier.discard();
            forced.forEach((key, wasForced) -> { var chunk = new ChunkPos(key); level.setChunkForced(chunk.x, chunk.z, wasForced); });
            restoreSettings();
        }
    }
}
