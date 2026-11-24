package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.GreeneMolt;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.registry.EncounterEntities;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MoltRoarGameTest {
    @GameTest(template="blackwatch_arena", batch="molt_finale", timeoutTicks=80)
    public static void bloodMistHasOnePhysicalFrontAcrossReload(GameTestHelper h) {
        var level = h.getLevel();
        var site = h.absolutePos(new BlockPos(8, 28, 8));
        var origin = Vec3.atBottomCenterOf(site);
        for (int x = -20; x <= 35; x++) for (int z = -19; z <= 19; z++)
            level.setBlockAndUpdate(site.offset(x, -1, z), Blocks.COBBLESTONE.defaultBlockState());
        for (int y=0; y<4; y++) for (int x=-2; x<=2; x++)
            level.setBlockAndUpdate(site.offset(x, y, 6), Blocks.STONE.defaultBlockState());
        var boss = EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        boss.moveTo(origin.x, origin.y, origin.z, 0, 0);
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WaveSurvivor");
        var player = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        player.connection = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, profile).connection;
        level.addNewPlayer(player);
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.moveTo(origin.x + 5, origin.y, origin.z, 0, 0);
        player.setOnGround(true);
        var jumpProfile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "WaveJumper");
        var jumper = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, jumpProfile, net.minecraft.server.level.ClientInformation.createDefault());
        jumper.connection = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, jumpProfile).connection;
        level.addNewPlayer(jumper); jumper.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        jumper.moveTo(origin.x + 9, origin.y + .8, origin.z, 0, 0);
        jumper.setOnGround(false);
        var near = spawn(h, ModEntities.COMMON_INFECTED.get(), origin.add(-5, 0, 0));
        var hidden = spawn(h, ModEntities.COMMON_INFECTED.get(), origin.add(0, 0, 10));
        var airborne = spawn(h, ModEntities.COMMON_INFECTED.get(), origin.add(8, 2, 0));
        var far = spawn(h, ModEntities.COMMON_INFECTED.get(), origin.add(30, 0, 0));
        near.setOnGround(true); hidden.setOnGround(true); far.setOnGround(true);
        var item = new net.minecraft.world.entity.item.ItemEntity(level, origin.x - 6, origin.y, origin.z,
                new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE));
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);
        var wave = new GreeneMolt();
        try {
            float hp = player.getHealth();
            for (int age = 404; age <= 414; age++) wave.tick(boss, age);
            h.assertTrue(player.getDeltaMovement().lengthSqr() == 0 && near.getDeltaMovement().lengthSqr() == 0, "gather is visibly harmless");
            for (int age = 415; age <= 418; age++) wave.tick(boss, age);
            h.assertTrue(player.getDeltaMovement().x > .2 && player.getDeltaMovement().x < .6 && player.getHealth() == hp,
                    "grounded survivor takes one gentle outward impulse with no damage");
            h.assertTrue(near.getDeltaMovement().x < 0 && item.getDeltaMovement().x < 0 && far.getDeltaMovement().lengthSqr() == 0,
                    "near infected and loose item move outward before the far wave");
            player.moveTo(origin.x + 32, origin.y, origin.z, 0, 0);
            player.setDeltaMovement(Vec3.ZERO); player.setOnGround(true);
            var resumed = new GreeneMolt(); resumed.load(wave.save()); wave = resumed;
            for (int age = 419; age <= 454; age++) wave.tick(boss, age);
            h.assertTrue(player.getDeltaMovement().lengthSqr() == 0 && far.getDeltaMovement().x > 0, "reload keeps first impulse and lets far wave arrive");
            h.assertTrue(hidden.getDeltaMovement().lengthSqr() == 0 && airborne.getDeltaMovement().lengthSqr() == 0 && jumper.getDeltaMovement().lengthSqr() == 0,
                    "solid wall shields the wave and airborne bodies clear it");
            var bounds = new net.minecraft.world.phys.AABB(origin.add(-20,-2,-19), origin.add(35,8,19));
            var displays = level.getEntitiesOfClass(net.minecraft.world.entity.Display.BlockDisplay.class, bounds);
            h.assertTrue(!displays.isEmpty() && displays.size() <= 30, "short-lived physical stone fragments form a bounded advancing front");
            h.assertTrue(level.getBlockState(site.below()).is(Blocks.COBBLESTONE) && wave.save().getList("MoltSurgeSwept", Tag.TAG_COMPOUND).isEmpty(),
                    "swept markers retire and source floor remains");
            int fragments = displays.size();
            System.out.println("MOLT_FINALE grounded=push/no damage; airborne/wall=clear; reload=once; physical fragments=" + fragments);
        } finally {
            wave.clear(boss);
            for (Entity entity : java.util.List.of(near, far, hidden, airborne, item, boss)) entity.discard();
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            level.removePlayerImmediately(jumper, Entity.RemovalReason.DISCARDED);
        }
        h.succeed();
    }

    private static Mob spawn(GameTestHelper h, EntityType<? extends Mob> type, Vec3 pos) {
        var mob = type.create(h.getLevel());
        mob.moveTo(pos.x, pos.y, pos.z, 0, 0);
        h.assertTrue(h.getLevel().addFreshEntity(mob), "can place fixture");
        return mob;
    }

    @GameTest(template="blackwatch_arena", timeoutTicks=60)
    public static void oneReactionPerBroodAcrossMoltReload(GameTestHelper h) {
        var level = h.getLevel();
        var site = h.absolutePos(new BlockPos(8, 18, 8));
        var origin = Vec3.atBottomCenterOf(site);
        var floor = site.offset(5, -1, 0);
        level.setBlockAndUpdate(floor, Blocks.COBBLESTONE.defaultBlockState());
        var roof = floor.above(33);
        level.setBlockAndUpdate(roof, Blocks.DEEPSLATE.defaultBlockState());
        var profile = new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "MoltWitness");
        var observer = new net.minecraft.server.level.ServerPlayer(level.getServer(), level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        var channel = new io.netty.channel.embedded.EmbeddedChannel(connection);
        int[] chips = {0, 0};
        observer.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(), connection, observer, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) {
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket particles
                        && particles.getParticle() instanceof net.minecraft.core.particles.BlockParticleOption material) {
                    if (material.getState().is(Blocks.COBBLESTONE) && Math.abs(particles.getY() - (floor.getY() + 1.19)) < .02)
                        chips[0]++;
                    if (material.getState().is(Blocks.DEEPSLATE) && Math.abs(particles.getY() - (roof.getY() - .25)) < .02)
                        chips[1]++;
                }
            }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) {send(packet);}
        };
        observer.moveTo(origin.x + 35, origin.y, origin.z, 0, 0);
        level.addNewPlayer(observer);
        var boss = EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        boss.moveTo(origin.x, origin.y, origin.z, 0, 0);
        var common = ModEntities.COMMON_INFECTED.get();
        var near = spawn(h, common, origin.add(5, 0, 0));
        var far = spawn(h, common, origin.add(22, 0, 0));
        var captive = spawn(h, common, origin.add(6, 0, 0));
        captive.getPersistentData().putUUID("GreeneMoltOwner", boss.getUUID());
        var rooted = spawn(h, ModEntities.BLACKLIGHT_HYDRA.get(), origin.add(-4, 0, 0));
        var wave = new GreeneMolt();
        try {
            wave.tick(boss, 0);
            for (int age = 1; age <= 15; age++) wave.tick(boss, age);
            h.assertTrue(near.getDeltaMovement().lengthSqr() == 0, "wait for jaw-opening beat");
            wave.tick(boss, 16);
            h.assertTrue(near.getDeltaMovement().x > 0 && far.getDeltaMovement().lengthSqr() == 0, "near brood recoils before outer ring");
            h.assertTrue(wave.save().getList("RoarBrood", Tag.TAG_COMPOUND).size() == 1, "one admitted near reactor");
            near.setDeltaMovement(Vec3.ZERO);
            near.setPos(origin.add(19, 0, 0));
            var resumed = new GreeneMolt();
            resumed.load(wave.save());
            wave = resumed;
            for (int age = 17; age <= 22; age++) wave.tick(boss, age);
            h.assertTrue(far.getDeltaMovement().x > 0 && near.getDeltaMovement().lengthSqr() == 0, "outer ring advances after load without repelling the same actor twice");
            h.assertTrue(wave.save().getList("RoarBrood", Tag.TAG_COMPOUND).size() == 2, "two eligible actors across distinct rings");
            for (int age = 23; age <= 32; age++) wave.tick(boss, age);
            h.assertTrue(rooted.getDeltaMovement().lengthSqr() == 0 && captive.getDeltaMovement().lengthSqr() == 0, "rooted hydra and absorbed actor keep position");
            h.assertTrue(chips[0] > 0 && chips[1] > 0, "distant witness receives chips from the real floor and ceiling: "+chips[0]+"/"+chips[1]);
            h.assertTrue(observer.getDeltaMovement().lengthSqr() == 0, "outer crowd reaction cannot push a player");
            h.assertTrue(level.getBlockState(floor).is(Blocks.COBBLESTONE), "ground fragments preserve blocks");
            h.assertTrue(wave.save().getList("RoarBrood", Tag.TAG_COMPOUND).isEmpty(), "short-lived reaction state retired");
        } finally {
            wave.clear(boss);
            for (Entity entity : java.util.List.of(near, far, captive, rooted, boss)) entity.discard();
            level.removePlayerImmediately(observer, Entity.RemovalReason.DISCARDED);
            channel.finishAndReleaseAll();
        }
        h.succeed();
    }
}
