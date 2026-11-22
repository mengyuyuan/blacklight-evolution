package com.codex.blacklight.world;

import com.codex.blacklight.power.GrowthSystem;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

public final class SurvivalGuide {
    private SurvivalGuide() {}
    private static final String[] SITES = {"on_research_station_maps", "lost_convoy", "mother_plaza", "dispatch_intersection"};

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        BossSiteData.get(player.server.overworld()).deliver(player);
        if (!done(player, "root")) {
            GrowthSystem.award(player, "root");
            player.displayClientMessage(introduction(), false);
        }
    }
    @SubscribeEvent public static void outbreak(net.minecraftforge.event.TickEvent.PlayerTickEvent event){
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END)return;
        if(event.player instanceof ServerPlayer player && player.tickCount%20==0
                && player.level().dimension()==com.codex.blacklight.city.CityTravel.WORLD
                && !player.isSpectator()
                && done(player,"plaza_cleared") && !done(player,"greene_outbreak"))GreeneOutbreak.arrive(player);
    }
    @SubscribeEvent public static void captive(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){
        if(event.getEntity() instanceof net.minecraft.world.entity.Mob mob && !mob.level().isClientSide){
            com.codex.blacklight.combat.GreeneMolt.orphanCheck(mob);
            com.codex.blacklight.combat.GoddessAttacks.releaseBrood(mob);
            if(mob.getPersistentData().getBoolean("GreeneBroodFall")){
                mob.fallDistance=0;
                if(mob.onGround())mob.getPersistentData().remove("GreeneBroodFall");
            }
        }
    }
    public static boolean done(ServerPlayer player, String name) {
        var advancement = player.server.getAdvancements().getAdvancement(new ResourceLocation("blacklight", name));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }
    private static Component introduction() {
        return Component.translatable("message.blacklight.survival_start", Component.keybind("key.advancements"));
    }
    public static Component consumeHint() {
        return Component.translatable("message.blacklight.awakening_guide", Component.keybind("key.blacklight.prototype_mode"),
                Component.keybind("key.blacklight.grab"), Component.keybind("key.use"));
    }
    public static void remember(ServerPlayer player, String site, BlockPos pos) {
        var data = player.getPersistentData();
        var saved = data.getCompound(Player.PERSISTED_NBT_TAG);
        saved.putLong("blacklight_" + site, pos.asLong());
        saved.putString("blacklight_" + site + "_dimension", player.level().dimension().location().toString());
        data.put(Player.PERSISTED_NBT_TAG, saved);
        destination(player, site, pos);
    }
    private static void destination(ServerPlayer player, String site, BlockPos pos) {
        var saved=player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        boolean city=saved.getString("blacklight_"+site+"_dimension").equals("blacklight:world");
        if(city && site.equals("mother_plaza")){
            int column=net.minecraft.util.Mth.clamp(pos.getX()/480,0,2);
            int row=net.minecraft.util.Mth.clamp(pos.getZ()/411,0,2);
            player.displayClientMessage(Component.translatable("survival.blacklight.plaza_search",
                    Component.translatable("survival.blacklight.sector."+(row*3+column))),false);
        } else {
            player.displayClientMessage(Component.translatable("survival.blacklight.destination",
                    Component.translatable("survival.blacklight.map."+site),pos.getX(),pos.getZ()),false);
        }
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blacklightguide").executes(ctx -> {
            var player = ctx.getSource().getPlayerOrException();
            Component next;
            if (!com.codex.blacklight.registry.ModAttachments.getData(player, com.codex.blacklight.registry.ModAttachments.PLAYER_STATE).isInfected()) {
                next = done(player, "sealed_sample") ? Component.translatable("advancement.blacklight.sealed_sample.desc")
                        : done(player, "research_station") ? Component.translatable("survival.blacklight.cold_storage") : introduction();
            } else if (done(player,"greene_outbreak")) next=Component.translatable("survival.blacklight.journal.greene_defeated");
            else if (done(player,"plaza_cleared")) next=Component.translatable("survival.blacklight.journal.mother_core");
            else if (done(player,"bloodtox_starter")) next=Component.translatable("survival.blacklight.journal.bloodtox_starter");
            else if (done(player,"convoy_cleared")) next=Component.translatable("survival.blacklight.after_convoy");
            else if (done(player,"containment_decoder")) next=Component.translatable("survival.blacklight.journal.containment_decoder");
            else if (done(player,"evolved_ganglion")) next=Component.translatable("survival.blacklight.journal.evolved_ganglion");
            else if (!done(player, "first_consume")) next = consumeHint();
            else if (!done(player, "first_evolution")) next = Component.translatable("survival.blacklight.upgrade", Component.keybind("key.blacklight.upgrades"));
            else next = Component.translatable("survival.blacklight.branch");
            player.displayClientMessage(next, false);
            var saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
            for (String site : SITES) if (saved.contains("blacklight_" + site))
                destination(player, site, BlockPos.of(saved.getLong("blacklight_" + site)));
            return 1;
        }));
    }
}
