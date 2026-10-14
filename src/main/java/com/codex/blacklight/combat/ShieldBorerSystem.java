package com.codex.blacklight.combat;

import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.UtilityPowerSystem;
import com.codex.blacklight.registry.ModAttachments;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;

/** Short, server-owned shield corridor. No remote block coordinates are accepted. */
@EventBusSubscriber(modid="blacklight")
public final class ShieldBorerSystem {
    public static final int MAX_BREAKS_PER_PLAYER=12, MAX_BREAKS_PER_TICK=24;
    private static final ThreadLocal<BlockEvent.BreakEvent> OWN_BREAK=new ThreadLocal<>();
    private static int nextPlayer;
    private ShieldBorerSystem() {}
    public static int tier(com.codex.blacklight.state.BlacklightState state){
        if(!state.hasCombatSkill(CombatSkill.SHIELD_BORER))return 0;
        if(!state.hasCombatSkill(CombatSkill.SHIELD_BORER_BOOST_1))return 1;
        return state.hasCombatSkill(CombatSkill.SHIELD_BORER_BOOST_2)?3:2;
    }
    public static int breakLimit(int tier){return tier<=0?0:Math.min(MAX_BREAKS_PER_PLAYER,3+3*Math.min(tier,3));}
    public static boolean ownsBreakEvent(BlockEvent.BreakEvent event){return OWN_BREAK.get()==event;}
    /** Grounded drilling takes precedence over automatically starting a wall run.
     * Shared by client prediction and server movement, never destroys client blocks. */
    public static boolean groundDrive(net.minecraft.world.entity.player.Player p,boolean sprint,int forward){
        if(!ModAttachments.available(p))return false;
        var state=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);
        return sprint&&forward>0&&p.onGround()&&p.isAlive()&&!p.isSpectator()&&p.mayBuild()
                &&state.isInfected()&&state.isPrototypeMode()&&state.hasCombatSkill(CombatSkill.SHIELD_BORER)
                &&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.SKILL_SETTINGS).shieldBorerBlockBreaking()
                &&UtilityPowerSystem.shieldReady(p)&&!p.isPassenger()&&!p.isShiftKeyDown()
                &&!p.isInWaterOrBubble()&&!p.isInLava();
    }
    public static boolean eligible(ServerPlayer p){
        if(!ModAttachments.available(p))return false;
        var movement=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE);
        var input=movement.wallInput();
        return groundDrive(p,input.sprint(),input.forward())&&!input.detach()&&movement.chargeTicks()==0
                &&!com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.CONSUMING)&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GRAB_STAGE)==0;
    }
    /** A two-block-high corridor; never excavate the support below the feet. */
    static List<BlockPos> candidates(Vec3 feet,float yaw){return candidates(feet,yaw,1);}
    static List<BlockPos> candidates(Vec3 feet,float yaw,int tier){
        if(tier<=0)return List.of();
        Vec3 forward=Vec3.directionFromRotation(0,yaw),right=new Vec3(-forward.z,0,forward.x);
        Set<BlockPos> cells=new LinkedHashSet<>();
        for(int step=0;step<2+Math.min(tier,3);step++)for(double y:new double[]{0,.9,1.7})for(double side:new double[]{0,-.38,.38}){
            double distance=.45+.5*step;
            cells.add(BlockPos.containing(feet.add(forward.scale(distance)).add(right.scale(side)).add(0,y+.05,0)));
        }
        return List.copyOf(cells);
    }
    @SubscribeEvent public static void tick(ServerTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        var players=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers();
        if(players.isEmpty())return;
        long start=System.nanoTime(),allowance=Math.min(2_000_000,BulletdiveTerrain.remainingTickNanos());
        if(allowance<=0)return;
        var loads=new IdentityHashMap<ServerLevel,BulletdiveTerrain.LoadBudget>();
        int broken=0,cursor=Math.floorMod(nextPlayer,players.size());
        for(int n=0;n<players.size()&&broken<MAX_BREAKS_PER_TICK&&System.nanoTime()-start<allowance;n++){
            var p=players.get(cursor);cursor=(cursor+1)%players.size();
            if(!eligible(p))continue;
            var level=p.serverLevel();var load=loads.computeIfAbsent(level,BulletdiveTerrain.LoadBudget::sample);
            if(!load.available())continue;
            var effects=new BulletdiveBreakEffects();int personal=0;
            float yaw=com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).cameraYaw(p.getYRot());
            int tier=tier(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE));
            for(var pos:candidates(p.position(),yaw,tier)){
                if(personal>=breakLimit(tier)||broken>=MAX_BREAKS_PER_TICK||!load.available()
                        ||System.nanoTime()-start>=allowance)break;
                if(breakBlock(p,pos,effects)){personal++;broken++;load.spawnedItems(effects.newItems());}
            }
        }
        nextPlayer=cursor;
    }
    public static boolean breakBlock(ServerPlayer p,BlockPos pos,BulletdiveBreakEffects effects){
        var level=p.serverLevel();
        if(!eligible(p)||!candidates(p.position(),com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.MOVEMENT_STATE).cameraYaw(p.getYRot()),tier(com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE))).contains(pos)
                ||!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)
                ||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||!level.mayInteract(p,pos))return false;
        var state=level.getBlockState(pos);float hardness=state.getDestroySpeed(level,pos);
        if(state.isAir()||state.getBlock() instanceof LiquidBlock||!Float.isFinite(hardness)||hardness<0)return false;
        var shape=state.getCollisionShape(level,pos);
        if(!shape.isEmpty()&&pos.getY()+shape.max(net.minecraft.core.Direction.Axis.Y)<=p.getY()+.01)return false;
        var event=new BlockEvent.BreakEvent(level,pos,state,p);var previous=OWN_BREAK.get();OWN_BREAK.set(event);
        try{MinecraftForge.EVENT_BUS.post(event);}finally{if(previous==null)OWN_BREAK.remove();else OWN_BREAK.set(previous);}
        if(event.isCanceled()||!eligible(p)||p.level()!=level||!level.mayInteract(p,pos)
                ||level.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null||!state.equals(level.getBlockState(pos)))return false;
        return effects.destroy(level,pos,p);
    }
}
