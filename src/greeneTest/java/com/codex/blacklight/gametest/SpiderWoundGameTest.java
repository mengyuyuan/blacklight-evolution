package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.registry.EncounterEntities;
import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.*;
import java.util.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class SpiderWoundGameTest {
    @GameTest(template="blackwatch_arena",batch="spider_meal_limits",timeoutTicks=50)
    public static void limitsFoodAndClosesWoundsWithoutRestoringLegs(GameTestHelper h){
        var l=h.getLevel();var b=boss(h,h.absoluteVec(new Vec3(8,36,8)));b.setNoAi(false);
        state(b,1,0,0);b.setHealth(b.getMaxHealth()*.25F);
        var mouth=b.spider.mouth(1);float start=b.getHealth();
        for(int meal=0;meal<4;meal++){
            var food=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HUNTER.get().create(l);
            food.setPos(mouth.x,mouth.y-food.getBbHeight()*.5,mouth.z);food.setNoGravity(true);food.setNoAi(true);l.addFreshEntity(food);
            var t=b.spiderData().copy();t.putLong("NextMove",l.getGameTime()+500);
            t.putLong("NextMeal",0);b.spiderData(t);b.tickCount=100;b.spider.tick();
            if(meal<3){
                h.assertTrue(b.spider.feeding(),"eligible natural hunter "+meal);
                t=b.spiderData().copy();t.putLong("SwallowAt",l.getGameTime());t.putLong("DragAt",l.getGameTime()-10);b.spiderData(t);
                for(int i=0;i<12 && !food.isRemoved();i++){b.spider.tick();SpiderPhase.settle(l);}
                h.assertTrue(food.isRemoved() && b.spiderData().getLong("NextMeal")>=l.getGameTime()+320,"elite meal cooldown food="+food.position()+" mouth="+b.spider.mouth(1)+" data="+b.spiderData());
                h.assertTrue(b.spider.severed(0) && b.spiderData().contains("Closed0") && b.spider.woundOpenness("Closed0",16)==0,"repair closes cut but missing shin stays missing");
            }else h.assertTrue(!b.spider.feeding() && food.isAlive(),"budget exhausted");
            food.discard();
        }
        float healed=(b.getHealth()-start)/b.getMaxHealth();h.assertTrue(Math.abs(healed-.24)<.0001,"actual healing limit "+healed);
        b.spider.load(b.spider.save());h.assertTrue(b.spider.healingSpent() && b.spider.severed(0),"save retains spent healing and severance");
        var legacy=b.spider.save();legacy.remove("Closed0");legacy.remove("HealedShare");
        legacy.putLong("Swallowed",l.getGameTime()-25);legacy.putLong("Break0",l.getGameTime()-50);b.spider.load(legacy);
        h.assertTrue(b.spider.severed(0) && b.spider.woundOpenness("Closed0",0)==0,"older save with a successful later meal closes a stale wound");
        b.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="spider_claim_race",timeoutTicks=50)
    public static void metamorphosisCancelsAnOldTetherAndQueuedBite(GameTestHelper h){
        var l=h.getLevel();var b=boss(h,h.absoluteVec(new Vec3(8,36,8)));b.setNoAi(false);state(b,0,0,0);
        b.setHealth(b.getMaxHealth()*.5F);var mouth=b.spider.mouth(1);
        var mob=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(l);
        mob.setNoAi(true);mob.setNoGravity(true);mob.setPos(mouth.x,mouth.y-mob.getBbHeight()*.5,mouth.z);l.addFreshEntity(mob);
        var t=b.spiderData().copy();t.putLong("NextMove",l.getGameTime()+500);b.spiderData(t);b.tickCount=100;b.spider.tick();
        h.assertTrue(b.spider.feeding(),"ordinary unclaimed host chosen");
        t=b.spiderData().copy();t.putLong("SwallowAt",l.getGameTime());t.putLong("DragAt",l.getGameTime()-10);b.spiderData(t);
        var old=b.spider.save();float hp=b.getHealth();b.spider.tick();
        h.assertTrue(mob.beginGreeneEvolution(),"host begins metamorphosing before end-of-tick settlement");
        SpiderPhase.settle(l);
        h.assertTrue(mob.isAlive() && !b.spider.feeding() && b.getHealth()==hp,"queued bite releases metamorphosing host without reward");
        b.spider.load(old);b.spider.tick();h.assertTrue(!b.spider.feeding() && mob.isAlive(),"stale saved tether released");
        mob.discard();b.discard();h.succeed();
    }
    @GameTest(template="blackwatch_arena",batch="spider_limb_hits",timeoutTicks=70)
    public static void eightLegsReceiveMeleeAndSweptCargo(GameTestHelper h){
        var l=h.getLevel();var b=boss(h,h.absoluteVec(new Vec3(8,41,8)));state(b,0,0,0);
        var p=FakePlayerFactory.get(l,new GameProfile(UUID.randomUUID(),"joint-test"));
        var powers=com.codex.blacklight.registry.ModAttachments.getData(p,com.codex.blacklight.registry.ModAttachments.PLAYER_STATE);powers.infect();powers.setPrototypeMode(true);
        var cargo=net.minecraft.world.entity.EntityType.COW.create(l);cargo.setPos(b.position().add(0,20,0));l.addFreshEntity(cargo);
        try{
            for(int leg=0;leg<8;leg++){
                int slot=SpiderAnatomy.FIRST_LEG+leg*2;
                var region=b.hurtRegions(0).get(slot);
                // The last span is on the upper segment. Test the visible knee from its outward side.
                var nearKnee=region.boxes().get(region.boxes().size()-1).center;
                var out=nearKnee.subtract(b.position()).multiply(1,0,1).normalize();
                var eye=nearKnee.add(out.scale(2.5));p.setPos(eye.add(0,-p.getEyeHeight(),0));
                var dir=nearKnee.subtract(p.getEyePosition()).normalize();
                p.setYRot((float)Math.toDegrees(Math.atan2(-dir.x,dir.z)));p.setXRot((float)-Math.toDegrees(Math.asin(dir.y)));
                CombatTargeting.requestLock(p,b.getId(),slot);
                h.assertTrue(CombatTargeting.lockedSelection(p)!=null && CombatTargeting.lockedSelection(p).region()==slot,"visible knee can be locked "+leg);
                // Prototype mode owns left-click via its weapon packet. Native attacks use vanilla mode.
                powers.setPrototypeMode(false);
                b.invulnerableTime=0;float prior=b.getHealth();p.attack(b.getParts()[slot]);
                powers.setPrototypeMode(true);
                h.assertTrue(b.getHealth()<prior && b.spiderData().getFloat("Wear"+leg)>0,"native melee contacts the locked leg "+leg);
                var hit=BlockThrowCollision.entity(l,cargo,eye,nearKnee.subtract(out),new Vec3(.35,.55,.35),e->e==b);
                h.assertTrue(hit instanceof BlockThrowCollision.AnimatedHit posed && SpiderAnatomy.legForRegion(SpiderAnatomy.NAMES.indexOf(posed.contact().bone()))==leg,"cargo hits the same knee "+leg);
                b.invulnerableTime=0;prior=b.getHealth();BlockThrowCollision.hurt(hit,p.damageSources().thrown(cargo,p),.5F);
                h.assertTrue(b.getHealth()<prior,"cargo contact deals joint damage "+leg);
                if(leg==0){
                    var form=com.codex.blacklight.power.PowerForm.HAMMERFISTS;powers.unlock(form);powers.activate(form);
                    p.setOnGround(true);p.tickCount=100;b.invulnerableTime=0;prior=b.getHealth();
                    CombatTargeting.requestLock(p,b.getId(),slot);
                    ClawCombatSystem.handle(p,com.codex.blacklight.network.ClawActionPayload.Action.ATTACK);
                    for(int i=0;i<45;i++){p.tickCount++;WeaponCombatSystem.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));}
                    h.assertTrue(b.getHealth()<prior,"prototype primary attack hits the reachable joint");
                }
            }
            state(b,1,0,0);var closed=b.hurtRegions(0).get(SpiderAnatomy.FIRST_LEG);
            h.assertTrue(!closed.boxes().isEmpty() && b.hurtRegions(0).get(SpiderAnatomy.FIRST_LEG+1).boxes().isEmpty(),"amputated leg retains upper segment, loses tendon");
            var tag=b.spiderData().copy();tag.putInt("Strike0",FleshStrike.HAMMER.id);b.spiderData(tag);
            h.assertTrue(b.hurtRegions(0).get(SpiderAnatomy.FIRST_LEG).boxes().size()>closed.boxes().size(),"folded lower leg retains its visible strike surface");
        }finally{cargo.discard();b.discard();}
        h.succeed();
    }
    private static final boolean RECORD="1".equals(System.getenv("BLACKLIGHT_WOUND_CAPTURE"));
    private static void frame(JsonArray frames,EncounterMob b,int age){
        if(!RECORD)return;
        var f=new JsonObject();var rig=new JsonObject();f.addProperty("age",age);
        var root=SpiderAnatomy.root(b,1);
        for(String bone:SpiderAnatomy.MOLT_JOINTS){
            float[] matrix=new float[16];b.spider.jointMatrix(root,bone,1).get(matrix);rig.add(bone,new Gson().toJsonTree(matrix));
        }
        f.add("rig",rig);f.addProperty("attack",b.spider.actions.move().name());
        var seed=b.spider.actions.flyingSeed(1);if(seed!=null)f.add("seed",point(b,seed));
        var cords=new JsonArray();
        for(int slot=1;slot<=2;slot++){var line=new JsonArray();for(var p:b.spider.rootPath(slot,1))line.add(point(b,p));cords.add(line);}
        f.add("cords",cords);frames.add(f);
    }
    private static JsonArray point(EncounterMob b,Vec3 p){
        var d=p.subtract(b.position());var result=new JsonArray();result.add(d.x);result.add(d.y);result.add(d.z);return result;
    }
    private static void write(String name,JsonElement data){
        if(!RECORD)return;
        try(var writer=new FileWriter(name)){new Gson().toJson(data,writer);}catch(IOException ex){throw new UncheckedIOException(ex);}
    }
    private static EncounterMob boss(GameTestHelper h,Vec3 pos){
        var b=EncounterEntities.CREATURES.get(EncounterKind.ELIZABETH_GREENE).get().create(h.getLevel());
        b.moveTo(pos.x,pos.y,pos.z,0,0);b.setNoAi(true);b.setNoGravity(true);b.startGreeneOutbreak();
        h.getLevel().addFreshEntity(b);h.assertTrue(b.spider.live(),"fixture spider must be guarded");
        return b;
    }
    private static void state(EncounterMob b,int mask,int down,int turn){
        long now=b.level().getGameTime();
        var s=b.spiderData().copy();s.putInt("Lost",mask);s.putInt("DownSide",down);
        s.putLong("DownUntil",down==0?0:now+45);s.putInt("Turns",turn);
        s.putInt("Action",0);s.putInt("Foot",-1);s.putLong("Start",now-55);s.putLong("NextMove",0);
        s.putFloat("Facing",0);s.putFloat("FaceFrom",0);s.putLong("FaceTick",now);
        for(int i=0;i<8;i++){s.putLong("Break"+i,now-150);s.putInt("Strike"+i,FleshStrike.EDGE.id);s.putFloat("Side"+i,1);}
        b.spiderData(s);
    }
    private static void atAge(EncounterMob b,int age){
        var s=b.spiderData().copy();s.putLong("Start",b.level().getGameTime()-age);b.spiderData(s);
    }
    private static List<Vector3f> hull(){
        try(var in=SpiderWoundGameTest.class.getResourceAsStream("/assets/blacklight/models/greene_abdomen_support.json");
            var reader=new InputStreamReader(in)){
            var ls=new ArrayList<Vector3f>();
            for(var e:JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("vertices")){
                var a=e.getAsJsonArray();ls.add(new Vector3f(a.get(0).getAsFloat(),a.get(1).getAsFloat(),a.get(2).getAsFloat()));
            }
            return ls;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    private static void floor(ServerLevel l,BlockPos center,int width,int depth){
        for(int x=-width;x<=width;x++)for(int z=-width;z<=depth;z++)
            l.setBlock(center.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2);
    }
    @GameTest(template="blackwatch_arena",batch="greene_pursuit_handoff",timeoutTicks=735)
    public static void pursuesAcrossCaveAndHandsOffOneBoss(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,36,8));
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=6;z++){
            int cx=(center.getX()>>4)+x,cz=(center.getZ()>>4)+z;
            if(level.setChunkForced(cx,cz,true))forced.add(new net.minecraft.world.level.ChunkPos(cx,cz));
        }
        floor(level,center,13,91);
        var b=boss(h,Vec3.atBottomCenterOf(center));var profile=new GameProfile(UUID.randomUUID(),"CaveSurvivor");
        var p=new ServerPlayer(level.getServer(),level,profile);
        p.connection=FakePlayerFactory.get(level,profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);p.setHealth(500);
        level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(b.position().add(0,0,12));
        b.setNoAi(false);var start=b.position();p.setPos(start.add(0,0,85));state(b,0,0,0);
        var saw=java.util.EnumSet.noneOf(SpiderActions.Move.class);
        for(int i=1;i<=192;i++)h.runAfterDelay(i,()->saw.add(b.spider.actions.move()));
        h.runAfterDelay(193,()->{
            h.assertTrue(b.getZ()-start.z>22,"spider closes a real distance against a player 85 blocks away, travel="+(b.getZ()-start.z)+" moves="+saw+" tick="+b.tickCount+" stage="+b.greeneStage());
            h.assertTrue(saw.contains(SpiderActions.Move.MARCH) && saw.contains(SpiderActions.Move.FLESH_VOLLEY),"pursuit must alternate chase and ranged pressure: "+saw);
            System.out.println("CAVE_PRESSURE travel="+(b.getZ()-start.z)+" moves="+saw);
            var skin=b.hurtRegions(0);var region=skin.get(SpiderAnatomy.FIRST_LEG);var pt=region.boxes().get(0).center;
            var c=new HydraHurtContact.Contact(region.bone(),pt,new Vec3(1,0,0),com.codex.blacklight.infection.InfectedAttackStyle.TEAR);
            b.hurtAt(p.damageSources().playerAttack(p),b.getMaxHealth()*4,c);
            h.assertTrue(b.greeneStage()==GreeneBattleCycle.MOLTING,"lethal strike enters molt");
            p.setGameMode(GameType.CREATIVE);
        });
        h.runAfterDelay(390,()->{
            var st=b.spider.save();st.putDouble("HuskX",b.getX());st.putFloat("HuskYaw",23);b.spider.load(st);
            h.assertTrue(!b.spiderData().contains("HuskYaw") && b.spiderData().getIntArray("LastPose").length==39*16,"old shell stamp is removed while preserving the actual collapse pose");
        });
        h.runAfterDelay(661,()->{try{
            h.assertTrue(b.greeneStage()==GreeneBattleCycle.GUARDED && GreeneForm.of(b)==GreeneForm.GODDESS && !b.spider.first(),"full transition returns to the airborne combat form");
            h.assertTrue(b.getHealth()==b.getMaxHealth(),"goddess starts with her own full bar");
            var nearby=b.getBoundingBox().inflate(100);
            h.assertTrue(level.getEntitiesOfClass(EncounterMob.class,nearby,m->m.kind()==EncounterKind.ELIZABETH_GREENE).size()==1 &&
                    level.getEntitiesOfClass(GreeneSpider.class,nearby).isEmpty(),"only one boss and no persistent first-form entity");
            System.out.println("CAVE_PRESSURE 460-tick molt, one goddess, no ground shell");
        }finally{b.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);for(var q:forced)level.setChunkForced(q.x,q.z,false);}h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="greene_ribbed_den",timeoutTicks=505)
    public static void eatsThenPursuesAroundDenPillar(GameTestHelper h){
        var level=h.getLevel();
        var center=h.absolutePos(new BlockPos(8,141,8));
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int dx=-3;dx<=3;dx++)for(int dz=-2;dz<=3;dz++){
            int x=(center.getX()>>4)+dx,z=(center.getZ()>>4)+dz;
            if(level.setChunkForced(x,z,true))forced.add(new net.minecraft.world.level.ChunkPos(x,z));
        }
        // Use the outbreak generator's actual floor heights, including raised ribs under the belly.
        for(int x=-30;x<=36;x++)for(int z=-14;z<=42;z++){
            var col=com.codex.blacklight.world.GreeneHiveCave.column(center,center.getX()+x,center.getZ()+z);
            if(col==null)continue;
            for(int y=-2;y<col.floor()-center.getY();y++)level.setBlock(center.offset(x,y,z),Blocks.STONE.defaultBlockState(),2);
        }
        for(int x=-4;x<=4;x++)for(int z=12;z<=13;z++)for(int y=0;y<8;y++)
            level.setBlock(center.offset(x,y,z),Blocks.DEEPSLATE.defaultBlockState(),2);
        var start=Vec3.atBottomCenterOf(center);
        var b=boss(h,start);b.setNoAi(false);b.setNoGravity(false);state(b,0,0,0);
        b.setHealth(b.getMaxHealth()*.76F);
        var meal=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HUNTER.get().create(level);
        meal.moveTo(start.x-14,start.y+1,start.z+2,0,0);meal.setNoAi(true);level.addFreshEntity(meal);
        var profile=new GameProfile(UUID.randomUUID(),"DenRunner");
        var p=new ServerPlayer(level.getServer(),level,profile);
        p.connection=FakePlayerFactory.get(level,profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1000);p.setHealth(1000);
        p.setPos(start.add(0,2,39));level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);
        var opening=b.spiderData().copy();opening.putLong("NextMove",level.getGameTime()+12);b.spiderData(opening);
        var metrics=new double[5];var fed=new boolean[1];var samples=new JsonArray();
        var previous=new Vec3[]{b.position()};
        for(int i=1;i<=485;i++){
            int tick=i;
            h.runAfterDelay(i,()->{
                double flank=Math.min(31,Math.max(0,tick-224)*.42);
                p.setPos(start.add(flank,3,39-flank*.60));
                if(b.spider.feeding()){
                    fed[0]=true;
                    if(RECORD){
                        var line=b.spider.rootPath(0,0);
                        var sample=new JsonObject();var points=new JsonArray();
                        for(var v:line)points.add(point(b,v));
                        sample.add("path",points);sample.addProperty("time",b.tickCount/20.);
                        sample.addProperty("contact",level.getGameTime()-b.spiderData().getLong("DragAt"));
                        sample.add("target",point(b,meal.getBoundingBox().getCenter()));
                        samples.add(sample);
                    }
                }
                metrics[0]=Math.max(metrics[0],b.getZ()-start.z);
                if(b.getZ()-start.z<19)metrics[1]=Math.max(metrics[1],Math.abs(b.getX()-start.x));
                if(tick%40==0)System.out.println("CAVE_RIB_TICK "+tick+" rel="+b.position().subtract(start)+" player="+p.position().subtract(start)+" action="+b.spider.actions.move()+" age="+b.spider.actions.age(0));
                if(tick==224)metrics[2]=b.getX();
                metrics[3]=Math.max(metrics[3],Math.abs(b.getX()-start.x));
                metrics[4]=Math.max(metrics[4],previous[0].subtract(b.position()).horizontalDistance());
                previous[0]=b.position();
            });
        }
        h.runAfterDelay(487,()->{try{
            double flank=b.getX()-metrics[2];
            System.out.println("CAVE_RIB_PURSUIT ate="+(!meal.isAlive())+" advance="+metrics[0]+" detour="+metrics[1]+" flank="+flank+" maxStep="+metrics[4]+" pos="+b.position()+" health="+b.getHealth());
            write("spider-meal-strand.json",samples);
            h.assertTrue(fed[0] && !meal.isAlive() && b.getHealth()>b.getMaxHealth()*.76F,"must finish real natural meal before chasing");
            h.assertTrue(metrics[0]>24 && metrics[1]>7 && flank>6,"must get around the stone pillar and follow a runner changing flank");
            h.assertTrue(metrics[4]<2 && !level.getBlockCollisions(b,b.getBoundingBox().deflate(.02)).iterator().hasNext(),"bounded strides end in free space");
        }finally{b.discard();meal.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);for(var q:forced)level.setChunkForced(q.x,q.z,false);}
        h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="greene_walking_routes",timeoutTicks=310)
    public static void walkingLinesAndLateDodge(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,104,8));
        var forced=new ArrayList<net.minecraft.world.level.ChunkPos>();
        for(int x=-2;x<=2;x++)for(int z=-2;z<=3;z++){
            int cx=(center.getX()>>4)+x,cz=(center.getZ()>>4)+z;
            if(level.setChunkForced(cx,cz,true))forced.add(new net.minecraft.world.level.ChunkPos(cx,cz));
        }
        floor(level,center,26,48);
        var start=Vec3.atBottomCenterOf(center);var profile=new GameProfile(UUID.randomUUID(),"DenWalker");
        var p=new ServerPlayer(level.getServer(),level,profile);
        p.connection=FakePlayerFactory.get(level,profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);
        p.setPos(start);level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);
        var damage=new float[4];
        for(int route=0;route<4;route++){
            int mode=route;
            var move=switch(mode){case 1->SpiderActions.Move.SPIKES;case 2->SpiderActions.Move.FLESH_VOLLEY;default->SpiderActions.Move.STAB;};
            int beginning=1+route*68,duration=mode==2?57:mode==1?35:19;
            record Attack(EncounterMob boss,Vec3 from,Vec3 tangent){}
            var encounter=new Attack[1];
            h.runAfterDelay(beginning,()->{
                var b=boss(h,start);state(b,0,0,0);b.setNoAi(false);
                var leg=SpiderLegPose.worldTip(b,0,1).subtract(start).multiply(.78,0,.78);
                var tangent=new Vec3(leg.z,0,-leg.x).normalize();
                var from=mode==1?new Vec3(0,0,25):mode==2?new Vec3(0,0,37):leg;
                var origin=start.add(from);encounter[0]=new Attack(b,from,tangent);
                p.setPos(origin);p.invulnerableTime=0;p.setHealth(500);p.setDeltaMovement(Vec3.ZERO);
                var s=b.spiderData().copy();s.putInt("Action",move.ordinal());s.putLong("Start",level.getGameTime());
                s.putUUID("ActionTarget",p.getUUID());s.putInt("ActionId",p.getId());s.putInt("Foot",0);
                s.putDouble("Ax",origin.x);s.putDouble("Ay",origin.y);s.putDouble("Az",origin.z);b.spiderData(s);
            });
            for(int i=1;i<=duration;i++){
                int t=i;h.runAfterDelay(beginning+i,()->{
                    var e=encounter[0];var origin=start.add(e.from());double walk=.216*t;
                    Vec3 next=switch(mode){
                        case 1->origin.add(0,0,walk);
                        case 2->start.add(e.from().yRot((float)(walk/37)));
                        case 3->origin.add(e.tangent().scale(.216*(t<=9?t:18-t)));
                        default->origin.add(e.tangent().scale(walk));
                    };
                    p.setPos(next);p.setDeltaMovement(Vec3.ZERO);
                });
            }
            h.runAfterDelay(beginning+duration+1,()->{
                var b=encounter[0].boss();
                damage[mode]=500-p.getHealth();
                System.out.println("WALKING_DEN "+move+" route="+mode+" damage="+damage[mode]);
                b.discard();level.getEntitiesOfClass(GreeneProjectile.class,new net.minecraft.world.phys.AABB(center).inflate(65),shot->shot.getOwner()==b).forEach(Entity::discard);
            });
        }
        h.runAfterDelay(272,()->{try{
            h.assertTrue(damage[0]>0 && damage[1]>0 && damage[2]>0,"strafe, retreat, orbit must be punished on unchanged walking lines: "+Arrays.toString(damage));
            h.assertTrue(damage[3]==0,"reversing direction after the stab commits must evade it: "+damage[3]);
        }finally{level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);for(var q:forced)level.setChunkForced(q.x,q.z,false);}
        h.succeed();});
    }
    @GameTest(template="blackwatch_arena",batch="greene_new_pressure",timeoutTicks=200)
    public static void standingStrikeAndCounterWindow(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,36,8));floor(level,center,25,25);
        var b=boss(h,Vec3.atBottomCenterOf(center));var profile=new GameProfile(UUID.randomUUID(),"LimbProbe");
        var p=new ServerPlayer(level.getServer(),level,profile);
        p.connection=FakePlayerFactory.get(level,profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);p.setHealth(500);
        level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(b.position().add(0,0,12));
        try{
            state(b,0,0,0);
            var joint=SpiderAnatomy.shin(0);
            var midpoint=new Vec3(9.2065,.8195,-6.3235);
            var mat=b.spider.jointMatrix(SpiderAnatomy.root(b,0),joint,0);
            var pos=b.position().add(new Vec3(mat.transformPosition(midpoint.toVector3f())));
            var outside=pos.subtract(b.position()).multiply(1,0,1).normalize();
            var eye=pos.add(outside.scale(2));p.setPos(eye.add(0,-p.getEyeHeight(),0));
            p.setYRot((float)Math.toDegrees(Math.atan2(outside.x,-outside.z)));p.setXRot(0);
            var choice=TargetSelection.aimedBoss(p,List.of(b),p.getEyePosition(),pos.subtract(outside),0,4);
            h.assertTrue(choice!=null && choice.region()==SpiderAnatomy.FIRST_LEG,"ground-level aim must select left front shaft "+choice);
            float hp=b.getHealth();
            p.attack(b.getParts()[SpiderAnatomy.FIRST_LEG]);
            h.assertTrue(b.getHealth()<hp && b.spiderData().contains("Along0") && b.spiderData().getFloat("Wear0")>0,"a real player attack must injure the selected shaft");
            b.spider.actions.tick(level);
            h.assertTrue(b.spider.actions.move()==SpiderActions.Move.HEAVY_STAMP && b.spiderData().getInt("Foot")==0,"nearest intact foot selected for heavy stamp");
            var stampStart=b.spiderData().copy();
            p.setPos(b.spider.actions.aim().add(7,0,0));float playerHp=p.getHealth();
            var frames=new JsonArray();
            if(RECORD)for(int i=0;i<18;i++){atAge(b,i);frame(frames,b,i);}
            atAge(b,18);b.spider.actions.tick(level);
            h.assertTrue(b.spider.actions.lodged(0) && p.getHealth()==playerHp,"evaded impact leaves foot lodged");
            var planted=SpiderLegPose.worldTip(b,0,0);double drift=0;
            for(int tick=18;tick<58;tick++){atAge(b,tick);frame(frames,b,tick);drift=Math.max(drift,planted.distanceTo(SpiderLegPose.worldTip(b,0,0)));h.assertTrue(b.spider.exposedTendon(0) && b.spider.actions.lodged(0),"counter window at "+tick);}
            h.assertTrue(drift<.40,"lodged foot stays in its actual floor contact during the counter: "+drift);
            System.out.println("CAVE_PRESSURE planted tip drift="+drift);
            if(RECORD)for(int i=58;i<=70;i++){atAge(b,i);frame(frames,b,i);}
            write("heavy-stamp.json",frames);
            atAge(b,58);h.assertTrue(!b.spider.actions.lodged(0),"window ends at 40 ticks");
            var landed=b.spider.actions.aim();
            b.spiderData(stampStart.copy());p.setPos(landed.add(4.7,0,0));p.invulnerableTime=0;
            atAge(b,18);b.spider.actions.tick(level);
            h.assertTrue(p.getHealth()<playerHp,"grounded survivor 4.7 blocks away takes the widened rupture");
            float after=p.getHealth();p.invulnerableTime=0;b.spider.actions.tick(level);
            h.assertTrue(p.getHealth()==after,"duplicate impact cannot hurt twice");
            b.spiderData(stampStart.copy());p.setPos(landed.add(4.7,1.2,0));
            atAge(b,18);b.spider.actions.tick(level);
            h.assertTrue(p.getHealth()==after,"jumping the outside shock frees the counter lane");
            var spikes=b.spiderData().copy();spikes.putInt("Action",SpiderActions.Move.SPIKES.ordinal());
            spikes.putDouble("Ax",b.getX());spikes.putDouble("Ay",b.getY());spikes.putDouble("Az",b.getZ()+16);
            b.spiderData(spikes);
            var sites=SpiderRupture.spikeSites(b,4);var left=sites[12];var right=sites[14];
            h.assertTrue(left!=null && right!=null && left.distanceTo(right)>14,"final fork spans both flanks");
            p.setPos(right);p.invulnerableTime=0;
            atAge(b,34);b.spider.actions.tick(level);b.spider.actions.tick(level);
            h.assertTrue(p.getHealth()<after,"outer fork damages at its visible eruption");
            var shards=level.getEntitiesOfClass(GroundSpikeEntity.class,b.getBoundingBox().inflate(27));
            h.assertTrue(shards.size()==3,"the same row fires only once");shards.forEach(Entity::discard);
            var wallPos=BlockPos.containing(b.position().lerp(right,.5));
            for(int dy=0;dy<4;dy++)for(int dx=-2;dx<=2;dx++)level.setBlockAndUpdate(wallPos.offset(dx,dy,0),Blocks.STONE.defaultBlockState());
            h.assertTrue(SpiderRupture.spikeSites(b,4)[14]==null,"wall stops the side lane in both simulation and rendering");
            var st=b.spiderData().copy();st.putInt("Action",SpiderActions.Move.FLESH_VOLLEY.ordinal());b.spiderData(st);
            for(int tick:new int[]{15,15,22,29}){atAge(b,tick);b.spider.actions.tick(level);}
            var shots=level.getEntitiesOfClass(GreeneProjectile.class,b.getBoundingBox().inflate(32),v->v.getOwner()==b && v.flesh());
            h.assertTrue(shots.size()==21,"three distinct seven-shot flesh waves, same tick cannot fire again: "+shots.size());
            shots.forEach(Entity::discard);
            b.greeneBattle().spiderKilled();
            h.assertTrue(b.greeneStage()==GreeneBattleCycle.MOLTING && b.spiderData().getIntArray("LastPose").length==39*16,"molt captures the same injured rig");
            h.assertTrue(!b.spiderData().contains("HuskX"),"old shell is not placed on the floor");
            h.succeed();
        }finally{b.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
    @GameTest(template="blackwatch_arena",batch="greene_abdomen",timeoutTicks=180)
    public static void bellyClearsFloorAcrossEveryDamageMask(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,36,8));floor(level,center,13,13);
        var b=boss(h,Vec3.atBottomCenterOf(center));var vertices=hull();
        var poses=new JsonObject();double min=100,max=0;int contacts=0;
        for(int mask=1;mask<=255;mask++)for(int side=-1;side<=1;side++){
            state(b,mask,side,0);
            var root=SpiderAnatomy.root(b,1);var shell=b.spider.jointMatrix(root,"carapace",1);
            double height=vertices.stream().mapToDouble(v->shell.transformPosition(v,new Vector3f()).y).min().orElseThrow();
            h.assertTrue(height>=.023,"mask "+mask+" side "+side+" penetrated floor at "+height);
            if(height<.028)contacts++;
            min=Math.min(min,height);max=Math.max(max,height);
            if(RECORD && ((mask==15 && side==-1) || (mask==255 && side==0))){
                var rows=new JsonObject();
                for(String bone:SpiderAnatomy.MOLT_JOINTS){
                    float[] m=new float[16];b.spider.jointMatrix(root,bone,1).get(m);
                    rows.add(bone,new Gson().toJsonTree(m));
                }
                poses.add("mask"+mask,rows);
            }
        }
        h.assertTrue(contacts>450,"rolls must rest against the ground: "+contacts);
        System.out.println("WOUND_QA 765 poses min="+min+" max="+max+" contacts="+contacts);
        write("wounded-poses.json",poses);
        // Change the ground under the posterior after the old terrain was cached.
        state(b,255,0,0);
        var shell=b.spider.jointMatrix(SpiderAnatomy.root(b,1),"carapace",1);
        var bottom=vertices.stream().map(v->shell.transformPosition(v,new Vector3f())).min(Comparator.comparingDouble(v->v.y)).orElseThrow();
        var under=BlockPos.containing(b.position().add(bottom.x,0,bottom.z));
        level.setBlock(under,Blocks.STONE_SLAB.defaultBlockState(),2);
        h.runAfterDelay(2,()->{
            float raised=SpiderAnatomy.root(b,1).m31();var nowShell=b.spider.jointMatrix(SpiderAnatomy.root(b,1),"carapace",1);
            var low=vertices.stream().mapToDouble(v->nowShell.transformPosition(v,new Vector3f()).y).min().orElseThrow();
            h.assertTrue(low>=.49 && low<=.56,"real half slab must lift rear by half a block: "+low);
            level.removeBlock(under,false);
            h.runAfterDelay(2,()->{
                h.assertTrue(raised-SpiderAnatomy.root(b,1).m31()>.40,"removing slab releases the shell");
                b.spider.actions.leaveShell();
                h.assertTrue(b.spiderData().getIntArray("LastPose").length==39*16 && b.spider.brokenMask()==255,"molt stores all joints and wounds");
                b.discard();System.out.println("WOUND_QA half slab + removal and molt handoff passed");h.succeed();
            });
        });
    }
    @GameTest(template="blackwatch_arena",batch="greene_injury_combat",timeoutTicks=220)
    public static void leglessSpiderSelectsHitsCanBeCutAndPlantsPods(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,36,8));floor(level,center,12,36);
        var b=boss(h,Vec3.atBottomCenterOf(center));var profile=new GameProfile(UUID.randomUUID(),"WoundProbe");
        var p=new ServerPlayer(level.getServer(),level,profile);
        p.connection=FakePlayerFactory.get(level,profile).connection;
        for(int i=0;i<61;i++)p.tick();
        p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(500);p.setHealth(500);
        level.addNewPlayer(p);p.setGameMode(GameType.SURVIVAL);p.setNoGravity(true);p.setPos(b.position().add(0,0,12));
        try{
            state(b,255,0,0);b.spider.actions.tick(level);
            h.assertTrue(b.spider.actions.move()==SpiderActions.Move.ORAL_SCISSORS,"near selection: "+b.spider.actions.move());
            var frames=new JsonArray();
            if(RECORD)for(int t=0;t<42;t++){atAge(b,t);frame(frames,b,t);}
            atAge(b,23);var path=b.spider.rootPath(1,0);
            h.assertTrue(path.size()>=3,"live, lockable mouth lash");
            var tip=path.get(path.size()-1);
            p.setPos(b.position().add(6,0,-10));b.spider.actions.tick(level);
            h.assertTrue(!b.spiderData().getBoolean("Struck"),"a player outside the moving cords can dodge");
            p.setPos(tip.add(0,-.35,0));
            var boxes=new ArrayList<HydraContact.Box>();b.spider.addRootBoxes(1,0,boxes);
            h.assertTrue(boxes.size()==path.size()-1,"visible segments and lock volumes agree");
            b.spider.actions.tick(level);
            h.assertTrue(b.spiderData().getBoolean("Struck"),"mouth lash actually hits a survival player");
            b.spider.contact(SpiderAnatomy.FIRST_ROOT+1,p.damageSources().playerAttack(p),3,new HydraHurtContact.Contact("feed_1",tip,new Vec3(0,1,0),com.codex.blacklight.infection.InfectedAttackStyle.TEAR));
            h.assertTrue(b.spider.actions.move()==SpiderActions.Move.REST && b.spider.rootPath(1,0).isEmpty(),"cut cancels both lines");
            p.setPos(b.position().add(0,0,26));state(b,255,0,0);b.spider.actions.tick(level);
            h.assertTrue(b.spider.actions.move()==SpiderActions.Move.POD_CAST,"distant selection");
            var target=p.position();p.setPos(target.add(6,0,0));
            if(RECORD)for(int t=0;t<17;t++){atAge(b,t);frame(frames,b,t);}
            atAge(b,17);b.spider.actions.tick(level);frame(frames,b,17);var seed=b.spider.actions.flyingSeed(0);
            h.assertTrue(seed!=null && seed.distanceTo(b.spider.mouth(1))<.001,"seed leaves actual mouth");
            for(int t=18;t<=61 && !b.spiderData().contains("SeedHit");t++){atAge(b,t);b.spider.actions.tick(level);frame(frames,b,t);}
            write("wounded-attacks.json",frames);
            var pods=level.getEntitiesOfClass(GreeneTendrilPod.class,b.getBoundingBox().inflate(40));
            h.assertTrue(pods.size()==1 && pods.get(0).position().distanceTo(target)<1.5,"seed lands at advertised point");
            var pod=pods.get(0);pod.hurt(p.damageSources().playerAttack(p),2);
            h.assertTrue(pod.isRemoved(),"cut planted pod before the fuse");
            // A wall across the ballistic lane intercepts the seed before the floor.
            for(int x=-2;x<=2;x++)for(int y=0;y<20;y++)level.setBlock(center.offset(x,y,19),Blocks.STONE.defaultBlockState(),2);
            state(b,255,0,0);p.setPos(target);b.spider.actions.tick(level);
            for(int t=17;t<61 && !b.spiderData().contains("SeedHit");t++){atAge(b,t);b.spider.actions.tick(level);}
            h.assertTrue(b.spiderData().contains("SeedHit") && level.getEntitiesOfClass(GreeneTendrilPod.class,b.getBoundingBox().inflate(40)).isEmpty(),"wall intercepts seed without spawning through it");
            // The pod's own server tick owns the damage, once on the advertised pulse.
            var home=b.position().add(-5,0,5);
            GreeneTendrilPod.plant(b,home);
            var hazard=level.getEntitiesOfClass(GreeneTendrilPod.class,b.getBoundingBox().inflate(40)).get(0);
            p.setPos(home.add(2,0,0));p.invulnerableTime=0;p.setHealth(p.getMaxHealth());
            var hp=p.getHealth();var nbt=new net.minecraft.nbt.CompoundTag();hazard.addAdditionalSaveData(nbt);
            nbt.putLong("Planted",level.getGameTime()-GreeneTendrilPod.WINDUP+1);hazard.readAdditionalSaveData(nbt);hazard.tick();
            h.assertTrue(p.getHealth()==hp,"the last charge tick must be safe");
            nbt.putLong("Planted",level.getGameTime()-GreeneTendrilPod.WINDUP);hazard.readAdditionalSaveData(nbt);hazard.tick();
            h.assertTrue(p.getHealth()<hp,"exposed player in range takes the detonation");
            hp=p.getHealth();p.invulnerableTime=0;hazard.tick();
            h.assertTrue(hp==p.getHealth(),"no second damage during retraction");hazard.discard();
            GreeneTendrilPod.plant(b,home);hazard=level.getEntitiesOfClass(GreeneTendrilPod.class,b.getBoundingBox().inflate(40)).get(0);
            var cover=BlockPos.containing(home.add(1,0,0));level.setBlock(cover,Blocks.STONE.defaultBlockState(),2);level.setBlock(cover.above(),Blocks.STONE.defaultBlockState(),2);
            hazard.addAdditionalSaveData(nbt);nbt.putLong("Planted",level.getGameTime()-GreeneTendrilPod.WINDUP);hazard.readAdditionalSaveData(nbt);hazard.tick();
            h.assertTrue(p.getHealth()==hp,"block cover stops the detonation");hazard.discard();
            level.removeBlock(cover,false);level.removeBlock(cover.above(),false);
            GreeneTendrilPod.plant(b,home);hazard=level.getEntitiesOfClass(GreeneTendrilPod.class,b.getBoundingBox().inflate(40)).get(0);
            level.removeBlock(BlockPos.containing(home.add(0,-.1,0)),false);hazard.tick();
            h.assertTrue(hazard.isRemoved() && p.getHealth()==hp,"removing the supporting block disarms the pod");
            System.out.println("WOUND_QA fuse 27 safe, 28 hits once, cover and support removal passed");
            System.out.println("WOUND_QA natural near and far selection, lash hit, cut, latched cast, pod cut, wall intercept passed");
            h.succeed();
        }finally{b.discard();level.removePlayerImmediately(p,Entity.RemovalReason.DISCARDED);}
    }
}
