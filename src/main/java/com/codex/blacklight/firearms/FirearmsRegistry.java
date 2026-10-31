package com.codex.blacklight.firearms;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.eventbus.api.IEventBus;

public final class FirearmsRegistry {
    private static final DeferredRegister<net.minecraft.world.item.Item> ITEMS=DeferredRegister.create(Registries.ITEM,"blacklight");
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"blacklight");
    private static final Map<String,Supplier<? extends Item>> AMMO=new LinkedHashMap<>();
    private static final Map<String,Supplier<? extends FirearmItem>> WEAPONS=new LinkedHashMap<>();
    static {
        for(var d:WeaponCatalog.ALL){
            if(!AMMO.containsKey(d.ammoId())){String id=d.ammoId();AMMO.put(id,registerItem(id,Item::new,new Item.Properties()));}
            WEAPONS.put(d.id(),registerItem(d.id(),props->new FirearmItem(props,d),new Item.Properties().stacksTo(1)));
        }
        TABS.register("military_weapons",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.blacklight.military_weapons"))
                .icon(()->weapon(WeaponCatalog.RIFLE.id()).getDefaultInstance())
                .displayItems((parameters,output)->{WEAPONS.values().forEach(i->output.accept(i.get()));AMMO.values().forEach(i->output.accept(i.get()));}).build());
    }
    public static Item ammo(String id){return AMMO.get(id).get();}
    public static FirearmItem weapon(String id){return WEAPONS.get(id).get();}
    public static void register(IEventBus bus){ITEMS.register(bus);TABS.register(bus);}
    private FirearmsRegistry(){}
    private static <T extends Item> RegistryObject<T> registerItem(String id,java.util.function.Function<Item.Properties,T> factory,Item.Properties properties){
        return ITEMS.register(id,()->factory.apply(properties));
    }
}
