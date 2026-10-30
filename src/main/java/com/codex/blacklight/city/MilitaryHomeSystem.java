package com.codex.blacklight.city;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.*;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Current assignments are separate from the immutable issuance provenance and roster. */
public final class MilitaryHomeSystem {
    public static final String HOME_BASE_TAG="BlacklightMilitaryHome",HOME_DIMENSION_TAG="BlacklightMilitaryHomeDimension";
    public static final String FIELD_ANCHOR_TAG="BlacklightMilitaryFieldAnchor",FIELD_DIMENSION_TAG="BlacklightMilitaryFieldDimension";
    public static final String MODE_TAG="BlacklightMilitaryHomeMode",ORIGIN_DIMENSION_TAG="BlacklightMilitaryOriginDimension";
    private static final String HOLD_TAG="BlacklightMilitaryHomeHold";
    private static final Map<ServerLevel,State> LEVELS=new WeakHashMap<>();
    private static final Map<MilitarySoldier,Boolean> GOALS=new WeakHashMap<>();
    private static final class Entry {
        final long joined;final BlockPos birth;long nextSearch;boolean ready;
        Entry(long time,BlockPos position){joined=time;birth=position.immutable();}
    }
    private static final class State {
        final Map<UUID,Entry> entries=new LinkedHashMap<>();long searchTick=-1;int searches;
        boolean searchBudget(long now){if(searchTick!=now){searchTick=now;searches=0;}if(searches>=8)return false;searches++;return true;}
    }
    private MilitaryHomeSystem(){}
    static void joined(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;
        if(!MilitaryCampaignSystem.ambient(soldier))soldier.setPersistenceRequired(); // Existing garrisons retain their persistent identity.
        state(level).entries.putIfAbsent(soldier.getUUID(),new Entry(level.getGameTime(),soldier.blockPosition()));
        if(GOALS.put(soldier,Boolean.TRUE)==null)soldier.goalSelector.addGoal(5,new HomePatrolGoal(soldier));
    }
    static void left(ServerLevel level,MilitarySoldier soldier){var state=LEVELS.get(level);if(state!=null)state.entries.remove(soldier.getUUID());}
    static void unload(ServerLevel level){LEVELS.remove(level);}
    static void stop(){LEVELS.clear();GOALS.clear();}
    private static State state(ServerLevel level){return LEVELS.computeIfAbsent(level,ignored->new State());}
    static void tick(ServerLevel level){
        var state=state(level);long now=level.getGameTime();
        var soldiers=new ArrayList<MilitarySoldier>();
        for(var entity:level.getAllEntities())if(entity instanceof MilitarySoldier soldier&&soldier.isAlive()&&!soldier.isRemoved()){
            state.entries.computeIfAbsent(soldier.getUUID(),ignored->new Entry(now,soldier.blockPosition()));soldiers.add(soldier);
        }
        // Oldest unserved deadline first. Budget rejection never advances that deadline.
        soldiers.sort(Comparator.comparingLong((MilitarySoldier soldier)->state.entries.get(soldier.getUUID()).nextSearch).thenComparing(MilitarySoldier::getUUID));
        for(var soldier:soldiers){
            var entry=state.entries.get(soldier.getUUID());
            if(now-entry.joined<20||BlacklightInfectionSystem.isInfected(soldier))continue;
            if(!entry.ready){entry.ready=true;if(!MilitaryCampaignSystem.ambient(soldier))soldier.setPersistenceRequired();migrateOrigin(level,soldier);}
            update(level,soldier,entry,state,now);
        }
    }
    private static String dimension(ServerLevel level){return level.dimension().location().toString();}
    private static boolean faction(CityMilitaryData.Base base,MilitarySoldier soldier){return base.site.blackwatch()==(soldier.faction()==MilitaryFaction.BLACKWATCH);}
    private static boolean owns(CityMilitaryData.Base base,UUID uuid){for(var id:base.slots)if(uuid.equals(id))return true;return false;}
    private static void migrateOrigin(ServerLevel level,MilitarySoldier soldier){
        var tag=soldier.getPersistentData();if(tag.contains(ORIGIN_DIMENSION_TAG))return;
        String source=tag.getString(CityMilitaryService.BASE_TAG);if(source.isEmpty())return;
        ServerLevel match=null;
        // An old save can already contain a cross-dimension soldier. Match the actual issued UUID,
        // using existing level SavedData only; this never requests a terrain/entity chunk.
        for(var candidate:level.getServer().getAllLevels()){
            var base=CityMilitaryData.get(candidate).bases.get(source);if(base==null||!owns(base,soldier.getUUID()))continue;
            if(match!=null)return; // Ambiguous/corrupt provenance must not be guessed from an ID.
            match=candidate;
        }
        if(match!=null)tag.putString(ORIGIN_DIMENSION_TAG,dimension(match));
    }
    static ServerLevel originLevel(ServerLevel current,MilitarySoldier soldier){
        migrateOrigin(current,soldier);var text=soldier.getPersistentData().getString(ORIGIN_DIMENSION_TAG);
        if(text.isEmpty())return null;
        var id=ResourceLocation.tryParse(text);return id==null?null:current.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,id));
    }
    static void nativeHome(ServerLevel level,MilitarySoldier soldier,CityMilitaryData.Base base){
        var tag=soldier.getPersistentData();tag.putString(ORIGIN_DIMENSION_TAG,dimension(level));
        tag.putString(HOME_BASE_TAG,base.id);tag.putString(HOME_DIMENSION_TAG,dimension(level));tag.putString(MODE_TAG,"auto");
    }
    public static boolean ordered(MilitarySoldier soldier){return soldier.level() instanceof ServerLevel level&&soldier.unitId()!=null&&MilitaryOrganizationSystem.organization(level).effectiveOrder(soldier.unitId()).isPresent();}
    private static void clearHome(MilitarySoldier soldier){var tag=soldier.getPersistentData();tag.remove(HOME_BASE_TAG);tag.remove(HOME_DIMENSION_TAG);tag.remove(HOLD_TAG);}
    private static void fieldAt(MilitarySoldier soldier,BlockPos at){var tag=soldier.getPersistentData();tag.putLong(FIELD_ANCHOR_TAG,at.asLong());tag.putString(FIELD_DIMENSION_TAG,soldier.level().dimension().location().toString());}
    private static void bind(ServerLevel level,MilitarySoldier soldier,CityMilitaryData.Base base){var tag=soldier.getPersistentData();tag.putString(HOME_BASE_TAG,base.id);tag.putString(HOME_DIMENSION_TAG,dimension(level));}
    static Optional<CityMilitaryData.Base> currentBase(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return Optional.empty();var tag=soldier.getPersistentData();
        if(!dimension(level).equals(tag.getString(HOME_DIMENSION_TAG)))return Optional.empty();
        var base=CityMilitaryData.get(level).bases.get(tag.getString(HOME_BASE_TAG));
        return base!=null&&faction(base,soldier)?Optional.of(base):Optional.empty();
    }
    private static void update(ServerLevel level,MilitarySoldier soldier,Entry entry,State state,long now){
        var tag=soldier.getPersistentData();String dim=dimension(level);
        if(!dim.equals(tag.getString(FIELD_DIMENSION_TAG)))fieldAt(soldier,entry.birth);
        if(!tag.contains(MODE_TAG))tag.putString(MODE_TAG,"auto");
        if(tag.getString(MODE_TAG).equals("field")){clearHome(soldier);return;}
        if(!tag.getString(HOME_BASE_TAG).isEmpty()&&!dim.equals(tag.getString(HOME_DIMENSION_TAG))){clearHome(soldier);fieldAt(soldier,soldier.blockPosition());}
        var home=currentBase(soldier).orElse(null);
        if(home==null&&!tag.getString(HOME_BASE_TAG).isEmpty()){clearHome(soldier);fieldAt(soldier,soldier.blockPosition());}
        if(home!=null){
            if(!home.health.inactive()){if(home.health.operational())tag.remove(HOLD_TAG);else if(!tag.contains(HOLD_TAG))tag.putLong(HOLD_TAG,soldier.blockPosition().asLong());return;} // Unknown/unloaded preserves identity; no destruction inference.
            clearHome(soldier);fieldAt(soldier,soldier.blockPosition());entry.nextSearch=0;
        }
        if(tag.getString(HOME_BASE_TAG).isEmpty()){
            var origin=CityMilitaryData.get(level).bases.get(tag.getString(CityMilitaryService.BASE_TAG));
            String originDimension=tag.getString(ORIGIN_DIMENSION_TAG);
            if(origin!=null&&owns(origin,soldier.getUUID())&&faction(origin,soldier)&&(originDimension.isEmpty()||originDimension.equals(dim))&&!origin.health.inactive()){
                bind(level,soldier,origin);return;
            }
        }
        if(now<entry.nextSearch)return;
        if(!state.searchBudget(now))return;entry.nextSearch=now+100;
        CityMilitaryData.Base nearest=null;double distance=256*256;
        for(var candidate:CityMilitaryData.get(level).bases.values()){
            if(!faction(candidate,soldier)||!candidate.health.operational())continue;
            double squared=soldier.position().distanceToSqr(center(candidate));
            if(squared<=distance&&(nearest==null||squared<distance||candidate.id.compareTo(nearest.id)<0)){distance=squared;nearest=candidate;}
        }
        if(nearest!=null)bind(level,soldier,nearest);
    }
    static Vec3 center(CityMilitaryData.Base base){return new Vec3(base.site.x()+base.site.width()/2.,base.site.floor()+1,base.site.z()+base.site.depth()/2.);}
    private static Vec3 rally(CityMilitaryData.Base base){int[] point=base.site.world(32,base.site.templateDepth()-8);return new Vec3(point[0]+.5,base.site.floor()+1,point[1]+.5);}
    static boolean originalPostApplies(MilitarySoldier soldier,CityMilitaryData.Base origin){
        var tag=soldier.getPersistentData();if("field".equals(tag.getString(MODE_TAG)))return false;
        if(origin.health.inactive()||!owns(origin,soldier.getUUID()))return false;
        var originDimension=tag.getString(ORIGIN_DIMENSION_TAG);if(!originDimension.isEmpty()&&!originDimension.equals(soldier.level().dimension().location().toString()))return false;
        if(tag.getString(HOME_BASE_TAG).isEmpty())return true; // Legacy origin while its delayed migration initializes.
        return tag.getString(HOME_BASE_TAG).equals(origin.id)&&tag.getString(HOME_DIMENSION_TAG).equals(soldier.level().dimension().location().toString());
    }
    private static Optional<Vec3> patrolAnchor(MilitarySoldier soldier){
        var home=currentBase(soldier).orElse(null);if(home!=null&&!home.health.inactive()){
            if(originalPostApplies(soldier,home)&&soldier.getPersistentData().contains(CityMilitaryService.POST_TAG))return Optional.empty();
            // Unknown is not destroyed. Hold a local position until the loaded core is observable again.
            return Optional.of(home.health.operational()?rally(home):Vec3.atBottomCenterOf(BlockPos.of(soldier.getPersistentData().getLong(HOLD_TAG))));
        }
        var tag=soldier.getPersistentData();return tag.contains(FIELD_ANCHOR_TAG)&&tag.getString(FIELD_DIMENSION_TAG).equals(soldier.level().dimension().location().toString())?Optional.of(Vec3.atBottomCenterOf(BlockPos.of(tag.getLong(FIELD_ANCHOR_TAG)))):Optional.empty();
    }
    public static Vec3 territory(MilitarySoldier soldier,Supplier<Vec3> fallback){
        if(ordered(soldier))return fallback.get();var anchor=patrolAnchor(soldier);
        if(anchor.isEmpty())return fallback.get();var point=anchor.get();
        var home=currentBase(soldier).orElse(null);
        // A field patrol always has its saved finite center. A physical relocation can defend
        // its declared departure-to-destination corridor, without moving that corridor after pursuit.
        if(home==null||!home.health.operational()||soldier.position().distanceToSqr(point)<=32*32)return point;
        var tag=soldier.getPersistentData();if(!tag.contains(FIELD_ANCHOR_TAG)||!tag.getString(FIELD_DIMENSION_TAG).equals(soldier.level().dimension().location().toString()))return point;
        var departure=Vec3.atBottomCenterOf(BlockPos.of(tag.getLong(FIELD_ANCHOR_TAG)));var segment=point.subtract(departure);
        double length=segment.lengthSqr();if(length<1)return point;
        double along=Math.max(0,Math.min(1,soldier.position().subtract(departure).dot(segment)/length));
        return departure.add(segment.scale(along));
    }
    public static void chooseField(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;joined(soldier);soldier.setPersistenceRequired();migrateOrigin(level,soldier);
        soldier.getPersistentData().putString(MODE_TAG,"field");clearHome(soldier);fieldAt(soldier,soldier.blockPosition());
    }
    public static void chooseAutomatic(MilitarySoldier soldier){
        if(!(soldier.level() instanceof ServerLevel level))return;joined(soldier);soldier.setPersistenceRequired();migrateOrigin(level,soldier);
        soldier.getPersistentData().putString(MODE_TAG,"auto");clearHome(soldier);fieldAt(soldier,soldier.blockPosition());
        var entry=state(level).entries.get(soldier.getUUID());entry.ready=true;entry.nextSearch=0;update(level,soldier,entry,state(level),level.getGameTime());
    }
    public static String describe(MilitarySoldier soldier){
        var tag=soldier.getPersistentData();var base=currentBase(soldier).orElse(null);
        return soldier.getName().getString()+" ["+soldier.getUUID()+"] mode="+("field".equals(tag.getString(MODE_TAG))?"FIELD":"AUTO")
            +" home="+(base==null?"FIELD_OR_PENDING":base.id+"/"+base.health.label())+" dimension="+soldier.level().dimension().location()
            +" anchor="+(tag.contains(FIELD_ANCHOR_TAG)?BlockPos.of(tag.getLong(FIELD_ANCHOR_TAG)):"PENDING")
            +" origin="+tag.getString(CityMilitaryService.BASE_TAG)+"@"+tag.getString(ORIGIN_DIMENSION_TAG)+" order="+(ordered(soldier)?"EXPLICIT":"DEFAULT");
    }
    static void commands(com.mojang.brigadier.CommandDispatcher<net.minecraft.commands.CommandSourceStack> dispatcher){
        var home=Commands.literal("home").requires(source->source.hasPermission(2));
        var selected=Commands.argument("entities",EntityArgument.entities());
        for(String action:List.of("status","auto","field"))selected.then(Commands.literal(action).executes(context->{
            var entities=EntityArgument.getEntities(context,"entities");
            if(entities.stream().anyMatch(entity->!(entity instanceof MilitarySoldier)||entity.level()!=context.getSource().getLevel()))return 0;
            for(var entity:entities){var soldier=(MilitarySoldier)entity;if(action.equals("auto"))chooseAutomatic(soldier);else if(action.equals("field"))chooseField(soldier);context.getSource().sendSuccess(()->Component.literal(describe(soldier)),false);}return entities.size();
        }));
        dispatcher.register(Commands.literal("blacklight").then(Commands.literal("military").then(home.then(selected))));
    }
    private static final class HomePatrolGoal extends Goal {
        private final MilitarySoldier soldier;private Vec3 destination;private long nextPatrol;private int ticks;private Deque<Vec3> route=new ArrayDeque<>();
        HomePatrolGoal(MilitarySoldier soldier){this.soldier=soldier;setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){return soldier.tacticsAvailable()&&soldier.getTarget()==null&&!ordered(soldier)&&!MilitaryCampaignSystem.active(soldier)&&patrolAnchor(soldier).isPresent();}
        @Override public boolean canContinueToUse(){return canUse();}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void start(){destination=null;route.clear();ticks=0;}
        @Override public void tick(){
            var anchor=patrolAnchor(soldier).orElse(null);if(anchor==null)return;
            var home=currentBase(soldier).orElse(null);if(home!=null&&!home.health.operational()){soldier.getNavigation().stop();return;}
            long now=soldier.level().getGameTime();
            if(soldier.position().distanceToSqr(anchor)>12*12){setDestination(anchor);}
            else if((destination==null||soldier.position().distanceToSqr(destination)<3)&&now>=nextPatrol){
                nextPatrol=now+60;Vec3 point=null;
                for(int attempt=0;attempt<8;attempt++){int x=soldier.getRandom().nextInt(25)-12,z=soldier.getRandom().nextInt(25)-12;if(x*x+z*z>144||x*x+z*z<4)continue;var candidate=anchor.add(x,0,z);if(soldier.level().hasChunkAt(BlockPos.containing(candidate))){point=candidate;break;}}
                if(point!=null)setDestination(point);
            }
            if(destination!=null&&ticks++%20==0){if(route.isEmpty())route=CityMilitaryService.route(soldier,destination);CityMilitaryService.follow(soldier,route,.85);}
        }
        private void setDestination(Vec3 point){if(destination==null||destination.distanceToSqr(point)>1){destination=point;route=CityMilitaryService.route(soldier,point);}}
        @Override public void stop(){soldier.getNavigation().stop();destination=null;route.clear();}
    }
}
