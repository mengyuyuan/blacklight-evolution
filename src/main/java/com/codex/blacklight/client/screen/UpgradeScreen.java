package com.codex.blacklight.client.screen;

import com.codex.blacklight.network.UpgradePurchasePayload;
import com.codex.blacklight.power.ClawUpgrade;
import com.codex.blacklight.power.CombatSkill;
import com.codex.blacklight.power.MovementUpgrade;
import com.codex.blacklight.registry.ModAttachments;
import com.codex.blacklight.state.BlacklightState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The upgrade menu in the original game's shape: a full-screen dark command-console
 * panel with a left category rail, the upgrade list of the selected category in the
 * center and the EP balance up top. Purchases are server-authoritative.
 */
public final class UpgradeScreen extends Screen {
    private static final int BG = 0xFF10131A;
    private static final int RAIL_BG = 0xFF0D0F16;
    private static final int RED = 0xFFE23A3A;
    private static final int RED_SOFT = 0xFF7A2026;
    private static final int TEXT = 0xFFE4E8EF;
    private static final int TEXT_DIM = 0xFFBDC6D4;
    private static final int ROW_HOVER = 0x2EE23A3A;

    private enum Category {
        ATTRIBUTES("screen.blacklight.section.attributes"),
        MOVEMENT("screen.blacklight.section.movement"),
        CLAW("screen.blacklight.section.claw"),
        FORMS("screen.blacklight.section.forms"),
        BIOLOGY("screen.blacklight.section.biology"),
        UTILITY("screen.blacklight.section.utility"),
        COMBAT("screen.blacklight.section.combat");

        private final String langKey;
        Category(String langKey) { this.langKey = langKey; }
    }

    private Category category = Category.ATTRIBUTES;
    private final List<Row> rows = new ArrayList<>();
    private int railWidth;
    private int contentX;
    private int contentWidth;
    private int scrollOffset,totalHeight,listBottom,buttonWidth,textWidth;
    private boolean stacked;
    private static final int LIST_TOP=58;

    public UpgradeScreen() {
        super(Component.translatable("screen.blacklight.upgrades"));
    }

    @Override
    protected void init() {
        railWidth = Math.clamp(width / 5,80,124);
        contentX = railWidth + 14;
        contentWidth = width - contentX - 14;
        listBottom=Math.max(LIST_TOP+35,height-23);
        buttonWidth=Math.min(108,contentWidth-16);
        stacked=contentWidth<285;
        textWidth=Math.max(40,contentWidth-20-(stacked?0:buttonWidth+12));
        rebuildRows();
    }

    private void rebuildRows() {
        rows.clear();
        clearWidgets();
        int y = 0;
        if(category==Category.ATTRIBUTES){
            y=attributeHeight();
        } else if(category==Category.BIOLOGY){
            for(var growth:com.codex.blacklight.power.GrowthUpgrade.values())y=addRow(y,growth.id,growth.cost,growth.requires()==null?null:growth.requires().id);
        } else if(category==Category.UTILITY){
            for(var form:new com.codex.blacklight.power.PowerForm[]{com.codex.blacklight.power.PowerForm.SHIELD,com.codex.blacklight.power.PowerForm.ARMOR,com.codex.blacklight.power.PowerForm.THERMAL_VISION,com.codex.blacklight.power.PowerForm.INFECTED_VISION})y=addRow(y,form.id(),form.epCost(),null);
        } else if (category == Category.COMBAT) {
            for (var skill : CombatSkill.values()) y = addRow(y, skill.id(), skill.epCost(),
                    skill.requires() != null ? skill.requires().id()
                            : skill.movementRequires() == null ? null : skill.movementRequires().id());
        } else if (category == Category.MOVEMENT) {
            for (MovementUpgrade upgrade : MovementUpgrade.values()) {
                y = addRow(y, upgrade.id(), upgrade.epCost(),
                        upgrade.requires() == null ? null : upgrade.requires().id());
            }
        } else if (category == Category.FORMS) {
            for (var form : new com.codex.blacklight.power.PowerForm[]{com.codex.blacklight.power.PowerForm.HAMMERFISTS, com.codex.blacklight.power.PowerForm.BLADE, com.codex.blacklight.power.PowerForm.WHIPFIST,com.codex.blacklight.power.PowerForm.MUSCLE_MASS}) {
                y = addRow(y, form.id(), form.epCost(), null);
            }
        } else {
            for (ClawUpgrade upgrade : ClawUpgrade.values()) {
                y = addRow(y, upgrade.id(), upgrade.epCost(), null);
            }
        }
        totalHeight=y;
        scrollOffset=Math.clamp(scrollOffset,0,maxScroll());
        layoutRows();
    }

