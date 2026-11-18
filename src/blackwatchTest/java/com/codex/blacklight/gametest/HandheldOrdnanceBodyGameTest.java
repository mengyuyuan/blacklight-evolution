package com.codex.blacklight.gametest;

import com.codex.blacklight.combat.*;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.firearms.*;
import com.codex.blacklight.infection.InfectedAttackStyle;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.gametest.*;

/** Real registered projectile ticks and onHit; never calls onHit or hurt directly.
 * Flight gravity alone is disabled to isolate collision, ownership and blast reception. */
@GameTestHolder("blacklight") @PrefixGameTestTemplate(false) @EventBusSubscriber(modid="blacklight")
public final class HandheldOrdnanceBodyGameTest {
    private static final Map<UUID,Fixture> DEATH_WATCH=new HashMap<>();
    @SubscribeEvent public static void snapshotInsideRealFatalImpact(LivingDeathEvent event){
        var f=DEATH_WATCH.get(event.getEntity().getUUID());
        if(f!=null&&event.getEntity()==f.hydra&&event.getSource().getDirectEntity()==f.shot){
            f.deaths++;f.spent=f.shot.saveWithoutId(new CompoundTag());f.impact=f.shot.position();
        }
    }
    private record Ray(Vec3 from,Vec3 direction,Vec3 contact){}
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final List<Entity> entities=new ArrayList<>();
        final List<ChunkPos> forced=new ArrayList<>();
        final Map<BlockPos,BlockState> blocks=new HashMap<>();
        BlacklightHydra hydra;
        Mob owner,witness;
        OrdnanceProjectile shot,restored;
        PlayerTeam team;
        CompoundTag spent;
        Vec3 impact;
        boolean started,done;
        int age,stage,at,deaths,effects;
        float health,witnessHealth;
        Fixture(GameTestHelper h){this.h=h;}
        void start(){
            var center=h.absolutePos(new BlockPos(14,32,14));
            for(int x=(center.getX()-48)>>4;x<=(center.getX()+48)>>4;x++)for(int z=(center.getZ()-48)>>4;z<=(center.getZ()+48)>>4;z++){
                var chunk=new ChunkPos(x,z);
                if(!h.getLevel().getForcedChunks().contains(chunk.toLong())){h.getLevel().setChunkForced(x,z,true);forced.add(chunk);}
                h.getLevel().getChunk(x,z);
            }
            hydra=ModEntities.BLACKLIGHT_HYDRA.get().create(h.getLevel());
            hydra.setNoAi(true);hydra.setNoGravity(true);hydra.setBodySize(2);
            hydra.setPos(Vec3.atBottomCenterOf(center));add(hydra);hydra.updateParts();
            owner=EntityType.PIG.create(h.getLevel());owner.setNoAi(true);owner.setNoGravity(true);
            owner.setPos(hydra.position().add(20,0,20));add(owner);
            health=hydra.getHealth();effects=hydra.incomingEffects();started=true;
        }
        void add(Entity entity){h.assertTrue(h.getLevel().addFreshEntity(entity),"fixture entity enters server level");entities.add(entity);}
        void stone(BlockPos p){blocks.putIfAbsent(p,h.getLevel().getBlockState(p));h.getLevel().setBlockAndUpdate(p,Blocks.STONE.defaultBlockState());}
        Ray headRay(){
            var regions=hydra.hurtRegions(0);double radius=WeaponCatalog.THERMOBARIC.radius();
            for(var region:regions)if(region.bone().equals("head"))for(var box:region.boxes())
                for(var outward:List.of(new Vec3(0,1,0),new Vec3(1,0,0),new Vec3(-1,0,0),new Vec3(0,0,1),new Vec3(0,0,-1))){
                    var contact=HydraHurtContact.ray(regions,box.center.add(outward.scale(12)),box.center,InfectedAttackStyle.CRUSH);
                    if(contact==null||!contact.bone().equals("head")||contact.point().distanceTo(hydra.position())<=radius+1
                            ||CombatTargeting.distanceSquared(contact.point(),hydra.getBoundingBox())<=radius*radius)continue;
                    Vec3 from=contact.point().add(outward.scale(1)),to=box.center;
                    if(h.getLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).getType()!=HitResult.Type.MISS)continue;
                    var pick=ProjectileUtil.getEntityHitResult(h.getLevel(),owner,from,to,new AABB(from,to).inflate(.3),e->FirearmsCombat.bulletCandidate(owner,e));
                    if(pick==null||pick.getEntity()==hydra||CombatTargeting.resolveTarget(pick.getEntity())!=hydra||pick.getLocation().distanceTo(contact.point())>.002)continue;
                    return new Ray(from,to.subtract(from).normalize(),contact.point());
                }
            throw new AssertionError("fixture requires an exposed actual head surface outside the old root-box blast query");
        }
        void launch(Vec3 from,Vec3 direction){
            shot=new OrdnanceProjectile(h.getLevel(),owner,WeaponDefinition.Kind.THERMOBARIC);
            shot.setPos(from);shot.setNoGravity(true);shot.setDeltaMovement(direction.scale(1.5));add(shot);
        }
        void pass(){done=true;close();h.succeed();}
        public void close(){
            if(hydra!=null)DEATH_WATCH.remove(hydra.getUUID());entities.forEach(Entity::discard);
            if(team!=null){h.getLevel().getScoreboard().removePlayerTeam(team);team=null;}
            blocks.forEach((p,state)->h.getLevel().setBlockAndUpdate(p,state));blocks.clear();
            for(var chunk:forced)h.getLevel().setChunkForced(chunk.x,chunk.z,false);forced.clear();
        }
    }
    private static void run(GameTestHelper h,Consumer<Fixture> tick){
        var f=new Fixture(h);
        h.onEachTick(()->{
            if(f.done||h.getTick()<20)return;
            try{if(!f.started)f.start();tick.accept(f);f.age++;h.assertTrue(f.done||f.age<30,"real projectile impact and post-load cleanup finish in bounded fixture time");}
            catch(RuntimeException|AssertionError failure){f.done=true;f.close();throw failure;}
        });
    }

    @GameTest(template="blackwatch_facility_arena",batch="handheldThermalHead",timeoutTicks=70)
    public static void realHeadImpactSelectsThermalDeathAndCallbackSpentSaveCannotReplay(GameTestHelper h){
        run(h,f->{
            if(f.age==0){
                var ray=f.headRay();f.hydra.setHealth(1);DEATH_WATCH.put(f.hydra.getUUID(),f);f.launch(ray.from(),ray.direction());
            }
            if(f.stage==0&&f.shot.isRemoved()){
                h.assertTrue(!f.hydra.isAlive()&&f.hydra.nativeDying()&&f.hydra.nativeDeathKind()==HydraDeath.THERMOBARIC,"actual far-head projectile blast reaches the body and selects native thermal death");
                h.assertTrue(f.deaths==1&&f.spent!=null&&f.spent.getBoolean("OrdnanceDetonated"),"fatal callback sees one already committed impact");
                h.assertTrue(f.impact.distanceTo(f.hydra.position())>WeaponCatalog.THERMOBARIC.radius(),"blast callback position is on the high part, outside root-distance reception");
                var tag=f.spent.copy();tag.remove("UUID");
                f.restored=new OrdnanceProjectile(ModEntities.MILITARY_ORDNANCE.get(),h.getLevel());f.restored.load(tag);f.add(f.restored);
                f.witness=EntityType.PIG.create(h.getLevel());f.witness.setNoAi(true);f.witness.setNoGravity(true);f.witness.setPos(f.impact);f.add(f.witness);
                f.witnessHealth=f.witness.getHealth();f.at=f.age;f.stage=1;
            }else if(f.stage==1&&f.age-f.at>=3){
                h.assertTrue(f.restored.isRemoved()&&f.witness.getHealth()==f.witnessHealth&&f.deaths==1,"spent callback snapshot is discarded before collision and cannot produce another damage pass");f.pass();
            }
        });
    }

    @GameTest(template="blackwatch_facility_arena",batch="handheldThermalCover",timeoutTicks=70)
    public static void realTerrainImpactCannotBlastNearbyBodyThroughSolidCeiling(GameTestHelper h){
        run(h,f->{
            if(f.age==0){
                var regions=f.hydra.hurtRegions(0);AABB bounds=regions.get(0).bounds();
                for(var r:regions)bounds=bounds.minmax(r.bounds());
                var top=regions.stream().flatMap(r->r.boxes().stream()).max(Comparator.comparingDouble(b->b.bounds.maxY)).orElseThrow();
                int y=(int)Math.ceil(bounds.maxY)+1;var block=BlockPos.containing(top.center.x,y,top.center.z);
                for(int x=(int)Math.floor(bounds.minX)-2;x<=(int)Math.ceil(bounds.maxX)+2;x++)for(int z=(int)Math.floor(bounds.minZ)-2;z<=(int)Math.ceil(bounds.maxZ)+2;z++)f.stone(new BlockPos(x,y,z));
                Vec3 explosion=new Vec3(block.getX()+.5,y+1.04,block.getZ()+.5),nearest=CombatTargeting.closestPoint(explosion,f.hydra);
                h.assertTrue(explosion.distanceTo(nearest)<WeaponCatalog.THERMOBARIC.radius(),"actual body surface is in range behind the cover");
                h.assertTrue(h.getLevel().clip(new ClipContext(explosion,nearest,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,f.owner)).getType()==HitResult.Type.BLOCK,"solid ceiling lies between impact and receiving surface");
                f.launch(explosion.add(0,2,0),new Vec3(0,-1,0));
            }
            if(f.shot.isRemoved()){
                h.assertTrue(f.hydra.getHealth()==f.health&&f.hydra.incomingEffects()==f.effects&&!f.hydra.nativeDying(),"real terrain explosion does not pass through solid cover to any other body region");f.pass();
            }
        });
    }

    @GameTest(template="blackwatch_facility_arena",batch="handheldThermalAlly",timeoutTicks=70)
    public static void realPartImpactUsesParentFriendshipWithoutDamagingIt(GameTestHelper h){
        run(h,f->{
            if(f.age==0){
                var ray=f.headRay();var scoreboard=h.getLevel().getScoreboard();
                f.team=scoreboard.addPlayerTeam("ord"+UUID.randomUUID().toString().substring(0,8));
                scoreboard.addPlayerToTeam(f.owner.getScoreboardName(),f.team);scoreboard.addPlayerToTeam(f.hydra.getScoreboardName(),f.team);
                h.assertTrue(FirearmsCombat.protectedTarget(f.owner,f.hydra),"friendship is present on the living parent, not assumed on a part");f.launch(ray.from(),ray.direction());
            }
            if(f.shot.isRemoved()){
                h.assertTrue(f.hydra.getHealth()==f.health&&f.hydra.incomingEffects()==f.effects&&!f.hydra.nativeDying(),"a real physical part hit consumes the projectile while parent protection prevents damage");f.pass();
            }
        });
    }
}
