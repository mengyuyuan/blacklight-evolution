package com.codex.blacklight.entity;

import com.codex.blacklight.power.GrowthSystem;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Survival hook: only the Supreme Parasite syringe injected into a Colossal Hive releases the Supreme Hunter.
 * Defeat and consumption are separate milestones; nothing repeats once defeated. */
@EventBusSubscriber(modid="blacklight")
public final class SupremeHunterRewards {
    public static final String DEFEATED="BlacklightSupremeHunterDefeated",SUMMONED="BlacklightSupremeHunterSummoned";
    public static final int EMERGE_DELAY=100;
    private record Pending(ServerLevel level,Vec3 at,UUID player,long due){}
    private static final List<Pending> PENDING=new ArrayList<>();
    private SupremeHunterRewards(){}

    /** Injection path: always releases a Supreme Hunter (also after a previous kill), faster than the natural rise. */
    public static void parasiteRelease(BlacklightHive hive,ServerPlayer player){
        if(player==null)return;
        keep(player).putBoolean(SUMMONED,true);
        PENDING.add(new Pending((ServerLevel)hive.level(),hive.position(),player.getUUID(),hive.level().getGameTime()+30));
        player.displayClientMessage(Component.translatable("message.blacklight.parasite_hive"),false);
    }
    /** Milestones live in the persisted sub-tag so death and respawn do not reset them. */
    static net.minecraft.nbt.CompoundTag keep(ServerPlayer player){
        var d=player.getPersistentData();var key=net.minecraft.world.entity.player.Player.PERSISTED_NBT_TAG;
        if(!d.contains(key,10))d.put(key,new net.minecraft.nbt.CompoundTag());
        var k=d.getCompound(key);
        for(String f:new String[]{DEFEATED,SUMMONED})if(d.getBoolean(f)){k.putBoolean(f,true);d.remove(f);}
        return k;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||PENDING.isEmpty())return;
        for(var it=PENDING.iterator();it.hasNext();){
            var p=it.next();
            if(p.level.getServer()!=event.getServer())continue;
            if(p.level.getGameTime()<p.due)continue;
            it.remove();
            var player=p.level.getServer().getPlayerList().getPlayer(p.player);
            var boss=summon(p.level,p.at,player);
            if(boss==null&&player!=null){keep(player).remove(SUMMONED);}
        }
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PENDING.removeIf(p->p.level.getServer()==event.getServer());}

    /** Searches a ring around the point for room; returns null when nowhere fits. */
    public static BlacklightSupremeHunter summon(ServerLevel level,Vec3 at,ServerPlayer target){
        var boss=ModEntities.SUPREME_HUNTER.get().create(level);if(boss==null)return null;
        var random=level.random;
        for(int attempt=0;attempt<24;attempt++){
            double r=attempt==0?0:4+random.nextDouble()*8,a=random.nextDouble()*Math.PI*2;
            double x=at.x+Math.cos(a)*r,z=at.z+Math.sin(a)*r;
            for(int dy=4;dy>=-6;dy--){
                var pos=BlockPos.containing(x,at.y+dy,z);
                if(!level.getBlockState(pos.below()).isSolid())continue;
                boss.moveTo(x,pos.getY(),z,random.nextFloat()*360,0);
                if(!level.noCollision(boss)||level.containsAnyLiquid(boss.getBoundingBox()))continue;
                boss.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null,null);
                if(target!=null)boss.setTarget(target);
                level.addFreshEntityWithPassengers(boss);
                level.playSound(null,pos,SoundEvents.WARDEN_EMERGE,SoundSource.HOSTILE,4F,.6F);
                return boss;
            }
        }
        return null;
    }
    static void defeated(BlacklightSupremeHunter boss,ServerPlayer player,boolean consumed){
        if(player==null)return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(player,ModAttachments.PLAYER_STATE);
        keep(player).putBoolean(DEFEATED,true);
        GrowthSystem.award(player,"supreme_hunter");
        com.codex.blacklight.power.ParasiteSystem.grantApex(player);
        if(consumed)GrowthSystem.award(player,"supreme_hunter_consumed");
        if(state.isInfected()&&!consumed){state.addEvolutionPoints(400);state.addBiomass(500);com.codex.blacklight.registry.ModAttachments.syncData(player,ModAttachments.PLAYER_STATE);}
        player.displayClientMessage(Component.translatable(consumed?"message.blacklight.supreme_hunter_consumed":"message.blacklight.supreme_hunter_slain"),false);
    }
}
