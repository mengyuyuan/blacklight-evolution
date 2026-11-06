package com.codex.blacklight.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Prototype's slowMotion/small after a full-charge hit: a short half-speed window once the
 * contact freeze ends. Singleplayer only, since a server tick rate is shared by every player.
 * A tick rate the user set themselves is never touched.
 */
@EventBusSubscriber(modid="blacklight")
public final class HeavySlowMotion {
    private static final float NORMAL=20F,SLOW=10F;
    private static int delay=-1,remaining;private static MinecraftServer owner;
    private HeavySlowMotion(){}
    public static void trigger(ServerPlayer p,int afterTicks){
        var server=p.server;
        if(!server.isSingleplayer()||server.isPublished()||server.getPlayerCount()!=1)return;
        var rates=server.tickRateManager();
        if(owner==null&&(rates.tickrate()!=NORMAL||rates.isSprinting()||rates.isFrozen()))return;
        owner=server;delay=Math.max(0,afterTicks);remaining=5;
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        if(owner==null||e.getServer()!=owner)return;
        var rates=owner.tickRateManager();
        if(delay>0){delay--;return;}
        if(delay==0){delay=-1;if(rates.tickrate()==NORMAL&&!rates.isSprinting())rates.setTickRate(SLOW);else{owner=null;return;}}
        if(--remaining<=0)restore();
    }
    private static void restore(){
        if(owner!=null&&owner.tickRateManager().tickrate()==SLOW)owner.tickRateManager().setTickRate(NORMAL);
        owner=null;delay=-1;remaining=0;
    }
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){if(e.getServer()==owner)restore();}
}
