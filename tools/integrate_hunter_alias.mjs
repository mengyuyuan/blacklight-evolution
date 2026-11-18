import fs from 'node:fs';
const neo='D:/blacklight-evolution/',forge='D:/blacklight-evolution-forge-1.20.1/';
const java='src/main/java/com/codex/blacklight/';
const hunter=java+'entity/BlacklightHunter.java';
const source=fs.readFileSync(neo+hunter,'utf8');
const start=source.indexOf('    /** Saved duplicate Brawlers');
const end=source.indexOf('    @Override protected SoundEvent getDeathSound',start);
if(start<0||end<0)throw Error('Missing compatibility method');
let method=source.slice(start,end).replace('if(Math.abs(getAttribute(Attributes.STEP_HEIGHT).getBaseValue()-.6)<.0001)getAttribute(Attributes.STEP_HEIGHT).setBaseValue(1);','if(getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).getBaseValue()==0)getAttribute(net.minecraftforge.common.ForgeMod.STEP_HEIGHT_ADDITION.get()).setBaseValue(.4);');
let port=fs.readFileSync(forge+hunter,'utf8');
if(!port.includes('restoreLegacyBrawlerAttributes(tag);'))port=port.replace('        super.readAdditionalSaveData(tag);','        super.readAdditionalSaveData(tag);\n        restoreLegacyBrawlerAttributes(tag);');
const oldStart=port.indexOf('    /** Saved duplicate Brawlers');
if(oldStart>=0)port=port.slice(0,oldStart)+method+port.slice(port.indexOf('    @Override protected SoundEvent getDeathSound',oldStart));
else port=port.replace('    @Override protected SoundEvent getDeathSound',method+'    @Override protected SoundEvent getDeathSound');
fs.writeFileSync(forge+hunter,port);
for(const root of [neo,forge]){
    const file=root+java+'client/ClientClawEvents.java';let text=fs.readFileSync(file,'utf8');
    const renderer='        event.registerEntityRenderer(com.codex.blacklight.registry.EncounterEntities.LEGACY_BRAWLER.get(), com.codex.blacklight.client.renderer.BlacklightHunterRenderer::new);';
    if(!text.includes(renderer))text=text.replace('        event.registerEntityRenderer(ModEntities.BLACKLIGHT_HUNTER.get(), com.codex.blacklight.client.renderer.BlacklightHunterRenderer::new);','$&\n'+renderer);
    fs.writeFileSync(file,text);
    for(const lang of ['zh_cn','en_us']){
        const file=root+'src/main/resources/assets/blacklight/lang/'+lang+'.json';const data=JSON.parse(fs.readFileSync(file,'utf8'));
        data['entity.blacklight.brawler']=data['entity.blacklight.blacklight_hunter'];
        data['item.blacklight.brawler_spawn_egg']=data['item.blacklight.blacklight_hunter_spawn_egg'];
        fs.writeFileSync(file,JSON.stringify(data,null,2)+'\n');
    }
}
console.log('Integrated canonical hunter save migration, renderer and legacy egg names in both versions.');
