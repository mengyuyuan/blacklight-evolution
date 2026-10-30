package com.codex.blacklight.client;

import com.codex.blacklight.combat.BulletdiveTerrain;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.CriticalMassState;
import com.codex.blacklight.power.AdrenalineState;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.power.SkillSettings;
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
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
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
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var run=active;if(run==null)return;
        try { run.advance(); } catch(Throwable error) { run.finish(error); }
    }
    private static final class Run {
        static final int FIXTURE_DEPTH=BulletdiveTerrain.plan(80).depth()+2;
        final ServerPlayer p;final CompletableFuture<JsonObject> completion=new CompletableFuture<>();
        final Map<BlockPos,BlockState> originalBlocks=new LinkedHashMap<>();
        final BlacklightState originalState;
        final CriticalMassState originalMass;
        final AdrenalineState originalAdrenaline;
        final SkillSettings originalSettings;
        final boolean originalDrops;
        final Set<UUID> originalItems=new HashSet<>();
        final BlockPos low,high,protectedLane,disabledLane,canceledLane,noDropsLane;
        final long lowId,highId,protectedId,jumpId,disabledId,canceledId,noDropsId,began;
        int stage;
        boolean fixtureTouched,cancelTriggered;
        long stageAt,disabledAir;
        final JsonObject report=new JsonObject();
        Consumer<BlockEvent.BreakEvent> protector;
        BlockPos protectedCell,bedrock,container;
        Run(ServerPlayer p) {
            this.p=p;originalState=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
            originalMass=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CRITICAL_MASS);originalAdrenaline=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.ADRENALINE);
            originalSettings=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.SKILL_SETTINGS);
            originalDrops=p.level().getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
            began=p.level().getGameTime();
            int y=com.codex.blacklight.forge.WingMath.clamp(p.getBlockY()-1,p.level().getMinBuildHeight()+10,p.level().getMaxBuildHeight()-10);
            low=new BlockPos(p.getBlockX()-24,y,p.getBlockZ()+24);
            high=new BlockPos(p.getBlockX(),y,p.getBlockZ()+24);
            protectedLane=new BlockPos(p.getBlockX()+24,y,p.getBlockZ()+24);
            disabledLane=new BlockPos(p.getBlockX()-24,y,p.getBlockZ()-24);
            canceledLane=new BlockPos(p.getBlockX(),y,p.getBlockZ()-24);
            noDropsLane=new BlockPos(p.getBlockX()+24,y,p.getBlockZ()-24);
            long first=-10_000_000-began*8;lowId=first;highId=first-1;protectedId=first-2;jumpId=first-3;
            disabledId=first-4;canceledId=first-5;noDropsId=first-6;
        }
        void begin() {
            // Validate the complete region before the first write: no chunk loading
            // and no replacing a pre-existing block entity even in the QA save.
            for(var center:lanes())
                for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=0;y<FIXTURE_DEPTH;y++) {
                    var pos=center.offset(x,-y,z);
                    require(p.serverLevel().getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)!=null,"terrain fixture chunk is unloaded");
                    var state=p.level().getBlockState(pos);
                    require(!state.hasBlockEntity()&&p.level().getBlockEntity(pos)==null,"terrain fixture would replace an existing block entity");
                    originalBlocks.put(pos,state);
                }
            var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.activate(PowerForm.NONE);
            state.addEvolutionPoints(10_000);state.buyMovementUpgrade(MovementUpgrade.GLIDE_1);state.buyCombatSkill(CombatSkill.BULLETDIVE_DROP);
            for(var center:lanes())for(var item:items(center))originalItems.add(item.getUUID());
            com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
            fixtureTouched=true;
            settings(true);
            p.level().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(true,p.server);
            for(var pos:originalBlocks.keySet())p.serverLevel().setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(low,Blocks.IRON_BLOCK.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(high,Blocks.IRON_BLOCK.defaultBlockState());
            bedrock=high.offset(1,0,0);container=high.offset(0,0,1);protectedCell=high.offset(-1,0,0);
            p.serverLevel().setBlockAndUpdate(bedrock,Blocks.BEDROCK.defaultBlockState());
            p.serverLevel().setBlockAndUpdate(container,Blocks.CHEST.defaultBlockState());
            var chest=(ChestBlockEntity)p.serverLevel().getBlockEntity(container);
            require(chest!=null,"fixture chest entity missing");
            chest.setItem(0,new ItemStack(Items.DIAMOND,2));chest.setItem(1,new ItemStack(Items.DIAMOND,4));
            p.serverLevel().setBlockAndUpdate(protectedCell,Blocks.GOLD_BLOCK.defaultBlockState());
            protector=event->{
                if(event.getPlayer()==p&&!cancelTriggered&&Math.abs(event.getPos().getX()-canceledLane.getX())<=9
                        &&Math.abs(event.getPos().getZ()-canceledLane.getZ())<=9){
                    var pending=BulletdiveTerrain.snapshot(p,canceledId);
                    if(pending!=null&&pending.destroyed()>=16){cancelTriggered=true;settings(false);settings(true);}
                }
                if(event.getPlayer()==p&&(event.getPos().equals(protectedCell)
                    ||Math.abs(event.getPos().getX()-protectedLane.getX())<=9&&Math.abs(event.getPos().getZ()-protectedLane.getZ())<=9))event.setCanceled(true);};
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH,false,BlockEvent.BreakEvent.class,protector);
            var jump=BulletdiveTerrain.begin(p,jumpId,impact(low),3);
            require(jump.budget()==0&&BulletdiveTerrain.snapshot(p,jumpId).complete(),"low jump queued destructive work");
            var lowPlan=BulletdiveTerrain.begin(p,lowId,impact(low),12);
            BulletdiveTerrain.begin(p,lowId,impact(low),80);
            require(BulletdiveTerrain.snapshot(p,lowId).plan().equals(lowPlan),"duplicate session changed its plan");
            stageAt=p.level().getGameTime();
            report.addProperty("source","sequential actual BulletdiveTerrain.begin, live ServerTick batches, real loot/container inventory, gamerule and cancellation");
        }
        void advance() {
            long now=p.level().getGameTime();
            require(now-began<1800,"terrain work exceeded bounded QA timeout");
            if(stage==0){
                var lowStats=BulletdiveTerrain.snapshot(p,lowId);if(lowStats==null||!lowStats.complete())return;
                require(lowStats.destroyed()>0,"low-height soil lane did not break any terrain");
                require(p.level().getBlockState(low).is(Blocks.IRON_BLOCK),"low fall broke the hard center block");
                require(countAir(low)==lowStats.destroyed(),"low destroyed count differs from actual blocks");
                require(itemCount(low,Items.DIRT)>0,"real low-height block drops missing");
                report.add("low",stats(lowStats));report.addProperty("lowActualAir",countAir(low));
                BulletdiveTerrain.begin(p,highId,impact(high),80);stage=1;stageAt=now;
            }else if(stage==1){
                var lowStats=BulletdiveTerrain.snapshot(p,lowId);var highStats=BulletdiveTerrain.snapshot(p,highId);
                if(highStats==null||!highStats.complete())return;
                require(highStats.destroyed()>lowStats.destroyed(),"high fall did not destroy more actual blocks");
                require(highStats.maximumRadius()>lowStats.maximumRadius(),"high fall did not widen crater");
                require(highStats.maximumDepth()>lowStats.maximumDepth(),"high fall did not deepen crater");
                require(p.level().getBlockState(high).isAir(),"high fall did not break hard center");
                require(p.level().getBlockState(bedrock).is(Blocks.BEDROCK),"bedrock was destroyed");
                require(p.level().getBlockState(container).isAir()&&p.level().getBlockEntity(container)==null,"container was not genuinely broken");
                require(p.level().getBlockState(protectedCell).is(Blocks.GOLD_BLOCK),"canceled protected block was destroyed");
                require(countAir(high)==highStats.destroyed(),"high destroyed count differs from real blocks");
                report.addProperty("chestDiamondsBeforeAssertion",itemCount(high,Items.DIAMOND));
                require(itemCount(high,Items.DIAMOND)==6,"chest inventory must drop once, exactly six diamonds");
                require(itemCount(high,Items.CHEST)==1,"chest block loot must drop once");
                require(itemCount(high,Items.DIRT)>0,"real high-height block drops missing");
                report.add("high",stats(highStats));report.addProperty("highActualAir",countAir(high));
                report.addProperty("chestDropped",itemCount(high,Items.CHEST));report.addProperty("inventoryDiamondsDropped",itemCount(high,Items.DIAMOND));
                report.addProperty("highDirtDropped",itemCount(high,Items.DIRT));report.addProperty("highCompletionTicks",now-stageAt);
                BulletdiveTerrain.begin(p,protectedId,impact(protectedLane),80);stage=2;stageAt=now;
            }else if(stage==2){
                var s=BulletdiveTerrain.snapshot(p,protectedId);if(s==null||!s.complete())return;
                require(s.destroyed()==0&&s.protectedBlocks()>0,"region event did not protect real terrain");
                report.add("protected",stats(s));settings(false);
                BulletdiveTerrain.begin(p,disabledId,impact(disabledLane),80);stage=3;stageAt=now;
            }else if(stage==3){
                if(now-stageAt<4)return;
                require(BulletdiveTerrain.snapshot(p,disabledId)==null&&countAir(disabledLane)==0,"disabled preference queued/destructed terrain");
                report.addProperty("disabledActualAir",countAir(disabledLane));settings(true);
                BulletdiveTerrain.begin(p,canceledId,impact(canceledLane),80);stage=4;stageAt=now;
            }else if(stage==4){
                var s=BulletdiveTerrain.snapshot(p,canceledId);if(s==null||s.destroyed()==0)return;
                require(cancelTriggered&&s.complete()&&s.reason().equals("block_breaking_disabled"),"mid-burst preference change did not cancel pending terrain");
                disabledAir=countAir(canceledLane);stage=5;stageAt=now;
            }else if(stage==5){
                if(now-stageAt<4)return;var s=BulletdiveTerrain.snapshot(p,canceledId);
                require(s!=null&&s.complete()&&s.reason().equals("block_breaking_disabled"),"pending work did not stop after toggle off");
                require(countAir(canceledLane)==disabledAir,"disabled pending work continued breaking");
                report.add("canceledBySetting",stats(s));report.addProperty("sameTickOffOnCanceledOldWork",true);settings(true);
                p.level().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false,p.server);
                BulletdiveTerrain.begin(p,noDropsId,impact(noDropsLane),80);stage=6;stageAt=now;
            }else if(stage==6){
                var s=BulletdiveTerrain.snapshot(p,noDropsId);if(s==null||!s.complete())return;
                require(s.destroyed()>0&&countAir(noDropsLane)==s.destroyed(),"tileDrops=false did not genuinely break blocks");
                require(items(noDropsLane).stream().noneMatch(item->!originalItems.contains(item.getUUID())),"tileDrops=false generated block loot");
                report.add("tileDropsOff",stats(s));report.addProperty("tileDropsOffNewItems",0);finish(null);
            }
        }
        BlockPos[] lanes(){return new BlockPos[]{low,high,protectedLane,disabledLane,canceledLane,noDropsLane};}
        void settings(boolean enabled){com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.SKILL_SETTINGS,new SkillSettings(enabled));
            BulletdiveTerrain.settingsChanged(p);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.SKILL_SETTINGS);}
        List<ItemEntity> items(BlockPos center){return p.serverLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(center.getX()-11,center.getY()-FIXTURE_DEPTH-4,center.getZ()-11,
                        center.getX()+12,center.getY()+12,center.getZ()+12));}
        int itemCount(BlockPos center,net.minecraft.world.item.Item kind){return items(center).stream()
                .filter(item->!originalItems.contains(item.getUUID())&&item.getItem().is(kind))
                .mapToInt(item->item.getItem().getCount()).sum();}
        int countAir(BlockPos center) {
            int count=0;for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++)for(int y=0;y<FIXTURE_DEPTH;y++)
                if(p.level().getBlockState(center.offset(x,-y,z)).isAir())count++;
            return count;
        }
        JsonObject stats(BulletdiveTerrain.Snapshot s) {
            var obj=new JsonObject();obj.addProperty("height",s.plan().height());obj.addProperty("radius",s.plan().radius());
            obj.addProperty("depth",s.plan().depth());obj.addProperty("budget",s.plan().budget());obj.addProperty("visited",s.visited());
            obj.addProperty("destroyed",s.destroyed());obj.addProperty("protected",s.protectedBlocks());
            obj.addProperty("maximumRadius",s.maximumRadius());obj.addProperty("maximumDepth",s.maximumDepth());
            obj.addProperty("reason",s.reason());obj.addProperty("elapsedTicks",s.elapsedTicks());obj.addProperty("firstTickDestroyed",s.firstTickDestroyed());obj.addProperty("peakBreaksPerTick",s.peakBreaksPerTick());obj.addProperty("peakSliceMillis",s.peakSliceMillis());return obj;
        }
        void finish(Throwable error) {
            active=null;report.addProperty("passed",error==null);if(error!=null)report.addProperty("error",error.toString());
            try {
                if(protector!=null)MinecraftForge.EVENT_BUS.unregister(protector);
                // Cancel any unfinished owner jobs before restoring the disposable terrain.
                for(long session:new long[]{lowId,highId,protectedId,jumpId,disabledId,canceledId,noDropsId})BulletdiveTerrain.cancel(p,session);
                if(fixtureTouched){
                    for(var center:lanes())for(var item:items(center))if(!originalItems.contains(item.getUUID()))item.discard();
                    for(var entry:originalBlocks.entrySet())p.serverLevel().setBlockAndUpdate(entry.getKey(),entry.getValue());
                    p.level().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(originalDrops,p.server);
                }
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.SKILL_SETTINGS,originalSettings);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.SKILL_SETTINGS);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.PLAYER_STATE,originalState);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.PLAYER_STATE);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.CRITICAL_MASS,originalMass);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.CRITICAL_MASS);
                com.codex.blacklight.registry.ModAttachments.setData(p, ModAttachments.ADRENALINE,originalAdrenaline);com.codex.blacklight.registry.ModAttachments.syncData(p, ModAttachments.ADRENALINE);
            } catch(Throwable restoreError){report.addProperty("passed",false);report.addProperty("restoreError",restoreError.toString());}
            completion.complete(report);
        }
    }
    private static Vec3 impact(BlockPos surface){return new Vec3(surface.getX()+.5,surface.getY()+1,surface.getZ()+.5);}
    private static void require(boolean condition,String message){if(!condition)throw new IllegalStateException(message);}
}
