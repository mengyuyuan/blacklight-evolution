package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.BlacklightUi;

import com.codex.blacklight.client.SkillBindings;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.DisguiseTogglePayload;
import com.codex.blacklight.network.PowerSelectPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** X-only morph choices. Independent of the weapon/utility wheel and its pages. */
public final class MorphWheelScreen extends Screen {
    private final KeyMapping binding;
    private int selected=1;
    private boolean submitted;
    public MorphWheelScreen(KeyMapping binding){super(Component.translatable("screen.blacklight.morph_wheel"));this.binding=binding;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    private boolean enabled(int slot){
        if(minecraft.player==null)return false;
        var slime=BiomassSystem.state(minecraft.player);
        if(slime.active())return slot==1&&slime.recallable();
        if(slot==2)return true;
        return slot==0?minecraft.player.getData(ModAttachments.DISGUISE_IDENTITY).valid():minecraft.player.getData(ModAttachments.PLAYER_STATE).hasUnlocked(PowerForm.BIOMASS);
    }
    private Component label(int slot){
        if(slot==2)return Component.translatable("screen.blacklight.identity_library");
        return Component.translatable(slot==0?"screen.blacklight.morph_identity":BiomassSystem.active(minecraft.player)?"screen.blacklight.morph_recall":"power.blacklight.biomass");
    }
    @Override public void tick(){
        if(minecraft.player==null||!minecraft.player.isAlive()||!minecraft.player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()
                ||minecraft.player.getData(ModAttachments.CONSUMING)||!minecraft.isWindowActive()){onClose();return;}
        if(!SkillBindings.down(binding))confirm();
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(minecraft.player==null)return;
        int cx=width/2,cy=height/2;float outer=Math.min(115,Math.min(width*.34F,height*.32F)),inner=outer*.44F;
        g.fill(0,0,width,height,0x7007090d);g.flush();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var mesh=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);var matrix=g.pose().last().pose();
        for(int slot=0;slot<3;slot++){
            boolean available=enabled(slot);
            int color=slot==selected?0xEF71383D:available?0xE227282D:0xDA1D1D23;
            int edge=slot==selected?BlacklightUi.RED:available?0xEB725357:0xCC414148;

            double middle=-Math.PI/2+slot*Math.PI*2/3,span=Math.PI/3-.07;
            for(int j=0;j<32;j++){
                double a=middle-span+2*span*j/32,b=middle-span+2*span*(j+1)/32;
                float ax=(float)Math.cos(a),ay=(float)Math.sin(a),bx=(float)Math.cos(b),by=(float)Math.sin(b);
                vertex(mesh,matrix,cx+ax*inner,cy+ay*inner,color);vertex(mesh,matrix,cx+ax*outer,cy+ay*outer,color);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,color);
                vertex(mesh,matrix,cx+ax*inner,cy+ay*inner,color);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,color);vertex(mesh,matrix,cx+bx*inner,cy+by*inner,color);
                float rim=outer-2;
                vertex(mesh,matrix,cx+ax*rim,cy+ay*rim,edge);vertex(mesh,matrix,cx+ax*outer,cy+ay*outer,edge);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,edge);
                vertex(mesh,matrix,cx+ax*rim,cy+ay*rim,edge);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,edge);vertex(mesh,matrix,cx+bx*rim,cy+by*rim,edge);
            }
        }
        for(int n=0;n<48;n++){
            double a=n*Math.PI/24,b=(n+1)*Math.PI/24;
            float radius=inner-2;
            vertex(mesh,matrix,cx,cy,0xED19171D);
            vertex(mesh,matrix,cx+radius*(float)Math.cos(a),cy+radius*(float)Math.sin(a),0xE9322B30);
            vertex(mesh,matrix,cx+radius*(float)Math.cos(b),cy+radius*(float)Math.sin(b),0xE9322B30);
        }
        BufferUploader.drawWithShader(mesh.buildOrThrow());RenderSystem.enableCull();RenderSystem.disableBlend();
        for(int slot=0;slot<3;slot++){
            double angle=-Math.PI/2+slot*Math.PI*2/3;
            int x=cx+(int)(Math.cos(angle)*outer*.72),y=cy+(int)(Math.sin(angle)*outer*.72);
            var item=switch(slot){case 0->net.minecraft.world.item.Items.PLAYER_HEAD;case 1->net.minecraft.world.item.Items.SLIME_BALL;default->net.minecraft.world.item.Items.BOOK;};
            g.renderItem(item.getDefaultInstance(),x-8,y-14);
            g.drawCenteredString(font,label(slot),x,y+4,enabled(slot)?BlacklightUi.TEXT:BlacklightUi.MUTED);
        }
        g.drawCenteredString(font,title,cx,cy-(int)outer-24,0xffff899a);
        var center=label(selected);
        var lines=font.split(center,Math.max(30,(int)(inner*1.55)));
        int centerY=cy-lines.size()*5;
        for(var line:lines){g.drawCenteredString(font,line,cx,centerY,BlacklightUi.TEXT);centerY+=10;}
        String hint=BiomassSystem.state(minecraft.player).fatal()?"screen.blacklight.morph_fatal":!enabled(selected)?"screen.blacklight.morph_unavailable":"screen.blacklight.morph_hint";
        BlacklightUi.wheelHint(g,font,Component.translatable(hint,binding.getTranslatedKeyMessage()),cx,cy+(int)outer+10,Math.min(340,width-30));
        super.render(g,mx,my,partial);
    }
    private static void vertex(BufferBuilder b,org.joml.Matrix4f m,float x,float y,int color){b.addVertex(m,x,y,0).setColor(color);}
    private void confirm(){
        if(submitted)return;
        submitted=true;
        if(enabled(selected)){
            if(selected==2){minecraft.setScreen(new DisguiseLibraryScreen(null));return;}
            if(selected==0||BiomassSystem.active(minecraft.player))PacketDistributor.sendToServer(new DisguiseTogglePayload());
            else PacketDistributor.sendToServer(new PowerSelectPayload(PowerForm.BIOMASS.ordinal(),false));
        }
        onClose();
    }
    @Override public void onClose(){submitted=true;super.onClose();}
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){if(vertical==0)return false;selected=Math.floorMod(selected+(vertical>0?-1:1),3);return true;}
    @Override public boolean keyReleased(int key,int scan,int mods){if(binding.matches(key,scan)){confirm();return true;}return super.keyReleased(key,scan,mods);}
    @Override public boolean mouseReleased(double x,double y,int button){if(binding.matchesMouse(button)){confirm();return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==1){onClose();return true;}return super.mouseClicked(x,y,button);}
}
