package com.codex.blacklight.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleGarageCommandsTest {
    private CommandSourceStack source(int permission) { return new CommandSourceStack(CommandSource.NULL,Vec3.ZERO,Vec2.ZERO,null,permission,"garage-qa",Component.literal("garage-qa"),null,null); }
    private CommandDispatcher<CommandSourceStack> dispatcher() { var d=new CommandDispatcher<CommandSourceStack>();d.register(Commands.literal("military").then(VehicleGarageCommands.commands()));return d; }
    @Test void operatorCanParseConcretePreviewAndSingleTokenConfirmation() {
        var d=dispatcher();var s=source(2);
        assertFalse(d.parse("military garage preview base 100 80 0 1",s).getReader().canRead());
        assertFalse(d.parse("military garage attach 12345678-1234-1234-1234-123456789abc",s).getReader().canRead());
        assertFalse(d.parse("military garage status",s).getReader().canRead());
        assertFalse(d.parse("military garage bases",s).getReader().canRead());
    }
    @Test void ordinaryPlayersCannotReachPlacementBranchesAndMalformedTokensAreRejected() {
        assertTrue(dispatcher().parse("military garage preview base 100 80 0 1",source(0)).getReader().canRead());
        assertTrue(dispatcher().parse("military garage attach fake",source(2)).getReader().canRead());
    }
    @Test void realSavedBaseIdCanBeUsedAsAQuotedPreviewArgument() {
        assertFalse(dispatcher().parse("military garage preview \"251000:80:0:0:false\" 251080 80 34 0",source(2)).getReader().canRead());
    }
}
