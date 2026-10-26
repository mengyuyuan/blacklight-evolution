package com.codex.blacklight.infection;

import com.codex.blacklight.city.*;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Surface-connected building colonisation. Work, storage and brood all have independent bounds. */
public final class BuildingInfectionSystem {
    private BuildingInfectionSystem() {}

    static boolean allowedSite(ServerLevel level, BlockPos p) {
        if (!CityTravel.isCity(level.dimension())) return true;
        if (!CityConfig.SPREAD.get() || !CityData.get(level).spread) return false;
        if (WorldOutbreakSystem.sanctuary(level,p)) return false;
        // The authored hives already have their own seven-port lifecycle. Never create a second
        // invisible producer on the same building, even after that original hive was destroyed.
        for (int hive = 0; hive < 2; hive++)
            if (CitySystem.port(level, hive, 0).distSqr(p) < 96 * 96) return false;
        return true;
    }
    private static boolean ready(ServerLevel level, BlockPos p) {
        if (level.isOutsideBuildHeight(p) || !level.getWorldBorder().isWithinBounds(p) || !level.hasChunkAt(p)) return false;
        // Support tests and collision queries may touch a neighbour; guard the full footprint.
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1})
            if (!level.hasChunkAt(p.offset(x, 0, z))) return false;
        return true;
    }
    public static boolean canRegister(ServerLevel level, BlockPos pos) {
        var data = BuildingInfectionData.get(level);
        if (data.activeColonies() >= BuildingInfectionConfig.COLONIES.get()
                || data.clearedSites.size() >= BuildingInfectionData.MAX_CLEARED || !allowedSite(level, pos)) return false;
        for (var c : data.colonies)
            if (Math.abs(c.origin.getX() - pos.getX()) <= BuildingInfectionConfig.RADIUS.get() * 2
                    && Math.abs(c.origin.getZ() - pos.getZ()) <= BuildingInfectionConfig.RADIUS.get() * 2) return false;
        for (long site : data.clearedSites) {
            var cleared=BlockPos.of(site);
            if (Math.abs(cleared.getX()-pos.getX()) <= BuildingInfectionConfig.RADIUS.get()*2
                    && Math.abs(cleared.getZ()-pos.getZ()) <= BuildingInfectionConfig.RADIUS.get()*2) return false;
        }
        return true;
    }
    public static boolean register(ServerLevel level, BlockPos pos) {
        if (!canRegister(level,pos)) return false;
        var data=BuildingInfectionData.get(level);
        data.compactCleared();
        var colony = new BuildingInfectionData.Colony(pos);
        neighbours(colony, pos);
        data.colonies.add(colony); data.setDirty(); return true;
    }
    public static void destroy(ServerLevel level, BlockPos pos) {
        var data = BuildingInfectionData.get(level);
        for (var c : data.colonies) if (c.origin.equals(pos) && !c.dead) {
            c.dead = true; c.frontier.clear(); data.rememberCleared(c); data.setDirty();
            CityEcology.cleared(level,pos);
        }
    }
    public static void considerNaturalCore(ServerLevel level, BlockPos tissue, RandomSource random) {
        if (!BuildingInfectionConfig.ENABLED.get() || !BuildingInfectionConfig.NATURAL_SEEDS.get()
                || !WorldOutbreakSystem.active(level)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || random.nextInt(16) != 0 || !ready(level, tissue) || !allowedSite(level, tissue)) return;
        var state = level.getBlockState(tissue);
        if (!state.is(ModBlocks.TISSUE_ATTACHMENT.get())) return;
        Direction support=null;
        // A floor patch beside an ordinary building must be able to climb that building too;
        // using only the patch's existing support would require the wall to be infected already.
        for(var side:Direction.Plane.HORIZONTAL) {
            boolean wallFound=true;
            for(int y=0;y<=2;y++) {
                var wall=tissue.relative(side).above(y);
                if(!ready(level,wall)||!validSupport(level,wall,side.getOpposite())) { wallFound=false;break; }
            }
            if(wallFound) { support=side;break; }
        }
        if(support==null) return;
        if (!canRegister(level,tissue)) return;
        NaturalInfectionPlacement.place(level,tissue, ModBlocks.BUILDING_CORE.get().defaultBlockState()
                .setValue(BuildingInfectionCoreBlock.FACING, support.getOpposite()));
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level && level.getGameTime() % 5 == 0) advance(level);
    }
    /** One scheduled batch; public for dedicated-server acceptance without speeding up world time. */
    public static int advance(ServerLevel level) {
        if (!BuildingInfectionConfig.ENABLED.get() || !WorldOutbreakSystem.active(level)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return 0;
        var data = BuildingInfectionData.get(level);
        if (data.colonies.isEmpty()) return 0;
        for (var c : data.colonies) {
            if (c.dead || !ready(level, c.origin) || !level.isPositionEntityTicking(c.origin) || !allowedSite(level, c.origin)) continue;
            var core = level.getBlockState(c.origin);
            if (!core.is(ModBlocks.BUILDING_CORE.get())) { destroy(level,c.origin); continue; }
            c.activeTicks = (int)Math.min(Integer.MAX_VALUE, (long)c.activeTicks + 5);
            int maturity = BuildingInfectionConfig.MATURITY.get() * 20;
            int next = c.activeTicks >= maturity && c.surfaces.size() >= 256 ? 3
                    : c.activeTicks >= maturity / 2 && c.surfaces.size() >= 96 ? 2
                    : c.activeTicks >= maturity / 6 && c.surfaces.size() >= 24 ? 1 : 0;
            c.stage = Math.max(c.stage, next);
            if (core.getValue(TissueAttachmentBlock.AGE) != c.stage)
                level.setBlock(c.origin, core.setValue(TissueAttachmentBlock.AGE, c.stage), 3);
            if (c.stage == 3 && (c.broodTicks += 5) >= 600) {
                c.broodTicks = 0; hatch(level, c);
            }
            data.setDirty();
        }
        int used = 0, attempts = 0, budget = BuildingInfectionConfig.WORK.get();
        while (used < budget && attempts++ < budget * data.colonies.size()) {
            var c = data.colonies.get(Math.floorMod(data.cursor++, data.colonies.size()));
            if (c.dead || !ready(level,c.origin) || !level.isPositionEntityTicking(c.origin) || !allowedSite(level,c.origin)) continue;
            // Surface expansion, volume, surface aging, volume share this dimension's budget.
            if((c.workTurns&1)==1&&BuildingInfectionConfig.VOLUME.get())BuildingBiomassSystem.step(level,c);
            else if(c.workTurns==2||c.frontier.isEmpty()||c.surfaces.size()>=BuildingInfectionConfig.SURFACES.get())ageSurface(level,c);
            else growSurface(level,c);
            c.workTurns=(c.workTurns+1)%4;
            used++; data.setDirty();
        }
        return used;
    }
    private static boolean validSupport(ServerLevel level, BlockPos pos, Direction face) {
        var block = level.getBlockState(pos);
        return !block.hasBlockEntity() && block.getFluidState().isEmpty() && block.getDestroySpeed(level,pos) >= 0
                && block.isFaceSturdy(level, pos, face);
    }
    private static Direction support(ServerLevel level, BlockPos pos) {
        // Prefer walls over the floor, so growth climbs the facade instead of staying a carpet.
        for (var face : new Direction[]{Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST,Direction.DOWN,Direction.UP})
            if (validSupport(level, pos.relative(face), face.getOpposite())) return face;
        return null;
    }
    private static boolean inRange(BuildingInfectionData.Colony c, BlockPos pos) {
        return Math.abs(pos.getX()-c.origin.getX()) <= BuildingInfectionConfig.RADIUS.get()
                && Math.abs(pos.getZ()-c.origin.getZ()) <= BuildingInfectionConfig.RADIUS.get()
                && Math.abs(pos.getY()-c.origin.getY()) <= BuildingInfectionConfig.HEIGHT.get();
    }
    private static void neighbours(BuildingInfectionData.Colony c, BlockPos pos) {
        for (int x=-1;x<=1;x++) for (int y=-1;y<=1;y++) for (int z=-1;z<=1;z++) {
            int distance=Math.abs(x)+Math.abs(y)+Math.abs(z);
            if (distance==0 || distance>2) continue; // surface edge/corner turns, never a detached jump
            var next=pos.offset(x,y,z);
            if (inRange(c,next)) c.offer(next);
        }
    }
    private static void growSurface(ServerLevel level, BuildingInfectionData.Colony c) {
        if (c.frontier.isEmpty()) return;
        long key = c.frontier.removeFirst(); var pos=BlockPos.of(key);
        if (!ready(level,pos) || !level.isPositionEntityTicking(pos)) { c.frontier.addLast(key); return; }
        if (!inRange(c,pos) || !allowedSite(level,pos)) return;
        var previous=level.getBlockState(pos);
        if (!previous.isAir() && !previous.is(ModBlocks.TISSUE_ATTACHMENT.get())) return;
        var direction=support(level,pos); if(direction==null) return;
        int u=direction.getAxis()==Direction.Axis.X?pos.getZ()-c.origin.getZ():pos.getX()-c.origin.getX();
        int v=direction.getAxis()==Direction.Axis.Y?pos.getZ()-c.origin.getZ():pos.getY()-c.origin.getY();
        var pattern=BuildingTissuePattern.at(c.origin.asLong() ^ level.getSeed(),u,v,direction.getAxis()!=Direction.Axis.Y);
        neighbours(c,pos); // Traverse the bounded facade, leaving designed gaps in its coverage.
        if(!pattern.covered()&&previous.isAir()) return;
        var tissue=ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState()
                .setValue(TissueAttachmentBlock.FACING,direction)
                .setValue(TissueAttachmentBlock.AGE,0)
                .setValue(TissueAttachmentBlock.VARIANT,pattern.variant());
        // Do not erase an older patch when the frontier encounters it.
        if(previous.isAir() && !NaturalInfectionPlacement.place(level,pos,tissue)) return;
        c.surfaces.add(key);
    }
    private static void ageSurface(ServerLevel level, BuildingInfectionData.Colony c) {
        if(c.surfaces.isEmpty()) return;
        var pos=BlockPos.of(c.surfaces.get(Math.floorMod(c.ageCursor++,c.surfaces.size())));
        if(!ready(level,pos)||!level.isPositionEntityTicking(pos)) return;
        var state=level.getBlockState(pos);
        if(state.is(ModBlocks.TISSUE_ATTACHMENT.get())&&state.getValue(TissueAttachmentBlock.AGE)<c.stage)
            level.setBlock(pos,state.setValue(TissueAttachmentBlock.AGE,c.stage),3);
    }
    public static boolean hatch(ServerLevel level, BuildingInfectionData.Colony c) {
        if(c.dead || c.stage<3 || !WorldOutbreakSystem.active(level)
                || !BuildingInfectionConfig.ENABLED.get() || !BuildingInfectionConfig.SPAWNS.get()
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                || level.getDifficulty()==Difficulty.PEACEFUL || !ready(level,c.origin)
                || !level.isPositionEntityTicking(c.origin) || !allowedSite(level,c.origin)
                || !level.getBlockState(c.origin).is(ModBlocks.BUILDING_CORE.get())) return false;
        if(CityTravel.isCity(level.dimension())&&(!CityConfig.SPAWNS.get()||!CityData.get(level).population)) return false;
        if(level.getEntitiesOfClass(Mob.class,new AABB(c.origin).inflate(48),Entity::isAlive).size()>=BuildingInfectionConfig.MOB_LIMIT.get()) return false;
        if(!WorldOutbreakSystem.populationRoom(level,c.origin)) return false;
        var direction=level.getBlockState(c.origin).getValue(BuildingInfectionCoreBlock.FACING);
        // A mature root can occupy the old straight-ahead outlet. Search flanking
        // ground as well, so a successful volumetric colony cannot seal its own brood.
        for(int lateral:new int[]{0,-3,3,-5,5,-7,7})for(int distance=2;distance<=8;distance++) for(int down=0;down<=3;down++) {
            var p=c.origin.relative(direction,distance).relative(direction.getClockWise(),lateral).below(down);
            if(!ready(level,p)||!level.isPositionEntityTicking(p)||!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                    || !level.getBlockState(p).getFluidState().isEmpty()) continue;
            int stage=CityEcology.stage(level,c.origin);
            var tier=c.births%4==0&&stage>=2?OutbreakRules.Tier.HUNTER
                    :OutbreakRules.choose(Math.max(1,stage),level.random.nextInt(100));
            if(WorldOutbreakSystem.spawnAt(level,p,tier)!=null) {
                c.births++; BuildingInfectionData.get(level).setDirty();
                level.playSound(null,c.origin,net.minecraft.sounds.SoundEvents.SLIME_SQUISH,net.minecraft.sounds.SoundSource.HOSTILE,1,.6f);
                return true;
            }
        }
        return false;
    }
}
