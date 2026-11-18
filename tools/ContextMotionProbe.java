import com.codex.blacklight.animation.MatrixMotionClip;
import com.google.gson.JsonParser;
import java.nio.file.*;
import java.util.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;

/** Uses the production MATRIX sampler; runs independently of the Minecraft toolchain. */
public final class ContextMotionProbe {
    public static void main(String[] args) throws Exception {
        Path root=Path.of(args[0]);int samples=0,files=0;
        var names=new ArrayList<String>(List.of("ground_stomp","block_throw_one_slam","block_throw_two_slam"));
        for(String pair:List.of("carry_sprint","carry_air","air_throw","leg_slam","normal_head_stomp","normal_beatdown","normal_knee_head","stealth_neck","stealth_bearhug","stealth_nelson","stealth_guillotine","stealth_jugular"))
            for(String role:List.of("actor","victim"))names.add("consume_"+pair+"_"+role);
        for(String name:names){
            var json=JsonParser.parseString(Files.readString(root.resolve("art/motion-profile/"+name+".json"))).getAsJsonObject();
            var clip=MatrixMotionClip.parse(json);var meta=json.getAsJsonObject("blacklight");
            float finish=clip.duration()+meta.get("transitionSeconds").getAsFloat();
            float ticks=meta.get("durationTicks").getAsInt()/20F;
            if(ticks+.001F<finish||ticks-finish>.101F)throw new AssertionError(name+" clock mismatch");
            if(!clip.joints().contains("Coord"))throw new AssertionError(name+" missing Coord");
            for(int tick=0;tick<=Math.ceil(clip.duration()*120);tick++)
                for(var matrix:clip.sample(tick/120F,false).values()){
                    if(!matrix.isFinite()||Math.abs(matrix.determinant3x3()-1)>0.002)throw new AssertionError(name+" invalid interpolation");
                    samples++;
                }
            System.out.println(name+": "+clip.duration()+"s, "+clip.joints().size()+" tracks");files++;
        }
        // Syntax only, not a substitute for a NeoForge compile/type check.
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        var diagnostics=new DiagnosticCollector<JavaFileObject>();
        try(var manager=compiler.getStandardFileManager(diagnostics,null,null)){
            var sources=new ArrayList<Path>();
            for(int i=1;i<args.length;i++)sources.add(root.resolve(args[i]));
            var task=(JavacTask)compiler.getTask(null,manager,diagnostics,List.of("-proc:none","--enable-preview","--source","17"),null,manager.getJavaFileObjectsFromPaths(sources));
            for(var ignored:task.parse()){}
            for(var diagnostic:diagnostics.getDiagnostics())if(diagnostic.getKind()==Diagnostic.Kind.ERROR)throw new AssertionError(diagnostic.toString());
            System.out.println("Syntax parsed: "+sources.size()+" Java sources (no type checking)");
        }
        System.out.println("PASS: "+files+" clips; "+samples+" interpolated matrices");
    }
}
