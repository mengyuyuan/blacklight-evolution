package com.codex.blacklight.command;

import com.codex.blacklight.entity.MilitaryVehicle;
import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.military.vehicle.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.*;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

/** Explicit administrative association of existing entities. This command never spawns crew or fuel. */
public final class VehicleFleetCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> commands(){
        var root=Commands.literal("fleet").requires(source->source.hasPermission(2));
        root.then(Commands.literal("crew").then(Commands.argument("vehicle",EntityArgument.entity()).then(Commands.argument("soldiers",EntityArgument.entities()).executes(context->{
            var source=context.getSource();var selected=EntityArgument.getEntity(context,"vehicle");if(!(selected instanceof MilitaryVehicle vehicle)||vehicle.level()!=source.getLevel()||vehicle.health()<=0||vehicle.controlLocked()||vehicle.playerOwner()!=null||vehicle.getPassengers().stream().anyMatch(net.minecraft.world.entity.player.Player.class::isInstance))return fail(source,"需要本维度未被玩家占用的有效车辆。");
            var soldiers=new ArrayList<MilitarySoldier>();
            for(var entity:EntityArgument.getEntities(context,"soldiers")){
                if(!(entity instanceof MilitarySoldier soldier)||soldier.level()!=source.getLevel()||soldier.faction()!=vehicle.faction()||soldier.isPassenger()||soldier.distanceToSqr(vehicle)>64||!MilitaryVehicle.crewOperational(soldier))return fail(source,"只能让8格内同阵营、有效且未乘车的真实军人上车。");soldiers.add(soldier);
            }
            if(soldiers.size()+vehicle.getPassengers().size()>vehicle.definition().seats().size())return fail(source,"真实座位不足。");
            soldiers.sort(Comparator.comparingInt((MilitarySoldier soldier)->soldier.commandRole().ordinal()).thenComparing(MilitarySoldier::getUUID));int count=0;
            for(var soldier:soldiers)if(soldier.startRiding(vehicle))count++;
            final int boarded=count;source.sendSuccess(()->Component.literal("已让 "+boarded+" 名原有军人真实上车，人员单位与军衔保留。"),false);return count;
        }))));
        root.then(Commands.literal("create").then(Commands.argument("vehicles",EntityArgument.entities()).then(Commands.argument("officer",EntityArgument.entity()).executes(context->{
            var source=context.getSource();var vehicles=new ArrayList<MilitaryVehicle>();
            for(var entity:EntityArgument.getEntities(context,"vehicles")){if(!(entity instanceof MilitaryVehicle vehicle))return fail(source,"只可选择1–4辆已存在的真实车辆。");vehicles.add(vehicle);}
            var id=VehicleFleetSystem.create(source.getLevel(),vehicles,EntityArgument.getEntity(context,"officer").getUUID());
            if(id.isEmpty())return fail(source,"登记失败：需要同阵营、未编队的真实车辆和车内有效军官。");
            source.sendSuccess(()->Component.literal("车队已登记："+id.get()+"。未增加人员、燃料或炮弹。"),false);return 1;
        }))));
        root.then(Commands.literal("list").executes(context->{
            var source=context.getSource();var fleets=VehicleFleetSystem.fleets(source.getLevel());
            for(var fleet:fleets)source.sendSuccess(()->Component.literal(describe(source,fleet)),false);return fleets.size();
        }));
        root.then(Commands.literal("inspect").then(Commands.argument("fleet",StringArgumentType.word()).executes(context->{
            var source=context.getSource();var id=id(StringArgumentType.getString(context,"fleet"));
            var fleet=VehicleFleetSystem.fleets(source.getLevel()).stream().filter(f->f.id().equals(id)).findFirst().orElse(null);
            if(fleet==null)return fail(source,"找不到本维度的车队。");source.sendSuccess(()->Component.literal(describe(source,fleet)),false);return 1;
        })));
        root.then(Commands.literal("order").then(Commands.argument("fleet",StringArgumentType.word()).then(Commands.argument("mission",StringArgumentType.word())
                .suggests((context,builder)->SharedSuggestionProvider.suggest(new String[]{"guard","patrol","clear_area","return","move"},builder))
                .executes(context->order(context,false)).then(Commands.argument("position",BlockPosArgument.blockPos()).executes(context->order(context,true))))));
        root.then(Commands.literal("remove").then(Commands.argument("fleet",StringArgumentType.word()).executes(context->{
            var source=context.getSource();var id=id(StringArgumentType.getString(context,"fleet"));
            if(id==null||!VehicleFleetSystem.remove(source.getLevel(),id))return fail(source,"找不到本维度的车队。");
            source.sendSuccess(()->Component.literal("已解除车队索引，原车辆和人员保留。"),false);return 1;
        })));
        return root;
    }
    private static int order(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context,boolean withPosition){
        var source=context.getSource();var id=id(StringArgumentType.getString(context,"fleet"));
        if(id==null||!VehicleFleetSystem.setMission(source.getLevel(),id,StringArgumentType.getString(context,"mission"),withPosition?BlockPosArgument.getBlockPos(context,"position"):null))return fail(source,"任务无效：MOVE需要坐标；车队必须在本维度。");
        source.sendSuccess(()->Component.literal("车队任务已更新。玩家占车、缺乘员或未知道路时安全停车；缺弹和重伤按有效车库返补。"),false);return 1;
    }
    private static UUID id(String value){try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){return null;}}
    private static String describe(CommandSourceStack source,VehicleFleet fleet){
        var budget=VehicleFleetSystem.budget(source.getLevel());return fleet.id()+" | "+fleet.faction().id()+" | "+fleet.mission()+" / "+fleet.status()+" | 后勤 "+VehicleFleetSystem.logisticsStatus(source.getLevel(),fleet.id())+" | 车辆 "+fleet.vehicles().size()+" | 正式军官 "+fleet.formalCommander()+" | 当前代理 "+VehicleFleetSystem.actingCommander(source.getLevel(),fleet.id())+" | 接敌 "+fleet.contacts()+"/已确认击倒 "+fleet.resolved()+" | 本tick规划 "+budget.plansThisTick()+"/2 | 最大搜索节点 "+budget.maxSearchNodes()+"/2048";
    }
    private static int fail(CommandSourceStack source,String message){source.sendFailure(Component.literal(message));return 0;}
    private VehicleFleetCommands(){}
}
