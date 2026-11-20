package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.combat.CombatTargeting;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.mode.PrototypeModeSystem;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class SurvivalCampaignGameTest {
    private static final InteractionHand HAND = InteractionHand.MAIN_HAND;

    @GameTest(template="quarantine_post",batch="campaign",timeoutTicks=40000)
    public static void oneSurvivorFromColdStorageToMotherCore(GameTestHelper helper) {
        new Run(helper).start();
    }

    // A single connected survivor keeps the same inventory across both fights and the gateway.
    // The fixture supplies prey and travel within a dimension. Every ingredient is collected in-world.
    // Accelerated player damage leaves the normal shield, death and loot handlers in control.
    private static final class Run {
        final GameTestHelper h;
        final ServerLevel home;
        final ServerPlayer player;
        BlockPos floor;
        BlacklightInfectedHost evolved;
        BlacklightHydra hydra;
        EncounterMob mother;
        GreeneHost human;
        BlockPos console, latch, convoyPad, gate;
        final java.util.List<Runnable> releaseTickets = new java.util.ArrayList<>();
        ItemStack manifest, starter;

        Run(GameTestHelper helper) {
            h = helper;
            home = h.getLevel();
            player = SurvivalEncounterGameTest.join(h,"campaign-runner");
        }
        void start() {
            h.assertTrue(player.getInventory().isEmpty() && !BlacklightInfectionSystem.isInfected(player),
                    "new survivor starts without equipment or powers");
            var records = findQuarantinePost();
            player.setItemInHand(HAND,take(records,ModItems.STATION_RECORD.get()));
            player.getInventory().add(take(records,Items.COPPER_INGOT));
            var map = player.getMainHandItem().getItem().use(home,player,HAND).getObject();
            h.assertTrue(map.is(Items.FILLED_MAP),"quarantine shipping record decodes to a station map");
            var marker = BlockPos.of(player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
                    .getLong("blacklight_on_research_station_maps"));
            var structure = home.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .get(new net.minecraft.resources.ResourceLocation("blacklight","frozen_research_station"));
            var box = home.getChunkAt(marker).getStartForStructure(structure).getPieces().get(0).getBoundingBox();
            ChestBlockEntity chest = null;
            var wiring = new java.util.ArrayList<BlockPos>();
            for (var at : BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                if (home.getBlockState(at).is(Blocks.REDSTONE_WIRE)) wiring.add(at.immutable());
                if (home.getBlockState(at).is(Blocks.CHEST) && home.getBlockEntity(at) instanceof ChestBlockEntity candidate
                        && candidate.countItem(ModItems.BLACKLIGHT_SAMPLE.get())>0) chest=candidate;
            }
            h.assertTrue(chest!=null,"station on the map contains the cold-storage samples");
            standAt(chest.getBlockPos().above());
            manifest = take(chest,ModItems.CONVOY_MANIFEST.get());
            player.setItemInHand(HAND,take(chest,ModItems.BLACKLIGHT_SAMPLE.get()));
            h.assertTrue(wiring.size()>=2,"lab service circuit contains recoverable redstone");
            for (var at : wiring.subList(0,2)) {
                standAt(at.above());
                h.assertTrue(player.gameMode.destroyBlock(at),"strip dust from the installed wire");
                player.getInventory().add(drop(home,Vec3.atCenterOf(at),Items.REDSTONE));
            }
            // Camp just outside the mapped lab, above its actual terrain. Keep the
            // sample, prey and planter together in the same entity-ticking area.
            var column = chest.getBlockPos().east(16);
            // Level#getHeight may return minBuildHeight for an as-yet unloaded neighbour.
            // Read the full native terrain before placing the camp and its tall mature hydra.
            home.getChunkAt(column);
            int surface = home.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,column.getX(),column.getZ());
            h.assertTrue(surface>home.getMinBuildHeight()+32,"native surface is ready outside the lab, y="+surface);
            floor = new BlockPos(column.getX(),surface+2,column.getZ());
            for (var at : BlockPos.betweenClosed(floor.offset(-5,0,-5),floor.offset(5,0,5)))
                home.setBlockAndUpdate(at,Blocks.STONE.defaultBlockState());
            for (var at : BlockPos.betweenClosed(floor.offset(-4,1,-4),floor.offset(4,4,4)))
                if (!home.isEmptyBlock(at)) home.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
            standAt(floor.above().east(2));
            holdChunks(home,floor);
            h.startSequence().thenWaitUntil(()->h.assertTrue(ready(home,floor),"survivor's initial area is ticking"))
                    .thenIdle(5).thenExecute(()->{
                        player.setXRot(90);
                        h.assertTrue(player.gameMode.useItem(player,home,player.getMainHandItem(),HAND).consumesAction(),"throw a physical vial");
                    }).thenWaitUntil(()->h.assertTrue(BlacklightInfectionSystem.isInfected(player),"shatter the cold-storage sample at the survivor's feet, player="+player.position()+" camp="+floor+" alive="+player.isAlive()+" vials="+home.getEntitiesOfClass(BlacklightVirusVialEntity.class,new AABB(floor).inflate(8)).size()))
                    .thenExecute(this::releaseEvolved)
                    .thenWaitUntil(this::findEscapee)
                    .thenExecute(this::followManifest)
                    .thenWaitUntil(()->h.assertTrue(ready(home,convoyPad),"approach the loaded convoy"))
                    .thenExecute(this::recoverConvoyCore)
                    .thenIdle(1).thenExecute(this::plantHiveLoot)
                    .thenWaitUntil(()->h.assertTrue(hydra.feeds()==5,"planted tissue has eaten five living animals, feeds="+hydra.feeds()))
                    .thenWaitUntil(this::takeGateway)
                    .thenExecute(()->holdChunks(player.serverLevel(),player.blockPosition()))
                    .thenWaitUntil(()->h.assertTrue(!HydraTransport.active(player),"arrival has released its passenger"))
                    .thenExecute(this::approachPump)
                    .thenWaitUntil(()->h.assertTrue(ready(player.serverLevel(),console),"pump street is ticking"))
                    .thenWaitUntil(this::interceptCargo)
                    .thenExecute(this::startPump)
                    .thenWaitUntil(this::findMother)
                    .thenWaitUntil(this::fightMother)
                    .thenExecute(this::findReturnedHuman)
                    .thenIdle(24).thenExecute(this::consumeHuman)
                    .thenWaitUntil(this::finishMeal)
                    .thenExecute(()->{
                        var trophy = drop(player.serverLevel(),human.position(),ModItems.MOTHER_CORE.get());
                        h.assertTrue(trophy.getCount()==1 && player.isAlive(),"one final core on a living survivor's run");
                        releaseTickets.forEach(Runnable::run);
                        player.server.getPlayerList().remove(player);
                    }).thenSucceed();
        }
        private net.minecraft.world.Container findQuarantinePost() {
            var registry = home.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            var type = registry.getHolderOrThrow(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.STRUCTURE,new net.minecraft.resources.ResourceLocation("blacklight","quarantine_post")));
            var nearest = home.getChunkSource().getGenerator().findNearestMapStructure(home,
                    net.minecraft.core.HolderSet.direct(type),home.getSharedSpawnPos(),8,false);
            h.assertTrue(nearest!=null,"a new survivor can discover a generated quarantine post");
            var box = home.getChunkAt(nearest.getFirst()).getStartForStructure(type.value()).getPieces().get(0).getBoundingBox();
            net.minecraft.world.level.block.entity.BarrelBlockEntity records = null;
            for (var at : BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.maxY(),box.maxZ())) {
                var block = home.getBlockState(at);
                if (block.getBlock() instanceof FieldPostBlock) gate=at.immutable();
                if (block.is(Blocks.BARREL) && home.getBlockEntity(at) instanceof net.minecraft.world.level.block.entity.BarrelBlockEntity barrel
                        && barrel.countItem(ModItems.STATION_RECORD.get())>0) records=barrel;
            }
            h.assertTrue(gate!=null && records!=null,"the natural post contains its damaged gate and its shipping record");
            standAt(records.getBlockPos().above());
            holdChunks(home,gate);
            return records;
        }
        private void releaseEvolved() {
            standAt(gate.above());
            var hit = new BlockHitResult(Vec3.atCenterOf(gate),Direction.UP,gate,false);
            player.gameMode.useItemOn(player,home,player.getMainHandItem(),HAND,hit);
        }
        private void findEscapee() {
            var actors=home.getEntitiesOfClass(BlacklightInfectedHost.class,new AABB(gate).inflate(64),
                    mob->mob.isEvolved() && mob.getPersistentData().getLong("QuarantineGate")==gate.asLong());
            h.assertTrue(actors.size()==1,"the native gate leads to exactly one evolved");
            evolved=actors.get(0);
        }
        private void followManifest() {
            evolved.hurt(player.damageSources().playerAttack(player),1000000);
            var decoder = SurvivalEncounterGameTest.craft(player,"containment_decoder",
                    drop(home,evolved.position(),ModItems.EVOLVED_GANGLION.get()),
                    take(player.getInventory(),Items.COPPER_INGOT),take(player.getInventory(),Items.REDSTONE));
            player.setItemInHand(HAND,manifest);
            var map = manifest.getItem().use(home,player,HAND).getObject();
            h.assertTrue(map.is(Items.FILLED_MAP),"actual manifest decodes to a convoy map");
            var marker = BlockPos.of(player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).getLong("blacklight_lost_convoy"));
            var structure = home.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                    .get(new net.minecraft.resources.ResourceLocation("blacklight","lost_convoy"));
            // The jigsaw's terrain-adaptation bounds extend below the actual road.
            var box = home.getChunkAt(marker).getStartForStructure(structure).getPieces().get(0).getBoundingBox();
            for (var at : BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.minY()+4,box.maxZ())) {
                if (home.getBlockState(at).getBlock() instanceof BossSiteBlock) { latch = at.immutable(); break; }
            }
            h.assertTrue(latch!=null,"map leads to a physical latch");
            standAt(latch.above());
            convoyPad = latch.relative(home.getBlockState(latch).getValue(BossSiteBlock.FACING).getOpposite(),12);
            holdChunks(home,convoyPad);
            player.setItemInHand(HAND,decoder);
        }
        private void recoverConvoyCore() {
            String outcome = BossSiteBlock.activate(home,latch,player);
            h.assertTrue(outcome.equals("convoy_started"),"earned decoder opens convoy: "+outcome+" at "+latch);
            var boss = home.getEntitiesOfClass(EncounterMob.class,new AABB(convoyPad).inflate(5)).get(0);
            SurvivalEncounterGameTest.finishConvoyFight(h,boss,player);
            player.getInventory().add(drop(home,boss.position(),ModItems.HEAVY_ARMOR_CORE.get()));
            h.assertTrue(SurvivalEncounterGameTest.done(player,"convoy_cleared") && player.getMainHandItem().is(ModItems.CONTAINMENT_DECODER.get()),"credit and reusable decoder");
            standAt(floor.above().east(2));
        }
        private void plantHiveLoot() {
            var hive = ModEntities.BLACKLIGHT_HIVE.get().create(home);
            hive.setPos(Vec3.atBottomCenterOf(floor.above().west(4)));
            home.addFreshEntity(hive);
            PrototypeModeSystem.handle(player,true);
            hive.hurt(player.damageSources().playerAttack(player),1000000);
            h.assertTrue(!hive.isAlive(),"awakened combat destroys the structural hive");
            PrototypeModeSystem.handle(player,false);
            var intel = drop(home,hive.position(),ModItems.MOTHER_INTEL.get());
            var tissue = drop(home,hive.position(),ModItems.LIVING_HIVE_TISSUE.get());
            player.setItemInHand(HAND,tissue);
            var hit = new BlockHitResult(Vec3.atCenterOf(floor).add(0,.5,0),Direction.UP,floor,false);
            h.assertTrue(tissue.useOn(new UseOnContext(player,HAND,hit)).consumesAction(),"plant tissue from the same hive");
            hydra = home.getEntitiesOfClass(BlacklightHydra.class,new AABB(floor).inflate(3)).get(0);
            h.assertTrue(hydra.feeds()==0 && hydra.growth()<1,"start with an unfed, immature gateway");
            for (int i=0;i<5;i++) {
                var prey = EntityType.PIG.create(home);
                prey.setNoAi(true);
                prey.moveTo(floor.getX()+.6+(i%3)*.4,floor.getY()+1,floor.getZ()+1.3+(i/3)*.5,0,0);
                home.addFreshEntity(prey);
            }
            // Carry the actual drops through the gateway.
            player.getInventory().add(intel);
        }
        private void takeGateway() {
            if (player.serverLevel().dimension()==CityTravel.WORLD) return;
            if (!hydra.isAlive() && home.getEntity(hydra.getUUID()) instanceof BlacklightHydra samePlanter)
                hydra = samePlanter;
            if (hydra.growth()>=1 && hydra.isAlive() && hydra.action()==0 && home.getGameTime()%100==0)
                player.interactOn(hydra,HAND);
            h.fail("mature gateway should carry the same survivor, growth="+hydra.growth()+" action="+hydra.action()
                    +" survivor="+player.isAlive()+" carrier="+hydra.isAlive()+" sight="+hydra.hasLineOfSight(player)
                    +" health="+hydra.getHealth()+" removed="+hydra.getRemovalReason()+" lastHit="+hydra.getLastDamageSource()+" pos="+hydra.position()+" distance="+hydra.distanceTo(player));
        }
        private void approachPump() {
            var city = player.serverLevel();
            h.assertTrue(player.getInventory().contains(ModItems.MOTHER_INTEL.get().getDefaultInstance())
                    && player.getInventory().contains(ModItems.HEAVY_ARMOR_CORE.get().getDefaultInstance()),"gateway retains drops");
            player.setItemInHand(HAND,take(player.getInventory(),ModItems.MOTHER_INTEL.get()));
            player.getMainHandItem().getItem().use(city,player,HAND);
            console = BlockPos.of(player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG)
                    .getLong("blacklight_mother_plaza"));
            h.assertTrue(city.getBlockState(console).getBlock() instanceof BossSiteBlock,"dossier charts real pump in this city");
            var checkpoint = console.offset(5,0,41);
            standAt(checkpoint.east(4));
            holdChunks(city,console);
            holdChunks(city,checkpoint);
        }
        private void interceptCargo() {
            var city = player.serverLevel();
            var route = new AABB(console).inflate(74);
            var trucks = city.getEntitiesOfClass(MilitaryVehicle.class,route,
                    vehicle->vehicle.getPersistentData().contains("FutianDispatchOrigin")
                            && !vehicle.getPersistentData().getBoolean("DispatchUnloaded"));
            h.assertTrue(!trucks.isEmpty(),"wait for a supplied dispatch vehicle at the real plaza");
            var truck = trucks.get(0);
            double yaw = Math.toRadians(truck.getYRot());
            double rear = truck.definition().length()*.5;
            player.moveTo(truck.getX()-Math.sin(yaw)*rear,truck.getY(),truck.getZ()-Math.cos(yaw)*rear,truck.getYRot(),0);
            player.setShiftKeyDown(true);
            try {
                h.assertTrue(player.interactOn(truck,HAND).consumesAction()
                        && truck.getPersistentData().getBoolean("DispatchUnloaded"),"open the actual truck's rear hatch");
            } finally { player.setShiftKeyDown(false); }
        }
        private void startPump() {
            starter = SurvivalEncounterGameTest.craft(player,"bloodtox_starter",take(player.getInventory(),ModItems.HEAVY_ARMOR_CORE.get()),
                    take(player.getInventory(),ModItems.MILITARY_CIRCUIT.get()),take(player.getInventory(),Items.REDSTONE));
            standAt(console.above());
            player.setItemInHand(HAND,starter);
            PrototypeModeSystem.handle(player,true);
            var status = BossSiteBlock.activate(player.serverLevel(),console,player);
            h.assertTrue(status.equals("plaza_started"),"earned starter starts pump, result="+status);
            // Observe the emergence from the same flanking roof used by the dispatch scout.
            standAt(console.offset(-10,8,10));
        }
        private void findMother() {
            var actors = player.serverLevel().getEntitiesOfClass(EncounterMob.class,new AABB(console).inflate(20),
                    boss->boss.kind()==EncounterKind.ELIZABETH_GREENE);
            h.assertTrue(actors.size()==1,"one transformed Mother");
            mother = actors.get(0);
        }
        private long lastSwing = -100;
        private int wavesBroken;
        private void fightMother() {
            // Embedded connections have no incoming player tick packets. Advance the same
            // combat handler as the live client while the survivor switches to prototype form.
            com.codex.blacklight.combat.ClawCombatSystem.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            long now = player.serverLevel().getGameTime();
            if (mother.isAlive() && now - lastSwing >= 12) {
                var cycle = mother.greeneBattle();
                if (cycle.canAttack() && cycle.targetable() && !mother.broken(2)) {
                    for (int i=0;i<3;i++) {
                        h.assertTrue(mother.hurtPart(i,player.damageSources().playerAttack(player),1000000)
                                && mother.broken(i),"targetable guard breaks independently, slot "+i);
                    }
                    wavesBroken++;
                    lastSwing = now;
                } else if (cycle.coreDamageable()) {
                    mother.hurt(player.damageSources().playerAttack(player),1000000);
                    lastSwing = now;
                }
            }
            h.assertTrue(!mother.isAlive(),"fight through collapse and resurfacing, wave="+wavesBroken
                    +" stage="+mother.greeneStage()+" hp="+mother.getHealth());
            h.assertTrue(wavesBroken>=2,"first collapse cannot bypass the next wave");
        }
        private void findReturnedHuman() {
            var actors = player.serverLevel().getEntitiesOfClass(GreeneHost.class,new AABB(console).inflate(24),
                    actor->actor.stage()==GreeneHost.SPENT);
            h.assertTrue(actors.size()==1 && starter.getCount()==1,"final human and reusable pump starter");
            human = actors.get(0);
        }
        private void consumeHuman() {
            player.moveTo(human.getX(),human.getY(),human.getZ()-1.7,0,0);
            player.setDeltaMovement(Vec3.ZERO);
            player.setOnGround(true);
            ModAttachments.getData(player,ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
            if (com.codex.blacklight.power.BiomassSystem.active(player)) {
                player.moveTo(human.getX(),human.getY(),human.getZ()-.6,0,0);
                h.assertTrue(com.codex.blacklight.power.BiomassSystem.edible(player,human),"exhausted Greene accepts contact with the recovering survivor");
                return; // Contact progresses on ordinary server ticks until reformation.
            }
            CombatTargeting.requestLock(player,human.getId());
            ConsumeSystem.request(player);
            h.assertTrue(ConsumeSystem.active(player),"grab exhausted human: hp="+player.getHealth()
                    +" sight="+player.hasLineOfSight(human)+" mode="+PrototypeModeSystem.isActive(player)
                    +" ground="+player.onGround()+" target="+CombatTargeting.locked(player)
                    +" eligible="+ConsumeSystem.eligible(human)+" weak="+ConsumeSystem.healthReady(player,human)
                    +" busy="+com.codex.blacklight.combat.WeaponCombatSystem.busy(player)+" dash="+ ModAttachments.getData(player,ModAttachments.MOVEMENT_STATE).dashTicks()+" charge="+ ModAttachments.getData(player,ModAttachments.MOVEMENT_STATE).chargeTicks()+" wall="+ ModAttachments.getData(player,ModAttachments.MOVEMENT_STATE).wallTraversal().attached()+" passenger="+player.isPassenger()
                    +" terrain="+com.codex.blacklight.combat.TerrainGrabSelection.targetsTerrain(player)
                    +" action="+ModAttachments.getData(player,ModAttachments.CLAW_COMBAT).action()+" dist="+player.distanceTo(human));
            ConsumeSystem.consume(player);
            for (int i=0;i<220 && ConsumeSystem.active(player);i++) {
                player.tickCount++;
                ConsumeSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            }
        }
        private void finishMeal() {
            if (!human.isAlive() && player.serverLevel().getEntity(human.getUUID()) instanceof GreeneHost returned)
                human = returned;
            if (com.codex.blacklight.power.BiomassSystem.active(player)) {
                // The embedded connection sends no movement packets. Crawl into the actual victim
                // and let the normal contact, absorption and reformation timers run on world time.
                if (human.isAlive() && com.codex.blacklight.power.BiomassSystem.state(player).phase()==com.codex.blacklight.power.BiomassState.CRAWLING)
                    player.moveTo(human.getX(),human.getY(),human.getZ()-.5,0,0);
                com.codex.blacklight.power.BiomassSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,player));
            }
            h.assertTrue(SurvivalEncounterGameTest.done(player,"plaza_cleared"),"one survivor completes the plaza; hp="+player.getHealth()
                    +" biomass="+com.codex.blacklight.power.BiomassSystem.state(player)+" human="+human.isAlive()+" gap="+player.distanceTo(human)
                    +" removal="+human.getRemovalReason()+" host="+human.getId()+" inWorld="+(player.serverLevel().getEntity(human.getId())==human)+" grab="+ConsumeSystem.active(player));
        }
        private void holdChunks(ServerLevel level,BlockPos at) {
            var centre = new net.minecraft.world.level.ChunkPos(at);
            for (int x=centre.x-1;x<=centre.x+1;x++) for (int z=centre.z-1;z<=centre.z+1;z++) {
                var chunk = new net.minecraft.world.level.ChunkPos(x,z);
                if(level.setChunkForced(x,z,true))
                    releaseTickets.add(()->level.setChunkForced(chunk.x,chunk.z,false));
            }
        }
        private static boolean ready(ServerLevel level,BlockPos at) {
            var centre = new net.minecraft.world.level.ChunkPos(at);
            for (int x=centre.x-1;x<=centre.x+1;x++) for (int z=centre.z-1;z<=centre.z+1;z++)
                if (!level.isPositionEntityTicking(new BlockPos(x*16,at.getY(),z*16))) return false;
            return true;
        }
        private void standAt(BlockPos at) {
            player.teleportTo(at.getX()+.5,at.getY(),at.getZ()+.5);
            player.setDeltaMovement(Vec3.ZERO);
        }
        private ItemStack take(net.minecraft.world.Container inventory,Item item) {
            for (int i=0;i<inventory.getContainerSize();i++)
                if (inventory.getItem(i).is(item)) return inventory.removeItem(i,1);
            h.fail("Missing inventory item "+item);
            return ItemStack.EMPTY;
        }
        private ItemStack drop(ServerLevel level,Vec3 center,Item item) {
            var drops = level.getEntitiesOfClass(ItemEntity.class,new AABB(center,center).inflate(8),entity->entity.getItem().is(item));
            h.assertTrue(drops.size()==1,"Expected one drop of "+item+" at "+center+", got "+drops.size());
            var entity = drops.get(0);
            var stack = entity.getItem().copy();
            entity.discard();
            return stack;
        }
    }
}
