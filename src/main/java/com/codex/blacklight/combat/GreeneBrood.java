package com.codex.blacklight.combat;

import com.codex.blacklight.entity.BlacklightHunter;
import com.codex.blacklight.entity.BlacklightHydra;
import com.codex.blacklight.entity.EncounterMob;
import com.codex.blacklight.mixin.LivingDeathAccess;
import com.codex.blacklight.registry.ModEntities;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.TickEvent;

/** P1 e08m04 brood ownership. Mission waveCount is supplied by the mother's cycle.
 * The source uses three total UndergroundBrawlers (79660 pc21..23), despite an
 * unused WAVEONETOTAL=6. TimesSquare locators and Hunter's missing underground
 * spawn animation are adapted to collision-checked, already loaded MC ground.
 * Unknown/unloaded child UUIDs are retained, never interpreted as deaths. */
@EventBusSubscriber(modid="blacklight")
public final class GreeneBrood {
    private static final String OWNER="BlacklightGreeneOwner",KIND="BlacklightGreeneKind",SLOT="BlacklightGreeneSlot",WAVE="BlacklightGreeneWave";
    private static final int HYDRA=1,HUNTER=2,ALIVE=0,DEAD=1,RETIRING=2;
    public static final int HYDRA_RESPAWN_TICKS=400;
    private final Slot[] hydras={new Slot(),new Slot(),new Slot(),new Slot()};
    private final List<Wave> hunters=new ArrayList<>();
    private UUID owner;
    private String dimension="";
    private int lastSubmergeWave,lastEmergeWave;
    private boolean respawnEnabled,firstDeathSent,firstDeathPending,disposed;
    private long nextAttempt;
    private ServerLevel boundLevel;
    private static final class Slot {UUID id;boolean wanted,raised;long due;}
    private static final class Wave {final int number;final UUID[] ids=new UUID[3];Wave(int number){this.number=number;}}

