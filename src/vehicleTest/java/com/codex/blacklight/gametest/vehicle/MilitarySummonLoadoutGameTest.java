package com.codex.blacklight.gametest.vehicle;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.FirearmsRegistry;
import com.codex.blacklight.military.MilitaryRole;
import com.codex.blacklight.military.MilitaryRank;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.*;
import net.minecraftforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitarySummonLoadoutGameTest {
    @GameTest(template="blackwatch_arena",batch="vehicle6_soldier_usability",timeoutTicks=100)
    public static void actualVanillaBareSummonFinalizesNewSoldierAndOfficerWithDeterministicFiniteDefaultGuns(GameTestHelper h){
        var level=h.getLevel();var position=h.absoluteVec(new Vec3(5.5,2,5.5));
        for(int x=3;x<=12;x++)for(int z=3;z<=8;z++){
            h.setBlock(x,1,z,net.minecraft.world.level.block.Blocks.STONE);
            for(int y=2;y<=6;y++)h.setBlock(x,y,z,net.minecraft.world.level.block.Blocks.AIR);
        }
        h.assertTrue(level.isPositionEntityTicking(net.minecraft.core.BlockPos.containing(position)),"native GameTest spawn position is genuinely entity-ticking");
        var source=level.getServer().createCommandSourceStack().withLevel(level).withPosition(position).withPermission(4);
        // No user NBT argument: vanilla must execute the ordinary command finalizeSpawn path.
        level.getServer().getCommands().performPrefixedCommand(source,"summon blacklight:military_soldier ~ ~ ~");
        level.getServer().getCommands().performPrefixedCommand(source,"summon blacklight:military_officer ~4 ~ ~");
        var soldiers=level.getEntitiesOfClass(MilitarySoldier.class,new AABB(position.add(-2,-1,-2),position.add(8,4,3)));
        h.assertTrue(soldiers.size()==2,"bare commands in ticking world "+source.getLevel().dimension().location()+" at "+source.getPosition()+" create exactly two indexed infantry; count="+soldiers.size()+" actual="+soldiers.stream().map(s->s.getUUID()+"@"+s.position()).toList());
        // Preserve the two actual command-created actors while observing ordinary server ticks.
        soldiers.forEach(s->s.setPersistenceRequired());
        h.runAfterDelay(2,()->{try{
            var trooper=soldiers.stream().filter(s->s.getType()==ModEntities.MILITARY_SOLDIER.get()).findFirst().orElseThrow();
            var officer=soldiers.stream().filter(s->s.getType()==ModEntities.MILITARY_OFFICER.get()).findFirst().orElseThrow();
            h.assertTrue(trooper.commandRole()==MilitaryRole.TROOPER&&trooper.rank()==MilitaryRank.PRIVATE,"bare trooper constructor identity remains TROOPER/PRIVATE");
            h.assertTrue(officer.commandRole()==MilitaryRole.OFFICER&&officer.rank()==MilitaryRank.LIEUTENANT,"bare officer constructor identity remains OFFICER/LIEUTENANT");
            h.assertTrue(trooper.isAlive()&&trooper.getMainHandItem().getItem()==FirearmsRegistry.weapon("military_rifle")&&trooper.magazineAmmo()==30&&trooper.reserveAmmo("rifle_round")==90,
                    "actual empty-own-NBT soldier keeps the deterministic M5 rifle30 plus typed rifle90 default; main="+trooper.getMainHandItem()+" magazine="+trooper.magazineAmmo()+" reserve="+trooper.reserveAmmo("rifle_round"));
            h.assertTrue(officer.isAlive()&&officer.commandRole()==MilitaryRole.OFFICER&&officer.rank()==MilitaryRank.LIEUTENANT&&officer.getMainHandItem().getItem()==FirearmsRegistry.weapon("military_carbine")&&officer.magazineAmmo()==24&&officer.reserveAmmo("rifle_round")==72,
                    "actual vanilla finalized officer keeps its constructor role and deterministic finite carbine24 plus typed rifle72");
            h.assertTrue(trooper.getOffhandItem().isEmpty()&&officer.getOffhandItem().isEmpty()&&trooper.reserveAmmo("rocket_round")==0&&officer.reserveAmmo("rocket_round")==0,
                    "ordinary bare summons never enter the native random RPG pool or create secondary resources");
        }finally{soldiers.forEach(s->s.discard());}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="vehicle6_soldier_usability",timeoutTicks=100)
    public static void actualPartialOldMilitaryTagsNeverQualifyForFreshSummonIssue(GameTestHelper h){
        try(var f=new MilitaryVehicleGameTest.Fixture(h,286000)){
            for(int variant=0;variant<5;variant++){
                var partial=new CompoundTag();
                if(variant==0)partial.putBoolean("MilitaryWeaponInitialized",false);
                else if(variant==1)partial.putInt("MilitaryAmmoReserve",90);
                else if(variant==2)partial.put("MilitaryLoadout",new CompoundTag());
                else if(variant==3)partial.putLong("BlackwatchHome",0);
                else partial.put("HandItems",new net.minecraft.nbt.ListTag());
                var old=ModEntities.MILITARY_SOLDIER.get().create(f.level);old.load(partial);old.setNoAi(true);f.entities.add(old);old.tick();
                h.assertTrue(old.getMainHandItem().isEmpty()&&old.getOffhandItem().isEmpty()&&old.reserveAmmo("rifle_round")==0&&old.reserveAmmo("rocket_round")==0&&!old.initializeNativeLoadout("partial-retry"),
                        "partial/corrupt existing military tag variant "+variant+" stays initialized and cannot compensate a missing gun or reinterpret old stock");
            }
            // A fresh vanilla HandItems payload owns exactly that gun; it is not permission to clone or refill it.
            var vanilla=new CompoundTag();var gun=new net.minecraft.world.item.ItemStack(FirearmsRegistry.weapon("military_carbine"));
            com.codex.blacklight.firearms.FirearmItem.store(gun,new com.codex.blacklight.firearms.AmmoState(7,0,12345));
            var hands=new net.minecraft.nbt.ListTag();hands.add(gun.save(new CompoundTag()));hands.add(new CompoundTag());vanilla.put("HandItems",hands);
            var carried=ModEntities.MILITARY_SOLDIER.get().create(f.level);carried.load(vanilla);carried.setNoAi(true);f.entities.add(carried);carried.tick();
            var actual=carried.getMainHandItem();var actualState=com.codex.blacklight.firearms.FirearmItem.state(actual,0);
            h.assertTrue(actual.getItem()==FirearmsRegistry.weapon("military_carbine")&&actual.getCount()==1&&actualState.magazine()==7&&actualState.readyAt()==12345&&carried.getOffhandItem().isEmpty()
                    &&carried.reserveAmmo("rifle_round")==0&&carried.reserveAmmo("rocket_round")==0&&!carried.initializeNativeLoadout("vanilla-item-retry"),
                    "fresh vanilla HandItems keeps exactly its actual finite gun and cooldown without default issue, reserve or duplication");
        }h.succeed();
    }
    private MilitarySummonLoadoutGameTest(){}
}
