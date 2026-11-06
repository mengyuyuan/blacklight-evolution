package com.codex.blacklight.mixin;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import static org.junit.jupiter.api.Assertions.*;

/**
 * CI runs without Epic Fight, so the {@code @Pseudo} mixin is never applied there. Check the redirect
 * handler against its target descriptor instead: Mixin rejects any handler type that differs from the
 * target without {@code @Coerce}, which fails mod loading as soon as Epic Fight is installed.
 */
class OptionalEpicFightModeMixinTest {
    private static final String REDIRECT = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String COERCE = "Lorg/spongepowered/asm/mixin/injection/Coerce;";

    @Test void redirectHandlerCoercesEveryTypeThatDiffersFromItsTarget() throws Exception {
        ClassNode mixin = new ClassNode();
        try (var input = getClass().getResourceAsStream("/com/codex/blacklight/mixin/OptionalEpicFightModeMixin.class")) {
            assertNotNull(input);
            new ClassReader(input).accept(mixin, ClassReader.SKIP_CODE);
        }
        int handlers = 0;
        for (MethodNode handler : mixin.methods) {
            AnnotationNode redirect = annotation(handler.visibleAnnotations, REDIRECT);
            if (redirect == null) redirect = annotation(handler.invisibleAnnotations, REDIRECT);
            if (redirect == null) continue;
            handlers++;
            String target = (String) value((AnnotationNode) value(redirect, "at"), "target");
            int ownerEnd = target.indexOf(';') + 1;
            Type owner = Type.getType(target.substring(0, ownerEnd));
            Type invoked = Type.getMethodType(target.substring(target.indexOf('(')));
            Type[] expected = new Type[invoked.getArgumentTypes().length + 1];
            expected[0] = owner;
            System.arraycopy(invoked.getArgumentTypes(), 0, expected, 1, invoked.getArgumentTypes().length);

            Type[] actual = Type.getArgumentTypes(handler.desc);
            assertTrue(actual.length >= expected.length, handler.name + " parameters");
            for (int i = 0; i < expected.length; i++) {
                if (actual[i].equals(expected[i])) continue;
                assertTrue(coerced(handler.invisibleParameterAnnotations, i) || coerced(handler.visibleParameterAnnotations, i),
                        handler.name + " parameter " + i + " is " + actual[i] + " but the target passes " + expected[i]);
            }
            Type returned = Type.getReturnType(handler.desc);
            if (!returned.equals(invoked.getReturnType())) {
                assertTrue(annotation(handler.invisibleAnnotations, COERCE) != null || annotation(handler.visibleAnnotations, COERCE) != null,
                        handler.name + " returns " + returned + " but the target returns " + invoked.getReturnType());
            }
        }
        assertEquals(1, handlers);
    }

    private static boolean coerced(List<AnnotationNode>[] parameters, int index) {
        return parameters != null && index < parameters.length && annotation(parameters[index], COERCE) != null;
    }

    private static AnnotationNode annotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) return null;
        for (AnnotationNode annotation : annotations) if (annotation.desc.equals(descriptor)) return annotation;
        return null;
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        return fail(annotation.desc + " has no " + name);
    }
}