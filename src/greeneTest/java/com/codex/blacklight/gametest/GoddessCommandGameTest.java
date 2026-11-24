package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ClipContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.*;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.List;
import java.util.UUID;
import java.util.LinkedHashMap;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GoddessCommandGameTest {
    private static ServerPlayer orbTarget(GameTestHelper h, GoddessTerrainGameTest.Arena a, String name,Vec3 at) {
        var profile=new GameProfile(UUID.randomUUID(),name);
        var player=new ServerPlayer(a.level.getServer(),a.level,profile);
        player.connection=FakePlayerFactory.get(a.level,profile).connection;
        player.moveTo(at.x,at.y,at.z,0,0);
        for(int age=0;age<61;age++)player.tick();
        player.moveTo(at.x,at.y,at.z,0,0);
        a.level.addNewPlayer(player);
        a.players.add(player);
        player.setGameMode(GameType.SURVIVAL);
        player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        player.setHealth(player.getMaxHealth());
        requireIndexed(h,a,player);
        return player;
    }
    private static void requireIndexed(GameTestHelper h,GoddessTerrainGameTest.Arena a,ServerPlayer player) {
        h.assertTrue(a.level.getEntity(player.getUUID())==player,"target UUID is tracked at "+player.position());
        h.assertTrue(a.level.getEntitiesOfClass(ServerPlayer.class,player.getBoundingBox()).contains(player),
                "target occupies swept collision index at "+player.position());
    }
    private static Vec3 placeFlightTarget(GameTestHelper h,GoddessTerrainGameTest.Arena arena,
                                         ServerPlayer target,Vec3 start) {
        // A wrist can lie outside the structure. Fly back towards an indexed, unobstructed lane.
        var directions=List.of(new Vec3(-1,0,0),new Vec3(1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1));
        for(var dir:directions.stream().sorted(java.util.Comparator.comparingDouble(
                (Vec3 d)->start.add(d.scale(14)).distanceToSqr(arena.base))).toList()){
            var end=start.add(dir.scale(14));
            if(arena.level.clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target))
                    .getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            target.setPos(end.add(0,-target.getBbHeight()*.5,0));
            if(arena.level.getEntitiesOfClass(ServerPlayer.class,target.getBoundingBox()).contains(target)){
                requireIndexed(h,arena,target);
                return dir;
            }
        }
        throw new AssertionError("no indexed 14-block flight lane from "+start+" near "+arena.base);
    }
    private static List<GoddessCommandProjectile> projectiles(GoddessTerrainGameTest.Arena a) {
        return a.level.getEntitiesOfClass(GoddessCommandProjectile.class,a.boss.getBoundingBox().inflate(128),
                e->e.caller()==a.boss && e.isAlive());
    }
    private static GoddessTerrainGameTest.Arena orbArena(GameTestHelper h,int belowCeiling){
        var structureChunk=new net.minecraft.world.level.ChunkPos(h.absolutePos(new BlockPos(8,0,8)));
        var site=new BlockPos(structureChunk.getMinBlockX()+1,h.getLevel().getMaxBuildHeight()-belowCeiling,
                structureChunk.getMinBlockZ()+8);
        return new GoddessTerrainGameTest.Arena(h,site);
    }
    private static void clear(GoddessTerrainGameTest.Arena a) {
        a.boss.goddess.attacks.cancel();
        for(var projectile:projectiles(a))projectile.discard();
    }

    @GameTest(template="blackwatch_arena",batch="command_orbs",timeoutTicks=120)
    public static void twoGreenClustersHoldTurnAndPayOnce(GameTestHelper h) {
        try(var a=orbArena(h,150)) {
            var target=orbTarget(h,a,"GreenTarget",a.base.add(7,0,0));
            a.begin(GoddessAttacks.Move.SEEKING_ORBS,target,92);
            a.tick(1,1);
            var balls=projectiles(a);
            h.assertTrue(balls.size()==6,"two three-orb clusters spawned");
            for(var ball:balls){ball.tick();h.assertTrue(ball.getDeltaMovement().lengthSqr()<.001,"suspended until wrist signal");}
            a.tick(2,14);
            var first=balls.stream().filter(b->b.slot()<3).toList();
            float full=target.getHealth();
            for(var ball:first){
                target.invulnerableTime=0;ball.setPos(target.getBoundingBox().getCenter().add(.20,0,0));ball.tick();
            }
            float once=target.getHealth();
            h.assertTrue(full>once && Math.abs((full-once)/full-.06)<.012,"first cluster as one 6% hit, got "+full+" -> "+once);
            a.boss.goddess.attacks.load(a.boss.goddess.attacks.save());
            a.tick(15,34);
            for(var ball:balls)if(ball.slot()>=3){
                target.invulnerableTime=0;ball.setPos(target.getBoundingBox().getCenter().add(.20,0,0));ball.tick();
            }
            h.assertTrue(Math.abs((once-target.getHealth())-(full-once))<.1,"independent second debit");
            var turned=GoddessCommandProjectile.steer(new Vec3(1,0,0),new Vec3(-1,0,0),Math.toRadians(3.8));
            h.assertTrue(Math.abs(Math.toDegrees(Math.acos(turned.x))-3.8)<.005,"cannot reverse onto a dodging player");
            clear(a);a.begin(GoddessAttacks.Move.SEEKING_ORBS,target,93);a.tick(1,20);
            var contact=new HydraHurtContact.Contact("goddess_hair",a.boss.position(),new Vec3(1,0,0),InfectedAttackStyle.CRUSH);
            a.boss.goddess.attacks.commands.hitController(a.boss.damageSources().playerAttack(target),contact);
            for(var ball:projectiles(a))ball.tick();
            h.assertTrue(a.boss.goddess.attacks.commands.secondBroken() && projectiles(a).stream().noneMatch(b->b.slot()>=3),
                    "crown cut removes only the second cluster");
            System.out.println("GODDESS_COMMAND_ORBS six=true hp="+full+"->"+once+"->"+target.getHealth()+" turn=3.8 crown_cut=true");
            clear(a);
        }
        h.succeed();
    }

    @GameTest(template="blackwatch_arena",batch="command_flight",timeoutTicks=120)
    public static void releasedOrbTravelsThroughWorldAndStopsAtCover(GameTestHelper h) {
        try(var a=orbArena(h,110)) {
            var target=orbTarget(h,a,"OrbFlight",a.base.add(7,0,0));
            a.begin(GoddessAttacks.Move.SEEKING_ORBS,target,100);a.tick(1,1);
            var primary=projectiles(a).stream().filter(b->b.slot()==1).findFirst().orElseThrow();
            var secondary=projectiles(a).stream().filter(b->b.slot()==4).findFirst().orElseThrow();
            a.tick(2,13);primary.tick();
            var start=primary.position();
            var heading=placeFlightTarget(h,a,target,start);
            var wall=BlockPos.containing(start.add(heading.scale(6)));
            var previous=new LinkedHashMap<BlockPos,BlockState>();
            try{
                for(int y=-2;y<=2;y++)for(int lateral=-2;lateral<=2;lateral++){
                    var pos=wall.offset((int)(heading.z*lateral),y,(int)(heading.x*lateral));
                    previous.put(pos,a.level.getBlockState(pos));
                    a.level.setBlockAndUpdate(pos,Blocks.STONE.defaultBlockState());
                }
                for(int age=14;age<=24;age++){
                    a.tick(age,age);if(primary.isAlive())primary.tick();
                }
                h.assertTrue(!primary.isAlive() && start.distanceTo(primary.position())>2 && target.getHealth()==target.getMaxHealth(),"travelling orb struck cover before target");
            }finally{
                previous.forEach(a.level::setBlockAndUpdate);
            }
            a.tick(25,33);secondary.tick();start=secondary.position();placeFlightTarget(h,a,target,start);
            for(int age=34;age<=50;age++){
                a.tick(age,age);if(secondary.isAlive())secondary.tick();
            }
            h.assertTrue(!secondary.isAlive() && start.distanceTo(secondary.position())>10
                    && target.getHealth()<target.getMaxHealth(),"second volley physically crossed exposed lane; start="+start+
                    " final="+secondary.position()+" target="+target.position()+" health="+target.getHealth());
            System.out.println("GODDESS_COMMAND_FLIGHT covered=100 exposed="+target.getHealth()+" flight="+start.distanceTo(secondary.position()));
            clear(a);
        }
        h.succeed();
    }

    private static void formHand(GoddessTerrainGameTest.Arena a) {
        var tag=a.boss.goddess.attacks.save();var hand=tag.getCompound("Hand");
        hand.putBoolean("Formed",true);hand.putBoolean("Visible",true);hand.putInt("Side",1);
        for(String prefix:new String[]{"At","From"}){
            hand.putDouble(prefix+"X",a.base.x+8); hand.putDouble(prefix+"Y",a.base.y+5); hand.putDouble(prefix+"Z",a.base.z-4);
        }
        tag.put("Hand",hand);a.boss.goddess.attacks.load(tag);
    }
    @GameTest(template="blackwatch_arena",batch="command_crush",timeoutTicks=120)
    public static void severOneFingerAndOnlyTheOtherRockMoves(GameTestHelper h) {
        try(var a=new GoddessTerrainGameTest.Arena(h)){
            var target=a.player("StoneTarget",a.base.add(17,0,0));
            // The right strand tears sideways out of a wall; the left one grips the floor.
            var wall=BlockPos.containing(target.position().add(0,1,-6));
            for(int y=0;y<=2;y++)a.level.setBlockAndUpdate(wall.above(y),Blocks.STONE.defaultBlockState());
            formHand(a);a.begin(GoddessAttacks.Move.TENDRIL_CRUSH,target,94);
            a.tick(1,10);var nodes=projectiles(a);
            h.assertTrue(nodes.size()==4,"two knots on each tendon, actual "+nodes.size());
            var command=a.boss.goddess.attacks.commands;
            var left=BlockPos.containing(command.stoneSite(0));var right=BlockPos.containing(command.stoneSite(1));
            h.assertTrue(right.equals(wall) && command.stoneAnchor(1).z>command.stoneSite(1).z+.5,"right tendon hooks the open quarry face");
            var knot=nodes.stream().filter(n->n.slot()==6).findFirst().orElseThrow();
            knot.tick();h.assertTrue(knot.hurt(a.boss.damageSources().playerAttack(target),5),"cut registered on node");
            a.tick(11,32);var rocks=a.level.getEntitiesOfClass(FallingBlockEntity.class,a.boss.getBoundingBox().inflate(80),
                    b->b.getPersistentData().hasUUID("EncounterOwner")&&b.getPersistentData().getUUID("EncounterOwner").equals(a.boss.getUUID()));
            h.assertTrue(command.cut(0)&&!command.cut(1)&&rocks.size()==1,"only second rock actually lifted");
            h.assertTrue(a.level.getBlockState(left).is(Blocks.STONE)&&a.level.getBlockState(right).isAir(),"original intact and pulled sites match their visible sides");
            var rock=rocks.get(0);Vec3 start=rock.position();float full=target.getHealth();
            for(int t=33;t<=48;t++){rock.tick();a.tick(t,t);}
            h.assertTrue(rock.position().distanceToSqr(start)>9,"actual rock travelled laterally");
            h.assertTrue(target.getHealth()<full && Math.abs((full-target.getHealth())/full-.16)<.025,"single remaining rock strikes on contact, hp="+target.getHealth());
            System.out.println("GODDESS_COMMAND_CRUSH nodes=4 remaining_rocks=1 sidewall_extracted=true cut_floor_intact=true hp="+full+"->"+target.getHealth());
            rock.discard();clear(a);
        }
        h.succeed();
    }

    private static BlacklightInfectedHost evolved(GoddessTerrainGameTest.Arena a,Vec3 pos){
        var mob=ModEntities.INFECTED_HOST.get().create(a.level);
        var save=mob.saveWithoutId(new net.minecraft.nbt.CompoundTag());var host=save.getCompound("BlacklightHost");
        host.putBoolean("Evolved",true);save.put("BlacklightHost",host);mob.load(save);
        mob.moveTo(pos.x,pos.y,pos.z,0,0);mob.setOnGround(true);a.level.addFreshEntity(mob);return mob;
    }
    @GameTest(template="blackwatch_arena",batch="command_pack",timeoutTicks=120)
    public static void existingEvolvedPounceWithBreakableLeader(GameTestHelper h){
        try(var a=new GoddessTerrainGameTest.Arena(h)){
            var target=a.player("PackTarget",a.base.add(17,0,0));
            var lead=evolved(a,a.base.add(11,0,.30));var second=evolved(a,a.base.add(9,0,-.30));
            var common=ModEntities.COMMON_INFECTED.get().create(a.level);
            common.moveTo(a.base.x+13,a.base.y,a.base.z+2,0,0);a.level.addFreshEntity(common);
            try{
                a.begin(GoddessAttacks.Move.PACK_POUNCE,target,95);a.tick(1,10);
                var cmd=a.boss.goddess.attacks.commands;
                h.assertTrue(cmd.save().getInt("Count")==2 && lead.isNoAi() && second.isNoAi() && !common.isNoAi(),"only the two live evolved join");
                h.assertTrue(lead.evolvedAction().equals("evolved_pounce") && second.evolvedAction().isEmpty(),"leader crouches before follower");
                lead.setHealth(lead.getHealth()*.75F);a.tick(11,11);
                h.assertTrue(!lead.isNoAi() && !second.isNoAi() && a.boss.goddess.attacks.move()!=GoddessAttacks.Move.PACK_POUNCE,"damaged leader interrupts group and releases both");
                a.begin(GoddessAttacks.Move.PACK_POUNCE,target,96);var start=lead.position();float full=target.getHealth();
                a.tick(1,47);
                h.assertTrue(lead.position().distanceToSqr(start)>9,"committed physical leap crossed arena");
                h.assertTrue(target.getHealth()<full && Math.abs((full-target.getHealth())/full-.10)<.015,"overlapping pounces debit one 10% hit, hp="+target.getHealth());
                System.out.println("GODDESS_COMMAND_PACK members=2 interrupted=true ordinary_untouched=true travel="+lead.position().distanceTo(start)+" hp="+full+"->"+target.getHealth());
            }finally{lead.discard();second.discard();common.discard();clear(a);}
        }
        h.succeed();
    }
}
