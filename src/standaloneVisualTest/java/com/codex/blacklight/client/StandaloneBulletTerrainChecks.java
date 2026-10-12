package com.codex.blacklight.client;

import com.codex.blacklight.combat.BulletdiveTerrain;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.CriticalMassState;
import com.codex.blacklight.power.AdrenalineState;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Explicit disposable-world acceptance: actual queued begin() calls, block changes and cancellation. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class StandaloneBulletTerrainChecks {
    private static final Path QA=Path.of("D:/Blacklight-Standalone/run-native-qa").toAbsolutePath().normalize();
    private static Run active;
    private StandaloneBulletTerrainChecks() { }
    public static CompletableFuture<JsonObject> run(ServerPlayer p) {
        if(!p.server.isSameThread())throw new IllegalStateException("Terrain checks require server thread");
        if(!p.server.getServerDirectory().toAbsolutePath().normalize().equals(QA)
                ||!p.server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                    .equals(QA.resolve("saves/Blacklight Standalone QA v4")))
            throw new IllegalStateException("Terrain checks refuse non-disposable directory/save");
        if(active!=null)return active.completion;
        var run=new Run(p);active=run;
        try { run.begin(); } catch(Throwable error) { run.finish(error); }
        return run.completion;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {
        var run=active;if(run==null)return;
        try { run.advance(); } catch(Throwable error) { run.finish(error); }
    }
    private static final class Run {
        final ServerPlayer p;final CompletableFuture<JsonObject> completion=new CompletableFuture<>();
        final Map<BlockPos,BlockState> originalBlocks=new LinkedHashMap<>();
        final BlacklightState originalState;
        final CriticalMassState originalMass;
        final AdrenalineState originalAdrenaline;
        final BlockPos low,high,protectedLane;
        final long lowId,highId,protectedId,jumpId,began;
        final JsonObject report=new JsonObject();
        Consumer<BlockEvent.BreakEvent> protector;
        BlockPos protectedCell,bedrock,container;
        Run(ServerPlayer p) {
            this.p=p;originalState=p.getData(ModAttachments.PLAYER_STATE);
            originalMass=p.getData(ModAttachments.CRITICAL_MASS);originalAdrenaline=p.getData(ModAttachments.ADRENALINE);
            began=p.level().getGameTime();
            int y=Math.clamp(p.getBlockY()-1,p.level().getMinBuildHeight()+10,p.level().getMaxBuildHeight()-10);
            low=new BlockPos(p.getBlockX()-24,y,p.getBlockZ()+24);
            high=new BlockPos(p.getBlockX(),y,p.getBlockZ()+24);
            protectedLane=new BlockPos(p.getBlockX()+24,y,p.getBlockZ()+24);
            long first=-10_000_000-began*4;lowId=first;highId=first-1;protectedId=first-2;jumpId=first-3;
        }
        void begin() {
            // Validate the complete region before the first write: no chunk loading
            // and no replacing a pre-existing block entity even in the QA save.
            for(var center:new BlockPos[]{low,high,protectedLane})
                for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=0;y<6;y++) {
                    var pos=center.offset(x,-y,z);
                    require(p.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null,"terrain fixture chunk is unloaded");
                    var state=p.level().getBlockState(pos);
                    require(!state.hasBlockEntity()&&p.level().getBlockEntity(pos)==null,"terrain fixture would replace an existing block entity");
                    originalBlocks.put(pos,state);
                }
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);
            state.addEvolutionPoints(10_000);state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP);
            p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
            for(var pos:originalBlocks.keySet())p.serverLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(low,Blocks.IRON_BLOCK.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(high,Blocks.IRON_BLOCK.defaultBlockState());
            bedrock=high.offset(1,0,0);container=high.offset(0,0,1);protectedCell=high.offset(-1,0,0);
            p.serverLevel().setBlockAndUpdate(bedrock,Blocks.BEDROCK.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(container,Blocks.CHEST.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(protectedCell,Blocks.GOLD_BLOCK.defaultBlockState());
            protector=event->{if(event.getPlayer()==p&&(event.getPos().equals(protectedCell)
                    ||Math.abs(event.getPos().getX()-protectedLane.getX())<=9&&Math.abs(event.getPos().getZ()-protectedLane.getZ())<=9))event.setCanceled(true);};
            NeoForge.EVENT_BUS.addListener(EventPriority.HIGH,false,BlockEvent.BreakEvent.class,protector);
            var jump=BulletdiveTerrain.begin(p,jumpId,impact(low),3);
            require(jump.budget()==0&&BulletdiveTerrain.snapshot(p,jumpId).complete(),"low jump queued destructive work");
            var lowPlan=BulletdiveTerrain.begin(p,lowId,impact(low),12);
            BulletdiveTerrain.begin(p,lowId,impact(low),80);
            require(BulletdiveTerrain.snapshot(p,lowId).plan().equals(lowPlan),"duplicate session changed its plan");
            BulletdiveTerrain.begin(p,highId,impact(high),80);
            BulletdiveTerrain.begin(p,protectedId,impact(protectedLane),80);
            report.addProperty("source","actual BulletdiveTerrain.begin, live ServerTick batches, real block states and canceled BlockEvent.BreakEvent");
        }
        void advance() {
            var lowStats=BulletdiveTerrain.snapshot(p,lowId);var highStats=BulletdiveTerrain.snapshot(p,highId);
            var protectedStats=BulletdiveTerrain.snapshot(p,protectedId);
            if(lowStats==null||highStats==null||protectedStats==null)return;
            require(p.level().getGameTime()-began<160,"terrain work exceeded bounded QA timeout");
            if(!lowStats.complete()||!highStats.complete()||!protectedStats.complete())return;
            require(lowStats.destroyed()>0,"low-height soil lane did not break any terrain");
            require(highStats.destroyed()>lowStats.destroyed(),"high fall did not destroy more actual blocks");
            require(highStats.maximumRadius()>lowStats.maximumRadius(),"high fall did not widen the actual crater");
            require(highStats.maximumDepth()>lowStats.maximumDepth(),"high fall did not deepen the actual crater");
            require(p.level().getBlockState(low).is(Blocks.IRON_BLOCK),"low fall broke the hard center block");
            require(p.level().getBlockState(high).isAir(),"high fall did not break the hard center block");
            require(p.level().getBlockState(bedrock).is(Blocks.BEDROCK),"bedrock was destroyed");
            require(p.level().getBlockState(container).is(Blocks.CHEST)&&p.level().getBlockEntity(container)!=null,"container was destroyed");
            require(p.level().getBlockState(protectedCell).is(Blocks.GOLD_BLOCK),"canceled protected block was destroyed");
            require(protectedStats.destroyed()==0&&protectedStats.protectedBlocks()>0,"region cancellation did not protect actual terrain");
            int lowCount=countAir(low),highCount=countAir(high);
            require(lowCount==lowStats.destroyed()&&highCount==highStats.destroyed(),"destroyed count differs from actual removed blocks");
            report.add("low",stats(lowStats));report.add("high",stats(highStats));report.add("protected",stats(protectedStats));
            report.addProperty("lowActualAir",lowCount);report.addProperty("highActualAir",highCount);
            finish(null);
        }
        int countAir(BlockPos center) {
            int count=0;for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=0;y<6;y++)
                if(p.level().getBlockState(center.offset(x,-y,z)).isAir())count++;
            return count;
        }
        JsonObject stats(BulletdiveTerrain.Snapshot s) {
            var obj=new JsonObject();obj.addProperty("height",s.plan().height());obj.addProperty("radius",s.plan().radius());
            obj.addProperty("depth",s.plan().depth());obj.addProperty("budget",s.plan().budget());obj.addProperty("visited",s.visited());
            obj.addProperty("destroyed",s.destroyed());obj.addProperty("protected",s.protectedBlocks());
            obj.addProperty("maximumRadius",s.maximumRadius());obj.addProperty("maximumDepth",s.maximumDepth());
            obj.addProperty("reason",s.reason());return obj;
        }
        void finish(Throwable error) {
            active=null;report.addProperty("passed",error==null);if(error!=null)report.addProperty("error",error.toString());
            try {
                if(protector!=null)NeoForge.EVENT_BUS.unregister(protector);
                // Cancel any unfinished owner jobs before restoring the disposable terrain.
                for(long session:new long[]{lowId,highId,protectedId,jumpId})BulletdiveTerrain.cancel(p,session);
                for(var entry:originalBlocks.entrySet())p.serverLevel().setBlockAndUpdate(entry.getKey(),entry.getValue());
                p.setData(ModAttachments.PLAYER_STATE,originalState);p.syncData(ModAttachments.PLAYER_STATE);
                p.setData(ModAttachments.CRITICAL_MASS,originalMass);p.syncData(ModAttachments.CRITICAL_MASS);
                p.setData(ModAttachments.ADRENALINE,originalAdrenaline);p.syncData(ModAttachments.ADRENALINE);
            } catch(Throwable restoreError){report.addProperty("passed",false);report.addProperty("restoreError",restoreError.toString());}
            completion.complete(report);
        }
    }
    private static Vec3 impact(BlockPos surface){return new Vec3(surface.getX()+.5,surface.getY()+1,surface.getZ()+.5);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
