package com.codex.blacklight.world;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class WikiCommandTest {
    @Test void ordinaryPlayerGetsClickableCommunityWikiLink() throws Exception {
        var messages = new ArrayList<Component>();
        var sink = new CommandSource() {
            public void sendSystemMessage(Component message) { messages.add(message); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var source = new CommandSourceStack(sink, Vec3.ZERO, Vec2.ZERO, null, 0,
                "wiki-test", Component.literal("wiki-test"), null, null);
        var dispatcher = new CommandDispatcher<CommandSourceStack>();
        SurvivalGuide.commands(new RegisterCommandsEvent(dispatcher, Commands.CommandSelection.ALL, null));
        assertEquals(1, dispatcher.execute("wiki", source));
        assertEquals(1, messages.size());
        var label = assertInstanceOf(TranslatableContents.class, messages.get(0).getContents());
        var link = assertInstanceOf(Component.class, label.getArgs()[0]);
        assertEquals("https://prototype.qibusu.com/wiki", link.getString());
        assertEquals(new ClickEvent(ClickEvent.Action.OPEN_URL, link.getString()), link.getStyle().getClickEvent());
    }
}
