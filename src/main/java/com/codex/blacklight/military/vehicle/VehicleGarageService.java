package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.city.MilitaryBaseAccess;
import com.codex.blacklight.city.VehicleGaragePiece;
import com.codex.blacklight.entity.BlackwatchSquadSystem;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFaction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Server-only garage operations; entry points are called by the platform lifecycle/commands. */
public final class VehicleGarageService {
    public record Preview(boolean allowed, String reason, UUID token, BoundingBox bounds) { }
    public record GarageView(String id, String baseId, MilitaryFaction faction, BlockPos position) { }
    private record Pending(String base, VehicleGarageData.Site site, long expires) { }
    private static final Map<ServerLevel,Map<UUID,Pending>> PREVIEWS=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<String,Recruitment>> RECRUITS=new WeakHashMap<>();
    private static final Map<ServerLevel,Integer> CURSORS=new WeakHashMap<>();
    private static final String KEY="blacklight_vehicle_garages";
    private record Bay(Vec3 center,BlockPos container,AABB repair) { }
    private static final Map<String,Bay> LAYOUT=loadBlueprint();
    private static Map<String,Bay> loadBlueprint() {
        try(var stream=VehicleGarageService.class.getResourceAsStream("/data/blacklight/military/vehicle_garages.json")) {
            if(stream==null)return Map.of();
            var root=com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),StandardCharsets.UTF_8)).getAsJsonObject();
            if(root.get("schemaVersion").getAsInt()!=1)return Map.of();
            Map<String,Bay> result=null;var factions=new HashSet<String>();
            for(var entry:root.getAsJsonArray("garages")) {
                var g=entry.getAsJsonObject();String faction=g.get("faction").getAsString();if(!factions.add(faction) || !g.get("template").getAsString().equals("blacklight:vehicle_garage_"+faction))return Map.of();
                var size=g.getAsJsonArray("size");if(size.size()!=3 || size.get(0).getAsInt()!=31 || size.get(1).getAsInt()!=10 || size.get(2).getAsInt()!=27)return Map.of();
                var bays=new LinkedHashMap<String,Bay>();
                for(var raw:g.getAsJsonArray("bays")) {
                    var b=raw.getAsJsonObject();String chassis=b.get("chassis").getAsString();
                    var c=b.getAsJsonArray("center");var p=b.getAsJsonArray("container");var a=b.getAsJsonArray("repairBounds");
                    if(c.size()!=3 || p.size()!=3 || a.size()!=6 || !VehicleGarageData.CHASSIS.contains(chassis) || bays.containsKey(chassis))return Map.of();
                    double[] values=new double[6];for(int i=0;i<6;i++){values[i]=a.get(i).getAsDouble();if(!Double.isFinite(values[i]))return Map.of();}
                    var bounds=new AABB(values[0],values[1],values[2],values[3],values[4],values[5]);
                    var center=new Vec3(c.get(0).getAsDouble(),c.get(1).getAsDouble(),c.get(2).getAsDouble());var box=new BlockPos(p.get(0).getAsInt(),p.get(1).getAsInt(),p.get(2).getAsInt());
                    var extent=new AABB(0,0,0,31,10,27);
                    if(!extent.contains(center) || !extent.contains(Vec3.atCenterOf(box)) || !contains(extent,bounds) || !bounds.contains(center) || bounds.contains(Vec3.atCenterOf(box)) || bounds.getSize()<=0)return Map.of();
                    bays.put(chassis,new Bay(center,box,bounds));
                }
                if(!bays.keySet().equals(Set.copyOf(VehicleGarageData.CHASSIS)) || result!=null&&!result.equals(bays))return Map.of();
                result=Map.copyOf(bays);
            }
            return factions.equals(Set.of("military","blackwatch")) && result!=null?result:Map.of();
        } catch(java.io.IOException | RuntimeException invalid) { return Map.of(); }
    }
    private static Storage storage(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(Storage::new,Storage::load),KEY);
    }
    private static boolean serverThread(ServerLevel level) { return level!=null && level.getServer().isSameThread(); }
    public static Preview preview(ServerLevel level, String baseId, BlockPos origin, int rotation) {
        if(!serverThread(level) || origin==null)return new Preview(false,"SERVER_THREAD_REQUIRED",null,null);
        VehicleGarageData.Site site;
        try { site=new VehicleGarageData.Site(origin.getX(),origin.getY(),origin.getZ(),rotation); }
        catch(IllegalArgumentException invalid) { return new Preview(false,"INVALID_SITE",null,null); }
        var box=VehicleGaragePiece.bounds(site);String reason=survey(level,baseId,site);
        if(reason!=null)return new Preview(false,reason,null,box);
        var pending=PREVIEWS.computeIfAbsent(level,key->new LinkedHashMap<>());
        pending.values().removeIf(p->p.expires()<level.getGameTime());
        if(pending.size()>=64)pending.remove(pending.keySet().iterator().next());
        var token=UUID.randomUUID();pending.put(token,new Pending(baseId,site,level.getGameTime()+1200));
        return new Preview(true,"READY_31x27_PLUS_9_BLOCK_EXIT_APRON",token,box);
    }
    public static String attach(ServerLevel level, String baseId, BlockPos origin, int rotation, UUID token) {
        if(!serverThread(level) || origin==null || token==null)return "";
        var pending=PREVIEWS.getOrDefault(level,Map.of()).get(token);
        if(pending==null || pending.expires()<level.getGameTime() || !Objects.equals(baseId,pending.base()))return "";
        var site=pending.site();
        if(site.x()!=origin.getX() || site.floor()!=origin.getY() || site.z()!=origin.getZ() || site.rotation()!=rotation)return "";
        PREVIEWS.get(level).remove(token); // Confirmation is single-use, including a changed footprint.
        if(survey(level,baseId,site)!=null)return "";
        var base=MilitaryBaseAccess.find(level,baseId).orElseThrow();
        var template=level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("blacklight",VehicleGaragePiece.templateName(base.faction())));
        var size=template.getSize();if(size.getX()!=31 || size.getY()!=10 || size.getZ()!=27)return "";
        var at=VehicleGaragePiece.origin(site);
        if(!template.placeInWorld(level,at,at,new StructurePlaceSettings().setRotation(Rotation.values()[rotation]).setIgnoreEntities(true),level.random,2))return "";
        return register(level,baseId,base.faction(),site);
    }
    /** The confirmed token already fixes the dimension, base and surveyed footprint. */
    public static String attach(ServerLevel level,UUID token) {
        if(!serverThread(level)||token==null)return "";
        var pending=PREVIEWS.getOrDefault(level,Map.of()).get(token);if(pending==null)return "";
        var site=pending.site();return attach(level,pending.base(),new BlockPos(site.x(),site.floor(),site.z()),site.rotation(),token);
    }
    /** Trusted entry for a discovered VehicleGaragePiece, never a generic nearby chest scan. */
    public static String registerNatural(ServerLevel level, String baseId, VehicleGarageData.Site site) {
        if(!serverThread(level) || site==null || storage(level).disabled || LAYOUT.isEmpty())return "";
        String id=id(level,baseId,site);if(data(level).garage(id).isPresent())return id;
        var base=MilitaryBaseAccess.find(level,baseId).orElse(null);
        if(base==null || !base.operational() || base.bounds().intersects(VehicleGaragePiece.bounds(site)) || !loaded(level,site))return "";
        for(String chassis:VehicleGarageData.CHASSIS)if(!(level.getBlockEntity(container(site,chassis)) instanceof Container))return "";
        return register(level,baseId,base.faction(),site);
    }
    private static String register(ServerLevel level,String base,MilitaryFaction faction,VehicleGarageData.Site site) {
        var saved=storage(level);if(saved.disabled)return "";
        String id=id(level,base,site);
        if(saved.ledger.register(id,base,level.dimension().location().toString(),faction,site))saved.setDirty();
        return saved.ledger.garage(id).isPresent()?id:"";
    }
    private static String id(ServerLevel level,String base,VehicleGarageData.Site site) {
        return "garage:"+UUID.nameUUIDFromBytes((level.dimension().location()+":"+base+":"+site).getBytes(StandardCharsets.UTF_8));
    }
    public static VehicleGarageData data(ServerLevel level) { return storage(level).ledger; }
    private static String survey(ServerLevel level,String baseId,VehicleGarageData.Site site) {
        if(LAYOUT.isEmpty())return "INVALID_GARAGE_BLUEPRINT";
        if(storage(level).disabled)return "INVALID_SAVED_LEDGER";
        if(!canAddGarage(data(level)))return "GARAGE_LEDGER_FULL";
        var base=MilitaryBaseAccess.find(level,baseId).orElse(null);
        if(base==null || !base.loaded() || !base.operational())return "BASE_CORE_UNAVAILABLE";
        var bounds=VehicleGaragePiece.bounds(site);
        if(base.bounds().intersects(bounds))return "ORIGINAL_BASE_FOOTPRINT";
        if(base.center().distSqr(new BlockPos(site.x(),site.floor(),site.z()))>256*256)return "TOO_FAR_FROM_BASE";
        for(var other:data(level).garages())if(VehicleGaragePiece.bounds(other.site()).intersects(bounds))return "REGISTERED_GARAGE_CONFLICT";
        var area=worldBox(site,0,0,0,31,10,36);
        if(!VehicleKinematics.loaded(level,area) || !level.getWorldBorder().isWithinBounds(area))return "FOOTPRINT_OR_EXIT_UNLOADED";
        // The first implementation accepts level terrain only. No clearing of built plots,
        // no remote terrain loading, and no hidden expansion of the original base bounds.
        for(int x=0;x<31;x++)for(int z=0;z<36;z++) {
            var floor=block(site,x,0,z);var ground=level.getBlockState(floor);
            if(!ground.isFaceSturdy(level,floor,Direction.UP) || !ground.getFluidState().isEmpty() || level.getBlockEntity(floor)!=null)return "UNSUPPORTED_OR_OCCUPIED_FLOOR";
            if(z<27 && !ground.is(BlockTags.BASE_STONE_OVERWORLD) && !ground.is(BlockTags.DIRT) && !ground.is(BlockTags.SAND) && !ground.is(Blocks.GRAVEL) && !ground.is(Blocks.SNOW_BLOCK))return "BUILT_FLOOR_CONFLICT";
            int height=z<27?9:7;
            for(int y=1;y<=height;y++) {
                var at=block(site,x,y,z);var state=level.getBlockState(at);
                if(!state.getFluidState().isEmpty() || level.getBlockEntity(at)!=null || !state.isAir()&&!state.canBeReplaced())return "FOOTPRINT_OR_EXIT_CONFLICT";
            }
        }
        return null;
    }
    private static double bayX(String chassis) { return LAYOUT.get(chassis).center().x; }
    private static Vec3 center(VehicleGarageData.Site site,String chassis) { var c=LAYOUT.get(chassis).center();return world(site,c.x,c.y,c.z); }
    private static Vec3 world(VehicleGarageData.Site site,double x,double y,double z) {
        return switch(site.rotation()) {
            case 1->new Vec3(site.x()+27-z,site.floor()+y,site.z()+x);
            case 2->new Vec3(site.x()+31-x,site.floor()+y,site.z()+27-z);
            case 3->new Vec3(site.x()+z,site.floor()+y,site.z()+31-x);
            default->new Vec3(site.x()+x,site.floor()+y,site.z()+z);
        };
    }
    private static BlockPos block(VehicleGarageData.Site site,int x,int y,int z) {
        var point=world(site,x+.5,y,z+.5);return BlockPos.containing(point);
    }
    private static BlockPos container(VehicleGarageData.Site site,String chassis) { var c=LAYOUT.get(chassis).container();return block(site,c.getX(),c.getY(),c.getZ()); }
    private static AABB worldBox(VehicleGarageData.Site site,double x1,double y1,double z1,double x2,double y2,double z2) {
        var a=world(site,x1,y1,z1);var b=world(site,x2,y2,z2);
        return new AABB(Math.min(a.x,b.x),Math.min(a.y,b.y),Math.min(a.z,b.z),Math.max(a.x,b.x),Math.max(a.y,b.y),Math.max(a.z,b.z));
    }
    private static boolean loaded(ServerLevel level,VehicleGarageData.Site site) { return VehicleKinematics.loaded(level,worldBox(site,0,0,0,31,10,27)); }
    private static AABB repairBay(VehicleGarageData.Site site,String chassis) {
        var b=LAYOUT.get(chassis).repair();return worldBox(site,b.minX,b.minY,b.minZ,b.maxX,b.maxY,b.maxZ);
    }
    private static boolean contains(AABB outer,AABB inner) {
        return outer.minX<=inner.minX+1e-6 && outer.minY<=inner.minY+1e-6 && outer.minZ<=inner.minZ+1e-6
                && outer.maxX+1e-6>=inner.maxX && outer.maxY+1e-6>=inner.maxY && outer.maxZ+1e-6>=inner.maxZ;
    }
    private static boolean valid(ServerLevel level,MilitaryVehicle vehicle,VehicleGarageData.Garage garage) {
        if(garage==null || vehicle==null || !LAYOUT.containsKey(vehicle.chassisId()) || vehicle.level()!=level || vehicle.isRemoved() || vehicle.health()<=0 || vehicle.faction()!=garage.faction()
                || !garage.dimension().equals(level.dimension().location().toString()) || !loaded(level,garage.site()))return false;
        var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
        return base!=null && base.operational() && base.faction()==garage.faction();
    }
    /** Moves actual matching Items only after checking the entire real hull is parked in its bay. */
    public static int transfer(ServerLevel level, MilitaryVehicle vehicle, String garageId) {
        if(!parked(level,vehicle,garageId))return 0;
        var garage=data(level).garage(garageId).orElse(null);
        var at=container(garage.site(),vehicle.chassisId());
        if(!level.hasChunkAt(at) || !(level.getBlockEntity(at) instanceof Container inventory))return 0;
        int count=0;
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            var stack=inventory.getItem(slot);if(stack.isEmpty())continue;
            int taken=accept(vehicle,stack);
            if(taken>0){stack.shrink(taken);count+=taken;inventory.setChanged();}
        }
        if(count>0)vehicle.syncVehicleState();return count;
    }
    public static boolean parked(ServerLevel level,MilitaryVehicle vehicle,String garageId) {
        if(!serverThread(level)||storage(level).disabled)return false;
        var garage=data(level).garage(garageId).orElse(null);if(!valid(level,vehicle,garage))return false;
        double speed=vehicle.getDeltaMovement().lengthSqr();if(!Double.isFinite(speed)||speed>.0004)return false;
        var e=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch()).bounds(new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ()),vehicle.getYRot());
        return contains(repairBay(garage.site(),vehicle.chassisId()),new AABB(e.minX(),e.minY(),e.minZ(),e.maxX(),e.maxY(),e.maxZ()))&&VehicleKinematics.placementReady(vehicle);
    }
    static int needed(MilitaryVehicle vehicle,net.minecraft.world.item.ItemStack stack) {
        var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return stack.isEmpty()||!id.getNamespace().equals("blacklight")?0:VehicleSupplyRules.deficit(vehicle.definition(),vehicle.inventory(),vehicle.health(),id.getPath());
    }
    static int accept(MilitaryVehicle vehicle,net.minecraft.world.item.ItemStack stack) {
        int offered=Math.min(stack.getCount(),needed(vehicle,stack));if(offered==0)return 0;
        String kind=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if(!kind.equals("vehicle_repair_parts"))return vehicle.inventory().addAmmunition(kind,offered);
        int count=0;while(count<offered&&vehicle.repair(20)>0)count++;return count;
    }
    static boolean automatic(MilitaryVehicle vehicle) {
        return vehicle!=null&&!vehicle.nativeOrigin().isEmpty()&&vehicle.nativeDimension().equals(vehicle.level().dimension().location().toString())
                &&vehicle.playerOwner()==null&&!vehicle.controlLocked()&&vehicle.getPassengers().stream().noneMatch(Player.class::isInstance);
    }
    static BlockPos supplyBox(ServerLevel level,MilitaryVehicle vehicle,String garageId) {
        var garage=data(level).garage(garageId).orElse(null);return valid(level,vehicle,garage)?container(garage.site(),vehicle.chassisId()):null;
    }
    public static boolean requestService(ServerLevel level,MilitaryVehicle vehicle,String garageId) {
        if(!serverThread(level)||!automatic(vehicle)||!vehicle.currentHome().equals(garageId)||!VehicleFleetSystem.serviceReady(level,vehicle)||!parked(level,vehicle,garageId))return false;
        var reserved=new HashSet<UUID>();RECRUITS.getOrDefault(level,Map.of()).values().forEach(r->reserved.addAll(r.people));
        var garage=data(level).garage(garageId).orElseThrow();
        return VehicleLogisticsService.request(level,vehicle,garage,container(garage.site(),vehicle.chassisId()),reserved);
    }
    static List<Vec3> garageApproach(ServerLevel level,MilitarySoldier soldier,VehicleGarageData.Garage garage,String chassis){
        var apron=world(garage.site(),bayX(chassis),1,32.5);var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
        // Once outside the original base, approach the actual garage entrance.
        // The personnel home router otherwise sends a blocked outside route back through the base gate.
        return base!=null&&!base.bounds().isInside(soldier.blockPosition())?List.of(apron):MilitaryBaseAccess.approach(soldier,apron);
    }
    public static String diagnostic(ServerLevel level,MilitaryVehicle vehicle,String garageId){
        var garage=data(level).garage(garageId).orElse(null);if(garage==null)return "GARAGE_MISSING "+garageId;
        var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
        var e=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch()).bounds(new VehicleDefinition.Vec3(vehicle.getX(),vehicle.getY(),vehicle.getZ()),vehicle.getYRot());
        var hull=new AABB(e.minX(),e.minY(),e.minZ(),e.maxX(),e.maxY(),e.maxZ());var bay=repairBay(garage.site(),vehicle.chassisId());
        var at=container(garage.site(),vehicle.chassisId());var items=new ArrayList<String>();
        if(level.hasChunkAt(at)&&level.getBlockEntity(at) instanceof Container box)for(int slot=0;slot<Math.min(64,box.getContainerSize());slot++){
            var stack=box.getItem(slot);if(!stack.isEmpty())items.add(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())+"="+stack.getCount()+"/need="+needed(vehicle,stack));
        }
        var workers=MilitaryBaseAccess.idleCrew(level,garage.baseId(),32,Set.of()).stream().limit(6).map(s->s.getUUID()+"@"+s.position()+"/ammoDeficit="+s.ammoDeficit()).toList();
        return "garage="+garageId+" site="+garage.site()+" base="+(base==null?"MISSING":base.loaded()+"/"+base.operational())+" loaded="+loaded(level,garage.site())
                +" valid="+valid(level,vehicle,garage)+" automatic="+automatic(vehicle)+" fleetReady="+VehicleFleetSystem.serviceReady(level,vehicle)+" parked="+parked(level,vehicle,garageId)
                +" yaw="+vehicle.getYRot()+" turret="+vehicle.turretYaw()+" pitch="+vehicle.gunPitch()+" velocity="+vehicle.getDeltaMovement()+" hull="+hull+" bay="+bay+" contains="+contains(bay,hull)
                +" envelope="+VehicleKinematics.checkEnvelope(vehicle,e)+" placement="+VehicleKinematics.placementReady(vehicle)+" box="+at+" items="+items+" idle="+workers
                +" recruits="+RECRUITS.getOrDefault(level,Map.of()).values().stream().filter(r->r.garage.id().equals(garageId)).map(r->r.chassis+":"+r.people).toList();
    }
    public static boolean homeAvailable(ServerLevel level,MilitaryVehicle vehicle,String garageId) {
        return serverThread(level)&&automatic(vehicle)&&valid(level,vehicle,data(level).garage(garageId).orElse(null));
    }
    /** Explicit registration accepts an existing parked vehicle; it never allocates another chassis. */
    public static boolean registerParked(ServerLevel level,MilitaryVehicle vehicle,String garageId) {
        if(!serverThread(level) || storage(level).disabled)return false;
        var garage=data(level).garage(garageId).orElse(null);
        return parked(level,vehicle,garageId) && vehicle.setCurrentHome(garageId);
    }
    private static boolean resources(ServerLevel level,VehicleGarageData.Garage garage,MilitaryVehicle vehicle) {
        if(!(level.getBlockEntity(container(garage.site(),vehicle.chassisId())) instanceof Container box))return false;
        boolean low=VehicleSupplyRules.lowPrimary(vehicle.definition(),vehicle.inventory()),damaged=vehicle.health()<vehicle.maxHealth()*.35;
        for(int slot=0;slot<box.getContainerSize();slot++) {
            var stack=box.getItem(slot);if(stack.isEmpty())continue;
            var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());if(!id.getNamespace().equals("blacklight"))continue;
            String kind=id.getPath();boolean repair=kind.equals("vehicle_repair_parts");
            if(low||damaged){if(needed(vehicle,stack)>0&&(damaged&&repair||low&&VehicleSupplyRules.primaryAmmunition(vehicle.definition()).contains(kind)))return true;}
            else if(repair||vehicle.definition().ammunitionCapacities().containsKey(kind))return true;
        }
        return false;
    }
    public static Optional<GarageView> findHome(ServerLevel level, MilitaryVehicle vehicle) {
        return homeCandidates(level,vehicle).stream().filter(g->road(level,vehicle,data(level).garage(g.id()).orElseThrow())).findFirst();
    }
    /** Metadata-only candidates. Automatic fleets certify one route under their shared planning ticket. */
    public static List<GarageView> homeCandidates(ServerLevel level, MilitaryVehicle vehicle) {
        if(!serverThread(level) || LAYOUT.isEmpty() || vehicle==null || vehicle.level()!=level || vehicle.isRemoved() || vehicle.health()<=0 || vehicle.playerOwner()!=null
                || vehicle.getPassengers().stream().anyMatch(p->p instanceof Player))return List.of();
        return data(level).garages().stream().filter(g->g.faction()==vehicle.faction() && g.dimension().equals(level.dimension().location().toString()))
                .filter(g->vehicle.position().distanceToSqr(center(g.site(),vehicle.chassisId()))<=256*256)
                .sorted(Comparator.comparingDouble(g->vehicle.position().distanceToSqr(center(g.site(),vehicle.chassisId()))))
                .filter(g->valid(level,vehicle,g) && resources(level,g,vehicle)).limit(8)
                .map(g->new GarageView(g.id(),g.baseId(),g.faction(),BlockPos.containing(center(g.site(),vehicle.chassisId())))).toList();
    }
    private static boolean road(ServerLevel level,MilitaryVehicle vehicle,VehicleGarageData.Garage garage) {
        var apron=world(garage.site(),bayX(vehicle.chassisId()),1,32.5);var bay=center(garage.site(),vehicle.chassisId());
        if(vehicle.position().distanceToSqr(apron)>256*256 || Math.abs(vehicle.getY()-apron.y)>.25)return false;
        return lane(level,vehicle,vehicle.position(),apron) && lane(level,vehicle,apron,bay);
    }
    /** Conservative loaded flat-road certification; blocked or unknown routes are rejected. */
    static Optional<BlockPos> patrolDestination(ServerLevel level,MilitaryVehicle vehicle,VehicleGarageData.Garage garage){
        if(garage==null||!automatic(vehicle))return Optional.empty();
        // Start with the actual doorway lane. Reject buildings, water and unknown
        // chunks using the same full-hull sweeps as the garage return path.
        for(double distance:new double[]{72.5,56.5,40.5,32.5}){
            var goal=world(garage.site(),bayX(vehicle.chassisId()),1,distance);
            if(lane(level,vehicle,vehicle.position(),goal))return Optional.of(BlockPos.containing(goal));
        }
        return Optional.empty();
    }
    private static boolean lane(ServerLevel level,MilitaryVehicle vehicle,Vec3 from,Vec3 to) {
        var delta=to.subtract(from);int steps=Math.max(1,(int)Math.ceil(delta.horizontalDistance()/2));
        float yaw=(float)Math.toDegrees(Math.atan2(delta.x,delta.z));var shape=VehicleHullShape.from(vehicle.definition(),vehicle.turretYaw(),vehicle.gunPitch());
        for(int step=0;step<=steps;step++) {
            var p=from.lerp(to,step/(double)steps);var envelope=shape.bounds(new VehicleDefinition.Vec3(p.x,p.y,p.z),yaw);
            if(VehicleKinematics.checkEnvelope(vehicle,envelope)!=VehicleKinematics.StopReason.NONE)return false;
            for(double x:new double[]{-vehicle.definition().width()/2,0,vehicle.definition().width()/2})for(double z:new double[]{-vehicle.definition().length()/2,0,vehicle.definition().length()/2}) {
                var q=VehicleDefinition.rotateYaw(new VehicleDefinition.Vec3(x,0,z),yaw);var floor=BlockPos.containing(p.x+q.x(),p.y-.01,p.z+q.z());
                if(!level.hasChunkAt(floor) || !level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP) || !level.getFluidState(floor).isEmpty())return false;
            }
        }
        return true;
    }
    public static void tick(ServerLevel level) {
        if(!serverThread(level))return;
        VehicleLogisticsService.tick(level);
        if(level.getGameTime()%20!=0 || storage(level).disabled || LAYOUT.isEmpty())return;
        var garages=data(level).garages();if(garages.isEmpty())return;
        var recruits=RECRUITS.computeIfAbsent(level,key->new LinkedHashMap<>());int cursor=CURSORS.getOrDefault(level,0),issued=0;
        for(int work=0;work<Math.min(8,garages.size());work++) {
            var garage=garages.get(Math.floorMod(cursor+work,garages.size()));
            var base=MilitaryBaseAccess.find(level,garage.baseId()).orElse(null);
            if(base==null || !base.operational() || !loaded(level,garage.site())) { cancelGarage(level,garage.id());continue; }
            for(String chassis:VehicleGarageData.CHASSIS) {
                var slot=data(level).garage(garage.id()).orElseThrow().slots().get(chassis);
                if(slot.state()==VehicleGarageData.State.LOST)continue;
                if(slot.state()!=VehicleGarageData.State.UNISSUED) {
                    if(slot.vehicle()!=null && level.getEntity(slot.vehicle()) instanceof MilitaryVehicle vehicle) {
                        if(vehicle.health()<=0){onDestroyed(level,vehicle);continue;}
                        if(slot.state()==VehicleGarageData.State.RESERVED && vehicle.chassisId().equals(chassis) && vehicle.faction()==garage.faction() && vehicle.nativeOrigin().equals(garage.baseId())) {
                            if(data(level).issued(garage.id(),chassis,vehicle.getUUID()))storage(level).setDirty();
                        }
                        if(vehicle.currentHome().equals(garage.id()))requestService(level,vehicle,garage.id());
                    }
                    continue; // Unknown, unloaded, hijacked, removed and lost never reopen a slot.
                }
                if(issued>=2 || loadedNativeVehicles(level)>=12)continue;
                String key=garage.id()+":"+chassis;var recruitment=recruits.get(key);
                if(recruitment==null) {
                    var reserved=new HashSet<UUID>(VehicleLogisticsService.reserved(level));recruits.values().forEach(r->reserved.addAll(r.people));
                    var available=MilitaryBaseAccess.idleCrew(level,garage.baseId(),32,reserved);
                    var positions=new LinkedHashMap<UUID,Vec3>();available.forEach(s->positions.put(s.getUUID(),s.position()));
                    var crew=prioritizeCrew(positions,center(garage.site(),chassis)).stream().limit(chassis.equals("truck")?1:2)
                            .map(id->(MilitarySoldier)level.getEntity(id)).toList();
                    if(crew.size()!=(chassis.equals("truck")?1:2))continue;
                    recruitment=new Recruitment(garage,chassis,crew.stream().map(MilitarySoldier::getUUID).toList(),level.getGameTime()+1200);recruits.put(key,recruitment);
                    for(int index=0;index<crew.size();index++){var goal=new BoardGoal(crew.get(index),recruitment,index);recruitment.goals.put(crew.get(index).getUUID(),goal);crew.get(index).goalSelector.addGoal(2,goal);}
                }
                var eligible=MilitaryBaseAccess.idleCrew(level,garage.baseId(),32,Set.of()).stream().map(MilitarySoldier::getUUID).collect(java.util.stream.Collectors.toSet());
                if(level.getGameTime()>recruitment.expires || !eligible.containsAll(recruitment.people)){cancel(level,key);continue;}
                var center=center(garage.site(),chassis);
                if(recruitment.people.stream().allMatch(id->level.getEntity(id) instanceof MilitarySoldier soldier && soldier.position().distanceToSqr(center)<=9)) {
                    var vehicle=VehicleRegistry.type(garage.faction(),chassis).create(level);if(vehicle==null)continue;
                    vehicle.setPos(center);vehicle.setYRot(-garage.site().rotation()*90);
                    if(!VehicleKinematics.placementReady(vehicle)){vehicle.discard();continue;}
                    if(!data(level).reserve(garage.id(),chassis,vehicle.getUUID())){vehicle.discard();cancel(level,key);continue;}
                    storage(level).setDirty(); // Reservation is durable identity evidence before entity issuance.
                    vehicle.initializeNativeOrigin(garage.baseId(),level.dimension().location().toString());vehicle.setCurrentHome(garage.id());vehicle.inventory().issueInitialLoadout();vehicle.syncVehicleState();
                    boolean added=level.addFreshEntity(vehicle),boarded=added;
                    if(added)for(UUID person:recruitment.people)if(!(level.getEntity(person) instanceof MilitarySoldier soldier) || !soldier.startRiding(vehicle)){boarded=false;break;}
                    if(boarded){data(level).issued(garage.id(),chassis,vehicle.getUUID());storage(level).setDirty();issued++;}
                    else { for(var person:vehicle.getPassengers().toArray(net.minecraft.world.entity.Entity[]::new))person.stopRiding();vehicle.discard(); }
                    cancel(level,key);
                }
            }
        }
        CURSORS.put(level,Math.floorMod(cursor+8,garages.size()));
    }
    private static int loadedNativeVehicles(ServerLevel level) {
        int count=0;
        for(var world:level.getServer().getAllLevels())for(var garage:data(world).garages())for(var slot:garage.slots().values())
            if(slot.vehicle()!=null && world.getEntity(slot.vehicle()) instanceof MilitaryVehicle vehicle && !vehicle.isRemoved() && vehicle.health()>0 && ++count>=12)return count;
        return count;
    }
    private static final class Recruitment {
        final VehicleGarageData.Garage garage;final String chassis;final List<UUID> people;final long expires;final Map<UUID,BoardGoal> goals=new LinkedHashMap<>();
        Recruitment(VehicleGarageData.Garage garage,String chassis,List<UUID> people,long expires){this.garage=garage;this.chassis=chassis;this.people=List.copyOf(people);this.expires=expires;}
    }
    private static void cancelGarage(ServerLevel level,String garage){var keys=new ArrayList<>(RECRUITS.getOrDefault(level,Map.of()).keySet());for(String key:keys)if(key.startsWith(garage+":"))cancel(level,key);}
    private static void cancel(ServerLevel level,String key) {
        var r=RECRUITS.getOrDefault(level,Map.of()).get(key);if(r==null)return;RECRUITS.get(level).remove(key);
        r.goals.forEach((id,goal)->{if(level.getEntity(id) instanceof MilitarySoldier soldier){soldier.goalSelector.removeGoal(goal);soldier.getNavigation().stop();}});
    }
    static boolean canAddGarage(VehicleGarageData ledger) { return ledger.garages().size()<2048; }
    static List<UUID> prioritizeCrew(Map<UUID,Vec3> positions,Vec3 boardingCenter) {
        // Native roster order is not a walking route: roof/tower guards can be
        // earlier than available ground personnel. Prefer the boarding level,
        // then the shorter loaded approach, without moving or replacing anyone.
        return positions.entrySet().stream().filter(e->e.getKey()!=null&&e.getValue()!=null&&Double.isFinite(e.getValue().lengthSqr()))
                .sorted(Comparator.<Map.Entry<UUID,Vec3>>comparingDouble(e->Math.abs(e.getValue().y-boardingCenter.y))
                        .thenComparingDouble(e->e.getValue().distanceToSqr(boardingCenter)).thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey).toList();
    }
    static boolean reachedBoardingWaypoint(Vec3 position,Vec3 waypoint,boolean finalPoint) { return position.distanceToSqr(waypoint)<(finalPoint?.04:1); }
    private static final class BoardGoal extends Goal {
        final MilitarySoldier soldier;final Recruitment recruitment;final Vec3 destination;final Deque<Vec3> route=new ArrayDeque<>();int ticks;
        BoardGoal(MilitarySoldier soldier,Recruitment r,int index){this.soldier=soldier;recruitment=r;var c=LAYOUT.get(r.chassis).center();destination=world(r.garage.site(),c.x+(index==0?2.8:-2.8),c.y,c.z);setFlags(EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){return soldier.level() instanceof ServerLevel level && level.getGameTime()<recruitment.expires && MilitaryBaseAccess.idleCrew(level,recruitment.garage.baseId(),32,Set.of()).stream().anyMatch(s->s==soldier);}
        @Override public void start(){route.clear();route.addAll(garageApproach((ServerLevel)soldier.level(),soldier,recruitment.garage,recruitment.chassis));route.add(destination);ticks=0;}
        @Override public boolean requiresUpdateEveryTick(){return true;}
        @Override public void tick(){
            while(!route.isEmpty()&&reachedBoardingWaypoint(soldier.position(),route.peek(),route.size()==1))route.remove();
            if(route.isEmpty()){soldier.getNavigation().stop();return;}
            if(!soldier.level().hasChunkAt(BlockPos.containing(route.peek())))return;
            if(route.size()==1&&soldier.position().distanceToSqr(route.peek())<1){
                // Path nodes use block centers; finish the last fraction by ordinary
                // collision-aware movement instead of accepting a point outside reach.
                soldier.getNavigation().stop();var point=route.peek();soldier.getMoveControl().setWantedPosition(point.x,point.y,point.z,1);return;
            }
            if(ticks++%10==0)BlackwatchSquadSystem.navigate(soldier,route.peek(),1);
        }
        @Override public void stop(){soldier.getNavigation().stop();}
    }
    public static void onDestroyed(ServerLevel level, MilitaryVehicle vehicle) {
        if(!serverThread(level) || vehicle==null || vehicle.health()>0 && (vehicle.getRemovalReason()==null || !vehicle.getRemovalReason().shouldDestroy()))return;
        ServerLevel origin=level;
        if(!vehicle.nativeDimension().isEmpty()) {
            try { origin=level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION,ResourceLocation.parse(vehicle.nativeDimension()))); }
            catch(IllegalArgumentException invalid){return;}
        }
        if(origin!=null && data(origin).lost(vehicle.getUUID()))storage(origin).setDirty();
    }
    public static List<String> status(ServerLevel level, Vec3 observer) {
        if(storage(level).disabled)return List.of("GARAGES_DISABLED_INVALID_SAVED_LEDGER");
        if(LAYOUT.isEmpty())return List.of("GARAGES_DISABLED_INVALID_BLUEPRINT");
        return data(level).garages().stream().sorted(Comparator.comparingDouble(g->observer.distanceToSqr(g.site().x(),g.site().floor(),g.site().z()))).limit(8)
                .map(g->g.id()+" base="+g.baseId()+" faction="+g.faction().id()+" at="+g.site()+" core="+MilitaryBaseAccess.find(level,g.baseId()).map(v->v.operational()?"ACTIVE":"UNAVAILABLE").orElse("UNKNOWN")+" slots="+g.slots()).toList();
    }
    /** Multi-file world saving is not a database transaction; reserved unknown UUIDs are retained. */
    private static final class Storage extends SavedData {
        VehicleGarageData ledger=new VehicleGarageData();boolean disabled;CompoundTag invalid;
        static Storage load(CompoundTag tag,HolderLookup.Provider registries) {
            var result=new Storage();
            if(!tag.contains("Garages") && !tag.contains("SchemaVersion"))return result; // pre-garage worlds stay empty
            try {
                if(tag.getInt("SchemaVersion")!=1 || !tag.contains("Garages",Tag.TAG_LIST))throw new IllegalArgumentException("Unsupported saved garage schema");
                var raw=tag.get("Garages");if(!(raw instanceof ListTag list) || !list.isEmpty()&&list.getElementType()!=Tag.TAG_COMPOUND || list.size()>2048)throw new IllegalArgumentException("Invalid garage list");
                var garages=new ArrayList<VehicleGarageData.Garage>();
                for(int index=0;index<list.size();index++) {
                    var g=list.getCompound(index);var slots=new LinkedHashMap<String,VehicleGarageData.Slot>();var rawSlots=g.get("Slots");
                    if(!(rawSlots instanceof ListTag savedSlots) || savedSlots.size()!=3 || savedSlots.getElementType()!=Tag.TAG_COMPOUND)throw new IllegalArgumentException("Invalid saved slots");
                    for(int slot=0;slot<savedSlots.size();slot++){var s=savedSlots.getCompound(slot);String chassis=s.getString("Chassis");if(slots.containsKey(chassis) || s.contains("UUID")&&!s.hasUUID("UUID"))throw new IllegalArgumentException("Duplicate slot or malformed UUID");slots.put(chassis,new VehicleGarageData.Slot(chassis,s.hasUUID("UUID")?s.getUUID("UUID"):null,VehicleGarageData.State.valueOf(s.getString("State"))));}
                    for(String field:List.of("X","Y","Z","Turn"))if(!g.contains(field,Tag.TAG_INT))throw new IllegalArgumentException("Missing saved site");
                    garages.add(new VehicleGarageData.Garage(g.getString("Id"),g.getString("BaseId"),g.getString("Dimension"),MilitaryFaction.parse(g.getString("Faction")).orElseThrow(),new VehicleGarageData.Site(g.getInt("X"),g.getInt("Y"),g.getInt("Z"),g.getInt("Turn")),slots));
                }
                result.ledger=VehicleGarageData.restore(new VehicleGarageData.Snapshot(1,garages));
            } catch(RuntimeException corrupt) { result.disabled=true;result.invalid=tag.copy(); }
            return result;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registries) {
            if(disabled)return invalid.copy();
            tag.putInt("SchemaVersion",1);var list=new ListTag();
            for(var garage:ledger.garages()) {
                var g=new CompoundTag();g.putString("Id",garage.id());g.putString("BaseId",garage.baseId());g.putString("Dimension",garage.dimension());g.putString("Faction",garage.faction().id());
                g.putInt("X",garage.site().x());g.putInt("Y",garage.site().floor());g.putInt("Z",garage.site().z());g.putInt("Turn",garage.site().rotation());var slots=new ListTag();
                for(String chassis:VehicleGarageData.CHASSIS){var slot=garage.slots().get(chassis);var s=new CompoundTag();s.putString("Chassis",chassis);s.putString("State",slot.state().name());if(slot.vehicle()!=null)s.putUUID("UUID",slot.vehicle());slots.add(s);}
                g.put("Slots",slots);list.add(g);
            }
            tag.put("Garages",list);return tag;
        }
    }
    private VehicleGarageService() { }
}
