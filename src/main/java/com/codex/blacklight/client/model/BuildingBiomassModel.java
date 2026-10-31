package com.codex.blacklight.client.model;

import com.codex.blacklight.infection.BuildingBiomassField;
import com.codex.blacklight.world.BuildingBiomassBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.geometry.*;
import net.minecraftforge.client.model.pipeline.QuadBakingVertexConsumer;

/** Baked into ordinary chunk meshes. No per-column renderer, entities, frame sampling or packets. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BuildingBiomassModel {
    private BuildingBiomassModel() {}
    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders e) {
        e.register("building_biomass",(IGeometryLoader<Geometry>)(json,context)->new Geometry());
    }
    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override public BakedModel bake(IGeometryBakingContext c,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides,ResourceLocation modelLocation) {
            var base=baker.bake(new ResourceLocation("blacklight","block/hive_flesh"),state);
            return new Model(base,new TextureAtlasSprite[]{sprites.apply(c.getMaterial("flesh")),sprites.apply(c.getMaterial("skin")),sprites.apply(c.getMaterial("fibre"))});
        }
    }
    private static final class Model extends BakedModelWrapper<BakedModel> {
        private final List<List<BakedQuad>>[] quads;
        private final TextureAtlasSprite particle;
        @SuppressWarnings("unchecked") Model(BakedModel base,TextureAtlasSprite[] sprites) {
            super(base);particle=sprites[0];quads=new List[3];
            for(int material=0;material<3;material++) {
                quads[material]=new ArrayList<>(256);
                for(int mask=0;mask<256;mask++) {
                    var faces=new ArrayList<BakedQuad>();
                    for(var t:BuildingBiomassField.mesh(mask)) {
                        var out=new QuadBakingVertexConsumer(faces::add);var n=t.normal();var sprite=sprites[material];
                        out.setSprite(sprite);out.setDirection(Direction.getNearest((float)n.x(),(float)n.y(),(float)n.z()));out.setShade(true);out.setHasAmbientOcclusion(false);
                        for(var p:List.of(t.a(),t.b(),t.c(),t.c())) {
                            double u,v;
                            if(Math.abs(n.y())>=Math.max(Math.abs(n.x()),Math.abs(n.z()))){u=p.x();v=p.z();}
                            else if(Math.abs(n.x())>Math.abs(n.z())){u=p.z();v=1-p.y();}else{u=p.x();v=1-p.y();}
                            out.vertex((float)p.x(),(float)p.y(),(float)p.z()).color(255,255,255,255)
                                .uv(sprite.getU(u*16),sprite.getV(v*16)).uv2(0,0).normal((float)n.x(),(float)n.y(),(float)n.z()).endVertex();
                        }
                    }
                    quads[material].add(List.copyOf(faces));
                }
            }
        }
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random) {
            return side!=null||s==null||!s.hasProperty(BuildingBiomassBlock.CORNERS)?List.of():quads[s.getValue(BuildingBiomassBlock.MATERIAL)].get(s.getValue(BuildingBiomassBlock.CORNERS));
        }
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType type) { return getQuads(s,side,random); }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState s,RandomSource r,ModelData data) { return ChunkRenderTypeSet.of(RenderType.solid()); }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return particle; }
        @Override public boolean useAmbientOcclusion() { return false; }
    }
}
