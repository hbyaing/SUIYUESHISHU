package dev.starcrown;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import com.eliotlash.mclib.math.IValue;
import com.eliotlash.mclib.math.functions.classic.*;
import software.bernie.geckolib.core.molang.MolangParser;

/** Keeps native BB JSON intact while adapting mesh.9's scope and angle units. */
public final class MingGuLongAnimationCompatibility {
    private MingGuLongAnimationCompatibility() {}

    public static void init() {
        String path = "/assets/starcrowndoll/models/entity/minggulong/native-json-manifest.json";
        try (var input = MingGuLongAnimationCompatibility.class.getResourceAsStream(path)) {
            if (input == null) throw new IllegalStateException("Missing native JSON manifest");
            var manifest = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var parser = MolangParser.INSTANCE;
            // Bedrock Molang uses degrees for inverse trig as well as sin/cos.
            // mesh.9 keeps mclib's radian-returning inverse functions. The
            // exported wing shear decomposition relies on the Bedrock units.
            parser.functions.put("math.asin", ASinDegrees.class);
            parser.functions.put("math.acos", ACosDegrees.class);
            parser.functions.put("math.atan", ATanDegrees.class);
            parser.functions.put("math.atan2", ATan2Degrees.class);
            // In mesh.9, an assignment to a new local on the first statement
            // dereferences a null compound scope. RHS lookups also use the global
            // map. Pre-register the exported names before resource loading.
            for (var value : manifest.getAsJsonArray("molang_assignment_variables")) {
                parser.getVariable(value.getAsString());
            }
        } catch (Exception error) {
            throw new IllegalStateException("Cannot initialize native BB animation variables", error);
        }
    }

    public static final class ASinDegrees extends ASin {
        public ASinDegrees(IValue[] args, String name) throws Exception { super(args, name); }
        @Override public double get() { return Math.toDegrees(super.get()); }
    }
    public static final class ACosDegrees extends ACos {
        public ACosDegrees(IValue[] args, String name) throws Exception { super(args, name); }
        @Override public double get() { return Math.toDegrees(super.get()); }
    }
    public static final class ATanDegrees extends ATan {
        public ATanDegrees(IValue[] args, String name) throws Exception { super(args, name); }
        @Override public double get() { return Math.toDegrees(super.get()); }
    }
    public static final class ATan2Degrees extends ATan2 {
        public ATan2Degrees(IValue[] args, String name) throws Exception { super(args, name); }
        @Override public double get() { return Math.toDegrees(super.get()); }
    }
}
