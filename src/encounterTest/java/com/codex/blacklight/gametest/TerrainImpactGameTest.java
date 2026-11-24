package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.TerrainImpactEffects;
import com.codex.blacklight.combat.ContactEffects;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class TerrainImpactGameTest {
    private static net.minecraft.world.entity.animal.Pig scene(GameTestHelper h) {
        for(int x=0;x<16;x++)for(int z=0;z<16;z++) {
            h.setBlock(x,1,z,x<8?Blocks.DIRT:Blocks.STONE);
            for(int y=2;y<8;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var actor=h.spawn(EntityType.PIG,new Vec3(7,2,8));actor.setNoAi(true);return actor;
    }
    private static BlockHitResult hit(Entity actor,Vec3 center) {
        return actor.level().clip(new ClipContext(center.add(0,.1,0),center.add(0,-.8,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
    }
    @GameTest(template="blackwatch_arena",batch="terrain_material",timeoutTicks=20)
    public static void groundUsesRealMaterialsAndDoesNotMineTerrain(GameTestHelper h) {
        var actor=scene(h);var contact=hit(actor,h.absoluteVec(new Vec3(8,2,8)));
        var samples=TerrainImpactEffects.surfaces(actor,contact.getLocation(),3.2F);
        h.assertTrue(samples.stream().anyMatch(s->actor.level().getBlockState(s.hit().getBlockPos()).is(Blocks.DIRT)),"dirt ring samples");
        h.assertTrue(samples.stream().anyMatch(s->actor.level().getBlockState(s.hit().getBlockPos()).is(Blocks.STONE)),"stone ring samples");
        h.assertTrue(TerrainImpactEffects.emit(actor,contact,3.2F,true),"supported impact emitted");
        h.assertTrue(h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,actor.getBoundingBox().inflate(6)).isEmpty(),"mob fragments wait for displayed contact; no premature server display");
        // Non-mob callers (including player heavy punches) keep their established server debris path.
        var proxy=h.spawn(EntityType.ARMOR_STAND,new Vec3(7,2,8));
        h.assertTrue(TerrainImpactEffects.emit(proxy,contact,3.2F,true),"legacy heavy punch terrain route");
        var debris=h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,actor.getBoundingBox().inflate(6));
        h.assertTrue(debris.size()>=4&&debris.size()<=8,"bounded real 3D fragments: "+debris.size());
        h.assertTrue(debris.stream().anyMatch(d->material(d).equals("minecraft:dirt"))&&debris.stream().anyMatch(d->material(d).equals("minecraft:stone")),"fragments retain sampled materials");
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new net.minecraft.core.BlockPos(x,1,z))).is(x<8?Blocks.DIRT:Blocks.STONE),"world blocks remain intact");
        debris.forEach(Entity::discard);proxy.discard();actor.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain_slam_ledges",timeoutTicks=20)
    public static void wideSlamKeepsDebrisOnSeparatedLedges(GameTestHelper h) {
        var actor = scene(h);
        h.setBlock(4, 5, 8, Blocks.SANDSTONE);
        var origin = hit(actor, h.absoluteVec(new Vec3(.5, 2, 8.5)));
        var ledge = hit(actor, h.absoluteVec(new Vec3(4.5, 6, 8.5)));
        var far = hit(actor, h.absoluteVec(new Vec3(15.5, 2, 8.5)));
        h.assertTrue(ledge.getLocation().y - origin.getLocation().y == 4, "raised ledge is four blocks higher");
        h.assertTrue(far.getLocation().distanceTo(origin.getLocation()) == 15, "far contact is fifteen blocks away");
        var samples = java.util.List.of(new TerrainImpactEffects.Surface(origin, 0, 1, 0),
                new TerrainImpactEffects.Surface(ledge, 0, 1, 2),
                new TerrainImpactEffects.Surface(far, 0, 1, 4));
        ContactEffects.ground(actor, origin, 4, true, samples);
        for (var sample : samples) {
            var at = sample.hit().getLocation();
            var debris = h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class, new AABB(at, at).inflate(1));
            h.assertTrue(debris.size() == (sample.hit() == origin ? 0 : 1), "debris at actual slam contact " + at);
            if (!debris.isEmpty()) {
                String expected = sample.hit() == ledge ? "minecraft:sandstone" : "minecraft:stone";
                h.assertTrue(expected.equals(material(debris.get(0))), "fragment uses the struck block");
            }
            debris.forEach(Entity::discard);
        }
        actor.discard();
        h.succeed();
    }

    private static String material(Display.BlockDisplay display) {
        return display.saveWithoutId(new net.minecraft.nbt.CompoundTag()).getCompound("block_state").getString("Name");
    }
    @GameTest(template="blackwatch_arena",batch="terrain_slab",timeoutTicks=20)
    public static void slabContactKeepsItsActualHalfHeight(GameTestHelper h) {
        var actor=scene(h);for(int x=3;x<13;x++)for(int z=3;z<13;z++)h.setBlock(x,1,z,Blocks.STONE_SLAB);
        var contact=hit(actor,h.absoluteVec(new Vec3(8,2,8)));
        double floor=h.absoluteVec(new Vec3(0,1.5,0)).y;
        var samples=TerrainImpactEffects.surfaces(actor,contact.getLocation(),3);
        h.assertTrue(samples.size()>20,"enough slab contacts");
        h.assertTrue(samples.stream().allMatch(s->Math.abs(s.hit().getLocation().y-floor)<.001),"not lifted to full cube height");
        actor.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain_wall",timeoutTicks=20)
    public static void wallStopsSurfaceFragmentsAndLooseItemImpulse(GameTestHelper h) {
        var actor=scene(h);for(int z=0;z<16;z++)for(int y=2;y<6;y++)h.setBlock(8,y,z,Blocks.STONE);
        var center=h.absoluteVec(new Vec3(6,2,8));var contact=hit(actor,center);
        var samples=TerrainImpactEffects.surfaces(actor,center,5);
        double wall=h.absoluteVec(new Vec3(8,2,8)).x;
        h.assertTrue(!samples.isEmpty()&&samples.stream().noneMatch(s->s.hit().getLocation().x>wall),"no dust or debris through wall");
        var near=new ItemEntity(h.getLevel(),center.x-1,center.y+.03,center.z,new ItemStack(Items.STICK,2));near.setDeltaMovement(Vec3.ZERO);
        var far=new ItemEntity(h.getLevel(),center.x+4,center.y+.03,center.z,new ItemStack(Items.STICK,3));far.setDeltaMovement(Vec3.ZERO);
        h.getLevel().addFreshEntity(near);h.getLevel().addFreshEntity(far);
        TerrainImpactEffects.emit(actor,contact,5,true);
        h.assertTrue(near.getDeltaMovement().x<0&&near.getDeltaMovement().y>0,"near loose drops move out and up");
        h.assertTrue(far.getDeltaMovement().equals(Vec3.ZERO),"wall blocks item impulse");
        h.assertTrue(near.getItem().getCount()==2&&far.getItem().getCount()==3,"no duplicated loot");
        h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,actor.getBoundingBox().inflate(8)).forEach(Entity::discard);
        near.discard();far.discard();actor.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="terrain_air",timeoutTicks=20)
    public static void airAndSideFacesCannotEmitGroundWave(GameTestHelper h) {
        var actor=scene(h);var air=h.absoluteVec(new Vec3(8,6,8));var block=net.minecraft.core.BlockPos.containing(air);
        h.assertTrue(!TerrainImpactEffects.emit(actor,new BlockHitResult(air,Direction.UP,block,false),3,true),"air support rejected");
        h.assertTrue(!TerrainImpactEffects.emit(actor,new BlockHitResult(air,Direction.NORTH,block,false),3,true),"wall face is not floor");
        h.assertTrue(h.getLevel().getEntitiesOfClass(Display.BlockDisplay.class,actor.getBoundingBox().inflate(8)).isEmpty(),"no false ground fragments");
        actor.discard();h.succeed();
    }
}
