package com.codex.blacklight.client;

import com.codex.blacklight.network.ManualPagesPayload;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class ManualReader {
    private static final Pattern ITEM = Pattern.compile("\\{([a-z0-9_.-]+:[a-z0-9_./-]+)\\}");
    private ManualReader() {}

    public static void open(ManualPagesPayload snapshot) {
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        var pages = new ArrayList<Component>();
        if (snapshot.handbook()) {
            String locale = mc.getLanguageManager().getSelected().equals("zh_cn") ? "zh_cn" : "en_us";
            var path = new ResourceLocation("blacklight","field_manual/"+locale+".json");
            try (var reader = mc.getResourceManager().openAsReader(path)) {
                var index = Component.translatable("item.blacklight.survival_manual").append("\n\n");
                pages.add(index);
                for (var node : JsonParser.parseReader(reader).getAsJsonArray()) {
                    var section = node.getAsJsonObject();
                    var click = new ClickEvent(ClickEvent.Action.CHANGE_PAGE, Integer.toString(pages.size()+1));
                    index.append(Component.literal(section.get("title").getAsString()+"\n").withStyle(s -> s.withUnderlined(true).withClickEvent(click)));
                    for (var page : section.getAsJsonArray("pages")) {
                        var pair = page.getAsJsonArray();
                        String body = ITEM.matcher(pair.get(1).getAsString()).replaceAll(m -> {
                            var id = ResourceLocation.tryParse(m.group(1));
                            return java.util.regex.Matcher.quoteReplacement(id == null ? m.group(1) : BuiltInRegistries.ITEM.get(id).getDescription().getString());
                        });
                        append(pages, pair.get(0).getAsString()+"\n\n"+body);
                    }
                }
            } catch (IOException error) {
                com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Cannot read survival manual",error);
            }
        }
        var notes = new StringBuilder(Component.translatable("book.blacklight.notes").getString());
        for (String encoded : snapshot.lines()) {
            var message = Component.Serializer.fromJson(encoded);
            if (message != null) notes.append("\n\n").append(message.getString());
        }
        append(pages, notes.toString());
        mc.setScreen(new BookViewScreen(new BookViewScreen.BookAccess(){
            public int getPageCount(){return pages.size();}
            public FormattedText getPageRaw(int index){return pages.get(index);}
        }));
    }

    private static void append(List<Component> pages, String text) {
        var font = Minecraft.getInstance().font;
        var lines = font.getSplitter().splitLines(Component.literal(text), 112, Style.EMPTY);
        int count = Math.max(1, 117 / font.lineHeight - 1);
        for (int i=0; i<lines.size(); i+=count) {
            pages.add(Component.literal(String.join("\n",lines.subList(i,Math.min(i+count,lines.size())).stream().map(FormattedText::getString).toList())));
        }
    }
}
