package com.codex.blacklight.entity;

import com.codex.blacklight.world.FieldNotes;
import com.codex.blacklight.power.GrowthSystem;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.TickEvent;
import java.util.*;

/** Survival hook: only the Supreme Parasite syringe injected into a Colossal Hive releases the Supreme Hunter.
 * Defeat and consumption are separate milestones; nothing repeats once defeated. */
@EventBusSubscriber(modid="blacklight")
public final class SupremeHunterRewards {
    public static final String DEFEATED="BlacklightSupremeHunterDefeated",SUMMONED="BlacklightSupremeHunterSummoned";
    public static final int EMERGE_DELAY=100;
    private SupremeHunterRewards(){}

    /** Injection path: always releases a Supreme Hunter (also after a previous kill), faster than the natural rise. */
    public static void parasiteRelease(BlacklightHive hive,ServerPlayer player){
        if(player==null)return;
        keep(player).putBoolean(SUMMONED,true);
        SupremeHunterEmergence.get((ServerLevel)hive.level()).schedule(hive.position(), player.getUUID(), hive.level().getGameTime()+30);
        FieldNotes.record(player, Component.translatable("message.blacklight.parasite_hive"));
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
        if(event.phase!=TickEvent.Phase.END)return;
        for (var level : event.getServer().getAllLevels()) SupremeHunterEmergence.get(level).tick(level);
    }

    /** Searches a ring around the point for room; returns null when nowhere fits. */
    public static BlacklightSupremeHunter summon(ServerLevel level,Vec3 at,ServerPlayer target){
        return summon(level, at, target, UUID.randomUUID());
    }
    static BlacklightSupremeHunter summon(ServerLevel level, Vec3 at, ServerPlayer target, UUID id) {
        var boss=ModEntities.SUPREME_HUNTER.get().create(level);if(boss==null)return null;
        boss.setUUID(id);
        var random=level.random;
        for(int attempt=0;attempt<24;attempt++){
            double r=attempt==0?0:4+random.nextDouble()*8,a=random.nextDouble()*Math.PI*2;
            double x=at.x+Math.cos(a)*r,z=at.z+Math.sin(a)*r;
            for(int dy=4;dy>=-6;dy--){
                var pos=BlockPos.containing(x,at.y+dy,z);
                if(!level.hasChunkAt(pos) || !level.getBlockState(pos.below()).isSolid())continue;
                boss.moveTo(x,pos.getY(),z,random.nextFloat()*360,0);
                if(!level.noCollision(boss)||level.containsAnyLiquid(boss.getBoundingBox()))continue;
                boss.finalizeSpawn(level,level.getCurrentDifficultyAt(pos),MobSpawnType.EVENT,null,null);
                if(target!=null && target.level()==level && target.isAlive())boss.setTarget(target);
                if (!level.addFreshEntity(boss) || boss.isRemoved()) return null;
                level.playSound(null,pos,SoundEvents.WARDEN_EMERGE,SoundSource.HOSTILE,4F,.6F);
                return boss;
            }
        }
        return null;
    }
    static void defeated(BlacklightSupremeHunter boss,ServerPlayer player,boolean consumed){
        if(player==null)return;
        keep(player).putBoolean(DEFEATED,true);
        GrowthSystem.award(player,"supreme_hunter");
        com.codex.blacklight.power.ParasiteSystem.grantApex(player);
        if(consumed)GrowthSystem.award(player,"supreme_hunter_consumed");
        FieldNotes.record(player, Component.translatable(consumed?"message.blacklight.supreme_hunter_consumed":"message.blacklight.supreme_hunter_slain"));
    }
}
