package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.HellfireBlastEntity;
import com.codex.blacklight.military.aircraft.*;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

/** Real connection/seat/input, ordinary world ticks, one projectile or cannon round, no direct fire call. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class HelicopterLongRangeGameTest {
    @GameTest(template="helicopter_arena",batch="airLongCannon",timeoutTicks=460)
    public static void playerCannonHitsGroundEnemyAt130Blocks(GameTestHelper h){run(h,false,340000);}
    @GameTest(template="helicopter_arena",batch="airLongMissile",timeoutTicks=460)
    public static void playerHellfireLaunchesAndDamagesGroundEnemyAt130Blocks(GameTestHelper h){run(h,true,341024);}
    private static void run(GameTestHelper h,boolean missile,int x){
        var f=new HelicopterPilotGameTest.Fixture(h,x);var level=f.level;
        // Loaded, empty flight corridor; use only this disposable fixture's remote chunks.
        for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=-1;cz<=10;cz++){
            long key=ChunkPos.asLong(cx,cz);f.forced.putIfAbsent(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);
        }
        for(int dx=-5;dx<=5;dx++)for(int z=25;z<=150;z++){
            level.setBlock(new BlockPos(x+dx,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=81;y<=110;y++){var at=new BlockPos(x+dx,y,z);if(!level.getBlockState(at).isAir())level.setBlock(at,Blocks.AIR.defaultBlockState(),2);}
        }
        var craft=f.craft(20);var player=HelicopterPilotGameTest.player(h,f,false);
        h.assertTrue(player.startRiding(craft,true),"real connected pilot boards");
        craft.combat().emptyPlayerStores();craft.combat().addPlayerAmmunition(missile?HelicopterSupplies.HELLFIRE:HelicopterSupplies.CANNON,1);
        var target=ModEntities.COMMON_INFECTED.get().create(level);Vec3 anchor=new Vec3(x,81,130);
        // The real player is beyond vanilla Monster's immediate 128-block despawn
        // radius. Keep the fixture target in the world, as the natural-chain fixture does.
        target.setPos(anchor);target.setNoAi(true);target.setNoGravity(true);target.setPersistenceRequired();target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);target.setHealth(1000);f.entities.add(target);
        long[] sequence={0};int[] time={0};boolean[] done={false},accepted={false},ready={false};double[] shotDistance={0};String[] shotGeometry={"not fired"};
        h.onEachTick(()->{
            if(done[0])return;
            if(!ready[0]){
                // setChunkForced schedules visibility; FULL block data alone does
                // not yet place remote mobs in ServerLevel's entity lookup.
                for(int z=0;z<=144;z+=16)if(!level.isPositionEntityTicking(new BlockPos(f.x,81,z)))return;
                if(!level.addFreshEntity(target)){done[0]=true;f.close();h.fail("long-range fixture target insertion rejected in ticking chunks");return;}
                ready[0]=true;
            }
            int t=++time[0];
            if(!target.isAlive()||target.isRemoved()||level.getEntity(target.getUUID())!=target){
                done[0]=true;String message="long-range fixture lost its actual target: tick="+t+" removed="+target.isRemoved()+" registered="+(level.getEntity(target.getUUID())==target);f.close();h.fail(message);return;
            }
            target.setPos(anchor);target.setDeltaMovement(Vec3.ZERO);
            if(t>=90&&t%2==0){
                Vec3 delta=target.getEyePosition().subtract(craft.weaponPoint(HelicopterArmament.GUN));
                double yaw=Math.toDegrees(Math.atan2(-delta.x,delta.z)),pitch=-Math.toDegrees(Math.atan2(delta.y,delta.horizontalDistance()));
                int actions=t>=130?(missile?VehicleControlAuthority.SECONDARY_FIRE:VehicleControlAuthority.PRIMARY_FIRE):0;
                accepted[0]|=craft.pilot().accept(player,craft.pilot().lease(player.getUUID()),sequence[0]++,0,0,yaw,pitch,actions);
            }
            if(craft.combat().shots()>0&&shotDistance[0]==0){shotDistance[0]=craft.position().distanceTo(target.position());shotGeometry[0]=geometry(level,craft,target);}
            var source=target.getLastDamageSource();
            if(source!=null&&source.getEntity()==player&&(missile?source.getDirectEntity() instanceof HellfireBlastEntity:source.getDirectEntity()==craft)){
                h.assertTrue(accepted[0]&&shotDistance[0]>120,"input-fired weapon reaches beyond former 72/96-block limits: "+shotDistance[0]);
                h.assertTrue(target.getHealth()<target.getMaxHealth(),"actual ground target accepted aircraft damage");
                h.assertTrue(craft.combat().shots()==1&&craft.combat().rounds()==0&&craft.combat().rockets()==0,"one accepted shot consumes the one finite player round");
                com.codex.blacklight.BlacklightNeoMod.LOGGER.info("HELI_LONG_RANGE_VERIFIED weapon={} launchDistance={} remainingHealth={} shots={}",missile?"Hellfire":"30mm",shotDistance[0],target.getHealth(),craft.combat().shots());
                done[0]=true;f.close();h.succeed();
            }
        });
        h.runAfterDelay(440,()->{if(!done[0]){done[0]=true;String message="long-range actual weapon failed: missile="+missile+" ready="+ready[0]+" activeTicks="+time[0]+" input="+accepted[0]+" shots="+craft.combat().shots()+" phase="+craft.phase()+" rotor="+craft.rotor()+" target="+target.getHealth()+" distance="+shotDistance[0]+" firstShot="+shotGeometry[0];f.close();h.fail(message);}});
    }
    private static String geometry(net.minecraft.server.level.ServerLevel level,BlackwatchHelicopter craft,BlacklightCommonInfected target){
        Vec3 direction=HelicopterArmament.rotate(HelicopterArmament.direction(craft.gunYaw(),craft.gunPitch()),craft.getYRot(),craft.bank());
        Vec3 muzzle=craft.weaponPoint(HelicopterArmament.GUN).add(direction.scale(1.2)),end=muzzle.add(direction.scale(HelicopterCombat.weaponRange()));
        var wall=level.clip(new net.minecraft.world.level.ClipContext(muzzle,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,craft));
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(level,craft,muzzle,end,new net.minecraft.world.phys.AABB(muzzle,end).inflate(.3),e->e.getRootVehicle()!=craft&&e.isAlive()&&e.isPickable());
        return "muzzle="+muzzle+" aim="+craft.gunYaw()+","+craft.gunPitch()+" wall="+wall.getType()+":"+wall.getLocation()+" targetBox="+target.getBoundingBox()+" crossesTarget="+target.getBoundingBox().clip(muzzle,end).isPresent()+" firstEntity="+(hit==null?"none":hit.getEntity().getType()+":"+hit.getLocation());
    }
}
