package com.codex.blacklight.compat;
import java.lang.reflect.Method;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import com.codex.blacklight.config.InfectionEcologyConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Optional public OPAC permission boundary. No foreign capability or claim data is modified. */
public final class NaturalInfectionProtection {
    private static final GameProfile ACTOR=new GameProfile(UUID.nameUUIDFromBytes("blacklight:natural_growth".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"[Blacklight]");
    private record Access(Method api,Method protection,Method allows){}
    private static final Access ACCESS=resolve();
    private static boolean reported;
    private NaturalInfectionProtection(){}
    private static Access resolve(){
        try{
            var api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
            var protection=api.getMethod("getChunkProtection");
            return new Access(api.getMethod("get",MinecraftServer.class),protection,protection.getReturnType().getMethod("hasChunkAccess",Entity.class,ResourceLocation.class,int.class,int.class));
        }catch(ClassNotFoundException absent){return null;}
        catch(ReflectiveOperationException incompatible){return new Access(null,null,null);}
    }
    public static boolean allowed(ServerLevel level,BlockPos pos){
        if(!InfectionEcologyConfig.value(InfectionEcologyConfig.PROTECT_CLAIMS)||ACCESS==null)return true;
        try{
            if(ACCESS.api()==null)throw new IllegalStateException("Unsupported OPAC public protection API");
            var api=ACCESS.api().invoke(null,level.getServer());
            var protection=ACCESS.protection().invoke(api);
            return Boolean.TRUE.equals(ACCESS.allows().invoke(protection,FakePlayerFactory.get(level,ACTOR),level.dimension().location(),pos.getX()>>4,pos.getZ()>>4));
        }catch(ReflectiveOperationException|RuntimeException failure){
            if(!reported){reported=true;com.codex.blacklight.BlacklightNeoMod.LOGGER.warn("Natural infection paused at OPAC permission boundary; originals retained. Check OPAC API compatibility or configure protectClaims explicitly.",failure);}
            return false;
        }
    }
}
