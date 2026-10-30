package com.codex.blacklight.movement;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Real 3D ground blocks blown out of the impact ring by heavy landings. The chunks
 * are block-display entities on hand-driven ballistic arcs, discarded a few ticks
 * after the toss: display entities can never place a block, drop an item, or touch
 * protected terrain, yet render exactly like the block that was shaken loose.
 */
public final class LandingDebrisSystem {
    private static final double GRAVITY = 0.04D;
    private static final int MAX_TRACKED = 64;

    private static final List<Entry> TRACKED = new ArrayList<>();
    private static int publishing;
    private static final java.util.Set<net.minecraft.server.MinecraftServer> STOPPING =
            java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private LandingDebrisSystem() {
    }

    /** Tosses one surface block chunk on a short outward arc. */
    public static void toss(ServerLevel level, double x, double y, double z,
                            BlockState state, double vx, double vy, double vz,
                            int lifetimeTicks) {
        spawn(level,x,y,z,state,vx,vy,vz,lifetimeTicks,1,false);
    }

    /** Smaller attack fragments share the landing debris budget and lifecycle. */
    public static void fragment(ServerLevel level,Vec3 at,BlockState state,Vec3 velocity,float size,int lifetimeTicks){
        spawn(level,at.x,at.y,at.z,state,velocity.x,velocity.y,velocity.z,lifetimeTicks,Math.clamp(size,.08F,.55F),true);
    }

    private static void spawn(ServerLevel level,double x,double y,double z,BlockState state,double vx,double vy,double vz,int lifetimeTicks,float size,boolean fragment){
        if (!level.getServer().isSameThread() || STOPPING.contains(level.getServer())
                || TRACKED.size() + publishing >= MAX_TRACKED) return;
        publishing++;
        try {
        Display.BlockDisplay chunk = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level){
            @Override public boolean shouldBeSaved(){return false;}
        };
        // BlockDisplay's state setter and data accessor are private in 1.21.1, so the
        // chunk receives its block look through the public NBT load path instead.
        CompoundTag tag = new CompoundTag();
        tag.put("block_state", NbtUtils.writeBlockState(state));
        if(fragment){
            var transform=new CompoundTag();
            transform.put("scale",floats(size,size,size));
            transform.put("translation",floats(-size/2,-size/2,-size/2));
            transform.put("left_rotation",floats(0,0,0,1));transform.put("right_rotation",floats(0,0,0,1));
            tag.put("transformation",transform);
            tag.putInt("teleport_duration",1);
        }
        chunk.load(tag);
        chunk.moveTo(x, y, z, 0.0F, 0.0F);
        if (!level.addFreshEntity(chunk) || chunk.isRemoved()) return;
        TRACKED.add(new Entry(chunk, state, vx, vy, vz, lifetimeTicks,fragment));
        } finally { publishing--; }
    }

    private static net.minecraft.nbt.ListTag floats(float... values){
        var list=new net.minecraft.nbt.ListTag();for(float value:values)list.add(net.minecraft.nbt.FloatTag.valueOf(value));return list;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TRACKED.isEmpty()) return;
        // Entity removal invokes other mods synchronously. New effects published
        // by those callbacks belong to the next tick, never this active traversal.
        for (Entry entry : List.copyOf(TRACKED)) {
            if (!TRACKED.contains(entry)) continue;
            Display.BlockDisplay chunk = entry.chunk;
            if (!chunk.isAlive()) {
                TRACKED.remove(entry);
                continue;
            }
            entry.ticksLeft--;
            if (entry.ticksLeft <= 0) {
                TRACKED.remove(entry);
                shatter(chunk, entry.state);
                continue;
            }
            entry.vy -= GRAVITY;
            Vec3 position = chunk.position();
            if(entry.fragment){
                var next=position.add(entry.vx,entry.vy,entry.vz);
                var hit=chunk.level().clip(new net.minecraft.world.level.ClipContext(position,next,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,chunk));
                if(hit.getType()!=net.minecraft.world.phys.HitResult.Type.MISS){TRACKED.remove(entry);shatter(chunk,entry.state);continue;}
                chunk.setYRot(chunk.getYRot()+19);chunk.setXRot(chunk.getXRot()+11);
            }
            chunk.setPos(position.x + entry.vx, position.y + entry.vy, position.z + entry.vz);
            chunk.hasImpulse = true;
        }
    }

    @SubscribeEvent
    public static void stopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        var server = event.getServer();
        if (!STOPPING.add(server)) return;
        try {
            for (Entry entry : List.copyOf(TRACKED)) {
                if (entry.chunk.level().getServer() != server) continue;
                TRACKED.remove(entry);
                entry.chunk.discard();
            }
        } finally { STOPPING.remove(server); }
    }

    /** A small puff of the chunk's own material masks the mid-air despawn. */
    private static void shatter(Display.BlockDisplay chunk, BlockState state) {
        if (chunk.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    chunk.getX(), chunk.getY(), chunk.getZ(),
                    6, 0.18D, 0.18D, 0.18D, 0.06D);
            level.sendParticles(ParticleTypes.CLOUD, chunk.getX(), chunk.getY(),
                    chunk.getZ(), 2, 0.12D, 0.10D, 0.12D, 0.015D);
        }
        chunk.discard();
    }

    private static final class Entry {
        private final Display.BlockDisplay chunk;
        private final BlockState state;
        private double vx;
        private double vy;
        private double vz;
        private int ticksLeft;
        private final boolean fragment;

        private Entry(Display.BlockDisplay chunk, BlockState state,
                      double vx, double vy, double vz, int ticksLeft,boolean fragment) {
            this.chunk = chunk;
            this.state = state;
            this.vx = vx;
            this.vy = vy;
            this.vz = vz;
            this.ticksLeft = ticksLeft;
            this.fragment=fragment;
        }
    }
}
