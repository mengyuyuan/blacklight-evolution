package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.BuildingBiomassBlock;
import com.codex.blacklight.world.BuildingInfectionCoreBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** One cell per shared colony-work operation, using normal chunk/block synchronization. */
public final class BuildingBiomassSystem {
    private static final ThreadLocal<Boolean> UPDATING=ThreadLocal.withInitial(()->false);
    private BuildingBiomassSystem() {}
    public static void removed(ServerLevel level,BlockPos pos) {
        if(UPDATING.get())return;
        var data=BuildingInfectionData.get(level);
        for(var c:data.colonies)if(c.mass!=null&&Math.abs(c.origin.getX()-pos.getX())<=BuildingInfectionConfig.RADIUS.get()
                &&Math.abs(c.origin.getZ()-pos.getZ())<=BuildingInfectionConfig.RADIUS.get()&&Math.abs(c.origin.getY()-pos.getY())<=BuildingInfectionConfig.HEIGHT.get()) {
            if(c.severed.size()<BuildingBiomassPlan.CELL_LIMIT)c.severed.add(pos.asLong());
            c.massBlocks=Math.max(0,c.massBlocks-1);data.setDirty();
        }
    }
    public static void step(ServerLevel level,BuildingInfectionData.Colony c) {
        if(c.mass==null) {
            if(c.stage<1||c.activeTicks-c.massAttempt<100)return;
            c.massAttempt=c.activeTicks;
            var core=level.getBlockState(c.origin);if(!core.hasProperty(BuildingInfectionCoreBlock.FACING))return;
            c.mass=BuildingBiomassPlan.find(level,c.origin,core.getValue(BuildingInfectionCoreBlock.FACING));
            if(c.mass==null)return;
        }
        var cells=c.mass.cells();if(cells.isEmpty())return;
        if(c.activeTicks-c.massSupportCheck>=100){c.massDisabled=c.mass.disabled(level,c.massDisabled);c.massSupportCheck=c.activeTicks;}
        var cell=cells.get(Math.floorMod(c.massCursor++,cells.size()));var pos=c.origin.offset(cell.x(),cell.y(),cell.z());
        if(c.severed.contains(pos.asLong())||level.isOutsideBuildHeight(pos)||!level.getWorldBorder().isWithinBounds(pos)
                ||Math.abs(cell.x())>BuildingInfectionConfig.RADIUS.get()||Math.abs(cell.z())>BuildingInfectionConfig.RADIUS.get()
                ||Math.abs(cell.y())>BuildingInfectionConfig.HEIGHT.get()||!level.hasChunkAt(pos)||!level.isPositionEntityTicking(pos))return;
        var before=level.getBlockState(pos);boolean own=before.is(ModBlocks.BUILDING_BIOMASS.get());
        if(!own&&!before.isAir()&&!before.is(ModBlocks.TISSUE_ATTACHMENT.get()))return;
        int age=(int)Math.min(24000,12000L*c.activeTicks/(BuildingInfectionConfig.MATURITY.get()*20L));
        int mask=BuildingBiomassField.mask(c.mass.stems,cell.x(),cell.y(),cell.z(),age,c.massDisabled);
        if(mask==0){if(own)change(level,c,pos,before,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),false);return;}
        // Broad longitudinal muscle/skin bands, not independent per-block checkerboard noise.
        double phase=(cell.y()+c.origin.getY())*.10+Math.atan2(cell.x()+.5,cell.z()+.5)*2;
        int material=Math.sin(phase)>.72?1:Math.cos(phase*.7)>.93?2:0;
        var desired=ModBlocks.BUILDING_BIOMASS.get().defaultBlockState().setValue(BuildingBiomassBlock.CORNERS,mask).setValue(BuildingBiomassBlock.MATERIAL,material);
        if(!before.equals(desired))change(level,c,pos,before,desired,!own);
    }
    private static void change(ServerLevel level,BuildingInfectionData.Colony c,BlockPos pos,BlockState before,BlockState next,boolean placed) {
        var snapshot=BlockSnapshot.create(level.dimension(),level,pos);boolean updating=UPDATING.get();UPDATING.set(true);
        try {
            if(!level.setBlock(pos,next,3))return;
            if(placed) {
                // With a null natural owner NeoForge reports snapshot.getState() as the
                // placed block. Preserve the OLD snapshot for rollback, but expose the
                // actual new state to protection listeners.
                var event=new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),null) {
                    @Override public BlockState getPlacedBlock(){return next;}
                    @Override public BlockState getState(){return next;}
                };
                NeoForge.EVENT_BUS.post(event);
                if(event.isCanceled()) {
                    if(level.getBlockState(pos).equals(next))level.setBlock(pos,before,3);
                    if(c.severed.size()<BuildingBiomassPlan.CELL_LIMIT)c.severed.add(pos.asLong());
                    return;
                }
            }
            if(!level.getBlockState(pos).equals(next))return;
            if(placed)c.massBlocks++;else if(next.isAir())c.massBlocks=Math.max(0,c.massBlocks-1);
        }finally{if(updating)UPDATING.set(true);else UPDATING.remove();}
    }
}
