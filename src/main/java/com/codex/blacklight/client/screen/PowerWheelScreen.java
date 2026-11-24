package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.BlacklightUi;

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
import net.neoforged.neoforge.network.PacketDistributor;

/** Hold the bound key, point at an unlocked sector, release to select. */
public final class PowerWheelScreen extends Screen {
    private final KeyMapping binding;
    private int selected=-1;
    private boolean submitted,utility,scrollSelection;
    private PowerForm heldForm;
    private int heldTicks;
    public PowerWheelScreen(KeyMapping binding){super(Component.translatable("screen.blacklight.wheel"));this.binding=binding;com.codex.blacklight.client.ClientWeaponCombat.beginWeaponWheel();}
    @Override protected void init() {
        int buttonWidth=Math.min(140,(width-28)/2);
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.blacklight.wheel.weapons"), b->{utility=false;selected=-1;})
                .bounds(width/2-buttonWidth-4,8,buttonWidth,20).build(TutorialButton::new));
        addRenderableWidget(net.minecraft.client.gui.components.Button.builder(Component.translatable("screen.blacklight.wheel.utilities"), b->{utility=true;selected=-1;})
                .bounds(width/2+4,8,buttonWidth,20).build(TutorialButton::new));
    }
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial) { }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){
        if(minecraft.player==null||!minecraft.player.isAlive()||!minecraft.player.getData(ModAttachments.PLAYER_STATE).isPrototypeMode()||minecraft.player.getData(ModAttachments.CONSUMING)){onClose();return;}
        var form=selected<0?null:PowerWheel.form(utility,selected);
        if(form!=heldForm){heldForm=form;heldTicks=0;}else if(form!=null)heldTicks++;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        if(minecraft.player==null)return;
        var state=minecraft.player.getData(ModAttachments.PLAYER_STATE);
        int cx=width/2,cy=height/2;
        float outer=Math.min(125,Math.min(width*.34F,height*.32F)),inner=outer*.38F;
        if(!scrollSelection)selected=PowerWheel.sector(mouseX-cx,mouseY-cy,inner,outer+12,utility);
        g.fill(0,0,width,height,0x6506090d);g.flush();
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.disableCull();RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var mesh=Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES,DefaultVertexFormat.POSITION_COLOR);
        var matrix=g.pose().last().pose();
        for(int slot=0;slot<PowerWheel.size(utility);slot++) {
            var form=PowerWheel.form(utility,slot);boolean owned=form==PowerForm.NONE||state.hasUnlocked(form);
            boolean available=owned;
            int color=slot==selected?0xEF71383D:available?0xE227282D:0xDA1D1D23;
            int edge=slot==selected?BlacklightUi.RED:available?0xEB725357:0xCC414148;

            double middle=-Math.PI/2+slot*Math.PI*2/PowerWheel.size(utility),span=Math.PI/PowerWheel.size(utility)-.035;
            for(int j=0;j<18;j++) {
                double a=middle-span+2*span*j/18,b=middle-span+2*span*(j+1)/18;
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
        for(int slot=0;slot<PowerWheel.size(utility);slot++) {
            var form=PowerWheel.form(utility,slot);boolean owned=form==PowerForm.NONE||state.hasUnlocked(form);
            double angle=-Math.PI/2+slot*Math.PI*2/PowerWheel.size(utility);
            int x=cx+(int)(Math.cos(angle)*outer*.70),y=cy+(int)(Math.sin(angle)*outer*.70);
            int size=outer>=90?24:16;
            BlacklightUi.emblem(g,form,x-size/2,y-size/2-4,size);
            var name=Component.translatable("power.blacklight."+form.id());
            int limit=Math.max(30,(int)(outer*.72));
            g.drawCenteredString(font,BlacklightUi.fit(font,name.getString(),limit),x,y+size/2-4,owned?BlacklightUi.TEXT:BlacklightUi.MUTED);
            if(form==state.getActivePower()||UtilityPowerSystem.active(minecraft.player,form))
                g.fill(x-5,y+size/2+7,x+5,y+size/2+8,BlacklightUi.RED);
        }
        var current=selected<0?null:PowerWheel.form(utility,selected);
        int tab=Math.min(140,(width-28)/2),left=utility?cx+4:cx-tab-4;
        g.fill(left,27,left+tab,29,BlacklightUi.RED);
        if(current==null){
            g.drawCenteredString(font,Component.translatable("screen.blacklight.wheel.cancel"),cx,cy-4,BlacklightUi.MUTED);
        }else{
            var name=Component.translatable("power.blacklight."+current.id());
            var lines=font.split(name,Math.max(30,(int)(inner*1.65)));
            int y=cy-lines.size()*5;
            for(var line:lines){g.drawCenteredString(font,line,cx,y,BlacklightUi.TEXT);y+=10;}
            if(current!=PowerForm.NONE&&!state.hasUnlocked(current))
                g.drawCenteredString(font,current==PowerForm.GAZE_VACUOLE ? Component.translatable("message.blacklight.gaze_source") : Component.literal(current.epCost()+" EP"),cx,y+1,BlacklightUi.GOLD);
        }
        Component hint=Component.translatable("screen.blacklight.wheel.hint",binding.getTranslatedKeyMessage());
        if(current!=null&&!PowerMorph.key(current).isEmpty())
            hint=Component.translatable(heldTicks>=10?"screen.blacklight.wheel.morph_ready":"screen.blacklight.wheel.morph_hold");
        int lineY=cy+(int)outer+8;
        BlacklightUi.wheelHint(g,font,hint,cx,lineY,Math.min(340,width-24));
        super.render(g,mouseX,mouseY,partial);
    }
    private static void vertex(BufferBuilder b,org.joml.Matrix4f m,float x,float y,int color){b.addVertex(m,x,y,0).setColor(color);}
    private void confirm(){
        if(!submitted&&selected>=0&&minecraft.player!=null){
            var form=PowerWheel.form(utility,selected);var state=minecraft.player.getData(ModAttachments.PLAYER_STATE);
            if(form==PowerForm.NONE||state.hasUnlocked(form)){submitted=true;com.codex.blacklight.client.ClientWeaponCombat.finishWeaponWheel(form);PacketDistributor.sendToServer(new PowerSelectPayload(form.ordinal(),form==heldForm&&heldTicks>=10));}
        }
        onClose();
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(vertical==0)return false;
        scrollSelection=true;selected=Math.floorMod(selected+(vertical>0?-1:1),PowerWheel.size(utility));return true;
    }
    @Override public void mouseMoved(double x,double y){scrollSelection=false;super.mouseMoved(x,y);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==org.lwjgl.glfw.GLFW.GLFW_KEY_TAB){utility=!utility;selected=-1;return true;}return super.keyPressed(key,scan,mods);}
    @Override public boolean keyReleased(int key,int scan,int mods){if(binding.matches(key,scan)){confirm();return true;}return super.keyReleased(key,scan,mods);}
    @Override public boolean mouseReleased(double x,double y,int button){if(binding.matchesMouse(button)){confirm();return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseClicked(double x,double y,int button){if(button==0){if(y>=8&&y<28)return super.mouseClicked(x,y,button);confirm();return true;}if(button==1){onClose();return true;}return super.mouseClicked(x,y,button);}
}
