package com.codex.blacklight.city;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.*;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.*;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import static com.codex.blacklight.city.MilitaryCampaignData.*;

/** Real finite personnel and real objectives. No chunk tickets, timed auto-kills or free ammo in existing inventories. */
@EventBusSubscriber(modid="blacklight")
public final class MilitaryCampaignSystem {
    private static final Map<ServerLevel,Long> LAST=new WeakHashMap<>();
    private static final Map<ServerLevel,Integer> CURSORS=new WeakHashMap<>();
    private static final Set<MilitarySoldier> GOALS=Collections.newSetFromMap(new WeakHashMap<>());
    private record Broken(BlockPos at,UUID player,String base) {}
    private static final Map<ServerLevel,List<Broken>> BROKEN=new WeakHashMap<>();
    private MilitaryCampaignSystem() {}
    public static boolean supported(ServerLevel level){return level.dimension().equals(Level.OVERWORLD)||CityTravel.isCity(level.dimension());}
    public static boolean enabled(ServerLevel level){return supported(level)&&MilitaryCampaignConfig.ENABLED.get();}
    public static boolean disabled(ServerLevel level,String id){
        if(!enabled(level))return false;var state=get(level).bases.get(id);return state!=null&&state.sabotage>0;
    }
    public static boolean controllingSoldier(Mob mob){
        return mob instanceof MilitarySoldier s&&s.level() instanceof ServerLevel level&&enabled(level)
                &&s.equipped()&&s.hasBattleAmmunition()&&!BlacklightInfectionSystem.isInfected(s)&&!s.isNoAi()&&!s.isPassenger();
    }
    private static Operation operation(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level)||!enabled(level))return null;
        for(var op:get(level).operations.values())if(op.members.contains(soldier.getUUID())&&!op.lost.contains(soldier.getUUID()))return op;
        return null;
    }
    public static boolean active(MilitarySoldier soldier){return operation(soldier)!=null&&!MilitaryHomeSystem.ordered(soldier);}
    public static boolean ambient(MilitarySoldier soldier){return FieldReinforcementSystem.ambient(soldier);}
    public static boolean deploymentAllows(MilitarySoldier soldier,Vec3 position){
        var op=operation(soldier);if(op==null||MilitaryHomeSystem.ordered(soldier))return false;
        var base=MilitaryBaseAccess.find((ServerLevel)soldier.level(),op.base).orElse(null);if(base==null&&!op.expedition)return false;
        Vec3 start=Vec3.atCenterOf(op.expedition?op.origin:base.center()),end=Vec3.atCenterOf(op.target),segment=end.subtract(start);
        double length=segment.lengthSqr(),t=length<1?0:com.codex.blacklight.forge.WingMath.clamp(position.subtract(start).dot(segment)/length,0,1);
        return position.distanceToSqr(start.add(segment.scale(t)))<=48*48;
    }
    public static boolean authorizedCore(MilitarySoldier soldier,BlockPos at){
        var op=operation(soldier);return op!=null&&op.phase==Phase.ADVANCE&&op.kind!=Kind.HIVE&&op.kind!=Kind.PATROL&&op.target.equals(at)
                &&soldier.level() instanceof ServerLevel level&&level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                &&!MilitaryHomeSystem.ordered(soldier)&&loaded(level,at)&&core(level,at);
    }
    static boolean loaded(ServerLevel level,BlockPos at){return !level.isOutsideBuildHeight(at)&&level.getWorldBorder().isWithinBounds(at)&&level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4)!=null&&level.isPositionEntityTicking(at);}
    private static boolean core(ServerLevel level,BlockPos at){var state=level.getBlockState(at);return state.is(ModBlocks.BUILDING_CORE.get())||state.is(CityBlocks.HATCH.get());}
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!supported(level))return;
        if(event.getEntity() instanceof BlacklightHive hive){var d=get(level);if(d.hives.size()<MAX_BASES||d.hives.containsKey(hive.getUUID())){d.hives.put(hive.getUUID(),hive.blockPosition());d.setDirty();}}
        if(event.getEntity() instanceof MilitarySoldier soldier&&GOALS.add(soldier))soldier.goalSelector.addGoal(1,new SortieGoal(soldier));
    }
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent event){if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(event.level instanceof ServerLevel level&&level.getGameTime()%20==0)
            step(level,level.players().stream().filter(p->!p.isSpectator()).map(Entity::position).toList());
    }
    static void step(ServerLevel level,List<Vec3> observers){
        if(!enabled(level)||observers.isEmpty()||Objects.equals(LAST.get(level),level.getGameTime()))return;
        LAST.put(level,level.getGameTime());var d=get(level);confirmBreaks(level);
        for(var entry:d.bases.entrySet()){
            var base=MilitaryBaseAccess.find(level,entry.getKey()).orElse(null);if(base==null||observers.stream().noneMatch(p->p.distanceToSqr(Vec3.atCenterOf(base.center()))<256*256))continue;
            var state=entry.getValue();int cooldown=state.cooldown,sabotage=state.sabotage;
            state.cooldown=Math.max(0,cooldown-20);state.sabotage=Math.max(0,sabotage-20);
            if(cooldown!=state.cooldown||sabotage!=state.sabotage)d.setDirty();
            if(state.destroyed&&!state.destructionRewarded&&state.actor!=null){
                var player=level.getServer().getPlayerList().getPlayer(state.actor);
                if(player!=null&&player.serverLevel()==level&&player.distanceToSqr(Vec3.atCenterOf(base.center()))<192*192){reward(player,MilitaryCampaignConfig.DESTROY_REWARD.get());state.destructionRewarded=true;d.setDirty();message(player,"base_destroyed",factionName(base.faction()));}
            }
        }
        d.infiltration.replaceAll((id,plan)->new Infiltration(plan.base(),Math.max(0,plan.remaining()-20)));
        if(d.infiltration.values().removeIf(p->p.remaining()==0)||!d.infiltration.isEmpty())d.setDirty();
        for(var op:new ArrayList<>(d.operations.values()))update(level,d,op,observers);
        FieldReinforcementSystem.retire(level,observers);
        if(WorldOutbreakSystem.active(level)&&level.getDifficulty()!=Difficulty.PEACEFUL
                &&level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)
                &&level.getGameTime()%MilitaryCampaignConfig.reinforcementInterval()==0)FieldReinforcementSystem.reinforce(level,observers);
        if(level.getGameTime()%MilitaryCampaignConfig.interval()!=0||!WorldOutbreakSystem.active(level)
                ||level.getDifficulty()==Difficulty.PEACEFUL||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return;
        if(d.operations.size()>=MilitaryCampaignConfig.ACTIVE.get())return;
        var bases=new ArrayList<>(CityMilitaryData.get(level).bases.values());if(bases.isEmpty())return;
        int first=Math.floorMod(CURSORS.getOrDefault(level,0),bases.size());CURSORS.put(level,(first+8)%bases.size());
        for(int i=0;i<Math.min(8,bases.size())&&d.operations.size()<MilitaryCampaignConfig.ACTIVE.get();i++){
            var base=bases.get((first+i)%bases.size());
            if(observers.stream().noneMatch(p->p.distanceToSqr(MilitaryHomeSystem.center(base))<128*128))continue;
            dispatch(level,base.id);
        }
    }
    static boolean dispatch(ServerLevel level,String baseId){
        if(!enabled(level)||!WorldOutbreakSystem.active(level)||level.getDifficulty()==Difficulty.PEACEFUL
                ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING))return false;
        var d=get(level);var base=MilitaryBaseAccess.find(level,baseId).orElse(null);
        if(base==null||!base.loaded()||!base.operational()||d.operations.size()>=MilitaryCampaignConfig.ACTIVE.get()
                ||d.operations.values().stream().anyMatch(op->op.base.equals(baseId)))return false;
        var state=d.base(baseId);if(state==null||state.cooldown>0||state.sabotage>0)return false;
        var reserved=new HashSet<UUID>();d.operations.values().forEach(op->reserved.addAll(op.members));
        var soldiers=new ArrayList<>(MilitaryBaseAccess.idleCrew(level,baseId,8,reserved));
        soldiers.removeIf(s->!s.hasBattleAmmunition());soldiers.sort(Comparator.comparing((MilitarySoldier s)->!s.heavy()).thenComparing(MilitarySoldier::getUUID));
        if(soldiers.size()<2)return false;
        var target=objective(level,base.id(),base.center());if(target==null)return false;
        var op=new Operation(UUID.randomUUID(),baseId,target.kind,target.target,target.entity,target.hive);
        op.origin=base.center();
        soldiers.stream().limit(MilitaryCampaignConfig.SQUAD.get()).forEach(s->{op.members.add(s.getUUID());s.getNavigation().stop();});
        d.operations.put(op.id,op);d.setDirty();return true;
    }
    static Operation objective(ServerLevel level,String baseId,BlockPos position){
        var list=new ArrayList<Operation>();Vec3 center=Vec3.atCenterOf(position);int range=MilitaryCampaignConfig.RANGE.get();
        for(var colony:BuildingInfectionData.get(level).colonies)if(!colony.dead&&loaded(level,colony.origin)&&level.getBlockState(colony.origin).is(ModBlocks.BUILDING_CORE.get())&&center.distanceToSqr(Vec3.atCenterOf(colony.origin))<=range*range)
            list.add(new Operation(UUID.randomUUID(),baseId,Kind.COLONY,colony.origin,null,-1));
        for(var entity:level.getAllEntities())if(entity instanceof BlacklightHive hive&&hive.isAlive()&&loaded(level,hive.blockPosition())&&center.distanceToSqr(hive.position())<=range*range)
            list.add(new Operation(UUID.randomUUID(),baseId,Kind.HIVE,hive.blockPosition(),hive.getUUID(),-1));
        if(CityTravel.isCity(level.dimension()))for(int hive=0;hive<2;hive++)if(!CityData.get(level).destroyed(hive)){
            for(int i=0;i<7;i++){var at=CitySystem.port(level,hive,i);if(loaded(level,at)&&level.getBlockState(at).is(CityBlocks.HATCH.get())&&center.distanceToSqr(Vec3.atCenterOf(at))<=range*range){list.add(new Operation(UUID.randomUUID(),baseId,Kind.CITY_HIVE,at,null,hive));break;}}
        }
        list.removeIf(candidate->get(level).operations.values().stream().anyMatch(op->op.kind==candidate.kind&&(op.entity!=null?op.entity.equals(candidate.entity):op.kind==Kind.CITY_HIVE?op.hive==candidate.hive:op.target.equals(candidate.target))));
        return list.stream().min(Comparator.comparingDouble(op->center.distanceToSqr(Vec3.atCenterOf(op.target)))).orElse(null);
    }
    private static void update(ServerLevel level,MilitaryCampaignData data,Operation op,List<Vec3> observers){
        var base=MilitaryBaseAccess.find(level,op.base).orElse(null);
        if(base==null&&!op.expedition){finish(level,data,op);return;}
        boolean attended=observers.stream().anyMatch(p->p.distanceToSqr(Vec3.atCenterOf(op.target))<192*192||p.distanceToSqr(Vec3.atCenterOf(op.origin))<128*128);
        if(!attended){op.absent+=20;data.setDirty();if(op.absent>=1200)finish(level,data,op);return;}
        op.absent=0;
        if(!loaded(level,op.target))return;
        var living=new ArrayList<MilitarySoldier>();
        for(var id:op.members)if(!op.lost.contains(id)&&level.getEntity(id) instanceof MilitarySoldier soldier&&soldier.isAlive()){
            if(soldier.isPassenger()||BlacklightInfectionSystem.isInfected(soldier)||MilitaryHomeSystem.ordered(soldier)){op.lost.add(id);data.setDirty();}else living.add(soldier);
        }
        if(op.lost.size()==op.members.size()){finish(level,data,op);return;}
        if(living.isEmpty())return;
        if(op.phase==Phase.ADVANCE){
            op.work=Math.min(240000,op.work+20);boolean complete=complete(level,op);
            var state=data.bases.get(op.base);
            boolean baseDisabled=base!=null&&base.loaded()&&!base.operational()||state!=null&&(state.destroyed||state.sabotage>0);
            if(complete||baseDisabled||op.work>=MilitaryCampaignConfig.TIMEOUT.get()||living.stream().noneMatch(MilitarySoldier::hasBattleAmmunition)){
                op.phase=Phase.RETURN;op.result=complete?"COMPLETE":baseDisabled?"BASE_DISABLED":op.work>=MilitaryCampaignConfig.TIMEOUT.get()?"TIMEOUT":"AMMUNITION";
                living.forEach(s->{s.setTarget(null);s.getNavigation().stop();});
            }
        }else{
            op.returnWork=Math.min(240000,op.returnWork+20);
            Vec3 rally=rally(level,op);
            if(living.stream().allMatch(s->s.distanceToSqr(rally)<12*12)||op.returnWork>=2400)finish(level,data,op);
        }data.setDirty();
    }
    private static boolean complete(ServerLevel level,Operation op){
        if(op.kind==Kind.PATROL)return false;
        if(op.kind==Kind.HIVE){var entity=level.getEntity(op.entity);if(entity instanceof BlacklightHive hive&&hive.isAlive())op.target=hive.blockPosition();return op.objectiveKilled;}
        if(op.kind==Kind.COLONY)return !level.getBlockState(op.target).is(ModBlocks.BUILDING_CORE.get());
        if(CityData.get(level).destroyed(op.hive))return true;
        boolean allLoaded=true;
        for(int i=0;i<7;i++){var at=CitySystem.port(level,op.hive,i);if(!loaded(level,at)){allLoaded=false;continue;}if(level.getBlockState(at).is(CityBlocks.HATCH.get())){op.target=at;return false;}}
        return allLoaded;
    }
    private static void finish(ServerLevel level,MilitaryCampaignData data,Operation op){
        for(var id:op.members)if(level.getEntity(id) instanceof MilitarySoldier soldier){soldier.getNavigation().stop();if(soldier.getTarget() instanceof BlacklightHive)soldier.setTarget(null);}
        var base=data.base(op.base);if(base!=null&&!op.expedition)base.cooldown=Math.max(base.cooldown,MilitaryCampaignConfig.COOLDOWN.get());
        data.operations.remove(op.id);data.setDirty();
    }
    private static Vec3 rally(ServerLevel level,Operation op){
        if(op.expedition)return Vec3.atBottomCenterOf(op.origin);
        var b=CityMilitaryData.get(level).bases.get(op.base);if(b==null)return Vec3.ZERO;int[] p=b.site.world(32,b.site.templateDepth()-8);return new Vec3(p[0]+.5,b.site.floor()+1,p[1]+.5);
    }
    public static void infectionSourceCleared(ServerLevel level,BlockPos pos){
        var d=get(level);if(!d.rememberClear(pos))return;
        var key=CityEcology.key(level,pos);boolean source=false;int infected=0;
        for(var colony:BuildingInfectionData.get(level).colonies)if(!colony.dead&&CityEcology.key(level,colony.origin).equals(key)){source=true;break;}
        if(!source)source=d.hives.values().stream().anyMatch(at->CityEcology.key(level,at).equals(key));
        if(!source&&CityTravel.isCity(level.dimension()))for(int hive=0;hive<2;hive++)if(!CityData.get(level).destroyed(hive)&&CityEcology.key(level,CitySystem.port(level,hive,0)).equals(key))source=true;
        for(var entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&BlacklightInfectionSystem.isInfected(mob)&&CityEcology.key(level,mob.blockPosition()).equals(key))infected++;
        CityEcologyData.get(level).purge(key,MilitaryCampaignConfig.RECOVERY.get(),MilitaryCampaignConfig.REDUCTION.get(),!source&&infected<=MilitaryCampaignConfig.SURVIVORS.get(),MilitaryCampaignConfig.SECURED_PRESSURE.get());
    }
    public static void baseCoreStateChanged(ServerLevel level,String id,boolean inactive){
        if(!enabled(level))return;var d=get(level);var state=d.base(id);if(state==null)return;
        state.destroyed=inactive;if(inactive)for(var op:d.operations.values())if(op.base.equals(id)){op.phase=Phase.RETURN;op.result="BASE_DISABLED";}
        d.setDirty();
    }
    private static String atCore(ServerLevel level,BlockPos at){
        for(var base:CityMilitaryData.get(level).bases.values()){
            if(at.getX()<base.site.x()||at.getX()>=base.site.x()+base.site.width()||at.getZ()<base.site.z()||at.getZ()>=base.site.z()+base.site.depth())continue;
            for(var local:LargeBaseBlueprint.get(base.site.blackwatch()).commandCores()){int[] p=base.site.world(local[0],local[2]);if(at.equals(new BlockPos(p[0],base.site.floor()+local[1],p[1])))return base.id;}
        }return null;
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void broken(BlockEvent.BreakEvent event){
        if(event.isCanceled()||com.codex.blacklight.firearms.FirearmBlockDamage.ownsBreakEvent(event)||!(event.getLevel() instanceof ServerLevel level)||!enabled(level))return;
        if(!event.getState().is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get()))return;
        String base=atCore(level,event.getPos());if(base==null)return;
        var pending=BROKEN.computeIfAbsent(level,k->new ArrayList<>());if(pending.size()<128)pending.add(new Broken(event.getPos().immutable(),event.getPlayer().getUUID(),base));
    }
    private static void confirmBreaks(ServerLevel level){
        var pending=BROKEN.remove(level);if(pending==null)return;
        for(var entry:pending)if(level.hasChunkAt(entry.at)&&!level.getBlockState(entry.at).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get())){
            var state=get(level).base(entry.base);if(state!=null){state.actor=entry.player;get(level).setDirty();}
        }
    }
    public static void actualPlayerBreak(ServerPlayer player,BlockPos at){
        var level=player.serverLevel();if(!enabled(level))return;String id=atCore(level,at);if(id==null)return;
        var state=get(level).base(id);if(state!=null){state.actor=player.getUUID();get(level).setDirty();}
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST) public static void terminal(PlayerInteractEvent.RightClickBlock event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer player)||event.getHand()!=InteractionHand.MAIN_HAND||!enabled(player.serverLevel()))return;
        var level=player.serverLevel();var at=event.getPos();if(player.isSpectator()||!loaded(level,at)||player.distanceToSqr(Vec3.atCenterOf(at))>36||!level.getBlockState(at).is(MilitaryFacilityBlocks.COMMAND_TERMINAL.get()))return;
        String id=atCore(level,at);if(id==null)return;
        var base=MilitaryBaseAccess.find(level,id).orElse(null);if(base==null||!base.operational())return;
        var identity=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.DISGUISE_IDENTITY);
        if(!com.codex.blacklight.disguise.DisguiseSystem.active(player)||!identity.family().equals(base.faction().id())||com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE).getAlert()>0){message(player,"identity_required",factionName(base.faction()));return;}
        var d=get(level);if(d.infiltration.size()>=MAX_PLANS&&!d.infiltration.containsKey(player.getUUID())||d.base(id)==null)return;
        d.infiltration.put(player.getUUID(),new Infiltration(id,12000));d.setDirty();
        message(player,"infiltration_started",factionName(base.faction()));event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
    }
    public static void consumed(ServerPlayer player,LivingEntity victim){
        var level=player.serverLevel();if(!enabled(level)||!(victim instanceof MilitarySoldier soldier)||soldier.commandRole()==MilitaryRole.TROOPER)return;
        var d=get(level);var plan=d.infiltration.get(player.getUUID());if(plan==null||plan.remaining()<=0||!plan.base().equals(soldier.getPersistentData().getString(CityMilitaryService.BASE_TAG)))return;
        var base=MilitaryBaseAccess.find(level,plan.base()).orElse(null);if(base==null||soldier.level()!=level||base.faction()!=soldier.faction())return;
        var state=d.base(plan.base());if(state==null)return;state.sabotage=Math.max(state.sabotage,MilitaryCampaignConfig.SABOTAGE.get());
        if(!state.infiltrationRewarded){reward(player,MilitaryCampaignConfig.INFILTRATE_REWARD.get());state.infiltrationRewarded=true;}
        d.infiltration.remove(player.getUUID());d.setDirty();message(player,"infiltration_complete",factionName(base.faction()));
    }
    private static void reward(ServerPlayer player,int points){var state=com.codex.blacklight.registry.ModAttachments.getData(player, ModAttachments.PLAYER_STATE);state.addEvolutionPoints(points);com.codex.blacklight.registry.ModAttachments.setData(player, ModAttachments.PLAYER_STATE,state);com.codex.blacklight.registry.ModAttachments.syncData(player, ModAttachments.PLAYER_STATE);}
    private static Component factionName(MilitaryFaction faction){return Component.translatable("campaign.blacklight."+faction.id());}
    private static void message(ServerPlayer player,String key,Object... args){player.displayClientMessage(Component.translatable("campaign.blacklight."+key,args),false);}
    /** Called only after the hive's LivingDeath has actually been accepted. */
    public static void hiveDefeated(BlacklightHive hive){
        if(!(hive.level() instanceof ServerLevel level)||!supported(level))return;
        var d=get(level);d.hives.remove(hive.getUUID());
        for(var op:d.operations.values())if(hive.getUUID().equals(op.entity))op.objectiveKilled=true;
        d.setDirty();
    }
    @SubscribeEvent public static void left(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level)||!supported(level))return;
        var reason=event.getEntity().getRemovalReason();if(reason==null||!reason.shouldDestroy()&&reason!=Entity.RemovalReason.CHANGED_DIMENSION)return;
        var d=get(level);
        if(event.getEntity() instanceof MilitarySoldier soldier)for(var op:d.operations.values())if(op.members.contains(soldier.getUUID())){op.lost.add(soldier.getUUID());d.setDirty();}
        if(event.getEntity() instanceof BlacklightHive hive&&d.hives.remove(hive.getUUID())!=null)d.setDirty();
    }
    @SubscribeEvent public static void commands(net.minecraftforge.event.RegisterCommandsEvent event){
        event.getDispatcher().register(net.minecraft.commands.Commands.literal("blacklight").then(net.minecraft.commands.Commands.literal("military").then(net.minecraft.commands.Commands.literal("campaign").executes(context->{
            var source=context.getSource();var level=source.getLevel();
            for(var base:MilitaryBaseAccess.nearby(level,source.getPosition())){var state=get(level).bases.get(base.id());
                source.sendSuccess(()->Component.literal((base.faction()==MilitaryFaction.BLACKWATCH?"黑色守望":"军方")+" "+base.center().toShortString()+" | "+(!base.operational()?"失去指挥":state!=null&&state.sabotage>0?"潜入瘫痪":"戒备")+" | 行动 "+get(level).operations.values().stream().filter(op->op.base.equals(base.id())).map(op->op.phase+" "+op.target.toShortString()+" "+op.result).toList()),false);
            }return get(level).operations.size();
        }))));
    }
    private static final class SortieGoal extends Goal {
        private final MilitarySoldier soldier;private Deque<Vec3> route=new ArrayDeque<>();private int ticks;
        SortieGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
        @Override public boolean canUse(){return soldier.tacticsAvailable()&&!MilitaryHomeSystem.ordered(soldier)&&operation(soldier)!=null&&!soldier.validTarget(soldier.getTarget());}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){route.clear();ticks=0;}
        @Override public void stop(){soldier.getNavigation().stop();}
        @Override public void tick(){
            var op=operation(soldier);if(op==null||!(soldier.level() instanceof ServerLevel level))return;
            Vec3 destination=op.phase==Phase.RETURN?rally(level,op):Vec3.atCenterOf(op.target);
            if(op.phase==Phase.ADVANCE&&op.kind==Kind.PATROL){double angle=(level.getGameTime()/200+soldier.getUUID().getLeastSignificantBits()%16)*2.399963;destination=destination.add(Math.cos(angle)*12,0,Math.sin(angle)*12);}
            if(!loaded(level,BlockPos.containing(destination)))return;
            soldier.getLookControl().setLookAt(destination.x,destination.y,destination.z,30,30);
            if(op.phase==Phase.ADVANCE&&op.kind==Kind.HIVE&&level.getEntity(op.entity) instanceof BlacklightHive hive&&soldier.validTarget(hive)){soldier.setTarget(hive);return;}
            if(op.phase==Phase.ADVANCE&&soldier.distanceToSqr(destination)<28*28&&soldier.fireAtCampaignCore(op.target)){soldier.getNavigation().stop();return;}
            if(++ticks%20==0){if(route.isEmpty()||ticks%100==0)route=CityMilitaryService.route(soldier,destination);CityMilitaryService.follow(soldier,route,.95);}
        }
    }
}
