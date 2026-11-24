package com.codex.blacklight.world;

import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.item.GreeneTissueItem;
import com.codex.blacklight.registry.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

final class GreeneOutbreak {
    private GreeneOutbreak(){}
    static void arrive(ServerPlayer player){
        // The escape drops one tracking sample for the party. Every credited participant can enter the den.
        var level = player.serverLevel();
        var den = GreeneHiveCave.nearest(level.getSeed(), player.blockPosition());
        if (GreeneTissueItem.held(player) && !GreeneTissueItem.isTissue(player.getUseItem())) {
            var from=player.getEyePosition().add(player.getLookAngle().scale(.6)).add(0,-.24,0);
            var direction=GreeneHiveCave.guidance(den,from);
            for(int i=1;i<=7;i++){
                var p=from.add(direction.scale(i*.26));
                level.sendParticles(player,ParticleTypes.CRIMSON_SPORE,false,p.x,p.y,p.z,1,.025,.025,.025,0);
            }
        }
        if(player.distanceToSqr(Vec3.atCenterOf(den))>88*88)return;
        if(!prepareLoaded(level,den,player))return;
        if(level.getDifficulty()==Difficulty.PEACEFUL || player.getY()>den.getY()+36 || player.distanceToSqr(Vec3.atCenterOf(den))>46*46)return;
        var sites=BossSiteData.get(level);if(sites.occupied(den) || !level.isPositionEntityTicking(den))return;
        var boss=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(level);
        if(boss==null)return;
        int floor=GreeneHiveCave.column(den,den.getX(),den.getZ()).floor();
        boss.moveTo(den.getX()+.5,floor,den.getZ()+.5,0,0);boss.setPersistenceRequired();boss.startGreeneOutbreak();
        sites.reserve(den,boss,player);
        try{
            if(!level.addFreshEntity(boss)||boss.isRemoved())return;
        }finally{if(level.getEntity(boss.getUUID())!=boss || boss.isRemoved())sites.release(boss);}
        for(int i=0;i<6;i++){
            var anchor=GreeneHiveCave.sac(den,i);
            var support=GreeneHiveCave.support(i);var base=anchor.relative(support);
            if(level.hasChunkAt(anchor) && level.getBlockState(anchor).isAir() && level.getBlockState(base).isFaceSturdy(level,base,support.getOpposite()))
                level.setBlock(anchor,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.FACING,support).setValue(TissueAttachmentBlock.AGE,3),3);
        }
        if(!player.isCreative())boss.setTarget(player);
        for(var p:level.players())if(p.distanceToSqr(boss)<72*72)
            FieldNotes.record(p, Component.translatable("survival.blacklight.greene_den_awake"));
    }

    /** Retrofit only matching natural rock in already-generated chunks. A visited chunk is never rebuilt. */
    private static boolean prepareLoaded(ServerLevel level,BlockPos den,ServerPlayer player){
        int cx=den.getX()>>4,cz=den.getZ()>>4;
        for(int r=0;r<=4;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            var chunk=level.getChunkSource().getChunkNow(cx+dx,cz+dz);
            if(chunk==null)continue;
            var memory=BossSiteData.get(level);
            if(memory.preparedDenChunks.contains(chunk.getPos().toLong()))continue;
            boolean changed=false;
            var pos=new BlockPos.MutableBlockPos();
            int bx=chunk.getPos().getMinBlockX(),bz=chunk.getPos().getMinBlockZ();
            for(int x=bx;x<bx+16;x++)for(int z=bz;z<bz+16;z++){
                var col=GreeneHiveCave.column(den,x,z);
                if(col==null)continue;
                pos.set(x,(col.floor()+col.roof())/2,z);
                if(chunk.getBlockState(pos).isAir())continue;
                for(int y=col.floor()-1;y<=col.roof()+1;y++){
                    pos.set(x,y,z);var state=chunk.getBlockState(pos);
                    if(!(state.is(Blocks.STONE)||state.is(Blocks.DEEPSLATE)||state.is(Blocks.ANDESITE)||state.is(Blocks.DIORITE)||state.is(Blocks.GRANITE)||state.is(Blocks.TUFF)))continue;
                    var next=col.replace(y,state);if(next!=state){level.setBlock(pos,next,2);changed=true;}
                }
            }
            memory.preparedDenChunks.add(chunk.getPos().toLong());memory.setDirty();
            if(changed && r<=3)return false;
        }
        return true;
    }
}
