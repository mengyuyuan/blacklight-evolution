package com.codex.blacklight.infection;

import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.world.TissueAttachmentBlock;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Paid, contact-only architectural assimilation. Original material is palette-saved for clearing. */
public final class BuildingAssimilation {
    public static final int COST=3,MAX_CELLS=8192,MAX_PALETTE=256;
    private BuildingAssimilation(){}
    private static BlockState tissue(int variant){return (variant==0?ModBlocks.HIVE_CARAPACE:ModBlocks.HIVE_SAC).get().defaultBlockState();}
    public static final class Ledger {
        public record Cell(int original,int tissue){}
        private final List<BlockState> palette=new ArrayList<>();
        private final Map<BlockState,Integer> indices=new HashMap<>();
        public final Map<Long,Cell> cells=new LinkedHashMap<>();
        public boolean empty(){return cells.isEmpty();}
        public boolean room(BlockState before){return cells.size()<MAX_CELLS&&(indices.containsKey(before)||palette.size()<MAX_PALETTE);}
        public void remember(BlockPos pos,BlockState before,int variant){int index=indices.computeIfAbsent(before,s->{int n=palette.size();palette.add(s);return n;});cells.put(pos.asLong(),new Cell(index,variant));}
        public BlockState original(Cell cell){return palette.get(cell.original);}
        public CompoundTag write(){
            var n=new CompoundTag();var states=new ListTag();for(var state:palette)states.add(NbtUtils.writeBlockState(state));n.put("Palette",states);
            long[] positions=new long[cells.size()];int[] materials=new int[cells.size()];int i=0;
            for(var e:cells.entrySet()){positions[i]=e.getKey();materials[i++]=e.getValue().original*2+e.getValue().tissue;}
            n.putLongArray("Positions",positions);n.putIntArray("Materials",materials);return n;
        }
        public static Ledger read(CompoundTag n,HolderLookup.Provider registry){
            var ledger=new Ledger();var source=n.getList("Palette",Tag.TAG_COMPOUND);
            for(int i=0;i<Math.min(MAX_PALETTE,source.size());i++){
                var state=NbtUtils.readBlockState(registry.lookupOrThrow(Registries.BLOCK),source.getCompound(i));ledger.palette.add(state);ledger.indices.putIfAbsent(state,i);
            }
            long[] positions=n.getLongArray("Positions");int[] materials=n.getIntArray("Materials");
            for(int i=0;i<Math.min(MAX_CELLS,Math.min(positions.length,materials.length));i++){
                int index=materials[i]/2;if(materials[i]<0||index>=ledger.palette.size()||ledger.palette.get(index).isAir()||ledger.palette.get(index).hasBlockEntity())continue;
                ledger.cells.putIfAbsent(positions[i],new Cell(index,materials[i]&1));
            }return ledger;
        }
    }
    private static boolean convertible(ServerLevel level,BlockPos pos,BlockState state){
        if(state.isAir()||state.hasBlockEntity()||!state.getFluidState().isEmpty()||state.getDestroySpeed(level,pos)<0||!state.isCollisionShapeFullBlock(level,pos))return false;
        var id=net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return !id.getNamespace().equals("blacklight")&&!state.is(Blocks.SPAWNER)&&!state.is(Blocks.TRIAL_SPAWNER)&&!state.is(Blocks.VAULT);
    }
    /** Exactly one contacted support is considered per shared work operation. */
    public static boolean step(ServerLevel level,BuildingInfectionData.Colony c){
        if(c.dead||c.assimilating||c.stage<1||c.surfaces.isEmpty()||!BuildingInfectionConfig.ASSIMILATE.get()||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)||!WorldOutbreakSystem.active(level))return false;
        var patch=BlockPos.of(c.surfaces.get(Math.floorMod(c.assimilationCursor++,c.surfaces.size())));
        if(!level.hasChunkAt(patch)||!level.isPositionEntityTicking(patch))return false;
        var skin=level.getBlockState(patch);if(!skin.is(ModBlocks.TISSUE_ATTACHMENT.get())||skin.getValue(TissueAttachmentBlock.AGE)<1)return false;
        var pos=patch.relative(skin.getValue(TissueAttachmentBlock.FACING));
        if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.isPositionEntityTicking(pos)||!BuildingInfectionSystem.allowedSite(level,pos)
                ||Math.abs(pos.getX()-c.origin.getX())>BuildingInfectionConfig.RADIUS.get()||Math.abs(pos.getZ()-c.origin.getZ())>BuildingInfectionConfig.RADIUS.get()||Math.abs(pos.getY()-c.origin.getY())>BuildingInfectionConfig.HEIGHT.get())return false;
        var before=level.getBlockState(pos);if(c.severed.contains(pos.asLong())||!convertible(level,pos,before)||!c.assimilation.room(before)||!c.supply.pay(COST))return false;
        int variant=TissueGrowth.sample(pos.asLong()^c.origin.asLong(),180)>.70?1:0;boolean committed=false;c.assimilating=true;
        try{
            if(!NaturalInfectionPlacement.place(level,pos,tissue(variant)))return false;
            c.assimilation.remember(pos,before,variant);committed=true;BuildingInfectionData.get(level).setDirty();return true;
        }catch(RuntimeException|Error failure){
            if(level.getBlockState(pos).equals(tissue(variant))){c.assimilation.remember(pos,before,variant);committed=true;BuildingInfectionData.get(level).setDirty();}
            throw failure;
        }finally{c.assimilating=false;if(!committed)c.supply.refund(COST);}
    }
    /** Clearing restores only recorded, unchanged tissue. Player edits and mined cells stay untouched. */
    public static int recover(ServerLevel level,BuildingInfectionData data,int budget){
        int used=0;if(budget<=0||data.colonies.isEmpty())return 0;
        int count=data.colonies.size();
        for(int attempts=0;attempts<count*budget&&used<budget;attempts++){
            var c=data.colonies.get(Math.floorMod(data.recoveryCursor++,count));if(!c.dead||c.assimilating||!c.hasRemains())continue;
            if(c.assimilation.empty()){used++;recoverStructureCell(level,c);data.setDirty();continue;}
            var entry=c.assimilation.cells.entrySet().iterator().next();var pos=BlockPos.of(entry.getKey());used++;
            if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.isPositionEntityTicking(pos)){c.assimilation.cells.remove(entry.getKey());c.assimilation.cells.put(entry.getKey(),entry.getValue());continue;}
            var current=level.getBlockState(pos);
            c.assimilating=true;
            try{if(!current.equals(tissue(entry.getValue().tissue))||NaturalInfectionPlacement.place(level,pos,c.assimilation.original(entry.getValue()))){c.assimilation.cells.remove(entry.getKey());data.setDirty();}
                else{c.assimilation.cells.remove(entry.getKey());c.assimilation.cells.put(entry.getKey(),entry.getValue());}}
            finally{c.assimilating=false;}
        }
        data.compactCleared();return used;
    }
    private static void recoverStructureCell(ServerLevel level,BuildingInfectionData.Colony c){
        var mass=c.mass==null?List.<BuildingBiomassField.Cell>of():c.mass.cells();int total=mass.size()+c.surfaces.size();if(total==0){c.mass=null;c.massBlocks=0;return;}
        int index=Math.floorMod(c.recoveryCell,total);c.recoveryCell=index+1;
        // Highest branches retreat first; attached skin follows the solid body.
        BlockPos pos;boolean volume=index<mass.size();
        if(volume){var cell=mass.get(mass.size()-1-index);pos=c.origin.offset(cell.x(),cell.y(),cell.z());}else pos=BlockPos.of(c.surfaces.get(index-mass.size()));
        if(!level.isInWorldBounds(pos)||!level.getWorldBorder().isWithinBounds(pos)||!level.hasChunkAt(pos)||!level.isPositionEntityTicking(pos))c.recoveryPending=true;
        else{
            var state=level.getBlockState(pos);boolean own=volume?state.is(ModBlocks.BUILDING_BIOMASS.get()):state.is(ModBlocks.TISSUE_ATTACHMENT.get());
            if(own){c.assimilating=true;try{if(!BuildingBiomassSystem.clearAfterDeath(level,c,pos))c.recoveryPending=true;}finally{c.assimilating=false;}}
        }
        if(index==total-1){c.recoveryCell=0;if(!c.recoveryPending){c.mass=null;c.massBlocks=0;c.surfaces.clear();}c.recoveryPending=false;}
    }
}
