package com.codex.blacklight.combat;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/**
 * The same short half-speed window as the 1.21 build, after contact freeze.
 * Forge 1.20.1 applies it to the integrated server scheduler and client timer;
 * no shared server, saved setting or timer field is changed.
 */
@EventBusSubscriber(modid="blacklight")
public final class HeavySlowMotion {
    private static volatile MinecraftServer owner;
    private static volatile boolean active;
    private static int delay=-1,remaining;
    private HeavySlowMotion(){}
    public static void trigger(ServerPlayer p,int afterTicks){
        var server=p.server;
        if(!eligible(server))return;
        owner=server;delay=Math.max(0,afterTicks);remaining=5;
    }
    private static boolean eligible(MinecraftServer server){
        return server!=null&&server.isSingleplayer()&&!server.isPublished()&&server.getPlayerCount()==1;
    }
    public static boolean active(MinecraftServer server){return active&&server!=null&&server==owner;}
    public static long serverMillis(MinecraftServer server,long original){
        return original==50L&&active(server)?100L:original;
    }
    public static float clientMillis(MinecraftServer server,float original){
        return original==50F&&active(server)?100F:original;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.END||owner==null||e.getServer()!=owner)return;
        if(!eligible(owner)){restore();return;}
        if(delay>0){delay--;return;}
        if(delay==0){delay=-1;active=true;}
        if(--remaining<=0)restore();
    }
    private static void restore(){active=false;owner=null;delay=-1;remaining=0;}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){if(e.getServer()==owner)restore();}
}
