package com.codex.blacklight.client.screen;

import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.network.PowerSelectPayload;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import com.codex.blacklight.forge.PacketDistributor;

/** Hold the bound key, point at an unlocked sector, release to select. */
public final class PowerWheelScreen extends Screen {
    private final KeyMapping binding;
    private int selected=-1;
    private boolean submitted,utility,scrollSelection;
    private PowerForm heldForm;
    private int heldTicks;
    public PowerWheelScreen(KeyMapping binding){super(Component.translatable("screen.blacklight.wheel"));this.binding=binding;}
    @Override protected void init() {
        int buttonWidth=Math.min(140,(width-28)/2);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.blacklight.wheel.weapons"), b->{utility=false;selected=-1;})
                .bounds(width/2-buttonWidth-4,8,buttonWidth,20).build());
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.blacklight.wheel.utilities"), b->{utility=true;selected=-1;})
                .bounds(width/2+4,8,buttonWidth,20).build());
    }
    @Override public void renderBackground(GuiGraphics g) { }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        if(minecraft.player==null||!minecraft.player.isAlive()||!com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.PLAYER_STATE).isPrototypeMode()||com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.CONSUMING)){onClose();return;}
        var form=selected<0?null:PowerWheel.form(utility,selected);
        if(form!=heldForm){heldForm=form;heldTicks=0;}else if(form!=null)heldTicks++;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        if(minecraft.player==null)return;
        var state=com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.PLAYER_STATE);
        int cx=width/2,cy=height/2;
        float outer=Math.min(125,Math.min(width*.34F,height*.36F)),inner=outer*.39F;
        if(!scrollSelection)selected=PowerWheel.sector(mouseX-cx,mouseY-cy,inner,outer+12,utility);
        g.fill(0,0,width,height,0x6506090d);g.flush();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var mesh=Tesselator.getInstance().getBuilder();mesh.begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        var matrix=g.pose().last().pose();
        for(int slot=0;slot<PowerWheel.size(utility);slot++) {
            var form=PowerWheel.form(utility,slot);boolean owned=form==PowerForm.NONE||state.hasUnlocked(form);
            int color=!owned?0xCE191D24:slot==selected?0xEF932D39:0xDC252B33;
            double middle=-Math.PI/2+slot*Math.PI*2/PowerWheel.size(utility),span=Math.PI/PowerWheel.size(utility)-.035;
            for(int j=0;j<18;j++) {
                double a=middle-span+2*span*j/18,b=middle-span+2*span*(j+1)/18;
                float ax=(float)Math.cos(a),ay=(float)Math.sin(a),bx=(float)Math.cos(b),by=(float)Math.sin(b);
                vertex(mesh,matrix,cx+ax*inner,cy+ay*inner,color);vertex(mesh,matrix,cx+ax*outer,cy+ay*outer,color);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,color);
                vertex(mesh,matrix,cx+ax*inner,cy+ay*inner,color);vertex(mesh,matrix,cx+bx*outer,cy+by*outer,color);vertex(mesh,matrix,cx+bx*inner,cy+by*inner,color);
            }
        }
        BufferUploader.drawWithShader(mesh.end());RenderSystem.enableCull();RenderSystem.disableBlend();
        for(int slot=0;slot<PowerWheel.size(utility);slot++) {
            var form=PowerWheel.form(utility,slot);boolean owned=form==PowerForm.NONE||state.hasUnlocked(form);
            double angle=-Math.PI/2+slot*Math.PI*2/PowerWheel.size(utility);
            int x=cx+(int)(Math.cos(angle)*outer*.70),y=cy+(int)(Math.sin(angle)*outer*.70);
            g.drawCenteredString(font,Component.translatable("power.blacklight."+form.id()),x,y-8,owned?0xFFF0F0F0:0xFF737A85);
            if(!owned)g.drawCenteredString(font,Component.translatable("screen.blacklight.wheel.locked",form.epCost()),x,y+4,0xFFAA747A);
            else if(form==state.getActivePower()||UtilityPowerSystem.active(minecraft.player,form))g.drawCenteredString(font,Component.translatable("screen.blacklight.wheel.active"),x,y+4,0xFFFF9F83);
        }
        g.drawCenteredString(font,selected<0?Component.translatable("screen.blacklight.wheel.cancel"):Component.translatable("power.blacklight."+PowerWheel.form(utility,selected).id()),cx,cy-4,0xFFFFFFFF);
        g.drawCenteredString(font,title,cx,Math.max(32,cy-(int)outer-28),0xFFFF8790);
        g.drawCenteredString(font,Component.translatable(utility?"screen.blacklight.wheel.utility_page":"screen.blacklight.wheel.weapon_page"),cx,Math.max(44,cy-(int)outer-14),0xFFCCD1D8);
        Component hint=selected<0?Component.translatable("screen.blacklight.wheel.hint",binding.getTranslatedKeyMessage()):Component.translatable("screen.blacklight.wheel."+PowerWheel.form(utility,selected).id());
        int lineY=cy+(int)outer+12;
        for(var line:font.split(hint,width-24)){g.drawCenteredString(font,line,cx,lineY,0xFFCCD1D8);lineY+=11;}
        if(selected>=0&&!com.codex.blacklight.power.PowerMorph.key(PowerWheel.form(utility,selected)).isEmpty())
            g.drawCenteredString(font,Component.translatable(heldTicks>=10?"screen.blacklight.wheel.morph_ready":"screen.blacklight.wheel.morph_hold"),cx,lineY+2,0xFFFFB090);
        super.render(g,mouseX,mouseY,partial);
    }
    private static void vertex(BufferBuilder b,org.joml.Matrix4f m,float x,float y,int color){b.vertex(m,x,y,0).color(color).endVertex();}
    private void confirm(){
        if(!submitted&&selected>=0&&minecraft.player!=null){
            var form=PowerWheel.form(utility,selected);var state=com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.PLAYER_STATE);
            if(form==PowerForm.NONE||state.hasUnlocked(form)){submitted=true;PacketDistributor.sendToServer(new PowerSelectPayload(form.ordinal(),form==heldForm&&heldTicks>=10));}
        }
        onClose();
    }
    @Override public boolean mouseScrolled(double x,double y,double vertical){if(vertical==0)return false;scrollSelection=true;selected=Math.floorMod(selected+(vertical>0?-1:1),PowerWheel.size(utility));return true;}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_TAB){utility=!utility;selected=-1;return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){if(binding.matches(key,scan)){confirm();return true;}return super.keyReleased(key,scan,mods);}
    @Override public boolean mouseReleased(double x,double y,int button){if(binding.matchesMouse(button)){confirm();return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){if(y>=8&&y<28)return super.mouseClicked(x,y,button);confirm();return true;}if(button==1){onClose();return true;}return super.mouseClicked(x,y,button);}
}
