package dev.settledlands.npc.client;

import dev.settledlands.model.BbModelStorage;
import dev.settledlands.npc.NpcProfessionSkin264;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lazily creates one texture-swapped model file per selected profession/variant. */
public final class NpcProfessionModel264 {
    private static final String PREFIX = "ivy_oleander_prof264_p";
    private static final String BASE = "ivy_oleander_professions.bbmodel";
    private static final String RESOURCE = "/assets/settledlands/dragon_default/ivy_oleander_professions.bbmodel";
    private static final Pattern SOURCE = Pattern.compile("\\\"source\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
    private static final Pattern TEXTURES = Pattern.compile("\\\"textures\\\"\\s*:");

    private NpcProfessionModel264() {}

    public static File materialize(String requestedName) {
        if (requestedName == null || !requestedName.startsWith(PREFIX) || !requestedName.endsWith(".bbmodel")) return null;
        try {
            int p = requestedName.indexOf("_m", PREFIX.length());
            int j = requestedName.indexOf("_j", p + 2);
            if (p < 0 || j < 0) return null;
            int profession = Integer.parseInt(requestedName.substring(PREFIX.length(), p));
            String maleText = requestedName.substring(p + 2, j);
            String jpText = requestedName.substring(j + 2, requestedName.length() - ".bbmodel".length());
            if (profession < 1 || profession > 16 || !("0".equals(maleText) || "1".equals(maleText))
                    || !("0".equals(jpText) || "1".equals(jpText))) return null;
            boolean male = "1".equals(maleText);
            boolean japanese = "1".equals(jpText);

            Path dir = BbModelStorage.getModelsDir();
            if (dir == null) return null;
            Files.createDirectories(dir);
            Path variant = dir.resolve(requestedName);
            if (Files.isRegularFile(variant)) return variant.toFile();

            Path base = dir.resolve(BASE);
            if (!Files.isRegularFile(base)) installBaseAsset(base);
            if (!Files.isRegularFile(base)) return null;
            String json = Files.readString(base);
            String selectedName = NpcProfessionSkin264.textureName(profession, male, japanese);
            int texturesStart = textureArrayStart(json);
            if (texturesStart < 0) return base.toFile();
            int firstStart = nextObjectStart(json, texturesStart);
            int firstEnd = matchingObjectEnd(json, firstStart);
            if (firstStart < 0 || firstEnd < 0) return base.toFile();
            String selectedSource = sourceForTexture(json, texturesStart, selectedName);
            if (selectedSource == null) return base.toFile();
            String first = json.substring(firstStart, firstEnd);
            Matcher source = SOURCE.matcher(first);
            if (!source.find()) return base.toFile();
            String replacement = first.substring(0, source.start(1)) + selectedSource + first.substring(source.end(1));
            int texturesEnd = matchingArrayEnd(json, texturesStart - 1);
            if (texturesEnd < 0) return base.toFile();
            // BbModelParser.loadTexture consumes the first embedded source only. Keep the full source
            // asset in the JAR, but omit the other profession PNGs from generated per-variant files.
            String rendered = json.substring(0, texturesStart) + replacement + "]" + json.substring(texturesEnd + 1);
            Files.writeString(variant, rendered);
            return variant.toFile();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static int textureArrayStart(String json) {
        Matcher key = TEXTURES.matcher(json);
        if (!key.find()) return -1;
        int p = key.end();
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        return p < json.length() && json.charAt(p) == '[' ? p + 1 : -1;
    }

    private static int nextObjectStart(String json, int from) {
        int p = from;
        while (p >= 0 && p < json.length() && json.charAt(p) != '{' && json.charAt(p) != ']') p++;
        return p >= 0 && p < json.length() && json.charAt(p) == '{' ? p : -1;
    }

    private static int matchingObjectEnd(String json, int start) {
        if (start < 0 || start >= json.length() || json.charAt(start) != '{') return -1;
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '\"') quoted = false;
            } else if (c == '\"') quoted = true;
            else if (c == '{') depth++;
            else if (c == '}' && --depth == 0) return i + 1;
        }
        return -1;
    }

    private static int matchingArrayEnd(String json, int start) {
        if (start < 0 || start >= json.length() || json.charAt(start) != '[') return -1;
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '\"') quoted = false;
            } else if (c == '\"') quoted = true;
            else if (c == '[') depth++;
            else if (c == ']' && --depth == 0) return i;
        }
        return -1;
    }

    private static String sourceForTexture(String json, int arrayStart, String wantedName) {
        int p = arrayStart;
        while ((p = nextObjectStart(json, p)) >= 0) {
            int end = matchingObjectEnd(json, p);
            if (end < 0) return null;
            String object = json.substring(p, end);
            if (object.contains("\"name\"")) {
                Matcher name = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").matcher(object);
                if (name.find() && wantedName.equals(name.group(1))) {
                    Matcher source = SOURCE.matcher(object);
                    return source.find() ? source.group(1) : null;
                }
            }
            p = end;
            while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
            if (p >= json.length() || json.charAt(p) == ']') return null;
        }
        return null;
    }

    private static void installBaseAsset(Path target) {
        try (InputStream in = NpcProfessionModel264.class.getResourceAsStream(RESOURCE)) {
            if (in != null) Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Throwable ignored) {
        }
    }
}
