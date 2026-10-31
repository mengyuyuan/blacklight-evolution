package com.codex.blacklight.city;

import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.military.vehicle.VehicleGarageData;
import com.codex.blacklight.registry.ModWorldgen;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** Independent attachment footprint. Worldgen never creates entities or edits SavedData. */
public final class VehicleGaragePiece extends StructurePiece {
    /** Pure map survey, called only while selecting a new structure start. */
    public static final class Planner {
        public static Optional<VehicleGarageData.Site> find(CityMilitaryPlan.Site base,CityMilitaryPlan.Checkpoint checkpoint,
                int chunkX,int chunkZ,boolean newStart,CityMilitaryPlan.Survey survey) {
            return find(base,checkpoint,chunkX,chunkZ,newStart,true,survey);
        }
        public static Optional<VehicleGarageData.Site> find(CityMilitaryPlan.Site base,CityMilitaryPlan.Checkpoint checkpoint,
                int chunkX,int chunkZ,boolean newStart,boolean urban,CityMilitaryPlan.Survey survey) {
            if(!newStart || base==null || survey==null)return Optional.empty();
            // Two independent side plots. The original base bounds and personnel
            // footprint stay unchanged, including when both attachments are refused.
            for(int localX:new int[]{69,-35}) {
                int localZ=base.templateDepth()-27;
                int[] a=base.world(localX,localZ),b=base.world(localX+30,localZ+26);
                VehicleGarageData.Site site;
                try { site=new VehicleGarageData.Site(Math.min(a[0],b[0]),base.floor(),Math.min(a[1],b[1]),base.rotation()); }
                catch(IllegalArgumentException invalid){continue;}
                int[] first=site.world(0,0),last=site.world(30,42);
                int minX=Math.min(first[0],last[0]),minZ=Math.min(first[1],last[1]);
                int maxX=Math.max(first[0],last[0]),maxZ=Math.max(first[1],last[1]);
                if(minX<(long)chunkX*16-96 || minZ<(long)chunkZ*16-96 || maxX>(long)chunkX*16+111 || maxZ>(long)chunkZ*16+111)continue;
                if(overlap(minX,minZ,maxX,maxZ,base.x(),base.z(),base.x()+base.width()-1,base.z()+base.depth()-1))continue;
                if(checkpoint!=null&&overlap(minX,minZ,maxX,maxZ,checkpoint.x(),checkpoint.z(),checkpoint.x()+16,checkpoint.z()+16))continue;
                if(urban?terrain(site,survey)&&roads(site,survey):fieldTerrain(site,survey))return Optional.of(site);
            }
            return Optional.empty();
        }
        public static String baseId(CityMilitaryPlan.Site site) { return site.x()+":"+site.floor()+":"+site.z()+":"+site.rotation()+":"+site.blackwatch(); }
        private static boolean overlap(int minX,int minZ,int maxX,int maxZ,int x,int z,int endX,int endZ) { return minX<=endX&&maxX>=x&&minZ<=endZ&&maxZ>=z; }
        private static boolean terrain(VehicleGarageData.Site site,CityMilitaryPlan.Survey survey) {
            // Read-only map columns, never ServerLevel.getChunk or a heightmap
            // lookup that could generate neighbour chunks from a worldgen thread.
            for(int x=0;x<31;x++)for(int z=0;z<43;z++) {
                int[] p=site.world(x,z);var surface=survey.at(p[0],p[1]);
                if(surface.water()||surface.blocked())return false;
                if(z<27) {
                    if(surface.road()||surface.ground()>site.floor()||site.floor()-surface.ground()>3)return false;
                } else if(surface.ground()!=site.floor())return false;
            }
            return true;
        }
        private static boolean fieldTerrain(VehicleGarageData.Site site,CityMilitaryPlan.Survey survey) {
            // A new field base has no pre-existing city road. Survey its own bounded
            // motor-pool apron; never retrofit old starts, build over water or demand
            // a perfectly flat 31-by-43 naturally generated patch.
            for(int x=0;x<31;x++)for(int z=0;z<43;z++) {
                int[] p=site.world(x,z);var surface=survey.at(p[0],p[1]);
                if(surface.water()||surface.blocked()||surface.road()
                        ||surface.ground()>site.floor()||site.floor()-surface.ground()>3)return false;
            }
            return true;
        }
        private static boolean roads(VehicleGarageData.Site site,CityMilitaryPlan.Survey survey) {
            // Each seven-wide doorway must reach an existing road within 16
            // blocks on the already surveyed flat exit space. No road is placed.
            for(int center:new int[]{5,15,25}) {
                boolean connected=false;
                for(int z=27;z<=42;z++) {
                    int[] p=site.world(center,z);
                    if(survey.at(p[0],p[1]).road()){connected=true;break;}
                }
                if(!connected)return false;
            }
            return true;
        }
        /** Pure geometry, independent of StructurePiece/native block registry initialization. */
        public static BoundingBox bounds(VehicleGarageData.Site site,boolean fieldApron) {
            Objects.requireNonNull(site);
            int[] a=fieldApron?site.world(0,0):new int[]{site.x(),site.z()};
            int[] b=fieldApron?site.world(30,42):new int[]{site.x()+site.width()-1,site.z()+site.depth()-1};
            return new BoundingBox(Math.min(a[0],b[0]),site.floor()-3,Math.min(a[1],b[1]),
                    Math.max(a[0],b[0]),site.floor()+9,Math.max(a[1],b[1]));
        }
        private Planner() { }
    }
    private final String baseId;
    private final MilitaryFaction faction;
    private final VehicleGarageData.Site site;
    private final boolean fieldApron;

