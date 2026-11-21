package com.codex.blacklight.world;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.registry.EncounterEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;

/** A separate emergence on the open pedestrian axis south of Futian Civic Centre. */
final class GreeneOutbreak {
    private static final BlockPos PLAZA=new BlockPos(668,64,830);
    private GreeneOutbreak(){}
    static void arrive(ServerPlayer player){
        var level=player.serverLevel();
        var sites=BossSiteData.get(level);
        if(level.getDifficulty()==Difficulty.PEACEFUL || sites.occupied(PLAZA))return;
        for(int dz:new int[]{0,14,-14,24,-24}) {
            var pad=PLAZA.offset(0,0,dz);
            if(!level.isPositionEntityTicking(pad) || !BossSiteBlock.clearPad(level,pad,EncounterKind.ELIZABETH_GREENE,null)
                    || !level.canSeeSky(pad.above(2)))continue;
            var boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
            if(boss==null)return;
            boss.moveTo(pad.getX()+.5,pad.getY(),pad.getZ()+.5,0,0);
            boss.setPersistenceRequired();boss.startGreeneOutbreak();
            sites.reserve(PLAZA,boss,player);
            try{
                if(!level.addFreshEntity(boss) || boss.isRemoved())return;
            }finally{
                if(level.getEntity(boss.getUUID())!=boss || boss.isRemoved())sites.release(boss);
            }
            if(!player.isCreative())boss.setTarget(player);
            for(var p:level.players())if(p.distanceToSqr(player)<64*64)
                p.displayClientMessage(Component.translatable("survival.blacklight.greene_outbreak_started"),false);
            return;
        }
    }
}
