package com.codex.blacklight.gametest;

import com.codex.blacklight.city.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.combat.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TravelBoundaryGameTest {
    private static ServerPlayer player(GameTestHelper h){
        var p=new net.minecraftforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"TravelBoundaryQA"));
        p.setPos(h.absolutePos(new BlockPos(4,20,4)).getCenter());h.getLevel().addNewPlayer(p);return p;
    }
    @GameTest(template="wall_test",batch="travel_null_boundaries")
    public static void missingTransitActorsAndTicketsNeverDereferenceNull(GameTestHelper h){
        var ticket=new HydraDestination();
        h.assertTrue(HydraLink.load(null)==null&&HydraLink.load(new net.minecraft.nbt.CompoundTag())==null,"missing or incomplete persisted links are rejected");
        h.assertTrue(!ticket.prepare((net.minecraft.server.level.ServerLevel)null),"missing source world is not a valid ticket");
        h.assertTrue(!ticket.prepare(h.getLevel(),null),"missing arrival coordinate rejects and releases ticket");
        ticket.release();ticket.release();h.assertTrue(ticket.diagnostics().equals("released"),"cleanup is idempotent");
        h.assertTrue(HydraTransport.carrier(null)==null&&!HydraTransport.active(null),"camera/player gap at dimension switch has no carrier");HydraTransport.place(null);
        h.assertTrue(CityTravel.travel(null)==0&&!CityTravel.enterFromHydra(null,BlockPos.ZERO,1.6F),"late command/transport rejects missing player");
        h.assertTrue(!CityTravel.isCity(null),"missing dimension key is not a city");h.succeed();
    }
    @GameTest(template="wall_test",batch="travel_missing_endpoint")
    public static void invalidEndpointLeavesOriginAndReturnDataUntouched(GameTestHelper h){
        var p=player(h);var origin=p.position();var saved=new net.minecraft.nbt.CompoundTag();saved.putString("dimension","minecraft:overworld");saved.putDouble("x",91);
        p.getPersistentData().put("BlacklightCityReturn",saved.copy());
        var portal=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());portal.setPos(origin);
        var missing=net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,new net.minecraft.resources.ResourceLocation("blacklight","missing_travel_test"));
        portal.connect(p.getUUID(),new HydraLink(missing,p.blockPosition(),java.util.UUID.randomUUID()));
        try{
            h.assertTrue(!CityTravel.transferFromHydra(p,portal,p.blockPosition(),1.6F),"missing dimension rejects handoff");
            h.assertTrue(!CityTravel.transferFromHydra(p,null,null,1.6F),"null return coordinate rejects handoff");
            h.assertTrue(!CityTravel.transferFromHydra(p,null,p.blockPosition(),Float.NaN),"malformed body size rejects before chunk work");
            h.assertTrue(p.position().equals(origin)&&p.level()==h.getLevel()&&!HydraTransport.active(p),"player remains free in original world");
            h.assertTrue(p.getPersistentData().getCompound("BlacklightCityReturn").equals(saved),"failed handoff cannot rewrite return marker");h.succeed();
        }finally{portal.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
    @GameTest(template="wall_test",batch="travel_removed_endpoint")
    public static void removedGatewayCannotClaimLivePlayer(GameTestHelper h){
        var p=player(h);var gateway=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());gateway.discard();
        try{
            h.assertTrue(!gateway.beginArrival(p)&&!gateway.hold(p),"dead gateway cannot begin attachment writes");
            h.assertTrue(!CityTravel.transferFromHydra(p,gateway,p.blockPosition(),1.6F),"removed source cannot start a transaction");
            h.assertTrue(!HydraTransport.active(p),"no phantom grip survives failed gateway");h.succeed();
        }finally{h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
    @GameTest(template="wall_test",batch="travel_capability_teardown")
    public static void invalidatedPlayerDuringGripCanAbortAndRecover(GameTestHelper h){
        var p=player(h);var gateway=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());gateway.setPos(p.position());h.getLevel().addFreshEntity(gateway);
        var persistent=ModAttachments.getData(p,ModAttachments.PLAYER_STATE);persistent.infect();
        try{
            h.assertTrue(gateway.beginArrival(p)&&HydraTransport.active(p),"real arrival must claim the player before teardown");
            p.invalidateCaps();
            h.assertTrue(!ModAttachments.available(p)&&!HydraTransport.active(p),"invalid original data is not revived by passive transport lookup");
            gateway.abortTransport(p);
            h.assertTrue(gateway.heldId()<0,"abort clears the hydra even when player data is unavailable");
            h.assertTrue(!BiomassSystemShim.begin(p),"late body action rejects invalid capability");
            p.reviveCaps();
            h.assertTrue(ModAttachments.getData(p,ModAttachments.PLAYER_STATE)==persistent&&persistent.isInfected(),"revive exposes the original progression, not an empty replacement");
            CityTravel.transitLanding(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));
            h.assertTrue(ModAttachments.getData(p,ModAttachments.HYDRA_CARRIER)<0,"landing cleanup clears a stale grip after data returns");h.succeed();
        }finally{p.reviveCaps();gateway.discard();h.getLevel().removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
    private static final class BiomassSystemShim {static boolean begin(ServerPlayer p){return com.codex.blacklight.power.BiomassSystem.begin(p,false);}}
}
