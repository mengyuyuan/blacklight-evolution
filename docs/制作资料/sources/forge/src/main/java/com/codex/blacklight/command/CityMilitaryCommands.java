package com.codex.blacklight.command;

import com.codex.blacklight.city.*;
import com.codex.blacklight.entity.MilitarySoldier;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

final class CityMilitaryCommands {
    static LiteralArgumentBuilder<CommandSourceStack> bases(){
        var root=Commands.literal("base");
        root.then(Commands.literal("status").executes(c->{
            var statuses=CityMilitaryService.status(c.getSource().getLevel(),c.getSource().getPosition());
            for(String status:statuses)c.getSource().sendSuccess(()->Component.literal(status),false);
            return statuses.size();
        }));
        for(String kind:new String[]{"military","blackwatch"})root.then(Commands.literal("place").then(Commands.literal(kind)
                .executes(c->place(c,kind,false,0)).then(Commands.argument("pos",BlockPosArgument.blockPos()).executes(c->place(c,kind,true,0))
                        .then(Commands.argument("turn",IntegerArgumentType.integer(0,3)).executes(c->place(c,kind,true,IntegerArgumentType.getInteger(c,"turn")))))));
        return root;
    }
    private static int place(CommandContext<CommandSourceStack> c,String kind,boolean coordinates,int turn) throws CommandSyntaxException{
        var source=c.getSource();var at=coordinates?BlockPosArgument.getBlockPos(c,"pos"):BlockPos.containing(source.getPosition()).below();
        try{
            var site=new CityMilitaryPlan.Site(at.getX(),at.getZ(),at.getY(),turn,kind.equals("blackwatch"));
            String id=CityMilitaryPlacement.place(source.getLevel(),site);
            source.sendSuccess(()->Component.translatable("command.blacklight.base.placed",kind,id,site.width(),site.depth()),true);return 1;
        }catch(IllegalArgumentException e){source.sendFailure(Component.translatable("command.blacklight.base.refused",e.getMessage()));return 0;}
    }
    static LiteralArgumentBuilder<CommandSourceStack> outfit(){
        return Commands.literal("outfit").then(Commands.argument("entities",EntityArgument.entities())
                .then(Commands.argument("outfit",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"uniform","equipped"},b)).executes(c->{
                    String value=StringArgumentType.getString(c,"outfit");if(!value.equals("uniform")&&!value.equals("equipped"))return 0;
                    var selected=EntityArgument.getEntities(c,"entities");
                    if(selected.stream().anyMatch(e->!(e instanceof MilitarySoldier)||e.level()!=c.getSource().getLevel()))return 0;
                    for(var entity:selected)((MilitarySoldier)entity).setEquipped(value.equals("equipped"));
                    c.getSource().sendSuccess(()->Component.translatable("command.blacklight.military.changed",value,selected.size()),false);return selected.size();
                })));
    }
    private CityMilitaryCommands(){}
}
