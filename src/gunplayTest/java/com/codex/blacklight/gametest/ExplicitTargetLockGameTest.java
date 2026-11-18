package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.*;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;

/** Pending real entity/Item.use regressions. Camera/input acceptance remains a player check. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class ExplicitTargetLockGameTest {
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;final BlockPos base;final ServerPlayer player;
        final List<Entity> entities=new ArrayList<>();final List<ChunkPos> forced=new ArrayList<>();
        Fixture(GameTestHelper h,int index){
            this.h=h;this.base=new BlockPos(631000+index*256,180,631000);
            var level=h.getLevel();
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++){
                var c=new ChunkPos((base.getX()>>4)+x,(base.getZ()>>4)+z);
                if(!level.getForcedChunks().contains(c.toLong())){forced.add(c);level.setChunkForced(c.x,c.z,true);}level.getChunk(c.x,c.z);
            }
            for(int x=-40;x<=40;x++)for(int z=-40;z<=40;z++){
                level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
                for(int y=0;y<42;y++)if(!level.isEmptyBlock(base.offset(x,y,z)))level.setBlock(base.offset(x,y,z),Blocks.AIR.defaultBlockState(),2);
            }
            player=FakePlayerFactory.get(level,new GameProfile(UUID.randomUUID(),"TargetRegionQA"));
            player.setGameMode(GameType.SURVIVAL);player.setPos(at(0,0,-18));player.setOnGround(true);level.addNewPlayer(player);rifle();
        }
        Vec3 at(double x,double y,double z){return Vec3.atLowerCornerOf(base).add(x+.5,y,z+.5);}
        void rifle(){var gun=FirearmsRegistry.weapon(WeaponCatalog.RIFLE.id());var stack=gun.getDefaultInstance();FirearmItem.store(stack,new AmmoState(30,0,0));player.setItemInHand(InteractionHand.MAIN_HAND,stack);player.getCooldowns().removeCooldown(gun);}
        BlackwatchHelicopter craft(){var c=ModEntities.BLACKWATCH_HELICOPTER.get().create(h.getLevel());c.setPos(at(0,8,8));h.getLevel().addFreshEntity(c);entities.add(c);return c;}
        EncounterMob mother(){
            var m=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
            var cycle=m.greeneBattle().save();cycle.putBoolean("OpeningPending",false);m.greeneBattle().load(cycle);
            m.setPos(at(0,0,0));m.setNoAi(true);m.setPersistenceRequired();m.restrictTo(base,8);h.getLevel().addFreshEntity(m);entities.add(m);return m;
        }
        void shot(){player.setYRot(90);player.setXRot(0);var gun=(FirearmItem)player.getMainHandItem().getItem();
            h.assertTrue(gun.use(h.getLevel(),player,InteractionHand.MAIN_HAND).getResult().consumesAction(),"real loaded Item.use accepts one shot");
            h.assertTrue(FirearmItem.state(player.getMainHandItem(),0).magazine()==29,"actual lock shot consumes one player round");}
        @Override public void close(){CombatTargeting.requestLock(player,-1);h.getLevel().removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);for(var e:entities)if(e instanceof ServerPlayer extra)h.getLevel().removePlayerImmediately(extra,Entity.RemovalReason.DISCARDED);else e.discard();for(var c:forced)h.getLevel().setChunkForced(c.x,c.z,false);}
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=60)
    public static void onFootGunUserCanAcquireAircraftAndActuallyShootOffAxis(GameTestHelper h){
        try(var f=new Fixture(h,0)){
            var craft=f.craft();h.assertTrue(ExplicitTarget.resolve(craft)==craft&&ExplicitTarget.candidates(f.player,64).contains(craft),"nonliving aircraft survives parent resolution and candidate collection");
            CombatTargeting.requestLock(f.player,craft.getId());h.assertTrue(CombatTargeting.lockedEntity(f.player)==craft&&CombatTargeting.locked(f.player)==null,"server accepts mechanical aircraft without claiming it is a consumable living body");
            float before=craft.health();f.shot();h.assertTrue(craft.health()<before,"off-axis actual rifle shot reaches the accepted aircraft hull");
            // FakePlayer.startRiding is deliberately disabled by the loader. Use the real player method.
            var profile=new GameProfile(UUID.randomUUID(),"ProtectedCrewQA");var ally=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),profile);
            ally.connection=FakePlayerFactory.get(h.getLevel(),profile).connection;ally.setGameMode(GameType.CREATIVE);ally.setPos(craft.position());
            f.entities.add(ally);h.getLevel().addNewPlayer(ally);
            h.assertTrue(ally.startRiding(craft,true),"fixture occupies aircraft with a protected player");
            h.assertTrue(!ExplicitTarget.selectable(f.player,craft)&&CombatTargeting.lockedEntity(f.player)==null,"protected occupants invalidate an existing aircraft lock");ally.stopRiding();
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=60)
    public static void coveredAircraftCannotBorrowLockVisibilityAndDestroyedCraftReleases(GameTestHelper h){
        try(var f=new Fixture(h,1)){
            var craft=f.craft();for(int x=-15;x<=15;x++)for(int y=0;y<25;y++)h.getLevel().setBlock(f.base.offset(x,y,-4),Blocks.BEDROCK.defaultBlockState(),2);
            CombatTargeting.requestLock(f.player,craft.getId());h.assertTrue(CombatTargeting.lockedEntity(f.player)==null,"full wall rejects a new ground-to-air lock");
            for(int x=-15;x<=15;x++)for(int y=0;y<25;y++)h.getLevel().setBlock(f.base.offset(x,y,-4),Blocks.AIR.defaultBlockState(),2);
            CombatTargeting.requestLock(f.player,craft.getId());h.assertTrue(CombatTargeting.lockedEntity(f.player)==craft,"removing actual cover admits the same aircraft");
            craft.hurt(h.getLevel().damageSources().playerAttack(f.player),craft.health()+1);
            h.assertTrue(craft.health()==0&&CombatTargeting.lockedEntity(f.player)==null,"crashing zero-health craft releases even before entity deletion");
        }h.succeed();
    }
    private static CompoundTag save(EncounterMob mob){var tag=new CompoundTag();mob.saveWithoutId(tag);return tag;}
    private static void prototype(Fixture f){prototype(f,com.codex.blacklight.power.PowerForm.HAMMERFISTS);}
    private static void prototype(Fixture f,com.codex.blacklight.power.PowerForm form){
        f.player.setItemInHand(InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        var state=ModAttachments.getData(f.player,ModAttachments.PLAYER_STATE);state.infect();state.setPrototypeMode(true);
        state.unlock(form);state.activate(form);
        ModAttachments.setData(f.player,ModAttachments.PLAYER_STATE,state);
    }
    private static void swing(Fixture f){
        WeaponCombatSystem.cancel(f.player);f.player.setOnGround(true);f.player.setDeltaMovement(Vec3.ZERO);
        WeaponCombatSystem.request(f.player,false);var move=ModAttachments.getData(f.player,ModAttachments.WEAPON_VISUAL).spec();
        f.h.assertTrue(move!=null,"actual prototype attack input begins a move");
        for(int tick=0;tick<move.duration+2;tick++){f.player.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,f.player));}
        WeaponCombatSystem.cancel(f.player);
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=60)
    public static void prototypeLockNeedsActualReachBeforeAircraftHullTakesMelee(GameTestHelper h){
        try(var f=new Fixture(h,4)){
            var craft=f.craft();craft.setPos(f.at(0,0,0));prototype(f);
            f.player.setPos(f.at(0,0,24));CombatTargeting.requestLock(f.player,craft.getId());
            h.assertTrue(CombatTargeting.lockedEntity(f.player)==craft,"prototype can select a distant aircraft");float before=craft.health();swing(f);
            h.assertTrue(craft.health()==before,"a lock alone does not remotely damage or teleport to the hull");
            f.player.setPos(f.at(0,0,5.4));f.player.setYRot(180);CombatTargeting.requestLock(f.player,craft.getId());swing(f);
            h.assertTrue(craft.health()<before,"same actual prototype action damages real nearby nose cuboids");
        }h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=60)
    public static void wallStillProtectsAircraftFromAnAlreadyLockedPrototypeSwing(GameTestHelper h){
        try(var f=new Fixture(h,5)){
            var craft=f.craft();craft.setPos(f.at(0,0,0));prototype(f);
            // The gun barrels extend to local z=4.8. A wall at fixture z=4 leaves
            // exposed barrels on the player's side and correctly permits damage.
            double front=com.codex.blacklight.military.aircraft.HelicopterSkinContact.boxes(craft).stream().mapToDouble(b->b.bounds.maxZ).max().orElseThrow();
            int wallZ=(int)Math.ceil(front);var start=new Vec3(craft.getX(),craft.getY(),wallZ+1.35);
            f.player.setPos(start);f.player.setYRot(180);
            CombatTargeting.requestLock(f.player,craft.getId());h.assertTrue(CombatTargeting.lockedEntity(f.player)==craft,"fixture locks before cover is inserted");
            h.assertTrue(front<wallZ,"entire actual structural hull is behind the wall, including the gun barrels");
            for(int x=-8;x<=8;x++)for(int y=0;y<10;y++)h.getLevel().setBlock(new BlockPos(f.base.getX()+x,f.base.getY()+y,wallZ),Blocks.BEDROCK.defaultBlockState(),2);
            h.assertTrue(com.codex.blacklight.military.aircraft.HelicopterSkinContact.visiblePoint(f.player,craft,64)==null,"wall blocks every structural skin sightline before the swing");
            float before=craft.health();swing(f);h.assertTrue(craft.health()==before,"retained identity cannot bypass actual block ray or authorise skin behind a wall");
            for(int x=-8;x<=8;x++)for(int y=0;y<10;y++)h.getLevel().setBlock(new BlockPos(f.base.getX()+x,f.base.getY()+y,wallZ),Blocks.AIR.defaultBlockState(),2);
            f.player.setPos(start);f.player.setYRot(180);CombatTargeting.requestLock(f.player,craft.getId());swing(f);
            h.assertTrue(craft.health()<before,"same starting position and real swing reach the hull once cover is removed");
        }h.succeed();
    }
    /** Find a genuine ground vantage where the chosen logical Slug is the first
     * actual receiving skin, instead of assuming a hand-tuned static pose. */
    private static boolean aimAt(Fixture f,EncounterMob mother,int slot){
        var selected=new TargetSelection(mother,slot);
        for(int i=0;i<32;i++){
            double angle=i*Math.PI/16;f.player.setPos(f.at(Math.sin(angle)*30,0,Math.cos(angle)*30));
            if(!selected.visibleWithinRange(f.player,64))continue;
            var from=f.player.getEyePosition().add(0,-.18,0);var point=selected.point(f.player);var delta=point.subtract(from).normalize();
            var contact=HydraHurtContact.ray(mother.hurtRegions(0),from,from.add(delta.scale(64)),InfectedAttackStyle.PIERCE);
            if(contact!=null&&contact.bone().equals("weak_"+slot))return true;
        }return false;
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=60)
    public static void threeIndependentSlugLocksDriveActualRifleDamageAndMeleeAim(GameTestHelper h){
        var f=new Fixture(h,2);var mother=f.mother();
        h.runAfterDelay(3,()->{try(f){
            h.assertTrue(TargetSelection.choices(f.player,mother).size()==3,"guarded mother offers exactly its three intact native health regions, never its immune core");
            for(int slot=0;slot<3;slot++){
                h.assertTrue(aimAt(f,mother,slot),"a real ground sightline reaches chosen Slug first: "+slot);f.rifle();
                CombatTargeting.requestLock(f.player,mother.getId(),slot);var selected=CombatTargeting.lockedSelection(f.player);
                h.assertTrue(selected!=null&&selected.target()==mother&&selected.region()==slot,"server retains selected persistent parent plus exact region "+slot);
                var point=selected.point(f.player);h.assertTrue(selected.boxes().stream().anyMatch(b->b.nearest(point).distanceToSqr(point)<1e-6),"lock marker lies on selected posed skin");
                h.assertTrue(CombatTargeting.aimPoint(f.player,f.player.position(),mother).distanceToSqr(point)<1e-8,"melee approach uses same selected region as firearm aim");
                var before=save(mother);f.shot();var after=save(mother);
                for(int other=0;other<3;other++)h.assertTrue(other==slot?after.getFloat("EncounterArmor"+other)<before.getFloat("EncounterArmor"+other):after.getFloat("EncounterArmor"+other)==before.getFloat("EncounterArmor"+other),"actual bullet debits only physically hit selected Slug "+slot+", inspected "+other);
            }
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=170)
    public static void brokenAndUndergroundRegionsRejectWhileCoreRequiresItsRealOpenGate(GameTestHelper h){
        var f=new Fixture(h,3);var mother=f.mother();
        h.runAfterDelay(3,()->{
            h.assertTrue(aimAt(f,mother,0),"visible first Slug fixture");CombatTargeting.requestLock(f.player,mother.getId(),0);
            float breaking=mother.greeneSegmentMaximum()/com.codex.blacklight.power.CombatScale.units(mother)+1;
            h.assertTrue(mother.hurtPart(0,h.getLevel().damageSources().generic(),breaking),"actual first child is destroyed");
            h.assertTrue(CombatTargeting.lockedEntity(f.player)==null&&!new TargetSelection(mother,0).selectable(f.player),"destroyed selection is cleared without silently retargeting its parent");
            h.assertTrue(TargetSelection.choices(f.player,mother).stream().allMatch(s->s.region()==1||s.region()==2),"cycle no longer includes detached Slug or immune core");
            for(int slot=1;slot<3;slot++)h.assertTrue(mother.hurtPart(slot,h.getLevel().damageSources().generic(),breaking),"remaining actual child breaks");
            h.assertTrue(!new TargetSelection(mother,-1).selectable(f.player),"collapse start alone does not expose the core");
        });
        h.runAfterDelay(132,()->{try(f){
            h.assertTrue(mother.greeneBattle().coreDamageable()&&new TargetSelection(mother,-1).selectable(f.player),"core becomes selectable only after the actual collapse/stun gate opens");
            var hidden=mother.greeneBattle().save();hidden.putInt("Stage",GreeneBattleCycle.GUARDED);hidden.putInt("Age",0);hidden.putBoolean("CoreDamageable",false);hidden.putBoolean("OpeningPending",true);
            mother.greeneRestoreAppendages(1);mother.greeneBattle().load(hidden);
            h.assertTrue(mother.greeneBattle().openingPending()&&!mother.broken(0)&&!mother.broken(1)&&!mother.broken(2),"opening fixture really has a pending guarded arrival with three intact Slugs");
            h.assertTrue(TargetSelection.choices(f.player,mother).isEmpty(),"a pending opening hides even intact region choices");
            hidden.putInt("Stage",GreeneBattleCycle.UNDERGROUND);hidden.putBoolean("OpeningPending",false);mother.greeneBattle().load(hidden);
            h.assertTrue(mother.greeneStage()==GreeneBattleCycle.UNDERGROUND&&TargetSelection.choices(f.player,mother).isEmpty(),"a real underground stage also rejects intact region choices");
            CombatTargeting.requestLock(f.player,mother.getId(),999);h.assertTrue(CombatTargeting.lockedEntity(f.player)==null,"forged out-of-range region is rejected");
        }h.succeed();});
    }
    /** Choose a grounded, unobstructed position using the actual segment and
     * visible weapon triangles. No trial damage or enlarged contact volume is
     * used to find this fixture. The subsequent request runs the real session. */
    private static boolean groundMeleeAt(Fixture f,EncounterMob mother,int slot,com.codex.blacklight.power.PowerForm form){
        var selected=new TargetSelection(mother,slot);var move=WeaponMove.choose(form,false,false,false,false);
        var local=LocalWeaponMotion.find(move.id).orElseThrow();
        var low=selected.boxes().stream().filter(b->b.bounds.minY<f.base.getY()+1.8&&b.bounds.maxY>f.base.getY()+.4)
                .sorted(Comparator.comparingDouble(b->b.center.y)).toList();
        for(var box:low){
            var outward=box.center.subtract(mother.position()).multiply(1,0,1).normalize();if(outward.lengthSqr()<.5)continue;
            for(double angle:new double[]{0,-.35,.35,-.7,.7})for(double gap:new double[]{1.15,.85,1.5}){
                var radial=outward.yRot((float)angle);var outside=box.center.add(radial.scale(12));
                var surface=box.nearest(new Vec3(outside.x,f.base.getY()+1.3,outside.z));
                f.player.setPos(surface.x+radial.x*gap,f.base.getY(),surface.z+radial.z*gap);f.player.setOnGround(true);
                if(!f.h.getLevel().noCollision(f.player,f.player.getBoundingBox())||!selected.visibleWithinRange(f.player,AttackReach.reach(f.player,move)+1.65))continue;
                var focus=selected.point(f.player);var delta=focus.subtract(f.player.position());float yaw=(float)Math.toDegrees(Math.atan2(-delta.x,delta.z));
                f.player.setYRot(yaw);f.player.setYHeadRot(yaw);f.player.yBodyRot=yaw;
                var eye=f.player.getEyePosition();var ray=HydraHurtContact.ray(mother.hurtRegions(0),eye,focus.add(focus.subtract(eye).normalize().scale(.02)),InfectedAttackStyle.TEAR);
                if(ray==null||!ray.bone().equals("weak_"+slot))continue;
                for(int tick=move.start;tick<move.end;tick++){
                    double from=Math.max(0,(tick-1)/20.0-local.transitionSeconds()),to=Math.max(0,tick/20.0-local.transitionSeconds());
                    var sweep=WeaponCutContact.sweep(f.player,move,from,to,f.player.position(),yaw,yaw,null);
                    var contact=sweep.trace(f.player,mother,mother.position());
                    if(contact.contacts().isEmpty())contact=sweep.bluntContact(f.player,mother,mother.position());
                    if(!contact.contacts().isEmpty()&&("weak_"+slot).equals(contact.contacts().get(0).receivingBone()))return true;
                }
            }
        }return false;
    }
    private static void regionalRootKeepsItsRealForwardStep(Fixture f,EncounterMob mother){
        var selected=new TargetSelection(mother,2);boolean found=false;
        for(double radius:new double[]{4.0,4.2,4.35})for(int i=0;i<32&&!found;i++){
            double angle=i*Math.PI/16;f.player.setPos(f.at(Math.sin(angle)*radius,0,Math.cos(angle)*radius));
            if(!selected.visibleWithinRange(f.player,4))continue;
            CombatTargeting.requestLock(f.player,mother.getId(),2);if(CombatTargeting.lockedSelection(f.player)==null)continue;
            var toward=selected.point(f.player).subtract(f.player.position()).multiply(1,0,1);if(toward.length()<1.05)continue;
            var step=toward.normalize().scale(.15);var rootDelta=mother.position().subtract(f.player.position());
            double rootSpacing=(f.player.getBbWidth()+mother.getBbWidth())*.5+.15;
            var old=com.codex.blacklight.animation.NativeRootMotion.standOff(step,rootDelta,Math.max(0,rootDelta.horizontalDistance()-rootSpacing));
            if(old.dot(step.normalize())>.03)continue;
            var actual=com.codex.blacklight.animation.NativeRootMotion.targetStandOff(f.player,mother,step);
            f.h.assertTrue(actual.distanceToSqr(step)<1e-8,"selected real skin has room for the unchanged Coord step even inside the mother's broad root radius");found=true;
        }f.h.assertTrue(found,"actual third Slug fixture exercises premature root-width stopping");
    }
    private static void groundWeaponRegions(GameTestHelper h,com.codex.blacklight.power.PowerForm form,int fixture){
        var f=new Fixture(h,fixture);var mother=f.mother();prototype(f,form);
        h.runAfterDelay(3,()->{try(f){
            if(form==com.codex.blacklight.power.PowerForm.CLAWS)regionalRootKeepsItsRealForwardStep(f,mother);
            for(int slot=0;slot<3;slot++){
                h.assertTrue(groundMeleeAt(f,mother,slot,form),"actual ground weapon mesh can reach selected Slug "+slot+" using "+form);
                CombatTargeting.requestLock(f.player,mother.getId(),slot);
                h.assertTrue(CombatTargeting.lockedSelection(f.player)!=null&&CombatTargeting.lockedSelection(f.player).region()==slot,"ground action retains exact region "+slot);
                var before=save(mother);var start=f.player.position();swing(f);var after=save(mother);
                h.assertTrue(Math.abs(f.player.getY()-start.y)<.01,"ground assist does not lift the player to a high body part");
                for(int other=0;other<3;other++)h.assertTrue(other==slot?after.getFloat("EncounterArmor"+other)<before.getFloat("EncounterArmor"+other):after.getFloat("EncounterArmor"+other)==before.getFloat("EncounterArmor"+other),
                        "real "+form+" request damages physically reached Slug "+slot+" only; inspected "+other);
            }
        }h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=90)
    public static void groundedClawsReachEachLockedSlugThroughTheRealWeaponInput(GameTestHelper h){groundWeaponRegions(h,com.codex.blacklight.power.PowerForm.CLAWS,6);}
    @GameTest(template="blackwatch_arena",batch="explicitTargetRegions",timeoutTicks=90)
    public static void groundedBladeReachesEachLockedSlugThroughTheRealWeaponInput(GameTestHelper h){groundWeaponRegions(h,com.codex.blacklight.power.PowerForm.BLADE,7);}
    private ExplicitTargetLockGameTest(){}
}
