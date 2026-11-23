package com.codex.blacklight.world;

import com.codex.blacklight.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.function.Supplier;

/** Rows run north to south. Rotating the entire offering also matches. */
public enum WeavePattern {
    CHEST(ModItems.PULSE_HEART, 180, "F.G.F",".V.V.","..W..",".L.L.","..C.."),
    KNEES(ModItems.SINEW_KNOT, 120, ".F.F.",".V.V.","L.W.L",".F.F.","....."),
    FEET(ModItems.SPORE_ANKLET, 80, ".....",".V.V.",".FWF.","L...L","FF.FF"),
    TEMPLE(ModItems.TEMPLE_BUD, 100, ".FVF.","C...C",".LWL.",".....",".....");

    public final Supplier<? extends Item> result;
    public final int biomass;
    private final String[] rows;
    WeavePattern(Supplier<? extends Item> result,int biomass,String... rows) {
        this.result=result; this.biomass=biomass; this.rows=rows;
    }
    public Item ingredient(int x,int z) {
        return switch(rows[z].charAt(x)) {
            case 'F' -> ModItems.TISSUE_FIBER.get();
            case 'V' -> ModItems.HIVE_VEIN.get();
            case 'L' -> ModItems.LIVING_HIVE_TISSUE.get();
            case 'W' -> ModItems.WITHER_CORE.get();
            case 'G' -> ModItems.EVOLVED_GANGLION.get();
            case 'C' -> ModItems.HIVE_CARAPACE.get();
            default -> null;
        };
    }
    boolean matches(ItemStack[] slots) {
        for(int rotation=0;rotation<4;rotation++) {
            boolean fits=true;
            for(int z=0;z<5 && fits;z++) for(int x=0;x<5;x++) {
                int rx=x, rz=z;
                for(int turn=0;turn<rotation;turn++){int old=rx; rx=4-rz; rz=old;}
                Item expected=ingredient(rx,rz);
                ItemStack actual=slots[z*5+x];
                if(expected==null ? !actual.isEmpty() : !actual.is(expected)) {fits=false;break;}
            }
            if(fits)return true;
        }
        return false;
    }
}
