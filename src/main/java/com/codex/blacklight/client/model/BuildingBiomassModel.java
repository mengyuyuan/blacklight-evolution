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
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.geometry.*;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;

/** Baked into ordinary chunk meshes. No per-column renderer, entities, frame sampling or packets. */
@EventBusSubscriber(modid="blacklight",bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BuildingBiomassModel {
    private BuildingBiomassModel() {}
    @SubscribeEvent public static void loaders(ModelEvent.RegisterGeometryLoaders e) {
        e.register(ResourceLocation.fromNamespaceAndPath("blacklight","building_biomass"),(IGeometryLoader<Geometry>)(json,context)->new Geometry());
    }
    public static final class Geometry implements IUnbakedGeometry<Geometry> {
        @Override public BakedModel bake(IGeometryBakingContext c,ModelBaker baker,Function<Material,TextureAtlasSprite> sprites,ModelState state,ItemOverrides overrides) {
            var base=baker.bake(ResourceLocation.fromNamespaceAndPath("blacklight","block/hive_flesh"),state);
            return new Model(base,new TextureAtlasSprite[]{sprites.apply(c.getMaterial("flesh")),sprites.apply(c.getMaterial("skin")),sprites.apply(c.getMaterial("fibre"))});
        }
    }
    private static final class Model extends BakedModelWrapper<BakedModel> {
        private static final ModelProperty<Integer> NEIGHBOUR_FACES=new ModelProperty<>();
        private final List<List<BakedQuad>>[] quads;
        private final List<BakedQuad>[][][] caps;
        private final TextureAtlasSprite particle;
        @SuppressWarnings("unchecked") Model(BakedModel base,TextureAtlasSprite[] sprites) {
            super(base);particle=sprites[0];quads=new List[3];caps=new List[3][6][256];
            for(int material=0;material<3;material++) {
                quads[material]=new ArrayList<>(256);
                for(int mask=0;mask<256;mask++) {
                    var faces=new ArrayList<BakedQuad>();
                    for(var t:BuildingBiomassField.mesh(mask))faces.add(bake(t,sprites[material]));
                    quads[material].add(List.copyOf(faces));
                }
                for(int face=0;face<6;face++)for(int pair=0;pair<256;pair++) {
                    var faces=new ArrayList<BakedQuad>();
                    for(var t:BuildingBiomassField.boundary(pair&15,pair>>4,face/2,(face&1)!=0))faces.add(bake(t,sprites[material]));
                    caps[material][face][pair]=List.copyOf(faces);
                }
            }
        }
        private static BakedQuad bake(BuildingBiomassField.Triangle t,TextureAtlasSprite sprite) {
            var out=new QuadBakingVertexConsumer();var n=t.normal();
            out.setSprite(sprite);out.setDirection(Direction.getNearest((float)n.x(),(float)n.y(),(float)n.z()));out.setShade(true);out.setHasAmbientOcclusion(false);
            for(var p:List.of(t.a(),t.b(),t.c(),t.c())) {
                double u,v;
                if(Math.abs(n.y())>=Math.max(Math.abs(n.x()),Math.abs(n.z()))){u=p.x();v=p.z();}
                else if(Math.abs(n.x())>Math.abs(n.z())){u=p.z();v=1-p.y();}else{u=p.x();v=1-p.y();}
                out.addVertex((float)p.x(),(float)p.y(),(float)p.z()).setColor(255,255,255,255)
                    .setUv(sprite.getU((float)u),sprite.getV((float)v)).setUv2(0,0).setNormal((float)n.x(),(float)n.y(),(float)n.z());
            }
            return out.bakeQuad();
        }
        @Override public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data) {
            int neighbours=0;
            for(var direction:Direction.values()) {
                int axis=switch(direction.getAxis()){case X->0;case Y->1;case Z->2;};
                boolean positive=direction.getAxisDirection()==Direction.AxisDirection.POSITIVE;
                var adjacent=level.getBlockState(pos.relative(direction));
                if(adjacent.getBlock() instanceof BuildingBiomassBlock)
                    neighbours|=BuildingBiomassField.faceMask(adjacent.getValue(BuildingBiomassBlock.CORNERS),axis,!positive)<<((axis*2+(positive?1:0))*4);
            }
            return data.derive().with(NEIGHBOUR_FACES,neighbours).build();
        }
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random) {
            return getQuads(s,side,random,ModelData.EMPTY,null);
        }
        @Override public List<BakedQuad> getQuads(BlockState s,Direction side,RandomSource random,ModelData data,RenderType type) {
            if(side!=null||s==null||!s.hasProperty(BuildingBiomassBlock.CORNERS))return List.of();
            int material=s.getValue(BuildingBiomassBlock.MATERIAL),mask=s.getValue(BuildingBiomassBlock.CORNERS);
            Integer neighbours=data.get(NEIGHBOUR_FACES);int packed=neighbours==null?0:neighbours;
            var surface=quads[material].get(mask);ArrayList<BakedQuad> result=null;
            for(int face=0;face<6;face++) {
                int own=BuildingBiomassField.faceMask(mask,face/2,(face&1)!=0),other=(packed>>>(face*4))&15;
                var cap=caps[material][face][own|(other<<4)];if(cap.isEmpty())continue;
                if(result==null)result=new ArrayList<>(surface);result.addAll(cap);
            }
            return result==null?surface:result;
        }
        @Override public ChunkRenderTypeSet getRenderTypes(BlockState s,RandomSource r,ModelData data) { return ChunkRenderTypeSet.of(RenderType.solid()); }
        @Override public TextureAtlasSprite getParticleIcon() { return particle; }
        @Override public TextureAtlasSprite getParticleIcon(ModelData data) { return particle; }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public net.neoforged.neoforge.common.util.TriState useAmbientOcclusion(BlockState s,ModelData d,RenderType r) { return net.neoforged.neoforge.common.util.TriState.FALSE; }
    }
}
