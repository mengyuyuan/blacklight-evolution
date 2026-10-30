package com.codex.blacklight.military.vehicle;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.military.MilitaryFaction;
import com.codex.blacklight.registry.ModEntities;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real chassis and consumable items; placing or supplying never creates another inventory truth. */
public final class VehicleRegistry {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"blacklight");
    private static final Map<String,Supplier<? extends Item>> ALL=new LinkedHashMap<>();
    static {
        for(var faction:MilitaryFaction.values())for(String chassis:new String[]{"truck","apc","tank"}) {
            String id=faction.id()+"_"+chassis+"_chassis";
            ALL.put(id,ITEMS.register(id,()->new ChassisItem(new Item.Properties().stacksTo(1),faction,chassis)));
        }
        for(String id:new String[]{"autocannon_round","tank_he_shell","tank_ap_shell","military_fuel_can","vehicle_repair_parts"})
            ALL.put(id,ITEMS.register(id,()->new Item(new Item.Properties()) {
                @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
                    super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("tooltip.blacklight."+id));
                }
            }));
        TABS.register("military_vehicles",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.blacklight.military_vehicles"))
                .icon(()->item("military_tank_chassis").getDefaultInstance()).displayItems((parameters,output)->ALL.forEach((id,value)->{if(!id.equals("military_fuel_can"))output.accept(value.get());})).build());
    }
    private VehicleRegistry(){}
    public static Item item(String id){var value=ALL.get(id);if(value==null)throw new IllegalArgumentException("Unknown vehicle item: "+id);return value.get();}
    public static void register(IEventBus bus){ITEMS.register(bus);TABS.register(bus);}
    public static EntityType<? extends MilitaryVehicle> type(MilitaryFaction faction,String chassis){
        return switch(chassis){
            case "truck"->faction==MilitaryFaction.MILITARY?ModEntities.MILITARY_TRUCK.get():ModEntities.BLACKWATCH_TRUCK.get();
            case "apc"->faction==MilitaryFaction.MILITARY?ModEntities.MILITARY_APC.get():ModEntities.BLACKWATCH_APC.get();
            case "tank"->faction==MilitaryFaction.MILITARY?ModEntities.MILITARY_TANK.get():ModEntities.BLACKWATCH_TANK.get();
            default->throw new IllegalArgumentException("Unknown chassis");
        };
    }
    public static InteractionResult supply(Player player,MilitaryVehicle vehicle,InteractionHand hand){
        if(player==null||vehicle==null||hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
        var stack=player.getItemInHand(hand);var id=net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if(!id.getNamespace().equals("blacklight"))return InteractionResult.PASS;
        String name=id.getPath();if(name.equals("military_fuel_can"))return InteractionResult.FAIL;
        boolean repair=name.equals("vehicle_repair_parts");
        boolean ammunition=java.util.Set.of("autocannon_round","tank_he_shell","tank_ap_shell","machine_gun_round","rocket_round").contains(name);
        if(!ammunition&&!repair)return InteractionResult.PASS;
        if(stack.isEmpty()||stack.getCount()<=0)return InteractionResult.FAIL;
        if(vehicle.level().isClientSide)return InteractionResult.SUCCESS;
        double speed=vehicle.getDeltaMovement().horizontalDistanceSqr();
        if(!player.isAlive()||player.isSpectator()||vehicle.isRemoved()||vehicle.health()<=0||vehicle.level()!=player.level()
                ||com.codex.blacklight.combat.CombatTargeting.distanceSquared(player.getEyePosition(),vehicle.getBoundingBox())>16
                ||!Double.isFinite(speed)||speed>.0004)return InteractionResult.FAIL;
        int taken;
        if(repair)taken=vehicle.repair(20)>0?1:0;
        else if(vehicle.definition().ammunitionCapacities().containsKey(name))taken=vehicle.inventory().addAmmunition(name,stack.getCount());
        else return InteractionResult.FAIL;
        if(taken==0)return InteractionResult.FAIL;
        if(player.getAbilities().instabuild){
            // Player.interactOn restores creative counts only on the original hand-stack object.
            // Replace it with the actual remainder so a successful finite transfer stays spent.
            var remainder=stack.copy();remainder.shrink(taken);player.setItemInHand(hand,remainder);
        }else stack.shrink(taken);
        player.getInventory().setChanged();
        if(player instanceof net.minecraft.server.level.ServerPlayer server)server.containerMenu.broadcastChanges();
        vehicle.syncVehicleState();return InteractionResult.CONSUME;
    }
    public static final class ChassisItem extends Item {
        private final MilitaryFaction faction;private final String chassis;
        ChassisItem(Properties properties,MilitaryFaction faction,String chassis){super(properties);this.faction=faction;this.chassis=chassis;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> tooltip,TooltipFlag flag){
            super.appendHoverText(stack,context,tooltip,flag);tooltip.add(Component.translatable("tooltip.blacklight.vehicle.place"));tooltip.add(Component.translatable("tooltip.blacklight.vehicle.supply"));
        }
        @Override public InteractionResult useOn(UseOnContext context){
            if(context.getLevel().isClientSide)return InteractionResult.SUCCESS;
            if(!(context.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)||!player.isAlive()||player.isSpectator()
                    ||!player.mayBuild()||!context.getLevel().mayInteract(player,context.getClickedPos()))return InteractionResult.FAIL;
            var at=context.getClickedPos().relative(context.getClickedFace());
            var vehicle=type(faction,chassis).create(context.getLevel());if(vehicle==null)return InteractionResult.FAIL;
            vehicle.setPos(at.getX()+.5,at.getY(),at.getZ()+.5);vehicle.setYRot(net.minecraft.util.Mth.wrapDegrees(-player.getYRot()));
            if(!VehicleKinematics.placementReady(vehicle)){vehicle.discard();return InteractionResult.FAIL;}
            vehicle.setPlayerOwner(player.getUUID());
            if(!context.getLevel().addFreshEntity(vehicle)){vehicle.discard();return InteractionResult.FAIL;}
            context.getItemInHand().shrink(1);return InteractionResult.CONSUME;
        }
    }
}
