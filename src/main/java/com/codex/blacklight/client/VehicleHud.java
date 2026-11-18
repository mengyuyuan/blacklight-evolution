package com.codex.blacklight.client;

import com.codex.blacklight.entity.MilitaryVehicle;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid="blacklight",value=Dist.CLIENT)
public final class VehicleHud {
    private VehicleHud(){}
    public record Layout(int x,int y,int width,float scale) {
        public float lineY(int line){return y+line*10*scale;}
        public float bottom(int lines){return lineY(lines);}
    }
    /** GUI coordinates; leave vanilla's actionbar and hotbar unobstructed. */
    public static Layout layout(int width,int height,int lines){
        return new Layout(8,28,Math.min(260,(width-24)/2),Math.min(1,(height-112F)/(Math.max(1,lines)*10)));
    }
    /** Resolve live mappings each frame so remapping and unbound keys stay truthful. */
    public static Component controlHints(net.minecraft.client.KeyMapping[] mappings){
        return controlHints(mappings,true,true);
    }
    public static Component controlHints(net.minecraft.client.KeyMapping[] mappings,boolean driver,boolean weaponOperator){
        var hints=Component.empty();
        if(driver)hints.append(Component.translatable("hud.blacklight.vehicle_drive_controls",
                keyLabel(mappings,"key.forward"),keyLabel(mappings,"key.left"),keyLabel(mappings,"key.back"),
                keyLabel(mappings,"key.right"),keyLabel(mappings,"key.jump")));
        else hints.append(Component.translatable("hud.blacklight.vehicle_passenger_controls"));
        if(weaponOperator)hints.append(" · ").append(Component.translatable("hud.blacklight.vehicle_fire_controls",
                keyLabel(mappings,"key.attack"),keyLabel(mappings,"key.use"),keyLabel(mappings,"key.blacklight.vehicle_next_ammo")));
        return hints.append(" · ").append(Component.translatable("hud.blacklight.vehicle_seat_controls",
                keyLabel(mappings,"key.sneak"),keyLabel(mappings,"key.blacklight.vehicle_next_seat")));
    }
    private static Component keyLabel(net.minecraft.client.KeyMapping[] mappings,String name){
        for(var key:mappings)if(key.getName().equals(name)&&!key.isUnbound())return key.getTranslatedKeyMessage();
        return Component.translatable("tutorial.blacklight.unbound");
    }
    private record Row(FormattedCharSequence text,int color) {}
    private static void add(List<Row> rows,net.minecraft.client.gui.Font font,Component text,int width,int color){
        for(var line:font.split(text,width))rows.add(new Row(line,color));
    }
    @SubscribeEvent public static void viewport(RenderGuiEvent.Pre event){
        var mc=Minecraft.getInstance();
        if(mc.player==null||!(mc.player.getVehicle() instanceof MilitaryVehicle vehicle))return;
        var g=event.getGuiGraphics();
        if(mc.options.getCameraType().isFirstPerson()&&!vehicle.chassisId().equals("truck")) {
            int w=g.guiWidth(),h=g.guiHeight(),side=w/10,top=h/7;
            g.fill(0,0,w,top,0xFF121819);g.fill(0,h-top,w,h,0xFF121819);
            g.fill(0,top,side,h-top,0xFF121819);g.fill(w-side,top,w,h-top,0xFF121819);
        }
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.player.getVehicle() instanceof com.codex.blacklight.entity.BlackwatchHelicopter craft){
            com.codex.blacklight.client.vehicle.HelicopterControls.hud(event.getGuiGraphics(),craft);return;
        }
        if(mc.player==null||!(mc.player.getVehicle() instanceof MilitaryVehicle vehicle))return;
        var g=event.getGuiGraphics();
        if(mc.options.hideGui)return;
        String seat=vehicle.seatOf(mc.player.getUUID());
        var seatName=Component.translatable("seat.blacklight."+(seat==null?"passenger":seat));
        if(com.codex.blacklight.config.GunplayClientConfig.COMPACT_VEHICLE_HUD.get()&&!ClientClawEvents.targetInfoKey().isDown()){
            int w=146,x=g.guiWidth()-w-8,y=g.guiHeight()-100;
            var seatStatus=Component.translatable("hud.blacklight.vehicle_seat_status",seatName,VehicleControls.seatKey()).getString();
            g.fill(x-4,y-4,x+w+4,y+54,0xB00C1116);
            g.drawString(mc.font,mc.font.plainSubstrByWidth(seatStatus,w),x,y,0xFFD6D6D6,false);
            y+=12;
            g.drawString(mc.font,Component.literal(Math.round(vehicle.health())+" / "+Math.round(vehicle.maxHealth())+" HP"),x,y,0xFFE8EDD9,false);
            g.fill(x,y+12,x+w,y+15,0xFF333A42);g.fill(x,y+12,x+(int)(w*com.codex.blacklight.forge.WingMath.clamp(vehicle.health()/vehicle.maxHealth(),0,1)),y+15,0xFFCA7B65);
            String selected=vehicle.selectedPrimaryWeapon();var def=vehicle.definition().weapons().get(selected);
            if(def!=null){var text=Component.translatable("weapon.blacklight."+selected).getString()+"  "+vehicle.inventory().ammunition(def.ammunitionId());g.drawString(mc.font,mc.font.plainSubstrByWidth(text,w),x,y+20,0xFFF7D699,false);}
            g.drawString(mc.font,Component.translatable("hud.blacklight.vehicle_details",ClientClawEvents.targetInfoKey().getTranslatedKeyMessage()),x,y+31,0xFF9FAFB9,false);return;
        }
        int width=layout(g.guiWidth(),g.guiHeight(),0).width();var rows=new ArrayList<Row>();
        var status=Component.translatableWithFallback("hud.blacklight.vehicle_status","HP %s/%s | %s",Math.round(vehicle.health()),Math.round(vehicle.maxHealth()),
                seatName);
        add(rows,mc.font,status,width,0xFFE8EDD9);
        String weapon=vehicle.selectedPrimaryWeapon();
        if(!weapon.isEmpty()) {
            var definition=vehicle.definition().weapons().get(weapon);
            if(definition!=null) {
                var inventory=vehicle.inventory();
                var primary=Component.translatableWithFallback("hud.blacklight.vehicle_primary","Primary: %s",Component.translatable("hud.blacklight.vehicle_ammo",
                        Component.translatable("weapon.blacklight."+weapon),inventory.ammunition(definition.ammunitionId())));
                if(vehicle.chassisId().equals("tank"))primary.append(Component.literal("  |  ")).append(Component.translatable("item.blacklight."+(weapon.equals("tank_he")?"tank_ap_shell":"tank_he_shell")))
                        .append(Component.literal(": "+inventory.ammunition(weapon.equals("tank_he")?"tank_ap_shell":"tank_he_shell")));
                add(rows,mc.font,primary,width,0xFFF7D699);
                var secondary=vehicle.definition().weapons().get(vehicle.secondaryWeapon());
                if(secondary!=null)add(rows,mc.font,Component.translatableWithFallback("hud.blacklight.vehicle_secondary","Secondary: %s",
                        Component.translatable("hud.blacklight.vehicle_ammo",Component.translatable("weapon.blacklight."+secondary.id()),inventory.ammunition(secondary.ammunitionId()))),width,0xFFF7D699);
            }
        }
        var occupied=vehicle.getPassengers().stream().map(p->vehicle.seatOf(p.getUUID())).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        var permissions=new VehicleControls.SeatInput(vehicle.definition(),seat,occupied);
        add(rows,mc.font,controlHints(mc.options.keyMappings,permissions.driver(),permissions.weaponOperator()),width,0xFFD6D6D6);
        if(permissions.driver()&&!permissions.weaponOperator()&&!vehicle.definition().weapons().isEmpty())
            add(rows,mc.font,Component.translatable("hud.blacklight.vehicle_other_gunner"),width,0xFFBECAC3);
        if(VehicleControls.seatUnbound()||permissions.weaponOperator()&&VehicleControls.ammunitionUnbound())
            add(rows,mc.font,Component.translatable("hud.blacklight.vehicle_bind_controls"),width,0xFFF7D699);
        add(rows,mc.font,Component.translatableWithFallback("hud.blacklight.vehicle_supply_hint","Supply: stop, dismount, use ammo / repair parts"),width,0xFFBECAC3);
        var layout=layout(g.guiWidth(),g.guiHeight(),rows.size());
        g.fill(layout.x()-4,layout.y()-4,(int)Math.ceil(layout.x()+layout.width()*layout.scale())+4,(int)Math.ceil(layout.bottom(rows.size()))+4,0xE0121819);
        g.pose().pushPose();
        try {
            g.pose().translate(layout.x(),layout.y(),0);g.pose().scale(layout.scale(),layout.scale(),1);
            for(int line=0;line<rows.size();line++)g.drawString(mc.font,rows.get(line).text(),0,line*10,rows.get(line).color(),false);
        }finally{g.pose().popPose();}
    }
}
