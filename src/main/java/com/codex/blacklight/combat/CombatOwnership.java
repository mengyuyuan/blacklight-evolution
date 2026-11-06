package com.codex.blacklight.combat;

import java.lang.reflect.Method;
import java.util.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.damagesource.DamageSource;

/** Resolve attribution without loading chunks or depending on foreign classes. */
public final class CombatOwnership {
    private static final ClassValue<List<Method>> OWNERS=new ClassValue<>(){
        protected List<Method> computeValue(Class<?> type){var methods=new ArrayList<Method>();
            for(String name:new String[]{"getOwner","getCaster","getShooter"})try{var m=type.getMethod(name);if(Entity.class.isAssignableFrom(m.getReturnType()))methods.add(m);}catch(NoSuchMethodException ignored){}
            return List.copyOf(methods);
        }
    };
    private CombatOwnership(){}
    public static Player player(DamageSource source){var p=player(source.getEntity());return p!=null?p:player(source.getDirectEntity());}
    public static Player player(Entity entity){
        var seen=Collections.newSetFromMap(new IdentityHashMap<Entity,Boolean>());
        for(int depth=0;entity!=null&&depth<8&&seen.add(entity);depth++){
            if(entity instanceof Player p)return p;
            Entity next=entity instanceof Projectile projectile?projectile.getOwner():entity instanceof OwnableEntity pet?pet.getOwner():null;
            if(next==null)for(var method:OWNERS.get(entity.getClass()))try{var owner=method.invoke(entity);if(owner instanceof Entity e){next=e;break;}}catch(ReflectiveOperationException ignored){}
            entity=next;
        }
        return null;
    }
}
