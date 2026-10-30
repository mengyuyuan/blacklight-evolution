package com.codex.blacklight.city;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;

/** A short-lived arrival ticket; polling never synchronously creates a chunk. */
public final class HydraDestination {
    private static final TicketType<UUID> TICKET=TicketType.create("blacklight_hydra_arrival",UUID::compareTo,200);
    private ServerLevel target;
    private ChunkPos centre;
    private int height;
    private final UUID id=UUID.randomUUID();
    public boolean prepare(ServerLevel source) {
        var next=source.getServer().getLevel(CityTravel.WORLD);
        if(next==null)return false;
        return prepare(next,CityTravel.arrivalOrigin(next));
    }
    public boolean prepare(ServerLevel next,BlockPos position) {
        if(next==null){release();return false;}
        height=position.getY();
        var nextCentre=new ChunkPos(position);
        if(target!=next||!nextCentre.equals(centre)){release();target=next;centre=nextCentre;}
        // FULL terrain alone is insufficient: pending entity storage may save/reload
        // a newly created carrier and erase its transient grip/animation state.
        // Level 30 keeps the prepared 3x3 centre entity-ticking until handoff.
        target.getChunkSource().addRegionTicket(TICKET,centre,3,id);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(target.getChunkSource().getChunkNow(centre.x+x,centre.z+z)==null
                    ||!target.areEntitiesLoaded(new ChunkPos(centre.x+x,centre.z+z).toLong())
                    ||!target.isPositionEntityTicking(new BlockPos((centre.x+x)*16,position.getY(),(centre.z+z)*16)))return false;
        return true;
    }
    public void release() {
        if(target!=null)target.getChunkSource().removeRegionTicket(TICKET,centre,3,id);
        target=null;centre=null;
    }
    /** Diagnostic reads never request or generate another chunk. */
    public String diagnostics(){
        if(target==null||centre==null)return "released";
        int full=0,entities=0,ticking=0;
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++){
            var pos=new ChunkPos(centre.x+x,centre.z+z);
            if(target.getChunkSource().getChunkNow(pos.x,pos.z)!=null)full++;
            if(target.areEntitiesLoaded(pos.toLong()))entities++;
            if(target.isPositionEntityTicking(new BlockPos(pos.x*16,height,pos.z*16)))ticking++;
        }
        return "full="+full+"/9 entities="+entities+"/9 ticking="+ticking+"/9";
    }
}
