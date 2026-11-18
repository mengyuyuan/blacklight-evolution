package com.codex.blacklight.client;

import com.codex.blacklight.entity.*;
import com.codex.blacklight.military.*;
import com.codex.blacklight.registry.ModEntities;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;

/** Native integrated-server exercise: original airborne troops walk to cover against real arrow entities. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class MilitaryCoverVisual {
    private static Path output;private static boolean initialized,done;private static int clientTicks,labelAge;
    private static volatile int craftId=-1;private static volatile boolean ground;private static volatile String error,evidence,label="";
    private static String lastLabel="";private static final Set<String> captured=new TreeSet<>(),sounds=new TreeSet<>();
    // Mission state below belongs only to the integrated server.
    private static BlackwatchHelicopter craft;private static Skeleton enemy;private static final List<MilitarySoldier> crew=new ArrayList<>(),troops=new ArrayList<>();
    private static final Map<Long,Boolean> forced=new HashMap<>();private static final Map<UUID,Vec3> landed=new HashMap<>();private static final Map<UUID,Integer> openingAmmo=new HashMap<>();
    private static final Set<UUID> sheltered=new HashSet<>(),hit=new HashSet<>(),returnedFire=new HashSet<>();
    private static final JsonArray frames=new JsonArray();private static long lastTick=Long.MIN_VALUE;private static int setupTicks,age,allShelteredAt=-1,wallRemovedAt=-1,completeAt=-1,arrows;
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
    private static void wall(ServerLevel level,boolean present){for(int z=-11;z<=10;z++)if(z<=-6||z>=6)for(int y=81;y<=83;y++)level.setBlock(new BlockPos(-4,y,z),(present?Blocks.STONE_BRICKS:Blocks.AIR).defaultBlockState(),2);}
    /** Count both real carried magazines and each finite reserve once, so switching guns cannot fake a shot. */
    private static int ammo(MilitarySoldier s){
        int total=0;var reserves=new HashSet<String>();
        for(var stack:List.of(s.getMainHandItem(),s.getOffhandItem()))if(stack.getItem() instanceof com.codex.blacklight.firearms.FirearmItem gun){
            total+=com.codex.blacklight.firearms.FirearmItem.state(stack,0).magazine();
            if(reserves.add(gun.definition().ammoId()))total+=s.reserveAmmo(gun.definition().ammoId());
        }return total;
    }
    private static void prepare(ServerLevel level){
        for(int cx=-3;cx<=3;cx++)for(int cz=-3;cz<=3;cz++){long key=ChunkPos.asLong(cx,cz);forced.put(key,level.getForcedChunks().contains(key));level.setChunkForced(cx,cz,true);}
        for(var old:level.getEntitiesOfClass(Entity.class,new AABB(-48,75,-48,48,140,48),e->!(e instanceof net.minecraft.world.entity.player.Player)))old.discard();
        for(int x=-32;x<=32;x++)for(int z=-32;z<=32;z++){
            level.setBlock(new BlockPos(x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            for(int y=81;y<=135;y++){var p=new BlockPos(x,y,z);if(!level.isEmptyBlock(p))level.setBlock(p,Blocks.AIR.defaultBlockState(),2);}
        }
        craft=ModEntities.BLACKWATCH_HELICOPTER.get().create(level);craft.setPos(-24,97,0);level.addFreshEntity(craft);craftId=craft.getId();
        for(int i=0;i<6;i++){
            var s=ModEntities.BLACKWATCH_SOLDIER.get().create(level);s.setPos(craft.position());s.setAutomaticUnitJoin(false);s.setPersistenceRequired();s.setNoAi(i>=2);s.getPersistentData().putBoolean("HelicopterTroop",i>=2);s.initializeNativeLoadout("cover-native:"+i);
            if(i>=2){s.getAttribute(Attributes.MAX_HEALTH).setBaseValue(80);s.setHealth(80);s.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);troops.add(s);}level.addFreshEntity(s);crew.add(s);require(s.startRiding(craft,true),"actual aircraft crew boarding");
        }
    }
    private static void advance(ServerLevel level){
        if(error!=null||evidence!=null||lastTick==level.getGameTime())return;lastTick=level.getGameTime();setupTicks++;
        if(setupTicks==30){for(var old:level.getEntitiesOfClass(Entity.class,new AABB(-48,75,-48,48,140,48),e->!(e instanceof net.minecraft.world.entity.player.Player)&&e!=craft&&!crew.contains(e)))old.discard();}
        if(setupTicks==40)require(craft.deployTo(new Vec3(-8,81,0)),"supported native delivery");
        require(setupTicks<2000,"native delivery/cover timeout");
        if(!ground){
            label="AIR_"+craft.phase().name();
            if(craft.getPassengers().size()==2&&troops.stream().noneMatch(Entity::isPassenger)&&craft.getY()>86){
                wall(level,true);enemy=EntityType.SKELETON.create(level);enemy.setPos(10,81,0);enemy.setNoAi(true);enemy.setPersistenceRequired();enemy.getAttribute(Attributes.MAX_HEALTH).setBaseValue(2000);enemy.setHealth(2000);enemy.setItemSlot(EquipmentSlot.HEAD,new ItemStack(Items.IRON_HELMET));enemy.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BOW));level.addFreshEntity(enemy);
                for(var s:troops)landed.put(s.getUUID(),s.position());ground=true;age=0;
            }return;
        }
        age++;require(troops.stream().allMatch(s->s.isAlive()&&!s.isPassenger()&&landed.containsKey(s.getUUID())),"original delivered soldiers remain alive and independent");require(enemy.isAlive(),"fixture attacker survives the measured return fire");
        if(age==14)troops.forEach(s->s.setNoAi(false));
        if(age>=14&&wallRemovedAt<0&&age%12==2||wallRemovedAt>=0&&(age-wallRemovedAt==2||age-wallRemovedAt==14)){
            for(var s:troops){enemy.performRangedAttack(s,1);arrows++;}
        }
        var row=new JsonObject();row.addProperty("tick",age);row.addProperty("worldTick",level.getGameTime());var members=new JsonArray();var destinations=new HashSet<BlockPos>();
        for(var s:troops){
            if(s.getHealth()<80&&s.getLastHurtByMob()==enemy)hit.add(s.getUUID());
            if(s.cover().arrived()&&!s.hasLineOfSight(enemy)&&s.position().distanceToSqr(landed.get(s.getUUID()))>1)sheltered.add(s.getUUID());
            if(wallRemovedAt>=0&&ammo(s)<openingAmmo.get(s.getUUID()))returnedFire.add(s.getUUID());
            var at=new JsonObject();at.addProperty("id",s.getUUID().toString());at.addProperty("x",s.getX());at.addProperty("y",s.getY());at.addProperty("z",s.getZ());at.addProperty("health",s.getHealth());at.addProperty("ammunition",ammo(s));at.addProperty("phase",s.phase());at.addProperty("suppressed",s.getPersistentData().getLong("SuppressedUntil")>level.getGameTime());at.addProperty("arrived",s.cover().arrived());at.addProperty("visible",s.hasLineOfSight(enemy));
            var goal=s.cover().destination();if(goal!=null){require(destinations.add(goal),"two active shelter intents share one cell");at.addProperty("goal",goal.toShortString());}members.add(at);
        }
        row.add("members",members);row.addProperty("enemyHealth",enemy.getHealth());row.addProperty("everSheltered",sheltered.size());row.addProperty("hitByRealArrows",hit.size());row.addProperty("returnedFire",returnedFire.size());row.addProperty("arrows",arrows);frames.add(row);
        if(sheltered.size()==4&&allShelteredAt<0)allShelteredAt=age;
        require(age<=340||sheltered.size()==4,"not all actual reinforcements reached cover: "+row);
        if(allShelteredAt>=0&&age-allShelteredAt>=20&&wallRemovedAt<0){wall(level,false);wallRemovedAt=age;for(var s:troops)openingAmmo.put(s.getUUID(),ammo(s));}
        label=age<14?"TROOPS_READY":wallRemovedAt>=0?(returnedFire.size()==4?"RETURN_FIRE":"WALL_REMOVED"):allShelteredAt>=0?"SHELTERED":troops.stream().anyMatch(s->s.cover().destination()!=null)?"SEEKING_COVER":"ARROWS";
        if(returnedFire.size()==4&&completeAt<0)completeAt=age;
        require(wallRemovedAt<0||age-wallRemovedAt<220,"not every soldier resumed real finite-ammunition fire: "+row);
        if(completeAt>=0&&age-completeAt>=20){
            require(hit.size()==4&&sheltered.size()==4,"every original troop actually hit by arrows and physically sheltered");var budget=MilitaryFormationSystem.metrics(level);require(budget.peakPlans()<=8&&budget.shortestInterval()>=10,"shared path budget exceeded: "+budget);
            var report=new JsonObject();report.addProperty("observedServerTicks",age);report.addProperty("arrowsLaunched",arrows);report.addProperty("shelteredOriginalTroops",sheltered.size());report.addProperty("hitOriginalTroops",hit.size());report.addProperty("originalTroopsReturningFire",returnedFire.size());report.addProperty("allShelteredAt",allShelteredAt);report.addProperty("wallRemovedAt",wallRemovedAt);report.addProperty("returnFireAt",completeAt);report.addProperty("peakPathPlans",budget.peakPlans());report.addProperty("shortestPathInterval",budget.shortestInterval());var ids=new JsonArray();crew.forEach(s->ids.add(s.getUUID().toString()));report.add("originalCrew",ids);report.add("frames",frames);
            craft.discard();crew.forEach(Entity::discard);enemy.discard();for(var arrow:level.getEntitiesOfClass(net.minecraft.world.entity.projectile.AbstractArrow.class,new AABB(-48,75,-48,48,115,48)))arrow.discard();forced.forEach((key,value)->{var c=new ChunkPos(key);level.setChunkForced(c.x,c.z,value);});
            evidence=new GsonBuilder().setPrettyPrinting().create().toJson(report);
        }
    }
    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent e){if(initialized&&(e.getName().startsWith("vehicle.helicopter")||e.getName().startsWith("firearm.")||e.getName().equals("entity.skeleton.shoot")||e.getName().startsWith("entity.arrow.")||e.getName().equals("block.stone.step")))sounds.add(e.getName());}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){ if(event.phase!=TickEvent.Phase.END)return;
        if(done||System.getProperty("blacklight.test.militaryCover","").isBlank())return;var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();if(mc.level==null||mc.player==null||server==null)return;
        try{
            require(mc.gameDirectory.toPath().toAbsolutePath().toString().replace('\\','/').startsWith("D:/CodexCache/creature-combat-qa/")&&server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals("Creature Skills QA Final"),"disposable D: native QA world required");
            if(error!=null)throw new IllegalStateException(error);
            if(!initialized){
                output=Path.of(System.getProperty("blacklight.test.militaryCover")).toAbsolutePath().normalize();require(output.startsWith(Path.of("D:/CodexCache")),"D: evidence required");Files.createDirectories(output);Files.writeString(output.resolve("status.txt"),"RUNNING");initialized=true;
                mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.fov().set(65);mc.options.framerateLimit().set(30);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);var id=mc.player.getUUID();
                server.execute(()->{try{var level=server.overworld();level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);level.setWeatherParameters(12000,0,false,false);var p=server.getPlayerList().getPlayer(id);p.setGameMode(GameType.SPECTATOR);p.connection.teleport(-13,104,14,0,20);prepare(level);}catch(Exception failure){error=failure.toString();}});return;
            }
            clientTicks++;require(clientTicks<2400,"client capture timed out");mc.getToasts().clear();mc.mouseHandler.releaseMouse();for(var key:mc.options.keyMappings)key.setDown(false);
            if(evidence!=null){
                Files.writeString(output.resolve("evidence.json"),evidence);Files.writeString(output.resolve("sounds.json"),new Gson().toJson(sounds));
                require(captured.containsAll(List.of("AIR_APPROACH","AIR_UNLOAD","TROOPS_READY","ARROWS","SEEKING_COVER","SHELTERED","WALL_REMOVED","RETURN_FIRE")),"missing native frames "+captured);
                require(sounds.containsAll(List.of("entity.skeleton.shoot","firearm.rifle","vehicle.helicopter_door")),"missing live sound cues "+sounds);Files.writeString(output.resolve("status.txt"),"PASS: four original helicopter troops hit by real arrows, walking into separate cover, then returning real finite-ammunition fire after wall removal; visual review required");done=true;mc.stop();return;
            }
            if(!label.equals(lastLabel)){lastLabel=label;labelAge=0;}else labelAge++;
            Vec3 camera=new Vec3(6,89,4),focus=new Vec3(-6,82,0);if(!ground&&mc.level.getEntity(craftId) instanceof BlackwatchHelicopter c){camera=c.position().add(11,6,14);focus=c.position().add(0,1.6,0);}
            var look=focus.subtract(camera.add(0,mc.player.getEyeHeight(),0));float yaw=(float)Math.toDegrees(Math.atan2(-look.x,look.z)),pitch=(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance()));mc.player.setPos(camera);mc.player.setYRot(yaw);mc.player.setXRot(pitch);mc.player.yRotO=yaw;mc.player.xRotO=pitch;
            var id=mc.player.getUUID();var at=camera;server.execute(()->{try{var p=server.getPlayerList().getPlayer(id);if(p!=null){p.setPos(at);p.setYRot(yaw);p.setXRot(pitch);}advance(server.overworld());}catch(Exception failure){error=failure.toString();}});
        }catch(Exception failure){done=true;failure.printStackTrace();try{if(output!=null){Files.writeString(output.resolve("status.txt"),"FAIL: "+failure);try(var shot=Screenshot.takeScreenshot(mc.getMainRenderTarget())){shot.writeToFile(output.resolve("FAILED.png"));}}}catch(Exception ignored){}mc.stop();}
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event){ if(event.phase!=TickEvent.Phase.END)return;if(!initialized||done||labelAge<3||lastLabel.isEmpty()||captured.contains(lastLabel))return;try(var shot=Screenshot.takeScreenshot(Minecraft.getInstance().getMainRenderTarget())){shot.writeToFile(output.resolve(lastLabel+".png"));captured.add(lastLabel);}catch(Exception failure){error=failure.toString();}}
}
