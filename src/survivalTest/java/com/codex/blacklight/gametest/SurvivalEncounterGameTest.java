package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.world.GreeneHost;
import com.codex.blacklight.combat.ConsumeSystem;
import com.codex.blacklight.combat.GreeneCloseAttack;
import com.codex.blacklight.registry.ModItems;
import com.codex.blacklight.world.BossSiteBlock;
import com.codex.blacklight.world.SurvivalGuide;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
@net.neoforged.fml.common.EventBusSubscriber(modid="blacklight")
public final class SurvivalEncounterGameTest {
    @net.neoforged.bus.api.SubscribeEvent public static void enableTestStructures(net.neoforged.neoforge.event.server.ServerAboutToStartEvent event){
        if(!(event.getServer() instanceof GameTestServer)) return;
        var options=event.getServer().getWorldData().worldGenOptions();
        java.lang.reflect.Field structures;
        try {
            // GameTestServer hardcodes generateStructures=false, regardless of the test preset.
            structures = java.util.Arrays.stream(options.getClass().getDeclaredFields())
                    .filter(f -> f.getType() == boolean.class && !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                    .filter(f -> {
                        try {
                            f.setAccessible(true);
                            return f.getBoolean(options.withStructures(true)) && !f.getBoolean(options.withStructures(false));
                        } catch (IllegalAccessException e) { throw new AssertionError(e); }
                    }).findFirst().orElseThrow();
            structures.setBoolean(options, true);
        } catch (IllegalAccessException e) { throw new AssertionError(e); }
    }

    static ServerPlayer join(GameTestHelper h, String name) {
        var profile = new GameProfile(UUID.randomUUID(), name);
        var server = h.getLevel().getServer();
        var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false);
        var player = new ServerPlayer(server, h.getLevel(), profile, cookie.clientInformation());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
        server.getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.setPos(h.absoluteVec(new net.minecraft.world.phys.Vec3(16, 2, 30)));
        return player;
    }
    static void finishWithMelee(ServerPlayer player, net.minecraft.world.entity.Mob target) {
        var attack = player.damageSources().playerAttack(player);
        target.hurt(attack, 1000000);
        if (com.codex.blacklight.combat.ConsumeLastChance.awaitingFinisher(target)) {
            target.invulnerableTime = 0;
            target.hurt(player.damageSources().playerAttack(player), 1000000);
        }
    }

    @GameTest(template="quarantine_post",timeoutTicks=420)
    public static void gateProducesOneTrackableEvolved(GameTestHelper h) { escapee(h,false); }

    @GameTest(template="quarantine_post",batch="quarantine_consume",timeoutTicks=420)
    public static void consumingEscapeePaysOneGanglion(GameTestHelper h) { escapee(h,true); }