    private int addRow(int y, String id, int cost, String requiresId) {
        var titleLines=font.split(Component.translatable("upgrade.blacklight."+id),textWidth);
        var description=font.split(Component.translatable("upgrade.blacklight."+id+".desc"),textWidth);
        int textHeight=titleLines.size()*11+4+description.size()*11;
        int rowHeight=Math.max(44,textHeight+16+(stacked?26:0));
        int buttonOffset=stacked?rowHeight-27:Math.max(8,(rowHeight-20)/2);
        Button button = Button.builder(Component.empty(), clicked -> buy(id))
                .bounds(contentX+contentWidth-buttonWidth-8,0,buttonWidth,20).build();
        button.setTooltip(Tooltip.create(Component.translatable("upgrade.blacklight."+id+".desc")));
        addRenderableWidget(button);
        rows.add(new Row(id,cost,requiresId,button,y,rowHeight,buttonOffset,titleLines,description));
        return y+rowHeight+6;
    }

    private int maxScroll(){return Math.max(0,totalHeight-(listBottom-LIST_TOP));}
    private void layoutRows(){
        for(var row:rows){
            int y=LIST_TOP+row.y-scrollOffset;
            row.button.setY(y+row.buttonOffset);
            row.button.visible=row.button.getY()>=LIST_TOP&&row.button.getBottom()<=listBottom;
        }
    }

    @Override public void renderBackground(GuiGraphics graphics,int mouseX,int mouseY,float partialTick){
        // Screen.render invokes this before its widgets. The opaque panel below owns
        // its background; invoking vanilla blur here would blur our already drawn text.
    }

    private void buy(String id) {
        PacketDistributor.sendToServer(new UpgradePurchasePayload(id));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, BG);
        graphics.fill(0, 0, railWidth, height, RAIL_BG);
        graphics.fill(railWidth, 0, railWidth + 1, height, RED_SOFT);
        graphics.fill(0, 44, width, 45, RED_SOFT);

        graphics.drawString(font,title,12,9,TEXT,false);
        BlacklightState state = currentState();
        Component ep = Component.translatable(category==Category.BIOLOGY?"screen.blacklight.biomass_balance":"screen.blacklight.ep_balance",
                state == null ? 0 : category==Category.BIOLOGY?state.getBiomass():state.getEvolutionPoints());
        graphics.drawString(font,ep,width-12-font.width(ep),font.width(title)+font.width(ep)+36>width?27:9,0xFFFFD796,false);
        graphics.drawString(font,Component.translatable("screen.blacklight.upgrades_hint"),12,29,TEXT_DIM,false);

        int railY = 64;
        for (Category entry : Category.values()) {
            boolean selected = entry == category;
            int railHeight = Math.min(22, railStep() - 2);
            if (mouseX >= 8 && mouseX < railWidth - 8 && mouseY >= railY && mouseY < railY + railHeight) {
                graphics.fill(8, railY, railWidth - 8, railY + railHeight, ROW_HOVER);
            }
            var label=font.split(Component.translatable(entry.langKey),railWidth-24);
            int labelY=railY+(label.size()>1?2:7);
            for(var line:label){graphics.drawString(font,line,16,labelY,selected?RED:TEXT_DIM,false);labelY+=10;}
            if (selected) graphics.fill(8, railY + 4, 10, railY + 18, RED);
            railY += railStep();
        }

        layoutRows();
        graphics.enableScissor(contentX,LIST_TOP,width-10,listBottom);
        if(category==Category.ATTRIBUTES)renderAttributes(graphics);
        for (Row row : rows) {
            refreshRow(row, state);
            int y=LIST_TOP+row.y-scrollOffset;
            if(y+row.height<LIST_TOP||y>=listBottom)continue;
            boolean hover=mouseX>=contentX&&mouseX<contentX+contentWidth&&mouseY>=y&&mouseY<y+row.height;
            graphics.fill(contentX,y,contentX+contentWidth,y+row.height,hover?0xFF2D2027:0xFF1B202B);
            graphics.fill(contentX,y,contentX+2,y+row.height,hover?RED:RED_SOFT);
            int textY=y+8;
            for(var line:row.titleLines){graphics.drawString(font,line,contentX+10,textY,TEXT,false);textY+=11;}
            textY+=4;
            for(var line:row.description){graphics.drawString(font,line,contentX+10,textY,TEXT_DIM,false);textY+=11;}
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.disableScissor();
        if(maxScroll()>0){
            int viewport=listBottom-LIST_TOP,thumb=Math.max(16,viewport*viewport/Math.max(1,totalHeight));
            int thumbY=LIST_TOP+(viewport-thumb)*scrollOffset/maxScroll();
            graphics.fill(width-8,LIST_TOP,width-5,listBottom,0xFF303744);
            graphics.fill(width-8,thumbY,width-5,thumbY+thumb,0xFFCF6A70);
            graphics.drawString(font,Component.translatable("screen.blacklight.scroll_hint"),contentX,height-15,TEXT_DIM,false);
        }
    }

    private int attributeHeight(){
        int height=0;
        for(var entry:PlayerAttributesView.entries())
            height+=27+11*font.split(entry.value(),Math.max(40,contentWidth-20)).size();
        return height;
    }

