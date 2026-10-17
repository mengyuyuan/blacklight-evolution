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
    private final UUID id=UUID.randomUUID();
    public boolean prepare(ServerLevel source) {
        var next=source.getServer().getLevel(CityTravel.CITY);
        if(next==null)return false;
        return prepare(next,CityTravel.arrivalOrigin(next));
    }
    public boolean prepare(ServerLevel next,BlockPos position) {
        if(next==null){release();return false;}
        var nextCentre=new ChunkPos(position);
        if(target!=next||!nextCentre.equals(centre)){release();target=next;centre=nextCentre;}
        target.getChunkSource().addRegionTicket(TICKET,centre,1,id);
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)
            if(target.getChunkSource().getChunkNow(centre.x+x,centre.z+z)==null)return false;
        return true;
    }
    public void release() {
        if(target!=null)target.getChunkSource().removeRegionTicket(TICKET,centre,1,id);
        target=null;centre=null;
    }
}
