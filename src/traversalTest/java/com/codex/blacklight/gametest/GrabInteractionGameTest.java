package com.codex.blacklight.gametest;
import com.codex.blacklight.registry.ModEntities;
import com.codex.blacklight.registry.EncounterEntities;
import com.codex.blacklight.entity.EncounterKind;
import com.codex.blacklight.world.GreeneHiveCave;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.item.GreeneTissueItem;
import com.codex.blacklight.registry.ModItems;
import com.codex.blacklight.mode.PrototypeSurvivalLock;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.codex.blacklight.network.ClawActionPayload.Action;
import com.codex.blacklight.power.PowerForm;
import com.codex.blacklight.registry.ModAttachments;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class GrabInteractionGameTest {
    private static com.codex.blacklight.entity.BlacklightInfectedHost evolvedBody(net.minecraft.server.level.ServerLevel level,Vec3 pos){
        var mob=ModEntities.INFECTED_HOST.get().create(level);
        var tag=new net.minecraft.nbt.CompoundTag();mob.saveWithoutId(tag);
        var host=new net.minecraft.nbt.CompoundTag();
        host.putString("Type","minecraft:zombie");host.putString("Family","ground");host.putBoolean("Evolved",true);
        host.putBoolean("Humanoid",true);host.putFloat("Width",1.064F);host.putFloat("Height",2.4192F);
        tag.put("BlacklightHost",host);mob.load(tag);mob.setPos(pos);mob.setNoAi(true);mob.setNoGravity(true);
        level.addFreshEntity(mob);return mob;
    }
    private static void face(ServerPlayer p,Vec3 point){
        var v=point.subtract(p.getEyePosition());
        p.setYRot((float)Math.toDegrees(Math.atan2(-v.x,v.z)));
        p.setXRot((float)-Math.toDegrees(Math.atan2(v.y,v.horizontalDistance())));
    }
    @GameTest(template="wall_test",batch="cave_throw",timeoutTicks=150)
    public static void feedsFromNaturalCaveFloor(GameTestHelper h){
        var l=h.getLevel();var den=h.absolutePos(new net.minecraft.core.BlockPos(8,45,8));
        // GameTestServer hosts a single level. Use the actual hive column generator for this strip of cave floor.
        for(int x=-25;x<=25;x++)for(int z=-20;z<=37;z++){
            var at=den.offset(x,0,z);var col=GreeneHiveCave.column(den,at.getX(),at.getZ());
            if(col!=null)l.setBlock(new net.minecraft.core.BlockPos(at.getX(),col.floor()-1,at.getZ()),col.replace(col.floor()-1,Blocks.STONE.defaultBlockState()),2);
        }
        for(int dx=-3;dx<=3;dx++)for(int dz=-3;dz<=3;dz++)l.getChunk((den.getX()>>4)+dx,(den.getZ()>>4)+dz);
        var b=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(l);
        int ground=GreeneHiveCave.column(den,den.getX(),den.getZ()).floor();
        b.moveTo(den.getX()+.5,ground,den.getZ()+.5,0,0);b.setNoGravity(true);b.startGreeneOutbreak();l.addFreshEntity(b);
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"cave-throw"));p.tickCount=100;
        var powers=p.getData(ModAttachments.PLAYER_STATE);powers.infect();powers.setPrototypeMode(true);powers.activate(PowerForm.CLAWS);
        var bodies=new java.util.ArrayList<net.minecraft.world.entity.Mob>();
        try{
            b.setHealth(b.getMaxHealth()*.50F);float hp=b.getHealth();
            for(int shot=0;shot<3;shot++){
                boolean blocked=shot==0, jump=shot==2;
                var state=b.spiderData().copy();state.putLong("NextMeal",0);state.putLong("NextMove",l.getGameTime()+500);
                b.spiderData(state);b.tickCount=100;
                var mouth=b.spider.mouth(1);var stand=mouth.add(0,0,shot==1?29:17);
                var col=net.minecraft.core.BlockPos.containing(stand);
                int y=GreeneHiveCave.column(den,col.getX(),col.getZ()).floor();
                var feet=new net.minecraft.core.BlockPos(col.getX(),y,col.getZ());
                h.assertTrue(!l.getBlockState(feet.below()).getCollisionShape(l,feet.below()).isEmpty(),"natural floor supports the shooter");
                p.setPos(stand.x,y,stand.z);p.setDeltaMovement(Vec3.ZERO);p.setOnGround(true);face(p,mouth);
                var cargo=evolvedBody(l,p.position().add(0,0,-1.85));bodies.add(cargo);
                b.spider.tick();
                h.assertTrue(!b.spider.feeding(),"full-health evolved survives natural selection");
                cargo.setHealth(cargo.getMaxHealth()*.32F);
                var food=ModEntities.COMMON_INFECTED.get().create(l);food.setPos(mouth.add(4,0,1));food.setNoGravity(true);food.setNoAi(true);l.addFreshEntity(food);bodies.add(food);
                b.spider.tick();
                h.assertTrue(b.spider.feeding() && b.spiderData().getUUID("Prey").equals(food.getUUID()),"meal selects ordinary food while the evolved is wounded");
                CombatTargeting.requestLock(p,b.getId(),SpiderAnatomy.MOUTH);
                h.assertTrue(CombatTargeting.locked(p)==b,"can prelock the mouth from the cave floor");
                ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
                h.assertTrue(ConsumeSystem.holding(p) && CombatTargeting.locked(p)==b,"prelocked mouth does not block grabbing wounded host");
                ClawCombatSystem.handle(p,Action.IMPLANT_HELD);h.assertTrue(cargo.getPersistentData().getBoolean(SpiderPhase.TUMOR),"X implants held host");
                if(jump){CombatTargeting.requestLock(p,-1);p.setOnGround(false);p.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,1.05,0));tick(p,1);}
                if(shot==1){
                    CombatTargeting.requestLock(p,b.getId(),SpiderAnatomy.RIM);
                    h.assertTrue(CombatTargeting.lockedSelection(p).region()==SpiderAnatomy.RIM,"outer ring is preselected before aiming into the open gap");
                }
                face(p,mouth);
                var wall=net.minecraft.core.BlockPos.containing(p.getEyePosition().lerp(mouth,.28));
                ClawCombatSystem.handle(p,Action.GRAB_THROW_START);ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
                if(blocked)for(int x=-2;x<=2;x++)for(int j=-2;j<=3;j++)l.setBlockAndUpdate(wall.offset(x,j,0),Blocks.STONE.defaultBlockState());
                tick(p,6);
                h.assertTrue(ConsumeSystem.inFlight(cargo),"release launches cargo on "+shot);
                int flight=0;var start=cargo.position();
                while(ConsumeSystem.inFlight(cargo) && flight++<41){ConsumeSystem.thrown(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(cargo));SpiderPhase.settle(l);}
                if(blocked){
                    h.assertTrue(b.spiderData().getInt("Oral")==0 && b.getHealth()==hp && food.isAlive(),"solid wall prevents feeding");
                    for(int x=-2;x<=2;x++)for(int j=-2;j<=3;j++)l.setBlockAndUpdate(wall.offset(x,j,0),Blocks.CAVE_AIR.defaultBlockState());
                    var reset=b.spiderData().copy();reset.remove("Prey");reset.remove("PreyId");b.spiderData(reset);
                }else{
                    h.assertTrue(b.spiderData().getInt("Oral")==shot && food.isAlive() && b.getHealth()==hp,
                            "mouth outcome "+shot+" ticks="+flight+" launch="+start+" impact="+cargo.position()+" mouth="+mouth+" blast="+b.spiderData().getInt("BlastPart"));
                    System.out.println("CAVE_OFFER distance="+p.getEyePosition().distanceTo(mouth)+" jump="+jump+" flightTicks="+flight+" oral="+shot);
                }
                cargo.discard();food.discard();
            }
            h.assertTrue(b.spider.sealed(),"two physical mouth hits permanently seal healing");
            // After sealing the mouth, throw an ordinary body at a visible leg from the same cave floor.
            var at=p.position();int floorY=GreeneHiveCave.column(den,(int)Math.floor(at.x),(int)Math.floor(at.z)).floor();
            p.setPos(at.x,floorY,at.z);p.setOnGround(true);
            var leg=TargetSelection.choices(p,b).stream().filter(s->s.region()>=SpiderAnatomy.FIRST_LEG && !SpiderAnatomy.tendon(s.region()) && s.visibleWithinRange(p,48))
                    .min(java.util.Comparator.comparingDouble(s->s.distanceSquared(p))).orElseThrow();
            var aim=leg.point(p);face(p,aim);
            CombatTargeting.requestLock(p,b.getId(),leg.region());
            var ordinary=EntityType.COW.create(l);var forward=aim.subtract(p.getEyePosition()).multiply(1,0,1).normalize();
            ordinary.setPos(p.position().add(forward.scale(1.5)));ordinary.setNoAi(true);l.addFreshEntity(ordinary);bodies.add(ordinary);
            ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
            h.assertTrue(ConsumeSystem.holding(p),"pick cargo with leg preselected");
            ClawCombatSystem.handle(p,Action.GRAB_THROW_START);ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);tick(p,6);
            float before=b.getHealth();int flight=0;
            while(ConsumeSystem.inFlight(ordinary) && flight++<41)ConsumeSystem.thrown(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(ordinary));
            int limb=SpiderAnatomy.legForRegion(leg.region());
            h.assertTrue(b.getHealth()<before && b.spiderData().getFloat("Wear"+limb)>0,
                    "quick throw hits preselected leg "+limb+" start="+p.position()+" impact="+ordinary.position()+" target="+aim);
            System.out.println("CAVE_JOINT_THROW leg="+limb+" distance="+p.getEyePosition().distanceTo(aim)+" flightTicks="+flight);
        }finally{ConsumeSystem.cancel(p);for(var m:bodies)m.discard();b.discard();}
        h.succeed();
    }
    @GameTest(template="wall_test",batch="crowd_grab",timeoutTicks=30)
    public static void nearbyAvailableBodyWinsInACrowd(GameTestHelper h){
        var p=player(h);var far=h.spawn(EntityType.COW,3,2,7);far.setNoAi(true);
        var resistant=h.spawn(EntityType.IRON_GOLEM,3,2,5);resistant.setNoAi(true);
        var side=h.spawn(EntityType.COW,5,2,4);side.setNoAi(true);
        p.setPos(p.position().add(0,15,0));far.setPos(p.position().add(0,0,5.7));side.setPos(p.position().add(1.9,0,.75));resistant.setPos(p.position().add(0,0,2.4));
        CombatTargeting.requestLock(p,far.getId());h.assertTrue(CombatTargeting.locked(p)==far,"far lock fixture; enabled="+ExplicitTarget.enabled(p)+" sight="+CombatTargeting.visibleWithinRange(p,far,32));
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(ConsumeSystem.holding(p) && ConsumeControl.protectedEntity(side) && CombatTargeting.locked(p)==far,
                "within reach, prefer a grabbable body even with an unreachable combat lock");
        ConsumeSystem.cancel(p);side.discard();far.discard();resistant.discard();h.succeed();
    }
    private static ServerPlayer player(GameTestHelper h) {
        for(int x=1;x<10;x++)for(int z=1;z<10;z++)h.setBlock(x,1,z,Blocks.STONE);
        var p=FakePlayerFactory.get(h.getLevel(),new GameProfile(UUID.randomUUID(),"grab-input"));
        p.setPos(h.absoluteVec(new Vec3(3,2,3)));p.setYRot(0);p.setXRot(0);p.setOnGround(true);p.tickCount=100;
        var s=p.getData(ModAttachments.PLAYER_STATE);s.infect();s.setPrototypeMode(true);s.activate(PowerForm.CLAWS);
        p.setData(ModAttachments.PLAYER_STATE,s);p.setHealth(10);return p;
    }
    private static void tick(ServerPlayer p,int count) {for(int i=0;i<count;i++){p.tickCount++;ConsumeSystem.tick(new PlayerTickEvent.Post(p));}}
    private static Vec3 carryAnchor(ServerPlayer p,net.minecraft.world.entity.Mob victim) {
        var v=victim.getData(ModAttachments.CONSUME_VISUAL);
        return ConsumePlacementTrack.carryAnchor(p.position(),p.getYRot(),victim,GrappleMotion.key(p,v),GrappleMotion.progress(p,v,0)).orElseThrow();
    }
    @GameTest(template="wall_test",timeoutTicks=35)
    public static void livingTissueAcceptsEitherHandAndYieldsToGrab(GameTestHelper h) {
        var p = player(h);
        long seed = h.getLevel().getSeed();
        var den = com.codex.blacklight.world.GreeneHiveCave.site(seed,1);
        var above = den.above(30);
        h.assertTrue(com.codex.blacklight.world.GreeneHiveCave.nearest(seed,above).equals(den)
                && com.codex.blacklight.world.GreeneHiveCave.guidance(den,Vec3.atCenterOf(above)).y < -.99, "a player above the nearest hive is directed down");
        var tracker = ModItems.GREENE_TISSUE.get();
        h.assertTrue(tracker != ModItems.MOTHER_CORE.get(), "new loot has its own registry ID");
        for (var hand : InteractionHand.values()) {
            var stack = new ItemStack(hand == InteractionHand.MAIN_HAND ? tracker : ModItems.MOTHER_CORE.get());
            p.setItemInHand(hand, stack);
            var use = new PlayerInteractEvent.RightClickItem(p, hand);
            PrototypeSurvivalLock.onRightClickItem(use);
            h.assertTrue(!use.isCanceled(), "Prototype input permits both fresh and old tissue");
            h.assertTrue(p.gameMode.useItem(p, p.level(), stack, hand).consumesAction() && p.isUsingItem()
                    && p.getUsedItemHand() == hand, "normal item entry starts sensing");
            h.assertTrue(GreeneTissueItem.opening(0) == 0 && GreeneTissueItem.opening(8) == 1
                    && GreeneTissueItem.opening(21) == 2 && GreeneTissueItem.opening(32) == 0, "valves open and close");
            p.releaseUsingItem();
            h.assertTrue(!p.isUsingItem() && stack.getCount() == 1, "interrupted listening preserves tissue");
            var echo = stack.getItem().finishUsingItem(stack, p.level(), p);
            h.assertTrue(echo == stack && echo.getCount() == 1 && p.getCooldowns().isOnCooldown(stack.getItem()), "complete scan is reusable");
            p.setItemInHand(hand, ItemStack.EMPTY);
        }
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(tracker));
        var cow = h.spawn(EntityType.COW,3,2,5);
        cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        var duringHold = new PlayerInteractEvent.RightClickItem(p,InteractionHand.OFF_HAND);
        PrototypeSurvivalLock.onRightClickItem(duringHold);
        h.assertTrue(duringHold.isCanceled(), "a captive keeps ownership of the use input");
        ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
        ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
        tick(p,6);
        h.assertTrue(cow.isAlive() && !ConsumeSystem.holding(p) && cow.getDeltaMovement().length() > 1, "tissue in the other hand permits a primary throw");
        cow.discard();
        h.succeed();
    }

    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeImpalesWithRightArmThenConsumesWithoutDroppingVictim(GameTestHelper h) {
        for(var type:new EntityType<?>[]{EntityType.CHICKEN,EntityType.COW}) {
            var p=player(h);var state=p.getData(ModAttachments.PLAYER_STATE);
            state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
            var mob=(net.minecraft.world.entity.Mob)h.spawn(type,3,2,5);mob.setNoAi(true);
            var before=mob.position();ConsumeSystem.request(p);tick(p,5);
            h.assertTrue(mob.position().distanceToSqr(before)<.0001,"wind-up must not drag the victim before blade contact");
            tick(p,ConsumeVisual.captureDuration(PowerForm.BLADE)-5);h.assertTrue(ConsumeSystem.holding(p),"blade capture reaches a decision hold");
            for(float yaw:new float[]{0,90,180,270})for(float pitch:new float[]{-60,0,60}) {
                p.setYRot(yaw);p.setXRot(pitch);tick(p,5);
                var expected=carryAnchor(p,mob);
                h.assertTrue(mob.position().distanceTo(expected)<.03,"right blade body anchor follows view");
            }
            p.setXRot(0);tick(p,5);var origin=p.position();ConsumeSystem.consume(p);
            int completion=ConsumeSequence.completionTick(PowerForm.BLADE,false);
            for(int t=1;t<completion;t++) {
                tick(p,1);
                h.assertTrue(mob.isAlive()&&p.getHealth()==10,"paired blade scene cannot pay before completion");
                h.assertTrue(mob.position().distanceTo(origin)<8,"paired receiver remains within the consume scene");
            }
            tick(p,2);h.assertTrue(mob.isRemoved()&&p.getHealth()>10&&!p.getData(ModAttachments.CONSUMING),"blade consumes once and unlocks");
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void bladeThrowReleasesOnRightArmSwingWithoutConsumeRewards(GameTestHelper h) {
        var p=player(h);var state=p.getData(ModAttachments.PLAYER_STATE);
        state.unlock(PowerForm.BLADE);state.activate(PowerForm.BLADE);
        var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.BLADE));ConsumeSystem.throwTarget(p);tick(p,5);
        h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.THROW,"retain body before release");
        tick(p,1);h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&p.getHealth()==10&&state.getEvolutionPoints()==0,"blade throw releases without reward");
        cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void smallVictimsStayAtPalmHeightAndFollowTurns(GameTestHelper h) {
        for(boolean baby:new boolean[]{false,true}) {
            var p=player(h);var chicken=h.spawn(EntityType.CHICKEN,3,2,5);chicken.setBaby(baby);chicken.setNoAi(true);
            ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));
            h.assertTrue(ConsumeSystem.holding(p),"chicken must be held");
            tick(p,1);
            h.assertTrue(chicken.position().distanceTo(carryAnchor(p,chicken))<.03,
                    "small bodies meet palm height instead of hovering below it");
            for(float yaw:new float[]{90,180,270,0}) for(float pitch:new float[]{75,-75,0}) {
                p.setYRot(yaw);p.setXRot(pitch);tick(p,5);
                var forward=net.minecraft.world.phys.Vec3.directionFromRotation(0,yaw);
                var contact=chicken.position().add(0,chicken.getBbHeight()*.6,0).subtract(forward.scale(chicken.getBbWidth()*.4));
                var hand=GrabPlacement.palm(p.position(),yaw,p.getXRot());
                h.assertTrue(chicken.position().distanceTo(carryAnchor(p,chicken))<.03&&ConsumeSystem.holding(p),"turning, looking up or down must not detach the held chicken");
            }
            ConsumeSystem.cancel(p);chicken.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void consumeCannotAcquireButCanQueueWithinExistingCapture(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.CONSUME);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING),"secondary intent without held target does not auto-grab");
        ClawCombatSystem.handle(p,Action.GRAB);ClawCombatSystem.handle(p,Action.CONSUME);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.CONSUME&&cow.isAlive()&&p.getHealth()==10,"RC49 queued right click starts consume only after capture");
        tick(p,ConsumeSequence.completionTick(PowerForm.CLAWS,false,cow.getData(ModAttachments.CONSUME_VISUAL).variant())-1);
        h.assertTrue(cow.isAlive()&&p.getHealth()==10,"queue cannot shorten the consume sequence");
        tick(p,2);ClawCombatSystem.handle(p,Action.CONSUME);tick(p,20);
        h.assertTrue(cow.isRemoved()&&p.getHealth()==12&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==2,"queued consume and repeated input pay exactly once");h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void grabKeyKeepsBodyAndPrimaryReleaseThrowsOnce(GameTestHelper h) {
        for (boolean early : new boolean[]{false,true}) {
            var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
            ClawCombatSystem.handle(p,Action.GRAB);
            if (!early) {
                tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
                ClawCombatSystem.handle(p,Action.GRAB);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_CANCEL);
                ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
                h.assertTrue(p.getData(ModAttachments.GRAB_STAGE) == ConsumeVisual.HOLD,"repeated grab and cancelled release keep the body");
            }
            ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
            ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
            if (early) {
                h.assertTrue(p.getData(ModAttachments.GRAB_STAGE) == ConsumeVisual.CAPTURE,"tap during capture waits for the hand");
                tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
            }
            p.setXRot(75);
            tick(p,5);
            h.assertTrue(p.getData(ModAttachments.GRAB_STAGE)==ConsumeVisual.THROW,"throw waits for hand release");
            tick(p,1);ClawCombatSystem.handle(p,Action.THROW);ClawCombatSystem.handle(p,Action.CONSUME);
            h.assertTrue(cow.isAlive()&&cow.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT&&!p.getData(ModAttachments.CONSUMING),"throw releases hold and begins free flight");
            var freeze=new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(cow);ConsumeSystem.freeze(freeze);
            h.assertTrue(!freeze.isCanceled(),"flight is not held by the capture freeze");
            h.assertTrue(cow.getDeltaMovement().z>1&&cow.getDeltaMovement().y>0,"victim launches in look direction");
            h.assertTrue(p.getHealth()==10&&p.getData(ModAttachments.PLAYER_STATE).getBiomass()==250&&p.getData(ModAttachments.PLAYER_STATE).getEvolutionPoints()==0,"throw and duplicate packets award no consumption resources");
            Vec3 launch=cow.position();
            for(int i=0;i<3;i++)ConsumeSystem.thrown(new net.neoforged.neoforge.event.tick.EntityTickEvent.Pre(cow));
            h.assertTrue(cow.position().distanceToSqr(launch)>4,"real flight displacement must work even for pre-existing NoAI victims");
            h.assertTrue(cow.isNoAi(),"flight must not change the original persistent NoAI setting");
            cow.discard();
        }
        h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=65)
    public static void chargedPrimaryThrowsAlongDownwardReleaseAim(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);
        tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        ClawCombatSystem.handle(p,Action.GRAB_THROW_START);
        h.runAfterDelay(10,()->ClawCombatSystem.handle(p,Action.GRAB_THROW_START));
        h.runAfterDelay(20,()->ClawCombatSystem.handle(p,Action.GRAB_THROW_START));
        h.runAfterDelay(31,()->{
            p.setXRot(70);
            ClawCombatSystem.handle(p,Action.GRAB_THROW_RELEASE);
            p.setXRot(-70);
            tick(p,6);
            var velocity=cow.getDeltaMovement();
            h.assertTrue(velocity.y < -1.9 && velocity.z > .6,
                    "fully charged body keeps downward pitch from the button release: "+velocity);
            h.succeed();
        });
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldTargetFollowsWalkingAndTurning(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));Vec3 before=cow.position();
        // Forward travel and a turn cannot cancel each other at the new left-palm pivot.
        for(int i=0;i<20;i++){p.setPos(p.position().add(0,0,.07));p.setYRot(i*4);tick(p,1);}
        h.assertTrue(ConsumeSystem.holding(p),"turning while walking must preserve the held state");
        Vec3 forward=Vec3.directionFromRotation(0,p.getYRot());
        Vec3 expected=carryAnchor(p,cow);
        h.assertTrue(expected.distanceToSqr(before)>.01,"walking and turning must produce a different contact point");
        h.assertTrue(cow.position().distanceToSqr(expected)<.0025,"held body follows the rotated palm after walking; gap="+cow.position().distanceTo(expected));ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldClicksDoNotStartGroundspikeOrClawAttack(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));
        for(var action:new Action[]{Action.GROUND_SPIKE_CHARGE_START,Action.CYCLE_FORM,Action.TOGGLE_CLAWS})ClawCombatSystem.handle(p,action);
        h.assertTrue(ConsumeSystem.holding(p)&&p.getData(ModAttachments.CLAW_COMBAT).action()==ClawCombatState.Action.IDLE,"held actions exclude combat and morph");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void carryingCanSprintTurnAndThrowWithoutStoppingOrHittingVictim(GameTestHelper h) {
        var p=player(h);p.setPos(h.absoluteVec(new Vec3(5,25,3)));
        var chicken=h.spawn(EntityType.CHICKEN,5,25,5);chicken.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));float health=chicken.getHealth();
        double traveled=0;
        com.codex.blacklight.movement.PrototypeMovementSystem.acceptInput(p,false,false,1,0,true,false);
        p.setDeltaMovement(0,0,.6);
        for(int i=0;i<8;i++) {
            // Stay inside the bounded test arena while covering a full running turn.
            p.setYRot(i*45);p.setOnGround(true);p.setDeltaMovement(Vec3.directionFromRotation(0,p.getYRot()).scale(.6));
            com.codex.blacklight.movement.PrototypeMovementSystem.onPlayerTick(new PlayerTickEvent.Post(p));
            h.assertTrue(p.isSprinting()&&p.getDeltaMovement().horizontalDistance()>.45,"held state accepts actual sprint input and acceleration");
            traveled+=p.getDeltaMovement().horizontalDistance();p.setPos(p.position().add(p.getDeltaMovement().multiply(1,0,1)));tick(p,1);
            var anchor=carryAnchor(p,chicken);
            h.assertTrue(ConsumeSystem.holding(p)&&chicken.position().distanceTo(anchor)<.03,"sprinting and turning keeps victim at palm: step="+i+" held="+ConsumeSystem.holding(p)+" gap="+chicken.position().distanceTo(anchor)+" player="+p.position()+" target="+chicken.position()+" anchor="+anchor);
        }
        h.assertTrue(traveled>4&&chicken.getHealth()==health,"running neither drops nor body-checks the carried chicken");
        p.setYRot(0);p.setDeltaMovement(0,0,.7);ConsumeSystem.throwTarget(p);
        for(int i=0;i<6;i++){p.setPos(p.position().add(0,0,.7));tick(p,1);}
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&chicken.getData(ModAttachments.CONSUME_VISUAL).stage()==ConsumeVisual.FLIGHT,"throw releases while carrier is moving");
        h.assertTrue(p.getDeltaMovement().z>.65&&chicken.getDeltaMovement().z>1.8,"throw preserves running speed and inherits carrier momentum");
        chicken.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void leavingPrototypeModeReleasesHeldBody(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);
        ClawCombatSystem.handle(p,Action.GRAB);tick(p,ConsumeVisual.captureDuration(p.getData(ModAttachments.PLAYER_STATE).getActivePower()));p.getData(ModAttachments.PLAYER_STATE).setPrototypeMode(false);tick(p,1);
        h.assertTrue(!p.getData(ModAttachments.CONSUMING)&&p.getData(ModAttachments.GRAB_STAGE)==0&&!cow.getData(ModAttachments.CONSUME_VISUAL).active()&&!cow.isNoAi(),"mode change frees target without persisting NoAI");
        h.assertTrue(p.getHealth()==10&&cow.isAlive(),"release is not consume");cow.discard();h.succeed();
    }
    @GameTest(template="wall_test",timeoutTicks=30)
    public static void heldBodyKeepsTheSharedGlideGripWithoutBreakingTheClaim(GameTestHelper h) {
        var p=player(h);var cow=h.spawn(EntityType.COW,3,2,5);cow.setNoAi(true);
        ConsumeSystem.request(p);tick(p,ConsumeVisual.captureDuration(PowerForm.CLAWS));
        h.assertTrue(ConsumeSystem.holding(p),"fixture reaches a real held state");
        p.setPos(p.position().add(0,3,0));cow.setPos(cow.position().add(0,3,0));p.setOnGround(false);
        p.setData(ModAttachments.GLIDE_UNTIL,p.level().getGameTime()+100);
        for(int step=0;step<8;step++) {
            tick(p,1);var visual=cow.getData(ModAttachments.CONSUME_VISUAL);
            var expected=GrappleMotion.carryAnchor(p,cow,visual,p.position(),p.getYRot(),0).orElseThrow();
            h.assertTrue(ConsumeSystem.holding(p)&&cow.position().distanceTo(expected)<.03,"gliding keeps the server's collision-resolved body on the shared anchor");
        }
        h.assertTrue(p.getData(ModAttachments.MOVEMENT_STATE).carryPosture().sample(p.tickCount,true,1)==1,"glide grip transition completes");
        ConsumeSystem.cancel(p);cow.discard();h.succeed();
    }
}
