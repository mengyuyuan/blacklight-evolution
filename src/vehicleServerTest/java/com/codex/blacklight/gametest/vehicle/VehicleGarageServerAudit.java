package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.vehicle.*;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.LoggerFactory;

/**
 * Disposable dedicated-server QA, excluded by the production gametest-package JAR rule.
 * Opt-in sources must include BOTH vehicleTest/java and vehicleServerTest/java.
 * Run the SAME normal-server directory twice with -Dblacklight.vehicleGarageAudit=true
 * and -Dblacklight.vehicleGarageAudit.phase=prepare, then phase=verify.
 * Optional blacklight.vehicleGarageAudit.file selects the JSON evidence file.
 * Public prepare/verify/poll are also explicit server-thread entry points for a QA harness.
 * A checkpoint is an expectation; only the separate verify process establishes durability.
 */
@EventBusSubscriber(modid="blacklight", value=net.neoforged.api.distmarker.Dist.DEDICATED_SERVER)
public final class VehicleGarageServerAudit {
    private enum Stage { MUSTER, DISPATCH, SETTLE, UNLOAD, RESTART, DONE }
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final org.slf4j.Logger LOG = LoggerFactory.getLogger(VehicleGarageServerAudit.class);
    private static final int X = 271000;
    private static Stage stage;
    private static VehicleGarageDispatchGameTest.Fixture fixture;
    private static JsonObject report;
    private static Path evidence;
    private static int begun, changedAt, restartReadyAt = -1;
    private static JsonArray expectedVehicles, expectedPeople;
    private static MilitaryVehicle departingApc;

    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        if (!Boolean.getBoolean("blacklight.vehicleGarageAudit") || stage == Stage.DONE) return;
        var server = event.getServer();
        try {
            if (stage == null) {
                String phase = System.getProperty("blacklight.vehicleGarageAudit.phase", "");
                if (phase.equals("prepare")) prepare(server);
                else if (phase.equals("verify")) verify(server);
                else throw new IllegalStateException("Choose explicit vehicleGarageAudit.phase=prepare or verify");
            }
            poll(server);
        } catch (Throwable failure) { fail(server, failure); }
    }

    private static void initialize(MinecraftServer server) {
        check(server.isDedicatedServer() && server.isSameThread(), "Use a real normal dedicated-server process on its server thread");
        check(stage == null, "Audit already initialized in this JVM");
        evidence = Path.of(System.getProperty("blacklight.vehicleGarageAudit.file", "vehicle-garage-audit.json")).toAbsolutePath().normalize();
        begun = server.getTickCount();
    }

    public static void prepare(MinecraftServer server) throws IOException {
        initialize(server);
        check(!Files.exists(evidence), "Preserve prior audit evidence and use a fresh disposable run directory: " + evidence);
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        check(level != null, "Overworld is unavailable");
        report = new JsonObject();
        report.addProperty("schemaVersion", 1);
        report.addProperty("loader", "neoforge-1.21.1");
        report.addProperty("preparePid", ProcessHandle.current().pid());
        report.addProperty("startedUtc", Instant.now().toString());
        report.addProperty("seed", level.getSeed());
        report.addProperty("dimension", level.dimension().location().toString());
        report.addProperty("blackwatch", false);
        report.addProperty("status", "PREPARING_REAL_NATIVE_DISPATCH");
        fixture = VehicleGarageDispatchGameTest.Fixture.create(level, X, false);
        report.addProperty("fixtureX", fixture.x);
        report.addProperty("baseId", fixture.baseId);
        stage = Stage.MUSTER;
        write();
    }

    public static void verify(MinecraftServer server) throws IOException {
        initialize(server);
        check(Files.isRegularFile(evidence), "Missing first-process checkpoint: " + evidence);
        report = JsonParser.parseString(Files.readString(evidence, StandardCharsets.UTF_8)).getAsJsonObject();
        check(report.get("schemaVersion").getAsInt() == 1 && report.get("status").getAsString().equals("PHASE1_SAVED"), "Only a completed prepare checkpoint may be verified");
        check(report.get("preparePid").getAsLong() != ProcessHandle.current().pid(), "Verification must use a second operating-system process");
        check(report.get("loader").getAsString().equals("neoforge-1.21.1"), "Loader/version changed between processes");
        var dimension = Objects.requireNonNull(ResourceLocation.tryParse(report.get("dimension").getAsString()));
        var level = server.getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        check(level != null && level.getSeed() == report.get("seed").getAsLong(), "World seed or dimension changed");
        fixture = VehicleGarageDispatchGameTest.Fixture.reopen(level, report.get("fixtureX").getAsInt(), report.get("blackwatch").getAsBoolean(),
                report.get("baseId").getAsString(), report.get("garageId").getAsString());
        report.getAsJsonObject("slots").entrySet().forEach(e -> fixture.vehicles.put(e.getKey(), UUID.fromString(e.getValue().getAsJsonObject().get("uuid").getAsString())));
        expectedVehicles = report.getAsJsonArray("vehicles"); expectedPeople = report.getAsJsonArray("people");
        report.addProperty("verifyPid", ProcessHandle.current().pid());
        stage = Stage.RESTART;
    }

    public static void poll(MinecraftServer server) throws IOException {
        if (stage == null || stage == Stage.DONE) return;
        check(server.isSameThread(), "Audit poll requires the server thread");
        int elapsed = server.getTickCount() - begun;
        check(elapsed < 3000, "Audit timed out at " + stage + ": " + fixture.diagnostic());
        switch (stage) {
            case MUSTER -> {
                if (fixture.muster(elapsed)) {
                    fixture.attach(); report.addProperty("garageId", fixture.garageId);
                    report.add("nativePersonnelSlots", slotPeople());
                    stage = Stage.DISPATCH;
                }
            }
            case DISPATCH -> {
                fixture.observeWalking();
                if (fixture.dispatched()) {
                    fixture.verifyDispatch();
                    // Immediate, real core loss prevents the garage from silently
                    // repairing resource changes while the checkpoint is prepared.
                    for (int[] core : LargeBaseBlueprint.get(fixture.blackwatch).commandCores()) {
                        int[] point = fixture.site.world(core[0], core[2]);
                        fixture.level.setBlock(new BlockPos(point[0], fixture.site.floor() + core[1], point[1]), Blocks.AIR.defaultBlockState(), 2);
                    }
                    fixture.vehicle("tank").hurt(fixture.level.damageSources().generic(), 10000);
                    changedAt = elapsed; stage = Stage.SETTLE;
                }
            }
            case SETTLE -> {
                if (elapsed - changedAt < 20) return;
                check(fixture.garage().slots().get("tank").state() == VehicleGarageData.State.LOST, "Actual destroyed tank was not recorded as permanently lost");
                for (var soldier : fixture.people.values()) if (!soldier.isPassenger()) soldier.setNoAi(true);
                // Let the existing delayed core/home system settle before recording
                // its legitimate current-home result for the restart comparison.
                if (elapsed - changedAt < 400) return;
                var truck = fixture.vehicle("truck"); var apc = fixture.vehicle("apc");
                check(truck.hurt(fixture.level.damageSources().generic(), 17) && apc.hurt(fixture.level.damageSources().generic(), 23), "Real mechanical damage was refused");
                check(truck.inventory().addFuel(1000)==1000 && apc.inventory().addFuel(1000)==1000, "Legacy finite fuel fixture was not initialized");
                check(truck.inventory().consumeFuel(137) && apc.inventory().consumeFuel(211), "Dormant finite fuel API was unavailable");
                check(apc.inventory().tryFire("autocannon", fixture.level.getGameTime()), "Actual autocannon inventory/cooldown consumption was refused");
                truck.syncVehicleState(); apc.syncVehicleState();
                var supply = (Container) fixture.level.getBlockEntity(fixture.garageOrigin.offset(25, 1, 2));
                check(supply != null && supply.removeItem(0, 1).getCount() == 1, "Actual repair part was not removed"); supply.setChanged();
                var shells = (Container) fixture.level.getBlockEntity(fixture.garageOrigin.offset(15, 1, 2));
                check(shells != null && shells.removeItem(1, 3).getCount() == 3, "Actual HE shells were not removed"); shells.setChanged();
                int infantryReloads = prepareInfantryReloads();
                check(infantryReloads > 0, "No actual original infantry gun could perform a finite typed reload before saving");
                report.addProperty("infantryFiniteReloads", infantryReloads);
                fixture.driveApcOutAndReleaseChunks(); departingApc = apc;
                expectedVehicles = new JsonArray(); expectedVehicles.add(vehicleState(truck)); expectedVehicles.add(vehicleState(apc));
                expectedPeople = peopleState();
                var marker = fixture.garageOrigin.offset(-10, 1, 35); fixture.level.setBlock(marker, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
                report.add("marker", position(marker)); report.add("vehicles", expectedVehicles); report.add("people", expectedPeople);
                report.add("containers", containerState()); report.add("slots", garageSlots());
                report.addProperty("lostTankUuid", fixture.vehicles.get("tank").toString());
                report.addProperty("unknownApcUuid", apc.getUUID().toString());
                changedAt = elapsed; stage = Stage.UNLOAD;
            }
            case UNLOAD -> {
                fixture.verifyIdentityLedger();
                check(elapsed - changedAt < 850, "Distant APC did not unload from the actual server chunk/entity managers");
                if (!fixture.apcActuallyUnloaded()) return;
                check(departingApc.isRemoved() && departingApc.getRemovalReason() == net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK,
                        "APC disappeared without an actual chunk unload removal");
                check(garageSlots().equals(report.getAsJsonObject("slots")), "Unknown/unloaded vehicle changed its one-time slot");
                check(containerState().equals(report.getAsJsonArray("containers")), "Core loss or unload refilled a real container");
                report.addProperty("apcActuallyUnloaded", true);
                report.addProperty("status", "PHASE1_SAVED"); report.addProperty("prepareCheckpointUtc", Instant.now().toString());
                report.addProperty("saveMeaning", "Expected actual state before normal saving shutdown; durability requires the separate verify process");
                write(); fixture.restoreSettings(); stage = Stage.DONE;
                LOG.info("VEHICLE_GARAGE_AUDIT PHASE1_SAVED {}; restart this same run directory with phase=verify", evidence);
                server.halt(false);
            }
            case RESTART -> {
                if (!allExpectedEntitiesLoaded()) { check(elapsed < 500, "Saved vehicle/crew entity regions did not load"); return; }
                if (restartReadyAt < 0) restartReadyAt = elapsed;
                if (elapsed - restartReadyAt < 80) return; // Let normal lifecycle/dispatch run before comparing.
                check(slotPeople().equals(report.getAsJsonArray("nativePersonnelSlots")), "Original personnel UUID slots changed after restart");
                check(garageSlots().equals(report.getAsJsonObject("slots")), "Issued/unknown/lost garage identities changed after restart");
                for (var expected : expectedVehicles) {
                    var value = expected.getAsJsonObject(); var actual = (MilitaryVehicle) fixture.level.getEntity(UUID.fromString(value.get("uuid").getAsString()));
                    check(vehicleState(actual).equals(value), "Saved vehicle UUID/HP/fuel/ammunition/cooldown/crew/home changed: " + value.get("uuid"));
                }
                check(peopleState().equals(expectedPeople), "Actual soldier UUID, health, equipment ammunition, role, unit, home or seat changed");
                check(containerState().equals(report.getAsJsonArray("containers")), "Removed real box supplies were restored after restart");
                check(fixture.level.getEntity(UUID.fromString(report.get("lostTankUuid").getAsString())) == null, "Destroyed tank respawned after restart");
                check(fixture.level.getBlockState(readPosition(report.getAsJsonArray("marker"))).is(Blocks.DIAMOND_BLOCK), "Saved world marker changed or fixture was rebuilt");
                fixture.verifyIdentityLedger();
                report.addProperty("status", "PASS"); report.addProperty("verifiedUtc", Instant.now().toString());
                write(); fixture.restoreSettings(); stage = Stage.DONE;
                LOG.info("VEHICLE_GARAGE_AUDIT PASS two processes, real native people, slots and physical inventory: {}", evidence);
                server.halt(false);
            }
            default -> { }
        }
    }

    private static boolean allExpectedEntitiesLoaded() {
        for (var raw : expectedVehicles) if (!(fixture.level.getEntity(UUID.fromString(raw.getAsJsonObject().get("uuid").getAsString())) instanceof MilitaryVehicle)) return false;
        for (var raw : expectedPeople) {
            UUID id = UUID.fromString(raw.getAsJsonObject().get("uuid").getAsString());
            if (!(fixture.level.getEntity(id) instanceof MilitarySoldier soldier)) return false;
            fixture.people.put(id, soldier);
        }
        return true;
    }
    private static JsonArray slotPeople() {
        var result = new JsonArray(); for (UUID id : fixture.slots()) result.add(id == null ? "UNISSUED" : id.toString()); return result;
    }
    private static JsonObject garageSlots() {
        var result = new JsonObject();
        for (String chassis : VehicleGarageData.CHASSIS) { var slot = fixture.garage().slots().get(chassis); var value = new JsonObject();
            value.addProperty("uuid", slot.vehicle() == null ? "UNISSUED" : slot.vehicle().toString()); value.addProperty("state", slot.state().name()); result.add(chassis, value); }
        return result;
    }
    private static JsonObject vehicleState(MilitaryVehicle vehicle) {
        var result = new JsonObject(); result.addProperty("uuid", vehicle.getUUID().toString()); result.addProperty("chassis", vehicle.chassisId());
        result.addProperty("health", vehicle.health()); result.add("inventory", JSON.toJsonTree(vehicle.inventory().snapshot()));
        result.addProperty("origin", vehicle.nativeOrigin()); result.addProperty("originDimension", vehicle.nativeDimension()); result.addProperty("home", vehicle.currentHome());
        var crew = new JsonObject(); for (var passenger : vehicle.getPassengers()) crew.addProperty(passenger.getUUID().toString(), vehicle.seatOf(passenger.getUUID())); result.add("crew", crew);
        return result;
    }
    private static JsonArray peopleState() {
        var result = new JsonArray();
        for (UUID id : Arrays.stream(fixture.slots()).filter(Objects::nonNull).sorted().toList()) {
            var soldier = fixture.people.get(id); check(soldier != null, "Expected real native person is absent: " + id);
            var value = new JsonObject(); value.addProperty("uuid", id.toString()); value.addProperty("health", soldier.getHealth());
            value.addProperty("role", soldier.commandRole().id()); value.addProperty("rank", soldier.rank().id()); value.addProperty("unit", String.valueOf(soldier.unitId()));
            value.addProperty("magazine", soldier.magazineAmmo()); value.addProperty("reserve", soldier.reserveAmmo());
            var saved = soldier.saveWithoutId(new net.minecraft.nbt.CompoundTag());
            check(saved.contains("MilitaryLoadout", 10), "Actual infantry typed inventory was not serialized");
            value.addProperty("typedLoadout", saved.getCompound("MilitaryLoadout").toString());
            value.addProperty("handItems", saved.getList("HandItems", 10).toString());
            value.addProperty("stowedPrimary", saved.getCompound("MilitaryStowedWeapon").toString());
            value.addProperty("stowedSecondary", saved.getCompound("MilitaryStowedSecondary").toString());
            value.addProperty("equipped", soldier.equipped());
            value.addProperty("weaponInitialized", saved.getBoolean("MilitaryWeaponInitialized"));
            value.addProperty("origin", soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG));
            value.addProperty("home", soldier.getPersistentData().getString(MilitaryHomeSystem.HOME_BASE_TAG));
            value.addProperty("homeDimension", soldier.getPersistentData().getString(MilitaryHomeSystem.HOME_DIMENSION_TAG));
            var vehicle = soldier.getVehicle(); value.addProperty("vehicle", vehicle == null ? "NONE" : vehicle.getUUID().toString());
            value.addProperty("seat", vehicle instanceof MilitaryVehicle military ? military.seatOf(id) : "NONE"); result.add(value);
        }
        return result;
    }
    /** Spend actual chamber stock, then run the real production reload; no new person, gun or ammunition is issued. */
    private static int prepareInfantryReloads() {
        final java.lang.reflect.Method reload;
        try { reload = MilitarySoldier.class.getDeclaredMethod("readyWeapon"); reload.setAccessible(true); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual infantry reload entry is unavailable", failure); }
        int count = 0;
        for (var soldier : fixture.people.values()) {
            var stack = soldier.getMainHandItem();
            if (!(stack.getItem() instanceof com.codex.blacklight.firearms.FirearmItem gun)) continue;
            var definition = gun.definition(); int reserve = soldier.reserveAmmo(definition.ammoId());
            var before = com.codex.blacklight.firearms.FirearmItem.state(stack, reserve);
            if (before.magazine() == 0 || reserve == 0 || before.readyAt() > fixture.level.getGameTime()) continue;
            com.codex.blacklight.firearms.FirearmItem.store(stack, new com.codex.blacklight.firearms.AmmoState(0, 0, before.readyAt()));
            try { reload.invoke(soldier); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Actual finite infantry reload failed", failure); }
            int transferred = Math.min(definition.capacity(), reserve);
            check(soldier.magazineAmmo() == transferred && soldier.reserveAmmo(definition.ammoId()) == reserve - transferred,
                    "Infantry reload did not debit its exact matching typed reserve");
            check(com.codex.blacklight.firearms.FirearmItem.state(stack, 0).readyAt() == fixture.level.getGameTime() + definition.reloadTicks(),
                    "Infantry reload did not store its real gun cooldown");
            count++;
        }
        return count;
    }
    private static JsonArray containerState() {
        var result = new JsonArray();
        for (int x : new int[]{5, 15, 25}) {
            var at = fixture.garageOrigin.offset(x, 1, 2); check(fixture.level.getBlockEntity(at) instanceof Container, "Real garage box missing: " + at);
            var inventory = (Container) fixture.level.getBlockEntity(at); var value = new JsonObject(); value.add("position", position(at)); var items = new JsonArray();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) { var stack = inventory.getItem(slot); if (stack.isEmpty()) continue;
                var item = new JsonObject(); item.addProperty("slot", slot); item.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()); item.addProperty("count", stack.getCount()); items.add(item); }
            value.add("items", items); result.add(value);
        }
        return result;
    }
    private static JsonArray position(BlockPos at) { var result = new JsonArray(); result.add(at.getX()); result.add(at.getY()); result.add(at.getZ()); return result; }
    private static BlockPos readPosition(JsonArray at) { return new BlockPos(at.get(0).getAsInt(), at.get(1).getAsInt(), at.get(2).getAsInt()); }
    private static void check(boolean condition, String message) { VehicleGarageDispatchGameTest.require(condition, message); }
    private static void write() throws IOException {
        Files.createDirectories(evidence.getParent()); var temporary = evidence.resolveSibling(evidence.getFileName() + ".tmp");
        Files.writeString(temporary, JSON.toJson(report) + "\n", StandardCharsets.UTF_8);
        try { Files.move(temporary, evidence, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
        catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, evidence, StandardCopyOption.REPLACE_EXISTING); }
    }
    private static void fail(MinecraftServer server, Throwable failure) {
        stage = Stage.DONE; LOG.error("VEHICLE_GARAGE_AUDIT FAIL evidence=" + evidence, failure);
        try { if (report == null) report = new JsonObject(); report.addProperty("status", "FAIL"); report.addProperty("error", failure.toString());
            var trace = new StringWriter(); failure.printStackTrace(new PrintWriter(trace)); report.addProperty("stackTrace", trace.toString()); report.addProperty("failedUtc", Instant.now().toString());
            if (evidence != null) write(); if (fixture != null) fixture.restoreSettings();
        } catch (Throwable writingFailure) { LOG.error("VEHICLE_GARAGE_AUDIT evidence write failed", writingFailure); }
        server.halt(false);
    }
    private VehicleGarageServerAudit() { }
}
