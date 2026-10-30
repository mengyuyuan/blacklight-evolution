package com.codex.blacklight.city;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryOfficersGameTest {
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=60)
    public static void officerDisguiseKeepsExplicitRoleAndUniformWithoutCopyingOwnership(GameTestHelper h){
        arena(h);var s=spawn(h,ModEntities.MILITARY_OFFICER.get(),5,5,true);s.setCommandRole(MilitaryRole.TROOPER);s.setRank(MilitaryRank.CAPTAIN);s.setEquipped(false);
        var identity=com.codex.blacklight.disguise.DisguiseIdentity.capture(s);var look=identity.appearance();
        h.assertTrue(identity.valid()&&look.getString("MilitaryRole").equals("trooper")&&!look.getBoolean("MilitaryEquipped")&&look.contains("MilitaryEquipped"),"captured officer disguise must preserve explicit role and uniform appearance");
        h.assertTrue(!look.contains("MilitaryUnit")&&!look.contains("UUID")&&!look.contains("MilitaryAmmoReserve")&&!look.contains("MilitaryStowedWeapon"),"appearance does not clone roster ownership or reserve/stowed inventory");
        var clone=ModEntities.MILITARY_OFFICER.get().create(h.getLevel());clone.readAdditionalSaveData(look);
        h.assertTrue(clone.commandRole()==MilitaryRole.TROOPER&&clone.rank()==MilitaryRank.CAPTAIN&&!clone.equipped(),"actual disguise clone does not infer a different command outfit from rank");s.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=60)
    public static void playerKillsKeepCircuitLootForAllSixCommandIdentities(GameTestHelper h){
        arena(h);var player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"officer-loot"));
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);player.setPos(h.absoluteVec(new Vec3(13,2,13)));
        EntityType<?>[] types={ModEntities.MILITARY_TEAM_LEADER.get(),ModEntities.MILITARY_OFFICER.get(),ModEntities.MILITARY_COMMANDER.get(),ModEntities.BLACKWATCH_TEAM_LEADER.get(),ModEntities.BLACKWATCH_OFFICER.get(),ModEntities.BLACKWATCH_COMMANDER.get()};
        for(int i=0;i<types.length;i++){
            var soldier=spawn(h,types[i],3+i%3*3,3+i/3*6,true);
            h.assertTrue(soldier.hurt(soldier.damageSources().playerAttack(player),1000)&&!soldier.isAlive(),"real player damage kills registered command identity");
        }
        h.runAfterDelay(2,()->{
            int circuits=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(h.absoluteVec(new Vec3(1,1,1)),h.absoluteVec(new Vec3(15,6,15)))).stream()
                    .filter(e->BuiltInRegistries.ITEM.getKey(e.getItem().getItem()).getPath().equals("military_circuit")).mapToInt(e->e.getItem().getCount()).sum();
            h.assertTrue(circuits==6,"six real player kills must retain exactly one military circuit per command identity; actual="+circuits);h.succeed();
        });
    }
    private static MilitarySoldier spawn(GameTestHelper h,EntityType<?> type,int x,int z,boolean noAi){
        var level=h.getLevel();var soldier=(MilitarySoldier)type.create(level);
        soldier.setPos(h.absoluteVec(new Vec3(x+.5,2,z+.5)));soldier.setPersistenceRequired();soldier.setNoAi(noAi);
        h.assertTrue(level.addFreshEntity(soldier),"real command personnel joins level");soldier.initializeWeapon();return soldier;
    }
    private static void arena(GameTestHelper h){for(int x=0;x<16;x++)for(int z=0;z<16;z++){h.setBlock(x,1,z,Blocks.STONE);for(int y=2;y<7;y++)h.setBlock(x,y,z,x==0||x==15||z==0||z==15?Blocks.BEDROCK:Blocks.AIR);}}
    private static int command(GameTestHelper h,int permission,String text){
        try{return h.getLevel().getServer().getCommands().getDispatcher().execute(text,h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(h.absoluteVec(new Vec3(5,2,5))).withPermission(permission).withSuppressedOutput());}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException failure){return 0;}
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=80)
    public static void sixOfficerIdentitiesHaveFiniteLoadoutsAndSpawnEggs(GameTestHelper h){
        arena(h);EntityType<?>[] types={ModEntities.MILITARY_TEAM_LEADER.get(),ModEntities.MILITARY_OFFICER.get(),ModEntities.MILITARY_COMMANDER.get(),ModEntities.BLACKWATCH_TEAM_LEADER.get(),ModEntities.BLACKWATCH_OFFICER.get(),ModEntities.BLACKWATCH_COMMANDER.get()};
        MilitaryRole[] roles={MilitaryRole.TEAM_LEADER,MilitaryRole.OFFICER,MilitaryRole.COMMANDER};
        for(int i=0;i<types.length;i++){
            var s=spawn(h,types[i],3+i%3*3,3+i/3*6,true);var role=roles[i%3];boolean officer=role!=MilitaryRole.TEAM_LEADER;
            h.assertTrue(s.commandRole()==role&&s.rank()==role.defaultRank(),"registered role has actual default synced rank");
            h.assertTrue(s.faction()==(i<3?MilitaryFaction.MILITARY:MilitaryFaction.BLACKWATCH),"new identities keep faction boundary");
            h.assertTrue(s.getMainHandItem().getItem() instanceof FirearmItem gun&&gun.definition().id().equals(officer?"military_carbine":"military_rifle"),"actual officer carbine or leader rifle exists");
            h.assertTrue(s.magazineAmmo()==(officer?24:30)&&s.reserveAmmo()==(officer?72:90),"role starts with finite, one-time loadout");
            h.assertTrue(s.profile().equals(MilitaryCombatProfile.of(s.faction(),false)),"rank/command title grants no hidden combat-stat boost");
            var id=BuiltInRegistries.ENTITY_TYPE.getKey(types[i]);var egg=BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("blacklight",id.getPath()+"_spawn_egg"));
            h.assertTrue(egg instanceof SpawnEggItem&&SpawnEggItem.byId(types[i])==egg,"real registered spawn egg resolves the new entity type");
            FirearmItem.store(s.getMainHandItem(),new AmmoState(3,0,123));s.setEquipped(false);
            var saved=s.saveWithoutId(new CompoundTag());var restored=(MilitarySoldier)types[i].create(h.getLevel());restored.load(saved);restored.initializeWeapon();
            h.assertTrue(restored.commandRole()==role&&restored.rank()==role.defaultRank()&&!restored.equipped()&&restored.magazineAmmo()==3&&restored.reserveAmmo()==(officer?72:90),"role, uniform, same gun and ammunition survive NBT");
            restored.setEquipped(true);h.assertTrue(FirearmItem.state(restored.getMainHandItem(),0).readyAt()==123,"hidden gun cooldown survives officer equipment round trip");s.discard();
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=60)
    public static void legacyOfficerRankMigratesWithoutRefillingStowedWeapon(GameTestHelper h){
        arena(h);var s=spawn(h,ModEntities.MILITARY_SOLDIER.get(),5,5,true);s.setRank(MilitaryRank.CAPTAIN);FirearmItem.store(s.getMainHandItem(),new AmmoState(2,0,500));s.setEquipped(false);
        var tag=s.saveWithoutId(new CompoundTag());tag.remove("MilitaryRole");tag.remove("MilitaryLoadout");tag.putInt("MilitaryAmmoReserve",5);
        var restored=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());restored.load(tag);restored.initializeWeapon();
        h.assertTrue(restored.commandRole()==MilitaryRole.OFFICER&&restored.rank()==MilitaryRank.CAPTAIN,"old saved rank migrates into a command appearance without losing rank");
        h.assertTrue(!restored.equipped()&&restored.magazineAmmo()==2&&restored.reserveAmmo()==5,"old hidden rifle never becomes a free full carbine");
        tag.putString("MilitaryRole","trooper");restored.load(tag);h.assertTrue(restored.commandRole()==MilitaryRole.TROOPER&&restored.rank()==MilitaryRank.CAPTAIN,"explicit role remains independent of rank");s.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=70)
    public static void officerRoleCommandPreservesAmmoManualAuthorityAndFaction(GameTestHelper h){
        arena(h);var s=spawn(h,ModEntities.MILITARY_SOLDIER.get(),4,4,true);var leader=spawn(h,ModEntities.MILITARY_TEAM_LEADER.get(),8,4,true);var foreign=spawn(h,ModEntities.BLACKWATCH_OFFICER.get(),11,11,true);
        var unit=UUID.randomUUID();var pos=s.position();h.assertTrue(MilitaryOrganizationSystem.transaction(h.getLevel(),org->org.create(unit,s.faction(),MilitaryUnitType.TACTICAL_SQUAD,"officers-"+unit,4,new MilitaryOrder.Point(pos.x,pos.y,pos.z))&&org.assign(unit,s.getUUID(),s.rank())&&org.assign(unit,leader.getUUID(),leader.rank())&&org.appoint(unit,leader.getUUID())),"explicit real manual roster exists");
        var saved=s.saveWithoutId(new CompoundTag());saved.getCompound("MilitaryLoadout").getCompound("Reserves").putInt(((FirearmItem)s.getMainHandItem().getItem()).definition().ammoId(),5);s.load(saved);FirearmItem.store(s.getMainHandItem(),new AmmoState(2,0,500));var weapon=s.getMainHandItem();
        h.assertTrue(s.reserveAmmo()==5,"role command fixture starts with five actual typed reserve rounds");
        h.assertTrue(command(h,0,"blacklight military role "+s.getUUID()+" commander")==0&&s.commandRole()==MilitaryRole.TROOPER,"role edits require operator permission");
        for(String role:new String[]{"commander","trooper","officer","commander"})h.assertTrue(command(h,2,"blacklight military role "+s.getUUID()+" "+role)>0,"actual Brigadier role command succeeds");
        h.assertTrue(s.commandRole()==MilitaryRole.COMMANDER&&s.rank().ordinal()>=MilitaryRank.MAJOR.ordinal(),"role conversion applies the command default floor");
        h.assertTrue(s.getMainHandItem()==weapon&&s.magazineAmmo()==2&&s.reserveAmmo()==5,"repeated identity edits never replace or refill the real weapon");
        h.assertTrue(MilitaryOrganizationSystem.organization(h.getLevel()).unit(unit).orElseThrow().commander().equals(leader.getUUID()),"manual command appointment is not stolen by a new title");
        h.assertTrue(command(h,2,"blacklight military unit assign "+unit+" "+foreign.getUUID())==0&&!MilitaryOrganizationSystem.organization(h.getLevel()).allMembers(unit).contains(foreign.getUUID()),"officer role cannot bypass faction assignment rules");
        s.discard();leader.discard();foreign.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=500)
    public static void militaryLateOfficerTakesAutomaticCommandAndHasSuccessor(GameTestHelper h){leadership(h,true);}
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=500)
    public static void blackwatchLateOfficerTakesAutomaticCommandAndHasSuccessor(GameTestHelper h){leadership(h,false);}
    private static void leadership(GameTestHelper h,boolean military){
        arena(h);var leader=spawn(h,military?ModEntities.MILITARY_TEAM_LEADER.get():ModEntities.BLACKWATCH_TEAM_LEADER.get(),4,5,false);var member=spawn(h,military?ModEntities.MILITARY_SOLDIER.get():ModEntities.BLACKWATCH_SOLDIER.get(),9,5,false);MilitarySoldier[] officer={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(leader.unitId()!=null&&leader.unitId().equals(member.unitId()),"actual automatic roster is formed before officer arrival"))
                .thenExecute(()->{h.assertTrue(MilitaryOrganizationSystem.data(h.getLevel()).automatic(leader.unitId()),"fixture is automatic, not a manual appointment");officer[0]=spawn(h,military?ModEntities.MILITARY_OFFICER.get():ModEntities.BLACKWATCH_OFFICER.get(),6,9,false);})
                .thenWaitUntil(()->h.assertTrue(leader.unitId().equals(officer[0].unitId())&&MilitaryOrganizationSystem.organization(h.getLevel()).unit(leader.unitId()).orElseThrow().commander().equals(officer[0].getUUID()),"late real officer actually receives automatic command"))
                .thenExecute(()->{
                    var unit=leader.unitId();officer[0].kill();
                    h.assertTrue(MilitaryOrganizationSystem.organization(h.getLevel()).unit(unit).orElseThrow().commander().equals(leader.getUUID()),"officer death returns actual command to the surviving ranked team leader");
                    h.assertTrue(!MilitaryOrganizationSystem.organization(h.getLevel()).allMembers(unit).contains(officer[0].getUUID()),"dead commander is removed, not respawned");leader.discard();member.discard();h.succeed();
                });
    }
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=450)
    public static void militaryBaseUsesExistingPostsForOfficersWithoutExtraSlots(GameTestHelper h){base(h,false,false,14800);}
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=450)
    public static void blackwatchBaseUsesExistingPostsForOfficersWithoutExtraSlots(GameTestHelper h){base(h,true,false,15200);}
    @GameTest(template="blackwatch_arena",batch="officers",timeoutTicks=450)
    public static void manualBaseAppointmentSurvivesLaterOfficerMuster(GameTestHelper h){base(h,false,true,15600);}
    private static void base(GameTestHelper h,boolean blackwatch,boolean manual,int x){
        var level=h.getLevel().getServer().getLevel(CityTravel.CITY);var site=new CityMilitaryPlan.Site(x,0,80,0,blackwatch);String id=CityMilitaryPlacement.place(level,site);var definition=LargeBaseBlueprint.get(blackwatch);
        var box=CityMilitaryPiece.bounds(site);for(int cx=(box.minX()>>4)-1;cx<=(box.maxX()>>4)+1;cx++)for(int cz=(box.minZ()>>4)-1;cz<=(box.maxZ()>>4)+1;cz++)level.setChunkForced(cx,cz,true);
        var guard=definition.guards().get(0);int[] point=site.world(guard.x(),guard.z());var at=new BlockPos(point[0],site.floor()+guard.y(),point[1]);var observers=List.of(new Vec3(site.x()+32,81,site.z()+site.depth()/2.));UUID[] explicit={null};
        h.startSequence().thenWaitUntil(()->h.assertTrue(level.isPositionEntityTicking(at),"base officer fixture must be ticking"))
                .thenExecute(()->{
                    CityMilitaryService.maintain(level,observers);var b=CityMilitaryData.get(level).bases.get(id);h.assertTrue(Arrays.stream(b.slots).filter(Objects::nonNull).count()==2,"first pass retains the original two-guard budget");
                    if(manual){var selected=(MilitarySoldier)level.getEntity(b.slots[1]);explicit[0]=selected.getUUID();try{int result=level.getServer().getCommands().getDispatcher().execute("blacklight military unit commander "+selected.unitId()+" "+selected.getUUID(),level.getServer().createCommandSourceStack().withLevel(level).withPermission(2).withSuppressedOutput());h.assertTrue(result>0,"explicit base appointment succeeds before later officers arrive");}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new IllegalStateException(e);}}
                }).thenExecuteFor(140,()->{if(level.getGameTime()%10==0)CityMilitaryService.maintain(level,observers);})
                .thenExecute(()->{
                    var b=CityMilitaryData.get(level).bases.get(id);h.assertTrue(Arrays.stream(b.slots).filter(Objects::nonNull).count()==b.slots.length&&b.slots.length==(blackwatch?20:16),"command personnel reuse the exact original finite base capacity");
                    var members=Arrays.stream(b.slots).map(uuid->(MilitarySoldier)level.getEntity(uuid)).toList();
                    var commander=members.stream().filter(s->s.commandRole()==MilitaryRole.COMMANDER).findFirst().orElseThrow();var officer=members.stream().filter(s->s.commandRole()==MilitaryRole.OFFICER).findFirst().orElseThrow();
                    h.assertTrue(members.get(0).commandRole()==MilitaryRole.TEAM_LEADER&&commander.faction()==(blackwatch?MilitaryFaction.BLACKWATCH:MilitaryFaction.MILITARY),"entry leader and HQ staff are real faction personnel");
                    h.assertTrue(commander.getPersistentData().contains(CityMilitaryService.POST_TAG)&&officer.getPersistentData().contains(CityMilitaryService.POST_TAG),"HQ officers use saved physical posts");
                    var unit=commander.unitId();var expected=manual?explicit[0]:commander.getUUID();h.assertTrue(MilitaryOrganizationSystem.organization(level).unit(unit).orElseThrow().commander().equals(expected),"base default command or explicit user appointment survives full muster");
                    var restored=MilitarySavedData.load(MilitaryOrganizationSystem.data(level).save(new CompoundTag(),level.registryAccess()),level.registryAccess());h.assertTrue(restored.organization().unit(unit).orElseThrow().commander().equals(expected),"command appointment survives actual SavedData serialization");
                    if(manual)h.assertTrue(restored.commanderManual(unit),"explicit base appointment marker survives serialization");h.succeed();
                });
    }
}