    public VehicleGaragePiece(String baseId, MilitaryFaction faction, VehicleGarageData.Site site) {
        this(baseId,faction,site,false);
    }
    public VehicleGaragePiece(String baseId, MilitaryFaction faction, VehicleGarageData.Site site,boolean fieldApron) {
        super(ModWorldgen.VEHICLE_GARAGE_PIECE.get(), 0, bounds(site,fieldApron));
        this.fieldApron=fieldApron;
        if (baseId == null || baseId.isBlank() || baseId.length() > 256)
            throw new IllegalArgumentException("Invalid garage base reference");
        this.baseId = baseId;
        this.faction = Objects.requireNonNull(faction);
        this.site = Objects.requireNonNull(site);
        setOrientation(null);
    }

    public VehicleGaragePiece(CompoundTag tag) {
        super(ModWorldgen.VEHICLE_GARAGE_PIECE.get(), tag);
        if (tag.getInt("SchemaVersion") != 1)
            throw new IllegalArgumentException("Unsupported garage piece schema");
        baseId = tag.getString("BaseId");
        if (baseId.isBlank() || baseId.length() > 256)
            throw new IllegalArgumentException("Invalid garage base reference");
        faction = MilitaryFaction.parse(tag.getString("Faction"))
                .orElseThrow(() -> new IllegalArgumentException("Invalid garage faction"));
        site = new VehicleGarageData.Site(tag.getInt("SiteX"), tag.getInt("Floor"),
                tag.getInt("SiteZ"), tag.getInt("Turn"));
        fieldApron=tag.getBoolean("FieldApron");
        boundingBox = bounds(site,fieldApron);
        setOrientation(null);
    }

    public String baseId() { return baseId; }
    public MilitaryFaction faction() { return faction; }
    public VehicleGarageData.Site site() { return site; }

    public static BoundingBox bounds(VehicleGarageData.Site site) {
        return Planner.bounds(site,false);
    }

    public static BoundingBox bounds(VehicleGarageData.Site site,boolean fieldApron) {
        return Planner.bounds(site,fieldApron);
    }
    public static BlockPos origin(VehicleGarageData.Site site) {
        int[] point = site.world(0, 0);
        return new BlockPos(point[0], site.floor(), point[1]);
    }

    public static String templateName(MilitaryFaction faction) {
        return "vehicle_garage_" + Objects.requireNonNull(faction).id();
    }

    @Override protected void addAdditionalSaveData(
            StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("SchemaVersion", 1);
        tag.putString("BaseId", baseId);
        tag.putString("Faction", faction.id());
        tag.putInt("SiteX", site.x());
        tag.putInt("Floor", site.floor());
        tag.putInt("SiteZ", site.z());
        tag.putInt("Turn", site.rotation());
        if(fieldApron)tag.putBoolean("FieldApron",true);
    }

    @Override public void postProcess(WorldGenLevel level, StructureManager manager,
            ChunkGenerator generator, RandomSource random, BoundingBox clip,
            ChunkPos chunk, BlockPos pivot) {
        var template = level.getLevel().getStructureManager().getOrCreate(
                new ResourceLocation("blacklight", templateName(faction)));
        var at = origin(site);
        var settings = new StructurePlaceSettings()
                .setRotation(Rotation.values()[site.rotation()])
                .setBoundingBox(clip).setIgnoreEntities(true);
        template.placeInWorld(level, at, at, settings, random, 2);
        if(fieldApron)for(int localX=0;localX<31;localX++)for(int localZ=27;localZ<43;localZ++){
            int[] point=site.world(localX,localZ);
            for(int y=site.floor()-3;y<=site.floor()+7;y++){
                var pos=new BlockPos(point[0],y,point[1]);if(!clip.isInside(pos))continue;
                // Every part is clipped into this NEW structure's saved bounds.
                // Fill surveyed shallow gaps, preserve deeper terrain, leave a
                // seven-block-clear exit for the complete tank hull.
                if(y==site.floor())level.setBlock(pos,Blocks.GRAY_CONCRETE.defaultBlockState(),2);
                else if(y>site.floor())level.setBlock(pos,Blocks.AIR.defaultBlockState(),2);
                else if(level.getBlockState(pos).isAir())level.setBlock(pos,Blocks.STONE_BRICKS.defaultBlockState(),2);
            }
        }
        for (int x = Math.max(site.x(), clip.minX());
                x <= Math.min(site.x() + site.width() - 1, clip.maxX()); x++) {
            for (int z = Math.max(site.z(), clip.minZ());
                    z <= Math.min(site.z() + site.depth() - 1, clip.maxZ()); z++) {
                for (int y = site.floor() - 1; y >= site.floor() - 3; y--) {
                    var foundation = new BlockPos(x, y, z);
                    if (clip.isInside(foundation) && level.getBlockState(foundation).isAir())
                        level.setBlock(foundation, Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
    }
}
