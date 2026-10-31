package com.codex.blacklight.gametest;

import java.lang.reflect.Field;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;

/** Test-only lifecycle access: 1.20 keeps the helper's info private. No release AT. */
public final class ForgeTestLifecycle {
    private static final Field INFO=findInfo();
    private ForgeTestLifecycle(){}
    private static Field findInfo(){
        for(var field:GameTestHelper.class.getDeclaredFields())if(field.getType()==GameTestInfo.class){
            if(field.trySetAccessible())return field;
            throw new IllegalStateException("Cannot observe GameTest lifecycle");
        }
        throw new IllegalStateException("GameTestHelper has no GameTestInfo field");
    }
    public static GameTestInfo info(GameTestHelper helper){
        try{return (GameTestInfo)INFO.get(helper);}
        catch(IllegalAccessException failure){throw new IllegalStateException("Cannot observe GameTest lifecycle",failure);}
    }
}
