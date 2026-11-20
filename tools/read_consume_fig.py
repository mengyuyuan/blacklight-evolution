"""Read the paired P1 puppet, grab-slot and fragment events from alex_fig.p3d.rz."""
import struct
import zlib
from pathlib import Path

# Track IDs and payload layout from the P1 EditP3D FIG schemas.
BRANCH = {7021078959221846271, 4359263088009983578, 27664593011459322,
          31052086116492924, 8163269272626025203, 9791952581861958631,
          8163269264019698807, 16414221063871377845, 4826239466064161373,
          13462353259087708995}

def hash_name(text):
    result = 0
    for char in text.encode("ascii"):
        result = ((result * 65599) ^ char) & 0xffffffffffffffff
    return result

def read_pairs(path: Path, bases):
    data = path.read_bytes()
    if data[:2] == b"RZ":
        data = zlib.decompress(data[16:])
    def get(fmt, offset):
        return struct.unpack_from("<"+fmt, data, offset)[0]
    branches = {}
    def node(pos, owner, end):
        kind, size = struct.unpack_from("<QI", data, pos)
        payload = pos+12
        after = payload+size
        if after+8 > end:
            raise ValueError(f"Invalid FIG node at {pos}")
        if kind in BRANCH:
            owner = pos
        else:
            branches.setdefault(owner, []).append((kind, payload))
        while get("Q", after):
            after = node(after, owner, end)
        return after+8
    def chunks(start, end):
        while start < end:
            kind, header, size = struct.unpack_from("<III", data, start)
            if not 12 <= header <= size <= end-start:
                raise ValueError(f"Invalid P3D chunk at {start}")
            if kind == 0x20000702 and data[start+16:start+20] == b"fig0":
                assert node(start+28, None, start+header) == start+header
            chunks(start+header, start+size)
            start += size
    chunks(0,len(data))
    names = {hash_name(base+role): base+role for base in bases for role in ("_gv","_rcv")}
    result = {}
    for records in branches.values():
        for kind,pos in records:
            if kind != 4673390682830472570 or get("Q",pos+12) not in names:
                continue
            name = names[get("Q",pos+12)]
            row = {"speed": get("f",pos+20), "events": {}, "gibs": []}
            for track,p in records:
                for key in ("consumeByParent","effectConsume","death","setVisibility"):
                    if track == hash_name(key):
                        row["events"][key] = get("f",p+4)
                if track == hash_name("spawnGib"):
                    row["gibs"].append({"seconds":get("f",p+4), "template":get("Q",p+20),
                                       "joint":get("Q",p+32), "slot":get("Q",p+68)})
                if track == 11773931617089059926 and get("f",p+4)==0:
                    row["socket"] = get("Q",p+16)
            result[name] = row
    return result
