package com.codex.blacklight.client;

import com.codex.blacklight.entity.BlacklightInfectedHost;
import com.codex.blacklight.registry.*;
import com.codex.blacklight.world.TissueAttachmentBlock;
import net.minecraft.client.*;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
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

/** Hidden, disposable native-renderer review. No desktop input, no personal saves. */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class TissueVisualSmoke {
    private static final String WORLD="Tissue growth QA "+System.currentTimeMillis();
    private static final List<BlacklightInfectedHost> hosts=new ArrayList<>();
    private static boolean creating,setup,done,complete,attackSetup,meleeSetup,walkSetup,headAttackSetup;
    private static volatile boolean ready,exitReady;
    private static volatile int attackId=-1;
    private static int settled;
    private static int ticks,shot,overviewTick=-1,headReviewTick=-1,headSideTick=-1;
    private static volatile double walkX;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(!Boolean.getBoolean("blacklight.tissueVisual")||done)return;
        var mc=Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());mc.mouseHandler.releaseMouse();
        mc.options.pauseOnLostFocus=false;
        if(mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen)mc.setScreen(new TitleScreen());
        if(!creating&&mc.screen instanceof TitleScreen) {
            creating=true;mc.options.renderDistance().set(2);mc.options.simulationDistance().set(3);mc.options.framerateLimit().set(30);
            var rules=new GameRules();rules.getRule(GameRules.RULE_SPAWN_CHUNK_RADIUS).set(0,null);
            mc.createWorldOpenFlows().createFreshLevel(WORLD,
                    new LevelSettings(WORLD,GameType.SPECTATOR,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(87231L,false,false),
                    r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),mc.screen);
            return;
        }
        if(mc.player==null||mc.getSingleplayerServer()==null||!mc.getSingleplayerServer().getWorldData().getLevelName().equals(WORLD))return;
        mc.options.hideGui=true;mc.options.fov().set(55);mc.options.setCameraType(CameraType.FIRST_PERSON);mc.setScreen(null);
        if(!setup) {
            setup=true;
            mc.getSingleplayerServer().execute(()->{
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());var l=p.serverLevel();
                l.setDayTime(6000);l.setWeatherParameters(12000,0,false,false);
                l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.getServer());
                for(int x=-12;x<=18;x++)for(int z=-6;z<=12;z++)l.setBlock(new BlockPos(x,100,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
                int i=0;
                for(var type:List.of(EntityType.COW,EntityType.ZOMBIE,EntityType.SPIDER)) {
                    var host=ModEntities.INFECTED_HOST.get().create(l);host.inherit(type.create(l));
                    host.setUUID(new java.util.UUID(0,171+i*107));
                    host.setInvulnerable(true);host.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STONE_BUTTON));
                    host.moveTo(-4+i*8,101,0,30,0);host.yBodyRot=30;host.yHeadRot=30;host.setNoAi(true);
                    l.addFreshEntity(host);hosts.add(host);i++;
                }
                for(int a=0;a<4;a++) {
                    var pos=new BlockPos(-4+a*2,101,3);
                    l.setBlock(pos,ModBlocks.TISSUE_ATTACHMENT.get().defaultBlockState().setValue(TissueAttachmentBlock.AGE,a),3);
                }
                p.teleportTo(l,-1.6,101.4,4.2,150,16);ready=true;
            });
        }
        if(!ready)return;
        ticks++;
        float viewYaw=headSideTick>=0?120:headReviewTick>=0?210:overviewTick>=0?218:150;
        float viewPitch=headSideTick>=0?5:headReviewTick>=0?10:16;
        mc.player.setYRot(viewYaw);mc.player.setXRot(viewPitch);mc.player.yRotO=viewYaw;mc.player.xRotO=viewPitch;
        if(ticks==130||ticks==230)mc.getSingleplayerServer().execute(()->hosts.forEach(h->h.advanceTissue(6000)));
        if(shot>=7&&shot<9&&!walkSetup){
            walkSetup=true;
            mc.getSingleplayerServer().execute(()->{
                var h=hosts.getFirst();h.setTarget(null);h.setNoAi(false);
                h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(.16);
                walkX=h.getX()+3.5;
            });
        }
        if(walkSetup&&(shot==7||shot==8))mc.getSingleplayerServer().execute(()->{
            var h=hosts.getFirst();
            // Re-enabling AI first stops the previous melee goal and its path.
            // Issue the QA walk after that handoff rather than losing it on the first tick.
            if(h.getTarget()==null&&h.getNavigation().isDone())h.getNavigation().moveTo(walkX,h.getY(),h.getZ(),1);
        });
        if(shot==9&&!meleeSetup){
            meleeSetup=true;
            mc.getSingleplayerServer().execute(()->{
                var h=hosts.getFirst();var l=(net.minecraft.server.level.ServerLevel)h.level();
                var prey=EntityType.COW.create(l);prey.setNoAi(true);prey.setPos(h.position().add(1.5,0,0));l.addFreshEntity(prey);
                h.getNavigation().stop();h.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
                // AI damage is covered by GameTest. Isolate the ordinary melee pose
                // here so a new tendril windup cannot take ownership of this shot.
                h.setDeltaMovement(0,0,0);h.setNoAi(true);h.setTarget(null);
                h.swing(net.minecraft.world.InteractionHand.MAIN_HAND);h.doHurtTarget(prey);
            });
        }
        if(shot==11&&!headAttackSetup) {
            headAttackSetup=true;
            mc.getSingleplayerServer().execute(()->hosts.getFirst().swing(net.minecraft.world.InteractionHand.MAIN_HAND));
        }
        if(ticks>=320&&!attackSetup) {
            attackSetup=true;
            mc.getSingleplayerServer().execute(()->{
                var host=hosts.getFirst();var level=(net.minecraft.server.level.ServerLevel)host.level();
                var prey=EntityType.COW.create(level);prey.setNoAi(true);
                prey.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);prey.setHealth(1000);
                prey.setPos(host.position().add((host.getBbWidth()+prey.getBbWidth())*.5+host.tissueOrganReach()*.8,0,0));
                level.addFreshEntity(prey);host.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(0);
                host.setNoAi(false);host.setTarget(prey);attackId=host.getId();
            });
        }
        // Finishing immediately during generation can make NeoForge 21.1.244's unload queue
        // repeatedly reschedule a chunk with an outstanding generation reference. Let the
        // disposable world drain those jobs while its server is still ticking normally.
        if(complete&&ticks%20==0)mc.getSingleplayerServer().execute(()->{
            boolean busy=false;
            for(var level:mc.getSingleplayerServer().getAllLevels())for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++) {
                var holder=level.getChunkSource().chunkMap.getVisibleChunkIfPresent(ChunkPos.asLong(x,z));
                if(holder!=null&&holder.getGenerationRefCount()>0)busy=true;
            }
            settled=busy?0:settled+1;if(settled>=2)exitReady=true;
        });
        if(exitReady){done=true;mc.stop();}
        if(ticks>2400)throw new IllegalStateException("Tissue QA did not finish or world generation did not settle");
    }
    @SubscribeEvent public static void capture(RenderLevelStageEvent event) {
        if(!Boolean.getBoolean("blacklight.tissueVisual")||done||complete||!ready||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var mc=Minecraft.getInstance();
        if(shot<3) {int target=shot==0?110:shot==1?210:310;if(ticks<target)return;}
        else if(shot<6) {
            if(!(mc.level.getEntity(attackId) instanceof BlacklightInfectedHost host))return;
            float elapsed=host.tissueStrikeElapsed(mc.getTimer().getGameTimeDeltaPartialTick(false));
            if(elapsed<(shot==3?4:shot==4?12:20))return;
        }
        else if(shot<9&&ticks<overviewTick+(shot==6?40:shot==7?50:57))return;
        else if(shot==7||shot==8){
            if(!(mc.level.getEntity(attackId) instanceof BlacklightInfectedHost h)||h.walkAnimation.speed()<.025F)return;
        }
        else if(shot==9){
            if(!(mc.level.getEntity(attackId) instanceof BlacklightInfectedHost h)||h.tissueStrikeElapsed(0)>=0||h.getAttackAnim(mc.getTimer().getGameTimeDeltaPartialTick(false))<.25F)return;
        }
        else if(shot==10&&ticks<headReviewTick+40)return;
        else if(shot==11) {
            if(!(mc.level.getEntity(attackId) instanceof BlacklightInfectedHost h)||h.getAttackAnim(mc.getTimer().getGameTimeDeltaPartialTick(false))<.4F)return;
        }
        else if(shot==12&&ticks<headSideTick+30)return;
        try {
            Path output=mc.gameDirectory.toPath().resolve("tissue-captures");Files.createDirectories(output);
            String name=shot<3?"stage-"+shot:shot==6?"side-mature":shot==7?"walk-contact":shot==8?"walk-lift":"claw-melee";
            if(shot>=3&&shot<6)name=new String[]{"strike-windup","strike-contact","strike-retract"}[shot-3];
            if(shot>=10)name=new String[]{"head-rest","head-open","head-side"}[shot-10];
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(output.resolve(name+".png"));}
            String state="";
            if(attackId>=0&&mc.level.getEntity(attackId) instanceof BlacklightInfectedHost host)
                state="; strike="+host.tissueStrikeElapsed(0)+"; walk="+host.walkAnimation.speed()+"; melee="+host.getAttackAnim(0);
            Files.writeString(output.resolve("status.txt"),name+": native renderer capture"+state+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            if(++shot==6) {
                overviewTick=ticks;
                mc.getSingleplayerServer().execute(()->{
                    hosts.getFirst().setNoAi(true);
                    // Remove the first strike's target before the non-combat walk;
                    // nearest-target AI must not replace the requested navigation.
                    var actor=hosts.getFirst();
                    for(var other:actor.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,actor.getBoundingBox().inflate(12)))
                        if(!(other instanceof BlacklightInfectedHost)&&!(other instanceof net.minecraft.world.entity.player.Player))other.discard();
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    // Stay inside the same chunk for the reverse close-up; no new generation at teardown.
                    p.teleportTo(p.serverLevel(),-7,101.4,3.8,218,16);
                });
            }
            if(shot==10) {
                headReviewTick=ticks;
                mc.getSingleplayerServer().execute(()->{
                    var h=hosts.getFirst();h.getNavigation().stop();h.setNoAi(true);h.setDeltaMovement(0,0,0);
                    for(var other:h.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,h.getBoundingBox().inflate(12)))
                        if(!(other instanceof BlacklightInfectedHost)&&!(other instanceof net.minecraft.world.entity.player.Player))other.discard();
                    h.setYRot(30);h.setXRot(0);h.yHeadRot=30;h.yBodyRot=30;
                    var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    p.teleportTo(p.serverLevel(),h.getX()-1.325,h.getY()-.4,h.getZ()+2.295,210,10);
                });
            }
            if(shot==12) {
                headSideTick=ticks;
                mc.getSingleplayerServer().execute(()->{
                    var h=hosts.getFirst();var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    p.teleportTo(p.serverLevel(),h.getX()+1.902,h.getY()-.4,h.getZ()+1.906,120,5);
                });
            }
            if(shot>=13)complete=true;
        } catch(Exception failure){done=true;throw new RuntimeException(failure);}
    }
}
