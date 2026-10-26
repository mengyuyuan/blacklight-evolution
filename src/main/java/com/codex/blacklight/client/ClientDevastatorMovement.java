package com.codex.blacklight.client;

import com.codex.blacklight.combat.BulletdivePhysics;
import com.codex.blacklight.combat.BulletdiveSteering;
import com.codex.blacklight.combat.DevastatorSystem;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Predict the server-owned fall before vanilla swept block collision. Vanilla's
 * motion packet clamps components to 3.9; this action clock keeps high dives
 * accelerating locally without trusting a client-selected damage or height.
 */
@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class ClientDevastatorMovement {
    private static java.lang.ref.WeakReference<net.minecraft.world.level.Level> rootLevel=new java.lang.ref.WeakReference<>(null);
    private static long landingSession=Long.MIN_VALUE;
    private ClientDevastatorMovement() { }
    /** The first confirmed impact owns its body pose and position together.
     * Do not interpolate that immediate, zero-blend landing pose from a flight
     * coordinate tens of blocks above the surface. Later recovery snapshots
     * retain ordinary positional interpolation, including real crater falls. */
    public static void acceptRoot(net.minecraft.client.player.LocalPlayer player) {
        if(rootLevel.get()!=player.level()){rootLevel=new java.lang.ref.WeakReference<>(player.level());landingSession=Long.MIN_VALUE;}
        var visual=player.getData(ModAttachments.DEVASTATOR_VISUAL);
        if(visual.phase()!=DevastatorSystem.LAND||(visual.kind()!=0&&visual.kind()!=3)
                ||visual.session()==landingSession)return;
        landingSession=visual.session();
        player.xo=player.xOld=player.getX();
        player.yo=player.yOld=player.getY();
        player.zo=player.zOld=player.getZ();
        player.setDeltaMovement(Vec3.ZERO);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(PlayerTickEvent.Pre event) {
        var mc=Minecraft.getInstance();var p=mc.player;
        if(p==null||event.getEntity()!=p||mc.level==null||!p.isAlive())return;
        var v=p.getData(ModAttachments.DEVASTATOR_VISUAL);
        if(v.kind()<0||v.phase()==DevastatorSystem.RECOVERY)return;
        p.fallDistance=0;
        if(v.phase()==DevastatorSystem.CHARGE) {
            var motion=p.getDeltaMovement();
            p.setDeltaMovement(motion.x*.25,p.onGround()?motion.y:Math.max(-.02,motion.y*.2),motion.z*.25);
        } else if(v.kind()==3&&v.phase()==DevastatorSystem.FLIGHT&&!p.onGround()) {
            double ticks=Math.max(0,(mc.level.getGameTime()-v.released())-Math.ceil(20D/6));
            var movement=p.getData(ModAttachments.MOVEMENT_STATE);var input=movement.wallInput();
            var target=mc.level.getEntity(v.targetId());
            Vec3 aim=target!=null&&target.isAlive()?target.position():v.diveAim();
            var horizontal=BulletdiveSteering.horizontal(p.position(),p.getDeltaMovement(),movement.cameraYaw(p.getYRot()),
                    input.forward(),input.sideways(),aim);
            p.setDeltaMovement(horizontal.x,-BulletdivePhysics.downSpeed(ticks),horizontal.z);
        } else if(v.phase()==DevastatorSystem.RELEASE&&(v.kind()==3||v.kind()==0&&!p.onGround())) {
            p.setDeltaMovement(0,p.onGround()?0:-.015,0);
        } else if(v.phase()==DevastatorSystem.LAND) {
            // LAND positions arrive once per server tick through root motion.
            // A crater can remove support after impact; that fall is simulated
            // by the server. Never retain the old, unbounded FLIGHT velocity
            // between these snapshots and repeatedly fall ahead of it.
            p.setDeltaMovement(Vec3.ZERO);
        }
    }
}
