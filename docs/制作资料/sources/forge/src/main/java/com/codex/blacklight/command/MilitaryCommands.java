package com.codex.blacklight.command;

import com.codex.blacklight.entity.MilitarySoldier;
import com.codex.blacklight.infection.BlacklightInfectionSystem;
import com.codex.blacklight.military.*;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Explicit, permission-gated organization edits. A failed draft never changes live rosters. */
public final class MilitaryCommands {
    private MilitaryCommands(){}
    private static RequiredArgumentBuilder<CommandSourceStack,String> unit(String argument){
        return Commands.argument(argument,StringArgumentType.string()).suggests((c,b)->SharedSuggestionProvider.suggest(
                MilitaryOrganizationSystem.organization(c.getSource().getLevel()).units().stream().map(u->StringArgumentType.escapeIfRequired(u.name())),b));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,String> faction(){
        return Commands.argument("faction",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(MilitaryFaction.values()).map(MilitaryFaction::id),b));
    }
    private static RequiredArgumentBuilder<CommandSourceStack,String> preset(){
        return Commands.argument("preset",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(MilitaryUnitType.values()).map(MilitaryUnitType::id),b));
    }
    public static LiteralArgumentBuilder<CommandSourceStack> commands(){
        var root=Commands.literal("military").requires(s->s.hasPermission(2));var units=Commands.literal("unit");
        units.then(Commands.literal("create").then(faction().then(preset().then(Commands.argument("name",StringArgumentType.string())
                .executes(c->create(c,0)).then(Commands.argument("capacity",IntegerArgumentType.integer(1,256)).executes(c->create(c,IntegerArgumentType.getInteger(c,"capacity"))))))));
        units.then(Commands.literal("assign").then(unit("unit").then(Commands.argument("entities",EntityArgument.entities()).executes(c->assign(c,false)))));
        units.then(Commands.literal("unassign").then(unit("unit").then(Commands.argument("entities",EntityArgument.entities()).executes(c->assign(c,true)))));
        units.then(Commands.literal("resize").then(unit("unit").then(Commands.argument("capacity",IntegerArgumentType.integer(1,32)).executes(MilitaryCommands::resize))));
        units.then(Commands.literal("attach").then(unit("parent").then(unit("child").executes(MilitaryCommands::attach))));
        units.then(Commands.literal("detach").then(unit("unit").executes(c->simple(c,false))));
        units.then(Commands.literal("remove").then(unit("unit").executes(c->simple(c,true))));
        units.then(Commands.literal("commander").then(unit("unit").then(Commands.argument("entity",EntityArgument.entity()).executes(MilitaryCommands::appoint))));
        root.then(units);
        root.then(Commands.literal("rank").then(Commands.argument("entities",EntityArgument.entities()).then(Commands.argument("rank",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(MilitaryRank.values()).map(MilitaryRank::id),b)).executes(MilitaryCommands::rank))));
        root.then(Commands.literal("role").then(Commands.argument("entities",EntityArgument.entities())
                .then(Commands.argument("role",StringArgumentType.word()).suggests((c,b)->SharedSuggestionProvider.suggest(Arrays.stream(MilitaryRole.values()).map(MilitaryRole::id),b)).executes(MilitaryCommands::role))));
        root.then(Commands.literal("order").then(unit("unit").then(Commands.argument("mode",StringArgumentType.word())
                .suggests((c,b)->SharedSuggestionProvider.suggest(new String[]{"guard","patrol","move","regroup","return","inherit"},b))
                .executes(c->order(c,false)).then(Commands.argument("pos",Vec3Argument.vec3()).executes(c->order(c,true))))));
        root.then(Commands.literal("organize").then(faction().then(preset().then(Commands.argument("entities",EntityArgument.entities()).executes(MilitaryCommands::organize)))));
        root.then(Commands.literal("inspect").then(unit("unit").executes(MilitaryCommands::inspect)));
        root.then(CityMilitaryCommands.bases());
        root.then(CityMilitaryCommands.outfit());
        return root;
    }
    private static UUID resolve(CommandContext<CommandSourceStack> c,String argument){
        String token=StringArgumentType.getString(c,argument);var org=MilitaryOrganizationSystem.organization(c.getSource().getLevel());
        try{UUID id=UUID.fromString(token);return org.unit(id).isPresent()?id:null;}catch(IllegalArgumentException ignored){return org.named(token).map(MilitaryOrganization.UnitView::id).orElse(null);}
    }
    private static int fail(CommandSourceStack source,String reason){source.sendFailure(Component.translatable("command.blacklight.military."+reason));return 0;}
    private static int done(CommandSourceStack source,String label,int count){
        source.sendSuccess(()->Component.translatable("command.blacklight.military.changed",label,count),false);return count;
    }
    private static List<MilitarySoldier> selected(CommandContext<CommandSourceStack> c,String argument) throws CommandSyntaxException{
        var result=new ArrayList<MilitarySoldier>();
        for(var entity:EntityArgument.getEntities(c,argument)){
            if(!(entity instanceof MilitarySoldier soldier)||entity.level()!=c.getSource().getLevel()||!soldier.isAlive()||soldier.isRemoved()||BlacklightInfectionSystem.isInfected(soldier))return null;
            result.add(soldier);
        }
        result.sort(Comparator.comparing(MilitarySoldier::getUUID));return result;
    }
    private static MilitaryOrder.Point origin(MilitarySoldier soldier){var home=soldier.home();return new MilitaryOrder.Point(home.getX()+.5,home.getY(),home.getZ()+.5);}
    private static int create(CommandContext<CommandSourceStack> c,int capacity){
        var source=c.getSource();var faction=MilitaryFaction.parse(StringArgumentType.getString(c,"faction")).orElse(null);
        var preset=MilitaryUnitType.parse(StringArgumentType.getString(c,"preset")).orElse(null);String name=StringArgumentType.getString(c,"name");
        if(faction==null||preset==null)return fail(source,"value");
        int size=capacity==0?preset.defaultSize(faction):capacity;var pos=source.getPosition();UUID id=UUID.randomUUID();
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->org.create(id,faction,preset,name,size,new MilitaryOrder.Point(pos.x,pos.y,pos.z))))return fail(source,"operation");
        return done(source,name,1);
    }
    private static int assign(CommandContext<CommandSourceStack> c,boolean remove) throws CommandSyntaxException{
        var source=c.getSource();UUID id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        var troops=selected(c,"entities");if(troops==null||troops.isEmpty())return fail(source,"members");
        var view=MilitaryOrganizationSystem.organization(source.getLevel()).unit(id).orElseThrow();
        if(troops.stream().anyMatch(s->s.faction()!=view.faction())||!remove&&!view.type().leaf())return fail(source,"members");
        boolean initiallyEmpty=view.members().isEmpty();
        boolean changed=MilitaryOrganizationSystem.transaction(source.getLevel(),org->{
            if(remove){
                var roster=org.allMembers(id);if(troops.stream().anyMatch(s->!roster.contains(s.getUUID())))return false;
                for(var soldier:troops)org.removeMember(soldier.getUUID());return true;
            }
            for(var soldier:troops){
                if(org.leafOf(soldier.getUUID()).filter(id::equals).isPresent())continue;
                org.removeMember(soldier.getUUID());if(!org.assign(id,soldier.getUUID(),soldier.rank()))return false;
            }
            if(initiallyEmpty)MilitaryOrganizationSystem.appointHighest(org,id);return true;
        });
        if(!changed)return fail(source,"operation");
        MilitaryOrganizationSystem.manual(source.getLevel(),id);for(var soldier:troops)soldier.setAutomaticUnitJoin(!remove);
        return done(source,view.name(),troops.size());
    }
    private static int resize(CommandContext<CommandSourceStack> c){
        var source=c.getSource();var id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->org.resize(id,IntegerArgumentType.getInteger(c,"capacity"))))return fail(source,"operation");
        MilitaryOrganizationSystem.manual(source.getLevel(),id);return done(source,StringArgumentType.getString(c,"unit"),1);
    }
    private static int attach(CommandContext<CommandSourceStack> c){
        var source=c.getSource();var parent=resolve(c,"parent");var child=resolve(c,"child");if(parent==null||child==null)return fail(source,"unit");
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->{org.detach(child);return org.attach(parent,child);}))return fail(source,"operation");
        MilitaryOrganizationSystem.manual(source.getLevel(),parent);return done(source,StringArgumentType.getString(c,"parent"),1);
    }
    private static int simple(CommandContext<CommandSourceStack> c,boolean remove){
        var source=c.getSource();var id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->remove?org.removeUnit(id):org.detach(id)))return fail(source,"operation");
        MilitaryOrganizationSystem.manual(source.getLevel(),id);return done(source,StringArgumentType.getString(c,"unit"),1);
    }
    private static int appoint(CommandContext<CommandSourceStack> c) throws CommandSyntaxException{
        var source=c.getSource();var id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        var entity=EntityArgument.getEntity(c,"entity");
        if(!(entity instanceof MilitarySoldier soldier)||entity.level()!=source.getLevel()||!soldier.isAlive())return fail(source,"members");
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->org.appoint(id,entity.getUUID())))return fail(source,"members");
        MilitaryOrganizationSystem.data(source.getLevel()).setCommanderManual(id,true);
        MilitaryOrganizationSystem.manual(source.getLevel(),id);return done(source,StringArgumentType.getString(c,"unit"),1);
    }
    private static int rank(CommandContext<CommandSourceStack> c) throws CommandSyntaxException{
        var source=c.getSource();var rank=MilitaryRank.parse(StringArgumentType.getString(c,"rank")).orElse(null);var troops=selected(c,"entities");
        if(rank==null)return fail(source,"value");if(troops==null||troops.isEmpty())return fail(source,"members");
        MilitaryOrganizationSystem.transaction(source.getLevel(),org->{for(var soldier:troops)org.rank(soldier.getUUID(),rank);return true;});
        for(var soldier:troops)soldier.setRank(rank);return done(source,rank.id(),troops.size());
    }
    private static int role(CommandContext<CommandSourceStack> c) throws CommandSyntaxException{
        var source=c.getSource();var role=MilitaryRole.parse(StringArgumentType.getString(c,"role")).orElse(null);var troops=selected(c,"entities");
        if(role==null)return fail(source,"value");if(troops==null||troops.isEmpty())return fail(source,"members");
        for(var soldier:troops)soldier.setCommandRole(role);
        return done(source,role.id(),troops.size());
    }
    private static int order(CommandContext<CommandSourceStack> c,boolean position) throws CommandSyntaxException{
        var source=c.getSource();var id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        String mode=StringArgumentType.getString(c,"mode");MilitaryOrder intent=null;
        if(!mode.equals("inherit")){
            MilitaryOrder.Mode kind;try{kind=MilitaryOrder.Mode.valueOf(mode.toUpperCase(Locale.ROOT));}catch(IllegalArgumentException e){return fail(source,"value");}
            if(kind==MilitaryOrder.Mode.MOVE&&!position)return fail(source,"value");
            var point=position?Vec3Argument.getVec3(c,"pos"):null;
            intent=new MilitaryOrder(kind,point==null?null:new MilitaryOrder.Point(point.x,point.y,point.z));
        }
        var finalIntent=intent;if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->org.order(id,finalIntent)))return fail(source,"value");
        MilitaryOrganizationSystem.manual(source.getLevel(),id);return done(source,StringArgumentType.getString(c,"unit"),1);
    }
    private static int organize(CommandContext<CommandSourceStack> c) throws CommandSyntaxException{
        var source=c.getSource();var faction=MilitaryFaction.parse(StringArgumentType.getString(c,"faction")).orElse(null);
        var preset=MilitaryUnitType.parse(StringArgumentType.getString(c,"preset")).orElse(null);var troops=selected(c,"entities");
        if(faction==null||preset==null)return fail(source,"value");
        if(troops==null||troops.isEmpty()||troops.size()>MilitaryOrganization.MAX_TREE_MEMBERS||troops.stream().anyMatch(s->s.faction()!=faction)
                ||preset.leaf()&&troops.size()>MilitaryOrganization.MAX_LEAF)return fail(source,"members");
        troops.sort(Comparator.<MilitarySoldier>comparingInt(s->s.rank().ordinal()).reversed().thenComparing(MilitarySoldier::getUUID));
        String name=preset.id()+"-"+UUID.randomUUID().toString().substring(0,8);UUID[] created={null};
        if(!MilitaryOrganizationSystem.transaction(source.getLevel(),org->{
            for(var soldier:troops)org.removeMember(soldier.getUUID());
            created[0]=build(org,faction,preset,troops,name);return created[0]!=null;
        }))return fail(source,"operation");
        MilitaryOrganizationSystem.manual(source.getLevel(),created[0]);for(var soldier:troops)soldier.setAutomaticUnitJoin(true);
        return done(source,name,troops.size());
    }
    private static UUID build(MilitaryOrganization org,MilitaryFaction faction,MilitaryUnitType type,List<MilitarySoldier> troops,String name){
        UUID id=UUID.randomUUID();int capacity=Math.max(type.defaultSize(faction),troops.size());
        if(!org.create(id,faction,type,name,capacity,origin(troops.get(0))))return null;
        if(type.leaf()){
            for(var soldier:troops)if(!org.assign(id,soldier.getUUID(),soldier.rank()))return null;
        }else{
            MilitaryUnitType child=switch(type){
                case BATTALION->faction==MilitaryFaction.MILITARY?MilitaryUnitType.COMPANY:MilitaryUnitType.SQUADRON;
                case COMPANY->MilitaryUnitType.PLATOON;
                case PLATOON->MilitaryUnitType.SECTION;
                case SQUADRON->MilitaryUnitType.TACTICAL_SQUAD;
                default->throw new IllegalStateException("Leaf handled above");
            };
            int size=Math.max(child.defaultSize(faction),(troops.size()+MilitaryOrganization.MAX_CHILDREN-1)/MilitaryOrganization.MAX_CHILDREN);
            for(int start=0,index=1;start<troops.size();start+=size,index++){
                var childId=build(org,faction,child,troops.subList(start,Math.min(troops.size(),start+size)),name+"/"+child.id()+index);
                if(childId==null||!org.attach(id,childId))return null;
            }
        }
        MilitaryOrganizationSystem.appointHighest(org,id);return id;
    }
    private static int inspect(CommandContext<CommandSourceStack> c){
        var source=c.getSource();var id=resolve(c,"unit");if(id==null)return fail(source,"unit");
        var level=source.getLevel();var org=MilitaryOrganizationSystem.organization(level);var unit=org.unit(id).orElseThrow();var members=org.allMembers(id);
        long loaded=members.stream().filter(uuid->level.getEntity(uuid) instanceof MilitarySoldier).count();
        var acting=MilitaryFormationSystem.orderCommander(level,id);
        source.sendSuccess(()->Component.translatable("command.blacklight.military.inspect",unit.name(),Component.translatable(unit.faction().labelKey()),
                Component.translatable(unit.type().labelKey()),members.size(),unit.capacity(),loaded,unit.children().size()),false);
        source.sendSuccess(()->Component.translatable("command.blacklight.military.commander",String.valueOf(unit.commander()),acting.map(UUID::toString).orElse("-")),false);
        if(unit.parent()!=null)source.sendSuccess(()->Component.translatable("command.blacklight.military.parent",org.unit(unit.parent()).orElseThrow().name()),false);
        return Math.max(1,members.size());
    }
}
