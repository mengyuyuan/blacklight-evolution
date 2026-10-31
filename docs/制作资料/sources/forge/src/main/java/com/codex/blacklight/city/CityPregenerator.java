package com.codex.blacklight.city;

import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** One non-ticking region ticket at a time. Never calls blocking getChunk/getChunkFuture on the server thread. */
public final class CityPregenerator {
    private static final TicketType<ChunkPos> TICKET=TicketType.create("blacklight_city_pregen",Comparator.comparingLong(ChunkPos::toLong));
    private static final Map<ServerLevel,Work> WORK=new WeakHashMap<>();
    private static final class Work {ChunkPos pending;int requested,next;}
    static void tick(ServerLevel level){
        if(!CitySystem.futian(level))return;
        var data=CityGenerationData.get(level);
        if(!data.initialized){data.initialized=true;data.running=CityConfig.WARMUP.get();data.setDirty();}
        var work=WORK.computeIfAbsent(level,l->new Work());
        if(!data.running){release(level,work);return;}
        int now=level.getServer().getTickCount();
        if(work.pending!=null){
            // getChunkNow is a lookup only. Ticket level 33 generates FULL without ticking entities.
            if(level.getChunkSource().getChunkNow(work.pending.x,work.pending.z)!=null){
                release(level,work);data.cursor++;data.setDirty();
                work.next=now+CityConfig.PREGEN_INTERVAL.get();
            }else if(now-work.requested>1200){
                data.failure="区块准备超时，可用 resume 重试";data.running=false;data.setDirty();release(level,work);
            }
            return;
        }
        var cells=CityGenerationPlan.cells(data.mode);
        if(data.cursor>=cells.size()){data.running=false;data.setDirty();return;}
        if(now<work.next||level.getServer().getAverageTickTime()>35.0F)return;
        var cell=cells.get(data.cursor);work.pending=new ChunkPos(cell.x(),cell.z());work.requested=now;
        level.getChunkSource().addRegionTicket(TICKET,work.pending,0,work.pending);
    }
    private static void release(ServerLevel level,Work work){
        if(work.pending!=null){level.getChunkSource().removeRegionTicket(TICKET,work.pending,0,work.pending);work.pending=null;}
    }
    static void unload(ServerLevel level){var work=WORK.remove(level);if(work!=null)release(level,work);}
    static void start(ServerLevel level,int mode){
        unload(level);var d=CityGenerationData.get(level);d.initialized=true;d.mode=mode;d.cursor=0;
        d.running=true;d.failure="";d.setDirty();
    }
    static void pause(ServerLevel level){
        var d=CityGenerationData.get(level);d.initialized=true;d.running=false;d.setDirty();unload(level);
    }
    static void resume(ServerLevel level){
        var d=CityGenerationData.get(level);d.initialized=true;d.running=d.cursor<CityGenerationPlan.cells(d.mode).size();
        d.failure="";d.setDirty();
    }
    static boolean hasTicket(ServerLevel level){var w=WORK.get(level);return w!=null&&w.pending!=null;}
    public static String status(ServerLevel level){
        var d=CityGenerationData.get(level);int total=CityGenerationPlan.cells(d.mode).size();
        return "城市预生成："+(d.mode==2?"完整福田":"出生点周边")+" "+d.cursor+"/"+total+"，"
            +(d.cursor>=total?"完成":d.running?"运行中（繁忙时自动等待）":"已暂停")+(d.failure.isEmpty()?"":"；"+d.failure);
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> command(){
        var root=Commands.literal("pregen").requires(s->s.hasPermission(2));
        root.then(Commands.literal("start")
            .then(Commands.literal("near").executes(c->execute(c.getSource(),"near")))
            .then(Commands.literal("full").executes(c->execute(c.getSource(),"full"))));
        for(String action:List.of("pause","resume","status"))
            root.then(Commands.literal(action).executes(c->execute(c.getSource(),action)));
        return root;
    }
    private static int execute(CommandSourceStack source,String action){
        var level=source.getServer().getLevel(CityTravel.CITY);
        if(level==null||!CitySystem.futian(level)){source.sendFailure(Component.literal("当前存档没有福田城市维度"));return 0;}
        switch(action){case "near"->start(level,1);case "full"->start(level,2);case "pause"->pause(level);case "resume"->resume(level);default->{}}
        source.sendSuccess(()->Component.literal(status(level)),false);return 1;
    }
    private CityPregenerator(){}
}
