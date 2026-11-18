package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.entity.BlackwatchHelicopter;
import com.codex.blacklight.military.vehicle.VehicleControlAuthority;
import com.codex.blacklight.network.HelicopterControlPayload;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;

/** Reuses vanilla movement mappings and the existing server-owned vehicle input path. */
public final class HelicopterControls {
    private static UUID vehicle,lease,player;private static long sequence;
    private static boolean switchWeapon;
    public static void requestWeaponSwitch(){switchWeapon=true;}
    public static void clear(){vehicle=null;lease=null;player=null;sequence=0;switchWeapon=false;}
    public static void update(BlackwatchHelicopter craft){
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.screen!=null)return;
        UUID token=craft.pilot().lease(mc.player.getUUID());if(token==null)return;
        if(!token.equals(lease)||!craft.getUUID().equals(vehicle)||!mc.player.getUUID().equals(player)){lease=token;vehicle=craft.getUUID();player=mc.player.getUUID();sequence=0;}
        if((mc.player.tickCount&1)!=0)return;
        int slot=craft.pilot().slot(player);boolean driver=slot==0,gunner=slot==1||driver&&craft.pilot().occupant(1)==null;
        int actions=(gunner&&mc.options.keyAttack.isDown()?1:0)|(gunner&&mc.options.keyUse.isDown()?2:0)
                |(gunner&&switchWeapon?VehicleControlAuthority.NEXT_AMMUNITION:0)
                |(driver&&mc.options.keyJump.isDown()?VehicleControlAuthority.ASCEND:0)|(driver&&mc.options.keySprint.isDown()?VehicleControlAuthority.DESCEND:0);
        var camera=mc.gameRenderer.getMainCamera();var look=camera.getLookVector();Vec3 from=camera.getPosition(),to=from.add(look.x()*120,look.y()*120,look.z()*120);
        var wall=mc.level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,mc.player));if(wall.getType()!=HitResult.Type.MISS)to=wall.getLocation();
        var hit=net.minecraft.world.entity.projectile.ProjectileUtil.getEntityHitResult(mc.player,from,to,new AABB(from,to).inflate(1),e->e.getRootVehicle()!=craft&&e!=craft&&e.isPickable()&&!e.isSpectator(),from.distanceToSqr(to));
        if(hit!=null)to=hit.getLocation();
        var target=com.codex.blacklight.client.ClientTargetLock.entityTarget();
        var selection=target==null?null:com.codex.blacklight.client.ClientTargetLock.selection(target);
        var aim=com.codex.blacklight.military.aircraft.HelicopterManualAim.resolve(mc.player,craft,selection,to,com.codex.blacklight.client.ClientTargetLock.range());
        PacketDistributor.sendToServer(new HelicopterControlPayload(vehicle,lease,sequence++,driver?(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0):0,
                driver?(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0):0,Mth.wrapDegrees(aim.yaw()),Mth.clamp(aim.pitch(),-90,90),actions));
        switchWeapon=false;
    }
    public static void hud(net.minecraft.client.gui.GuiGraphics graphics,BlackwatchHelicopter craft){
        var mc=Minecraft.getInstance();if(mc.options.hideGui||mc.player==null)return;
        var lines=new java.util.ArrayList<Component>();int slot=craft.pilot().slot(mc.player.getUUID());
        lines.add(Component.translatable("hud.blacklight.air_status",Math.round(craft.health()),360,Component.translatable("seat.blacklight."+(slot==0?"pilot":slot==1?"copilot":"passenger"))));
        lines.add(Component.translatable("hud.blacklight.air_ammo",craft.controlData().getInt("Rounds"),craft.controlData().getInt("Rockets")));
        lines.add(Component.translatable("hud.blacklight.air_rapid",craft.controlData().getInt("RapidLeft"),craft.controlData().getInt("RapidRight")));
        boolean gunner=slot==1||slot==0&&craft.pilot().occupant(1)==null;
        Component weapon=Component.translatable(craft.controlData().getBoolean("RapidSelected")?"hud.blacklight.air_70mm":"hud.blacklight.air_hellfire");
        lines.add(Component.translatable(gunner?"hud.blacklight.air_weapon":"hud.blacklight.air_weapon_observer",weapon,com.codex.blacklight.client.VehicleControls.ammunitionKey()));
        if(craft.pilot().hijack().active())lines.add(Component.translatable("hud.blacklight.air_hijack",Math.min(100,craft.pilot().hijack().ticks()*100/76)));
        else {
            if(slot==0){
                if(craft.rotor()<.99F)lines.add(Component.translatable("hud.blacklight.air_starting",Math.round(craft.rotor()*100)));
                lines.add(Component.translatable("hud.blacklight.air_controls",mc.options.keyUp.getTranslatedKeyMessage(),mc.options.keyDown.getTranslatedKeyMessage(),mc.options.keyLeft.getTranslatedKeyMessage(),mc.options.keyRight.getTranslatedKeyMessage(),mc.options.keyJump.getTranslatedKeyMessage(),mc.options.keySprint.getTranslatedKeyMessage()));
            }
            if(gunner){
                lines.add(Component.translatable("hud.blacklight.air_fire_controls",mc.options.keyAttack.getTranslatedKeyMessage(),mc.options.keyUse.getTranslatedKeyMessage()));
                if(com.codex.blacklight.client.VehicleControls.ammunitionUnbound())lines.add(Component.translatable("hud.blacklight.air_bind_ammo"));
            }else lines.add(Component.translatable(slot==0?"hud.blacklight.air_copilot_weapons":"hud.blacklight.air_passenger_controls"));
            lines.add(Component.translatable(com.codex.blacklight.client.VehicleControls.seatUnbound()?"hud.blacklight.air_bind_seat":"hud.blacklight.air_seat_controls",com.codex.blacklight.client.VehicleControls.seatKey()));
        }
        lines.add(Component.translatable("hud.blacklight.air_exit",mc.options.keyShift.getTranslatedKeyMessage()));
        int width=Math.min(260,graphics.guiWidth()-20),y=28;var rows=new java.util.ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:lines)rows.addAll(mc.font.split(line,width));
        graphics.fill(4,y-4,12+width,y+rows.size()*10+4,0xCC10181E);for(var row:rows){graphics.drawString(mc.font,row,8,y,0xFFE8EDD9,false);y+=10;}
    }
    private HelicopterControls(){}
}
