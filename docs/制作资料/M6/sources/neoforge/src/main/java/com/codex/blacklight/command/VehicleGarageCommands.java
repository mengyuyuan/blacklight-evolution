package com.codex.blacklight.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.codex.blacklight.city.MilitaryBaseAccess;
import com.codex.blacklight.military.vehicle.VehicleGarageService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.network.chat.Component;

/** Explicit operator preview/confirmation; no old-world automatic retrofit. */
public final class VehicleGarageCommands {
    public static LiteralArgumentBuilder<CommandSourceStack> commands() {
        var root=Commands.literal("garage").requires(source->source.hasPermission(2));
        root.then(Commands.literal("preview").then(Commands.argument("base",StringArgumentType.string())
                .suggests((context,builder)->SharedSuggestionProvider.suggest(MilitaryBaseAccess.nearby(context.getSource().getLevel(),context.getSource().getPosition()).stream().map(base->StringArgumentType.escapeIfRequired(base.id())),builder))
                .then(Commands.argument("position",BlockPosArgument.blockPos()).executes(context->preview(context,0))
                        .then(Commands.argument("turn",IntegerArgumentType.integer(0,3)).executes(context->preview(context,IntegerArgumentType.getInteger(context,"turn")))))));
        root.then(Commands.literal("attach").then(Commands.argument("token",UuidArgument.uuid()).executes(context->{
            var source=context.getSource();String id=VehicleGarageService.attach(source.getLevel(),UuidArgument.getUuid(context,"token"));
            if(id.isEmpty())return fail(source,"确认无效、已过期或现场已变化；请重新预览。未覆盖冲突方块。");
            source.sendSuccess(()->Component.literal("车库已附加并登记："+id+"。初始车辆等待原有空闲人员抵达，不补造士兵。"),true);return 1;
        })));
        root.then(Commands.literal("status").executes(context->{
            var source=context.getSource();var statuses=VehicleGarageService.status(source.getLevel(),source.getPosition());
            if(statuses.isEmpty())source.sendSuccess(()->Component.literal("当前维度没有已登记车库。"),false);
            for(String status:statuses)source.sendSuccess(()->Component.literal(status),false);return statuses.size();
        }));
        root.then(Commands.literal("bases").executes(context->{
            var source=context.getSource();var bases=MilitaryBaseAccess.nearby(source.getLevel(),source.getPosition());
            for(var base:bases)source.sendSuccess(()->Component.literal(StringArgumentType.escapeIfRequired(base.id())+" | "+base.faction().id()+" | 中心 "+base.center().toShortString()+" | 地面Y="+(base.center().getY()-1)+" | "+(base.operational()?"核心有效":base.loaded()?"核心失效":"未完整加载")),false);
            if(bases.isEmpty())source.sendSuccess(()->Component.literal("附近没有已登记基地；先加载基地所在区域。"),false);return bases.size();
        }));
        return root;
    }
    private static int preview(CommandContext<CommandSourceStack> context,int rotation) {
        var source=context.getSource();var at=BlockPosArgument.getBlockPos(context,"position");
        var preview=VehicleGarageService.preview(source.getLevel(),StringArgumentType.getString(context,"base"),at,rotation);
        if(!preview.allowed())return fail(source,"车库预览拒绝："+preview.reason());
        var bounds=preview.bounds();
        source.sendSuccess(()->Component.literal("车库占地31×27，范围 ["+bounds.minX()+", "+at.getY()+", "+bounds.minZ()+"] 至 ["+bounds.maxX()+", "+bounds.maxY()+", "+bounds.maxZ()+"]；需现有9格出口通道。"),false);
        source.sendSuccess(()->Component.literal("约60秒内确认：/blacklight military garage attach "+preview.token()+"（确认时重新检查冲突）"),false);return 1;
    }
    private static int fail(CommandSourceStack source,String message){source.sendFailure(Component.literal(message));return 0;}
    private VehicleGarageCommands() { }
}
