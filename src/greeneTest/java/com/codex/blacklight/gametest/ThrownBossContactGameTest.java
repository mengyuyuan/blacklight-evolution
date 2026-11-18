package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.HydraContact;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.entity.ThrownBlock;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.EncounterEntities;
import com.codex.blacklight.registry.ModEntities;
import com.mojang.authlib.GameProfile;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

/** Real projectile ticks against rendered multipart surfaces. Geometry below
 * only chooses an exposed launch corridor; no fixture calls hurt or onHit. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ThrownBossContactGameTest {
    private static final float DAMAGE=8;
    private static final double HALF=.45;
    private record Touch(String bone,Vec3 center) {}
    private record Shot(Vec3 from,Vec3 to,Touch first) {}
    private record Surface(String bone,HydraContact.Box box) {}

    private static EncounterMob boss(GameTestHelper h,EncounterKind kind){
        for(int x=0;x<16;x++)for(int z=0;z<16;z++){
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<32;y++)h.setBlock(x,y,z,Blocks.AIR);
        }
        var boss=EncounterEntities.CREATURES.get(kind).get().create(h.getLevel());
        if(kind==EncounterKind.ELIZABETH_GREENE){
            // Existing guarded mother, not the independently tested fresh emergence.
            var state=boss.greeneBattle().save();state.putBoolean("OpeningPending",false);boss.greeneBattle().load(state);
        }
        boss.setNoAi(true);boss.setNoGravity(true);boss.setPersistenceRequired();
        boss.setPos(h.absoluteVec(new Vec3(8,2,8)));
        boss.setYRot(0);boss.yBodyRot=boss.yBodyRotO=0;
        h.assertTrue(h.getLevel().addFreshEntity(boss),"registered boss is added to the real level");
        boss.updateParts();return boss;
    }
    private static CompoundTag save(Entity entity){var tag=new CompoundTag();entity.saveWithoutId(tag);return tag;}
    private static float armor(CompoundTag tag,int slot){return tag.getFloat("EncounterArmor"+slot);}
    private static double volume(HydraContact.Box box){return box.bounds.getXsize()*box.bounds.getYsize()*box.bounds.getZsize();}

    /** Independent discrete setup check, deliberately not BlockThrowCollision.
     * Ambiguous simultaneous contacts cannot be used to prove one armor slot. */
    private static Touch first(List<HydraContact.Region> regions,Vec3 from,Vec3 to){
        var possible=new ArrayList<Surface>();
        for(var region:regions)for(var box:region.boxes())
            if(box.bounds.inflate(HALF).contains(from)||box.bounds.inflate(HALF).clip(from,to).isPresent())
                possible.add(new Surface(region.bone(),box));
        for(int i=0;i<=160;i++){
            var at=from.lerp(to,i/160d);var cargo=new AABB(at,at).inflate(HALF);String bone=null;
            for(var surface:possible)if(surface.box.touches(cargo)){
                if(i==0||bone!=null&&!bone.equals(surface.bone))return null;
                bone=surface.bone;
            }
            if(bone!=null)return new Touch(bone,at);
        }
        return null;
    }
    private static Shot shot(GameTestHelper h,EncounterMob boss,Predicate<String> desired){
        var regions=boss.hurtRegions(0);var nextRegions=boss.hurtRegions(1);var candidates=new ArrayList<Surface>();
        for(var region:regions)if(desired.test(region.bone()))for(var box:region.boxes())candidates.add(new Surface(region.bone(),box));
        candidates.sort(Comparator.comparingDouble((Surface s)->volume(s.box)).reversed());
        var directions=new ArrayList<Vec3>(List.of(new Vec3(0,1,0),new Vec3(0,0,1),new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(-1,0,0)));
        for(int x=-1;x<=1;x++)for(int y=0;y<=1;y++)for(int z=-1;z<=1;z++)
            if(x*x+y*y+z*z>1)directions.add(new Vec3(x,y,z).normalize());
        var allowed=new AABB(h.absoluteVec(new Vec3(.6,2.6,.6)),h.absoluteVec(new Vec3(15.4,30,15.4)));
        for(var surface:candidates)for(var direction:directions){
            Vec3 to=surface.box.center;
            // Start beyond this surface, with enough free travel to insert a wall.
            Vec3 from=surface.box.nearest(to.add(direction.scale(32))).add(direction.scale(3.5));
            if(!allowed.contains(from)||!allowed.contains(to))continue;
            var contact=first(regions,from,to);
            if(contact!=null&&desired.test(contact.bone)&&from.distanceTo(contact.center)>2.5){
                // The boss may advance its idle pose before the new projectile's
                // first tick. Do not aim at a seam that changes receiver then.
                var next=first(nextRegions,from,to);
                if(next!=null&&next.bone.equals(contact.bone))return new Shot(from,to,contact);
            }
        }
        h.fail("No unambiguous exposed cargo corridor for the requested real boss region");return null;
    }
    private static ThrownBlock launch(GameTestHelper h,Shot shot){
        var block=ModEntities.THROWN_BLOCK.get().create(h.getLevel());
        var tag=save(block);tag.putBoolean("Armed",true);tag.putBoolean("Recover",false);
        tag.putFloat("Damage",DAMAGE);tag.putDouble("Knockback",0);
        tag.put("Block",NbtUtils.writeBlockState(Blocks.STONE.defaultBlockState()));block.load(tag);
        var owner=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"ThrownBossQA"));
        owner.setPos(shot.from.add(0,-2,0));block.setOwner(owner);
        block.setPos(shot.from);block.setDeltaMovement(shot.to.subtract(shot.from));
        h.assertTrue(block.cargo().half().distanceToSqr(new Vec3(HALF,HALF,HALF))<1e-12,"fixture carries one actual stone block");
        h.assertTrue(h.getLevel().addFreshEntity(block),"armed projectile is added for ordinary server movement ticks");
        return block;
    }
    private static void finished(GameTestHelper h,EncounterMob boss,ThrownBlock block){
        h.assertTrue(block.isRemoved()||save(block).getBoolean("Recover"),"the flight really reached a collision and entered recovery");
        block.discard();boss.discard();
        for(var item:h.getLevel().getEntitiesOfClass(ItemEntity.class,new AABB(block.position(),block.position()).inflate(1.5)))
            if(item.getItem().is(Blocks.STONE.asItem()))item.discard();
        h.succeed();
    }
    private static void weak(GameTestHelper h,EncounterKind kind,int slot){
        var boss=boss(h,kind);
        h.runAfterDelay(5,()->{
            var shot=shot(h,boss,bone->bone.equals("weak_"+slot));
            var before=save(boss);float health=boss.getHealth();
            float expected=DAMAGE*(kind==EncounterKind.ELIZABETH_GREENE?CombatScale.units(boss):1);
            h.assertTrue(armor(before,slot)>expected,"one test block does not break the selected limb");
            var block=launch(h,shot);
            h.runAfterDelay(3,()->{
                var after=save(boss);
                for(int i=0;i<3;i++){
                    float change=armor(before,i)-armor(after,i);
                    h.assertTrue(Math.abs(change-(i==slot?expected:0))<.002F,
                            "real thrown cargo damages only weak_"+slot+"; armor "+i+" changed by "+change);
                }
                if(kind==EncounterKind.ELIZABETH_GREENE)
                    h.assertTrue(boss.getHealth()==health,"direct appendage contact does not become a core redirect");
                else h.assertTrue(boss.getHealth()<health,"Goliath weak armor contact also reaches its real health path");
                finished(h,boss,block);
            });
        });
    }

    @GameTest(template="blackwatch_arena",batch="throw_greene_weak0",timeoutTicks=30)
    public static void thrownStoneDamagesOnlyGreenesFirstAppendage(GameTestHelper h){weak(h,EncounterKind.ELIZABETH_GREENE,0);}
    @GameTest(template="blackwatch_arena",batch="throw_greene_weak1",timeoutTicks=30)
    public static void thrownStoneDamagesOnlyGreenesSecondAppendage(GameTestHelper h){weak(h,EncounterKind.ELIZABETH_GREENE,1);}
    @GameTest(template="blackwatch_arena",batch="throw_greene_weak2",timeoutTicks=30)
    public static void thrownStoneDamagesOnlyGreenesThirdAppendage(GameTestHelper h){weak(h,EncounterKind.ELIZABETH_GREENE,2);}
    @GameTest(template="blackwatch_arena",batch="throw_goliath_weak0",timeoutTicks=30)
    public static void thrownStoneDamagesGoliathsFirstWeakSurface(GameTestHelper h){weak(h,EncounterKind.GOLIATH,0);}
    @GameTest(template="blackwatch_arena",batch="throw_goliath_weak1",timeoutTicks=30)
    public static void thrownStoneDamagesGoliathsSecondWeakSurface(GameTestHelper h){weak(h,EncounterKind.GOLIATH,1);}

    @GameTest(template="blackwatch_arena",batch="throw_goliath_skin",timeoutTicks=30)
    public static void thrownStoneHitsGoliathsVisibleSkinWithoutInventingWeakArmorContact(GameTestHelper h){
        var boss=boss(h,EncounterKind.GOLIATH);
        h.runAfterDelay(5,()->{
            var shot=shot(h,boss,bone->!bone.startsWith("weak_"));var before=save(boss);float health=boss.getHealth();
            var block=launch(h,shot);
            h.runAfterDelay(3,()->{
                h.assertTrue(boss.getHealth()<health,"actual flight into the animated skin reduces Goliath health");
                var after=save(boss);for(int i=0;i<3;i++)h.assertTrue(armor(after,i)==armor(before,i),"skin contact does not redirect into weak armor "+i);
                finished(h,boss,block);
            });
        });
    }
    @GameTest(template="blackwatch_arena",batch="throw_greene_wall",timeoutTicks=30)
    public static void stoneHitsTheWallBeforeTheOtherwiseExposedGreeneAppendage(GameTestHelper h){
        var boss=boss(h,EncounterKind.ELIZABETH_GREENE);
        h.runAfterDelay(5,()->{
            var shot=shot(h,boss,bone->bone.equals("weak_2"));BlockPos wall=null;
            for(double t:new double[]{.4,.5,.6}){
                var pos=BlockPos.containing(shot.from.lerp(shot.first.center,t));
                var solid=new AABB(pos);var expanded=solid.inflate(HALF-.015);var hit=expanded.clip(shot.from,shot.to);
                if(!expanded.contains(shot.from)&&hit.isPresent()
                        &&shot.from.distanceTo(hit.get())+.2<shot.from.distanceTo(shot.first.center)){
                    wall=pos;break;
                }
            }
            h.assertTrue(wall!=null,"fixture inserts a real stone wall strictly before the selected skin contact");
            h.getLevel().setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());
            var before=save(boss);float health=boss.getHealth();var block=launch(h,shot);
            h.runAfterDelay(3,()->{
                h.assertTrue(boss.getHealth()==health,"wall-first collision cannot damage the guarded core");
                var after=save(boss);for(int i=0;i<3;i++)h.assertTrue(armor(after,i)==armor(before,i),"wall-first collision cannot damage appendage "+i);
                h.assertTrue(block.position().distanceTo(shot.from)+.1<shot.first.center.distanceTo(shot.from),"real cargo stops on the wall side of the skin");
                finished(h,boss,block);
            });
        });
    }
    private ThrownBossContactGameTest(){}
}
