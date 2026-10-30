package com.codex.blacklight.world;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModBlocks;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.ModWorldgen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** No block or entity write escapes the postProcess chunk. Spawn flags are serialized
 * with the piece and stable UUIDs additionally protect partially persisted generation. */
public final class ColossalHivePiece extends StructurePiece {
    private final BlockPos centre;
    private final long shapeSeed;
    private volatile long spawned;

    public ColossalHivePiece(BlockPos centre,long seed) {
        super(ModWorldgen.COLOSSAL_HIVE_PIECE.get(),0,new BoundingBox(
                centre.getX()-ColossalHiveLayout.RADIUS,centre.getY()+ColossalHiveLayout.MIN_Y,centre.getZ()-ColossalHiveLayout.RADIUS,
                centre.getX()+ColossalHiveLayout.RADIUS,centre.getY()+ColossalHiveLayout.MAX_Y,centre.getZ()+ColossalHiveLayout.RADIUS));
        this.centre=centre.immutable();shapeSeed=seed;setOrientation(null);
    }
    public ColossalHivePiece(CompoundTag tag) {
        super(ModWorldgen.COLOSSAL_HIVE_PIECE.get(),tag);
        centre=BlockPos.of(tag.getLong("Centre"));shapeSeed=tag.getLong("ShapeSeed");spawned=tag.getLong("Spawned");
    }
    @Override protected void addAdditionalSaveData(StructurePieceSerializationContext context,CompoundTag tag) {
        tag.putLong("Centre",centre.asLong());tag.putLong("ShapeSeed",shapeSeed);tag.putLong("Spawned",spawned);
    }
    @Override public synchronized void postProcess(WorldGenLevel level,StructureManager structures,ChunkGenerator generator,
            RandomSource random,BoundingBox clip,ChunkPos chunk,BlockPos pivot) {
        int minX=Math.max(boundingBox.minX(),clip.minX()),maxX=Math.min(boundingBox.maxX(),clip.maxX());
        int minZ=Math.max(boundingBox.minZ(),clip.minZ()),maxZ=Math.min(boundingBox.maxZ(),clip.maxZ());
        int minY=Math.max(clip.minY(),Math.max(boundingBox.minY(),level.getMinBuildHeight()+1));
        int maxY=Math.min(clip.maxY(),Math.min(boundingBox.maxY(),level.getMaxBuildHeight()-1));
        // Protect other generated structures, regardless of which structure is placed first.
        List<BoundingBox> protectedBoxes=new ArrayList<>();
        for(var start:structures.startsForStructure(chunk,s->!(s instanceof ColossalHiveStructure)))
            for(var piece:start.getPieces())if(piece.getBoundingBox().intersects(clip))protectedBoxes.add(piece.getBoundingBox());
        var at=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=minY;y<=maxY;y++) {
            var material=ColossalHiveLayout.materialAt(x-centre.getX(),y-centre.getY(),z-centre.getZ(),shapeSeed);
            if(material==ColossalHiveLayout.Material.KEEP)continue;
            at.set(x,y,z);
            if(protectedAt(at,protectedBoxes))continue;
            BlockState old=level.getBlockState(at);
            if(!old.getFluidState().isEmpty()||old.hasBlockEntity()||!replaceable(old))continue;
            BlockState placed=state(material);
            if(!old.equals(placed))level.setBlock(at,placed,2);
        }
        // Each location's own chunk is complete before its finite inhabitants are added.
        spawnResidents(level,clip,protectedBoxes);
    }
    /** Manual placement calls this only after every column in the entire hive is complete. */
    public synchronized void spawnResidents(WorldGenLevel level,BoundingBox clip,List<BoundingBox> protectedBoxes) {
        spawn(level,clip,protectedBoxes,0,centre.offset(0,1,0),true,false);
        spawn(level,clip,protectedBoxes,1,centre.offset(ColossalHiveLayout.CORE_X,ColossalHiveLayout.CORE_Y,ColossalHiveLayout.CORE_Z),false,true);
        for(int i=0;i<20;i++) {
            int[] p=ColossalHiveLayout.preyPosition(i);
            spawn(level,clip,protectedBoxes,i+2,centre.offset(p[0],p[1],p[2]),false,false);
        }
    }
    /** Null means KEEP. The command shares exactly the natural structure's voxel layout. */
    public BlockState plannedState(BlockPos at) {
        var material=ColossalHiveLayout.materialAt(at.getX()-centre.getX(),at.getY()-centre.getY(),at.getZ()-centre.getZ(),shapeSeed);
        return material==ColossalHiveLayout.Material.KEEP?null:state(material);
    }
    public static boolean canReplace(BlockState old) {
        return old.getFluidState().isEmpty()&&!old.hasBlockEntity()&&replaceable(old);
    }
    private void spawn(WorldGenLevel level,BoundingBox clip,List<BoundingBox> protectedBoxes,int slot,BlockPos at,boolean devourer,boolean core) {
        long bit=1L<<slot;
        if((spawned&bit)!=0||!clip.isInside(at))return;
        // A protected structure may deliberately omit this chamber: never spawn into it.
        if(!clip.isInside(at.below())||protectedAt(at,protectedBoxes)||!level.getBlockState(at.below()).isSolid()
                ||!level.getBlockState(at).isAir()) { spawned|=bit;return; }
        Mob inhabitant;
        if(core)inhabitant=ModEntities.BLACKLIGHT_HIVE.get().create(level.getLevel());
        else {
            EntityType<?> type=devourer?EntityType.IRON_GOLEM:slot<14?EntityType.ZOMBIE:slot<18?EntityType.COW:EntityType.PIG;
            Entity source=type.create(level.getLevel());
            if(!(source instanceof Mob original))return;
            BlacklightInfectedHost host=ModEntities.INFECTED_HOST.get().create(level.getLevel());
            if(host==null)return;
            host.inherit(original,false);inhabitant=host;
        }
        if(inhabitant==null)return;
        inhabitant.setUUID(UUID.nameUUIDFromBytes(("blacklight:colossal_hive:"+centre.asLong()+":"+shapeSeed+":"+slot).getBytes(StandardCharsets.UTF_8)));
        // The core is four blocks wide. Move edge-centred spawns inward within their
        // owning chunk, rather than querying an ungenerated neighbour for clearance.
        double radius=inhabitant.getBbWidth()*.5+.05;
        double spawnX=com.codex.blacklight.util.MathCompat.clamp(at.getX()+.5,clip.minX()+radius,clip.maxX()+1-radius);
        double spawnZ=com.codex.blacklight.util.MathCompat.clamp(at.getZ()+.5,clip.minZ()+radius,clip.maxZ()+1-radius);
        inhabitant.moveTo(spawnX,at.getY(),spawnZ,180,0);
        int clearance=devourer||core?4:(int)Math.ceil(inhabitant.getBbHeight());
        var body=inhabitant.getBoundingBox();
        var check=new BlockPos.MutableBlockPos();
        for(int x=(int)Math.floor(body.minX);x<Math.ceil(body.maxX);x++)
            for(int z=(int)Math.floor(body.minZ);z<Math.ceil(body.maxZ);z++) {
                check.set(x,at.getY()-1,z);
                if(!clip.isInside(check)||protectedAt(check,protectedBoxes)||!level.getBlockState(check).isSolid()) { spawned|=bit;return; }
                for(int y=0;y<clearance;y++) {
                    check.set(x,at.getY()+y,z);
                    if(!clip.isInside(check)||protectedAt(check,protectedBoxes)||!level.getBlockState(check).isAir()) { spawned|=bit;return; }
                }
            }
        inhabitant.setPersistenceRequired();inhabitant.restrictTo(centre.above(),core?48:16);
        inhabitant.getPersistentData().putLong("BlacklightHiveHome",centre.above().asLong());
        inhabitant.getPersistentData().putBoolean("BlacklightColossalHive",true);
        if(devourer) {
            inhabitant.getPersistentData().putBoolean("BlacklightDevourer",true);
            inhabitant.getPersistentData().putLong("BlacklightDevourHome",centre.above().asLong());
        }
        spawned|=bit;
        level.addFreshEntity(inhabitant);
    }
    private static boolean protectedAt(BlockPos pos,List<BoundingBox> boxes) {
        for(var box:boxes)if(box.isInside(pos))return true;
        return false;
    }
    private static boolean replaceable(BlockState state) {
        return state.isAir()||state.canBeReplaced()||state.is(BlockTags.BASE_STONE_OVERWORLD)
                ||state.is(BlockTags.DIRT)||state.is(BlockTags.SAND)||state.is(BlockTags.TERRACOTTA)
                ||state.is(BlockTags.LEAVES)||state.is(BlockTags.LOGS)||state.is(Blocks.GRAVEL)
                ||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.SNOW)
                ||state.is(ModBlocks.INFECTED_SOIL.get())||state.is(ModBlocks.INFECTED_STONE.get())
                ||state.is(ModBlocks.TISSUE_FIBER.get())||state.is(ModBlocks.HIVE_VEIN.get())
                ||state.is(ModBlocks.HIVE_MEMBRANE.get())||state.is(ModBlocks.HIVE_BONE.get())
                ||state.is(ModBlocks.NUTRIENT_NODE.get())||state.is(ModBlocks.HIVE_FLESH.get())
                ||state.is(ModBlocks.HIVE_CARAPACE.get())||state.is(ModBlocks.HIVE_SAC.get());
    }
    private static BlockState state(ColossalHiveLayout.Material material) {
        return switch(material) {
            case AIR,KEEP -> Blocks.AIR.defaultBlockState();
            case INFECTED_SOIL -> ModBlocks.INFECTED_SOIL.get().defaultBlockState();
            case INFECTED_STONE -> ModBlocks.INFECTED_STONE.get().defaultBlockState();
            case TISSUE_FIBER -> ModBlocks.TISSUE_FIBER.get().defaultBlockState();
            case HIVE_VEIN -> ModBlocks.HIVE_VEIN.get().defaultBlockState();
            case HIVE_MEMBRANE -> ModBlocks.HIVE_MEMBRANE.get().defaultBlockState();
            case HIVE_BONE -> ModBlocks.HIVE_BONE.get().defaultBlockState();
            case NUTRIENT_NODE -> ModBlocks.NUTRIENT_NODE.get().defaultBlockState();
            case HIVE_FLESH -> ModBlocks.HIVE_FLESH.get().defaultBlockState();
            case HIVE_CARAPACE -> ModBlocks.HIVE_CARAPACE.get().defaultBlockState();
            case HIVE_SAC -> ModBlocks.HIVE_SAC.get().defaultBlockState();
        };
    }
}
