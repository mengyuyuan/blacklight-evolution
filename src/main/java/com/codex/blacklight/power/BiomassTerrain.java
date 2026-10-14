package com.codex.blacklight.power;

import com.codex.blacklight.entity.BiomassHusk;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Bounded server crater and persistent tissue blocks joined by the husk renderer. */
public final class BiomassTerrain {
    private static final ThreadLocal<BlockEvent.BreakEvent> OWN_BREAK=new ThreadLocal<>();
    private static final ThreadLocal<BlockEvent.EntityPlaceEvent> OWN_PLACE=new ThreadLocal<>();
    private BiomassTerrain(){}
    public static boolean ownsPlaceEvent(BlockEvent.EntityPlaceEvent event){return OWN_PLACE.get()==event;}
    public static boolean ownsBreakEvent(BlockEvent.BreakEvent event){return OWN_BREAK.get()==event;}
    public static CompoundTag erupt(ServerPlayer owner,BiomassHusk husk){
        CompoundTag debris=new CompoundTag();debris.putBoolean("tissueWorld",true);var level=owner.serverLevel();
        if(level!=husk.level()||!owner.mayBuild()||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return debris;
        BlockPos center=husk.blockPosition().below();int broken=0,kept=0;
        // A shallow radial crater is resolved in this tick, with no unbounded job
        // or chunk loading. Containers, liquids and unbreakable blocks stay intact.
        for(int layer=0;layer<3;layer++)for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
            int radius=layer==0?5:layer==1?4:2;
            if(broken>=160||x*x+z*z>radius*radius)continue;
            BlockPos pos=center.offset(x,-layer,z);
            if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.mayInteract(owner,pos))continue;
            var state=level.getBlockState(pos);float hardness=state.getDestroySpeed(level,pos);
            if(state.isAir()||!state.getFluidState().isEmpty()||state.hasBlockEntity()||hardness<0||hardness>5)continue;
            var event=new BlockEvent.BreakEvent(level,pos,state,owner);var old=OWN_BREAK.get();OWN_BREAK.set(event);
            try{NeoForge.EVENT_BUS.post(event);}finally{if(old==null)OWN_BREAK.remove();else OWN_BREAK.set(old);}
            if(event.isCanceled()||!level.getBlockState(pos).equals(state))continue;
            if(!level.destroyBlock(pos,true,owner))continue;
            broken++;

        }
        // A small real tissue base supports the strands; it is not a flat rendered puddle.
        for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)if(x*x+z*z<=1)
            place(owner,center.offset(x,0,z),(x==0&&z==0?ModBlocks.HIVE_FLESH:ModBlocks.TISSUE_FIBER).get().defaultBlockState());
        place(owner,center.below(),ModBlocks.HIVE_FLESH.get().defaultBlockState());
        long seed=husk.getUUID().getMostSignificantBits()^husk.getUUID().getLeastSignificantBits();
        var random=net.minecraft.util.RandomSource.create(seed);
        int planned=6+random.nextInt(7);debris.putInt("planned",planned);debris.putLong("seed",seed);
        var used=new java.util.HashSet<BlockPos>();
        double rotation=random.nextDouble()*Math.PI*2;
        for(int arm=0;arm<planned;arm++){
            double angle=rotation+arm*Math.PI*2/planned+(random.nextDouble()-.5)*.3, radius=7+random.nextDouble()*4;
            var target=husk.position().add(Math.cos(angle)*radius,random.nextDouble()*5,Math.sin(angle)*radius);
            BlockPos pos=null;boolean existing=false;
            // Most strands latch onto actual nearby walls, terrain or placed blocks.
            // A few grow new tissue masses so the rupture still has its own biomass.
            if(arm%3!=0){
                pos=anchor(owner,husk.position().add(0,1,0),target,husk);
                if(pos==null)pos=anchor(owner,target.add(0,3,0),target.add(0,-8,0),husk);
                existing=pos!=null;
            }
            BlockState core;
            if(existing){core=level.getBlockState(pos);if(!used.add(pos))continue;}
            else{
                pos=BlockPos.containing(target);core=arm%2==0?ModBlocks.TISSUE_FIBER.get().defaultBlockState():ModBlocks.HIVE_FLESH.get().defaultBlockState();
                if(!used.add(pos)||!place(owner,pos,core))continue;
                place(owner,pos.above(),ModBlocks.TISSUE_FIBER.get().defaultBlockState());
                place(owner,pos.offset(arm%2==0?1:-1,0,0),ModBlocks.HIVE_VEIN.get().defaultBlockState());
                place(owner,pos.offset(0,0,arm%2==0?1:-1),ModBlocks.HIVE_FLESH.get().defaultBlockState());
            }
            debris.put("block"+kept,NbtUtils.writeBlockState(core));debris.putLong("pos"+kept,pos.asLong());debris.putBoolean("existing"+kept,existing);kept++;
        }
        debris.putInt("count",kept);return debris;
    }
    private static BlockPos anchor(ServerPlayer owner,net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,BiomassHusk husk){
        var level=owner.serverLevel();
        // Check the entire short ray before clipping: never force-load a neighbor chunk.
        for(int i=0;i<=16;i++)if(!level.hasChunkAt(BlockPos.containing(from.lerp(to,i/16D))))return null;
        var hit=level.clip(new net.minecraft.world.level.ClipContext(from,to,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,husk));
        if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)return null;
        var pos=hit.getBlockPos();var state=level.getBlockState(pos);
        if(pos.distSqr(husk.blockPosition())<9||!level.mayInteract(owner,pos)||state.isAir()||!state.getFluidState().isEmpty())return null;
        return pos;
    }
    private static boolean place(ServerPlayer owner,BlockPos pos,BlockState tissue){
        var level=owner.serverLevel();
        if(!level.isInWorldBounds(pos)||!level.hasChunkAt(pos)||!level.mayInteract(owner,pos))return false;
        var before=level.getBlockState(pos);
        if(!before.isAir()||!level.getEntities((net.minecraft.world.entity.Entity)null,new AABB(pos),e->e instanceof net.minecraft.world.entity.LivingEntity||e.isPickable()).isEmpty())return false;
        var snapshot=BlockSnapshot.create(level.dimension(),level,pos);
        if(!level.setBlock(pos,tissue,2))return false;
        var event=new BlockEvent.EntityPlaceEvent(snapshot,level.getBlockState(pos.below()),owner);
        var old=OWN_PLACE.get();OWN_PLACE.set(event);
        try{NeoForge.EVENT_BUS.post(event);}finally{if(old==null)OWN_PLACE.remove();else OWN_PLACE.set(old);}
        if(event.isCanceled()){
            if(level.getBlockState(pos).equals(tissue))level.setBlock(pos,before,3);
            return false;
        }
        if(!level.getBlockState(pos).equals(tissue))return false;
        level.blockUpdated(pos,tissue.getBlock());return true;
    }
}
