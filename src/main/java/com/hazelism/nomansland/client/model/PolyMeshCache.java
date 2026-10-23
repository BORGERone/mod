package com.hazelism.nomansland.client.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hazelism.nomansland.NoMansLand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Loads the Bedrock poly_mesh sidecars produced by tools/convert_bedrock_geo.py
 * (assets/nomansland/polymesh/&lt;model&gt;.json) and pre-bakes them into flat
 * float arrays, so the per-frame render path does zero parsing and zero allocation.
 *
 * <p>Layout per bone: quads padded to 4 vertices, stride 8 floats per vertex:
 * {@code x, y, z, nx, ny, nz, u, v} - already converted from Bedrock model space
 * to GeckoLib/Java model space (-x mirror, /16) at load time.
 *
 * <p>Client-only. Reloaded on resource reload (F3+T) via a reload listener.
 */
public final class PolyMeshCache {
    private static final int STRIDE = 8;
    private static final Map<String, Map<String, float[]>> MODELS = new HashMap<>();

    /** (Re)load one model's sidecar. Called from the client reload listener; not on the render hot path. */
    public static void reload(ResourceManager resources, String modelName) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NoMansLand.MOD_ID, "polymesh/" + modelName + ".json");
        Optional<Resource> resource = resources.getResource(id);
        if (resource.isEmpty()) {
            NoMansLand.LOGGER.warn("PolyMesh sidecar not found: {}", id);
            MODELS.remove(modelName);
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            float texW = root.get("texture_width").getAsFloat();
            float texH = root.get("texture_height").getAsFloat();
            Map<String, float[]> bones = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("bones").entrySet()) {
                bones.put(entry.getKey(), bake(entry.getValue().getAsJsonObject(), texW, texH));
            }
            MODELS.put(modelName, bones);
            NoMansLand.LOGGER.debug("Loaded polymesh {} ({} bones)", modelName, bones.size());
        } catch (Exception e) {
            NoMansLand.LOGGER.error("Failed to load polymesh sidecar {}", id, e);
            MODELS.remove(modelName);
        }
    }

    /** @return baked vertex data for the bone, or null if the bone has no poly_mesh. Render-thread read only. */
    public static float[] getBone(String modelName, String boneName) {
        Map<String, float[]> model = MODELS.get(modelName);
        return model == null ? null : model.get(boneName);
    }

    public static int stride() {
        return STRIDE;
    }

    private static float[] bake(JsonObject mesh, float texW, float texH) {
        boolean normalized = mesh.has("normalized_uvs") && mesh.get("normalized_uvs").getAsBoolean();
        JsonArray positions = mesh.getAsJsonArray("positions");
        JsonArray normals = mesh.getAsJsonArray("normals");
        JsonArray uvs = mesh.getAsJsonArray("uvs");
        JsonArray polys = mesh.getAsJsonArray("polys");

        float[] out = new float[polys.size() * 4 * STRIDE];
        int o = 0;
        for (JsonElement polyEl : polys) {
            JsonArray poly = polyEl.getAsJsonArray();
            int verts = Math.min(poly.size(), 4);
            for (int v = 0; v < 4; v++) {
                // Triangles are padded by repeating the last vertex (degenerate quad, invisible).
                JsonArray idx = poly.get(Math.min(v, verts - 1)).getAsJsonArray();
                JsonArray p = positions.get(idx.get(0).getAsInt()).getAsJsonArray();
                JsonArray n = normals.get(idx.get(1).getAsInt()).getAsJsonArray();
                JsonArray t = uvs.get(idx.get(2).getAsInt()).getAsJsonArray();
                // Bedrock -> Java model space: mirror X, pixels -> blocks. Normals mirror the same way.
                out[o++] = -p.get(0).getAsFloat() / 16.0F;
                out[o++] = p.get(1).getAsFloat() / 16.0F;
                out[o++] = p.get(2).getAsFloat() / 16.0F;
                out[o++] = -n.get(0).getAsFloat();
                out[o++] = n.get(1).getAsFloat();
                out[o++] = n.get(2).getAsFloat();
                float u = t.get(0).getAsFloat();
                float vCoord = t.get(1).getAsFloat();
                out[o++] = normalized ? u : u / texW;
                out[o++] = normalized ? vCoord : vCoord / texH;
            }
        }
        return out;
    }

    private PolyMeshCache() {}
}
