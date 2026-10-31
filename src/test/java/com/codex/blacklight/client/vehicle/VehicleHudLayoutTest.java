package com.codex.blacklight.client.vehicle;

import com.codex.blacklight.client.VehicleControls;
import com.codex.blacklight.client.VehicleHud;
import com.codex.blacklight.client.renderer.StandalonePlayerRendering;
import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VehicleHudLayoutTest {
    @Test void longEnglishAndChineseVehicleRowsFitAboveVanillaActionbarAtSupportedGuiScales() {
        // Font metrics are the boundary input; use conservative 6px Latin / 9px CJK.
        var splitter=new StringSplitter((codepoint,style)->codepoint>=0x2E80?9:6);
        var languages=List.of(List.of("HP 420/420 | Commander", "Primary: HE Main Gun: 999 rounds | Tank AP Shell: 999",
                "Secondary: Coaxial Machine Gun: 9999 rounds",
                "WASD drive · Space brake · Shift dismount · Bind seat/ammo keys in Controls",
                "Supply: stop, dismount, right-click the hull with matching ammunition or repair parts"),
                List.of("耐久 420/420 · 车长", "主武器：坦克高爆主炮：999 发 | 坦克穿甲弹：999 发", "副武器：同轴机枪：9999 发",
                "WASD驾驶 · 空格刹车 · Shift下车 · 换座/选弹请在按键设置中绑定", "补给：停稳下车，持对应弹药/维修件右键车体"));
        for(int[] resolution:new int[][]{{1920,1080},{854,480},{1280,720},{1280,800}})for(int requested:new int[]{2,3,4}) {
            int scale=1;
            while(scale<requested&&resolution[0]/(scale+1)>=320&&resolution[1]/(scale+1)>=240)scale++;
            int width=(int)Math.ceil(resolution[0]/(double)scale),height=(int)Math.ceil(resolution[1]/(double)scale);
            for(var rows:languages) {
                int textWidth=VehicleHud.layout(width,height,0).width();int lineCount=0;
                for(String row:rows) {
                    var lines=splitter.splitLines(FormattedText.of(row),textWidth,Style.EMPTY);lineCount+=lines.size();
                    for(var line:lines)assertTrue(splitter.stringWidth(line)<=textWidth,"wrapped text must stay in its panel");
                }
                var layout=VehicleHud.layout(width,height,lineCount);
                String scenario=resolution[0]+"x"+resolution[1]+" GUI "+requested+" lines="+lineCount;
                assertTrue(layout.y()>=26,"leave the existing top-left ability bar visible: "+scenario);
                assertTrue(layout.x()>=6&&layout.x()+layout.width()*layout.scale()<=width/2F-4,"keep panel left of the central viewport: "+scenario);
                assertTrue(layout.bottom(lineCount)+4<=height-80,"panel must clear the actionbar at height-72 and hotbar: "+scenario);
                assertTrue(layout.scale()>=.75F&&layout.scale()<=1,"text stays readable: "+scenario);
                for(int line=1;line<lineCount;line++)assertTrue(layout.lineY(line)-layout.lineY(line-1)>=9*layout.scale(),"wrapped rows cannot overlap");
            }
        }
    }
    @Test void mountedVehicleYieldsNativeHandsBeforeAnyAbilityDrawing() {
        assertFalse(StandalonePlayerRendering.allowFirstPersonHands(true),"a mounted real vehicle must stop native ability hand drawing at entry");
    }
    @Test void nonvehicleHandsRemainAvailable() {
        assertTrue(StandalonePlayerRendering.allowFirstPersonHands(false),"ordinary claws/arms/shield keep their existing hand path");
    }
    @Test void vehicleHandCancellationRunsBeforeNormalNativeHandlers() throws Exception {
        var registration=VehicleControls.class.getMethod("hands",RenderHandEvent.class).getAnnotation(SubscribeEvent.class);
        assertEquals(EventPriority.HIGHEST,registration.priority(),"cancellation must precede NORMAL handlers, not happen after their vertices");
    }
}
