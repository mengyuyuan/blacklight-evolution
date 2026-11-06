package com.codex.blacklight.infection;

import com.codex.blacklight.config.GameplayBalanceConfig;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;

/** Shared by every dimension. No pending entity queue or unloaded-chunk access. */
public final class InfectionConversionWork {
    private static final class Window {
        int tick=Integer.MIN_VALUE,attempts;
        final Map<EntityType<?>,Integer> reported=new IdentityHashMap<>();
    }
    private static final Map<MinecraftServer,Window> WINDOWS=new WeakHashMap<>();
    private InfectionConversionWork() {}
    public static boolean acquire(ServerLevel level){
        var server=level.getServer();if(!server.isSameThread())return false;
        var window=WINDOWS.computeIfAbsent(server,s->new Window());int now=server.getTickCount();
        if(window.tick!=now){window.tick=now;window.attempts=0;}
        if(window.attempts>=GameplayBalanceConfig.value(GameplayBalanceConfig.CONVERSION_PER_TICK))return false;
        window.attempts++;return true;
    }
    /** One diagnostic per entity TYPE per cooldown, not one full stack per creature. */
    public static boolean reportFailure(ServerLevel level,EntityType<?> type){
        var server=level.getServer();var window=WINDOWS.computeIfAbsent(server,s->new Window());int now=server.getTickCount();
        Integer old=window.reported.get(type);int interval=GameplayBalanceConfig.value(GameplayBalanceConfig.CONVERSION_RETRY_TICKS);
        if(old!=null&&now>=old&&(long)now-old<interval)return false;
        window.reported.put(type,now);return true;
    }
}
