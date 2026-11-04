package io.github.blackbaroness.loader.runtime.relocator;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelocationTest {
    @Test
    void replacesLiteralPackagePrefixesOnly() {
        var rule = new Relocation("com.old", "relocated.$literal");
        assertTrue(rule.canRelocateClass("com.old.Type"));
        assertFalse(rule.canRelocateClass("com.older.Type"));
        assertFalse(rule.canRelocatePath("com/older/Type.class"));
        assertEquals("relocated.$literal.Type", rule.relocateClass("com.old.Type"));
        assertEquals("relocated/$literal/Type.class", rule.relocatePath("com/old/Type.class"));
        assertTrue(rule.canRelocatePath("/com/old/Type.class"));
        assertEquals("/relocated/$literal/Type.class", rule.relocatePath("/com/old/Type.class"));
    }

    @Test
    void preservesDescriptorsAndMultiReleasePrefixes() {
        var remapper = new RelocatingRemapper(List.of(new Relocation("com.old", "relocated")));
        assertEquals("Lrelocated/Type;", remapper.mapValue("Lcom/old/Type;"));
        assertEquals("[[Lrelocated/Type;", remapper.mapValue("[[Lcom/old/Type;"));
        assertEquals("META-INF/versions/17/relocated/Type.class",
            remapper.map("META-INF/versions/17/com/old/Type.class"));
        assertEquals("META-INF/versions/17/relocated.Type",
            remapper.mapValue("META-INF/versions/17/com.old.Type"));
        assertEquals("[[I", remapper.mapValue("[[I"));
        assertEquals("unrelated text", remapper.mapValue("unrelated text"));
        assertEquals(Type.getObjectType("relocated/Type"), remapper.mapValue(Type.getObjectType("com/old/Type")));
    }

    @Test
    void rejectsMalformedPrefixesWithoutRejectingUnicodeOrDollarSigns() {
        for (String prefix : List.of("", " ", ".com", "com.", "com..old", "/com", "com/", "com//old",
            "com. old", "com.\told", "com.\u00a0old")) {
            assertThrows(IllegalArgumentException.class, () -> new Relocation(prefix, "hidden"), prefix);
            assertThrows(IllegalArgumentException.class, () -> new Relocation("old", prefix), prefix);
        }
        var rule = new Relocation("пример/пакет", "скрыто.$literal");
        assertEquals("скрыто.$literal.Тип", rule.relocateClass("пример.пакет.Тип"));
    }

    @Test
    void preservesRuleOrderAndOwnsAnImmutableCopy() {
        var rules = new ArrayList<>(List.of(
            new Relocation("com.old", "first"),
            new Relocation("com.old.special", "second")));
        var remapper = new RelocatingRemapper(rules);
        rules.clear();
        assertEquals("first/special/Type", remapper.map("com/old/special/Type"));
        assertEquals("first.special.Type", remapper.mapClassName("com.old.special.Type"));
        assertEquals("first.special.Type", remapper.mapValue("com.old.special.Type"));
        assertEquals("outside/Type", remapper.map("outside/Type"));
    }

}
