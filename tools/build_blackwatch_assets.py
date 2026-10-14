"""Author the faction's small pixel atlas and two inventory icons. No external assets."""
from pathlib import Path
import struct,zlib,binascii
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources/assets/blacklight/textures'
def image(w,h,base):
 return [[base for x in range(w)] for y in range(h)]
def rect(a,x,y,w,h,c):
 for j in range(y,min(y+h,len(a))):
  for i in range(x,min(x+w,len(a[0]))):a[j][i]=c
 defunused=0
def png(path,a):
 h=len(a);w=len(a[0]);raw=b''.join(b'\0'+bytes(v for c in row for v in c) for row in a)
 def chunk(t,b):return struct.pack('>I',len(b))+t+b+struct.pack('>I',binascii.crc32(t+b)&0xffffffff)
 path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',w,h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
a=image(64,64,(37,43,45,255))
for y in range(64):
 for x in range(64):
  v=((x*13+y*7+x*y)%7)-3;a[y][x]=(37+v,43+v,45+v,255)
# Seams, armor edges, webbing and crimson unit bands on the humanoid UV.
for x,y,w,h in [(8,8,8,8),(20,20,8,12),(4,20,4,12),(44,20,4,12),(0,48,30,14)]:
 rect(a,x,y,w,h,(27,31,34,255));rect(a,x,y,w,1,(63,70,73,255));rect(a,x,y,1,h,(49,56,60,255))
rect(a,9,10,6,2,(112,134,129,255));rect(a,10,13,4,2,(15,19,20,255))
rect(a,44,23,4,2,(113,29,32,255));rect(a,20,28,8,1,(92,82,59,255))
rect(a,0,32,35,14,(29,35,38,255));rect(a,0,32,35,1,(78,86,88,255))
rect(a,40,32,18,5,(24,28,29,255));rect(a,40,40,10,3,(124,144,127,255))
rect(a,32,0,24,13,(23,27,30,255));rect(a,32,0,24,1,(74,80,82,255))
for y in (52,56,60):rect(a,6,y,16,1,(67,70,61,255))
png(ROOT/'entity/blackwatch_soldier.png',a)
a=image(16,16,(0,0,0,0));rect(a,2,2,12,12,(43,63,50,255));rect(a,3,3,10,10,(31,90,61,255));rect(a,6,5,5,6,(25,28,29,255))
for i in (3,5,7,9,11):rect(a,i,1,1,2,(190,155,77,255));rect(a,i,13,1,2,(190,155,77,255))
for x,y in ((4,4),(4,9),(11,4),(11,11)):rect(a,x,y,2,1,(210,177,89,255))
png(ROOT/'item/military_circuit.png',a)
a=image(16,16,(0,0,0,0));rect(a,3,3,10,12,(46,52,51,255));rect(a,4,4,8,7,(21,40,30,255));rect(a,5,5,6,5,(34,77,43,255));rect(a,8,0,2,4,(83,91,87,255));rect(a,5,12,2,1,(129,36,30,255));rect(a,9,12,2,1,(137,152,119,255));rect(a,7,6,1,3,(167,213,108,255));rect(a,6,7,3,1,(167,213,108,255))
png(ROOT/'item/field_scanner.png',a)
