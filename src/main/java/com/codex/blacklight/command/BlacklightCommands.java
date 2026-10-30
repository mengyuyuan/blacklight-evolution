package com.codex.blacklight.command;

import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class BlacklightCommands {
    private BlacklightCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("blacklight")
                .then(HiveCommands.commands())
                .then(MilitaryCommands.commands())
                .then(Commands.literal("infect").requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
                    state.infect();
                    player.setData(ModAttachments.PLAYER_STATE, state);
                    player.syncData(ModAttachments.PLAYER_STATE);
                    context.getSource().sendSuccess(() -> Component.translatable("command.blacklight.infected"), false);
                    return 1;
                }))
                .then(Commands.literal("hive_sample").requires(source->source.hasPermission(2)).executes(context->{
                    var p=context.getSource().getPlayerOrException();var hive=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HIVE.get().create(p.level());
                    var pos=net.minecraft.core.BlockPos.containing(p.position().add(p.getLookAngle().multiply(1,0,1).normalize().scale(14)));
                    for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)for(int y=0;y<=13;y++)
                        if(!p.level().getBlockState(pos.offset(x,y,z)).isAir()){context.getSource().sendFailure(Component.literal("Hive needs a clear 15 x 15 x 14 site."));return 0;}
                    com.codex.blacklight.world.HiveBuilding.build(p.serverLevel(),pos);
                    hive.moveTo(pos.getX()+.5,pos.getY()+1,pos.getZ()+.5,0,0);p.serverLevel().addFreshEntity(hive);return 1;
                }))
                .then(Commands.literal("evolved_sample").requires(source -> source.hasPermission(2)).executes(context->{
                    var player=context.getSource().getPlayerOrException();var level=player.serverLevel();
                    var host=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(level);if(host==null)return 0;
                    host.inherit(net.minecraft.world.entity.EntityType.ZOMBIE.create(level));
                    var genes=new java.util.EnumMap<com.codex.blacklight.infection.TissueCore,com.codex.blacklight.infection.TissueInheritance.Organ>(com.codex.blacklight.infection.TissueCore.class);
                    for(var core:new com.codex.blacklight.infection.TissueCore[]{com.codex.blacklight.infection.TissueCore.MUSCLE,com.codex.blacklight.infection.TissueCore.CARAPACE,com.codex.blacklight.infection.TissueCore.JAW,com.codex.blacklight.infection.TissueCore.GRASP,com.codex.blacklight.infection.TissueCore.BLADE,com.codex.blacklight.infection.TissueCore.HYDROSTAT}){
                        int weapons=core==com.codex.blacklight.infection.TissueCore.BLADE?com.codex.blacklight.infection.TissueInheritance.BLADE:core==com.codex.blacklight.infection.TissueCore.GRASP?com.codex.blacklight.infection.TissueInheritance.CLAWS:core==com.codex.blacklight.infection.TissueCore.HYDROSTAT?com.codex.blacklight.infection.TissueInheritance.TENDRIL:0;
                        genes.put(core,new com.codex.blacklight.infection.TissueInheritance.Organ(350,0,0,0,weapons,71+core.ordinal()));
                    }
                    long seed=com.codex.blacklight.infection.TissueOrgans.seed(host.getUUID());
                    var inventory=new com.codex.blacklight.infection.TissueInheritance(genes,6,2100,false,null,1,6).digest(1,true,seed,504000,com.codex.blacklight.infection.TissueInheritance.Rules.DEFAULT,com.codex.blacklight.infection.TissueCore.MUSCLE);
                    var tag=new net.minecraft.nbt.CompoundTag();host.saveWithoutId(tag);tag.getCompound("BlacklightHost").putString("NativeCore",com.codex.blacklight.infection.TissueCore.MUSCLE.key());
                    tag.putLong("BlacklightTissueAge",504000);tag.put("BlacklightTissueInheritance",com.codex.blacklight.infection.TissueInheritanceCodec.write(inventory));tag.putInt("BlacklightTissueNutrition",100);host.load(tag);host.advanceTissue(0);
                    var pos=player.position().add(player.getLookAngle().multiply(1,0,1).normalize().scale(8));host.moveTo(pos.x,pos.y,pos.z,player.getYRot()+180,0);
                    if(!level.noCollision(host,host.getBoundingBox())||!level.addFreshEntity(host)){context.getSource().sendFailure(Component.literal("这里空间不足，进化者样本未生成。"));return 0;}
                    host.setCustomName(Component.literal("刀臂进化者 · 试玩样本"));host.setPersistenceRequired();
                    if(!host.beginEvolvedMetamorphosis()){host.discard();context.getSource().sendFailure(Component.literal("这里没有完整蜕变所需空间。"));return 0;}
                    context.getSource().sendSuccess(()->Component.literal("已生成预置成熟器官的试玩样本，开始十秒蜕变。自然进化仍需满足真实吞噬门槛。"),false);return 1;
                }))
                .then(Commands.literal("infected_sample").requires(source -> source.hasPermission(2)).executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    var level=player.serverLevel();
                    var zombie=com.codex.blacklight.registry.ModEntities.BLACKLIGHT_HUNTER.get().create(level);
                    if(zombie==null)return 0;
                    var direction=player.getLookAngle().multiply(1,0,1).normalize();
                    var pos=player.position().add(direction.scale(3));
                    zombie.moveTo(pos.x,pos.y,pos.z,player.getYRot()+180,0);
                    zombie.setPersistenceRequired();
                    zombie.setCustomName(Component.literal("黑光感染体 · 重爪"));
                    level.addFreshEntity(zombie);

                    return 1;
                }))
                .then(Commands.literal("infect_mob").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("targets",net.minecraft.commands.arguments.EntityArgument.entities()).executes(context->{
                            int infected=0;
                            for(var target:net.minecraft.commands.arguments.EntityArgument.getEntities(context,"targets"))
                                if(target instanceof net.minecraft.world.entity.LivingEntity living
                                        &&com.codex.blacklight.infection.BlacklightInfectionSystem.infect(living,context.getSource().getEntity()))infected++;
                            int count=infected;
                            context.getSource().sendSuccess(()->Component.translatable("command.blacklight.infect_mob",count),false);
                            return count;
                        })))
                .then(Commands.literal("spawn_infected").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("species",StringArgumentType.word())
                                .suggests((context,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[]{"zombie","skeleton","creeper","cow","pig","sheep","wolf","spider","warden","iron_golem","wither","ender_dragon"},builder))
                                .executes(context->spawnInfected(context.getSource(),StringArgumentType.getString(context,"species"),null,false))
                                .then(Commands.argument("position",net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .executes(context->spawnInfected(context.getSource(),StringArgumentType.getString(context,"species"),
                                                net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context,"position"),false)))))
                .then(Commands.literal("spawn_colossus").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("species",StringArgumentType.word())
                                .suggests((context,builder)->net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        new String[]{"warden","iron_golem","wither","ender_dragon"},builder))
                                .executes(context->spawnInfected(context.getSource(),StringArgumentType.getString(context,"species"),null,true))
                                .then(Commands.argument("position",net.minecraft.commands.arguments.coordinates.Vec3Argument.vec3())
                                        .executes(context->spawnInfected(context.getSource(),StringArgumentType.getString(context,"species"),
                                                net.minecraft.commands.arguments.coordinates.Vec3Argument.getVec3(context,"position"),true)))))
                .then(Commands.literal("colossus_status").requires(source -> source.hasPermission(2)).executes(context->{
                    var player=context.getSource().getPlayerOrException();
                    context.getSource().sendSuccess(()->com.codex.blacklight.infection.ColossusEvolutionSystem.describe(player),false);
                    return 1;
                }))
                .then(Commands.literal("form").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((context, builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                new String[]{"none", "claws", "hammerfists", "blade", "whipfist"}, builder))
                        .executes(context -> {
                            String id = StringArgumentType.getString(context, "id");
                            var form = com.codex.blacklight.power.PowerForm.byId(id);
                            if (!form.id().equals(id)) return 0;
                            return com.codex.blacklight.power.PowerFormSystem.select(context.getSource().getPlayerOrException(), form, true) ? 1 : 0;
                        })))
                .then(Commands.literal("status").executes(context -> {
                    ServerPlayer player = context.getSource().getPlayerOrException();
                    BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
                    context.getSource().sendSuccess(() -> Component.literal(
                                    "infected=" + state.isInfected() +
                                    " mode=" + (state.isPrototypeMode() ? "prototype" : "vanilla") +
                                    " biomass=" + state.getBiomass() +
                                    " EP=" + state.getEvolutionPoints() +
                                    " power=" + state.getActivePower().id()), false);
                    return 1;
                }))
                .then(Commands.literal("grant_ep").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1, 100000))
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
                                    int amount = IntegerArgumentType.getInteger(context, "amount");
                                    state.addEvolutionPoints(amount);
                                    sync(player, state);
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "已获得 " + amount + " EP"), false);
                                    return amount;
                                })))
                .then(Commands.literal("claw_upgrade").requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            BlacklightState state = player.getData(ModAttachments.PLAYER_STATE);
                            String id = StringArgumentType.getString(context, "id");
                            ClawUpgrade upgrade = ClawUpgrade.byId(id);
                            if (upgrade == null) {
                                context.getSource().sendFailure(Component.literal(
                                        "未知升级；可用：range, damage, dashing_slice, pounce"));
                                return 0;
                            }
                            if (!state.buyClawUpgrade(upgrade)) {
                                context.getSource().sendFailure(Component.literal(
                                        "无法购买 " + id + "（需要 " + upgrade.epCost() + " EP，或已经拥有）"));
                                return 0;
                            }
                            sync(player, state);
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "已解锁利爪升级：" + id), false);
                            return 1;
                        }))));
    }

    private static void sync(ServerPlayer player, BlacklightState state) {
        player.setData(ModAttachments.PLAYER_STATE, state);
        player.syncData(ModAttachments.PLAYER_STATE);
    }

    private static int spawnInfected(CommandSourceStack source,String species,net.minecraft.world.phys.Vec3 requested,boolean colossal){
        var id=net.minecraft.resources.ResourceLocation.tryParse(species);
        var registry=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE;
        if(id==null||!id.getNamespace().equals("minecraft")||!registry.containsKey(id)){
            source.sendFailure(Component.translatable("command.blacklight.infected_unknown",species));return 0;
        }
        if(colossal&&com.codex.blacklight.entity.InfectedBossProfile.forSpecies(id.toString())==null){
            source.sendFailure(Component.translatable("command.blacklight.colossus_unknown",species));return 0;
        }
        var original=registry.get(id).create(source.getLevel());
        if(!(original instanceof net.minecraft.world.entity.Mob mob)){
            source.sendFailure(Component.translatable("command.blacklight.infected_unknown",species));return 0;
        }
        var infected=com.codex.blacklight.registry.ModEntities.INFECTED_HOST.get().create(source.getLevel());
        if(infected==null)return 0;
        infected.inherit(mob,colossal);
        var facing=net.minecraft.world.phys.Vec3.directionFromRotation(0,source.getRotation().y);
        var pos=requested!=null?requested:source.getPosition().add(facing.scale(infected.giantBoss()?48:4));
        infected.moveTo(pos.x,pos.y,pos.z,source.getRotation().y+180,0);
        infected.yBodyRot=infected.getYRot();infected.yHeadRot=infected.getYRot();
        var bounds=infected.evolutionVisualBounds(0);
        var areaMin=net.minecraft.core.BlockPos.containing(bounds.minX,bounds.minY,bounds.minZ);
        var areaMax=net.minecraft.core.BlockPos.containing(bounds.maxX,bounds.maxY,bounds.maxZ);
        if(bounds.minY<source.getLevel().getMinBuildHeight()||bounds.maxY>source.getLevel().getMaxBuildHeight()
                ||!source.getLevel().getWorldBorder().isWithinBounds(bounds)
                ||!source.getLevel().hasChunksAt(areaMin,areaMax)
                ||infected.evolutionCollisionBoxes(0).stream().anyMatch(box->!source.getLevel().noCollision(infected,box))){
            source.sendFailure(Component.translatable("command.blacklight.infected_blocked"));return 0;
        }
        if(!source.getLevel().addFreshEntity(infected))return 0;
        source.sendSuccess(()->Component.translatable("command.blacklight.infected_spawned",mob.getType().getDescription()),false);
        return 1;
    }
}
