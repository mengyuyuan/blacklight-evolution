package com.codex.blacklight.client.tuning;

import com.codex.blacklight.BlacklightNeoMod;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

/** Retains the proportions saved by older clients for each biological arm. */
public final class ClawTransformSettings {
    public enum HandSide { RIGHT, LEFT }
    private record Shape(double width,double length) {}
    private static final Shape DEFAULT = new Shape(1,1);
    private static Shape right=DEFAULT, left=DEFAULT;
    private static boolean loaded;

    private ClawTransformSettings() {}

    private static Shape shape(HandSide side) {
        if (!loaded) {
            loaded=true;
            var path=Minecraft.getInstance().gameDirectory.toPath().resolve("config/blacklight-claw-transform.properties");
            if (Files.isRegularFile(path)) {
                try(var reader=Files.newBufferedReader(path)) {
                    var values=new Properties();
                    values.load(reader);
                    var rightShape=read(values,"right_");
                    var leftShape=read(values,"left_");
                    right=rightShape;
                    left=leftShape;
                } catch(IOException | IllegalArgumentException e) {
                    BlacklightNeoMod.LOGGER.warn("Unable to read saved claw proportions",e);
                }
            }
        }
        return side==HandSide.LEFT?left:right;
    }

    private static Shape read(Properties values,String prefix) {
        double width=number(values,prefix+"limb_width",1,1.4);
        double length=number(values,prefix+"talon_length",.7,1.5);
        return new Shape(width,length);
    }
    private static double number(Properties values,String key,double min,double max) {
        double value=Double.parseDouble(values.getProperty(key,"1"));
        return Double.isFinite(value)?Math.min(max,Math.max(min,value)):1;
    }
    public static double limbWidth(HandSide side) { return shape(side).width; }
    public static double talonLength(HandSide side) { return shape(side).length; }
}
