"""Verify the complete municipal Arnis bundle without starting Minecraft."""
from pathlib import Path
import argparse,hashlib,json,struct,zipfile

def verify(data: Path) -> dict:
    index=json.loads((data/'index.json').read_text(encoding='utf8'))
    assert index['schema']==2 and index['complete'] is True,'Incomplete municipal dataset'
    assert index['projection']['earthRadius']==6371000,'Native projection mismatch'
    raw=(data/'coverage.bin').read_bytes()
    assert hashlib.sha256(raw).hexdigest()==index['coverageSHA256'],'Coverage identity mismatch'
    assert raw[:4]==b'LACM'
    version,count=struct.unpack_from('>II',raw,4)
    assert version==1 and len(raw)==12+136*count
    masks={}
    for pos in range(12,len(raw),136):
        x,z=struct.unpack_from('>ii',raw,pos)
        assert (x,z) not in masks
        masks[x,z]=raw[pos+8:pos+136]
    regions={(r['x'],r['z']):r for r in index['regions']}
    assert len(regions)==len(index['regions'])==len(masks) and regions.keys()==masks.keys()
    assert {p.name for p in data.glob('*.lar')}=={f'{x}_{z}.lar' for x,z in regions}
    total=0;size=0
    for (x,z),r in regions.items():
        p=data/f'{x}_{z}.lar';payload=p.read_bytes();mask=masks[x,z]
        assert len(payload)==r['bytes'] and hashlib.sha256(payload).hexdigest()==r['sha256'],p
        assert struct.unpack_from('>II',payload)==(0x4c415232,1024),p
        end=8200;n=0
        for slot in range(1024):
            offset,length=struct.unpack_from('>II',payload,8+slot*8)
            selected=bool(mask[slot//8]&(1<<(slot%8)))
            assert bool(offset)==selected and bool(length)==selected,(p,slot)
            if selected:
                assert offset==end and length>=18 and offset+length<=len(payload),(p,slot)
                assert payload[offset:offset+3]==b'\x1f\x8b\x08',(p,slot)
                end=offset+length;n+=1
        assert end==len(payload) and n==r['chunks'],p
        total+=n;size+=len(payload)
    assert total==index['nativeChunks']==sum(v.bit_count() for mask in masks.values() for v in mask)
    return {'complete':True,'regions':count,'nativeChunks':total,'bytes':size,'indexSHA256':hashlib.sha256((data/'index.json').read_bytes()).hexdigest(),'checks':['all region identities','exact municipal coverage','contiguous chunk payloads','GZIP framing'],'gameplayTested':False}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--data',type=Path,required=True);p.add_argument('--report',type=Path);a=p.parse_args();result=verify(a.data)
    if a.report:a.report.write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf8')
    print(json.dumps(result,ensure_ascii=False,indent=2))
if __name__=='__main__':main()
