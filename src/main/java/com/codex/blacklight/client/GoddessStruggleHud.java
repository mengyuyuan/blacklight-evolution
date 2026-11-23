package com.codex.blacklight.client;

import com.codex.blacklight.client.animation.GoddessPressurePose;
import com.codex.blacklight.combat.GoddessStruggle;
import com.codex.blacklight.network.GoddessStrugglePayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The server starts each response clock only after this prompt has actually been drawn. */
public final class GoddessStruggleHud {
    private static int cast=-1, phase=-1, sent=-1;
    private static java.util.UUID owner;
    private static int buttons(Minecraft mc){
        return (SkillBindings.down(mc.options.keyAttack)?GoddessStruggle.ATTACK:0)
                | (SkillBindings.down(mc.options.keyJump)?GoddessStruggle.JUMP:0)
                | (SkillBindings.down(mc.options.keyLeft)?GoddessStruggle.LEFT:0)
                | (SkillBindings.down(mc.options.keyRight)?GoddessStruggle.RIGHT:0);
    }
    public static void render(GuiGraphics gui){
        var mc=Minecraft.getInstance();
        if(mc.player==null || mc.screen!=null || mc.options.hideGui)return;
        var b=GoddessPressurePose.owner(mc.player);
        if(b==null || !b.goddess.attacks.struggle.holds())return;
        var q=b.goddess.attacks.struggle;
        String key=q.step().name().toLowerCase(java.util.Locale.ROOT);
        KeyMapping mapping=switch(q.step()){
            case PUSH -> mc.options.keyAttack;
            case JUMP -> mc.options.keyJump;
            case LEFT -> mc.options.keyLeft;
            case RIGHT -> mc.options.keyRight;
            case REPRISE -> q.remainingKnot()==GoddessStruggle.LEFT?mc.options.keyLeft:mc.options.keyRight;
            default -> null;
        };
        var text=Component.translatable("boss.blacklight.struggle."+key,mapping==null?Component.empty():mapping.getTranslatedKeyMessage());
        int x=gui.guiWidth()/2,y=gui.guiHeight()/2+20;
        int width=Math.max(78,mc.font.width(text)+20);
        PrototypeHealthHud.reserve(x-width/2,y-5,x+width/2,y+14);
        gui.fill(x-width/2,y-5,x+width/2,y+14,0xc523131b);
        gui.drawCenteredString(mc.font,text,x,y,0xffffe7db);
        if(q.step()==GoddessStruggle.Step.PUSH)
            gui.fill(x-width/2,y+12,x-width/2+(int)(width*q.effort(0)),y+14,0xffff927e);
        if(mapping==null)return;
        int bits=buttons(mc),seq=b.goddess.attacks.sequence(),token=q.promptToken();
        if(seq!=cast || token!=phase || bits!=sent || !b.getUUID().equals(owner)){
            cast=seq;phase=token;sent=bits;owner=b.getUUID();
            PacketDistributor.sendToServer(new GoddessStrugglePayload(b.getId(),seq,(token<<4)|bits));
        }
    }
    private GoddessStruggleHud(){}
}
