package com.codex.blacklight.client;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.ModEntities;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.*;
import java.nio.file.*;
import java.util.*;

/** Same engine on different native skeletons, including a real server tendril cycle.
 * Hidden disposable client only; no desktop input or user save is used. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ProceduralTissueSmoke {
    private static final String WORLD="Procedural tissue QA "+System.currentTimeMillis();
    private static final boolean VARIANTS=Boolean.getBoolean("blacklight.tissueVariants");
    private static final boolean LATE=Boolean.getBoolean("blacklight.tissueLateGrowth");
    private static final boolean DEVELOPMENT=Boolean.getBoolean("blacklight.tissueDevelopmentPreview");
    private static final boolean EVOLUTION=Boolean.getBoolean("blacklight.tissueEvolutionPreview");
    private static final boolean EVOLVED=Boolean.getBoolean("blacklight.tissueEvolvedRegression");
    private static final boolean TENTACLES=Boolean.getBoolean("blacklight.tissueTentaclePreview");
    private static final boolean CONTRAST=Boolean.getBoolean("blacklight.tissueContrastPreview");
    private static final boolean SILHOUETTE=Boolean.getBoolean("blacklight.tissueSilhouettePreview")||CONTRAST;
    private static final boolean FRAMED=DEVELOPMENT||EVOLUTION||EVOLVED||TENTACLES||SILHOUETTE;
    private static final int[] VARIANT_IDS={0,1,8,11};
    private static final List<EntityType<?>> TYPES=CONTRAST?List.of(EntityType.PIGLIN,EntityType.PIGLIN,EntityType.PIGLIN,EntityType.PIGLIN,EntityType.PIGLIN,EntityType.COW,EntityType.COW,EntityType.COW,EntityType.COW,EntityType.COW,EntityType.SLIME,EntityType.SPIDER):SILHOUETTE?List.of(EntityType.PIG,EntityType.CHICKEN,EntityType.OCELOT,EntityType.CAVE_SPIDER,EntityType.ZOMBIE,EntityType.COW):TENTACLES?List.of(EntityType.COW,EntityType.ZOMBIE,EntityType.WOLF):DEVELOPMENT||EVOLUTION?List.of(EntityType.COW,EntityType.ZOMBIE,EntityType.SPIDER):VARIANTS
            ?List.of(EntityType.COW,EntityType.COW,EntityType.COW,EntityType.COW,EntityType.WOLF,EntityType.WOLF,EntityType.WOLF,EntityType.WOLF)
            :List.of(EntityType.COW,EntityType.PIG,EntityType.ZOMBIE,EntityType.SPIDER,EntityType.HORSE,EntityType.SHEEP,
            EntityType.WOLF,EntityType.CHICKEN,EntityType.VILLAGER,EntityType.SKELETON,EntityType.CREEPER,EntityType.WITCH);
    private static final String[] PHASES=CONTRAST?new String[]{"mature-front","mature-side","hypertrophic-front","hypertrophic-side","transition-front","transition-side","ancient-front","ancient-side","generation8-front","generation8-side","generation32-front","generation32-side","walk","attack","windup","contact","retract","rest"}:SILHOUETTE?new String[]{"mature-front","mature-side","ancient-front","ancient-side","generation8-front","generation8-side","generation32-front","generation32-side","walk","attack","windup","contact","retract","rest"}:TENTACLES?new String[]{"emerging","grown-front","grown-side","grown-back","walk","attack","windup","contact","grip","retract","rest"}:EVOLUTION?new String[]{"ancient-front","ancient-side","ancient-back",
            "generation1-front","generation1-side","generation1-back","generation3-front","generation3-side","generation3-back",
            "generation8-front","generation8-side","generation8-back","generation32-front","generation32-side","generation32-back",
            "walk","attack","windup","contact","retract","rest"}:EVOLVED?new String[]{"ancient","generation1","generation8","walk","attack","windup","contact","retract","rest"}:DEVELOPMENT?new String[]{"mature-front","mature-side","mature-back","rooting-front",
            "hypertrophic-front","hypertrophic-side","hypertrophic-back","transition-front","ancient-front","ancient-side","ancient-back",
            "walk","attack","windup","contact","retract","rest"}:VARIANTS?new String[]{"angle-a","angle-b"}
            :LATE?new String[]{"mature","hypertrophic","ancient","walk","attack","windup","contact","retract","rest"}
            :new String[]{"young","growing","mature","walk","attack","windup","contact","retract","rest"};
    private static boolean creating,setup,complete,done;
    private static volatile boolean ready,exitReady;
    private static volatile int actor=-1;
    private static int fixture,step,ticks,mark,settled;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("blacklight.proceduralVisual")||Boolean.getBoolean("blacklight.assimilationVisual")||done)return;
        var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();
        // Some Windows drivers report a 1x1 framebuffer after hiding a window.
        // Keep this disposable offscreen client at its declared capture size.
        if(mc.getWindow().getWidth()!=1280||mc.getWindow().getHeight()!=800
                ||mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800) {
            mc.getWindow().setWidth(1280);mc.getWindow().setHeight(800);
            mc.getMainRenderTarget().resize(1280,800,Minecraft.ON_OSX);
        }
        mc.options.pauseOnLostFocus=false;
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
        if(!creating&&mc.screen instanceof TitleScreen) {
            creating=true;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(5);mc.options.framerateLimit().set(30);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(87512L,false,false),r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        mc.options.hideGui=true;mc.options.fov().set(FRAMED?50:55);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
        float cameraYaw=viewYaw(),pitch=FRAMED?10:20;
        mc.player.setYRot(cameraYaw);mc.player.yRotO=cameraYaw;mc.player.setXRot(pitch);mc.player.xRotO=pitch;
        ticks++;
        if(ticks%20==0&&Files.exists(mc.gameDirectory.toPath().resolve("stop-request.txt"))){complete=true;ready=false;}
        if(ready&&!complete&&ticks%120==0)mc.getSingleplayerServer().execute(()->{
            if(mc.getSingleplayerServer().overworld().getEntity(actor) instanceof BlacklightInfectedHost h) {
                var prey=h.getTarget();String info="fixture="+fixture+"; phase="+step+"; actor="+h.speciesId()+"; swing="+h.getAttackAnim(0)+"; swinging="+h.swinging+"; strike="+h.tissueStrikeElapsed(0)
                        +"; reach="+h.tissueOrganReach()+"; target="+(prey==null?"none":prey.getType()+"; infected="+com.codex.blacklight.infection.BlacklightInfectionSystem.isInfected(prey)
                        +"; distance="+h.distanceTo(prey)+"; melee="+h.isWithinMeleeAttackRange(prey)+"; sight="+h.hasLineOfSight(prey));
                try {Files.writeString(mc.gameDirectory.toPath().resolve("fixture-status.txt"),info);}catch(Exception ex){throw new RuntimeException(ex);}
            }
        });
        if(!setup) {
            setup=true;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();
                l.setDayTime(6000);l.setWeatherParameters(12000,0,false,false);l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                for(int x=-10;x<=10;x++)for(int z=-8;z<=8;z++)l.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                spawn(0);
            });
            mark=ticks;
        }
        if(ready&&PHASES[step].equals("walk")&&!complete)mc.getSingleplayerServer().execute(()->{
            if(mc.getSingleplayerServer().overworld().getEntity(actor) instanceof BlacklightInfectedHost h&&h.getNavigation().isDone())h.getNavigation().moveTo(2.4,101,0,1);
        });
        if(ready&&PHASES[step].equals("attack")&&!complete&&ticks-mark>30&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            if(mc.getSingleplayerServer().overworld().getEntity(actor) instanceof BlacklightInfectedHost h&&!h.swinging)h.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        if(complete&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            boolean busy=false;
            for(var l:mc.getSingleplayerServer().getAllLevels())for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++) {
                var holder=l.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z));
                if(holder!=null&&!holder.isReadyForSaving())busy=true;
            }
            settled=busy?0:settled+1;if(settled>=5)exitReady=true;
        });
        if(exitReady){done=true;mc.stop();}
        if(ticks>6000)throw new IllegalStateException("Procedural tissue QA stalled: fixture="+fixture+", phase="+step);
    }
    private static void spawn(int index) {
        var mc=Minecraft.getInstance();var l=mc.getSingleplayerServer().overworld();
        for(var entity:l.getEntitiesOfClass(LivingEntity.class,new net.minecraft.world.phys.AABB(-12,100,-9,12,108,9)))if(!(entity instanceof net.minecraft.world.entity.player.Player))entity.discard();
        var host=ModEntities.INFECTED_HOST.get().create(l);host.inherit((Mob)TYPES.get(index).create(l));
        long seed=DEVELOPMENT||EVOLUTION?new long[]{171,385,492}[index]:171+index*107;
        if(CONTRAST)for(seed=1;com.codex.blacklight.infection.TissueCore.forSeed(seed).ordinal()!=index%5;seed++);
        if(TENTACLES)for(seed=1;com.codex.blacklight.infection.TissueEvolution.tentacles(com.codex.blacklight.infection.TissueEvolution.sample(seed,120000))<.99F
                ||com.codex.blacklight.infection.TissueEvolution.tentacleCount(seed)!=(index==1?6:4);seed++);
        if(VARIANTS)for(seed=1;(com.codex.blacklight.infection.TissueGrowth.mix(seed)&15)!=VARIANT_IDS[index%4];seed++);
        host.setUUID(new UUID(0,seed));host.setNoAi(true);host.setInvulnerable(true);
        if(VARIANTS||LATE||FRAMED)host.advanceTissue(TENTACLES?108000:EVOLUTION||EVOLVED?48000:com.codex.blacklight.infection.TissueGrowth.MATURITY_TICKS);
        host.moveTo(0,101,0,30,0);host.yBodyRot=30;host.yHeadRot=30;l.addFreshEntity(host);actor=host.getId();ready=true;
        positionCamera(host,false);
    }
    private static void positionCamera(BlacklightInfectedHost host,boolean reverse) {
        var mc=Minecraft.getInstance();var l=mc.getSingleplayerServer().overworld();
        var player=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        float scale=Math.clamp(host.getBbHeight()/host.tissueBodyScale()/1.8F,.7F,1.65F);
        if(FRAMED) {
            // Every generation uses the same world scale and camera distance.
            double distance=(CONTRAST?5.8:EVOLUTION||EVOLVED||SILHOUETTE?8:TENTACLES?6.5:5.2)*scale,bearing=Math.toRadians(viewYaw()-180);
            double bodyTarget=EVOLUTION||EVOLVED||TENTACLES||SILHOUETTE?host.getBbHeight()/host.tissueBodyScale()*.85:host.getBbHeight()*.6;
            // Normalize projected body height, so the contrast review cannot
            // mistake global size growth for anatomical shape development.
            if(CONTRAST){distance*=host.tissueBodyScale();bodyTarget=host.getBbHeight()*.65;}
            double eyeTarget=bodyTarget+distance*Math.tan(Math.toRadians(10));
            player.teleportTo(l,host.getX()-Math.sin(bearing)*distance,host.getY()+eyeTarget-player.getEyeHeight(),host.getZ()+Math.cos(bearing)*distance,viewYaw(),10);
            return;
        }
        double eyeTarget=host.getBbHeight()*.6+Math.hypot(2.1,3.7)*scale*Math.tan(Math.toRadians(20));
        int sign=reverse?-1:1;
        player.teleportTo(l,sign*2.1*scale,101+eyeTarget-player.getEyeHeight(),sign*3.7*scale,reverse?-30:150,20);
    }
    private static float viewYaw() {
        if(FRAMED)return 210+(PHASES[step].endsWith("-side")?90:PHASES[step].endsWith("-back")?180:0);
        return VARIANTS&&step==1?-30:150;
    }
    private static int snapshotAge(String phase) {
        if(phase.equals("emerging"))return 108000;
        if(phase.startsWith("grown"))return 120000;
        if(phase.startsWith("mature"))return 12000;
        if(phase.startsWith("rooting"))return 18000;
        if(phase.startsWith("hypertrophic"))return 24000;
        if(phase.startsWith("transition"))return 36000;
        if(phase.startsWith("ancient"))return 48000;
        if(phase.startsWith("generation32"))return 48000+32*24000;
        if(phase.startsWith("generation8"))return 48000+8*24000;
        if(phase.startsWith("generation3"))return 48000+3*24000;
        if(phase.startsWith("generation1"))return 48000+24000;
        return -1;
    }
    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if(!Boolean.getBoolean("blacklight.proceduralVisual")||Boolean.getBoolean("blacklight.assimilationVisual")||done||complete||!ready||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();if(!(mc.level.getEntity(actor) instanceof BlacklightInfectedHost h))return;
        if(mc.getMainRenderTarget().width!=1280||mc.getMainRenderTarget().height!=800)return;
        if(FRAMED&&(event.getFrustum()==null||!event.getFrustum().isVisible(h.getBoundingBox().inflate(.2))))return;
        String phase=PHASES[step];
        boolean strike=phase.equals("windup")||phase.equals("contact")||phase.equals("grip")||phase.equals("retract");
        if(ticks<mark+(step==0?40:phase.equals("walk")?18:strike||phase.equals("attack")?0:25))return;
        float partial=mc.getTimer().getGameTimeDeltaPartialTick(false);
        if(phase.equals("walk")&&h.walkAnimation.speed()<.035F)return;
        if(phase.equals("attack")&&h.getAttackAnim(partial)<.25F)return;
        float elapsed=h.tissueStrikeElapsed(partial);
        if(phase.equals("windup")&&(elapsed<2||elapsed>=com.codex.blacklight.infection.TissueOrgans.WINDUP))return;
        if(phase.equals("contact")&&(elapsed<com.codex.blacklight.infection.TissueOrgans.HIT||elapsed>=20))return;
        if(phase.equals("grip")&&(elapsed<16||elapsed>=com.codex.blacklight.infection.TissueOrgans.GRAB_RELEASE||h.tissueGripReleaseAt()<mc.level.getGameTime()))return;
        if(phase.equals("retract")&&(elapsed<20||elapsed>=com.codex.blacklight.infection.TissueOrgans.DURATION))return;
        if(phase.equals("rest")&&(elapsed>=0||ticks-mark<10))return;
        try {
            var output=mc.gameDirectory.toPath().resolve("procedural-captures");Files.createDirectories(output);
            String name=BuiltInRegistries.ENTITY_TYPE.getKey(TYPES.get(fixture)).getPath()
                    +(CONTRAST?"-core-"+com.codex.blacklight.infection.TissueCore.forSeed(com.codex.blacklight.infection.TissueOrgans.seed(h.getUUID())).name().toLowerCase(java.util.Locale.ROOT):"")
                    +(VARIANTS?"-variant-"+VARIANT_IDS[fixture%4]:"")+"-"+PHASES[step];
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
                if(image.getWidth()!=1280||image.getHeight()!=800)return;
                image.writeToFile(output.resolve(name+".png"));
            }
            Files.writeString(output.resolve("status.txt"),name+"; age="+h.tissueAge()+"; walk="+h.walkAnimation.speed()+"; attack="+h.getAttackAnim(partial)+"; tendril="+elapsed
                    +"; bodyScale="+h.tissueBodyScale()+"; generation="+com.codex.blacklight.infection.TissueEvolution.sample(com.codex.blacklight.infection.TissueOrgans.seed(h.getUUID()),h.tissueAge()).generation()
                    +"; tentacles="+h.tissueTentacles()+"; arms="+com.codex.blacklight.infection.TissueEvolution.tentacleCount(com.codex.blacklight.infection.TissueOrgans.seed(h.getUUID()))
                    +"; gripRelease="+h.tissueGripReleaseAt()+"; worldTime="+mc.level.getGameTime()
                    +(FRAMED?"; yaw="+mc.player.getYRot()+"; pitch="+mc.player.getXRot()+"; visible=1":"")+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            ready=false;mark=ticks;
            if(++step==PHASES.length) {
                step=0;if(++fixture==TYPES.size()){complete=true;return;}
                int next=fixture;mc.getSingleplayerServer().execute(()->spawn(next));
            } else {
                int next=step;mc.getSingleplayerServer().execute(()->{
                    var l=mc.getSingleplayerServer().overworld();if(!(l.getEntity(actor) instanceof BlacklightInfectedHost host))return;
                    String action=PHASES[next];
                    if(VARIANTS){positionCamera(host,true);ready=true;return;}
                    if((DEVELOPMENT||EVOLUTION||TENTACLES||SILHOUETTE)&&snapshotAge(action)>=0){host.advanceTissue(Math.max(0,snapshotAge(action)-host.tissueAge()));positionCamera(host,false);ready=true;return;}
                    if(!DEVELOPMENT&&!EVOLUTION&&!TENTACLES&&!SILHOUETTE&&(next==1||next==2))host.advanceTissue(EVOLVED?next==1?24000:168000:!LATE?6000:next==1?12000:24000);
                    if(action.equals("walk")){if(FRAMED)positionCamera(host,false);host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(.13);host.setNoAi(false);host.setTarget(null);}
                    if(action.equals("attack")){
                        host.getNavigation().stop();host.removeAllGoals(goal->true);host.setNoAi(false);host.setDeltaMovement(0,0,0);
                        var prey=EntityType.COW.create(l);prey.setNoAi(true);prey.setPos(host.position().add((host.getBbWidth()+.9)*.5,0,0));l.addFreshEntity(prey);
                        host.swing(net.minecraft.world.InteractionHand.MAIN_HAND);host.doHurtTarget(prey);
                    }
                    if(action.equals("windup")){
                        for(var other:l.getEntitiesOfClass(LivingEntity.class,host.getBoundingBox().inflate(12)))if(other!=host&&!(other instanceof net.minecraft.world.entity.player.Player))other.discard();
                        // The earlier stages already exercised ordinary gait
                        // and melee. Isolate the server's real organ loop here
                        // so a wide body's melee cannot knock prey out of range
                        // before the tendril starts. No synced fields are set.
                        host.getNavigation().stop();host.removeAllGoals(goal->true);host.setNoAi(false);
                        host.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);host.setSpeed(0);
                        host.setZza(0);host.setXxa(0);host.setDeltaMovement(0,0,0);
                        var prey=EntityType.COW.create(l);prey.setNoAi(true);prey.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);prey.setHealth(100);
                        float reach=host.tissueOrganReach();
                        prey.setPos(host.position().add((host.getBbWidth()+prey.getBbWidth())*.5+reach*.85,0,0));l.addFreshEntity(prey);host.setTarget(prey);
                    }
                    // Keep AI ticking until the server finishes its own
                    // retraction. NoAi would cancel the remaining animation.
                    if(action.equals("rest"))host.setTarget(null);
                    ready=true;
                });
            }
        } catch(Exception e){done=true;throw new RuntimeException(e);}
    }
}
