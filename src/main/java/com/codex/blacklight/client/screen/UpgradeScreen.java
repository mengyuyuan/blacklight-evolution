package com.codex.blacklight.client.screen;

import com.codex.blacklight.client.SkillBindings;
import com.codex.blacklight.network.*;
import com.codex.blacklight.power.*;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import com.codex.blacklight.forge.PacketDistributor;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

/** Category / skill / demonstration. Selecting and learning are separate actions. */
public final class UpgradeScreen extends Screen {
    private static final int BG=0xFF101114, PANEL=0xFF1A1C20, RED=0xFFE94B42, TEXT=0xFFE9E5DF, DIM=0xFFABA9A7;
    private static final int TOP=48,ROW=31;
    private enum Category { BASIC, MOVEMENT, CLAW, FORMS, COMBAT, UTILITY, BIOLOGY, ATTRIBUTES, SETTINGS;
        Component label(){return Component.translatable("screen.blacklight.section."+name().toLowerCase(java.util.Locale.ROOT));}}
    private record Entry(String id,int cost,String required,boolean free) {}
    private Category category=Category.BASIC;
    private final List<Entry> entries=new ArrayList<>();
    private String selected="mode";
    private int rail,listX,listWidth,detailX,detailWidth,bottom,listScroll,detailScroll,detailHeight;
    private Button purchase;
    private final TutorialGif gif=new TutorialGif();
    private long selectedAt=System.nanoTime(),pausedAt;
    private boolean paused;

