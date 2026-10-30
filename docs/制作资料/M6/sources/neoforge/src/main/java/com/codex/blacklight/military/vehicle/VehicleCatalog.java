package com.codex.blacklight.military.vehicle;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Locale;
import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.codex.blacklight.military.vehicle.VehicleDefinition.*;

public final class VehicleCatalog {
    private static final String CONFIG="data/blacklight/military/vehicles.json", MOUNTS="data/blacklight/military/vehicle-mounts.json";
    private static final Set<String> CHASSIS=Set.of("truck","apc","tank");
    private static final Map<String,Set<String>> AMMUNITION=Map.of("truck",Set.of(),"apc",Set.of("autocannon_round","rocket_round"),"tank",Set.of("tank_he_shell","tank_ap_shell","machine_gun_round"));
    private static final Map<String,String> WEAPONS=Map.of("autocannon","autocannon_round","vehicle_rocket","rocket_round","tank_he","tank_he_shell","tank_ap","tank_ap_shell","coaxial_machine_gun","machine_gun_round");
    private final Map<String,VehicleDefinition> definitions;
    private VehicleCatalog(Map<String,VehicleDefinition> definitions) { this.definitions=Map.copyOf(definitions); }
    public static VehicleCatalog loadDefault() { return fromJson(resource(CONFIG)); }
    public static VehicleCatalog fromJson(String json) {
        try {
            JsonObject root=object(parse(json)); fields(root,"schemaVersion","balanceOrigin","vehicles"); version(root);
            JsonObject mounts=object(parse(resource(MOUNTS))); fields(mounts,"schemaVersion","sourceSha256","coordinateSystem","chassis"); version(mounts);
            var mountChassis=object(required(mounts,"chassis"));
            if(!mountChassis.keySet().equals(CHASSIS)) throw new IllegalArgumentException("Unknown or missing mount chassis");
            var result=new LinkedHashMap<String,VehicleDefinition>();
            for(JsonElement element:array(root,"vehicles")) {
                JsonObject v=object(element);
                fields(v,"id","width","length","height","maxHealth","maxSpeed","maxFuelTicks","armor","liveries","mountsResource","chassis","seats","ammunitionCapacities","weapons");
                String id=string(v,"id");
                if(!CHASSIS.contains(id) || !id.equals(string(v,"chassis")) || !MOUNTS.equals(string(v,"mountsResource"))) throw new IllegalArgumentException("Unknown vehicle/mount resource");
                if(result.containsKey(id)) throw new IllegalArgumentException("Duplicate vehicle identifier");
                var mount=object(required(mountChassis,id)); fields(mount,"turretPivot","gunPivot","barrelRadius","anchors");
                var anchors=new LinkedHashMap<String,Anchor>();
                for(var entry:object(required(mount,"anchors")).entrySet()) {
                    var anchor=object(entry.getValue()); fields(anchor,"frame","position");
                    anchors.put(entry.getKey(),new Anchor(enumValue(Frame.class,string(anchor,"frame")),vec(required(anchor,"position"))));
                }
                var seats=new ArrayList<Seat>();
                for(var seatElement:array(v,"seats")) {
                    var seat=object(seatElement); fields(seat,"id","job","anchorId");
                    seats.add(new Seat(string(seat,"id"),enumValue(Job.class,string(seat,"job")),string(seat,"anchorId")));
                }
                var capacities=new LinkedHashMap<String,Integer>();
                for(var entry:object(required(v,"ammunitionCapacities")).entrySet()) capacities.put(entry.getKey(),integer(entry.getValue()));
                if(!capacities.keySet().equals(AMMUNITION.get(id))) throw new IllegalArgumentException("Unknown or missing ammunition identifier");
                var weapons=new LinkedHashMap<String,Weapon>();
                for(var weaponElement:array(v,"weapons")) {
                    var w=object(weaponElement); fields(w,"id","ammunitionId","cooldownTicks","cooldownGroup");
                    var weapon=new Weapon(string(w,"id"),string(w,"ammunitionId"),integer(required(w,"cooldownTicks")),string(w,"cooldownGroup"));
                    if(!weapon.ammunitionId().equals(WEAPONS.get(weapon.id())) || weapons.putIfAbsent(weapon.id(),weapon)!=null)
                        throw new IllegalArgumentException("Unknown or duplicate weapon");
                }
                var definition=new VehicleDefinition(id,number(v,"width"),number(v,"length"),number(v,"height"),number(v,"maxHealth"),number(v,"maxSpeed"),whole(required(v,"maxFuelTicks")),
                        enumValue(Armor.class,string(v,"armor")),strings(array(v,"liveries")),seats,anchors,vec(required(mount,"turretPivot")),vec(required(mount,"gunPivot")),number(mount,"barrelRadius"),capacities,weapons);
                for(var weapon:weapons.values()) {
                    boolean secondary=weapon.id().equals("vehicle_rocket") || weapon.id().equals("coaxial_machine_gun");
                    String suffix=secondary?"secondary":"primary";
                    if(!anchors.containsKey("muzzle_"+suffix) || !anchors.containsKey("breech_"+suffix)) throw new IllegalArgumentException("Weapon has no authoring muzzle/breech");
                }
                result.put(id,definition);
            }
            if(!result.keySet().equals(CHASSIS)) throw new IllegalArgumentException("Three supported chassis are required");
            return new VehicleCatalog(result);
        } catch(IllegalArgumentException e) { throw e; }
        catch(RuntimeException e) { throw new IllegalArgumentException("Invalid vehicle configuration",e); }
    }
    public VehicleDefinition require(String id) {
        var definition=definitions.get(id); if(definition==null) throw new IllegalArgumentException("Unknown vehicle: "+id); return definition;
    }
    public Map<String, VehicleDefinition> definitions() { return definitions; }
    private static String resource(String path) {
        try(var stream=VehicleCatalog.class.getResourceAsStream("/"+path)) {
            if(stream==null) throw new IllegalArgumentException("Missing vehicle resource: "+path);
            byte[] data=stream.readNBytes(1048577);
            if(data.length>1048576) throw new IllegalArgumentException("Oversized vehicle resource");
            return new String(data,StandardCharsets.UTF_8);
        } catch(IOException e) { throw new IllegalArgumentException("Unreadable vehicle resource",e); }
    }
    private static JsonElement parse(String json) {
        if(json==null || json.length()>1048576) throw new IllegalArgumentException("Invalid vehicle JSON size");
        try(var reader=new JsonReader(new StringReader(json))) {
            reader.setLenient(false); JsonElement value=read(reader,0);
            if(reader.peek()!=JsonToken.END_DOCUMENT) throw new IllegalArgumentException("Trailing vehicle JSON");
            return value;
        } catch(IOException|NumberFormatException e) { throw new IllegalArgumentException("Invalid vehicle JSON",e); }
    }
    private static JsonElement read(JsonReader r,int depth) throws IOException {
        if(depth>16) throw new IllegalArgumentException("Vehicle JSON nesting exceeds limit");
        switch(r.peek()) {
            case BEGIN_OBJECT: {
                r.beginObject(); var object=new JsonObject(); var names=new HashSet<String>();
                while(r.hasNext()) {
                    String name=r.nextName(); if(name.length()>128 || names.size()>=256 || !names.add(name)) throw new IllegalArgumentException("Duplicate or excessive vehicle JSON keys");
                    object.add(name,read(r,depth+1));
                }
                r.endObject(); return object;
            }
            case BEGIN_ARRAY: {
                r.beginArray(); var array=new JsonArray();
                while(r.hasNext()) { if(array.size()>=256) throw new IllegalArgumentException("Vehicle JSON array exceeds limit"); array.add(read(r,depth+1)); }
                r.endArray(); return array;
            }
            case STRING: { String s=r.nextString(); if(s.length()>256) throw new IllegalArgumentException("Vehicle JSON string exceeds limit"); return new JsonPrimitive(s); }
            case NUMBER: { var n=new BigDecimal(r.nextString()); if(!Double.isFinite(n.doubleValue()))throw new IllegalArgumentException("Nonfinite vehicle number"); return new JsonPrimitive(n); }
            case BOOLEAN: return new JsonPrimitive(r.nextBoolean());
            default: throw new IllegalArgumentException("Unsupported vehicle JSON value");
        }
    }
    private static JsonObject object(JsonElement e) { if(!e.isJsonObject())throw new IllegalArgumentException("Vehicle object required"); return e.getAsJsonObject(); }
    private static JsonElement required(JsonObject o,String key) { var e=o.get(key); if(e==null)throw new IllegalArgumentException("Missing vehicle field: "+key); return e; }
    private static JsonArray array(JsonObject o,String key) { var e=required(o,key); if(!e.isJsonArray())throw new IllegalArgumentException("Vehicle array required"); return e.getAsJsonArray(); }
    private static String string(JsonObject o,String key) {
        var e=required(o,key); if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("Vehicle string required"); return e.getAsString();
    }
    private static double number(JsonObject o,String key) {
        var e=required(o,key); if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Vehicle number required");
        double n=e.getAsDouble(); if(!Double.isFinite(n))throw new IllegalArgumentException("Nonfinite vehicle number"); return n;
    }
    private static long whole(JsonElement e) {
        if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Vehicle integer required");
        try { return e.getAsBigDecimal().longValueExact(); } catch(ArithmeticException ex) { throw new IllegalArgumentException("Unbounded vehicle integer",ex); }
    }
    private static int integer(JsonElement e) { long n=whole(e); if(n<Integer.MIN_VALUE || n>Integer.MAX_VALUE)throw new IllegalArgumentException("Unbounded vehicle integer"); return (int)n; }
    private static Vec3 vec(JsonElement e) {
        if(!e.isJsonArray() || e.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Three-coordinate vehicle mount required");
        var a=e.getAsJsonArray(); double[] p=new double[3];
        for(int i=0;i<3;i++) { if(!a.get(i).isJsonPrimitive() || !a.get(i).getAsJsonPrimitive().isNumber())throw new IllegalArgumentException("Numeric vehicle mount required"); p[i]=a.get(i).getAsDouble(); }
        return new Vec3(p[0],p[1],p[2]);
    }
    private static List<String> strings(JsonArray a) {
        var strings=new ArrayList<String>(); for(var e:a) { if(!e.isJsonPrimitive() || !e.getAsJsonPrimitive().isString())throw new IllegalArgumentException("String livery required"); strings.add(e.getAsString()); } return strings;
    }
    private static void fields(JsonObject o,String... names) { if(!Set.of(names).containsAll(o.keySet()))throw new IllegalArgumentException("Unknown vehicle field"); }
    private static void version(JsonObject o) { if(whole(required(o,"schemaVersion"))!=1)throw new IllegalArgumentException("Unsupported vehicle schema"); }
    private static <E extends Enum<E>> E enumValue(Class<E> type,String value) { return Enum.valueOf(type,value.toUpperCase(Locale.ROOT)); }
}
