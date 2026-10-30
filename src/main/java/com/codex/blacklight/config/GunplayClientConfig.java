package com.codex.blacklight.config;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Presentation limits only. Server ammunition, cooldown and damage never read these values. */
public final class GunplayClientConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue CAMERA_KICK,TRACERS,CASINGS;
    public static final ModConfigSpec.IntValue EFFECT_LIMIT;
    static{
        var b=new ModConfigSpec.Builder();
        CAMERA_KICK=b.comment("轻微本地开火镜头反馈，不修改玩家朝向或瞄准方向。").define("cameraKick",true);
        TRACERS=b.comment("显示短暂曳光线，伤害仍由服务端判定。").define("tracers",true);
        CASINGS=b.comment("显示短寿命弹壳；不创建服务端物品实体。").define("casings",true);
        EFFECT_LIMIT=b.comment("本地同时存在的枪炮表现事件上限；降低可减轻低配置机器负担。").defineInRange("effectLimit",96,16,128);
        SPEC=b.build();
    }
    private GunplayClientConfig(){}
}
