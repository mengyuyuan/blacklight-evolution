package com.codex.blacklight.client;

import com.codex.blacklight.BlacklightNeoMod;
import com.codex.blacklight.combat.*;
import com.codex.blacklight.network.*;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.google.gson.*;
import net.minecraft.client.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Opt-in production-path audit, excluded from release jars. Never opens a user's save. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class RagdollVisualAudit {
    static final String PROPERTY="blacklight.test.ragdollVisual",WORLD="Blacklight Ragdoll QA";
    record Case(String name,PowerForm form,String type,boolean consume,boolean heavy,int variant){
        Case(String name,PowerForm form,String type,boolean consume,boolean heavy){this(name,form,type,consume,heavy,ConsumeVariant.DEFAULT);}
    }
    static final List<Case> CASES=List.of(
            new Case("blade_tissue_host_pig_lethal",PowerForm.BLADE,"blacklight:infected_host",false,true),
            new Case("blade_tissue_host_cow_lethal",PowerForm.BLADE,"blacklight:infected_host",false,true),
            new Case("hammer_tissue_host_pig_survive",PowerForm.HAMMERFISTS,"blacklight:infected_host",false,true),
            new Case("claws_tissue_host_consume",PowerForm.CLAWS,"blacklight:infected_host",true,false),
            new Case("blade_tissue_wilden_lethal",PowerForm.BLADE,"ars_nouveau:wilden_hunter",false,true),
            new Case("claws_tissue_wilden_consume",PowerForm.CLAWS,"ars_nouveau:wilden_hunter",true,false),
            new Case("blade_tissue_drygmy_lethal",PowerForm.BLADE,"ars_nouveau:drygmy",false,true),
            new Case("blade_infected",PowerForm.BLADE,"blacklight:common_infected",false,true),
            new Case("blade_infected_lethal",PowerForm.BLADE,"blacklight:common_infected",false,true),
            new Case("blade_pig",PowerForm.BLADE,"minecraft:pig",false,true),
            new Case("blade_pig_side",PowerForm.BLADE,"minecraft:pig",false,true),
            new Case("blade_pig_combo",PowerForm.BLADE,"minecraft:pig",false,false),
            new Case("blade_pig_close",PowerForm.BLADE,"minecraft:pig",false,true),
            new Case("blade_pig_side_close",PowerForm.BLADE,"minecraft:pig",false,true),
            new Case("blade_pig_offset",PowerForm.BLADE,"minecraft:pig",false,true),
            new Case("blade_skeleton",PowerForm.BLADE,"minecraft:skeleton",false,true),
            new Case("blade_skeleton_close",PowerForm.BLADE,"minecraft:skeleton",false,true),
            new Case("claws_cow",PowerForm.CLAWS,"minecraft:cow",false,false),
            new Case("claws_cow_side",PowerForm.CLAWS,"minecraft:cow",false,false),
            new Case("claws_cow_close",PowerForm.CLAWS,"minecraft:cow",false,false),
            new Case("claws_skeleton",PowerForm.CLAWS,"minecraft:skeleton",false,false),
            new Case("claws_cow_survive",PowerForm.CLAWS,"minecraft:cow",false,false),
            new Case("claws_cow_miss",PowerForm.CLAWS,"minecraft:cow",false,false),
            new Case("hammer_zombie",PowerForm.HAMMERFISTS,"minecraft:zombie",false,true),
            new Case("claws_consume",PowerForm.CLAWS,"minecraft:villager",true,false),
            new Case("blade_consume",PowerForm.BLADE,"minecraft:villager",true,false),
            new Case("claws_cow_consume",PowerForm.CLAWS,"minecraft:cow",true,false),
            new Case("claws_host_consume",PowerForm.CLAWS,"blacklight:infected_host",true,false),
            new Case("claws_host_pig_consume",PowerForm.CLAWS,"blacklight:infected_host",true,false),
            new Case("muscle_consume",PowerForm.MUSCLE_MASS,"minecraft:villager",true,false),
            new Case("whip_consume",PowerForm.WHIPFIST,"minecraft:villager",true,false),
            new Case("whip_cow_consume",PowerForm.WHIPFIST,"minecraft:cow",true,false),
            new Case("whip_wilden_consume",PowerForm.WHIPFIST,"ars_nouveau:wilden_hunter",true,false),
            new Case("claws_head_consume",PowerForm.CLAWS,"minecraft:villager",true,false,ConsumeVariant.CLASSIC),
            new Case("blade_wilden_lethal",PowerForm.BLADE,"ars_nouveau:wilden_hunter",false,true),
            new Case("claws_wilden_consume",PowerForm.CLAWS,"ars_nouveau:wilden_hunter",true,false),
            new Case("blade_drygmy_lethal",PowerForm.BLADE,"ars_nouveau:drygmy",false,true),
            new Case("claws_drygmy_consume",PowerForm.CLAWS,"ars_nouveau:drygmy",true,false))
            .stream().filter(c->!c.type().startsWith("ars_nouveau:")||net.neoforged.fml.ModList.get().isLoaded("ars_nouveau"))
            .filter(c->{var filter=System.getProperty("blacklight.test.ragdollCases","");return filter.isBlank()||Arrays.asList(filter.split(",")).contains(c.name());}).toList();
    static boolean opening,initialized,done,failed;static volatile boolean ready;
    static int index,tick,frame;static volatile int victimId=-1;static volatile float health;static volatile boolean lastChance;
    static long previousFrameNanos;
    static final Map<String,com.codex.blacklight.client.renderer.ConsumeBladeContact.Sample> drawnBlades=new LinkedHashMap<>();
    static Path output;static final JsonArray rows=new JsonArray();
    static final JsonArray checks=new JsonArray();
    static final JsonArray contactTrace=new JsonArray();
    static final Map<Object,Integer> partIds=new IdentityHashMap<>();
    static int partId(Object part){return partIds.computeIfAbsent(part,p->partIds.size()+1);}
    static Mob standingProbe;static Vec3 oldActor,oldVictim;
    static ThreadPoolExecutor writer;static volatile String imageError;static int dropped;
    static final Map<KeyMapping,com.mojang.blaze3d.platform.InputConstants.Key> bindings=new IdentityHashMap<>();
    static boolean active(){return initialized&&!done&&!failed;}
    static Case current(){return CASES.get(index);}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ClientTickEvent.Post event){
        if(failed||System.getProperty(PROPERTY,"").isBlank())return;
        var mc=Minecraft.getInstance();
        if(done){if(writer.isTerminated())mc.stop();return;}
        try{
            if(!opening&&mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen){
                opening=true;
                if(!mc.gameDirectory.toPath().toAbsolutePath().normalize().getFileName().toString().equals("run-ragdoll-qa"))throw new IllegalStateException("Refusing non-QA directory");
                mc.options.pauseOnLostFocus=false;mc.options.renderDistance().set(4);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(30);
                var world=mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
                if(Files.exists(world))throw new IllegalStateException("Existing QA world: launch with quickPlayWorld instead");
                var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,net.minecraft.world.Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                        new net.minecraft.world.level.levelgen.WorldOptions(7711L,false,false),registries->registries.registryOrThrow(net.minecraft.core.registries.Registries.WORLD_PRESET)
                                .getHolderOrThrow(net.minecraft.world.level.levelgen.presets.WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
                return;
            }
            var server=mc.getSingleplayerServer();if(server==null||mc.level==null||mc.player==null)return;
            if(!server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).normalize().getFileName().toString().equals(WORLD))throw new IllegalStateException("Refusing non-QA world");
            if(!initialized){
                if(CASES.isEmpty())throw new IllegalArgumentException("No selected QA cases");
                output=Path.of(System.getProperty(PROPERTY)).toAbsolutePath().normalize();
                if(!output.toString().toLowerCase(Locale.ROOT).startsWith("d:"))throw new IllegalStateException("Output must be on D:");
                Files.createDirectories(output);
                // A manually maximized QA window otherwise captures 4K PNGs
                // faster than the bounded writer can save them. Keep this test
                // fixture's requested resolution; never change the user's client.
                if(mc.getWindow().isFullscreen())mc.getWindow().toggleFullScreen();
                org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),960,640);
                Files.writeString(output.resolve("capture-status.txt"),"Running "+CASES.size()+" cases");
                writer=new ThreadPoolExecutor(2,2,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(24),r->{var t=new Thread(r,"ragdoll-qa-images");t.setDaemon(true);return t;});
                for(var key:List.of(mc.options.keyAttack,mc.options.keyUse)){bindings.put(key,key.getKey());key.setKey(com.mojang.blaze3d.platform.InputConstants.UNKNOWN);key.setDown(false);}
                KeyMapping.resetMapping();mc.options.pauseOnLostFocus=false;mc.options.framerateLimit().set(30);mc.options.hideGui=true;mc.options.fov().set(Math.clamp(Integer.getInteger("blacklight.test.ragdollFov",55),30,80));mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
                mc.setScreen(null);mc.mouseHandler.releaseMouse();mc.getWindow().setTitle("Blacklight Ragdoll QA - frame capture");
                com.codex.blacklight.client.renderer.ConsumeBladeContact.observer=sample->{if(mc.player!=null&&sample.actor()==mc.player.getId())drawnBlades.put(sample.edge(),sample);};
                ClientWeaponCombat.setFixtureOwnsInput(true);initialized=true;setup();return;
            }
            if(imageError!=null)throw new IllegalStateException(imageError);
            if(!ready)return;
            tick++;
            var c=current();
            mc.player.setYRot(0);mc.player.yRotO=0;mc.player.setXRot(0);mc.player.xRotO=0;
            mc.player.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
            if(tick==40){
                if(!c.consume()&&c.form()==PowerForm.BLADE)ClientWeaponCombat.prepareSpecialMovement();
                if(c.consume())PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.GRAB));
                else PacketDistributor.sendToServer(new WeaponInputPayload(!c.heavy(),c.heavy()));
            }
            // Explicit physical-button packets are the same production route used by normal controls.
            if(!c.consume()&&tick>40&&tick<70&&c.heavy()&&tick%5==0)PacketDistributor.sendToServer(new WeaponInputPayload(false,true));
            if(!c.consume()&&tick==(c.heavy()?70:42))PacketDistributor.sendToServer(new WeaponInputPayload(false,false));
            if(c.name().equals("blade_pig_combo")){
                if(tick==60||tick==80||tick==100)PacketDistributor.sendToServer(new WeaponInputPayload(true,false));
                if(tick==62||tick==82||tick==102)PacketDistributor.sendToServer(new WeaponInputPayload(false,false));
            }
            // Weakened prey gets one authored consume opportunity. A second real attack
            // verifies the lethal path without bypassing that production mechanic.
            if(c.name().endsWith("_lethal")&&tick==105)server.execute(()->{
                var v=server.overworld().getEntity(victimId);var p=server.getPlayerList().getPlayer(mc.player.getUUID());
                if(v instanceof LivingEntity l&&l.isAlive()){
                    // The first rush carries the actor past its target. Reset the test
                    // stance before the next input; damage still uses the normal sweep.
                    p.connection.teleport(l.getX(),251,l.getZ()-2,0,0);p.setDeltaMovement(Vec3.ZERO);
                }
            });
            if(c.name().endsWith("_lethal")&&tick==115)PacketDistributor.sendToServer(new WeaponInputPayload(true,false));
            if(c.name().endsWith("_lethal")&&tick==117)PacketDistributor.sendToServer(new WeaponInputPayload(false,false));
            if(c.consume()&&tick==80)PacketDistributor.sendToServer(new ClawActionPayload(ClawActionPayload.Action.CONSUME));
            if(tick>=220){
                checkCase();
                Files.writeString(output.resolve(c.name()+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(rows));rows.asList().clear();
                synchronized(contactTrace){Files.writeString(output.resolve(c.name()+"-contact.json"),new GsonBuilder().setPrettyPrinting().create().toJson(contactTrace));contactTrace.asList().clear();}
                if(++index>=CASES.size()){
                    done=true;writer.shutdown();com.codex.blacklight.client.renderer.ConsumeBladeContact.observer=null;ClientWeaponCombat.setFixtureOwnsInput(false);bindings.forEach(KeyMapping::setKey);KeyMapping.resetMapping();mc.options.hideGui=false;
                    Files.writeString(output.resolve("capture-status.txt"),"Completed "+CASES.size()+" cases. Dropped frames="+dropped+". Visual review is still required.");
                    Files.writeString(output.resolve("case-checks.json"),new GsonBuilder().setPrettyPrinting().create().toJson(checks));
                    BlacklightNeoMod.LOGGER.info("Ragdoll QA complete; dropped={}",dropped);
                }else setup();
            }
        }catch(Exception e){failed=true;ClientWeaponCombat.setFixtureOwnsInput(false);bindings.forEach(KeyMapping::setKey);KeyMapping.resetMapping();BlacklightNeoMod.LOGGER.error("Ragdoll QA failed",e);if(writer!=null)writer.shutdown();}
    }
    static void checkCase(){
        var c=current();var result=new JsonObject();result.addProperty("case",c.name());
        int cuts=0,components=0;boolean committed=false,saved=false;float minimum=Float.MAX_VALUE,maximum=0;
        for(var element:rows){var r=element.getAsJsonObject();minimum=Math.min(minimum,r.get("health").getAsFloat());
            maximum=Math.max(maximum,r.get("health").getAsFloat());saved|=r.get("lastChance").getAsBoolean();
            committed|=ConsumeVisual.consumeStage(r.get("grab").getAsInt());
            if(r.has("cuts"))cuts=Math.max(cuts,r.get("cuts").getAsInt());if(r.has("components"))components=Math.max(components,r.get("components").getAsInt());
        }
        boolean strikeSplit=!c.consume()&&(c.form()==PowerForm.BLADE||c.form()==PowerForm.CLAWS)&&!c.name().endsWith("_miss")&&!c.name().endsWith("_survive")&&!(saved&&minimum>0);
        boolean expectsSplit=strikeSplit||c.consume()&&Float.isFinite(ConsumeSequence.splitSeconds(c.form(),false,c.variant()));
        result.addProperty("consumeCommitted",committed);result.addProperty("minHealth",minimum);result.addProperty("maxCuts",cuts);result.addProperty("maxComponents",components);
        result.addProperty("lastChanceUsed",saved);result.addProperty("expectedSplit",expectsSplit);
        int edgeCount=0; synchronized(contactTrace){for(var e:contactTrace)edgeCount=Math.max(edgeCount,e.getAsJsonObject().get("acceptedEdges").getAsInt());}
        var bodies=new HashSet<Long>();boolean monotonic=true;int previousCuts=0;
        for(var e:rows){var r=e.getAsJsonObject();if(r.has("bodyStarted"))bodies.add(r.get("bodyStarted").getAsLong());
            if(r.has("cuts")){int value=r.get("cuts").getAsInt();monotonic&=value>=previousCuts;previousCuts=value;}}
        result.addProperty("maxAcceptedEdges",edgeCount);result.addProperty("bodyInstances",bodies.size());result.addProperty("cutsPreserved",monotonic);
        boolean damagePassed=c.consume()?committed&&minimum==0
                :c.name().endsWith("_lethal")?minimum==0&&components>0:minimum<maximum||saved;
        if(c.name().endsWith("_miss"))damagePassed=minimum==maximum&&edgeCount==0&&components==0;
        if(c.name().endsWith("_survive"))damagePassed=minimum>0&&minimum<maximum&&cuts==0;
        boolean passed=damagePassed&&(!expectsSplit||components>=2);
        // Four contacting claws do not imply four fully detached chunks: a
        // partial cut can retain a bridge, and two blades can meet the same cut.
        if(c.name().equals("claws_cow"))passed&=edgeCount==4&&cuts>0&&components>=2&&bodies.size()==1&&monotonic;
        if(c.name().equals("claws_skeleton"))passed&=minimum==0&&components>=2&&bodies.size()==1&&monotonic;
        if(c.name().equals("hammer_zombie"))passed&=minimum==0&&cuts==0&&components==1&&bodies.size()==1&&monotonic;
        double floorClearance=Double.POSITIVE_INFINITY;int penetrations=0;
        for(var e:rows){var r=e.getAsJsonObject();if(r.has("floorClearance")){double clearance=r.get("floorClearance").getAsDouble();floorClearance=Math.min(floorClearance,clearance);if(clearance<-.02)penetrations++;}}
        if(Double.isFinite(floorClearance))result.addProperty("minFloorClearance",floorClearance);
        result.addProperty("floorPenetrationFrames",penetrations);result.addProperty("floorPassed",penetrations==0);passed&=penetrations==0;
        double renderedClearance=Double.POSITIVE_INFINITY;int renderedPenetrations=0,renderedSamples=0;
        for(var e:rows){var r=e.getAsJsonObject();if(r.has("renderMeshFloorClearance")){
            double clearance=r.get("renderMeshFloorClearance").getAsDouble();renderedClearance=Math.min(renderedClearance,clearance);renderedSamples++;if(clearance<-.02)renderedPenetrations++;}}
        if(Double.isFinite(renderedClearance))result.addProperty("minRenderedMeshClearance",renderedClearance);
        result.addProperty("renderedMeshSamples",renderedSamples);result.addProperty("renderedMeshPenetrations",renderedPenetrations);passed&=renderedPenetrations==0;
        double frozenMotion=0;int frozenFrames=0;
        for(var e:rows){var r=e.getAsJsonObject();if(r.has("frozenHorizontalMotion")){frozenFrames++;frozenMotion=Math.max(frozenMotion,r.get("frozenHorizontalMotion").getAsDouble());}}
        result.addProperty("frozenFrames",frozenFrames);result.addProperty("maxFrozenHorizontalMotion",frozenMotion);passed&=frozenMotion<1e-5;
        result.addProperty("damagePassed",damagePassed);result.addProperty("splitObserved",cuts>0&&components>=2);
        int maxTorsoPieces=0,maxTorsoCaps=0,maxIncisions=0,maxSubstantialTorsoPieces=0;double originalTorsoVolume=0;
        for(var e:rows){var r=e.getAsJsonObject();int torsoPieces=0,torsoCaps=0;
            if(r.has("materialIncisions"))maxIncisions=Math.max(maxIncisions,r.get("materialIncisions").getAsInt());
            if(r.has("pieces"))for(var part:r.getAsJsonArray("pieces")){var p=part.getAsJsonObject();if(p.get("region").getAsInt()==32){torsoPieces++;torsoCaps+=p.get("capVertices").getAsInt();}}
            if(r.has("pieces")){
                double sum=0;for(var part:r.getAsJsonArray("pieces")){var p=part.getAsJsonObject();if(p.get("region").getAsInt()==32)sum+=p.get("volume").getAsDouble();}
                if(originalTorsoVolume==0)originalTorsoVolume=sum;
                int substantial=0;for(var part:r.getAsJsonArray("pieces")){var p=part.getAsJsonObject();if(p.get("region").getAsInt()==32&&p.get("volume").getAsDouble()>=originalTorsoVolume*.1)substantial++;}
                maxSubstantialTorsoPieces=Math.max(maxSubstantialTorsoPieces,substantial);
            }
            maxTorsoPieces=Math.max(maxTorsoPieces,torsoPieces);maxTorsoCaps=Math.max(maxTorsoCaps,torsoCaps);
        }
        result.addProperty("maxTorsoPieces",maxTorsoPieces);result.addProperty("maxTorsoCapVertices",maxTorsoCaps);result.addProperty("materialIncisions",maxIncisions);
        // A tiny chip is not the requested torso segmentation. This threshold
        // is only an audit criterion; it never changes production cutting.
        result.addProperty("maxSubstantialTorsoPieces",maxSubstantialTorsoPieces);
        boolean pigTorso=c.type().equals("minecraft:pig")&&maxSubstantialTorsoPieces>=2;
        result.addProperty("pigTorsoSeparation",pigTorso);
        if(c.type().equals("minecraft:pig")&&!c.consume()&&expectsSplit)passed&=pigTorso;
        int prematureBodies=0,maxConsumeEdges=0,maxConsumeGeneration=0,consumeFrames=0,maxConsumeTickGap=0,previousTick=-1;double maxConsumeFrameMillis=0;
        for(var e:rows){var r=e.getAsJsonObject();int stage=r.get("grab").getAsInt();
            if(c.consume()&&(stage==ConsumeVisual.CAPTURE||stage==ConsumeVisual.HOLD)&&r.has("pieces"))prematureBodies++;
            if(stage==ConsumeVisual.CONSUME){consumeFrames++;if(previousTick>=0)maxConsumeTickGap=Math.max(maxConsumeTickGap,r.get("tick").getAsInt()-previousTick);previousTick=r.get("tick").getAsInt();
                if(r.has("frameMillis"))maxConsumeFrameMillis=Math.max(maxConsumeFrameMillis,r.get("frameMillis").getAsDouble());}
            if(r.has("consumeMaxGeneration"))maxConsumeGeneration=Math.max(maxConsumeGeneration,r.get("consumeMaxGeneration").getAsInt());
            if(r.has("consumeBladeEdges"))maxConsumeEdges=Math.max(maxConsumeEdges,r.getAsJsonArray("consumeBladeEdges").size());}
        result.addProperty("prematureGrabBodies",prematureBodies);result.addProperty("maxConsumeBladeEdges",maxConsumeEdges);passed&=prematureBodies==0;
        result.addProperty("maxConsumeGeneration",maxConsumeGeneration);
        if(c.name().contains("_tissue_")){
            int attached=0,unbound=0;
            for(var e:rows){var r=e.getAsJsonObject();
                if(r.has("attachmentVertices"))attached=Math.max(attached,r.get("attachmentVertices").getAsInt());
                if(r.has("unboundAttachmentVertices"))unbound+=r.get("unboundAttachmentVertices").getAsInt();
            }
            result.addProperty("maxAttachmentVertices",attached);result.addProperty("unboundAttachmentVertices",unbound);
            result.addProperty("attachmentsBound",attached>0&&unbound==0);passed&=attached>0&&unbound==0;
        }
        result.addProperty("consumeFrames",consumeFrames);result.addProperty("maxConsumeTickGap",maxConsumeTickGap);result.addProperty("maxConsumeFrameMillis",maxConsumeFrameMillis);
        result.addProperty("passed",passed);
        checks.add(result);
    }
    static void setup()throws ReflectiveOperationException{
        ready=false;tick=frame=0;previousFrameNanos=0;var mc=Minecraft.getInstance();var server=mc.getSingleplayerServer();var c=current();var uuid=mc.player.getUUID();
        partIds.clear();
        drawnBlades.clear();
        // Each isolated fixture starts with clean blood, so old splats cannot be
        // mistaken for a new hit in the frame-by-frame contact review.
        for(String name:List.of("EMITTERS","DROPS","SPLATS","FRAMES","SEVERED","CONSUME_HITS")){
            var f=com.codex.blacklight.client.renderer.WoundBlood.class.getDeclaredField(name);f.setAccessible(true);var value=f.get(null);
            if(value instanceof Collection<?> collection)collection.clear();else if(value instanceof Map<?,?> map)map.clear();
        }
        server.execute(()->{
            try{
                var level=server.overworld();var p=server.getPlayerList().getPlayer(uuid);
                ConsumeSystem.cancel(p);WeaponCombatSystem.cancel(p);WeaponCombatSystem.cancelHeldInput(p);CombatTargeting.requestLock(p,-1);
                for(var e:level.getEntitiesOfClass(Entity.class,new AABB(-20,240,-20,20,280,30),e->!(e instanceof net.minecraft.world.entity.player.Player)))e.discard();
                for(int x=-10;x<=10;x++)for(int z=-10;z<=20;z++){
                    level.setBlockAndUpdate(new BlockPos(x,250,z),((x+z)%5==0?Blocks.WHITE_CONCRETE:Blocks.GRAY_CONCRETE).defaultBlockState());
                    for(int y=251;y<258;y++)level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
                }
                level.setDayTime(18000);level.setWeatherParameters(6000,0,false,false);
                var state=new BlacklightState();state.infect();state.setPrototypeMode(true);state.unlock(c.form());state.activate(c.form());state.addBiomass(1000);
                p.setData(ModAttachments.PLAYER_STATE,state);p.syncData(ModAttachments.PLAYER_STATE);
                p.setData(ModAttachments.WEAPON_VISUAL,WeaponVisual.EMPTY);p.syncData(ModAttachments.WEAPON_VISUAL);
                p.setData(ModAttachments.HIT_REACTION,HitReaction.EMPTY);p.syncData(ModAttachments.HIT_REACTION);
                p.getInventory().clearContent();p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=false;p.onUpdateAbilities();
                p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,100000,0,false,false));
                p.connection.teleport(.5,251,.5,0,0);p.setDeltaMovement(Vec3.ZERO);p.getData(ModAttachments.MOVEMENT_STATE).setCameraYaw(0);
                var type=BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(c.type()));
                if(!(type.create(level) instanceof Mob v))throw new IllegalStateException("Missing mob "+c.type());
                if(c.name().contains("_tissue_"))v.setUUID(UUID.nameUUIDFromBytes(c.name().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                if(v instanceof com.codex.blacklight.entity.BlacklightInfectedHost host){
                    Mob source=c.name().contains("host_pig")?EntityType.PIG.create(level):c.name().contains("host_cow")?EntityType.COW.create(level):EntityType.VILLAGER.create(level);
                    host.inherit(source);
                    if(c.name().contains("_tissue_"))host.advanceTissue(com.codex.blacklight.infection.TissueGrowth.MATURITY_TICKS);
                }else if(c.name().contains("_tissue_")){
                    if(!com.codex.blacklight.infection.BlacklightInfectionSystem.infect(v,p))throw new IllegalStateException("Cannot infect fixture "+c.name());
                    var data=new net.minecraft.nbt.CompoundTag();data.putLong("Age",com.codex.blacklight.infection.TissueGrowth.MATURITY_TICKS);data.putString("Core","carapace");
                    v.getPersistentData().put("BlacklightNativeInfection",data);
                    com.codex.blacklight.infection.NativeInfection.tick(v,level);
                }
                float facing=c.name().contains("_side")?90:180;
                v.moveTo(c.name().equals("blade_pig_offset")?1.3:.5,251,c.consume()||c.name().endsWith("_survive")||c.name().endsWith("_close")?2.2:c.name().endsWith("_miss")?6:3.0,facing,0);v.setYHeadRot(facing);v.yBodyRot=facing;v.setNoAi(true);v.setPersistenceRequired();
                level.addFreshEntity(v);v.setHealth(c.name().endsWith("_survive")?v.getMaxHealth():c.consume()||c.name().endsWith("_lethal")?1:Math.min(v.getMaxHealth(),8));victimId=v.getId();health=v.getHealth();
                standingProbe=(Mob)type.create(level);standingProbe.moveTo(v.getX(),v.getY(),v.getZ(),facing,0);standingProbe.yBodyRot=standingProbe.yBodyRotO=facing;
                oldActor=p.position();oldVictim=v.position();
                ConsumeVariant.qaForced=c.variant();
                lastChance=false;ready=true;BlacklightNeoMod.LOGGER.info("Ragdoll QA case={} id={}",c.name(),victimId);
            }catch(Exception e){imageError="QA setup: "+e;BlacklightNeoMod.LOGGER.error("Ragdoll QA setup failed",e);}
        });
    }
    @SubscribeEvent public static void serverTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event){
        if(!active()||!ready)return;var v=event.getServer().overworld().getEntity(victimId);health=v instanceof LivingEntity l?l.getHealth():0;
        if(v instanceof Mob m)lastChance|=ConsumeLastChance.used(m);
        if(v instanceof LivingEntity l&&!current().consume())try{traceContact(event.getServer(),l);}catch(Exception e){imageError="Contact audit: "+e;}
    }
    static void traceContact(net.minecraft.server.MinecraftServer server,LivingEntity victim)throws ReflectiveOperationException{
        var p=server.getPlayerList().getPlayer(Minecraft.getInstance().player.getUUID());
        var f=WeaponCombatSystem.class.getDeclaredField("ACTIVE");f.setAccessible(true);var session=((Map<?,?>)f.get(null)).get(p);
        if(session!=null&&!(Boolean)field(session,"charging")){
            var move=(WeaponMove)field(session,"move");int elapsed=p.tickCount-(Integer)field(session,"started");
            if(elapsed<=move.end+4){
                float offset=(Float)field(session,"clipOffset"),yaw=(Float)field(session,"yaw");
                var local=LocalWeaponMotion.find(move.id);double transition=local.map(LocalWeaponMotion.Definition::transitionSeconds).orElse(.05F),correction=offset-Math.round(offset*20)/20.0;
                double from=Math.max(offset,(elapsed-1)/20.0-transition+correction),to=Math.max(offset,elapsed/20.0-transition+correction);
                var sweep=WeaponCutContact.sweep(p,move,from,to,oldActor,yaw,yaw,local.flatMap(LocalWeaponMotion.Definition::whipCurve).orElse(null));
                var actual=sweep.trace(p,victim,oldVictim);var blunt=sweep.bluntContact(p,victim,oldVictim);var standing=sweep.trace(p,standingProbe,standingProbe.position());
                var row=new JsonObject();row.addProperty("tick",tick);row.addProperty("elapsed",elapsed);row.addProperty("active",move.contactIndex(elapsed)>=0);row.addProperty("from",from);row.addProperty("to",to);row.addProperty("health",victim.getHealth());
                row.addProperty("cutWindow",(Integer)field(session,"cutWindow"));row.addProperty("cutStroke",(Long)field(session,"cutStroke"));
                var pending=((Map<?,?>)field(session,"pendingCuts")).get(victim.getUUID());row.addProperty("acceptedEdges",pending==null?0:((Set<?>)field(pending,"edges")).size());
                if(pending!=null){var paths=new JsonObject();var sampleFrames=new JsonObject();for(var entry:((Map<?,?>)field(pending,"blades")).entrySet()){
                    var c=(WeaponCutContact.Contact)entry.getValue();var path=new JsonArray();var frames=new JsonArray();for(var span:c.path()){
                        var profile=new JsonArray();for(var point:span.points())profile.add(vector(point));path.add(profile);
                        var at=new JsonObject();at.addProperty("time",span.time());at.addProperty("yaw",span.yaw());at.add("origin",vector(span.origin()));frames.add(at);
                    }paths.add(entry.getKey().toString(),path);sampleFrames.add(entry.getKey().toString(),frames);
                }row.add("bladePaths",paths);row.add("bladeFrames",sampleFrames);}
                row.add("actor",vector(p.position()));row.add("victim",vector(victim.position()));row.addProperty("actualCuts",actual.contacts().size());row.addProperty("bluntContacts",blunt.contacts().size());row.addProperty("standingCuts",standing.contacts().size());
                var nativeBlades=new JsonObject();var names=new LinkedHashSet<String>();for(String hand:List.of("Hand_R","Hand_L"))names.addAll(WeaponCutContact.sharpProfiles(move.form,hand).keySet());
                for(var name:names){var samples=new JsonArray();for(var span:sweep.path(name,victim,oldVictim)){var sample=new JsonObject();sample.addProperty("time",span.time());var points=new JsonArray();for(var point:span.points())points.add(vector(span.worldPoint(point)));sample.add("points",points);samples.add(sample);}nativeBlades.add(name,samples);}row.add("nativeBlades",nativeBlades);
                var edges=new JsonArray();for(var c:standing.contacts()){var q=new JsonObject();q.addProperty("edge",c.edge());q.add("point",vector(c.point()));q.add("normal",vector(c.normal()));edges.add(q);}row.add("standingEdges",edges);
                synchronized(contactTrace){contactTrace.add(row);}
            }
        }
        oldActor=p.position();oldVictim=victim.position();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void camera(ViewportEvent.ComputeFov e){
        if(!active()||!ready)return;
        int side=Integer.getInteger("blacklight.test.ragdollCameraSide",1)<0?-1:1;
        Vec3 at=new Vec3(.5,251.8,3),eye=at.add(4*side,2.4,-4.4),look=at.subtract(eye);
        var a=(com.codex.blacklight.mixin.client.PrototypeCameraAccess)e.getCamera();a.blacklight$setPosition(eye);
        a.blacklight$setRotation((float)Math.toDegrees(Math.atan2(-look.x,look.z)),(float)-Math.toDegrees(Math.atan2(look.y,look.horizontalDistance())),0);
    }
    static Object field(Object o,String name)throws ReflectiveOperationException{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
    static JsonArray vector(Vec3 v){var a=new JsonArray();a.add(v.x);a.add(v.y);a.add(v.z);return a;}
    static double skinVolume(List<?> mesh,Vec3 center)throws ReflectiveOperationException{
        double sum=0;for(int i=0;i+3<mesh.size();i+=4){
            Vec3 a=((Vec3)field(mesh.get(i),"p")).subtract(center),b=((Vec3)field(mesh.get(i+1),"p")).subtract(center),
                    c=((Vec3)field(mesh.get(i+2),"p")).subtract(center),d=((Vec3)field(mesh.get(i+3),"p")).subtract(center);
            sum+=a.dot(b.cross(c))+a.dot(c.cross(d));
        }return Math.abs(sum/6);
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event){
        if(!active()||!ready||tick<35||tick>190)return;
        try{
            var mc=Minecraft.getInstance();var row=new JsonObject();row.addProperty("frame",frame);row.addProperty("tick",tick);row.addProperty("health",health);row.addProperty("gameTime",mc.level.getGameTime());
            long nowNanos=System.nanoTime();if(previousFrameNanos!=0)row.addProperty("frameMillis",(nowNanos-previousFrameNanos)/1e6);previousFrameNanos=nowNanos;
            row.addProperty("partial",mc.getTimer().getGameTimeDeltaPartialTick(false));
            var blades=new JsonObject();for(var entry:drawnBlades.entrySet()){var sample=entry.getValue();var value=new JsonObject();value.addProperty("time",sample.time());var points=new JsonArray();sample.points().forEach(p->points.add(vector(p)));value.add("points",points);blades.add(entry.getKey(),value);}row.add("drawnBlades",blades);
            row.addProperty("move",mc.player.getData(ModAttachments.WEAPON_VISUAL).toString());row.addProperty("grab",mc.player.getData(ModAttachments.GRAB_STAGE));row.addProperty("lastChance",lastChance);
            clawContact(row,mc);
            var f=com.codex.blacklight.client.renderer.AdaptiveRagdoll.class.getDeclaredField("BODIES");f.setAccessible(true);
            var bodies=(Map<?,?>)f.get(null);
            for(var entry:bodies.entrySet())if(entry.getKey() instanceof LivingEntity l&&l.getId()==victimId){
                var body=entry.getValue();row.addProperty("bodyStarted",(Long)field(body,"started"));row.addProperty("cuts",(Integer)field(body,"appliedCuts"));row.addProperty("components",(Integer)field(body,"components"));
                row.addProperty("attachmentVertices",(Integer)field(body,"attachmentVertices"));row.addProperty("unboundAttachmentVertices",(Integer)field(body,"unboundAttachmentVertices"));
                var sectionEvents=new JsonArray();for(var key:(Set<?>)field(body,"sectionEvents"))sectionEvents.add(key.toString());row.add("sectionEvents",sectionEvents);
                row.addProperty("materialIncisions",(Integer)field(body,"materialIncisions"));
                row.addProperty("poseTime",(Double)field(body,"poseTime"));row.addProperty("waitingCuts",((List<?>)field(body,"waitingCuts")).size());
                row.addProperty("freezeUntil",(Long)field(body,"freezeUntil"));
                var consumeEdges=new TreeSet<String>();for(var part:(List<?>)field(body,"parts"))for(var key:((Map<?,?>)field(part,"consumePaths")).keySet())consumeEdges.add(key.toString());
                var consumeKeys=new JsonArray();consumeEdges.forEach(consumeKeys::add);row.add("consumeBladeEdges",consumeKeys);
                int generation=0;for(var part:(List<?>)field(body,"parts"))for(var sequence:((Map<?,?>)field(part,"consumePaths")).values())generation=Math.max(generation,(Integer)field(sequence,"generation"));
                row.addProperty("consumeMaxGeneration",generation);
                if(rows.asList().stream().noneMatch(r->r.getAsJsonObject().has("pieces"))){
                    var mesh=new JsonArray();for(var part:(List<?>)field(body,"parts"))for(var vertex:(List<?>)field(part,"mesh")){
                        var q=new JsonObject();q.addProperty("part",partId(part));q.addProperty("color",(Integer)field(vertex,"color"));q.addProperty("u",(Float)field(vertex,"u"));q.addProperty("v",(Float)field(vertex,"v"));q.addProperty("type",field(vertex,"type").toString());q.add("p",vector((Vec3)field(vertex,"p")));mesh.add(q);
                    }Files.writeString(output.resolve(current().name()+"-mesh.json"),new Gson().toJson(mesh));
                }
                boolean frozen=mc.level.getGameTime()<=(Long)field(body,"freezeUntil")&&(Double)field(body,"poseTime")>(Long)field(body,"started");
                double frozenMotion=0,floorClearance=Double.POSITIVE_INFINITY,renderMeshFloorClearance=Double.POSITIVE_INFINITY;
                int renderedParts=0;var pieces=new JsonArray();for(var part:(List<?>)field(body,"parts")){
                    Vec3 pos=(Vec3)field(part,"pos");var rotation=(org.joml.Quaternionf)field(part,"rotation");
                    if(!Double.isFinite(pos.lengthSqr())||!rotation.isFinite())throw new IllegalStateException("Non-finite body in "+current().name()+" tick="+tick+" frame="+frame);
                    var q=new JsonObject();q.add("pos",vector(pos));q.add("center",vector((Vec3)field(part,"center")));q.addProperty("cuts",((List<?>)field(part,"boundaries")).size());q.addProperty("region",(Integer)field(field(part,"bind"),"region"));
                    q.addProperty("part",partId(part));q.add("oldPos",vector((Vec3)field(part,"old")));var oldTurn=(org.joml.Quaternionf)field(part,"oldRotation");
                    int capVertices=0;for(var vertex:(List<?>)field(part,"mesh"))if((Integer)field(vertex,"color")==0xff70231f)capVertices++;
                    q.addProperty("capVertices",capVertices);q.addProperty("meshRevision",(Integer)field(part,"meshRevision"));
                    q.addProperty("vertices",((List<?>)field(part,"mesh")).size());
                    q.addProperty("volume",skinVolume((List<?>)field(part,"mesh"),(Vec3)field(part,"center")));
                    if(frozen)frozenMotion=Math.max(frozenMotion,pos.subtract((Vec3)field(part,"old")).horizontalDistance());
                    var oldRotation=new JsonArray();oldRotation.add(oldTurn.x);oldRotation.add(oldTurn.y);oldRotation.add(oldTurn.z);oldRotation.add(oldTurn.w);q.add("oldRotation",oldRotation);
                    var turn=new JsonArray();turn.add(rotation.x);turn.add(rotation.y);turn.add(rotation.z);turn.add(rotation.w);q.add("rotation",turn);q.add("half",vector((Vec3)field(part,"half")));q.addProperty("scale",(Float)field(part,"scale"));
                    if((Long)field(part,"renderedAt")==mc.level.getGameTime()&&(Float)field(part,"flow")<=.995F
                            &&Math.abs((Float)field(part,"renderedPartial")-row.get("partial").getAsFloat())<1e-5){
                        var drawn=(Vec3)field(part,"renderedPosition");var drawnTurn=(org.joml.Quaternionf)field(part,"renderedRotation");
                        var center=(Vec3)field(part,"center");float scale=(Float)field(part,"scale");
                        double bottom=Double.POSITIVE_INFINITY;boolean onPlatform=true;
                        for(var vertex:(List<?>)field(part,"mesh")){
                            var local=((Vec3)field(vertex,"p")).subtract(center).scale(scale);var offset=drawnTurn.transform(local.toVector3f());
                            var world=drawn.add(offset.x,offset.y,offset.z);bottom=Math.min(bottom,world.y);
                            onPlatform&=world.x>=-10&&world.x<=11&&world.z>=-10&&world.z<=21;
                        }
                        q.add("renderPos",vector(drawn));
                        if(onPlatform){renderedParts++;renderMeshFloorClearance=Math.min(renderMeshFloorClearance,bottom-251);}
                    }
                    if((Double)field(body,"poseTime")>(Long)field(body,"started")){
                        var half=(Vec3)field(part,"half");float scale=(Float)field(part,"scale");double bottom=Double.POSITIVE_INFINITY;boolean onPlatform=true;
                        for(int corner=0;corner<8;corner++){
                            var local=new Vec3((corner&1)==0?-half.x:half.x,(corner&2)==0?-half.y:half.y,(corner&4)==0?-half.z:half.z).scale(scale);
                            var offset=rotation.transform(local.toVector3f());var world=pos.add(offset.x,offset.y,offset.z);
                            onPlatform&=world.x>=-10&&world.x<=11&&world.z>=-10&&world.z<=21;bottom=Math.min(bottom,world.y);
                        }
                        if(onPlatform)floorClearance=Math.min(floorClearance,bottom-251);
                    }
                    int woundVertices=0;for(var skin:((Map<?,?>)field(part,"incisions")).values())woundVertices+=((List<?>)skin).size();q.addProperty("woundVertices",woundVertices);pieces.add(q);
                }row.add("pieces",pieces);if(Double.isFinite(floorClearance))row.addProperty("floorClearance",floorClearance);
                if(Double.isFinite(renderMeshFloorClearance))row.addProperty("renderMeshFloorClearance",renderMeshFloorClearance);
                row.addProperty("renderedParts",renderedParts);
                if(frozen)row.addProperty("frozenHorizontalMotion",frozenMotion);
            }
            String name=current().name()+String.format(Locale.ROOT,"_%04d.png",frame++);row.addProperty("image",name);rows.add(row);
            var image=Screenshot.takeScreenshot(mc.getMainRenderTarget());
            try{writer.execute(()->{try(image){image.writeToFile(output.resolve(name));}catch(Exception e){imageError=e.toString();}});row.addProperty("imageQueued",true);}
            catch(RejectedExecutionException e){image.close();dropped++;row.remove("image");row.addProperty("imageQueued",false);}
        }catch(Exception e){imageError=e.toString();}
    }
    static void clawContact(JsonObject row,Minecraft mc)throws ReflectiveOperationException{
        if(current().form()!=PowerForm.CLAWS||!(mc.level.getEntity(victimId) instanceof LivingEntity victim))return;
        var visual=victim.getData(ModAttachments.CONSUME_VISUAL);if(!visual.consuming()||visual.variant()!=ConsumeVariant.DEFAULT)return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        var field=com.codex.blacklight.client.renderer.StandalonePlayerRendering.class.getDeclaredField("RENDERERS");field.setAccessible(true);
        var renderer=(com.codex.blacklight.client.renderer.BlacklightPlayerRenderer)((Map<?,?>)field.get(null)).get(mc.player.getSkin().model());
        var model=(com.codex.blacklight.client.model.BlacklightPlayerModel)renderer.getModel();var matrices=model.matrices();if(matrices==null)return;
        var rig=com.codex.blacklight.animation.PlayerMotionController.skeleton();var wrist=rig.searchJointByName("Tool_R");
        var base=com.codex.blacklight.client.animation.WeaponModelAnchors.get(PowerForm.CLAWS,"trailRoot_R",new org.joml.Vector3f());
        var tip=com.codex.blacklight.client.animation.WeaponModelAnchors.get(PowerForm.CLAWS,"trailTip_R",base);
        var transform=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(mc.player,partial))).scale(.9375F*mc.player.getScale())
                .mul(matrices[wrist.getId()]).mul(new org.joml.Matrix4f(wrist.getLocalTransform()).invert());
        transform.transformPosition(base);transform.transformPosition(tip);
        Vec3 a=mc.player.getPosition(partial).add(base.x,base.y,base.z),b=mc.player.getPosition(partial).add(tip.x,tip.y,tip.z);
        var target=com.codex.blacklight.client.animation.RagdollDrive.contact(victim,32,partial);
        Vec3 segment=b.subtract(a),edge=a.add(segment.scale(Math.clamp(target.subtract(a).dot(segment)/Math.max(1e-8,segment.lengthSqr()),0,1)));
        var shoulder=matrices[rig.armR.getId()].getTranslation(new org.joml.Vector3f());var elbow=matrices[rig.handR.getId()].getTranslation(new org.joml.Vector3f());
        var actorFrame=new org.joml.Matrix4f().rotationY((float)Math.toRadians(180-com.codex.blacklight.client.animation.BlacklightRenderPose.facing(mc.player,partial))).scale(.9375F*mc.player.getScale());
        actorFrame.transformPosition(shoulder);actorFrame.transformPosition(elbow);
        Vec3 s=mc.player.getPosition(partial).add(shoulder.x,shoulder.y,shoulder.z),j=mc.player.getPosition(partial).add(elbow.x,elbow.y,elbow.z);
        row.addProperty("shoulderDistance",s.distanceTo(target));row.addProperty("upperLength",s.distanceTo(j));row.addProperty("bladeBaseLength",j.distanceTo(a));row.addProperty("bladeTipLength",j.distanceTo(b));
        row.addProperty("consumeSeconds",(mc.level.getGameTime()-visual.started()+partial)/20F);
        row.add("clawEdge",vector(edge));row.add("cutTarget",vector(target));row.addProperty("clawError",edge.distanceTo(target));
    }
}
