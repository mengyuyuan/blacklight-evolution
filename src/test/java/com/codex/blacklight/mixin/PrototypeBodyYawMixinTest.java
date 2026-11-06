package com.codex.blacklight.mixin;

import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CI never launches a client, and the body-yaw injection uses require = 0. A renamed or retyped
 * movement handler would therefore disable the alignment silently; check the target statically.
 */
class PrototypeBodyYawMixinTest {
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";

    @Test void injectionTargetsTheMovementHandlerAndIsRegisteredAsClientMixin() throws Exception {
        ClassNode mixin = read("com/codex/blacklight/mixin/client/PrototypeBodyYawMixin");
        ClassNode events = read("com/codex/blacklight/client/ClientMovementEvents");
        int handlers = 0;
        for (MethodNode handler : mixin.methods) {
            AnnotationNode inject = annotation(handler, INJECT);
            if (inject == null) continue;
            handlers++;
            @SuppressWarnings("unchecked") List<String> selectors = (List<String>) value(inject, "method");
            assertEquals(1, selectors.size());
            String selector = selectors.get(0);
            String name = selector.substring(0, selector.indexOf('(')), desc = selector.substring(selector.indexOf('('));
            MethodNode target = events.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                    .orElseThrow(() -> new AssertionError("ClientMovementEvents has no " + selector));
            assertTrue((target.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) != 0, "static handler");
            assertEquals(Type.getArgumentTypes(target.desc)[0], Type.getArgumentTypes(handler.desc)[0], "handler event type");
        }
        assertEquals(1, handlers);

        try (InputStream in = getClass().getResourceAsStream("/blacklight.mixins.json")) {
            assertNotNull(in);
            var client = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("client");
            assertTrue(client.toString().contains("\"client.PrototypeBodyYawMixin\""), "registered in the client mixin list");
        }
    }

    private ClassNode read(String internalName) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream in = getClass().getResourceAsStream("/" + internalName + ".class")) {
            assertNotNull(in, internalName);
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
        }
        return node;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        for (List<AnnotationNode> annotations : List.of(
                method.visibleAnnotations == null ? List.<AnnotationNode>of() : method.visibleAnnotations,
                method.invisibleAnnotations == null ? List.<AnnotationNode>of() : method.invisibleAnnotations)) {
            for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        }
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        return fail(annotation.desc + " has no " + name);
    }
}