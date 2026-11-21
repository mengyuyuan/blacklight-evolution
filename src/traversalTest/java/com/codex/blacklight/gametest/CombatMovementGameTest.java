package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.WeaponCombatSystem;
import com.codex.blacklight.combat.WeaponMove;
import com.codex.blacklight.movement.*;
import com.codex.blacklight.network.MovementInputPayload;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class CombatMovementGameTest {
    private static ServerPlayer actor(GameTestHelper h,boolean ground) {
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"combat-motion"));
        p.setPos(h.absoluteVec(new Vec3(4.5,40,2)));
        p.setYRot(0);p.setXRot(0);p.setOnGround(ground);p.tickCount=100;
        var state=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        state.infect();state.setPrototypeMode(true);state.unlock(PowerForm.CLAWS);state.activate(PowerForm.CLAWS);
        ModAttachments.setData(p,ModAttachments.PLAYER_STATE,state);p.setHealth(p.getMaxHealth());return p;
    }

    private static void combatTick(ServerPlayer p) {
        p.tickCount++;
        WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
    }

    private static void movementTick(ServerPlayer p) {
        p.tickCount++;
        PrototypeMovementSystem.onPlayerTick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
    }

    private static void evade(ServerPlayer p,int forward,int right) {
        PrototypeMovementSystem.acceptInput(p,false,true,forward,right,false,false,true);
    }

    private record Slash(double slide,double release){}
    private static Slash slash(GameTestHelper h,boolean held) {
        var p=actor(h,true);
        p.setSprinting(true);p.setDeltaMovement(0,0,.8);
        var origin=p.position();
        WeaponCombatSystem.acceptInput(p,true,false);
        var visual=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL);
        h.assertTrue(visual.spec()==WeaponMove.CLAW_SPRINT && visual.charging(),"sprinting attack enters claw charge");
        h.assertTrue(visual.targetId()<0,"open-lane measurement requires no selected target");
        if(!held)WeaponCombatSystem.acceptInput(p,false,false);
        for(int i=0;i<24 && ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).charging();i++)combatTick(p);
        h.assertTrue(!ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).charging(),"charge released");
        double releaseZ=p.getZ();
        for(int i=0;i<4;i++)combatTick(p);
        var result=new Slash(releaseZ-origin.z,p.getZ()-releaseZ);
        WeaponCombatSystem.cancel(p);
        return result;
    }

    private static void floor(GameTestHelper h) {
        for(int x=3;x<=6;x++)for(int z=1;z<=12;z++)h.setBlock(x,39,z,Blocks.STONE);
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void sprintClawCarriesEntryAndScalesRelease(GameTestHelper h) {
        floor(h);
        var tap=slash(h,false);var full=slash(h,true);
        System.out.printf(java.util.Locale.ROOT,"CLAW_TRAVEL tapSlide=%.4f tapRelease=%.4f fullSlide=%.4f fullRelease=%.4f%n",
                tap.slide,tap.release,full.slide,full.release);
        h.assertTrue(tap.slide>.75 && full.slide>=tap.slide,"existing sprint speed must advance the charge");
        h.assertTrue(tap.release>.7 && full.release-tap.release>3.4 && full.release-tap.release<3.55,
                "release distance must reflect charge once: "+tap+" / "+full);
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void chargedSprintClawStopsAtSolidWall(GameTestHelper h) {
        floor(h);
        for(int x=2;x<=7;x++)for(int y=40;y<46;y++)h.setBlock(x,y,5,Blocks.STONE);
        var motion=slash(h,true);double distance=motion.slide+motion.release;
        h.assertTrue(distance>2.5 && distance<=2.701,"body stops its half-width before wall: "+distance);
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void airComboHandsOffOnlyInRecovery(GameTestHelper h) {
        var p=actor(h,false);var state=ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE);
        WeaponCombatSystem.request(p,false);
        h.assertTrue(ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.AIR_AUTO1,"actual air attack selected");
        evade(p,0,1);h.assertTrue(WeaponCombatSystem.active(p) && !state.consumeDashRequest(),"startup keeps attack ownership");
        // Release the physical movement key before stepping the independent action clock.
        PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,false);
        for(int i=0;i<WeaponMove.AIR_AUTO1.end;i++)combatTick(p);
        h.assertTrue(WeaponCombatSystem.cancelableRecovery(p),"active contact window finished");
        state.setCameraYaw(90);evade(p,0,1);
        h.assertTrue(!WeaponCombatSystem.active(p),"air recovery yields to a deliberate evade");
        movementTick(p);
        h.assertTrue(state.evading() && state.dashTicks()>0 && p.getDeltaMovement().z<-.8 && Math.abs(p.getDeltaMovement().x)<.01,
                "camera yaw 90 plus D moves toward negative Z: "+p.getDeltaMovement());
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void pounceKeepsTravelAndRakeBeforeEvade(GameTestHelper h) {
        var p=actor(h,false);WeaponCombatSystem.request(p,true);
        h.assertTrue(ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec()==WeaponMove.CLAW_POUNCE,"air special selected pounce");
        for(int i=0;i<12;i++) {p.move(MoverType.SELF,p.getDeltaMovement());combatTick(p);}
        evade(p,0,1);h.assertTrue(WeaponCombatSystem.active(p),"flight cannot yield early");
        PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,false);
        int wait=0;
        while(!WeaponCombatSystem.cancelableRecovery(p) && WeaponCombatSystem.active(p) && wait++<40) {
            p.move(MoverType.SELF,p.getDeltaMovement());combatTick(p);
        }
        h.assertTrue(WeaponCombatSystem.active(p) && ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).clipOffset()>=.5F,"pounce finished pursuit and raked");
        evade(p,-1,0);movementTick(p);
        h.assertTrue(!WeaponCombatSystem.active(p) && p.getDeltaMovement().z<-.8,"pounce recovery can disengage backward");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void secondAirEvadeUsesUpgradeAndOneBufferedPress(GameTestHelper h) {
        var p=actor(h,true);var blacklight=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);
        blacklight.addEvolutionPoints(300);h.assertTrue(blacklight.buyMovementUpgrade(MovementUpgrade.AIR_DASH_PLUS),"upgrade purchased");
        ModAttachments.setData(p,ModAttachments.PLAYER_STATE,blacklight);movementTick(p);p.setOnGround(false);
        var movement=ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE);
        evade(p,1,0);movementTick(p);
        int wait=0;
        while(movement.dashTicks()>2 && wait++<30)movementTick(p);
        h.assertTrue(movement.dashTicks()==2,"two ticks before first dash ends");
        evade(p,0,1);movementTick(p);
        movement.setCameraYaw(90);
        PrototypeMovementSystem.acceptInput(p,false,false,0,0,false,false);
        movementTick(p);
        h.assertTrue(movement.dashTicks()==0,"first dash completed before second");
        movementTick(p);
        h.assertTrue(movement.dashTicks()>0 && p.getDeltaMovement().x<-.8,"second dash consumes the buffered right input");
        while(movement.dashTicks()>0)movementTick(p);
        evade(p,-1,0);movementTick(p);h.assertTrue(movement.dashTicks()==0,"third dash cannot start before refill");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void realWallRunRestoresSpentAirEvade(GameTestHelper h) {
        for(int y=36;y<65;y++)for(int x=2;x<=7;x++)h.setBlock(x,y,5,Blocks.STONE);
        var p=actor(h,false);var movement=ModAttachments.getData(p,ModAttachments.MOVEMENT_STATE);
        evade(p,1,0);movementTick(p);
        while(movement.dashTicks()>0)movementTick(p);
        p.setPos(h.absoluteVec(new Vec3(4.5,40,4.69)));p.setOnGround(false);p.setDeltaMovement(Vec3.ZERO);
        PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);movementTick(p);
        h.assertTrue(movement.wallTraversal().mode()==WallTraversal.Mode.RUN && !movement.canAirDash(),"wall touch alone does not refill");
        double start=p.getY();
        for(int i=0;i<12 && !movement.canAirDash();i++) {
            p.move(MoverType.SELF,p.getDeltaMovement());movementTick(p);
        }
        h.assertTrue(p.getY()-start>=1 && movement.canAirDash(),"physical wall run restores the aerial charge: "+(p.getY()-start));
        evade(p,-1,0);movementTick(p);
        h.assertTrue(movement.dashTicks()>0 && p.getDeltaMovement().z<-.8,"can leave wall with restored charge");
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=40)
    public static void bothDashGesturesRoundTrip(GameTestHelper h) {
        for(int code=0;code<3;code++){
            var buf=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                var input=new MovementInputPayload(false,code!=0,-1,1,true,false,73,code==2);
                MovementInputPayload.STREAM_CODEC.encode(buf,input);
                h.assertTrue(buf.getUnsignedByte(1)==code,"existing action byte encoding");
                h.assertTrue(input.equals(MovementInputPayload.STREAM_CODEC.decode(buf)) && buf.readableBytes()==0,"input round trip "+code);
            }finally{buf.release();}
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=40)
    public static void chargeMovesEachStrikeInGroundClawChain(GameTestHelper h) {
        floor(h);
        var expected=new WeaponMove[]{WeaponMove.CLAW_AUTO1,WeaponMove.CLAW_AUTO2,WeaponMove.CLAW_AUTO3};
        var distances=new double[2][3];
        for(int held=0;held<=1;held++){
            var p=actor(h,true);
            for(int i=0;i<expected.length;i++){
                // Start each measurement on the same lane. The server retains its normal combo grace.
                p.setPos(h.absoluteVec(new Vec3(4.5,40,2)));p.setOnGround(true);
                WeaponCombatSystem.acceptInput(p,true,false);
                h.assertTrue(ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec()==expected[i],"linked press selects "+expected[i]);
                if(held==0)WeaponCombatSystem.acceptInput(p,false,false);
                int wait=0;
                while(ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).charging() && wait++<24)combatTick(p);
                var release=ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).spec();
                h.assertTrue(release!=null && release.uncharged()==expected[i] && (release!=expected[i])==(held==1),"right charged variant "+i);
                WeaponCombatSystem.acceptInput(p,false,false);
                double z=p.getZ();
                for(int tick=0;tick<5;tick++)combatTick(p);
                distances[held][i]=p.getZ()-z;
                wait=0;while(WeaponCombatSystem.active(p) && wait++<55)combatTick(p);
                h.assertTrue(!WeaponCombatSystem.active(p),"finite recovery "+i);
            }
        }
        for(int i=0;i<3;i++)
            h.assertTrue(distances[0][i]>.4 && distances[1][i]-distances[0][i]>.6,"charged step must extend for strike "+i+": "+distances[0][i]+"/"+distances[1][i]);
        System.out.printf("CLAW_COMBO tap=%s charged=%s%n",java.util.Arrays.toString(distances[0]),java.util.Arrays.toString(distances[1]));
        h.succeed();
    }

    private static final net.minecraft.server.level.TicketType<UUID> TICKET=net.minecraft.server.level.TicketType.create("claw_move_test",UUID::compareTo);

    static void loaded(GameTestHelper h,Runnable check) {
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(4,55,4));
        var chunk=new net.minecraft.world.level.ChunkPos(pos);
        var level=h.getLevel();var source=level.getChunkSource();var lease=UUID.randomUUID();
        source.addRegionTicket(TICKET,chunk,3,lease);level.getChunk(chunk.x,chunk.z);
        h.runAfterDelay(305,()->source.removeRegionTicket(TICKET,chunk,3,lease));
        h.startSequence().thenWaitUntil(()->{
            h.assertTrue(level.isPositionEntityTicking(pos),"entity tracking active");
        }).thenExecute(()->{try{check.run();}finally{source.removeRegionTicket(TICKET,chunk,3,lease);}});
    }

    @GameTest(template="wall_test",timeoutTicks=300)
    public static void directionalEvadeKeepsLockAndDoesNotDealClawDamage(GameTestHelper h) {
        loaded(h,()->{
            var p=actor(h,false);
            p.setPos(h.absoluteVec(new Vec3(4.5,55,3.5)));
            var mob=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,4,55,4);
            mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(p.position().add(0,0,1));
            float health=mob.getHealth();
            com.codex.blacklight.combat.CombatTargeting.requestLock(p,mob.getId());
            h.assertTrue(com.codex.blacklight.combat.CombatTargeting.locked(p)==mob,"fixture acquired visible golem");
            evade(p,0,1);movementTick(p);
            h.assertTrue(p.getDeltaMovement().x<-.8 && Math.abs(p.getDeltaMovement().z)<.05,"lock cannot turn sideways escape toward victim");
            h.assertTrue(mob.getHealth()==health && com.codex.blacklight.combat.CombatTargeting.locked(p)==mob,"evade preserves lock without contact damage");
            p.setOnGround(true);movementTick(p);p.setOnGround(false);
            PrototypeMovementSystem.acceptInput(p,false,true,1,0,false,false);movementTick(p);
            h.assertTrue(mob.getHealth()<health,"ordinary claw dash still damages this same victim at contact");
            mob.discard();h.succeed();
        });
    }

    @GameTest(template="wall_test",timeoutTicks=300)
    public static void sprintClawStopsAtSelectedVictim(GameTestHelper h) {
        loaded(h,()->{
            var p=actor(h,true);
            for(int z=1;z<=9;z++)h.setBlock(4,54,z,Blocks.STONE);
            p.setPos(h.absoluteVec(new Vec3(4.5,55,2.5)));
            var mob=h.spawn(net.minecraft.world.entity.EntityType.IRON_GOLEM,4,55,6);
            mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(p.position().add(0,0,3.5));
            com.codex.blacklight.combat.CombatTargeting.requestLock(p,mob.getId());
            p.setSprinting(true);p.setDeltaMovement(0,0,.8);WeaponCombatSystem.acceptInput(p,true,false);
            h.assertTrue(ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).targetId()==mob.getId(),"normal gameplay selects receiver");
            for(int i=0;i<24 && ModAttachments.getData(p,ModAttachments.WEAPON_VISUAL).charging();i++)combatTick(p);
            for(int i=0;i<5;i++)combatTick(p);
            double gap=mob.getZ()-p.getZ();
            h.assertTrue(gap>=(p.getBbWidth()+mob.getBbWidth())*.5-.01 && gap<1.5,"slide and release stop within actual striking distance: "+gap);
            mob.discard();WeaponCombatSystem.cancel(p);h.succeed();
        });
    }
}