    private void renderAttributes(GuiGraphics graphics){
        totalHeight=attributeHeight();scrollOffset=Math.clamp(scrollOffset,0,maxScroll());
        int y=LIST_TOP-scrollOffset;
        for(var entry:PlayerAttributesView.entries()){
            var lines=font.split(entry.value(),Math.max(40,contentWidth-20));
            int height=21+11*lines.size();
            graphics.fill(contentX,y,contentX+contentWidth,y+height,0xFF1B202B);
            graphics.fill(contentX,y,contentX+2,y+height,RED_SOFT);
            graphics.drawString(font,entry.label(),contentX+10,y+5,TEXT_DIM,false);
            int textY=y+17;
            for(var line:lines){graphics.drawString(font,line,contentX+10,textY,TEXT,false);textY+=11;}
            y+=height+6;
        }
    }

    private void refreshRow(Row row, BlacklightState state) {
        if (state == null) {row.button.active=false;return;}
        boolean owned = owned(state, row.id);
        boolean prereqMet = row.requiresId == null || owned(state, row.requiresId);
        boolean affordable = (category==Category.BIOLOGY?state.getBiomass():state.getEvolutionPoints()) >= row.cost;
        if (owned) {
            row.button.active = false;
            row.button.setMessage(Component.translatable("screen.blacklight.state_owned"));
        } else if (!prereqMet) {
            row.button.active = false;
            row.button.setMessage(Component.translatable("screen.blacklight.requires_short"));
            row.button.setTooltip(Tooltip.create(Component.translatable("screen.blacklight.state_requires",Component.translatable("upgrade.blacklight."+row.requiresId))));
        } else {
            row.button.setTooltip(Tooltip.create(Component.translatable("upgrade.blacklight."+row.id+".desc")));
            row.button.active = affordable;
            row.button.setMessage(Component.translatable(category==Category.BIOLOGY?"screen.blacklight.biomass_cost":"screen.blacklight.state_cost", row.cost));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int railY = 64;
        for (Category entry : Category.values()) {
            if (mouseX >= 8 && mouseX < railWidth - 8
                    && mouseY >= railY && mouseY < railY + Math.min(22, railStep() - 2)) {
                if (entry != category) {
                    category = entry;
                    scrollOffset=0;
                    rebuildRows();
                }
                return true;
            }
            railY += railStep();
        }
        return mouseY>=LIST_TOP&&mouseY<listBottom&&super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX,double mouseY,double horizontal,double vertical){
        if(mouseX<contentX||mouseY<LIST_TOP||mouseY>listBottom)return false;
        scrollOffset=Math.clamp(scrollOffset-(int)(vertical*30),0,maxScroll());layoutRows();return true;
    }

    private static boolean owned(BlacklightState state, String id) {
        var combat = CombatSkill.byId(id);
        if (combat != null) return state.hasCombatSkill(combat);
        var growth=com.codex.blacklight.power.GrowthUpgrade.byId(id);
        if(growth!=null){var p=Minecraft.getInstance().player;return p!=null&&p.getData(ModAttachments.GROWTH).owns(growth);}
        var form = com.codex.blacklight.power.PowerForm.byId(id);
        if (form != com.codex.blacklight.power.PowerForm.NONE) return state.hasUnlocked(form);
        ClawUpgrade claw = ClawUpgrade.byId(id);
        if (claw != null) return state.hasClawUpgrade(claw);
        MovementUpgrade movement = MovementUpgrade.byId(id);
        return movement != null && state.hasMovementUpgrade(movement);
    }

    private int railStep() { return Math.clamp((height - 72) / Category.values().length, 20, 26); }

    private static BlacklightState currentState() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player == null ? null
                : minecraft.player.getData(ModAttachments.PLAYER_STATE);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if(keyCode==GLFW.GLFW_KEY_PAGE_DOWN||keyCode==GLFW.GLFW_KEY_PAGE_UP||keyCode==GLFW.GLFW_KEY_HOME||keyCode==GLFW.GLFW_KEY_END){
            int page=Math.max(30,listBottom-LIST_TOP-20);
            scrollOffset=keyCode==GLFW.GLFW_KEY_HOME?0:keyCode==GLFW.GLFW_KEY_END?maxScroll():Math.clamp(scrollOffset+(keyCode==GLFW.GLFW_KEY_PAGE_DOWN?page:-page),0,maxScroll());
            layoutRows();return true;
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private static final class Row {
        private final String id;
        private final int cost;
        private final String requiresId;
        private final Button button;
        private final int y;
        private final int height,buttonOffset;
        private final List<net.minecraft.util.FormattedCharSequence> titleLines,description;

        private Row(String id,int cost,String requiresId,Button button,int y,int height,int buttonOffset,List<net.minecraft.util.FormattedCharSequence> titleLines,List<net.minecraft.util.FormattedCharSequence> description) {
            this.id = id;
            this.cost = cost;
            this.requiresId = requiresId;
            this.button = button;
            this.y = y;
            this.height=height;this.buttonOffset=buttonOffset;this.titleLines=titleLines;this.description=description;
        }
    }
}
