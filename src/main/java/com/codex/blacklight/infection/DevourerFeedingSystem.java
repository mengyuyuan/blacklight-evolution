package com.codex.blacklight.infection;

import com.codex.blacklight.combat.ConsumeSequence;
import com.codex.blacklight.combat.HitReaction;
import com.codex.blacklight.combat.ImpactControl;
import com.codex.blacklight.entity.*;
import com.codex.blacklight.power.CombatScale;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import com.codex.blacklight.combat.ForgeEntityTickEvent;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Finite live prey, an interruptible paired sequence, and credit only for an accepted kill. */
@EventBusSubscriber(modid="blacklight")
public final class DevourerFeedingSystem {
    public static final int FEED_TICKS=HiveFeedingMotion.END,FEED_INTERVAL=500,MAX_BIOMASS=60;
    private static final String FLAG="BlacklightDevourer",HOME="BlacklightDevourHome";
    private static final String BIOMASS="BlacklightDevourBiomass",MEALS="BlacklightDevourMeals";
    private static final String NEXT="BlacklightDevourNextFeed",CLAIM="BlacklightDevourClaim";
    private static final String RETRY="BlacklightDevourRetryAfter";
    private static final String PAUSE="BlacklightDevourPause";
    private static final Map<BlacklightInfectedHost,Session> ACTIVE=new IdentityHashMap<>();
    private static final Map<Mob,Session> CAPTURED=new IdentityHashMap<>();
    private static final class Session {
        final BlacklightInfectedHost host;final Mob prey;final long acquired;
        long started=-1;float yaw;Vec3 origin;boolean captured;
        final int node; final Vec3 mouth;
        final java.util.List<HiveTendril> tendrils=new java.util.ArrayList<>();
        Session(BlacklightInfectedHost host,Mob prey,long now,int node){this.host=host;this.prey=prey;acquired=now;
            this.node=node;mouth=HiveFeedingMotion.mouth(home(host),node);}
    }
    private DevourerFeedingSystem() {}

