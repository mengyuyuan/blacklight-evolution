package com.codex.blacklight.combat;

import com.codex.blacklight.entity.GroundSpikeEntity;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public class PlayerEffectGameTest {
    private static ServerPlayer player(GameTestHelper h,PowerForm form) {
        var profile=new GameProfile(UUID.randomUUID(),"effect_probe");
        var p=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile,net.minecraft.server.level.ClientInformation.createDefault());p.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;
        var state=p.getData(ModAttachments.PLAYER_STATE);
        state.infect(); state.setPrototypeMode(true); state.unlock(form); state.activate(form);
        p.setYRot(0); p.setXRot(0); p.setOnGround(true);
        return p;
    }
    private static java.util.List<net.minecraft.world.level.ChunkPos> activeLane(GameTestHelper h,Vec3 at) {
        var level=h.getLevel();var center=new net.minecraft.world.level.ChunkPos(BlockPos.containing(at));
        var tickets=new java.util.ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=center.x-1;x<=center.x+1;x++)for(int z=center.z-1;z<=center.z+1;z++){
            var chunk=new net.minecraft.world.level.ChunkPos(x,z);
            if(!level.getForcedChunks().contains(chunk.toLong())){level.setChunkForced(x,z,true);tickets.add(chunk);}
            level.getChunk(x,z);
        }
        return tickets;
    }
    @GameTest(template="wall_test",timeoutTicks=260)
    public static void clawStaysOnTheLowerSlabFloor(GameTestHelper h) {
        var level=h.getLevel();
        var p=player(h,PowerForm.CLAWS);
        BlockPos origin=h.absolutePos(new BlockPos(8,4,8)); Vec3 foot=Vec3.atBottomCenterOf(origin);
        p.setPos(foot);
        Vec3 aim=foot.add(0,0,GroundSpikeRules.BASE_CAST_DISTANCE*AttackReach.scale(p));
        BlockPos target=BlockPos.containing(aim);var tickets=activeLane(h,aim);
        level.addNewPlayer(p);
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(target)
                &&level.isPositionEntityTicking(target.offset(5,0,5))&&level.isPositionEntityTicking(target.offset(-5,0,-5)),
                "waiting for entity-ticking floor")).thenExecute(()->{
        for(int x=-6;x<=6;x++) for(int z=-6;z<=6;z++){
            var tile=target.offset(x,0,z);
            level.setBlockAndUpdate(tile,Blocks.STONE_SLAB.defaultBlockState());
            level.setBlockAndUpdate(tile.above(),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(tile.above(2),Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(tile.above(3),Blocks.STONE.defaultBlockState());
        }
        ClawCombatSystem.eruptGroundSpike(p);
        var all=level.getEntitiesOfClass(GroundSpikeEntity.class,new AABB(target).inflate(8));
        h.assertTrue(all.size()==6,"small master should emit six source shards, got "+all.size());
        for(var shard:all) {
            h.assertTrue(shard.getY()==target.getY()+.5,"shard on wrong storey: "+shard.getY());
            h.assertTrue(shard.playerShardSize()>=1&&shard.playerShardSize()<=2,"small master chose incorrect mesh");
            h.assertTrue(shard.riseTicks()>=4&&shard.riseTicks()<=10,"source rise interval");
        }
        h.runAfterDelay(25,()->{h.assertTrue(all.stream().allMatch(GroundSpikeEntity::isRemoved),"visual shards outlived their lease "+all.get(0).tickCount);for(var c:tickets)level.setChunkForced(c.x,c.z,false);level.removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);h.succeed();});
        });
    }
    @GameTest(template="wall_test",timeoutTicks=45)
    public static void hammerNeedsRealTopFace(GameTestHelper h) {
        var p=player(h,PowerForm.HAMMERFISTS);
        var tile=h.absolutePos(new BlockPos(2,4,2)); var level=h.getLevel();
        level.setBlockAndUpdate(tile,Blocks.STONE_SLAB.defaultBlockState());
        Vec3 at=Vec3.atBottomCenterOf(tile).add(0,.5,0);
        var hit=new BlockHitResult(at,Direction.UP,tile,false);
        h.assertTrue(CombatImpactEffects.ground(p,WeaponMove.HAMMER_QUAKE,hit),"ordinary hammer effect on slab");
        h.assertTrue(CombatImpactEffects.ground(p,WeaponMove.HAMMER_QUAKE_CHARGED,hit),"charged hammer effect on slab");
        h.assertTrue(CombatImpactEffects.ground(p,WeaponMove.HAMMER_DROP,hit),"drop effect on slab");
        level.setBlockAndUpdate(tile,Blocks.AIR.defaultBlockState());
        h.assertTrue(!CombatImpactEffects.ground(p,WeaponMove.HAMMER_DROP,hit),"stale contact emitted an effect");
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=260,batch="ordinary_spike_pin")
    public static void clawsCatchThenReleaseSurvivingWalker(GameTestHelper h) {
        var p=player(h,PowerForm.CLAWS);
        var at=Vec3.atBottomCenterOf(h.absolutePos(new BlockPos(6,10,6)));
        p.setPos(at);p.setYRot(0);
        var spot=at.add(0,0,GroundSpikeRules.BASE_CAST_DISTANCE*AttackReach.scale(p));
        var tile=BlockPos.containing(spot).below();var tickets=activeLane(h,spot);
        h.getLevel().addNewPlayer(p);
        h.startSequence().thenWaitUntil(()->h.assertTrue(h.getLevel().isPositionEntityTicking(tile),
                "waiting for entity-ticking pin lane")).thenExecute(()->{
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)h.getLevel().setBlockAndUpdate(tile.offset(x,0,z),Blocks.STONE.defaultBlockState());
        var mob=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
        mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        mob.setHealth(200);mob.setPos(spot);h.getLevel().addFreshEntity(mob);
        h.getLevel().setBlockAndUpdate(tile.above(4),Blocks.STONE.defaultBlockState());
        var upstairs=net.minecraft.world.entity.EntityType.COW.create(h.getLevel());
        upstairs.setNoAi(true);upstairs.setPos(spot.add(0,4,0));h.getLevel().addFreshEntity(upstairs);
        mob.setDeltaMovement(.23,0,0);
        ClawCombatSystem.eruptGroundSpike(p);
        h.assertTrue(upstairs.isAlive()&&upstairs.getHealth()==upstairs.getMaxHealth()&&!GroundSpikePin.held(upstairs),
                "blocked upper storey was hit by a lower shaft");
        upstairs.discard();
        var shard=h.getLevel().getEntitiesOfClass(GroundSpikeEntity.class,new AABB(tile).inflate(4)).stream()
                .filter(v->v.position().distanceToSqr(spot)<.001).findFirst().orElseThrow();
        Vec3 base=shard.position();
        h.assertTrue(GroundSpikePin.held(mob)&&mob.getHealth()<200,"surviving target caught at actual shaft");
        h.runAfterDelay(7,()->{
            h.assertTrue(GroundSpikePin.held(mob)&&!mob.isNoAi(),"mob held without changing persistent AI");
            h.assertTrue(mob.getY()>base.y+.2&&Math.abs(mob.getX()-spot.x)<.08,"mob lifted at original feet, mob="+mob.position()+" root="+base+" age="+mob.tickCount);
            mob.setDeltaMovement(.32,0,0);
        });
        h.runAfterDelay(11,()->{
            h.assertTrue(shard.position().equals(base)&&Math.abs(mob.getX()-spot.x)<.08,"lateral impulse moved shaft or victim");
        });
        h.runAfterDelay(24,()->{
            h.assertTrue(shard.isRemoved()&&!GroundSpikePin.held(mob)&&!ImpactControl.stunned(mob),"withdraw did not release");
            // A live cow may already be walking away from the pin in any direction.
            // Measure the requested displacement from its position on this tick.
            Vec3 released=mob.position();
            mob.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(.55,0,0));
            h.assertTrue(mob.getX()-released.x>.12,"released target could not step from "+released+" to "+mob.position());
        });
        h.runAfterDelay(27,()->{h.assertTrue(!GroundSpikePin.held(mob),"withdrawn shaft recaptured its target");mob.discard();for(var c:tickets)h.getLevel().setChunkForced(c.x,c.z,false);h.getLevel().removePlayerImmediately(p,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);h.succeed();});
        });
    }
}