    public void onSubmerge(EncounterMob mother,int sourceWave){
        if(!bind(mother)||disposed||sourceWave<=lastSubmergeWave)return;
        collectDeaths();lastSubmergeWave=Math.max(1,sourceWave);hunters.add(new Wave(lastSubmergeWave));
        for(var slot:hydras){slot.raised=false;applyHydraState(slot);}
        nextAttempt=0;tick(mother);
    }
    public void onEmerge(EncounterMob mother,int sourceWave){
        if(!bind(mother)||disposed||sourceWave<=lastEmergeWave)return;
        collectDeaths();lastEmergeWave=Math.max(1,sourceWave);
        // HYDRAS_PER_WAVE[1]={}; [2..5]={1,2,3,4}. Lua nil beyond5 defaults to all4 in MotherSpawnHydras.
        if(sourceWave>=2)for(var slot:hydras){slot.wanted=true;if(slot.id==null)slot.due=0;}
        nextAttempt=0;tick(mother);
    }
    /** Source77124 resets this on entering Combat, not on each individual Hydra respawn. */
    public void onCombatStarted(){firstDeathSent=false;firstDeathPending=false;}
    public void setRespawnEnabled(boolean value){
        respawnEnabled=!disposed&&value;
        if(boundLevel!=null&&owner!=null&&!disposed)Ledger.get(boundLevel).respawn(owner,respawnEnabled);
        // Already scheduled source callbacks are not canceled by toggling the enable flag.
    }
    public boolean pollFirstHydraDeath(){boolean result=firstDeathPending;firstDeathPending=false;return result;}
    public void raiseHydra(EncounterMob mother,int oneBasedSlot){
        if(!bind(mother)||disposed||oneBasedSlot<1||oneBasedSlot>4)return;
        var slot=hydras[oneBasedSlot-1];if(slot.id==null&&!slot.wanted)return;slot.raised=true;
        // RaiseMotherHydra only operates an existing slot. It does not create a missing Hydra.
        applyHydraState(slot);
    }
    public void tick(EncounterMob mother){
        if(!bind(mother)||!active(mother))return;
        collectDeaths();for(var slot:hydras)applyHydraState(slot);long now=boundLevel.getGameTime();
        if(now<nextAttempt)return;nextAttempt=now+40; // bounded MC retry when a locator is obstructed/unloaded
        for(int i=0;i<4;i++){
            var slot=hydras[i];
            if(slot.id==null&&slot.wanted&&now>=slot.due){
                var child=spawn(mother,HYDRA,i+1,lastEmergeWave,slot.raised);
                if(!active(mother))return;
                if(child!=null){slot.id=child.getUUID();slot.wanted=false;slot.due=0;}
            }
        }
        for(var wave:new ArrayList<>(hunters))for(int i=0;i<3;i++)if(wave.ids[i]==null){
            var child=spawn(mother,HUNTER,i+1,wave.number,true);
            if(!active(mother))return;
            if(child!=null)wave.ids[i]=child.getUUID();
        }
        // Completed waves do not need another spawn pass. Their identities remain in the world ledger.
        hunters.removeIf(w->Arrays.stream(w.ids).allMatch(Objects::nonNull));
    }
    /** Call only on accepted owner death/task termination. A chunk unload must only save this object. */
    public void dispose(EncounterMob mother){
        if(!bind(mother)||disposed)return;disposed=true;respawnEnabled=false;hunters.clear();
        for(var slot:hydras){slot.wanted=false;slot.raised=false;slot.due=0;}
        var ledger=Ledger.get(boundLevel);ledger.respawn(owner,false);
        for(var row:new ArrayList<>(ledger.children.values()))if(row.owner.equals(owner)){
            if(row.status==DEAD){ledger.children.remove(row.id);continue;}
            row.status=RETIRING;var child=entity(row.id);if(child!=null)retire(boundLevel,child,row);
        }
        ledger.respawns.remove(owner);ledger.setDirty();
    }
    private boolean bind(EncounterMob mother){
        if(mother==null||!mother.asset().equals("elizabeth_greene")||!(mother.level() instanceof ServerLevel level))return false;
        String key=level.dimension().location().toString();
        if(owner!=null&&(!owner.equals(mother.getUUID())||!dimension.equals(key)))return false;
        owner=mother.getUUID();dimension=key;boundLevel=level;var ledger=Ledger.get(level);if(!disposed)ledger.respawn(owner,respawnEnabled);
        // Recover the same identity after the entity and ledger were saved in different orders.
        for(var row:ledger.children.values())if(row.owner.equals(owner)&&row.kind==HYDRA&&row.slot>=1&&row.slot<=4&&row.status!=RETIRING){
            var slot=hydras[row.slot-1];if(slot.id==null)slot.id=row.id;
        }
        for(var wave:hunters)for(var row:ledger.children.values())
            if(row.owner.equals(owner)&&row.kind==HUNTER&&row.status!=RETIRING&&row.wave==wave.number&&row.slot>=1&&row.slot<=3
                    &&wave.ids[row.slot-1]==null)wave.ids[row.slot-1]=row.id;
        return true;
    }
    private Entity entity(UUID id){return id==null||boundLevel==null?null:boundLevel.getEntity(id);}
    /** Persist commands even if only the child chunk is unloaded. Applying the
     * same desired state must not restart rise/burrow or interrupt surface combat. */
    private void applyHydraState(Slot slot){
        if(slot.id==null)return;var ledger=Ledger.get(boundLevel);var row=ledger.children.get(slot.id);
        if(row==null||row.kind!=HYDRA||row.status!=ALIVE||!row.owner.equals(owner))return;
        if(!Objects.equals(row.raised,slot.raised)){row.raised=slot.raised;ledger.setDirty();}
        var child=entity(slot.id);if(child instanceof BlacklightHydra hydra)hydra.setGreeneUnderground(owner,!slot.raised);
    }
    private void collectDeaths(){
        var ledger=Ledger.get(boundLevel);
        for(var slot:hydras){
            if(slot.id==null)continue;var row=ledger.children.get(slot.id);
            if(row==null||row.status!=DEAD||!row.owner.equals(owner))continue;
            if(row.firstDeath&&!firstDeathSent){firstDeathSent=true;firstDeathPending=true;}
            slot.id=null;slot.wanted=row.respawn;slot.raised=row.respawn;slot.due=row.diedAt+HYDRA_RESPAWN_TICKS;
            ledger.children.remove(row.id);ledger.setDirty();
        }
    }
    private boolean active(EncounterMob mother){return !disposed&&mother.isAlive()&&!mother.isRemoved()&&mother.level()==boundLevel;}
    private Mob spawn(EncounterMob mother,int kind,int slot,int wave,boolean raised){
        if(!active(mother))return null;
        Mob child=kind==HYDRA?ModEntities.BLACKLIGHT_HYDRA.get().create(boundLevel):ModEntities.BLACKLIGHT_HUNTER.get().create(boundLevel);
        if(child==null)return null;
        // Source Hydra slots: angle=2pi*slot/4+.8, radius12. Hunter locators use a documented MC ring.
        double base=kind==HYDRA?Math.PI*2*slot/4+.8:Math.PI*2*(slot-1)/3;
        Vec3 point=null;
        for(double radius:kind==HYDRA?new double[]{12,16,20,24}:new double[]{16,20,24,28}){
            for(double turn:new double[]{0,.18,-.18,.36,-.36}){
                int x=(int)Math.floor(mother.getX()+Math.cos(base+turn)*radius),z=(int)Math.floor(mother.getZ()+Math.sin(base+turn)*radius);
                var chunk=boundLevel.getChunkSource().getChunkNow(x>>4,z>>4);if(chunk==null)continue;
                int y=chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+1;
                var pos=new BlockPos(x,y,z);if(!mother.isWithinRestriction(pos)||!boundLevel.isPositionEntityTicking(pos))continue;
                child.moveTo(x+.5,y,z+.5,mother.getYRot(),0);
                if(clearGround(boundLevel,child)){point=child.position();break;}
            }
            if(point!=null)break;
        }
        if(point==null){child.discard();return null;}
        if(child instanceof BlacklightHydra hydra&&!hydra.bindGreene(owner,!raised)){child.discard();return null;}
        child.setPersistenceRequired();
        if(kind==HUNTER&&mother.getTarget()!=null&&mother.getTarget().isAlive())child.setTarget(mother.getTarget());
        var tag=child.getPersistentData();tag.putUUID(OWNER,owner);tag.putInt(KIND,kind);tag.putInt(SLOT,slot);tag.putInt(WAVE,wave);
        var ledger=Ledger.get(boundLevel);var row=new Child(child.getUUID(),owner,kind,slot,wave);if(kind==HYDRA)row.raised=raised;ledger.children.put(row.id,row);ledger.setDirty();
        if(!boundLevel.addFreshEntity(child)){ledger.children.remove(row.id);ledger.setDirty();clearMarker(child);child.discard();if(!active(mother))dispose(mother);return null;}
        // EntityJoinLevel callbacks may synchronously terminate the owner. The
        // child was not discoverable in the level during that dispose pass.
        if(!active(mother)){
            dispose(mother);var registered=ledger.children.get(row.id);
            if(registered!=null){registered.status=RETIRING;retire(boundLevel,child,registered);ledger.setDirty();}
            return null;
        }
        return child;
    }
    private static boolean clearGround(ServerLevel level,Mob mob){
        AABB foot=mob.getBoundingBox(),box=foot;
        if(mob instanceof BlacklightHydra hydra){
            // Reserve the visible emergence sweep, including the neck/horns outside the root AABB.
            // Below-ground authored motion is intentional; only the portion above the surface must be clear.
            for(int age=0;age<=80;age+=4)for(var part:HydraContact.body(
                    com.codex.blacklight.city.HydraMotion.INSTANCE.pose(5,age,-1,0),mob.position(),mob.getYRot(),hydra.visualScale())){
                var b=part.bounds;if(b.maxY>mob.getY()+.1)box=box.minmax(new AABB(b.minX,Math.max(mob.getY()+.1,b.minY),b.minZ,b.maxX,b.maxY,b.maxZ));
            }
        }
        int minX=(int)Math.floor(box.minX),maxX=(int)Math.floor(box.maxX-1e-6);
        int minZ=(int)Math.floor(box.minZ),maxZ=(int)Math.floor(box.maxZ-1e-6),y=(int)Math.floor(box.minY);
        if(y<=level.getMinBuildHeight()||box.maxY>=level.getMaxBuildHeight())return false;
        for(int x=minX-1;x<=maxX+1;x++)for(int z=minZ-1;z<=maxZ+1;z++){
            if(level.getChunkSource().getChunkNow(x>>4,z>>4)==null)return false;
            if(!level.getWorldBorder().isWithinBounds(new BlockPos(x,y,z)))return false;
        }
        for(int x=(int)Math.floor(foot.minX);x<=Math.floor(foot.maxX-1e-6);x++)for(int z=(int)Math.floor(foot.minZ);z<=Math.floor(foot.maxZ-1e-6);z++){
            var floor=new BlockPos(x,y-1,z);var state=level.getBlockState(floor);
            if(!state.getFluidState().isEmpty()||!state.isFaceSturdy(level,floor,Direction.UP))return false;
        }
        return level.noCollision(mob,box)&&!level.containsAnyLiquid(box)
                &&level.getEntities(mob,box,e->e.isAlive()&&!e.isSpectator()).isEmpty();
    }
    public CompoundTag save(){
        var tag=new CompoundTag();tag.putInt("Schema",1);if(owner!=null)tag.putUUID("Owner",owner);tag.putString("Dimension",dimension);
        tag.putInt("SubmergeWave",lastSubmergeWave);tag.putInt("EmergeWave",lastEmergeWave);tag.putBoolean("Respawn",respawnEnabled);
        tag.putBoolean("FirstSent",firstDeathSent);tag.putBoolean("FirstPending",firstDeathPending);tag.putBoolean("Disposed",disposed);tag.putLong("NextAttempt",nextAttempt);
        var slots=new ListTag();for(var slot:hydras){var s=new CompoundTag();if(slot.id!=null)s.putUUID("Id",slot.id);s.putBoolean("Wanted",slot.wanted);s.putBoolean("Raised",slot.raised);s.putLong("Due",slot.due);slots.add(s);}tag.put("Hydras",slots);
        var waves=new ListTag();for(var wave:hunters){var w=new CompoundTag();w.putInt("Wave",wave.number);for(int i=0;i<3;i++)if(wave.ids[i]!=null)w.putUUID("Child"+i,wave.ids[i]);waves.add(w);}tag.put("Hunters",waves);return tag;
    }
    public void load(CompoundTag tag){
        owner=tag.hasUUID("Owner")?tag.getUUID("Owner"):null;dimension=tag.getString("Dimension");boundLevel=null;
        lastSubmergeWave=Math.max(0,tag.getInt("SubmergeWave"));lastEmergeWave=Math.max(0,tag.getInt("EmergeWave"));
        respawnEnabled=tag.getBoolean("Respawn");firstDeathSent=tag.getBoolean("FirstSent");firstDeathPending=tag.getBoolean("FirstPending");disposed=tag.getBoolean("Disposed");nextAttempt=Math.max(0,tag.getLong("NextAttempt"));
        var slots=tag.getList("Hydras",Tag.TAG_COMPOUND);for(int i=0;i<4;i++){var s=i<slots.size()?slots.getCompound(i):new CompoundTag();var slot=hydras[i];slot.id=s.hasUUID("Id")?s.getUUID("Id"):null;slot.wanted=s.getBoolean("Wanted");slot.raised=s.getBoolean("Raised");slot.due=Math.max(0,s.getLong("Due"));}
        hunters.clear();var waves=tag.getList("Hunters",Tag.TAG_COMPOUND);for(int i=0;i<waves.size();i++){var w=waves.getCompound(i);int n=w.getInt("Wave");if(n<=0)continue;var wave=new Wave(n);for(int k=0;k<3;k++)if(w.hasUUID("Child"+k))wave.ids[k]=w.getUUID("Child"+k);hunters.add(wave);}
    }