    private static void escapee(GameTestHelper h,boolean consume) {
        var level=h.getLevel();
        var gate=h.absolutePos(new BlockPos(12,2,9));
        var player=join(h,consume?"track-consumer":"track-reader");
        var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(gate),
                net.minecraft.core.Direction.UP,gate,false);
        var area=new AABB(gate).inflate(32);
        player.setPos(gate.getX()+.5,gate.getY(),gate.getZ()-2);
        var records=(net.minecraft.world.level.block.entity.BarrelBlockEntity)level.getBlockEntity(h.absolutePos(new BlockPos(4,2,3)));
        h.assertTrue(records!=null && records.countItem(ModItems.STATION_RECORD.get())==1,"quarantine records are in the manifest barrel");
        player.gameMode.useItemOn(player,level,player.getMainHandItem(),hand,hit);
        player.gameMode.useItemOn(player,level,player.getMainHandItem(),hand,hit);
        h.startSequence().thenWaitUntil(()->{
            var hosts=level.getEntitiesOfClass(com.codex.blacklight.entity.BlacklightInfectedHost.class,area,
                    host->host.getPersistentData().getLong("QuarantineGate")==gate.asLong());
            h.assertTrue(hosts.size()==1 && hosts.get(0).isEvolved(),"one escapee completes its molt");
            var host=hosts.get(0);
            if (consume) {
                var reward=com.codex.blacklight.power.AssimilationSystem.quote(player,host);
                h.assertTrue(!com.codex.blacklight.power.AssimilationSystem.settle(player,host,reward,false),"living escapee gives nothing");
                host.discard();
                h.assertTrue(com.codex.blacklight.power.AssimilationSystem.settle(player,host,reward,false)
                        && !com.codex.blacklight.power.AssimilationSystem.settle(player,host,reward,false),"successful consumption settles once");
            } else {
                host.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
                h.assertTrue(!host.isAlive() && !host.claimGanglion(),"accepted death settles the ganglion");
            }
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area,
                    drop->drop.getItem().is(ModItems.EVOLVED_GANGLION.get()));
            h.assertTrue(drops.size()==1 && drops.get(0).getItem().getCount()==1,"one physical ganglion for either path");
            level.getServer().getPlayerList().remove(player);
        }).thenSucceed();
    }

    @GameTest(template="lost_convoy",batch="futian_dispatch",timeoutTicks=3000)
    public static void seededDispatchProvidesThreeApproaches(GameTestHelper h) {
        var player=join(h,"dispatch-scout");
        var city=player.server.getLevel(com.codex.blacklight.city.CityTravel.WORLD);
        var origin=com.codex.blacklight.city.MotherPlazaStructure.origin(city.getSeed());
        var area=new AABB(origin.offset(16,2,40)).inflate(70);
        var door=origin.offset(2,1,28);
        var desk=origin.offset(8,1,68);
        for(int x=0;x<3;x++)for(int z=0;z<5;z++)city.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,true);
        player.teleportTo(city,desk.getX()+.5,desk.getY(),desk.getZ()+2.5,180,0);
        h.assertTrue(com.codex.blacklight.city.MotherPlazaStructure.chart(city),"seeded district available");
        var deskState=city.getBlockState(desk);
        var truck=new com.codex.blacklight.entity.MilitaryVehicle[1];
        var state=player.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
        state.infect(); state.setPrototypeMode(true);
        h.startSequence().thenWaitUntil(()->{
            var vehicles=city.getEntitiesOfClass(com.codex.blacklight.entity.MilitaryVehicle.class,area,
                    v->v.getPersistentData().contains("FutianDispatchOrigin"));
            h.assertTrue(vehicles.size()==1,"one physical dispatch truck at the checkpoint");
            truck[0]=vehicles.get(0);
        }).thenExecute(()->{
            com.codex.blacklight.world.FutianDispatch.read(city,desk,player);
            h.assertTrue(!city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"civilian cannot open the side door");
            var guard=city.getEntitiesOfClass(com.codex.blacklight.entity.MilitarySoldier.class,area,g->!g.isPassenger()).get(0);
            com.codex.blacklight.disguise.DisguiseSystem.captured(player,com.codex.blacklight.disguise.DisguiseIdentity.capture(guard));
            state.setDisguised(true);
            player.getPersistentData().remove("FutianWitnessUntil");
            com.codex.blacklight.world.FutianDispatch.read(city,desk,player);
            h.assertTrue(city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),"military clearance unlocks physical side door");
            city.removeBlock(desk, false);
            player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).remove("blacklight_dispatch_intersection");
            player.moveTo(origin.getX()+5.5,origin.getY()+9,origin.getZ()+58.5,0,0);
        }).thenWaitUntil(()->{
            h.assertTrue(player.getPersistentData().hasUUID("DispatchTail"),"scout identifies departing truck from roof");
            player.moveTo(origin.getX()+53,origin.getY()+12,origin.getZ()+74,0,0);
        }).thenWaitUntil(()->{
            h.assertTrue(!player.getPersistentData().hasUUID("DispatchTail"),"breaking sight loses the outbound truck");
            h.assertTrue(!player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).contains("blacklight_dispatch_intersection"),"loss gives no unearned street coordinates");
            player.moveTo(origin.getX()+5.5,origin.getY()+9,origin.getZ()+58.5,0,0);
        }).thenWaitUntil(()->{
            h.assertTrue(player.getPersistentData().hasUUID("DispatchTail"),"next shift can be followed after losing the first");
        }).thenWaitUntil(()->{
            h.assertTrue(truck[0].getZ()<origin.getZ()+52,"truck drives around the first bend");
            player.moveTo(origin.getX()+6,origin.getY()+9,origin.getZ()+38,0,0);
        }).thenWaitUntil(()->{
            h.assertTrue(player.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG).contains("blacklight_dispatch_intersection"),
                    "following to the unloading point records the intersection, truck="+truck[0].position()+" stop="+truck[0].getPersistentData().getInt("DispatchStop")+" scout="+player.position());
            var driver=truck[0].occupant(com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER);
            h.assertTrue(driver!=null,"dispatch driver remains aboard at delivery");
            var seat=truck[0].definition().seats().stream().filter(s -> s.job()==com.codex.blacklight.military.vehicle.VehicleDefinition.Job.DRIVER).findFirst().orElseThrow();
            var cab=truck[0].definition().mountWorld(seat.anchorId(),new com.codex.blacklight.military.vehicle.VehicleDefinition.Vec3(truck[0].getX(),truck[0].getY(),truck[0].getZ()),truck[0].getYRot(),0,0);
            player.moveTo(cab.x()+1.8,truck[0].getY(),cab.z(),90,0);
            h.assertTrue(com.codex.blacklight.military.vehicle.VehicleHijackSystem.request(player,truck[0]),"normal cab hijack starts");
        }).thenWaitUntil(()->{
            h.assertTrue(player.getVehicle()==truck[0],"physical hijack transfers driver seat");
            state.setDisguised(false);
            var patrol=city.getEntitiesOfClass(com.codex.blacklight.entity.MilitarySoldier.class,area,g->!g.isPassenger()).get(0);
            patrol.moveTo(truck[0].getX()+5,truck[0].getY(),truck[0].getZ(),90,0);
        }).thenWaitUntil(()->{
            h.assertTrue(player.getPersistentData().getLong("FutianWitnessUntil")>city.getGameTime(),"guard witnesses a seized vehicle");
            h.assertTrue(player.getVehicle()==truck[0] && !truck[0].getPersistentData().contains("FutianDispatchOrigin"),"driver keeps the vehicle");
            player.stopRiding();player.moveTo(origin.getX()+80,origin.getY()+20,origin.getZ()+100,0,0);
        }).thenIdle(140).thenExecute(()->{
            h.assertTrue(player.getPersistentData().getLong("FutianWitnessUntil")<=city.getGameTime(),"breaking sight clears local pursuit");
        }).thenWaitUntil(()->{
            h.assertTrue(!city.getBlockState(door).getValue(net.minecraft.world.level.block.DoorBlock.OPEN),
                    "the unlocked door expires even after the dispatch desk is destroyed");
        }).thenExecute(()->{
            city.setBlockAndUpdate(desk,deskState);
            city.getServer().getPlayerList().remove(player);
            for(int x=0;x<3;x++)for(int z=0;z<5;z++)city.setChunkForced((origin.getX()>>4)+x,(origin.getZ()>>4)+z,false);
        }).thenSucceed();
    }

    static boolean done(ServerPlayer p, String name) {
        var advancement = p.server.getAdvancements().get(ResourceLocation.fromNamespaceAndPath("blacklight", name));
        return advancement != null && p.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    @GameTest(template="lost_convoy", timeoutTicks=60)
    public static void convoyRejectsObstructionAndCreditsBothFighters(GameTestHelper h) { fight(h); }

    @GameTest(template="mother_plaza", batch="rotated_pump", rotationSteps=1, timeoutTicks=880)
    public static void rotatedPlazaTransformationAndConsumption(GameTestHelper h) {
        var level=h.getLevel();
        var console=h.absolutePos(new BlockPos(16,2,28));
        var pad=h.absolutePos(new BlockPos(16,2,16));
        // The rotated street now spans five chunks. Keep both ends of the scene ticking on a headless server.
        java.util.List<net.minecraft.world.level.ChunkPos> held=new java.util.ArrayList<>();
        for(int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) {
            var c=new net.minecraft.world.level.ChunkPos((pad.getX()>>4)+x,(pad.getZ()>>4)+z);
            if (level.setChunkForced(c.x,c.z,true)) held.add(c);
        }
        var audience=join(h,"pump-operator");
        var finisher=join(h,"plaza-finisher");
        var forms=new GreeneHost[2];
        var boss=new EncounterMob[1];
        var bounds=new AABB(pad).inflate(4);
        h.startSequence().thenWaitUntil(()->{
            var waiting=level.getEntitiesOfClass(GreeneHost.class,bounds,e->e.stage()==GreeneHost.WAITING);
            h.assertTrue(waiting.size()==1,"human staging: count="+waiting.size()+" state="+level.getBlockState(console)+" pad="+level.getBlockState(pad)+" ticking="+level.isPositionEntityTicking(pad)+" nearby="+level.hasNearbyAlivePlayer(console.getX(),console.getY(),console.getZ(),48));
            forms[0]=waiting.get(0);
        }).thenExecute(()->{
            h.assertTrue(!ConsumeSystem.eligible(forms[0]),"the healthy actor cannot be grabbed");
            h.assertTrue(BossSiteBlock.activate(level,console,audience).equals("pump_key_needed"),"empty hands cannot start Greene");
            audience.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(ModItems.BLOODTOX_COMMAND_KEY.get()));
            h.assertTrue(BossSiteBlock.activate(level,console,audience).equals("pump_key_needed"),"legacy key requires a Heavy Armor Core");
            audience.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ModItems.BLOODTOX_STARTER.get().getDefaultInstance());
            var fill=pad.above(2);
            level.setBlockAndUpdate(fill,Blocks.OBSIDIAN.defaultBlockState());
            h.assertTrue(BossSiteBlock.activate(level,console,audience).equals("obstructed"),"blocked transformation pad");
            level.setBlockAndUpdate(fill,Blocks.AIR.defaultBlockState());
            h.assertTrue(BossSiteBlock.activate(level,console,audience).equals("plaza_started"),"pressure ignites the transformation");
            h.assertTrue(BossSiteBlock.activate(level,console,finisher).equals("active"),"second pump use must not start another transformation");
            h.assertTrue(forms[0].stage()==GreeneHost.CHANGING && level.getEntitiesOfClass(EncounterMob.class,bounds).isEmpty(),"the same human changes before Mother appears");
        }).thenIdle(15).thenExecute(()->{
            var saved=forms[0].saveWithoutId(new net.minecraft.nbt.CompoundTag());
            int age=(int)forms[0].age(0);
            forms[0].load(saved);
            h.assertTrue(forms[0].stage()==GreeneHost.CHANGING && age>0 && forms[0].age(0)==age,
                    "restoring the transformation continues from the same frame");
            level.setBlockAndUpdate(pad.above(2),Blocks.OBSIDIAN.defaultBlockState());
        }).thenWaitUntil(()->{
            h.assertTrue(forms[0].stage()==GreeneHost.BLOCKED,"late obstruction pauses emergence without losing the encounter");
        }).thenExecute(()->{
            level.setBlockAndUpdate(pad.above(2),Blocks.AIR.defaultBlockState());
            // Forge's buried GameTest fixture has natural gravel falling from the ceiling above the template.
            for(var at:BlockPos.betweenClosed(pad.offset(-5,0,-5),pad.offset(5,14,5)))
                if(level.getBlockState(at).is(Blocks.GRAVEL)) level.setBlockAndUpdate(at,Blocks.AIR.defaultBlockState());
        }).thenWaitUntil(()->{
            var bodies=level.getEntitiesOfClass(EncounterMob.class,new AABB(pad).inflate(20),e->e.kind()==com.codex.blacklight.entity.EncounterKind.ELIZABETH_GREENE);
            h.assertTrue(bodies.size()==1 && forms[0].stage()==GreeneHost.OVERLAP,"Mother rises under the growing shell "+forms[0].stage()+"/"+forms[0].age(0)+" removed="+forms[0].isRemoved()+" at "+forms[0].blockPosition()+" pad="+pad+" bodies="+bodies);
            boss[0]=bodies.get(0);
        }).thenWaitUntil(()->{
            h.assertTrue(boss[0].greeneCloseSide()==GreeneCloseAttack.Side.LEFT && boss[0].greeneCloseAge(0)>=39,"left limb is planted and open to a counter");
        }).thenExecute(()->{
            h.assertTrue(forms[0].isRemoved(),"the visual shell clears at the handoff");
            h.assertTrue(Math.abs(boss[0].getY()-pad.getY())<.05,"Mother stays on top of the plaza during the first attack: "+boss[0].getY());
            int left=GreeneCloseAttack.Side.LEFT.segment();
            h.assertTrue(boss[0].hurtPart(left,finisher.damageSources().playerAttack(finisher),1),"an independently targeted left limb receives damage");
            h.assertTrue(boss[0].greeneCloseSide()==null,"the counter interrupts the opening strike");
            h.assertTrue(level.getBlockEntity(console) instanceof com.codex.blacklight.world.PumpStation pump && pump.running() && pump.dent()==1,
                    "counter deflects the lash across the nose of the running truck");
            boss[0].hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
            var hosts=level.getEntitiesOfClass(GreeneHost.class,bounds,e->e.stage()==GreeneHost.SPENT);
            h.assertTrue(hosts.size()==1 && ConsumeSystem.eligible(hosts.get(0)),"the defeated shell releases one consumable Greene");
            forms[1]=hosts.get(0);
            h.assertTrue(!com.codex.blacklight.infection.BlacklightInfectionSystem.infect(forms[1],audience),
                    "exhausted Greene rejects the infection carried by a virus vial");
            h.assertTrue(!done(audience,"plaza_cleared") && BossSiteBlock.activate(level,console,audience).equals("active"),"consumption still pending");
        }).thenIdle(56).thenExecute(()->{
            h.assertTrue(forms[1].isAlive(), "the original spent human survives the conversion interval");
            var state=finisher.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
            state.infect();state.setPrototypeMode(true);
            var host=forms[1];
            finisher.moveTo(host.getX(),host.getY(),host.getZ()-1.7,0,0);
            finisher.setOnGround(true);
            finisher.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            finisher.getData(com.codex.blacklight.registry.ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
            com.codex.blacklight.combat.CombatTargeting.requestLock(finisher,host.getId());

            h.assertTrue(finisher.isAlive() && !finisher.isRemoved(),"finisher is present: "+finisher.getRemovalReason());
            h.assertTrue(state.isPrototypeMode() && state.isInfected(),"infected grab controls enabled");
            h.assertTrue(com.codex.blacklight.combat.CombatTargeting.selectable(finisher,host) && ConsumeSystem.healthReady(finisher,host),"host permits ordinary target selection");
            h.assertTrue(finisher.hasLineOfSight(host),"clear sight to the returned human: "+finisher.position()+" "+host.position());
            ConsumeSystem.request(finisher);
            h.assertTrue(ConsumeSystem.active(finisher),"normal grab acquires exhausted Greene; aim="+com.codex.blacklight.movement.TraversalSteering.forward(finisher)+" ground="+finisher.onGround()+" blocked="+com.codex.blacklight.combat.TerrainGrabSelection.targetsTerrain(finisher)+" distance="+finisher.distanceTo(host));
            ConsumeSystem.consume(finisher);
            // Embedded connections have no client movement packets to advance the player scene.
            for(int tick=0;tick<220 && ConsumeSystem.active(finisher);tick++) {
                finisher.tickCount++;
                ConsumeSystem.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(finisher));
            }
            h.assertTrue(host.isAlive() && host.stage()==GreeneHost.ESCAPING,"manual consume starts a live escape");
            var separation = host.position().subtract(finisher.position());
            var facing = net.minecraft.world.phys.Vec3.directionFromRotation(0, finisher.getYRot());
            h.assertTrue(separation.dot(facing) >= 0.59 && separation.horizontalDistance() < 1.1,
                    "Greene breaks free within reach in front of the player: " + separation);
            h.assertTrue(!ConsumeSystem.active(finisher) && !ConsumeSystem.eligible(host),"grapple releases control and the escape cannot be acquired again");
            h.assertTrue(!host.beginEscape(audience),"a second finisher cannot start a duplicate escape");
        }).thenWaitUntil(()->{
            h.assertTrue(forms[1].isRemoved() && done(finisher,"plaza_cleared") && done(audience,"plaza_cleared"),
                    "completed consume pays both players: removed="+forms[1].isRemoved()+" operator="+done(audience,"plaza_cleared")+" finisher="+done(finisher,"plaza_cleared"));
        }).thenExecute(()->{
            h.assertTrue(BossSiteBlock.activate(level,console,audience).equals("plaza_started"),"pump rearms after consumption");
            finisher.setPos(audience.getX()+1,audience.getY(),audience.getZ()+1);
        }).thenWaitUntil(()->{
            h.assertTrue(level.getBlockEntity(console) instanceof com.codex.blacklight.world.PumpStation pump && pump.dent()==2,
                    "uninterrupted second opening hits the truck broadside");
        }).thenExecute(()->{
            level.getEntitiesOfClass(GreeneHost.class,bounds).forEach(GreeneHost::discard);
            level.getEntitiesOfClass(EncounterMob.class,bounds).forEach(EncounterMob::discard);
            level.getServer().getPlayerList().remove(finisher);
            level.getServer().getPlayerList().remove(audience);
            for(var c:held) level.setChunkForced(c.x,c.z,false);
        }).thenSucceed();
    }

    @GameTest(template="lost_convoy",batch="opening_charge",timeoutTicks=90)
    public static void convoyDoorReleasesARealCharge(GameTestHelper h) {
        var level = h.getLevel();
        var console = h.absolutePos(new BlockPos(16,2,28));
        var pad = h.absolutePos(new BlockPos(16,2,16));
        var gate = h.absolutePos(new BlockPos(16,2,21));
        var operator = join(h,"opening-driver");
        operator.setPos(console.getX()+.5, console.getY(), console.getZ()+1.5);
        operator.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                ModItems.CONTAINMENT_DECODER.get().getDefaultInstance());
        h.assertTrue(BossSiteBlock.activate(level,console,operator).equals("convoy_started"),"open actual truck");
        var boss = level.getEntitiesOfClass(EncounterMob.class,new AABB(pad).inflate(5)).get(0);
        h.runAfterDelay(10,()->h.assertTrue(boss.move()==com.codex.blacklight.combat.EncounterMove.NONE
                && boss.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(pad))<1,
                "boss waits while the gate is retracting"));
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(level.getBlockState(gate).getValue(com.codex.blacklight.world.ConvoyRail.BENT)
                    && boss.move()==com.codex.blacklight.combat.EncounterMove.CHARGE,
                    "open gate releases its operator-facing charge, move="+boss.move()+" boss="+boss.position()
                    +" operator="+operator.position()+" visible="+boss.hasLineOfSight(operator));
        }).thenExecute(()->{
            boss.discard();
            level.getServer().getPlayerList().remove(operator);
        }).thenSucceed();
    }

    @GameTest(template="lost_convoy",batch="survival_respawn",timeoutTicks=90)
    public static void defeatedOperatorCanRejoinTheSameFight(GameTestHelper h) {
        var level = h.getLevel();
        var console = h.absolutePos(new BlockPos(16,2,28));
        var pad = h.absolutePos(new BlockPos(16,2,16));
        var first = join(h,"returning-pilot");
        SurvivalGuide.remember(first,"lost_convoy",console);
        first.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ModItems.CONTAINMENT_DECODER.get().getDefaultInstance());
        h.assertTrue(BossSiteBlock.activate(level,console,first).equals("convoy_started"),"open convoy before dying");
        var boss = level.getEntitiesOfClass(EncounterMob.class,new AABB(pad).inflate(6)).get(0);
        var players = level.getServer().getPlayerList();
        first.hurt(level.damageSources().genericKill(),Float.MAX_VALUE);
        h.assertTrue(!first.isAlive(),"survivor died during the encounter");
        var returned = players.respawn(first,false,net.minecraft.world.entity.Entity.RemovalReason.KILLED);
        try {
            returned.teleportTo(console.getX()+.5,console.getY(),console.getZ()+1.5);
            var saved=returned.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            h.assertTrue(saved.getLong("blacklight_lost_convoy")==console.asLong(),"remember the street after respawn");
            h.assertTrue(BossSiteBlock.activate(level,console,returned).equals("active"),"return to the same fight without a second key");
            h.assertTrue(level.getEntitiesOfClass(EncounterMob.class,new AABB(pad).inflate(6)).size()==1,"one boss remains");
            finishConvoyFight(h,boss,returned);
            h.assertTrue(done(returned,"convoy_cleared"),"returning participant receives clearance");
        } finally {
            players.remove(returned);
            boss.discard();
        }
        h.succeed();
    }

    // These fixtures exercise convoy ownership, credit and the physical drop. The
    // live GoliathOrganGameTest drives each opening from an intact boss to this saved pose.
    static void finishConvoyFight(GameTestHelper h,EncounterMob boss,net.minecraft.server.level.ServerPlayer player){
        var source=player.damageSources().playerAttack(player);
        boss.hurt(source,1000000);
        h.assertTrue(boss.isAlive(),"normal damage cannot bypass the convoy's organ fight");
        var state=new net.minecraft.nbt.CompoundTag();boss.saveWithoutId(state);
        long now=boss.level().getGameTime();
        state.putInt("EncounterBroken",3);
        state.putInt("GoliathWindow",com.codex.blacklight.combat.GoliathWindow.FACE.ordinal());
        state.putLong("EncounterExposedStart",Math.max(0,now-30));
        state.putLong("EncounterExposed",now+35);
        boss.load(state);
        var face=boss.hurtRegions(0).stream().filter(r->r.bone().equals("jaw")).findFirst().orElseThrow();
        boss.invulnerableTime=0;
        boss.hurtAt(source,100,new com.codex.blacklight.combat.HydraHurtContact.Contact("jaw",face.boxes().get(0).center,
            new net.minecraft.world.phys.Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.CRUSH));
        h.assertTrue(boss.goliathFinisherReady(),"the fallen face unlocks a kill or consumption");
        boss.invulnerableTime=0;boss.hurt(source,100);
        if (com.codex.blacklight.combat.ConsumeLastChance.used(boss) && boss.isAlive()) {
            h.assertTrue(boss.getHealth()==1 && com.codex.blacklight.combat.ConsumeLastChance.awaitingFinisher(boss),
                    "the first lethal attack leaves a final consume opportunity");
            boss.invulnerableTime=0;
            boss.hurt(source,100);
        }
        h.assertTrue(!boss.isAlive(),"the operator completes the ordinary final blow");
    }

    private static void fight(GameTestHelper h) {
        var level = h.getLevel();
        var console = h.absolutePos(new BlockPos(16, 2, 28));
        var pad = h.absolutePos(new BlockPos(16, 2, 16));
        var operator = join(h, "site-operator");
        var ally = join(h, "site-ally");
        try {
            h.assertTrue(BossSiteBlock.activate(level,console,operator).equals("decoder_needed"),"empty hands cannot open the convoy");
            operator.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,ModItems.CONTAINMENT_DECODER.get().getDefaultInstance());
            level.setBlockAndUpdate(pad, Blocks.OBSIDIAN.defaultBlockState());
            h.assertTrue(BossSiteBlock.activate(level, console, operator).equals("obstructed"), "blocked pad must not spend the trigger");
            level.setBlockAndUpdate(pad, Blocks.AIR.defaultBlockState());
            String expected = "convoy_started";
            var start = BossSiteBlock.activate(level, console, operator);
            h.assertTrue(start.equals(expected), "real console and pad should start the fight: " + start);
            h.assertTrue(BossSiteBlock.activate(level, console, ally).equals("active"), "a second operator cannot duplicate the boss");
            var bosses = level.getEntitiesOfClass(EncounterMob.class, new AABB(pad).inflate(5));
            h.assertTrue(bosses.size() == 1 && bosses.get(0).isPersistenceRequired(), "one persistent boss on the rotated pad");
            var boss = bosses.get(0);
            finishConvoyFight(h,boss,ally);
            String victory = "convoy_cleared";
            h.assertTrue(done(operator, victory), "operator earns encounter credit");
            h.assertTrue(done(ally, victory), "attacker earns credit too");
            h.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,boss.getBoundingBox().inflate(3),
                    item -> item.getItem().is(ModItems.HEAVY_ARMOR_CORE.get())).size()==1,"single physical core for both fighters");
            boss.discard();
            h.assertTrue(BossSiteBlock.activate(level, console, operator).equals(expected), "site can be used after the encounter ends");
            level.getEntitiesOfClass(EncounterMob.class, new AABB(pad).inflate(5)).forEach(EncounterMob::discard);
        } finally {
            level.getServer().getPlayerList().remove(ally);
            level.getServer().getPlayerList().remove(operator);
        }
        h.succeed();
    }
    @GameTest(template="frozen_research_station", timeoutTicks=80)
    public static void coldStorageSuppliesAwakeningAndConvoyLead(GameTestHelper h) {
        var p = join(h, "sample-reader");
        var level = h.getLevel();
        try {
            h.assertTrue(done(p, "root"), "new survival player receives the root clue");
            var chest = (ChestBlockEntity)level.getBlockEntity(h.absolutePos(new BlockPos(11, 2, 19)));
            h.assertTrue(chest != null, "cold storage chest is present");
            int vials=0, manifests=0;
            for (int i=0; i<chest.getContainerSize(); i++) {
                var stack=chest.getItem(i);
                if (stack.is(ModItems.BLACKLIGHT_SAMPLE.get())) vials+=stack.getCount();
                if (stack.is(ModItems.CONVOY_MANIFEST.get())) manifests+=stack.getCount();
            }
            h.assertTrue(vials == 3 && manifests == 1, "real station loot contains three vials and one transport manifest");
            var goal = new BlockPos(101, 64, -203);
            SurvivalGuide.remember(p, "lost_convoy", goal);
            var saved = p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
            h.assertTrue(saved.getLong("blacklight_lost_convoy") == goal.asLong(), "map coordinates survive in death-persistent player data");
            var source=p.createCommandSourceStack().withPermission(0);
            var dispatcher=level.getServer().getCommands().getDispatcher();
            try {
                h.assertTrue(dispatcher.execute("blacklightguide", source)==1,"non-operator can recall the route");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) { throw new AssertionError(e); }
        } finally { level.getServer().getPlayerList().remove(p); }
        h.succeed();
    }
