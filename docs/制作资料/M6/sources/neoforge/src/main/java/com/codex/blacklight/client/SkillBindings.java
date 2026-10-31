package com.codex.blacklight.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** One source of truth for tutorial labels and physical combat inputs. */
public final class SkillBindings {
    private SkillBindings() {}
    public static KeyMapping find(String name) {
        for (var mapping : Minecraft.getInstance().options.keyMappings)
            if (mapping.getName().equals(name)) return mapping;
        return null;
    }
    public static Component label(String name) {
        var mapping=find(name);
        return mapping==null || mapping.isUnbound()
                ? Component.translatable("tutorial.blacklight.unbound") : mapping.getTranslatedKeyMessage();
    }
    public static boolean matches(String name,int key,int scan) {
        var mapping=find(name);
        return mapping!=null && mapping.matches(key,scan);
    }
    public static boolean mouse(KeyMapping mapping,int button) {
        return mapping.matchesMouse(button) && mapping.getKeyModifier().isActive(mapping.getKeyConflictContext());
    }
    private static boolean validKeyCode(int key){
        return key==32||key==39||key>=44&&key<=57||key==59||key==61||key>=65&&key<=93||key==96
                ||key==161||key==162||key>=256&&key<=269||key>=280&&key<=284||key>=290&&key<=314
                ||key>=320&&key<=336||key>=340&&key<=348;
    }
    public static boolean down(KeyMapping mapping) {
        var mc=Minecraft.getInstance();
        if(mapping==null || mapping.isUnbound() || mc.getWindow()==null)return false;
        if(!mapping.getKeyModifier().isActive(mapping.getKeyConflictContext()))return false;
        var key=mapping.getKey();long window=mc.getWindow().getWindow();
        if(key.getType()==InputConstants.Type.MOUSE)return GLFW.glfwGetMouseButton(window,key.getValue())==GLFW.GLFW_PRESS;
        if(key.getType()==InputConstants.Type.KEYSYM)return InputConstants.isKeyDown(window,key.getValue());
        // Scancode bindings are uncommon but remain valid after a layout change.
        for(int code=GLFW.GLFW_KEY_SPACE;code<=GLFW.GLFW_KEY_LAST;code++)
            if(validKeyCode(code) && GLFW.glfwGetKeyScancode(code)==key.getValue() && InputConstants.isKeyDown(window,code))return true;
        return false;
    }
}
