package com.codex.blacklight.world;

import com.codex.blacklight.combat.QuillHarvest;
import com.codex.blacklight.registry.ModBlockEntities;
import com.codex.blacklight.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class WeaveCell extends BlockEntity {
    public static final int DURATION = 96;
    private net.minecraft.world.phys.Vec3 stationFeet;
    private static final java.util.Map<Level, java.util.Map<UUID, BlockPos>> ACTIVE =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    public static WeaveCell channelOf(net.minecraft.world.entity.player.Player player) {
        var links = ACTIVE.get(player.level());
        if (links == null) return null;
        var at = links.get(player.getUUID());
        if (at == null || !player.level().hasChunkAt(at)) return null;
        return player.level().getBlockEntity(at) instanceof WeaveCell c && player.getUUID().equals(c.weaver) ? c : null;
    }

    private void register(UUID id) {
        if (level == null) return;
        var links = ACTIVE.computeIfAbsent(level, unused -> new java.util.HashMap<>());
        if (weaver != null) links.remove(weaver, worldPosition);
        weaver = id;
        if (id != null) links.put(id, worldPosition);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (weaver != null) register(weaver);
    }
    @Override public void setRemoved() {
        register(null);
        super.setRemoved();
    }
    public net.minecraft.world.phys.Vec3 stationFeet() { return stationFeet; }

    // World-space coordinates shared by the hand IK and every fiber, for any of the four sides.
    public net.minecraft.world.phys.Vec3 inward() {
        var center = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition);
        var d = center.subtract(stationFeet);
        return Math.abs(d.x) > Math.abs(d.z) ? new net.minecraft.world.phys.Vec3(Math.signum(d.x),0,0)
                                            : new net.minecraft.world.phys.Vec3(0,0,Math.signum(d.z));
    }
    public net.minecraft.world.phys.Vec3 palm(int side) {
        var f = inward();
        var right = new net.minecraft.world.phys.Vec3(-f.z,0,f.x);
        var center = net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition);
        double lateral = net.minecraft.util.Mth.clamp(stationFeet.subtract(center).dot(right) + side*.34, -2.4, 2.4);
        return center.add(f.scale(-2.41)).add(right.scale(lateral)).add(0,.89,0);
    }
    private boolean atEdge(ServerPlayer p) {
        var d = p.position().subtract(net.minecraft.world.phys.Vec3.atBottomCenterOf(worldPosition));
        double x = Math.abs(d.x), z = Math.abs(d.z);
        return Math.max(x,z) >= 2.78 && Math.max(x,z) <= 3.10 && Math.min(x,z) <= 2.15
                && Math.abs(d.y) < .25 && !p.isPassenger() && !p.isSwimming();
    }
    private ItemStack offering = ItemStack.EMPTY;
    private int guide = -1;
    private UUID weaver;
    private long started;
    private WeavePattern pending;
    private long lastVisualCheck=Long.MIN_VALUE;
    private boolean visualAssembly;

    public boolean displaysMantle(){
        if(level==null || !getBlockState().is(ModBlocks.WEAVE_CORE.get()))return false;
        long tick=level.getGameTime();
        if(tick!=lastVisualCheck){
            visualAssembly=formed(level,worldPosition);
            lastVisualCheck=tick;
        }
        return visualAssembly;
    }

    public WeaveCell(BlockPos pos,BlockState state){super(ModBlockEntities.WEAVE_CELL.get(),pos,state);}
    public ItemStack offering(){return offering;}
    public WeavePattern guide(){return guide>=0 && guide<WeavePattern.values().length ? WeavePattern.values()[guide] : null;}
    public UUID weaver(){return weaver;}
    public long started(){return started;}
    public static Block part(int x,int z) {
        int a=Math.abs(x), b=Math.abs(z);
        if(a==0 && b==0) return ModBlocks.WEAVE_CORE.get();
        if(a==2 && b==2) return ModBlocks.WEAVE_ANCHOR.get();
        if(a==2 || b==2) return ModBlocks.WEAVE_CHANNEL.get();
        return ModBlocks.WEAVE_BED.get();
    }
    public static boolean formed(Level level,BlockPos center) {
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
            BlockPos p=center.offset(x,0,z);
            if(!level.hasChunkAt(p) || !level.getBlockState(p).is(part(x,z)))return false;
        }
        return true;
    }
    private static WeaveCell owner(Level level,BlockPos pos) {
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
            var center=pos.offset(x,0,z);
            if(level.hasChunkAt(center) && level.getBlockState(center).is(ModBlocks.WEAVE_CORE.get())
                    && formed(level,center) && level.getBlockEntity(center) instanceof WeaveCell c)return c;
        }
        return null;
    }
    static void interruptNearby(ServerLevel level,BlockPos pos) {
        for(int z=-2;z<=2;z++)for(int x=-2;x<=2;x++) {
            var center=pos.offset(x,0,z);
            if(level.hasChunkAt(center) && level.getBlockEntity(center) instanceof WeaveCell c && c.weaver!=null)c.stop();
        }
    }
    public void interact(ServerPlayer player,ItemStack held) {
        ServerLevel server=player.serverLevel();
        WeaveCell center=owner(server,worldPosition);
        if(center!=null && center.weaver!=null) { hint(player,"busy"); return; }
        if(center!=null && held.isEmpty() && player.isShiftKeyDown()) { center.start(player); return; }
        if(held.isEmpty() && !offering.isEmpty()) {
            ItemStack returned=offering; offering=ItemStack.EMPTY; sync();
            if(!player.getInventory().add(returned))player.drop(returned,false);
            return;
        }
        if(center==null) {hint(player,"structure");return;}
        if(held.isEmpty()) {
            if(center==this) {
                guide=(guide+1)%(WeavePattern.values().length+1);
                if(guide==WeavePattern.values().length)guide=-1;
                sync();
                WeavePattern selected=guide();
                if(selected!=null)hint(player,"guide",selected.result.get().getDescription(),selected.biomass);
                else hint(player,"guide_off");
            } else hint(player,"place");
            return;
        }
        if(!offering.isEmpty()) {hint(player,"occupied");return;}
        offering=held.copy(); offering.setCount(1);
        if(!player.getAbilities().instabuild)held.shrink(1);
        sync();
        server.playSound(null,worldPosition,SoundEvents.SLIME_BLOCK_PLACE,SoundSource.BLOCKS,.4F,1.3F);
    }
    private WeaveCell[] cells() {
        if(!formed(level,worldPosition))return null;
        WeaveCell[] cells=new WeaveCell[25];
        for(int i=0;i<25;i++) {
            if(!(level.getBlockEntity(worldPosition.offset(i%5-2,0,i/5-2)) instanceof WeaveCell cell))return null;
            cells[i]=cell;
        }
        return cells;
    }
    private static WeavePattern match(WeaveCell[] cells) {
        if(cells==null)return null;
        ItemStack[] slots=new ItemStack[25];
        for(int i=0;i<25;i++)slots[i]=cells[i].offering;
        for(WeavePattern p:WeavePattern.values())if(p.matches(slots))return p;
        return null;
    }
    private void start(ServerPlayer p) {
        WeavePattern recipe=match(cells());
        if(recipe==null){hint(p,"mismatch");return;}
        if(!p.getAbilities().instabuild) {
            if(!QuillHarvest.state(p).isInfected()){hint(p,"infection");return;}
            if(QuillHarvest.state(p).getBiomass()<recipe.biomass){hint(p,"cost",recipe.biomass);return;}
        }
        if (!atEdge(p)) { hint(p,"edge"); return; }
        if (channelOf(p) != null) { hint(p,"busy"); return; }
        ServerLevel server=p.serverLevel();
        stationFeet=p.position();
        register(p.getUUID()); started=server.getGameTime(); pending=recipe; sync();
        server.scheduleTick(worldPosition,getBlockState().getBlock(),2);
        server.playSound(null,worldPosition,SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.BLOCKS,.8F,.75F);
        hint(p,"start",recipe.result.get().getDescription(),recipe.biomass);
    }
    void advance(ServerLevel server) {
        if(weaver==null)return;
        ServerPlayer p=server.getServer().getPlayerList().getPlayer(weaver);
        if(p==null || !p.isAlive() || p.isSpectator() || p.level()!=server || p.position().distanceToSqr(stationFeet)>.25*.25 || p.isPassenger() || !formed(server,worldPosition)){
            stop();return;
        }
        long elapsed=server.getGameTime()-started;
        if(elapsed<DURATION){
            if (elapsed == 12) server.playSound(null, p.blockPosition(), SoundEvents.SLIME_BLOCK_FALL, SoundSource.PLAYERS, .55F, .78F);
            if (elapsed == 22 || elapsed == 30 || elapsed == 38)
                server.playSound(null,worldPosition,SoundEvents.SCULK_BLOCK_PLACE,SoundSource.BLOCKS,.58F,.72F+elapsed*.009F);
            if (elapsed == 48 || elapsed == 62 || elapsed == 76)
                server.playSound(null,worldPosition,SoundEvents.SCULK_CATALYST_BLOOM,SoundSource.BLOCKS,.60F,.65F+(elapsed-48)*.008F);
            if (elapsed == 86) server.playSound(null,worldPosition,SoundEvents.SLIME_BLOCK_BREAK,SoundSource.BLOCKS,.5F,1.05F);
            server.scheduleTick(worldPosition,getBlockState().getBlock(),2);
            return;
        }
        WeaveCell[] cells=cells();
        if(pending==null || match(cells)!=pending){stop();return;}
        if(!p.getAbilities().instabuild){
            var state=QuillHarvest.state(p);
            if(!state.isInfected() || !state.spendBiomass(pending.biomass)){hint(p,"cost",pending.biomass);stop();return;}
            QuillHarvest.sync(p);
        }
        var product=new ItemStack(pending.result.get());
        for(WeaveCell c:cells)if(c!=this && !c.offering.isEmpty()){c.offering=ItemStack.EMPTY;c.sync();}
        offering=product;guide=-1;stop();
        server.playSound(null,worldPosition,SoundEvents.SCULK_SHRIEKER_BREAK,SoundSource.BLOCKS,.7F,1.25F);
        hint(p,"done",product.getHoverName());
    }
    private void stop(){register(null);stationFeet=null;pending=null;started=0;sync();}
    private void sync(){setChanged();level.sendBlockUpdated(worldPosition,getBlockState(),getBlockState(),Block.UPDATE_CLIENTS);}
    private static void hint(ServerPlayer p,String key,Object... args){FieldNotes.record(p, Component.translatable("weave.blacklight."+key,args));}
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket(){return ClientboundBlockEntityDataPacket.create(this);}
    // Inventory and guide survive unloading. An interrupted channel is always safe to restart.
    @Override protected void saveAdditional(CompoundTag tag){
        super.saveAdditional(tag);if(!offering.isEmpty())tag.put("Offering",offering.save(new CompoundTag()));tag.putInt("Guide",guide);
    }
    @Override public CompoundTag getUpdateTag(){
        var tag=new CompoundTag();saveAdditional(tag);
        if(weaver!=null){tag.putUUID("Weaver",weaver);tag.putLong("Started",started);
            tag.putDouble("FeetX",stationFeet.x);tag.putDouble("FeetY",stationFeet.y);tag.putDouble("FeetZ",stationFeet.z);}return tag;
    }
    @Override public void load(CompoundTag tag){
        super.load(tag);offering=ItemStack.of(tag.getCompound("Offering"));
        guide=tag.contains("Guide")?tag.getInt("Guide"):-1;
        UUID actor=tag.hasUUID("Weaver")?tag.getUUID("Weaver"):null;
        stationFeet=actor == null ? null : new net.minecraft.world.phys.Vec3(tag.getDouble("FeetX"),tag.getDouble("FeetY"),tag.getDouble("FeetZ"));
        if(level==null)weaver=actor;else register(actor);
        started=tag.getLong("Started");
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox(){
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(weaver!=null?8:getBlockState().is(ModBlocks.WEAVE_CORE.get())?3:1);
    }
}
