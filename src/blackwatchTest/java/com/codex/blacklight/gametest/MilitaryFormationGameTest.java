package com.codex.blacklight.gametest;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class MilitaryFormationGameTest {
    private static List<MilitarySoldier> troops(GameTestHelper h,int count){
        var from=h.absolutePos(BlockPos.ZERO);var to=h.absolutePos(new BlockPos(95,0,95));
        // Fixture only: Forge1.20's GameTest runner otherwise stops entity ticks at its small default ticket edge.
        for(int x=(from.getX()>>4)-1;x<=(to.getX()>>4)+1;x++)for(int z=(from.getZ()>>4)-1;z<=(to.getZ()>>4)+1;z++)h.getLevel().setChunkForced(x,z,true);
        for(int x=0;x<96;x++)for(int z=0;z<96;z++)h.setBlock(x,1,z,Blocks.STONE);
        var result=new ArrayList<MilitarySoldier>();
        for(int i=0;i<count;i++){
            var s=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());s.setPersistenceRequired();
            s.setPos(h.absoluteVec(new Vec3(24.5+i%9*2,2,24.5+i/9*2)));s.setRank(i==0?MilitaryRank.CAPTAIN:MilitaryRank.PRIVATE);
            h.getLevel().addFreshEntity(s);result.add(s);
        }
        return result;
    }
    private static UUID organize(GameTestHelper h,List<MilitarySoldier> troops,String type){
        var p=h.absolutePos(new BlockPos(0,2,0));var selector="@e[type=blacklight:military_soldier,x="+p.getX()+",y="+p.getY()+",z="+p.getZ()+",dx=95,dy=6,dz=95]";
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPosition(troops.get(0).position()).withPermission(2).withSuppressedOutput();
        try{h.assertTrue(h.getLevel().getServer().getCommands().getDispatcher().execute("blacklight military organize military "+type+" "+selector,source)==troops.size(),"actual organize command accepts every existing troop");}
        catch(Exception e){throw new AssertionError(e);}
        var org=MilitaryOrganizationSystem.organization(h.getLevel());return org.rootOf(troops.get(0).unitId()).orElseThrow();
    }
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=180)
    public static void twelveMembersKeepOneCommanderAndThreeDisjointFireCells(GameTestHelper h){
        var troops=troops(h,12);
        h.runAtTickTime(30,()->organize(h,troops,"tactical_squad"));
        h.runAtTickTime(120,()->{
            var groups=new HashSet<Set<UUID>>();var seen=new HashSet<UUID>();
            for(var s:troops){var members=Set.copyOf(BlackwatchSquadSystem.squad(s).members());h.assertTrue(members.size()<=4,"internal fire cell stays at most four");groups.add(members);}
            h.assertTrue(groups.size()==3,"twelve-person formal unit fills three fire cells, rather than twelve independent shooters");
            for(var g:groups)for(var id:g)h.assertTrue(seen.add(id),"each shooter occupies one cell only");
            var org=MilitaryOrganizationSystem.organization(h.getLevel());var leaf=org.unit(troops.get(0).unitId()).orElseThrow();
            h.assertTrue(leaf.members().size()==12&&leaf.commander().equals(troops.get(0).getUUID()),"one formal twelve-person commander remains distinct from internal firing turns");h.succeed();
        });
    }
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=180)
    public static void thirtyTwoMemberLeafKeepsEightFireCells(GameTestHelper h){
        var troops=troops(h,32);h.runAtTickTime(30,()->organize(h,troops,"tactical_squad"));
        h.runAtTickTime(120,()->{var cells=new HashSet<Set<UUID>>();for(var s:troops)cells.add(Set.copyOf(BlackwatchSquadSystem.squad(s).members()));
            h.assertTrue(cells.size()==8&&cells.stream().allMatch(c->c.size()==4),"maximum32-person leaf uses eight four-person internal cells");h.succeed();});
    }
    private static void march(GameTestHelper h,int count,String type){
        var troops=troops(h,count);var previous=new HashMap<UUID,Vec3>();var destination=h.absoluteVec(new Vec3(78.5,2,65.5));
        final UUID[] root={null};
        var sequence=h.startSequence().thenWaitUntil(()->{
            h.assertTrue(troops.stream().allMatch(s->s.tickCount>=20)&&h.getLevel().isPositionEntityTicking(BlockPos.containing(destination)),"fixture troops and destination enter entity-ticking chunks before timing the march");
        }).thenExecute(()->{root[0]=organize(h,troops,type);MilitaryOrganizationSystem.organization(h.getLevel()).order(root[0],new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(destination.x,destination.y,destination.z)));});
        h.onEachTick(()->{for(var s:troops){var p=previous.put(s.getUUID(),s.position());if(p!=null)h.assertTrue(p.distanceToSqr(s.position())<1.6,"movement uses walking, without teleport correction");}});
        sequence.thenExecuteAfter(1050,()->{
            var captain=troops.get(0);h.assertTrue(captain.position().distanceToSqr(destination)<25,"commanding officer follows long-distance movement intent: "+captain.position()+" goal "+destination+" memberTicks="+captain.tickCount+" loaded="+h.getLevel().hasChunkAt(captain.blockPosition())+" ticking="+h.getLevel().isPositionEntityTicking(captain.blockPosition())+" target="+captain.getTarget()+" pathDone="+captain.getNavigation().isDone());
            long arrived=troops.stream().filter(s->s.position().distanceToSqr(destination)<40*40).count();
            h.assertTrue(arrived>=count*3/4,"most actual members follow their hierarchy beyond the old home boundary: "+arrived+"/"+count);
            var stats=MilitaryFormationSystem.metrics(h.getLevel());h.assertTrue(stats.peakPlans()<=8&&stats.shortestInterval()>=10,"native and requested path plans share the level budget: "+stats);
            h.assertTrue(stats.plannedMembers()>=count,"FIFO service eventually plans a path for every member: "+stats);h.succeed();
        });
    }
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=3000)
    public static void twelvePersonLeafWalksBeyondItsOriginalHome(GameTestHelper h){march(h,12,"tactical_squad");}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=3000)
    public static void twentyFourPersonPlatoonWalksAsThreeSections(GameTestHelper h){march(h,24,"platoon");}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=3000)
    public static void seventyTwoPersonCompanyWalksWithBoundedPathWork(GameTestHelper h){march(h,72,"company");}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=180)
    public static void childOrderOverridesParentAndUnloadedTargetsNeverLoadChunks(GameTestHelper h){
        var troops=troops(h,24);
        h.runAtTickTime(30,()->{
            var root=organize(h,troops,"platoon");var org=MilitaryOrganizationSystem.organization(h.getLevel());var leaf=troops.get(0).unitId();
            var p=h.absoluteVec(new Vec3(70,2,70));org.order(root,new MilitaryOrder(MilitaryOrder.Mode.MOVE,new MilitaryOrder.Point(p.x,p.y,p.z)));
            org.order(leaf,new MilitaryOrder(MilitaryOrder.Mode.GUARD,null));
            h.assertTrue(MilitaryFormationSystem.scope(troops.get(0)).equals(leaf),"explicit child order has its own command scope");
            org.order(leaf,null);h.assertTrue(MilitaryFormationSystem.scope(troops.get(0)).equals(root),"inherit restores parent scope");
            var far=new BlockPos(20000000,64,20000000);h.assertTrue(!h.getLevel().hasChunkAt(far),"test begins with an unloaded destination");
            h.assertTrue(troops.get(0).getNavigation().createPath(far,0)==null&&!h.getLevel().hasChunkAt(far),"public navigation rejects unloaded destination before any chunk access");
            h.succeed();
        });
    }
    private static void alarm(GameTestHelper h,boolean outsideLink){
        var troops=troops(h,2);var a=troops.get(0);var b=troops.get(1);b.setPos(h.absoluteVec(new Vec3(outsideLink?94.5:80.5,2,24.5)));
        var target=ModEntities.INFECTED_HOST.get().create(h.getLevel());target.inherit(net.minecraft.world.entity.EntityType.COW.create(h.getLevel()));target.setNoAi(true);target.setPersistenceRequired();
        target.setPos(h.absoluteVec(new Vec3(42.5,2,24.5)));target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);target.setHealth(10000);h.getLevel().addFreshEntity(target);
        for(int z=0;z<96;z++)for(int y=2;y<7;y++)h.setBlock(60,y,z,Blocks.BEDROCK);
        final BlackwatchSquad.Contact[] original={null};
        h.runAtTickTime(20,()->{
            var org=MilitaryOrganizationSystem.organization(h.getLevel());org.removeMember(a.getUUID());org.removeMember(b.getUUID());
            UUID parent=UUID.randomUUID(),left=UUID.randomUUID(),right=UUID.randomUUID();
            for(var item:List.of(Map.entry(left,a),Map.entry(right,b))){var s=item.getValue();org.create(item.getKey(),MilitaryFaction.MILITARY,MilitaryUnitType.SECTION,"alarm-"+item.getKey(),1,new MilitaryOrder.Point(s.getX(),s.getY(),s.getZ()));org.assign(item.getKey(),s.getUUID(),s.rank());s.setUnitId(item.getKey());org.order(item.getKey(),new MilitaryOrder(MilitaryOrder.Mode.GUARD,null));}
            org.create(parent,MilitaryFaction.MILITARY,MilitaryUnitType.PLATOON,"alarm-"+parent,2,new MilitaryOrder.Point(a.getX(),a.getY(),a.getZ()));org.attach(parent,left);org.attach(parent,right);org.appoint(parent,a.getUUID());
            a.setTarget(target);BlackwatchSquadSystem.tick(a);original[0]=BlackwatchSquadSystem.contact(a).orElseThrow();
            for(int z=0;z<96;z++)for(int y=2;y<7;y++)h.setBlock(36,y,z,Blocks.BEDROCK);
            target.setPos(h.absoluteVec(new Vec3(44.5,2,30.5)));
        });
        h.runAtTickTime(70,()->{
            var contact=MilitaryFormationSystem.contact(b);
            if(outsideLink)h.assertTrue(contact.isEmpty(),"commander edge longer than64 cannot relay an alarm");
            else{h.assertTrue(contact.isPresent(),"real observation travels leaf-parent-leaf through loaded commanders");h.assertTrue(contact.get().observedAt()==original[0].observedAt()&&contact.get().position().equals(original[0].position()),"forwarding preserves original time and last-known position through new cover");}
        });
        h.runAtTickTime(135,()->{h.assertTrue(MilitaryFormationSystem.contact(b).isEmpty(),"multi-hop forwarding never refreshes the100tick TTL");h.succeed();});
    }
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=170)
    public static void hierarchyRelaysOnlyTheOriginalSightingUntilItExpires(GameTestHelper h){alarm(h,false);}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=170)
    public static void hierarchyCannotRelayAcrossAnOutOfRangeCommanderLink(GameTestHelper h){alarm(h,true);}

    private static UUID independent(GameTestHelper h,MilitarySoldier soldier){
        var org=MilitaryOrganizationSystem.organization(h.getLevel());org.removeMember(soldier.getUUID());var id=UUID.randomUUID();var p=soldier.home();
        h.assertTrue(org.create(id,MilitaryFaction.MILITARY,MilitaryUnitType.TACTICAL_SQUAD,"review-"+id,1,new MilitaryOrder.Point(p.getX()+.5,p.getY(),p.getZ()+.5)),"create independent fixture roster");
        org.assign(id,soldier.getUUID(),soldier.rank());soldier.setUnitId(id);return id;
    }
    private static int command(GameTestHelper h,String text){
        var source=h.getLevel().getServer().createCommandSourceStack().withLevel(h.getLevel()).withPermission(2).withSuppressedOutput();
        try{return h.getLevel().getServer().getCommands().getDispatcher().execute(text,source);}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){return 0;}
    }
    private static String coordinates(Vec3 point){return String.format(java.util.Locale.ROOT,"%.3f %.3f %.3f",point.x,point.y,point.z);}
    private static void coordinates(GameTestHelper h,String mode){
        var soldier=troops(h,1).get(0);var goal=h.absoluteVec(new Vec3(78.5,2,65.5));final net.minecraft.world.entity.LivingEntity[] victim={null};float[] health={0};
        h.runAtTickTime(30,()->{var id=independent(h,soldier);h.assertTrue(command(h,"blacklight military order "+id+" "+mode+" "+coordinates(goal))>0,"actual coordinate order is accepted");});
        h.runAtTickTime(1000,()->{
            h.assertTrue(soldier.position().distanceToSqr(goal)<(mode.equals("patrol")?14*14:16),mode+" actually acts at the specified distant point: "+soldier.position()+" goal="+goal);
            h.assertTrue(BlackwatchSquadSystem.withinTerritory(soldier,goal)&&BlackwatchSquadSystem.withinTerritory(soldier,soldier.position()),"new post is a legal defensive territory beyond the old home48 boundary");
            if(mode.equals("guard")){
                var target=ModEntities.INFECTED_HOST.get().create(h.getLevel());target.inherit(net.minecraft.world.entity.EntityType.COW.create(h.getLevel()));target.setNoAi(true);target.setPersistenceRequired();target.setPos(goal.add(8,0,0));
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);h.getLevel().addFreshEntity(target);target.setHealth(target.getMaxHealth());target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                victim[0]=target;health[0]=target.getHealth();soldier.setTarget(target);
            }
        });
        h.runAtTickTime(1060,()->{if(victim[0]!=null)h.assertTrue(victim[0].getHealth()<health[0],"guard at the new post actually fires after arriving");h.succeed();});
    }
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=1150) public static void guardCoordinatesEstablishANewFiringTerritory(GameTestHelper h){coordinates(h,"guard");}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=1150) public static void patrolCoordinatesMoveTheActualPatrolCenter(GameTestHelper h){coordinates(h,"patrol");}
    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=1150) public static void regroupCoordinatesMoveToTheSpecifiedPoint(GameTestHelper h){coordinates(h,"regroup");}

    @GameTest(template="military_march_arena",batch="militaryFormation",timeoutTicks=3000)
    public static void commandersGuardingChildDoesNotStopTheOtherInheritedMarch(GameTestHelper h){
        var troops=troops(h,24);var goal=h.absoluteVec(new Vec3(78.5,2,65.5));final UUID[] root={null},held={null};
        var sequence=h.startSequence().thenWaitUntil(()->h.assertTrue(troops.stream().allMatch(s->s.tickCount>=20)&&h.getLevel().isPositionEntityTicking(BlockPos.containing(goal)),"fixture regions are ticking before child-override movement timing"))
            .thenExecute(()->{root[0]=organize(h,troops,"platoon");held[0]=troops.get(0).unitId();
            h.assertTrue(command(h,"blacklight military order "+root[0]+" move "+coordinates(goal))>0&&command(h,"blacklight military order "+held[0]+" guard")>0,"both actual parent and child orders are accepted");});
        sequence.thenExecuteAfter(1060,()->{var org=MilitaryOrganizationSystem.organization(h.getLevel());
            h.assertTrue(org.unit(root[0]).orElseThrow().commander().equals(troops.get(0).getUUID()),"operational deputy does not replace the formal appointment");
            h.assertTrue(troops.get(0).position().distanceToSqr(Vec3.atBottomCenterOf(troops.get(0).home()))<100,"overridden HQ child really remains on guard");
            long arrived=troops.stream().filter(s->!s.unitId().equals(held[0])&&s.position().distanceToSqr(goal)<25*25).count();
            h.assertTrue(arrived>=12,"other inherited sections actually march despite formal officer staying behind: "+arrived+"/16");h.succeed();});
    }

    private static void stairs(GameTestHelper h,boolean sameColumn){
        for(int x=0;x<48;x++)for(int z=0;z<48;z++)h.setBlock(x,1,z,Blocks.STONE);
        var from=h.absolutePos(BlockPos.ZERO);var to=h.absolutePos(new BlockPos(47,0,47));for(int x=(from.getX()>>4)-1;x<=(to.getX()>>4)+1;x++)for(int z=(from.getZ()>>4)-1;z<=(to.getZ()>>4)+1;z++)h.getLevel().setChunkForced(x,z,true);
        int rise=sameColumn?4:18;
        for(int z=7;z<=6+rise;z++)for(int x=11;x<=13;x++)for(int y=2;y<=1+z-6;y++)h.setBlock(x,y,z,Blocks.STONE);
        if(sameColumn){for(int z=6;z<=10;z++)h.setBlock(9,5,z,Blocks.STONE);for(int x=9;x<=12;x++){h.setBlock(x,5,6,Blocks.STONE);h.setBlock(x,5,10,Blocks.STONE);}}
        var s=ModEntities.MILITARY_SOLDIER.get().create(h.getLevel());s.setPersistenceRequired();s.setNoAi(true);s.setPos(h.absoluteVec(new Vec3(12.5,2,6.5)));h.getLevel().addFreshEntity(s);
        var goal=h.absoluteVec(new Vec3(12.5,rise+2,sameColumn?6.5:6.5+rise));
        h.runAtTickTime(30,()->{
            s.setNoAi(false);s.setOnGround(true);
            var reference=new net.minecraft.world.entity.ai.navigation.GroundPathNavigation(s,h.getLevel()).createPath(BlockPos.containing(goal),0);
            h.assertTrue(reference!=null&&reference.canReach(),"the loaded staircase is reachable by unmodified native navigation: pos="+s.position()+" ground="+s.onGround()+" target="+goal+" range="+s.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE)+" path="+(reference==null?"null":reference.getNodeCount()+"nodes,end="+reference.getEndNode().asBlockPos()+",canReach="+reference.canReach()));
            var id=independent(h,s);h.assertTrue(command(h,"blacklight military order "+id+" move "+coordinates(goal))>0,"actual staircase command is accepted");
        });
        h.runAtTickTime(750,()->{h.assertTrue(s.position().distanceToSqr(goal)<4,"real soldier walks the reachable "+(sameColumn?"same-XZ upper floor":"long staircase")+": "+s.position()+" -> "+goal);h.succeed();});
    }
    @GameTest(template="military_slope_arena",batch="militarySlope",timeoutTicks=850) public static void soldierWalksALongNativeReachableStaircase(GameTestHelper h){stairs(h,false);}
    @GameTest(template="military_slope_arena",batch="militarySlope",timeoutTicks=850) public static void sameXZUpperFloorStillRequiresVerticalMovement(GameTestHelper h){stairs(h,true);}
}