@GameTest(template="lost_convoy", timeoutTicks=160)
    public static void hiveIntelligenceAndStationManifestChartGeneratedSites(GameTestHelper h) {
        var p = join(h, "map-reader");
        var level = h.getLevel();
        var options = level.getServer().getWorldData().worldGenOptions();
        try {
            h.assertTrue(options.generateStructures(), "worldgen fixture must allow native structure starts");
            var hive = com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HIVE.get().create(level);
            hive.setPos(p.getX()+3, p.getY(), p.getZ());
            level.addFreshEntity(hive);
            var powers = p.getData(com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);
            powers.infect();
            powers.setPrototypeMode(true);
            finishWithMelee(p, hive);
            h.assertTrue(!hive.isAlive(), "the killed hive settles its loot");
            var drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,hive.getBoundingBox().inflate(3));
            h.assertTrue(drops.stream().anyMatch(drop -> drop.getItem().is(ModItems.MOTHER_INTEL.get())), "real hive defeat drops the plaza dossier");
            powers.setPrototypeMode(false);
            var home=level;
            for (String site : new String[]{"lost_convoy","mother_plaza"}) {
                boolean plaza=site.equals("mother_plaza");
                if(plaza){
                    var file=ModItems.MOTHER_INTEL.get();
                    p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new net.minecraft.world.item.ItemStack(file));
                    h.assertTrue(file.use(home,p,net.minecraft.world.InteractionHand.MAIN_HAND).getObject().is(file),"Overworld preserves the city dossier");
                    level=home.getServer().getLevel(com.codex.blacklight.city.CityTravel.WORLD);
                    h.assertTrue(level!=null,"Blacklight world loaded");
                    var sitePump=com.codex.blacklight.city.MotherPlazaStructure.console(level.getSeed());
                    p.teleportTo(level,sitePump.getX()+.5,sitePump.getY()+1,sitePump.getZ()+3.5,180,0);
                }
                var document=(site.equals("lost_convoy")?ModItems.CONVOY_MANIFEST.get():ModItems.MOTHER_INTEL.get());
                var hand=net.minecraft.world.InteractionHand.MAIN_HAND;
                p.setItemInHand(hand, new net.minecraft.world.item.ItemStack(document));
                var result=document.use(level,p,hand).getObject();
                if(plaza) {
                    h.assertTrue(result.is(document),"the intercepted report supplies a district clue and stays in hand");
                    var distinct=java.util.stream.LongStream.range(0,128).mapToObj(com.codex.blacklight.city.MotherPlazaStructure::origin).distinct().count();
                    h.assertTrue(distinct>=16,"plaza location varies between seeds");
                } else {
                    h.assertTrue(result.is(net.minecraft.world.item.Items.FILLED_MAP),"the convoy still has an exact map");
                    var data=net.minecraft.world.item.MapItem.getSavedData(result,level);
                    h.assertTrue(data!=null && data.dimension==level.dimension(),"convoy map dimension");
                }
                var saved=p.getPersistentData().getCompound(net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG);
                var marker=BlockPos.of(saved.getLong("blacklight_"+site));
                var id=ResourceLocation.fromNamespaceAndPath("blacklight", site);
                var structure=level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE).get(id);
                h.assertTrue(!plaza || marker.equals(com.codex.blacklight.city.MotherPlazaStructure.console(level.getSeed())),"city chart marks the street-side pump");
                var chunk=level.getChunkAt(marker);
                var start=plaza ? level.structureManager().getStructureWithPieceAt(marker,structure)
                        : chunk.getStartForStructure(structure);
                h.assertTrue(start!=null && start.isValid(), "missing native "+site+" at "+chunk.getPos()+" dimension="+level.dimension()
                        +" sets="+level.getChunkSource().getGeneratorState().possibleStructureSets().stream().map(v->v.unwrapKey().orElseThrow().location().toString()).toList()
                        +" structures="+options.generateStructures()+" biome="+level.getBiome(marker).unwrapKey()
                        +" starts="+chunk.getAllStarts().keySet()+" fixture="+level.getChunkSource().getGenerator().getClass().getSimpleName());
                var box=start.getPieces().get(0).getBoundingBox();
                boolean console=false;
                for (var pos : BlockPos.betweenClosed(box.minX(),box.minY(),box.minZ(),box.maxX(),box.minY()+3,box.maxZ())) {
                    var state=level.getBlockState(pos);
                    if (!(state.getBlock() instanceof BossSiteBlock)) continue;
                    console=state.getValue(BossSiteBlock.MOTHER)==site.equals("mother_plaza");
                    var pad=pos.relative(state.getValue(BossSiteBlock.FACING).getOpposite(),12);
                    for (int dx : new int[]{-6,6}) for (int dz : new int[]{-6,6}) level.getChunkAt(pad.offset(dx,0,dz));
                    p.setPos(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5);
                    if(plaza){
                        powers.setPrototypeMode(true);
                        p.setItemInHand(hand,net.minecraft.world.item.ItemStack.EMPTY);
                        var emptyResult=BossSiteBlock.activate(level,pos.immutable(),p);
                        h.assertTrue(emptyResult.equals("pump_key_needed"),"seeded site rejects empty hand: "+emptyResult);
                        verifyCommandKey(h,p);
                        var keys=p.getInventory().items.stream().filter(stack -> stack.is(ModItems.BLOODTOX_COMMAND_KEY.get())).findFirst().orElseThrow();
                        p.getInventory().removeItem(keys);
                        p.setItemInHand(hand,keys);
                        h.assertTrue(BossSiteBlock.activate(level,pos.immutable(),p).equals("pump_key_needed"),"key alone cannot bypass Goliath");
                        p.setItemInHand(hand,craft(p,"bloodtox_starter_from_key",ModItems.HEAVY_ARMOR_CORE.get().getDefaultInstance(),keys));
                        var hit=new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),
                                state.getValue(BossSiteBlock.FACING),pos.immutable(),false);
                        var resultOnPump=p.gameMode.useItemOn(p,level,p.getMainHandItem(),hand,hit);
                        h.assertTrue(resultOnPump.consumesAction() && level.getBlockEntity(pos) instanceof com.codex.blacklight.world.PumpStation pump
                                && pump.running(),"an infected player can start the physical city pump with the normal use action");
                    } else {
                        p.setItemInHand(hand,craft(p,"containment_decoder",ModItems.EVOLVED_GANGLION.get().getDefaultInstance(),
                                net.minecraft.world.item.Items.COPPER_INGOT.getDefaultInstance(),net.minecraft.world.item.Items.REDSTONE.getDefaultInstance()));
                        var status=BossSiteBlock.activate(level,pos.immutable(),p);
                        h.assertTrue(status.equals("convoy_started"),site+" must open on its actual generated pad: "+status);
                    }
                    level.getEntitiesOfClass(GreeneHost.class,new AABB(pad).inflate(6)).forEach(GreeneHost::discard);
                    level.getEntitiesOfClass(EncounterMob.class,new AABB(pad).inflate(6)).forEach(EncounterMob::discard);
                    break;
                }
                h.assertTrue(console, "the physical destination under the map marker has the correct console");
            }
        } finally {
            level.getServer().getPlayerList().remove(p);
        }
        h.succeed();
    }
    static net.minecraft.world.item.ItemStack craft(ServerPlayer player,String recipe,net.minecraft.world.item.ItemStack... items) {
        var level=player.serverLevel();
        var grid=net.minecraft.world.item.crafting.CraftingInput.of(items.length,1,java.util.List.of(items));
        var holder=level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING,grid,level).orElseThrow();
        if (!holder.id().getPath().equals(recipe)) throw new AssertionError("Unexpected recipe "+holder.id());
        return holder.value().assemble(grid,level.registryAccess());
    }
    private static void verifyCommandKey(GameTestHelper h, ServerPlayer player) {
        var level=player.serverLevel();
        var key=ModItems.BLOODTOX_COMMAND_KEY.get();
        int expectedKeys=0;
        for(int scenario=0;scenario<5;scenario++){
            var type=switch(scenario){
                case 1 -> com.codex.blacklight.registry.ModEntities.BLACKWATCH_OFFICER.get();
                case 4 -> com.codex.blacklight.registry.ModEntities.MILITARY_COMMANDER.get();
                default -> com.codex.blacklight.registry.ModEntities.BLACKWATCH_COMMANDER.get();
            };
            var victim=type.create(level);
            victim.setPos(player.position().add(4,0,0));
            level.addFreshEntity(victim);
            var reward=com.codex.blacklight.power.AssimilationSystem.quote(player,victim);
            victim.setHealth(0);
            // Fix the next reward roll to check both ordinary outcomes without flaky tests.
            victim.getRandom().setSeed(scenario==2?2L:0L);
            if(scenario==0) com.codex.blacklight.power.AssimilationSystem.settleKill(player,victim,10,5);
            else {
                h.assertTrue(com.codex.blacklight.power.AssimilationSystem.settle(player,victim,reward,false),"consume settles once");
                h.assertTrue(!com.codex.blacklight.power.AssimilationSystem.settle(player,victim,reward,false),"duplicate consume cannot reroll");
            }
            victim.discard();
            if(scenario>=3)expectedKeys++;
            h.assertTrue(player.getInventory().countItem(key)==expectedKeys,"both factions roll on commander consumption only, case "+scenario);
        }
    }

}
