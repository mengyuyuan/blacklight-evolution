package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.BlackwatchSoldier;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModItems;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Exercises the actual registered jigsaw elements, entities, containers and crafting data. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class BlackwatchFacilityGameTest {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("blacklight", path); }

    private static List<StructurePoolElement> elements(GameTestHelper h) {
        return h.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL)
                .get(id("blackwatch_outpost")).getShuffledTemplates(RandomSource.create(42));
    }

    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void registeredPoolIncludesBothFacilities(GameTestHelper h) {
        var manager = h.getLevel().getStructureManager();
        var elements = elements(h);
        h.assertTrue(elements.size() == 3, "pool contains three weighted entries");
        h.assertTrue(elements.stream().filter(e -> e.getSize(manager, Rotation.NONE).getX() == 25).count() == 2,
                "outpost weight remains two");
        h.assertTrue(elements.stream().filter(e -> e.getSize(manager, Rotation.NONE).getX() == 17).count() == 1,
                "checkpoint weight is one");
        var structure = h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(id("blackwatch_outpost"));
        h.assertTrue(structure != null && structure.biomes().size() == 5, "existing structure ID and five biome holders load");
        h.succeed();
    }

    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void scannerCanBeCraftedFromMilitaryCircuit(GameTestHelper h) {
        var input = CraftingInput.of(3, 3, List.of(ItemStack.EMPTY, new ItemStack(Items.IRON_INGOT), ItemStack.EMPTY,
                new ItemStack(ModItems.MILITARY_CIRCUIT.get()), new ItemStack(Items.GLASS), new ItemStack(ModItems.MILITARY_CIRCUIT.get()),
                ItemStack.EMPTY, new ItemStack(Items.REDSTONE), ItemStack.EMPTY));
        var recipe = h.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, h.getLevel());
        h.assertTrue(recipe.isPresent(), "actual crafting grid matches a loaded recipe");
        h.assertTrue(recipe.orElseThrow().value().assemble(input, h.getLevel().registryAccess()).is(ModItems.FIELD_SCANNER.get()),
                "recipe actually creates the field scanner");
        h.succeed();
    }

    private static void place(GameTestHelper h, int width, Rotation rotation) {
        place(h,width,rotation,false);
    }
    private static void place(GameTestHelper h, int width, Rotation rotation,boolean army) {
        var start=h.absolutePos(BlockPos.ZERO);var end=h.absolutePos(new BlockPos(31,0,31));
        for(int x=(start.getX()>>4)-1;x<=(end.getX()>>4)+1;x++)for(int z=(start.getZ()>>4)-1;z<=(end.getZ()>>4)+1;z++)h.getLevel().setChunkForced(x,z,true);
        h.startSequence().thenWaitUntil(()->{
            for(int x=start.getX()>>4;x<=end.getX()>>4;x++)for(int z=start.getZ()>>4;z<=end.getZ()>>4;z++)h.assertTrue(h.getLevel().isPositionEntityTicking(new BlockPos(x*16,start.getY(),z*16)),"fixture chunk entity sections must be visible before native garrison placement");
        }).thenExecute(()->placeReady(h,width,rotation,army));
    }
    private static void placeReady(GameTestHelper h, int width, Rotation rotation,boolean army) {
        var level = h.getLevel(); var manager = level.getStructureManager();
        var pool=army?level.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(id("military_outpost")):null;
        var entries=army&&pool!=null?pool.getShuffledTemplates(RandomSource.create(42)):elements(h);
        var element = entries.stream().filter(e -> e.getSize(manager, Rotation.NONE).getX() == width).findFirst().orElse(null);
        h.assertTrue(element != null, "requested facility exists in the registered pool");
        var zeroBox = element.getBoundingBox(manager, BlockPos.ZERO, rotation);
        var base = h.absolutePos(new BlockPos(3, 2, 3));
        var origin = base.offset(-zeroBox.minX(), -zeroBox.minY(), -zeroBox.minZ());
        var box = element.getBoundingBox(manager, origin, rotation);
        h.assertTrue(element.place(manager, level, level.structureManager(), level.getChunkSource().getGenerator(),
                origin, origin, rotation, box, RandomSource.create(71), LiquidSettings.APPLY_WATERLOGGING, false),
                "native jigsaw pool placement succeeds");
        var soldiers = level.getEntitiesOfClass(MilitarySoldier.class, AABB.of(box));
        int expected = army?(width==21?8:4):width == 25 ? 5 : 3;
        h.assertTrue(soldiers.size() == expected, "template actually creates " + expected + " garrison soldiers; actual="+soldiers.stream().map(s->s.getType()+"@"+s.position()).toList());
        h.assertTrue(soldiers.stream().filter(MilitarySoldier::heavy).count() == 1, "one heavy in the garrison");
        h.assertTrue(soldiers.stream().allMatch(s->s.faction()==(army?MilitaryFaction.MILITARY:MilitaryFaction.BLACKWATCH)),"native entity IDs have the correct faction");
        h.assertTrue(soldiers.stream().filter(s->s.rank()==MilitaryRank.SERGEANT).count()==1,"one template commander carries the sergeant rank");
        h.assertTrue(soldiers.stream().filter(s->s.rank()==MilitaryRank.CORPORAL&&s.heavy()).count()==1,"heavy has corporal identification");
        for (var soldier : soldiers) {
            h.assertTrue(soldier.isPersistenceRequired(), "garrison does not randomly despawn");
            h.assertTrue(level.getBlockState(soldier.blockPosition().below()).isSolid(), "garrison has a solid floor");
            h.assertTrue(level.noCollision(soldier, soldier.getBoundingBox()), "garrison has head and body clearance");
            soldier.setNoAi(true); // Freeze only after native placement; other tests exercise live tactical movement.
        }
        int tech = 0, supplies = 0, beds = 0, signs = 0;
        for (var pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.BedBlock) beds++;
            if (state.getBlock() instanceof net.minecraft.world.level.block.SignBlock) {
                signs++; h.assertTrue(state.canSurvive(level, pos), "information sign has real mounting support");
            }
            if (!(level.getBlockEntity(pos) instanceof ChestBlockEntity chest)) continue;
            h.assertTrue(!level.getBlockState(pos.above()).isRedstoneConductor(level, pos.above()), "chest lid is usable");
            var table = chest.getLootTable();
            h.assertTrue(table != null, "native chest retains its deferred loot table");
            var lootId = table.location();
            int circuitCount = 0, total = 0;
            for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                var stack = chest.getItem(slot); // Opens the real deferred loot table, not a JSON inspection.
                total += stack.getCount();
                if (stack.is(ModItems.MILITARY_CIRCUIT.get())) circuitCount += stack.getCount();
            }
            if (lootId.equals(id("chests/blackwatch_technology"))) {
                tech++; h.assertTrue(circuitCount >= 3 && circuitCount <= 6, "technology cache yields original circuit range");
            } else if (lootId.equals(id("chests/blackwatch_supplies"))) {
                supplies++; h.assertTrue(total > 0 && circuitCount == 0, "supply cache yields actual non-tech supplies");
            } else h.fail("unexpected military chest loot table " + lootId);
        }
        h.assertTrue(tech == (!army&&width == 25 ? 2 : 1) && supplies == 1, "expected technology and supply caches are present");
        h.assertTrue(signs >= 2, "facility has readable information signs");
        if (width == 25) h.assertTrue(beds >= 4, "barracks includes at least two usable beds");
        h.runAfterDelay(3, () -> {
            for (var soldier : soldiers) h.assertTrue(soldier.home().equals(soldier.blockPosition()), "garrison initializes its own home");
            var units=soldiers.stream().map(MilitarySoldier::unitId).collect(java.util.stream.Collectors.toSet());
            h.assertTrue(units.size()==1&&!units.contains(null),"every rotated facility deploys its garrison into one unique persistent leaf");
            var unit=MilitaryOrganizationSystem.organization(level).unit(units.iterator().next()).orElseThrow();
            h.assertTrue(unit.members().size()==expected&&unit.commander().equals(soldiers.stream().filter(s->s.rank()==MilitaryRank.SERGEANT).findFirst().orElseThrow().getUUID()),"actual SavedData appoints the facility commander without nearby barracks merging");
            h.succeed();
        });
    }

    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void outpostNorth(GameTestHelper h) { place(h, 25, Rotation.NONE); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void outpostEast(GameTestHelper h) { place(h, 25, Rotation.CLOCKWISE_90); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void outpostSouth(GameTestHelper h) { place(h, 25, Rotation.CLOCKWISE_180); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void outpostWest(GameTestHelper h) { place(h, 25, Rotation.COUNTERCLOCKWISE_90); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void checkpointNorth(GameTestHelper h) { place(h, 17, Rotation.NONE); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void checkpointEast(GameTestHelper h) { place(h, 17, Rotation.CLOCKWISE_90); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void checkpointSouth(GameTestHelper h) { place(h, 17, Rotation.CLOCKWISE_180); }
    @GameTest(template = "blackwatch_facility_arena", timeoutTicks = 300)
    public static void checkpointWest(GameTestHelper h) { place(h, 17, Rotation.COUNTERCLOCKWISE_90); }

    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300)
    public static void militaryGenerationHasIndependentSpacingAndOneWayExclusion(GameTestHelper h){
        var sets=h.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE_SET);var army=sets.get(id("military_outposts"));var watch=sets.get(id("blackwatch_outposts"));
        h.assertTrue(army!=null&&watch!=null,"both natural generation sets are registered");
        var placement=(net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement)army.placement();
        h.assertTrue(placement.spacing()==40&&placement.separation()==16,"army has its own40/16 distribution");
        var ops=net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE,h.getLevel().registryAccess());
        var armyJson=net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC.encodeStart(ops,placement).result().orElseThrow().getAsJsonObject();
        var exclusion=armyJson.getAsJsonObject("exclusion_zone");h.assertTrue(exclusion.get("chunk_count").getAsInt()==8&&exclusion.get("other_set").getAsString().equals("blacklight:blackwatch_outposts"),"actual registered army placement excludes Blackwatch candidates by8chunks");
        var watchJson=net.minecraft.world.level.levelgen.structure.placement.StructurePlacement.CODEC.encodeStart(ops,watch.placement()).result().orElseThrow().getAsJsonObject();
        h.assertTrue(!watchJson.has("exclusion_zone"),"Blackwatch never recursively excludes military");
        var pool=h.getLevel().registryAccess().registryOrThrow(Registries.TEMPLATE_POOL).get(id("military_outpost"));h.assertTrue(pool!=null&&pool.getShuffledTemplates(RandomSource.create()).size()==3,"army pool has two camps and one checkpoint weighted entry");h.succeed();
    }
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCampNorth(GameTestHelper h){place(h,21,Rotation.NONE,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCampEast(GameTestHelper h){place(h,21,Rotation.CLOCKWISE_90,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCampSouth(GameTestHelper h){place(h,21,Rotation.CLOCKWISE_180,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCampWest(GameTestHelper h){place(h,21,Rotation.COUNTERCLOCKWISE_90,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCheckpointNorth(GameTestHelper h){place(h,17,Rotation.NONE,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCheckpointEast(GameTestHelper h){place(h,17,Rotation.CLOCKWISE_90,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCheckpointSouth(GameTestHelper h){place(h,17,Rotation.CLOCKWISE_180,true);}
    @GameTest(template="blackwatch_facility_arena",timeoutTicks=300) public static void militaryCheckpointWest(GameTestHelper h){place(h,17,Rotation.COUNTERCLOCKWISE_90,true);}
}