    private static final class Child {
        final UUID id,owner;final int kind,slot,wave;int status;long diedAt;boolean respawn,firstDeath;Boolean raised;
        Child(UUID id,UUID owner,int kind,int slot,int wave){this.id=id;this.owner=owner;this.kind=kind;this.slot=slot;this.wave=wave;}
    }
    /** Death receipts also survive when a loaded child dies while its mother is in an unloaded chunk. */
    private static final class Ledger extends SavedData {
        static final String KEY="blacklight_greene_broods";
        final Map<UUID,Child> children=new LinkedHashMap<>();final Map<UUID,Boolean> respawns=new HashMap<>();
        static Ledger get(ServerLevel level){return level.getDataStorage().computeIfAbsent(Ledger::load,Ledger::new,KEY);}
        void respawn(UUID owner,boolean value){if(!Objects.equals(respawns.put(owner,value),value))setDirty();}
        boolean threeLivingHydras(ServerLevel level,UUID owner){
            int alive=0;
            for(var c:children.values())if(c.owner.equals(owner)&&c.kind==HYDRA&&c.status==ALIVE){
                var e=level.getEntity(c.id);
                // An unloaded known identity still occupies its slot; there is no chunk-loading health query.
                if(e==null||e instanceof LivingEntity living&&living.getHealth()>0)alive++;
            }
            return alive==3;
        }
        void dead(Entity entity,long time,boolean first){
            var row=children.get(entity.getUUID());if(row==null||row.status!=ALIVE)return;
            row.status=DEAD;row.diedAt=time;
            // Retain spent Hunter identities until owner cleanup, including a death during owner unload.
            if(row.kind==HUNTER){setDirty();return;}
            row.respawn=respawns.getOrDefault(row.owner,false);
            row.firstDeath=first;
            setDirty();
        }
        @Override public CompoundTag save(CompoundTag tag){
            var entries=new ListTag();for(var c:children.values()){var e=new CompoundTag();e.putUUID("Id",c.id);e.putUUID("Owner",c.owner);e.putInt("Kind",c.kind);e.putInt("Slot",c.slot);e.putInt("Wave",c.wave);e.putInt("Status",c.status);e.putLong("Died",c.diedAt);e.putBoolean("Respawn",c.respawn);e.putBoolean("First",c.firstDeath);if(c.raised!=null)e.putBoolean("Raised",c.raised);entries.add(e);}tag.put("Children",entries);
            var owners=new ListTag();for(var e:respawns.entrySet()){var o=new CompoundTag();o.putUUID("Id",e.getKey());o.putBoolean("Respawn",e.getValue());owners.add(o);}tag.put("Owners",owners);return tag;
        }
        static Ledger load(CompoundTag tag){
            var ledger=new Ledger();var children=tag.getList("Children",Tag.TAG_COMPOUND);
            for(int i=0;i<children.size();i++){var e=children.getCompound(i);if(!e.hasUUID("Id")||!e.hasUUID("Owner"))continue;int kind=e.getInt("Kind"),slot=e.getInt("Slot");if(kind!=HYDRA&&kind!=HUNTER||slot<1||slot>(kind==HYDRA?4:3))continue;var c=new Child(e.getUUID("Id"),e.getUUID("Owner"),kind,slot,e.getInt("Wave"));c.status=Math.max(ALIVE,Math.min(RETIRING,e.getInt("Status")));c.diedAt=Math.max(0,e.getLong("Died"));c.respawn=e.getBoolean("Respawn");c.firstDeath=e.getBoolean("First");c.raised=e.contains("Raised")?e.getBoolean("Raised"):null;ledger.children.put(c.id,c);}
            var owners=tag.getList("Owners",Tag.TAG_COMPOUND);for(int i=0;i<owners.size();i++){var o=owners.getCompound(i);if(o.hasUUID("Id"))ledger.respawns.put(o.getUUID("Id"),o.getBoolean("Respawn"));}return ledger;
        }
    }
    private record Pending(LivingEntity entity,LivingDeathEvent event,long time,boolean first){}
    private static final Map<MinecraftServer,Map<UUID,Pending>> PENDING=new IdentityHashMap<>();
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void childDeath(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel level))return;
        var entity=event.getEntity();if(!(entity instanceof BlacklightHydra||entity instanceof BlacklightHunter)||!entity.getPersistentData().hasUUID(OWNER))return;
        var ledger=Ledger.get(level);var row=ledger.children.get(entity.getUUID());if(row==null)return;
        boolean first=row.kind==HYDRA&&ledger.threeLivingHydras(level,row.owner);
        PENDING.computeIfAbsent(level.getServer(),s->new LinkedHashMap<>()).putIfAbsent(entity.getUUID(),new Pending(entity,event,level.getGameTime(),first));
    }
    @SubscribeEvent public static void afterTick(TickEvent.ServerTickEvent event){if(event.phase==TickEvent.Phase.END)flush(event.getServer());}
    private static void flush(MinecraftServer server){
        var pending=PENDING.remove(server);if(pending==null)return;
        for(var p:pending.values())if(!p.event.isCanceled()&&!p.entity.isAlive()&&((LivingDeathAccess)p.entity).blacklight$deathAccepted()
                &&p.entity.level() instanceof ServerLevel level)Ledger.get(level).dead(p.entity,p.time,p.first);
    }
    @SubscribeEvent public static void childLeft(EntityLeaveLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var entity=event.getEntity();if(!(entity instanceof BlacklightHydra||entity instanceof BlacklightHunter))return;var why=entity.getRemovalReason();
        if(why==Entity.RemovalReason.UNLOADED_TO_CHUNK||why==Entity.RemovalReason.UNLOADED_WITH_PLAYER||why==Entity.RemovalReason.CHANGED_DIMENSION)return;
        var ledger=Ledger.get(level);var row=ledger.children.get(entity.getUUID());if(row==null)return;
        if(row.status==RETIRING){ledger.children.remove(row.id);ledger.setDirty();return;}
        if(entity instanceof LivingEntity living&&!living.isAlive()&&((LivingDeathAccess)living).blacklight$deathAccepted())ledger.dead(entity,level.getGameTime(),ledger.threeLivingHydras(level,row.owner));
    }
    @SubscribeEvent public static void childJoined(EntityJoinLevelEvent event){
        if(!(event.getLevel() instanceof ServerLevel level))return;var entity=event.getEntity();if(!(entity instanceof BlacklightHydra||entity instanceof BlacklightHunter))return;var row=Ledger.get(level).children.get(entity.getUUID());
        if(row!=null&&row.status==RETIRING)retire(level,entity,row);
        else if(row!=null&&row.status==ALIVE&&row.raised!=null&&entity instanceof BlacklightHydra hydra)
            hydra.setGreeneUnderground(row.owner,!row.raised);
    }
    private static void retire(ServerLevel level,Entity child,Child row){
        if(child instanceof BlacklightHydra hydra){
            if(hydra.ownedByGreene(row.owner))hydra.retireGreene(row.owner);
            clearMarker(child); // The retiring Hydra retains only its own cleanup phase, not an encounter owner.
        }else{
            // No source deletion of these interim Hunters was proved; release their task ownership.
            clearMarker(child);var ledger=Ledger.get(level);ledger.children.remove(row.id);ledger.setDirty();
        }
    }
    private static void clearMarker(Entity child){var t=child.getPersistentData();t.remove(OWNER);t.remove(KIND);t.remove(SLOT);t.remove(WAVE);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){PENDING.remove(event.getServer());}
}