    public static boolean isDevourer(BlacklightInfectedHost host) {
        return host.speciesId().equals("minecraft:iron_golem")&&host.getPersistentData().getBoolean(FLAG);
    }
    public static int biomass(BlacklightInfectedHost host) { return Mth.clamp(host.getPersistentData().getInt(BIOMASS),0,MAX_BIOMASS); }
    public static int meals(BlacklightInfectedHost host) { return Math.max(0,host.getPersistentData().getInt(MEALS)); }
    public static int availableGrowthTicks(BlacklightInfectedHost host) { return biomass(host)*300; }
    private static int foodBuffer(BlacklightInfectedHost host) {
        return Math.min(ColossusEvolutionSystem.EVOLUTION_TICKS,1200*HiveAcceleration.multiplier(host));
    }
    private static int mealInterval(BlacklightInfectedHost host) {
        return Math.max(20,FEED_INTERVAL/HiveAcceleration.multiplier(host));
    }
    /** Rescale a pending delay once when the administrator changes this nursery's rate. */
    public static void adjustMealWait(BlacklightInfectedHost host,int before,int after) {
        if(ACTIVE.containsKey(host))return; // A captured victim always gets the full sequence.
        CompoundTag data=host.getPersistentData();long now=host.level().getGameTime();
        if(!data.contains(NEXT))return;
        long remaining=com.codex.blacklight.util.MathCompat.clamp(data.getLong(NEXT)-now,0,FEED_INTERVAL);
        long scaled=(long)Math.ceil(remaining*(double)Mth.clamp(before,1,20)/Mth.clamp(after,1,20));
        data.putLong(NEXT,now+Math.min(FEED_INTERVAL,scaled));
    }
    public static BlockPos home(BlacklightInfectedHost host) {
        return host.getPersistentData().contains(HOME,Tag.TAG_LONG)?BlockPos.of(host.getPersistentData().getLong(HOME)):host.blockPosition();
    }
    private static Vec3 center(BlacklightInfectedHost host) { return Vec3.atBottomCenterOf(home(host)); }
    private static double roamRadius(BlacklightInfectedHost host) {
        return Math.max(2,23-InfectedBossProfile.IRON_GOLEM.visualWidth()*host.bodyScale()*.5-2);
    }
    public static boolean withinTerritory(BlacklightInfectedHost host,Entity target) {
        Vec3 d=target.position().subtract(center(host));double radius=roamRadius(host);
        return d.horizontalDistanceSqr()<=radius*radius&&Math.abs(d.y)<=24;
    }
    /** Incubating hosts stay in their chamber; the fixed hive owns all feeding. */
    public static boolean controls(BlacklightInfectedHost host) {
        return isDevourer(host)&&!host.giantBoss();
    }
    private static boolean recovering(BlacklightInfectedHost host) {
        return host.feedingTargetId()<0&&host.feedingStarted()>=0&&host.level().getGameTime()-host.feedingStarted()<FEED_TICKS+16;
    }
    public static void tick(BlacklightInfectedHost host) {
        if(!(host.level() instanceof ServerLevel level))return;
        if(!isDevourer(host)){release(host);return;}
        if(!host.getPersistentData().contains(HOME,Tag.TAG_LONG))host.getPersistentData().putLong(HOME,host.blockPosition().asLong());
        boolean disabled=!host.isAlive()||host.isNoAi()||host.isPassenger()||host.isVehicle()||host.isLeashed()
                ||com.codex.blacklight.registry.ModAttachments.getData(host, ModAttachments.CONSUME_VISUAL).active()||ImpactControl.stunned(host);
        if(disabled){release(host);return;}
        boolean supplied=DevourerHabitat.canFeed(host);
        host.syncNursery(home(host),DevourerHabitat.nodeMask(host),supplied&&!host.giantBoss());
        if(host.giantBoss()){release(host);return;}
        host.setTarget(null);returnHome(host);
        host.setXxa(0);host.setZza(0);host.setDeltaMovement(host.getDeltaMovement().multiply(0,1,0));
        if(recovering(host))return;
        if(host.feedingTargetId()<0&&host.feedingStarted()>=0)host.setFeedingVisual(-1,-1);
        Session session=ACTIVE.get(host);
        if(!supplied||biomass(host)>=MAX_BIOMASS||level.getDifficulty()==Difficulty.PEACEFUL) {
            release(host);returnHome(host);return;
        }
        if(session!=null){advance(level,session);return;}
        returnHome(host);
        long now=level.getGameTime();CompoundTag data=host.getPersistentData();
        if(!data.contains(NEXT)){data.putLong(NEXT,now+Math.max(5,100/HiveAcceleration.multiplier(host)));return;}
        if(now<data.getLong(NEXT)||!withinTerritory(host,host)
                ||availableGrowthTicks(host)>host.evolutionTicks()+foodBuffer(host))return;
        // Failed searches remain bounded to one per second even in accelerated nurseries.
        data.putLong(NEXT,now+20);
        Vec3 home=center(host);
        Mob prey=level.getEntitiesOfClass(Mob.class,new AABB(home,home).inflate(40),mob->eligible(host,mob,now))
                .stream().filter(mob->feedingNode(host,mob)>=0)
                .min(Comparator.comparingDouble(mob->HiveFeedingMotion.mouth(home(host),feedingNode(host,mob)).distanceToSqr(mob.position()))).orElse(null);
        if(prey==null)return;
        if(prey.getPersistentData().contains(CLAIM))clearClaim(prey);
        CompoundTag claim=new CompoundTag();claim.putUUID("Owner",host.getUUID());claim.putLong("Until",now+200);
        prey.getPersistentData().put(CLAIM,claim);ACTIVE.put(host,new Session(host,prey,now,feedingNode(host,prey)));
        data.remove(PAUSE);
        host.setTarget(null);host.getNavigation().stop();
    }
    private static boolean eligible(BlacklightInfectedHost host,Mob prey,long now) {
        if(prey==host||!prey.isAlive()||prey.isRemoved()||prey.level()!=host.level()||prey.isNoAi()||prey.isInvulnerable()
                ||prey.hasCustomName()||prey.isLeashed()||prey.isPassenger()||prey.isVehicle()
                ||com.codex.blacklight.registry.ModAttachments.getData(prey, ModAttachments.CONSUME_VISUAL).active()||com.codex.blacklight.registry.ModAttachments.getData(prey, ModAttachments.CONSUMING)
                ||com.codex.blacklight.registry.ModAttachments.getData(prey, ModAttachments.GRAB_STAGE)!=0||ConsumeSequence.boss(prey)
                ||prey instanceof HiveTendril||prey instanceof BlacklightHive||prey instanceof BlacklightHunter||prey instanceof BlacklightMutant
                ||prey instanceof BlacklightInfectedHost infected&&(infected.hasMultipartBody()||infected.hostData().getBoolean("Tamed"))
                ||InfectedBossProfile.forSpecies(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(prey.getType()).toString())!=null
                ||prey instanceof TamableAnimal tame&&tame.isTame()
                ||prey instanceof AbstractHorse horse&&horse.isTamed())return false;
        if(prey.getPersistentData().getLong(RETRY)>now)return false;
        if(prey.position().distanceToSqr(center(host))>40*40)return false;
        CompoundTag claim=prey.getPersistentData().getCompound(CLAIM);
        if(claim.hasUUID("Owner")&&!claim.getUUID("Owner").equals(host.getUUID())&&claim.getLong("Until")>now)return false;
        return true;
    }
    private static void returnHome(BlacklightInfectedHost host) {
        if(!withinTerritory(host,host))host.setTarget(null);
        if(host.getTarget()!=null)return;
        Vec3 destination=center(host);
        if(host.position().subtract(destination).horizontalDistanceSqr()>2.5*2.5) {
            if(host.tickCount%10==0||host.getNavigation().isDone())host.getNavigation().moveTo(destination.x,destination.y,destination.z,.9);
        } else host.getNavigation().stop();
    }
    private static boolean clearLine(BlacklightInfectedHost host,Vec3 mouth,Mob prey) {
        var level=host.level();var to=prey.getBoundingBox().getCenter();
        if(!level.hasChunksAt(BlockPos.containing(Math.min(mouth.x,to.x),Math.min(mouth.y,to.y),Math.min(mouth.z,to.z)),
                BlockPos.containing(Math.max(mouth.x,to.x),Math.max(mouth.y,to.y),Math.max(mouth.z,to.z))))return false;
        return level.clip(new net.minecraft.world.level.ClipContext(mouth,to,
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,host)).getType()==net.minecraft.world.phys.HitResult.Type.MISS;
    }
    private static int feedingNode(BlacklightInfectedHost host,Mob prey) {
        int mask=DevourerHabitat.nodeMask(host),best=-1;double distance=24*24;
        for(int i=0;i<4;i++)if((mask&(1<<i))!=0){
            Vec3 mouth=HiveFeedingMotion.mouth(home(host),i);double d=mouth.distanceToSqr(prey.position());
            if(d<distance&&clearLine(host,mouth,prey)){best=i;distance=d;}
        }
        return best;
    }
    private static void advance(ServerLevel level,Session session) {
        var host=session.host;var prey=session.prey;long now=level.getGameTime();
        CompoundTag claim=prey.getPersistentData().getCompound(CLAIM);
        if(!eligible(host,prey,now)||!claim.hasUUID("Owner")||!claim.getUUID("Owner").equals(host.getUUID())
                ||(DevourerHabitat.nodeMask(host)&(1<<session.node))==0
                ||!clearLine(host,session.mouth,prey)||session.mouth.distanceToSqr(prey.position())>26*26) {
            abort(session,"捕食口被破坏、猎物失效或拖拽路径被遮挡");return;
        }
        claim.putLong("Until",now+200);
        if(session.started<0) {
            session.started=now;session.origin=prey.position();
            session.yaw=(float)(Mth.atan2(prey.getZ()-session.mouth.z,prey.getX()-session.mouth.x)*Mth.RAD_TO_DEG)-90;
            host.setFeedingNode(session.node);host.setFeedingVisual(prey.getId(),now);
            level.playSound(null,BlockPos.containing(session.mouth),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,.9F,.55F);
            for(int i=0;i<3;i++){
                HiveTendril tendril=com.codex.blacklight.registry.ModEntities.HIVE_TENDRIL.get().create(level);
                if(tendril==null){abort(session,"捕食触手无法生成");return;}
                tendril.bind(host,session.mouth,prey.getBoundingBox().getCenter(),(i+1)/4.0);
                session.tendrils.add(tendril);
                if(!level.addFreshEntity(tendril)){abort(session,"捕食触手生成被阻止");return;}
            }
        }
        int age=(int)(now-session.started);
        if(age>=HiveFeedingMotion.CONTACT&&!session.captured) {
            session.captured=true;session.origin=prey.position();
            claim.putBoolean("HadGravityFlag",true);claim.putBoolean("NoGravity",prey.isNoGravity());
            prey.setNoGravity(true);ImpactControl.cancel(prey);prey.getNavigation().stop();prey.setTarget(null);
            prey.walkAnimation.setSpeed(0);CAPTURED.put(prey,session);recoil(prey,now,session.yaw);
        }
        if(session.captured) {
            Vec3 desired=HiveFeedingMotion.destination(session.origin,session.mouth,prey.getBbHeight(),age);
            Vec3 movement=desired.subtract(prey.position());
            if(movement.lengthSqr()>1.2*1.2)movement=movement.normalize().scale(1.2);
            Vec3 before=prey.position();prey.move(MoverType.SELF,movement);prey.setDeltaMovement(Vec3.ZERO);prey.hurtMarked=true;prey.fallDistance=0;
            prey.yBodyRot=session.yaw+180;prey.yHeadRot=prey.yBodyRot;prey.setYRot(prey.yBodyRot);
            if(movement.lengthSqr()>.025&&prey.position().distanceToSqr(before)<.0004){abort(session,"拖拽路径被地形阻挡，已释放猎物");return;}
            if(age==46||age==74||age==98)recoil(prey,now,session.yaw);
            if(age>=HiveFeedingMotion.PULL_END&&age%5==0)level.sendParticles(ParticleTypes.SCULK_SOUL,prey.getX(),prey.getY()+prey.getBbHeight()*.5,prey.getZ(),3,.25,.3,.25,.01);
        }
        Vec3 end=prey.getBoundingBox().getCenter();
        double reach=HiveFeedingMotion.smooth((age-4)/16.0);
        for(int i=0;i<session.tendrils.size();i++){
            HiveTendril part=session.tendrils.get(i);
            if(!part.isAlive()||part.isRemoved()){abort(session,"捕食触手断裂，已释放猎物");return;}
            part.follow(session.mouth,session.mouth.lerp(end,reach),(i+1)/4.0);
        }
        if(age>=FEED_TICKS){
            Vec3 finish=HiveFeedingMotion.destination(session.origin,session.mouth,prey.getBbHeight(),FEED_TICKS);
            if(prey.position().distanceToSqr(finish)>1){abort(session,"猎物未到达吸收口，未结算养分");return;}
            complete(level,session);
        }
    }
    public static void sever(BlacklightInfectedHost host) {
        Session session=ACTIVE.get(host);
        if(session!=null)abort(session,"捕食触手被击断，已释放猎物");
    }
    public static boolean active(BlacklightInfectedHost host) { return ACTIVE.containsKey(host); }
    private static void recoil(Mob prey,long now,float yaw) {
        com.codex.blacklight.registry.ModAttachments.setData(prey, ModAttachments.HIT_REACTION,new HitReaction(now,19,HitReaction.HEAVY,yaw));com.codex.blacklight.registry.ModAttachments.syncData(prey, ModAttachments.HIT_REACTION);
    }
    private static void complete(ServerLevel level,Session session) {
        var host=session.host;var prey=session.prey;
        // Snapshot before death hooks alter attributes or remove the entity.
        int units=Mth.clamp((int)Math.floor(CombatScale.originalMaximum(prey)/5),1,8);
        float lethal=Math.min(1_000_000,(prey.getMaxHealth()+prey.getAbsorptionAmount()+10)*20/CombatScale.units(prey));
        boolean accepted=prey.isAlive()&&prey.hurt(host.damageSources().mobAttack(host),lethal);
        // DYING is set after LivingDeath vetoes. A refused damage/death event earns nothing.
        boolean killed=accepted&&!prey.isAlive()&&prey.getPose()==Pose.DYING;
        // Keep normal death/experience bookkeeping, but never restore a full-sized
        // corpse when the feeding target is cleared after its visible contraction.
        if(killed)prey.setInvisible(true);
        release(host);host.getPersistentData().putLong(NEXT,level.getGameTime()+mealInterval(host));
        if(!killed){host.getPersistentData().putString(PAUSE,"上次吞噬伤害或死亡结算被阻止，未获得生物质");return;}
        host.getPersistentData().remove(PAUSE);
        host.setFeedingNode(session.node);host.setFeedingVisual(-1,session.started);
        CompoundTag data=host.getPersistentData();data.putInt(BIOMASS,Math.min(MAX_BIOMASS,biomass(host)+units));
        data.putInt(MEALS,(int)Math.min(Integer.MAX_VALUE,(long)meals(host)+1));
        // CombatScale multiplies healing events by recipient units; divide once here.
        host.heal(host.getMaxHealth()*.1F/CombatScale.units(host));
        level.playSound(null,BlockPos.containing(session.mouth),SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.HOSTILE,1.2F,.55F);
        level.sendParticles(ParticleTypes.SCULK_SOUL,prey.getX(),prey.getY()+prey.getBbHeight()*.5,prey.getZ(),24,.6,.7,.6,.045);
        level.sendParticles(ParticleTypes.SMOKE,prey.getX(),prey.getY()+prey.getBbHeight()*.5,prey.getZ(),14,.5,.5,.5,.02);
    }
    private static void abort(Session session,String reason) {
        session.prey.getPersistentData().putLong(RETRY,session.host.level().getGameTime()+600);
        session.host.getPersistentData().putLong(NEXT,session.host.level().getGameTime()+100);
        session.host.getPersistentData().putString(PAUSE,reason);release(session.host);
    }
    public static void release(BlacklightInfectedHost host) {
        Session session=ACTIVE.remove(host);host.setFeedingVisual(-1,-1);host.setFeedingNode(-1);
        if(session==null)return;
        CAPTURED.remove(session.prey);host.getNavigation().stop();
        for(HiveTendril part:session.tendrils)part.discard();
        CompoundTag claim=session.prey.getPersistentData().getCompound(CLAIM);
        if(claim.hasUUID("Owner")&&claim.getUUID("Owner").equals(host.getUUID()))clearClaim(session.prey);
    }
    private static void clearClaim(Mob mob) {
        CompoundTag claim=mob.getPersistentData().getCompound(CLAIM);
        if(claim.getBoolean("HadGravityFlag"))mob.setNoGravity(claim.getBoolean("NoGravity"));
        mob.getPersistentData().remove(CLAIM);mob.setDeltaMovement(Vec3.ZERO);mob.hurtMarked=true;
    }
    @SubscribeEvent public static void freeze(com.codex.blacklight.combat.ForgeEntityTickEvent.Pre event) {
        if(!(event.getEntity() instanceof Mob prey)||prey.level().isClientSide)return;
        Session session=CAPTURED.get(prey);if(session==null)return;
        if(!DevourerHabitat.canFeed(session.host)||(DevourerHabitat.nodeMask(session.host)&(1<<session.node))==0) {abort(session,"供养中断，已释放猎物");return;}
        if(!session.host.isAlive()||!prey.isAlive()||com.codex.blacklight.registry.ModAttachments.getData(prey, ModAttachments.CONSUME_VISUAL).active()) {abort(session,"吞噬者或猎物死亡，或猎物已被玩家抓取");return;}
        // Keep interpolation endpoints current while AI/gravity are held for the paired motion.
        prey.xo=prey.getX();prey.yo=prey.getY();prey.zo=prey.getZ();
        prey.xOld=prey.getX();prey.yOld=prey.getY();prey.zOld=prey.getZ();
        prey.yBodyRotO=prey.yBodyRot;prey.yHeadRotO=prey.yHeadRot;
        event.setCanceled(true);
    }
    @SubscribeEvent public static void join(EntityJoinLevelEvent event) {
        if(!event.getLevel().isClientSide&&event.getEntity() instanceof Mob mob&&mob.getPersistentData().contains(CLAIM))clearClaim(mob);
    }
    @SubscribeEvent public static void leave(EntityLeaveLevelEvent event) {
        if(event.getLevel().isClientSide)return;
        if(event.getEntity() instanceof BlacklightInfectedHost host)release(host);
        if(event.getEntity() instanceof BlacklightHive hive)for(Session session:List.copyOf(ACTIVE.values()))
            if(session.host.level()==hive.level()&&home(session.host).asLong()==hive.getPersistentData().getLong("BlacklightHiveHome"))abort(session,"蜂巢核心离开已加载区域");
        for(Session session:List.copyOf(ACTIVE.values()))if(session.prey==event.getEntity())abort(session,"猎物离开当前已加载区域");
    }
    @SubscribeEvent public static void stop(ServerStoppingEvent event) {
        for(var host:List.copyOf(ACTIVE.keySet()))release(host);
    }

