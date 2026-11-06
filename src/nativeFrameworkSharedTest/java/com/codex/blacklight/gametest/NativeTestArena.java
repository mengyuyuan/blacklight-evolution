package com.codex.blacklight.gametest;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/** QA-only entity-ticking lease for geometry expanded beyond the small template.
 * Unique region tickets cannot be undone by another fixture's forced-chunk cleanup. */
final class NativeTestArena implements AutoCloseable {
    private static final TicketType<Long> TYPE=TicketType.create("blacklight_native_qa",Long::compareTo);
    private static final AtomicLong IDS=new AtomicLong();
    private final long id=IDS.incrementAndGet();
    private final ServerLevel level;
    private final List<ChunkPos> chunks=new ArrayList<>();
    private boolean closed;
    private NativeTestArena(ServerLevel level){this.level=level;}
    static NativeTestArena open(GameTestHelper h,int min,int max){
        var arena=new NativeTestArena(h.getLevel());
        var start=h.absolutePos(new BlockPos(min,2,min));var end=h.absolutePos(new BlockPos(max,2,max));
        try{
            for(int x=(start.getX()>>4)-1;x<=(end.getX()>>4)+1;x++)for(int z=(start.getZ()>>4)-1;z<=(end.getZ()>>4)+1;z++){
                var chunk=new ChunkPos(x,z);arena.chunks.add(chunk);
                arena.level.getChunkSource().addRegionTicket(TYPE,chunk,3,arena.id);arena.level.getChunk(x,z);
            }
            return arena;
        }catch(Throwable failure){arena.close();throw failure;}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        for(var chunk:chunks)level.getChunkSource().removeRegionTicket(TYPE,chunk,3,id);
    }
}
