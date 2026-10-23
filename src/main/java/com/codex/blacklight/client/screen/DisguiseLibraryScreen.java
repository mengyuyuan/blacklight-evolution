package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.DisguiseControls;
import com.codex.blacklight.disguise.*;
import com.codex.blacklight.network.DisguiseSelectPayload;
import com.codex.blacklight.registry.ModAttachments;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import com.codex.blacklight.forge.PacketDistributor;

/** An owned collection: selecting an entry sends only its server revision and index. */
public final class DisguiseLibraryScreen extends Screen {
    private final Screen parent;
    private DisguiseLibrary library=DisguiseLibrary.EMPTY;
    private int page,selected,rows,left,listWidth,right,detailWidth;
    public DisguiseLibraryScreen(Screen parent){super(Component.translatable("screen.blacklight.identity_library"));this.parent=parent;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g){}
    @Override protected void init(){
        if(minecraft.player!=null)library=ModAttachments.getData(minecraft.player,ModAttachments.DISGUISE_LIBRARY);
        rows=Math.max(1,Math.min(8,(height-100)/26));left=12;listWidth=Math.min(190,Math.max(100,width*2/5));
        right=left+listWidth+12;detailWidth=Math.max(60,width-right-12);
        selected=Math.max(0,Math.min(selected,library.identities().size()-1));page=selected/rows;
        rebuild();
    }
    private void rebuild(){
        clearWidgets();
        int start=page*rows;
        for(int i=start;i<Math.min(start+rows,library.identities().size());i++){
            final int index=i;var identity=library.identities().get(i);
            addRenderableWidget(Button.builder(name(identity),b->{selected=index;rebuild();})
                    .bounds(left+24,38+(i-start)*26,listWidth-24,22).build());
        }
        var previous=addRenderableWidget(Button.builder(Component.literal("‹"),b->{page--;rebuild();}).bounds(left,height-54,28,20).build());
        var next=addRenderableWidget(Button.builder(Component.literal("›"),b->{page++;rebuild();}).bounds(left+listWidth-28,height-54,28,20).build());
        previous.active=page>0;next.active=(page+1)*rows<library.identities().size();
        var choose=addRenderableWidget(Button.builder(Component.translatable("screen.blacklight.identity_use"),b->{
            PacketDistributor.sendToServer(new DisguiseSelectPayload(library.revision(),selected));minecraft.setScreen(null);
        }).bounds(right,height-54,detailWidth,20).build());
        choose.active=!library.identities().isEmpty();
        addRenderableWidget(Button.builder(Component.translatable("gui.back"),b->onClose()).bounds(width-92,height-28,80,20).build());
    }
    private static Component name(DisguiseIdentity identity){return identity.valid()?identity.entityType().getDescription():Component.literal(identity.type());}
    @Override public void tick(){
        if(minecraft.player==null||!minecraft.player.isAlive()||!ModAttachments.getData(minecraft.player,ModAttachments.PLAYER_STATE).isPrototypeMode()){
            minecraft.setScreen(null);return;
        }
        var next=ModAttachments.getData(minecraft.player,ModAttachments.DISGUISE_LIBRARY);
        if(next.revision()!=library.revision()){library=next;selected=0;page=0;rebuild();}
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xF0101217);
        g.drawString(font,title,12,14,0xFFF3D9DD,false);
        g.drawString(font,Component.translatable("screen.blacklight.identity_count",library.identities().size(),DisguiseLibrary.LIMIT),right,14,0xFFAAA4AD,false);
        for(int i=page*rows;i<Math.min((page+1)*rows,library.identities().size());i++){
            int y=38+(i-page*rows)*26;
            if(i==selected)g.fill(left-2,y-2,left+listWidth+2,y+24,0xFF6A2939);
            var identity=library.identities().get(i);var egg=identity.valid()?SpawnEggItem.byId(identity.entityType()):null;
            g.renderItem(new ItemStack(egg==null?Items.PAPER:egg),left+3,y+3);
        }
        int textY=42;
        if(library.identities().isEmpty())textY=paragraph(g,Component.translatable("screen.blacklight.identity_empty"),right,textY,detailWidth,0xFFCEC4C9);
        else {
            var identity=library.identities().get(selected);var trait=SpeciesTrait.of(identity.type());
            textY=paragraph(g,name(identity),right,textY,detailWidth,0xFFFFA2AB)+10;
            textY=paragraph(g,Component.translatable("species.blacklight."+trait.id()),right,textY,detailWidth,0xFFF0E5E7)+6;
            textY=paragraph(g,Component.translatable("species.blacklight."+trait.id()+".desc"),right,textY,detailWidth,0xFFCEC4C9)+10;
            if(trait!=SpeciesTrait.NONE) {
                textY=paragraph(g,Component.translatable("screen.blacklight.species_key",DisguiseControls.ABILITY.getTranslatedKeyMessage(),trait.cooldown/20F),right,textY,detailWidth,0xFFFFB0AF)+6;
            }
            if(SpeciesTrait.fireproof(identity.type()))textY=paragraph(g,Component.translatable("species.blacklight.fireproof"),right,textY,detailWidth,0xFFFFB0AF)+6;
        }
        if(textY+24<height-62)paragraph(g,Component.translatable("screen.blacklight.identity_note"),right,textY,detailWidth,0xFF928A94);
        g.drawCenteredString(font,Component.literal((page+1)+" / "+Math.max(1,(library.identities().size()+rows-1)/rows)),left+listWidth/2,height-48,0xFFB7ABB5);
        super.render(g,mx,my,partial);
    }
    private int paragraph(GuiGraphics g,Component text,int x,int y,int w,int color){
        for(var line:font.split(text,w)) {
            if(y+font.lineHeight>height-62)break;
            g.drawString(font,line,x,y,color,false);y+=font.lineHeight+2;
        }
        return y;
    }
    @Override public void onClose(){minecraft.setScreen(parent);}
}
