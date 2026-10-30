package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.combat.ImpactControl;
import com.codex.blacklight.combat.ImpactProfile;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real saved data, live entities and Brigadier commands. A separate batch isolates spawn work. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryOrganizationGameTest {
    private static void arena(GameTestHelper h) {
        for (int x=0;x<32;x++) for(int z=0;z<32;z++) {
            h.setBlock(x,1,z,Blocks.STONE);
            for(int y=2;y<7;y++)h.setBlock(x,y,z,x==0||z==0||x==31||z==31?Blocks.BEDROCK:Blocks.AIR);
        }
    }
    private static List<MilitarySoldier> troops(GameTestHelper h,int count,boolean army) {
        var result=new ArrayList<MilitarySoldier>();
        int columns=count<=12?4:10;
        for(int i=0;i<count;i++){
            MilitarySoldier soldier=army?ModEntities.MILITARY_SOLDIER.get().create(h.getLevel()):ModEntities.BLACKWATCH_SOLDIER.get().create(h.getLevel());
            soldier.setPersistenceRequired();soldier.setPos(h.absoluteVec(new Vec3(3.5+(i%columns)*2,2,3.5+(i/columns)*2)));
            soldier.setRank(i==0?MilitaryRank.SERGEANT:MilitaryRank.PRIVATE);
            h.assertTrue(h.getLevel().addFreshEntity(soldier),"registered troop enters the real level");result.add(soldier);
        }
        return result;
    }
    private static MilitaryOrganization org(GameTestHelper h){return MilitaryOrganizationSystem.organization(h.getLevel());}
    private static String name(){return "qa-"+UUID.randomUUID().toString().substring(0,12);}
    private static int command(GameTestHelper h,int permission,String command){
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel())
                .withPosition(h.absoluteVec(new Vec3(3.5,2,3.5))).withPermission(permission).withSuppressedOutput();
        try{return h.getLevel().getServer().getCommands().getDispatcher().execute(command,source);}
        catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}
    }
    private static String selector(GameTestHelper h){var p=h.absolutePos(new BlockPos(0,2,0));return "@e[type=blacklight:military_soldier,x="+p.getX()+",y="+p.getY()+",z="+p.getZ()+",dx=31,dy=5,dz=31]";}

    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=150)
    public static void twelveTroopsAutomaticallyCreateAnEightAndAFourRoster(GameTestHelper h){
        arena(h);var troops=troops(h,12,true);
        h.runAtTickTime(80,()->{
            var units=new HashSet<UUID>();for(var soldier:troops){h.assertTrue(soldier.unitId()!=null,"troop has persistent leaf identity");units.add(soldier.unitId());}
            h.assertTrue(units.size()==2,"automatic leaf unit is eight capacity, not four");
            var sizes=units.stream().map(id->org(h).allMembers(id).size()).sorted().toList();
            h.assertTrue(sizes.equals(List.of(4,8)),"twelve actual troops form understrength four and full eight");
            for(var soldier:troops)h.assertTrue(org(h).leafOf(soldier.getUUID()).orElseThrow().equals(soldier.unitId()),"entity and saved roster agree");
            h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=40)
    public static void savedDataRoundTripsHierarchyRanksAppointmentAndInheritedOrder(GameTestHelper h){
        var data=new MilitarySavedData();var org=data.organization();UUID parent=UUID.randomUUID(),leaf=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        var point=new MilitaryOrder.Point(20,64,30);
        h.assertTrue(org.create(parent,MilitaryFaction.MILITARY,MilitaryUnitType.PLATOON,"parent",24,point),"create parent");
        h.assertTrue(org.create(leaf,MilitaryFaction.MILITARY,MilitaryUnitType.SECTION,"leaf",12,point),"create leaf");
        org.assign(leaf,a,MilitaryRank.SERGEANT);org.assign(leaf,b,MilitaryRank.CAPTAIN);org.attach(parent,leaf);org.appoint(parent,b);
        var order=new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(40,64,50));org.order(parent,order);
        h.assertTrue(data.isDirty(),"pure organization mutation marks real SavedData dirty");
        var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());
        var restored=MilitarySavedData.load(tag,h.getLevel().registryAccess()).organization();
        h.assertTrue(restored.allMembers(parent).size()==2&&restored.unit(leaf).orElseThrow().parent().equals(parent),"actual NBT restores hierarchy and real member IDs");
        h.assertTrue(restored.unit(parent).orElseThrow().commander().equals(b)&&restored.unit(leaf).orElseThrow().members().get(b)==MilitaryRank.CAPTAIN,"appointment and rank survive");
        h.assertTrue(restored.effectiveOrder(leaf).orElseThrow().equals(order),"inherited movement intent survives");h.succeed();
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=40)
    public static void malformedSavedDataCannotIntroduceDuplicateMembersOrUnknownFactions(GameTestHelper h){
        var data=new MilitarySavedData();var org=data.organization();var point=new MilitaryOrder.Point(0,64,0);
        UUID a=UUID.randomUUID(),b=UUID.randomUUID(),member=UUID.randomUUID();
        org.create(a,MilitaryFaction.MILITARY,MilitaryUnitType.SECTION,"a",8,point);org.create(b,MilitaryFaction.MILITARY,MilitaryUnitType.SECTION,"b",8,point);org.assign(a,member,MilitaryRank.SERGEANT);
        var tag=data.save(new CompoundTag(),h.getLevel().registryAccess());var entries=tag.getList("Units",10);
        var first=entries.getCompound(0);var second=entries.getCompound(1);
        var roster=new ListTag();var duplicate=new CompoundTag();duplicate.putUUID("Id",member);duplicate.putString("Rank","not_a_rank");roster.add(duplicate);second.put("Members",roster);
        first.putUUID("Parent",second.getUUID("Id"));second.putUUID("Parent",first.getUUID("Id"));
        var bad=first.copy();bad.putUUID("Id",UUID.randomUUID());bad.putString("Name","invalid-faction");bad.putString("Faction","unknown_future_faction");entries.add(bad);
        var restored=MilitarySavedData.load(tag,h.getLevel().registryAccess()).organization();
        long appearances=restored.units().stream().filter(u->u.members().containsKey(member)).count();
        h.assertTrue(appearances==1&&restored.units().size()==2,"duplicate ownership and unknown faction are rejected safely");
        h.assertTrue(restored.units().stream().allMatch(u->u.parent()==null),"illegal cyclic leaf-parent edges are ignored");
        h.assertTrue(MilitarySavedData.load(new CompoundTag(),h.getLevel().registryAccess()).organization().units().isEmpty(),"missing legacy data safely starts empty");h.succeed();
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=150)
    public static void actualCommandsEnforcePermissionAndAtomicFactionTransfers(GameTestHelper h){
        arena(h);var army=troops(h,2,true);var watch=troops(h,1,false).get(0);watch.setPos(h.absoluteVec(new Vec3(25,2,25)));
        h.runAtTickTime(80,()->{
            String name=name(),forbidden=name();
            h.assertTrue(command(h,0,"blacklight military unit create military tactical_squad "+forbidden+" 12")==0&&!org(h).named(forbidden).isPresent(),"unprivileged command cannot mutate organization");
            h.assertTrue(command(h,2,"blacklight military unit create military tactical_squad "+name+" 12")>0,"real create command succeeds");
            h.assertTrue(command(h,2,"blacklight military unit assign "+name+" "+army.get(0).getUUID())>0,"assign moves an existing automatic member");
            var unit=org(h).named(name).orElseThrow().id();var watchUnit=watch.unitId();
            h.assertTrue(command(h,2,"blacklight military unit assign "+name+" "+watch.getUUID())==0,"foreign-faction transfer fails");
            h.assertTrue(org(h).allMembers(unit).equals(List.of(army.get(0).getUUID()))&&watchUnit.equals(watch.unitId()),"failed transfer leaves both real rosters and entities intact");
            h.assertTrue(command(h,2,"blacklight military rank "+army.get(0).getUUID()+" captain")>0&&army.get(0).rank()==MilitaryRank.CAPTAIN,"rank command changes actual synced entity");
            h.assertTrue(command(h,2,"blacklight military unit commander "+name+" "+army.get(0).getUUID())>0,"commander command appoints roster member");
            h.assertTrue(command(h,2,"blacklight military inspect "+name)>0,"inspection resolves actual unit");h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=150)
    public static void oneCommanderCanOwnATwelvePersonLeafAfterActualAssignment(GameTestHelper h){
        arena(h);var troops=troops(h,12,true);
        h.runAtTickTime(80,()->{
            String name=name();command(h,2,"blacklight military unit create military tactical_squad "+name+" 12");
            h.assertTrue(command(h,2,"blacklight military unit assign "+name+" "+selector(h))==12,"actual selector moves twelve existing troops transactionally");
            var unit=org(h).named(name).orElseThrow();h.assertTrue(unit.members().size()==12,"formal leaf is twelve rather than four");
            h.assertTrue(unit.commander().equals(troops.get(0).getUUID()),"highest ranked member gets initial appointment");
            for(var soldier:troops)h.assertTrue(unit.id().equals(soldier.unitId())&&org(h).leafOf(soldier.getUUID()).orElseThrow().equals(unit.id()),"every actual troop has exactly one new leaf");h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=180)
    public static void organizeCommandBuildsARealSeventyTwoPersonCompany(GameTestHelper h){
        arena(h);var troops=troops(h,72,true);
        h.runAtTickTime(100,()->{
            h.assertTrue(command(h,2,"blacklight military organize military company "+selector(h))==72,"organize uses seventy-two existing entities");
            var leaf=org(h).leafOf(troops.get(0).getUUID()).orElseThrow();var root=org(h).rootOf(leaf).orElseThrow();var company=org(h).unit(root).orElseThrow();
            h.assertTrue(company.type()==MilitaryUnitType.COMPANY&&company.children().size()==3&&org(h).allMembers(root).size()==72,"real company has three platoons and72 unique members");
            for(var platoonId:company.children()){
                var platoon=org(h).unit(platoonId).orElseThrow();h.assertTrue(platoon.type()==MilitaryUnitType.PLATOON&&platoon.children().size()==3,"each platoon has three sections");
                for(var section:platoon.children())h.assertTrue(org(h).unit(section).orElseThrow().members().size()==8,"each section has eight real troops");
            }
            h.assertTrue(troops.stream().allMatch(s->org(h).rootOf(s.unitId()).orElseThrow().equals(root)),"all actual unit hints agree with the same company tree");h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=150)
    public static void commanderStaggerIsTemporaryAndPermanentRemovalReassigns(GameTestHelper h){
        arena(h);var troops=troops(h,3,true);var captain=troops.get(0);captain.setRank(MilitaryRank.CAPTAIN);troops.get(1).setRank(MilitaryRank.SERGEANT);
        h.runAtTickTime(80,()->{
            var unit=captain.unitId();h.assertTrue(org(h).unit(unit).orElseThrow().commander().equals(captain.getUUID()),"formal commander is highest initial rank");
            ImpactControl.begin(captain,new ImpactProfile(HitReaction.STAGGER,ImpactProfile.BODY,12,0,0,false),0);
            h.assertTrue(MilitaryOrganizationSystem.commander(h.getLevel(),unit).orElseThrow().equals(troops.get(1).getUUID()),"real control selects a healthy deputy");
            h.assertTrue(org(h).unit(unit).orElseThrow().commander().equals(captain.getUUID()),"temporary control preserves formal appointment");
            ImpactControl.cancel(captain);h.assertTrue(MilitaryOrganizationSystem.commander(h.getLevel(),unit).orElseThrow().equals(captain.getUUID()),"recovery returns command");
            captain.discard();h.assertTrue(!org(h).allMembers(unit).contains(captain.getUUID())&&org(h).unit(unit).orElseThrow().commander().equals(troops.get(1).getUUID()),"permanent removal updates roster and formal successor");h.succeed();
        });
    }
    @GameTest(template="blackwatch_facility_arena",batch="militaryOrganization",timeoutTicks=150)
    public static void unloadingRuntimePreservesRosterAndDimensionsKeepIndependentData(GameTestHelper h){
        arena(h);var troops=troops(h,2,true);
        h.runAtTickTime(80,()->{
            var unit=troops.get(0).unitId();var before=org(h).unit(unit).orElseThrow();
            MilitaryOrganizationSystem.unload(new net.neoforged.neoforge.event.level.LevelEvent.Unload(h.getLevel()));
            h.assertTrue(org(h).unit(unit).orElseThrow().members().equals(before.members()),"runtime unloading preserves SavedData roster");
            var other=h.getLevel().getServer().getLevel(Level.NETHER);h.assertTrue(other!=null,"real Nether is loaded");
            var otherOrg=MilitaryOrganizationSystem.organization(other);
            h.assertTrue(otherOrg.unit(unit).isEmpty(),"same UUID has no meaning in another dimension's organization");
            h.assertTrue(otherOrg.create(unit,MilitaryFaction.BLACKWATCH,MilitaryUnitType.SECTION,name(),8,new MilitaryOrder.Point(0,64,0)),"second dimension accepts independent unit identity");
            h.assertTrue(org(h).unit(unit).orElseThrow().faction()==MilitaryFaction.MILITARY&&otherOrg.unit(unit).orElseThrow().faction()==MilitaryFaction.BLACKWATCH,"faction data cannot alias across worlds");
            otherOrg.removeUnit(unit);h.succeed();
        });
    }
}