    /** Requested diagnostics only: no claims, timer changes, entity creation or chunk loading. */
    public static String describe(BlacklightInfectedHost host) {
        if(!(host.level() instanceof ServerLevel level)||!isDevourer(host))return "不是巢内吞噬者";
        long now=level.getGameTime();
        if(host.giantBoss())return "巨构已完成，不再吞噬成长";
        if(!host.isAlive())return "吞噬者已死亡";
        if(host.isNoAi()||host.isPassenger()||host.isVehicle()||host.isLeashed()
                ||com.codex.blacklight.registry.ModAttachments.getData(host, ModAttachments.CONSUME_VISUAL).active()||ImpactControl.stunned(host))return "吞噬者被控制、骑乘、拴绳或停用 AI";
        if(level.getDifficulty()==Difficulty.PEACEFUL)return "和平难度暂停进食";
        if(!DevourerHabitat.suppliesLoaded(host))return "供养节点或核心区块未加载，暂停进食";
        if(DevourerHabitat.liveNodes(host)<DevourerHabitat.REQUIRED_NODES)return "供养节点不足 2 个，暂停进食";
        if(!DevourerHabitat.canFeed(host))return "所属蜂巢核心缺失或无法供养";
        if(biomass(host)>=MAX_BIOMASS)return "生物质已满 60，等待成长条件满足";
        Session session=ACTIVE.get(host);
        if(session!=null)return "蜂巢捕食口 "+(session.node+1)+"："+session.prey.getDisplayName().getString()+"（"+Math.min(FEED_TICKS,Math.max(0,now-session.started))+"/"+FEED_TICKS+" tick）";
        if(recovering(host))return "吞噬动作收势中";
        if(!withinTerritory(host,host))return "正在返回巢室中心";
        if(availableGrowthTicks(host)>host.evolutionTicks()+foodBuffer(host))
            return "已有 "+availableGrowthTicks(host)/20+" 秒成长养分，等待蜂巢成熟或成长消耗后再进食";
        String last=host.getPersistentData().getString(PAUSE);
        long remaining=host.getPersistentData().getLong(NEXT)-now;
        if(remaining>0)return "下一次觅食约 "+(remaining+19)/20+" 秒后"+(last.isEmpty()?"":"；上次停止："+last);
        var prey=level.getEntitiesOfClass(Mob.class,new AABB(center(host),center(host)).inflate(40),mob->eligible(host,mob,now));
        if(prey.isEmpty())return "巢室周围没有可吞噬的活猎物；宠物、命名或受保护生物不计入"+(last.isEmpty()?"":"；上次停止："+last);
        if(prey.stream().noneMatch(mob->feedingNode(host,mob)>=0))return "猎物不在完整捕食口的 24 格视线范围内";
        return "蜂巢正在寻找可捕食的猎物";
    }
}