    public UpgradeScreen(){super(Component.translatable("screen.blacklight.upgrades"));}
    @Override protected void init(){
        rail=com.codex.blacklight.util.MathCompat.clamp(width/6,62,108);listX=rail+7;
        listWidth=com.codex.blacklight.util.MathCompat.clamp((width-rail)/3,85,170);detailX=listX+listWidth+10;
        detailWidth=width-detailX-12;bottom=height-31;
        clearWidgets();
        addRenderableWidget(new TutorialButton(width-114,8,78,20,Component.translatable("tutorial.blacklight.configure"),b->minecraft.setScreen(new KeyBindsScreen(this,minecraft.options))));
        addRenderableWidget(new TutorialButton(width-31,8,22,20,Component.literal("×"),b->onClose()));
        purchase=addRenderableWidget(new TutorialButton(detailX,height-25,Math.max(60,detailWidth),20,Component.empty(),b->activate()));
        rebuild();
        if(!selected.isEmpty()&&category!=Category.SETTINGS&&category!=Category.ATTRIBUTES)gif.select(SkillTutorial.forSkill(selected).family());
    }
    private void rebuild(){
        entries.clear();
        switch(category){
            case BASIC -> {for(String id:List.of("mode","grab","target_lock","disguise"))entries.add(new Entry(id,0,null,true));}
            case MOVEMENT -> {for(var u:MovementUpgrade.values())entries.add(new Entry(u.id(),u.epCost(),u.requires()==null?null:u.requires().id(),false));}
            case CLAW -> {entries.add(new Entry("groundspike",0,null,true));for(var u:ClawUpgrade.values())entries.add(new Entry(u.id(),u.epCost(),null,false));}
            case FORMS -> {for(var u:List.of(PowerForm.CLAWS,PowerForm.HAMMERFISTS,PowerForm.BLADE,PowerForm.WHIPFIST,PowerForm.MUSCLE_MASS))entries.add(new Entry(u.id(),u.epCost(),null,u==PowerForm.CLAWS));}
            case COMBAT -> {for(var u:CombatSkill.values())if(!u.shieldBorer()&&!u.adaptivePassive())entries.add(new Entry(u.id(),u.epCost(),u.requires()!=null?u.requires().id():u.movementRequires()!=null?u.movementRequires().id():u.powerRequires()!=null?u.powerRequires().id():null,false));}
            case UTILITY -> {for(var u:List.of(PowerForm.SHIELD,PowerForm.ARMOR,PowerForm.THERMAL_VISION,PowerForm.INFECTED_VISION,PowerForm.BIOMASS))entries.add(new Entry(u.id(),u.epCost(),null,false));for(var u:CombatSkill.values())if(u.shieldBorer())entries.add(new Entry(u.id(),u.epCost(),u.requires()==null?PowerForm.SHIELD.id():u.requires().id(),false));}
            case BIOLOGY -> {for(var u:CombatSkill.values())if(u.adaptivePassive())entries.add(new Entry(u.id(),u.epCost(),u.requires()==null?null:u.requires().id(),false));for(var u:GrowthUpgrade.values())entries.add(new Entry(u.id,u.cost,u.requires()==null?null:u.requires().id,false));}
            case SETTINGS -> {for(String id:List.of("bulletdive_blocks","shield_borer_blocks"))entries.add(new Entry(id,0,null,true));}
            default -> {}
        }
        if(entries.stream().noneMatch(e->e.id.equals(selected)))select(entries.isEmpty()?"":entries.get(0).id);
        listScroll=com.codex.blacklight.util.MathCompat.clamp(listScroll,0,maxListScroll());purchase.visible=category!=Category.ATTRIBUTES;refreshButton();
    }
    private void select(String id){
        selected=id;detailScroll=0;selectedAt=System.nanoTime();paused=false;
        if(!id.isEmpty()&&category!=Category.SETTINGS)gif.select(SkillTutorial.forSkill(id).family());else gif.close();
    }
    private Entry entry(){return entries.stream().filter(e->e.id.equals(selected)).findFirst().orElse(null);}
    private Component label(String id){return Component.translatable((category==Category.SETTINGS?"setting.blacklight.":"upgrade.blacklight.")+id);}
    private static BlacklightState state(){var p=Minecraft.getInstance().player;return p==null?null:com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.PLAYER_STATE);}
    private boolean owns(Entry e){return e.free||owned(state(),e.id);}
    private boolean prerequisite(Entry e){return e.required==null||owned(state(),e.required);}
    private int balance(){var s=state();return s==null?0:s.getEvolutionPoints();}
    private void activate(){
        var e=entry();if(e==null||minecraft.player==null)return;
        if(category==Category.SETTINGS){
            var setting=e.id.equals("shield_borer_blocks")?SkillSettingsPayload.Setting.SHIELD_BORER_BLOCKS:SkillSettingsPayload.Setting.BULLETDIVE_BLOCKS;
            PacketDistributor.sendToServer(new SkillSettingsPayload(setting,!settingEnabled(e.id)));return;
        }
        if(!owns(e)&&prerequisite(e)&&balance()>=e.cost)PacketDistributor.sendToServer(new UpgradePurchasePayload(e.id));
    }
    private boolean settingEnabled(String id){
        if(minecraft.player==null)return false;
        var settings=com.codex.blacklight.registry.ModAttachments.getData(minecraft.player, ModAttachments.SKILL_SETTINGS);
        return id.equals("shield_borer_blocks")?settings.shieldBorerBlockBreaking():settings.bulletdiveBlockBreaking();
    }
    private void refreshButton(){
        var e=entry();if(e==null)return;
        if(category==Category.SETTINGS){
            boolean on=settingEnabled(e.id);
            purchase.setMessage(Component.translatable(on?"screen.blacklight.setting_on":"screen.blacklight.setting_off"));purchase.active=minecraft.player!=null;return;
        }
        purchase.active=minecraft.player!=null&&!owns(e)&&prerequisite(e)&&balance()>=e.cost;
        purchase.setMessage(Component.translatable(owns(e)?"screen.blacklight.state_owned":!prerequisite(e)?"screen.blacklight.requires_short":"screen.blacklight.state_cost",e.cost));
    }
    @Override public void renderBackground(GuiGraphics g){}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,BG);g.fill(0,0,rail,height,0xFF0B0C0E);g.fill(0,37,width,38,0xFF74302D);
        g.fill(10,10,13,27,RED);g.drawString(font,title,20,13,TEXT,false);
        var ep=Component.translatable("screen.blacklight.ep_balance",balance());
        if(width>460)g.drawString(font,ep,width-126-font.width(ep),14,0xFFE6C68A,false);
        int step=Math.max(14,Math.min(27,(height-54)/Category.values().length)),y=TOP;
        for(var c:Category.values()){
            if(c==category){g.fill(4,y,rail-3,y+step-2,0xFF4A2020);g.fill(4,y,6,y+step-2,RED);}
            g.drawString(font,font.plainSubstrByWidth(c.label().getString(),rail-15),11,y+(step-10)/2,c==category?TEXT:DIM,false);y+=step;
        }
        if(category==Category.ATTRIBUTES)renderAttributes(g);
        else {
            g.enableScissor(listX,TOP,listX+listWidth,bottom);y=TOP-listScroll;
            for(var e:entries){
                boolean chosen=e.id.equals(selected),hover=mx>=listX&&mx<listX+listWidth&&my>=y&&my<y+ROW-3;
                g.fill(listX,y,listX+listWidth,y+ROW-3,chosen?0xFF682C29:hover?0xFF302627:PANEL);
                if(chosen)g.fill(listX,y,listX+2,y+ROW-3,RED);
                g.drawString(font,font.plainSubstrByWidth(label(e.id).getString(),listWidth-12),listX+7,y+5,TEXT,false);
                var status=Component.translatable(category==Category.SETTINGS?(settingEnabled(e.id)?"screen.blacklight.setting_on":"screen.blacklight.setting_off"):owns(e)?"tutorial.blacklight.learned":prerequisite(e)?"tutorial.blacklight.available":"tutorial.blacklight.locked");
                g.drawString(font,status,listX+7,y+17,owns(e)?0xFFB1BEAE:0xFFD6A089,false);y+=ROW;
            }
            g.disableScissor();bar(g,listX+listWidth-2,TOP,bottom,listScroll,entries.size()*ROW);
            renderDetail(g,mx,my);refreshButton();
            if(width<=460)g.drawString(font,ep,listX,height-19,0xFFE6C68A,false);
        }
        super.render(g,mx,my,partial);
    }
    private void renderDetail(GuiGraphics g,int mx,int my){
        var e=entry();if(e==null)return;
        g.fill(detailX-3,TOP-4,width-8,bottom,0xFF16181B);g.enableScissor(detailX,TOP,width-10,bottom);
        int y=paragraph(g,label(e.id),TOP-detailScroll,TEXT)+6;
        if(category!=Category.SETTINGS){
            var tutorial=SkillTutorial.forSkill(e.id);int previewHeight=previewHeight();
            g.fill(detailX,y,detailX+detailWidth,y+previewHeight,0xFF22262B);
            long elapsed=((paused?pausedAt:System.nanoTime())-selectedAt)/1_000_000;
            if(!gif.render(g,detailX+(detailWidth-previewHeight*16/9)/2,y,previewHeight*16/9,previewHeight,elapsed))
                g.drawCenteredString(font,Component.translatable(gif.failed?"tutorial.blacklight.preview_missing":"tutorial.blacklight.loading"),detailX+detailWidth/2,y+previewHeight/2,DIM);
            y+=previewHeight+4;y=paragraph(g,Component.translatable(paused?"tutorial.blacklight.paused":List.of("vision","critical","health","adrenaline").contains(tutorial.family())?"tutorial.blacklight.diagram":"tutorial.blacklight.playing"),y,DIM)+8;
            y=paragraph(g,Component.translatable("tutorial.blacklight.controls"),y,RED)+4;
            y=paragraph(g,tutorial.controls(),y,TEXT)+6;y=paragraph(g,tutorial.steps(),y,DIM)+10;
            for(var key:tutorial.keyLabels())y=paragraph(g,key,y,0xFFD6C5AE)+3;y+=5;
        }
        y=paragraph(g,Component.translatable("tutorial.blacklight.effect"),y,RED)+4;
        y=paragraph(g,Component.translatable((category==Category.SETTINGS?"setting.blacklight.":"upgrade.blacklight.")+e.id+".desc"),y,DIM)+8;
        if(e.required!=null)y=paragraph(g,Component.translatable("screen.blacklight.state_requires",Component.translatable("upgrade.blacklight."+e.required)),y,0xFFD6A089)+8;
        detailHeight=y-(TOP-detailScroll);g.disableScissor();detailScroll=com.codex.blacklight.util.MathCompat.clamp(detailScroll,0,maxDetailScroll());bar(g,width-10,TOP,bottom,detailScroll,detailHeight);
    }
    private int previewHeight(){return Math.min(detailWidth*9/16,Math.max(48,Math.min(120,(bottom-TOP)*46/100)));}
    private int paragraph(GuiGraphics g,Component text,int y,int color){
        for(var line:font.split(text,Math.max(50,detailWidth-6))){g.drawString(font,line,detailX,y,color,false);y+=11;}return y;
    }
    private void renderAttributes(GuiGraphics g){
        g.enableScissor(listX,TOP,width-12,bottom);int y=TOP-listScroll;
        for(var a:PlayerAttributesView.entries()){
            g.drawString(font,a.label(),listX+5,y,DIM,false);y+=13;
            for(var line:font.split(a.value(),width-listX-20)){g.drawString(font,line,listX+5,y,TEXT,false);y+=11;}y+=12;
        }
        detailHeight=y-(TOP-listScroll);g.disableScissor();bar(g,width-12,TOP,bottom,listScroll,detailHeight);
    }
    private void bar(GuiGraphics g,int x,int top,int end,int offset,int total){
        int view=end-top;if(total<=view)return;int size=Math.max(12,view*view/total),y=top+(view-size)*offset/Math.max(1,total-view);
        g.fill(x,top,x+2,end,0xFF303034);g.fill(x,y,x+2,y+size,RED);
    }
    private int maxListScroll(){return Math.max(0,(category==Category.ATTRIBUTES?detailHeight:entries.size()*ROW)-(bottom-TOP));}
    private int maxDetailScroll(){return Math.max(0,detailHeight-(bottom-TOP));}
    @Override public boolean mouseClicked(double x,double y,int button){
        var binding=SkillBindings.find("key.blacklight.upgrades");
        if(binding!=null&&SkillBindings.mouse(binding,button)){onClose();return true;}
        if(button==0){
            int step=Math.max(14,Math.min(27,(height-54)/Category.values().length));
            if(x>=4&&x<rail&&y>=TOP&&y<TOP+step*Category.values().length){category=Category.values()[(int)(y-TOP)/step];listScroll=detailScroll=0;rebuild();return true;}
            if(x>=listX&&x<listX+listWidth&&y>=TOP&&y<bottom){int index=((int)y-TOP+listScroll)/ROW;if(index<entries.size()){select(entries.get(index).id);return true;}}
            if(category!=Category.ATTRIBUTES&&category!=Category.SETTINGS&&x>=detailX&&x<detailX+detailWidth&&y>=TOP&&y<bottom){
                int start=TOP-detailScroll+font.split(label(selected),Math.max(50,detailWidth-6)).size()*11+6;
                if(y>=start&&y<start+previewHeight()+15){if(paused){selectedAt+=System.nanoTime()-pausedAt;paused=false;}else{pausedAt=System.nanoTime();paused=true;}return true;}
            }
        }
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double vertical){
        if(y<TOP||y>bottom)return false;
        if(x>=detailX&&category!=Category.ATTRIBUTES)detailScroll=com.codex.blacklight.util.MathCompat.clamp(detailScroll-(int)(vertical*26),0,maxDetailScroll());
        else if(x>=listX)listScroll=com.codex.blacklight.util.MathCompat.clamp(listScroll-(int)(vertical*26),0,maxListScroll());else return false;return true;
    }
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(SkillBindings.matches("key.blacklight.upgrades",key,scan)){onClose();return true;}
        if(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP){detailScroll=com.codex.blacklight.util.MathCompat.clamp(detailScroll+(key==GLFW.GLFW_KEY_PAGE_DOWN?1:-1)*(bottom-TOP-15),0,maxDetailScroll());return true;}
        if((key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP)&&!entries.isEmpty()){
            int i=entries.indexOf(entry()),next=com.codex.blacklight.util.MathCompat.clamp(i+(key==GLFW.GLFW_KEY_DOWN?1:-1),0,entries.size()-1);select(entries.get(next).id);listScroll=com.codex.blacklight.util.MathCompat.clamp(next*ROW-(bottom-TOP)/2,0,maxListScroll());return true;
        }
        return super.keyPressed(key,scan,mods);
    }
    @Override public void removed(){gif.close();}
    @Override public boolean isPauseScreen(){return false;}
    private static boolean owned(BlacklightState state,String id){
        if(state==null)return false;
        var c=CombatSkill.byId(id);if(c!=null)return state.hasCombatSkill(c);
        var g=GrowthUpgrade.byId(id);if(g!=null){var p=Minecraft.getInstance().player;return p!=null&&com.codex.blacklight.registry.ModAttachments.getData(p, ModAttachments.GROWTH).owns(g);}
        var f=PowerForm.byId(id);if(f!=PowerForm.NONE)return state.hasUnlocked(f);
        var claw=ClawUpgrade.byId(id);if(claw!=null)return state.hasClawUpgrade(claw);
        var movement=MovementUpgrade.byId(id);return movement!=null&&state.hasMovementUpgrade(movement);
    }
}
