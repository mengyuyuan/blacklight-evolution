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
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.io.*;
import java.util.*;

@GameTestHolder("blacklight") @PrefixGameTestTemplate(false)
public final class SpiderWoundGameTest {
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
        var p=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
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
    @GameTest(template="blackwatch_arena",batch="greene_new_pressure",timeoutTicks=200)
    public static void standingStrikeAndCounterWindow(GameTestHelper h){
        var level=h.getLevel();var center=h.absolutePos(new BlockPos(8,36,8));floor(level,center,25,25);
        var b=boss(h,Vec3.atBottomCenterOf(center));var profile=new GameProfile(UUID.randomUUID(),"LimbProbe");
        var p=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
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
            p.setPos(p.position().add(7,0,0));float playerHp=p.getHealth();
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
            var st=b.spiderData().copy();st.putInt("Action",SpiderActions.Move.FLESH_VOLLEY.ordinal());b.spiderData(st);
            for(int tick:new int[]{17,17,26,35}){atAge(b,tick);b.spider.actions.tick(level);}
            var shots=level.getEntitiesOfClass(GreeneProjectile.class,b.getBoundingBox().inflate(32),v->v.getOwner()==b && v.flesh());
            h.assertTrue(shots.size()==15,"three distinct five-shot flesh waves, same tick cannot fire again: "+shots.size());
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
        var p=new ServerPlayer(level.getServer(),level,profile,ClientInformation.createDefault());
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
