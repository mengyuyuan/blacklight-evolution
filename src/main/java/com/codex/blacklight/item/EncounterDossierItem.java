package com.codex.blacklight.item;

import com.codex.blacklight.world.SurvivalGuide;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/** Decode each document in the dimension its chart describes. */
public final class EncounterDossierItem extends Item {
    private final String site;
    private final TagKey<Structure> destinations;
    public EncounterDossierItem(Properties properties, String site) {
        super(properties);
        this.site = site;
        destinations = TagKey.create(Registries.STRUCTURE, new ResourceLocation("blacklight", site));
    }
    @Override public void appendHoverText(ItemStack stack, Level context,
            java.util.List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("survival.blacklight.read." + site));
    }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer reader))
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        boolean plaza="mother_plaza".equals(site);
        var dimension=plaza?com.codex.blacklight.city.CityTravel.WORLD:Level.OVERWORLD;
        if (level.dimension()!=dimension || player.isSpectator()) {
            player.displayClientMessage(Component.translatable(plaza?"survival.blacklight.enter_futian":"survival.blacklight.return_overworld"), false);
            return InteractionResultHolder.fail(stack);
        }
        player.getCooldowns().addCooldown(this, 40);
        if(plaza){
            if(!com.codex.blacklight.city.MotherPlazaStructure.chart(server)){
                player.displayClientMessage(Component.translatable("survival.blacklight.city_site_unavailable"),false);
                return InteractionResultHolder.fail(stack);
            }
            SurvivalGuide.remember(reader,site,com.codex.blacklight.city.MotherPlazaStructure.console(server));
            return InteractionResultHolder.success(stack);
        }
        BlockPos dest=server.findNearestMapStructure(destinations,player.blockPosition(),8,false);
        if (dest == null) {
            player.displayClientMessage(Component.translatable(plaza?"survival.blacklight.city_site_unavailable":"survival.blacklight.no_site"), false);
            return InteractionResultHolder.fail(stack);
        }
        var map = MapItem.create(level, dest.getX(), dest.getZ(), (byte) 2, true, true);
        MapItem.renderBiomePreviewMap(server, map);
        MapItemSavedData.addTargetDecoration(map, dest, "+", net.minecraft.world.level.saveddata.maps.MapDecoration.Type.RED_X);
        map.setHoverName(Component.translatable("survival.blacklight.map." + site));
        SurvivalGuide.remember(reader, site, dest);
        return InteractionResultHolder.success(ItemUtils.createFilledResult(stack, player, map));
    }
}
