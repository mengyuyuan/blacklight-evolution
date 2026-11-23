package com.codex.blacklight.world;

import com.codex.blacklight.network.ManualPagesPayload;
import com.codex.blacklight.forge.PacketDistributor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.util.ArrayList;

/** Personal field reports retain their original translation and live arguments across respawn. */
public final class FieldNotes {
    private static final String NOTES = "BlacklightFieldNotes";
    private static final int LIMIT = 32;
    private FieldNotes() {}

    public static void record(Player reader, Component message) {
        if (!(reader instanceof ServerPlayer player)) return;
        String encoded = Component.Serializer.toJson(message);
        if (encoded.length() > ManualPagesPayload.MAX_LINE) return;
        String key = message.getContents() instanceof TranslatableContents text ? text.getKey() : encoded;
        CompoundTag saved = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        ListTag notes = saved.getList(NOTES, Tag.TAG_COMPOUND);
        for (int i = notes.size() - 1; i >= 0; i--) {
            if (!key.equals(notes.getCompound(i).getString("key"))) continue;
            if (i == notes.size()-1 && encoded.equals(notes.getCompound(i).getString("text"))) return;
            notes.remove(i);
            break;
        }
        CompoundTag row = new CompoundTag();
        row.putString("key", key);
        row.putString("text", encoded);
        notes.add(row);
        while (notes.size() > LIMIT) notes.remove(0);
        saved.put(NOTES, notes);
        player.getPersistentData().put(Player.PERSISTED_NBT_TAG, saved);
    }

    public static void open(ServerPlayer player, boolean handbook) {
        var lines = new ArrayList<String>();
        for (Component lead : SurvivalGuide.currentLeads(player)) lines.add(Component.Serializer.toJson(lead));
        ListTag notes = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getList(NOTES, Tag.TAG_COMPOUND);
        for (int i=notes.size()-1; i>=Math.max(0,notes.size()-LIMIT); i--) {
            String line = notes.getCompound(i).getString("text");
            if (!line.isEmpty() && line.length() <= ManualPagesPayload.MAX_LINE) lines.add(line);
        }
        PacketDistributor.sendToPlayer(player,new ManualPagesPayload(handbook,lines));
    }
}
