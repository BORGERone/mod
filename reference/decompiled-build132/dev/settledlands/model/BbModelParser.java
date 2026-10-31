package dev.settledlands.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public final class BbModelParser {
    private BbModelParser() {}

    public static final class Quad {
        public final float x0, y0, z0;
        public final float x1, y1, z1;
        public final float x2, y2, z2;
        public final float x3, y3, z3;
        public final float u0, v0;
        public final float u1, v1;
        public final float u2, v2;
        public final float u3, v3;
        public final float nx, ny, nz;
        public final byte normIdx;

        public Quad(
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float u0, float v0,
            float u1, float v1,
            float u2, float v2,
            float u3, float v3,
            float nx, float ny, float nz
        ) {
            this.x0 = x0 / 16.0f; this.y0 = y0 / 16.0f; this.z0 = z0 / 16.0f;
            this.x1 = x1 / 16.0f; this.y1 = y1 / 16.0f; this.z1 = z1 / 16.0f;
            this.x2 = x2 / 16.0f; this.y2 = y2 / 16.0f; this.z2 = z2 / 16.0f;
            this.x3 = x3 / 16.0f; this.y3 = y3 / 16.0f; this.z3 = z3 / 16.0f;
            this.u0 = u0; this.v0 = v0;
            this.u1 = u1; this.v1 = v1;
            this.u2 = u2; this.v2 = v2;
            this.u3 = u3; this.v3 = v3;
            this.nx = nx; this.ny = ny; this.nz = nz;

            if (nz < -0.9f) this.normIdx = 0;       // north (0, 0, -1)
            else if (nz > 0.9f) this.normIdx = 1;  // south (0, 0, 1)
            else if (nx < -0.9f) this.normIdx = 2; // west  (-1, 0, 0)
            else if (nx > 0.9f) this.normIdx = 3;  // east  (1, 0, 0)
            else if (ny < -0.9f) this.normIdx = 4; // up    (0, -1, 0)
            else if (ny > 0.9f) this.normIdx = 5;  // down  (0, 1, 0)
            else this.normIdx = -1;
        }
    }

    public static final class BbPerFaceCube extends ModelPart.Cube {
        public final Quad[] quads;
        public final Quad[] lod1Quads;
        public final Quad[] lod2Quads;
        private final int quadCount;
        private final float[] vPos;
        private final float[] vUv;
        private final byte[] vNormIdx;
        private final float[] vArbNormals;

        private final int lod1QuadCount;
        private final float[] lod1VPos;
        private final float[] lod1VUv;
        private final byte[] lod1VNormIdx;
        private final float[] lod1VArbNormals;

        private final int lod2QuadCount;
        private final float[] lod2VPos;
        private final float[] lod2VUv;
        private final byte[] lod2VNormIdx;
        private final float[] lod2VArbNormals;

        public BbPerFaceCube(Quad[] quads) {
            this(quads, quads, quads);
        }

        public BbPerFaceCube(Quad[] quads, Quad[] lodQuads) {
            this(quads, lodQuads, lodQuads);
        }

        public BbPerFaceCube(Quad[] quads, Quad[] lod1Quads, Quad[] lod2Quads) {
            super(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, false, 64, 64, Collections.emptySet());
            this.quads = quads != null ? quads : new Quad[0];
            this.lod1Quads = lod1Quads != null ? lod1Quads : this.quads;
            this.lod2Quads = lod2Quads != null ? lod2Quads : this.lod1Quads;

            // Pack LOD 0
            this.quadCount = this.quads.length;
            this.vPos = new float[this.quadCount * 12];
            this.vUv = new float[this.quadCount * 8];
            this.vNormIdx = new byte[this.quadCount];
            boolean hasArb0 = false;
            for (Quad q : this.quads) {
                if (q.normIdx < 0) { hasArb0 = true; break; }
            }
            this.vArbNormals = hasArb0 ? new float[this.quadCount * 3] : null;
            packQuadArrays(this.quads, this.vPos, this.vUv, this.vNormIdx, this.vArbNormals, hasArb0);

            // Pack LOD 1
            this.lod1QuadCount = this.lod1Quads.length;
            this.lod1VPos = new float[this.lod1QuadCount * 12];
            this.lod1VUv = new float[this.lod1QuadCount * 8];
            this.lod1VNormIdx = new byte[this.lod1QuadCount];
            boolean hasArb1 = false;
            for (Quad q : this.lod1Quads) {
                if (q.normIdx < 0) { hasArb1 = true; break; }
            }
            this.lod1VArbNormals = hasArb1 ? new float[this.lod1QuadCount * 3] : null;
            packQuadArrays(this.lod1Quads, this.lod1VPos, this.lod1VUv, this.lod1VNormIdx, this.lod1VArbNormals, hasArb1);

            // Pack LOD 2
            this.lod2QuadCount = this.lod2Quads.length;
            this.lod2VPos = new float[this.lod2QuadCount * 12];
            this.lod2VUv = new float[this.lod2QuadCount * 8];
            this.lod2VNormIdx = new byte[this.lod2QuadCount];
            boolean hasArb2 = false;
            for (Quad q : this.lod2Quads) {
                if (q.normIdx < 0) { hasArb2 = true; break; }
            }
            this.lod2VArbNormals = hasArb2 ? new float[this.lod2QuadCount * 3] : null;
            packQuadArrays(this.lod2Quads, this.lod2VPos, this.lod2VUv, this.lod2VNormIdx, this.lod2VArbNormals, hasArb2);
        }

        private static void packQuadArrays(Quad[] src, float[] outPos, float[] outUv, byte[] outNorm, float[] outArb, boolean hasArb) {
            int pIdx = 0;
            int uvIdx = 0;
            for (int i = 0; i < src.length; i++) {
                Quad q = src[i];
                outPos[pIdx++] = q.x0; outPos[pIdx++] = q.y0; outPos[pIdx++] = q.z0;
                outPos[pIdx++] = q.x1; outPos[pIdx++] = q.y1; outPos[pIdx++] = q.z1;
                outPos[pIdx++] = q.x2; outPos[pIdx++] = q.y2; outPos[pIdx++] = q.z2;
                outPos[pIdx++] = q.x3; outPos[pIdx++] = q.y3; outPos[pIdx++] = q.z3;

                outUv[uvIdx++] = q.u0; outUv[uvIdx++] = q.v0;
                outUv[uvIdx++] = q.u1; outUv[uvIdx++] = q.v1;
                outUv[uvIdx++] = q.u2; outUv[uvIdx++] = q.v2;
                outUv[uvIdx++] = q.u3; outUv[uvIdx++] = q.v3;

                outNorm[i] = q.normIdx;
                if (hasArb && outArb != null) {
                    outArb[i * 3] = q.nx;
                    outArb[i * 3 + 1] = q.ny;
                    outArb[i * 3 + 2] = q.nz;
                }
            }
        }

        @Override
        public void compile(PoseStack.Pose pose, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
            int lod = BbModel.CURRENT_LOD_LEVEL.get();
            int count;
            float[] pArr, uvArr, arbArr;
            byte[] normArr;
            if (lod >= 2) {
                count = this.lod2QuadCount;
                if (count == 0) return;
                pArr = this.lod2VPos; uvArr = this.lod2VUv; normArr = this.lod2VNormIdx; arbArr = this.lod2VArbNormals;
            } else if (lod == 1) {
                count = this.lod1QuadCount;
                if (count == 0) return;
                pArr = this.lod1VPos; uvArr = this.lod1VUv; normArr = this.lod1VNormIdx; arbArr = this.lod1VArbNormals;
            } else {
                count = this.quadCount;
                if (count == 0) return;
                pArr = this.vPos; uvArr = this.vUv; normArr = this.vNormIdx; arbArr = this.vArbNormals;
            }

            Matrix4f mat = pose.pose();
            Matrix3f normMat = pose.normal();

            float m00 = mat.m00(), m01 = mat.m01(), m02 = mat.m02(), m03 = mat.m03();
            float m10 = mat.m10(), m11 = mat.m11(), m12 = mat.m12(), m13 = mat.m13();
            float m20 = mat.m20(), m21 = mat.m21(), m22 = mat.m22(), m23 = mat.m23();
            float m30 = mat.m30(), m31 = mat.m31(), m32 = mat.m32();

            float n00 = normMat.m00(), n01 = normMat.m01(), n02 = normMat.m02();
            float n10 = normMat.m10(), n11 = normMat.m11(), n12 = normMat.m12();
            float n20 = normMat.m20(), n21 = normMat.m21(), n22 = normMat.m22();

            // Zero-allocation cardinal normal projections in registers
            float nx0 = -n20, nx1 = n20, nx2 = -n00, nx3 = n00, nx4 = -n10, nx5 = n10;
            float ny0 = -n21, ny1 = n21, ny2 = -n01, ny3 = n01, ny4 = -n11, ny5 = n11;
            float nz0 = -n22, nz1 = n22, nz2 = -n02, nz3 = n02, nz4 = -n12, nz5 = n12;

            int pIdx = 0;
            int uvIdx = 0;
            for (int i = 0; i < count; i++) {
                float nx, ny, nz;
                byte nIdx = normArr[i];
                switch (nIdx) {
                    case 0 -> { nx = nx0; ny = ny0; nz = nz0; }
                    case 1 -> { nx = nx1; ny = ny1; nz = nz1; }
                    case 2 -> { nx = nx2; ny = ny2; nz = nz2; }
                    case 3 -> { nx = nx3; ny = ny3; nz = nz3; }
                    case 4 -> { nx = nx4; ny = ny4; nz = nz4; }
                    case 5 -> { nx = nx5; ny = ny5; nz = nz5; }
                    default -> {
                        float anX = arbArr[i * 3];
                        float anY = arbArr[i * 3 + 1];
                        float anZ = arbArr[i * 3 + 2];
                        nx = n00 * anX + n10 * anY + n20 * anZ;
                        ny = n01 * anX + n11 * anY + n21 * anZ;
                        nz = n02 * anX + n12 * anY + n22 * anZ;
                    }
                }

                float x0 = pArr[pIdx++];
                float y0 = pArr[pIdx++];
                float z0 = pArr[pIdx++];
                buffer.addVertex(
                    m00 * x0 + m10 * y0 + m20 * z0 + m30,
                    m01 * x0 + m11 * y0 + m21 * z0 + m31,
                    m02 * x0 + m12 * y0 + m22 * z0 + m32,
                    color, uvArr[uvIdx++], uvArr[uvIdx++], packedOverlay, packedLight, nx, ny, nz
                );

                float x1 = pArr[pIdx++];
                float y1 = pArr[pIdx++];
                float z1 = pArr[pIdx++];
                buffer.addVertex(
                    m00 * x1 + m10 * y1 + m20 * z1 + m30,
                    m01 * x1 + m11 * y1 + m21 * z1 + m31,
                    m02 * x1 + m12 * y1 + m22 * z1 + m32,
                    color, uvArr[uvIdx++], uvArr[uvIdx++], packedOverlay, packedLight, nx, ny, nz
                );

                float x2 = pArr[pIdx++];
                float y2 = pArr[pIdx++];
                float z2 = pArr[pIdx++];
                buffer.addVertex(
                    m00 * x2 + m10 * y2 + m20 * z2 + m30,
                    m01 * x2 + m11 * y2 + m21 * z2 + m31,
                    m02 * x2 + m12 * y2 + m22 * z2 + m32,
                    color, uvArr[uvIdx++], uvArr[uvIdx++], packedOverlay, packedLight, nx, ny, nz
                );

                float x3 = pArr[pIdx++];
                float y3 = pArr[pIdx++];
                float z3 = pArr[pIdx++];
                buffer.addVertex(
                    m00 * x3 + m10 * y3 + m20 * z3 + m30,
                    m01 * x3 + m11 * y3 + m21 * z3 + m31,
                    m02 * x3 + m12 * y3 + m22 * z3 + m32,
                    color, uvArr[uvIdx++], uvArr[uvIdx++], packedOverlay, packedLight, nx, ny, nz
                );
            }
        }
    }

    private record ElementData(
        String uuid,
        String name,
        float[] from,
        float[] to,
        int u, int v,
        float[] origin,
        float[] rotation,
        boolean hasRotation,
        Map<String, float[]> faces
    ) {}

    private static class BoneNode {
        String name;
        String uuid;
        float[] origin = new float[]{0, 0, 0};
        float[] rotation = new float[]{0, 0, 0};
        final List<ElementData> cubes = new ArrayList<>();
        final List<BoneNode> children = new ArrayList<>();
    }

    public static BbModel parse(File file) throws Exception {
        String jsonText = Files.readString(file.toPath());
        String modelName = file.getName();
        if (modelName.toLowerCase().endsWith(".bbmodel")) {
            modelName = modelName.substring(0, modelName.length() - ".bbmodel".length());
        }
        return parse(modelName, jsonText, file.getParentFile());
    }

    public static BbModel parse(String modelName, String jsonText, File parentDir) throws Exception {
        JsonObject root = JsonParser.parseString(jsonText).getAsJsonObject();

        // 1. Resolution
        int texWidth = 64;
        int texHeight = 64;
        if (root.has("resolution") && root.get("resolution").isJsonObject()) {
            JsonObject res = root.getAsJsonObject("resolution");
            if (res.has("width")) texWidth = res.get("width").getAsInt();
            if (res.has("height")) texHeight = res.get("height").getAsInt();
        }

        // 2. Elements (cubes)
        Map<String, ElementData> elementsByUuid = new HashMap<>();
        if (root.has("elements") && root.get("elements").isJsonArray()) {
            for (JsonElement elNode : root.getAsJsonArray("elements")) {
                if (!elNode.isJsonObject()) continue;
                JsonObject el = elNode.getAsJsonObject();
                String uuid = el.has("uuid") ? el.get("uuid").getAsString() : UUID.randomUUID().toString();
                String name = el.has("name") ? el.get("name").getAsString() : "cube";

                float[] from = readFloat3(el.getAsJsonArray("from"), new float[]{0, 0, 0});
                float[] to = readFloat3(el.getAsJsonArray("to"), new float[]{1, 1, 1});
                float[] elemOrigin = readFloat3(el.getAsJsonArray("origin"), new float[]{0, 0, 0});
                float[] elemRot = readFloat3(el.getAsJsonArray("rotation"), new float[]{0, 0, 0});
                boolean hasRot = Math.abs(elemRot[0]) > 1e-4f || Math.abs(elemRot[1]) > 1e-4f || Math.abs(elemRot[2]) > 1e-4f;

                int u = 0, v = 0;
                if (el.has("uv_offset") && el.get("uv_offset").isJsonArray()) {
                    JsonArray uvo = el.getAsJsonArray("uv_offset");
                    u = uvo.size() > 0 ? uvo.get(0).getAsInt() : 0;
                    v = uvo.size() > 1 ? uvo.get(1).getAsInt() : 0;
                }

                Map<String, float[]> faces = new LinkedHashMap<>();
                if (el.has("faces") && el.get("faces").isJsonObject()) {
                    JsonObject fRoot = el.getAsJsonObject("faces");
                    for (String side : new String[]{"north", "east", "south", "west", "up", "down"}) {
                        if (fRoot.has(side) && fRoot.get(side).isJsonObject()) {
                            JsonObject fObj = fRoot.getAsJsonObject(side);
                            if (fObj.has("uv") && fObj.get("uv").isJsonArray()) {
                                JsonArray uva = fObj.getAsJsonArray("uv");
                                if (uva.size() >= 4) {
                                    faces.put(side, new float[]{
                                        uva.get(0).getAsFloat(),
                                        uva.get(1).getAsFloat(),
                                        uva.get(2).getAsFloat(),
                                        uva.get(3).getAsFloat()
                                    });
                                }
                            }
                        }
                    }
                }

                elementsByUuid.put(uuid, new ElementData(uuid, name, from, to, u, v, elemOrigin, elemRot, hasRot, faces));
            }
        }

        // 3. Groups (metadata indexed by UUID for Blockbench 5.0 / GeckoLib format)
        Map<String, JsonObject> groupsByUuid = new HashMap<>();
        if (root.has("groups") && root.get("groups").isJsonArray()) {
            for (JsonElement gItem : root.getAsJsonArray("groups")) {
                if (gItem.isJsonObject()) {
                    JsonObject g = gItem.getAsJsonObject();
                    if (g.has("uuid")) {
                        groupsByUuid.put(g.get("uuid").getAsString(), g);
                    }
                }
            }
        }

        // 4. Outliner (bone hierarchy)
        Set<String> assignedCubes = new HashSet<>();
        List<BoneNode> rootBones = new ArrayList<>();
        int[] boneCounter = new int[]{1};

        if (root.has("outliner") && root.get("outliner").isJsonArray()) {
            for (JsonElement outItem : root.getAsJsonArray("outliner")) {
                if (outItem.isJsonObject()) {
                    BoneNode bone = parseBone(outItem.getAsJsonObject(), groupsByUuid, elementsByUuid, assignedCubes, boneCounter);
                    rootBones.add(bone);
                } else if (outItem.isJsonPrimitive()) {
                    String elemUuid = outItem.getAsString();
                    ElementData ed = elementsByUuid.get(elemUuid);
                    if (ed != null) {
                        BoneNode single = new BoneNode();
                        single.name = ed.name;
                        single.uuid = elemUuid;
                        single.origin = new float[]{0, 0, 0};
                        single.cubes.add(ed);
                        assignedCubes.add(elemUuid);
                        rootBones.add(single);
                    }
                }
            }
        }

        // Fallback: If no outliner or unassigned cubes remain, create a default root
        if (rootBones.isEmpty() || assignedCubes.size() < elementsByUuid.size()) {
            BoneNode fallback = new BoneNode();
            fallback.name = "root_unassigned";
            fallback.uuid = "root_unassigned";
            fallback.origin = new float[]{0, 24, 0};
            for (ElementData ed : elementsByUuid.values()) {
                if (!assignedCubes.contains(ed.uuid)) {
                    fallback.cubes.add(ed);
                }
            }
            if (!fallback.cubes.isEmpty()) {
                rootBones.add(fallback);
            }
        }

        // 5. Build ModelPart hierarchy directly with exact PerFaceCubes
        Map<String, ModelPart> partsByName = new HashMap<>();
        Map<String, float[]> restRotations = new HashMap<>();
        Map<String, float[]> restPositions = new HashMap<>();
        Map<String, ModelPart> rootChildren = new LinkedHashMap<>();
        Set<String> rootSiblingNames = new HashSet<>();

        for (BoneNode rb : rootBones) {
            ModelPart rbPart = buildModelPartHierarchy(rb, null, partsByName, restRotations, restPositions, rootSiblingNames, texWidth, texHeight);
            rootChildren.put(rb.name, rbPart);
        }

        ModelPart bakedRoot = new ModelPart(Collections.emptyList(), rootChildren);

        // 6. Textures: Extract base64 PNG or find external .png file
        ResourceLocation textureLocation = loadTexture(modelName, root, parentDir);

        // 7. Animations: Parse animation clips and keyframes
        Map<String, BbAnimation> animations = new HashMap<>();
        if (root.has("animations") && root.get("animations").isJsonArray()) {
            for (JsonElement aItem : root.getAsJsonArray("animations")) {
                if (!aItem.isJsonObject()) continue;
                JsonObject aObj = aItem.getAsJsonObject();
                String aName = aObj.has("name") ? aObj.get("name").getAsString().toLowerCase().trim() : "animation";
                float length = aObj.has("length") ? aObj.get("length").getAsFloat() : 1.0f;
                String loop = "loop";
                if (aObj.has("loop")) {
                    JsonElement lEl = aObj.get("loop");
                    if (lEl.isJsonPrimitive()) {
                        String s = lEl.getAsString().toLowerCase();
                        if (s.equals("once") || s.equals("false")) loop = "once";
                    }
                }

                Map<String, List<BbAnimation.Keyframe>> channels = new HashMap<>();
                if (aObj.has("animators") && aObj.get("animators").isJsonObject()) {
                    JsonObject animators = aObj.getAsJsonObject("animators");
                    for (var entry : animators.entrySet()) {
                        if (!entry.getValue().isJsonObject()) continue;
                        JsonObject anObj = entry.getValue().getAsJsonObject();
                        String boneName = anObj.has("name") ? anObj.get("name").getAsString().toLowerCase().trim() : "";
                        if (boneName.isEmpty()) continue;

                        if (anObj.has("keyframes") && anObj.get("keyframes").isJsonArray()) {
                            for (JsonElement kfItem : anObj.getAsJsonArray("keyframes")) {
                                if (!kfItem.isJsonObject()) continue;
                                JsonObject kf = kfItem.getAsJsonObject();
                                String channel = parseString(kf.get("channel"), "rotation").toLowerCase(Locale.ROOT);
                                float time = parseNumber(kf.get("time"));
                                String interp = parseString(kf.get("interpolation"), "linear").toLowerCase(Locale.ROOT);
                                String easing = parseString(kf.get("easing"), "");

                                float[] bezierLeftTime = readVectorHandle(kf, "bezier_left_time");
                                float[] bezierLeftValue = readVectorHandle(kf, "bezier_left_value");
                                float[] bezierRightTime = readVectorHandle(kf, "bezier_right_time");
                                float[] bezierRightValue = readVectorHandle(kf, "bezier_right_value");

                                float[] pts = readVectorPoint(kf);
                                String channelKey = boneName + "/" + channel;
                                channels.computeIfAbsent(channelKey, k -> new ArrayList<>())
                                    .add(new BbAnimation.Keyframe(
                                        time, pts[0], pts[1], pts[2],
                                        interp, easing,
                                        bezierLeftTime, bezierLeftValue,
                                        bezierRightTime, bezierRightValue
                                    ));
                            }
                        }
                    }
                }

                for (var list : channels.values()) {
                    list.sort(Comparator.comparingDouble(BbAnimation.Keyframe::time));
                }

                animations.put(aName, new BbAnimation(aName, length, loop, channels));
            }
        }

        Map<String, Set<String>> boneSubtrees = new HashMap<>();
        for (BoneNode rb : rootBones) {
            collectBoneSubtrees(rb, boneSubtrees);
        }

        return new BbModel(
            modelName,
            texWidth,
            texHeight,
            bakedRoot,
            textureLocation,
            partsByName,
            restRotations,
            restPositions,
            animations,
            boneSubtrees
        );
    }

    private static Set<String> collectBoneSubtrees(BoneNode bone, Map<String, Set<String>> map) {
        Set<String> set = new HashSet<>();
        if (bone.name != null) set.add(bone.name.toLowerCase(Locale.ROOT));
        if (bone.uuid != null) set.add(bone.uuid.toLowerCase(Locale.ROOT));
        for (BoneNode child : bone.children) {
            set.addAll(collectBoneSubtrees(child, map));
        }
        if (bone.name != null) map.put(bone.name.toLowerCase(Locale.ROOT), set);
        if (bone.uuid != null) map.put(bone.uuid.toLowerCase(Locale.ROOT), set);
        return set;
    }

    private static BoneNode parseBone(
        JsonObject node,
        Map<String, JsonObject> groupsByUuid,
        Map<String, ElementData> elements,
        Set<String> assigned,
        int[] counter
    ) {
        String uuid = node.has("uuid") ? node.get("uuid").getAsString() : UUID.randomUUID().toString();
        JsonObject meta = groupsByUuid.getOrDefault(uuid, node);

        BoneNode bone = new BoneNode();
        bone.uuid = uuid;

        if (meta.has("name") && !meta.get("name").getAsString().isBlank()) {
            bone.name = meta.get("name").getAsString().trim();
        } else if (node.has("name") && !node.get("name").getAsString().isBlank()) {
            bone.name = node.get("name").getAsString().trim();
        } else {
            bone.name = "bone_" + (counter[0]++);
        }

        bone.origin = meta.has("origin") ? readFloat3(meta.getAsJsonArray("origin"), new float[]{0, 0, 0})
            : readFloat3(node.getAsJsonArray("origin"), new float[]{0, 0, 0});
        bone.rotation = meta.has("rotation") ? readFloat3(meta.getAsJsonArray("rotation"), new float[]{0, 0, 0})
            : readFloat3(node.getAsJsonArray("rotation"), new float[]{0, 0, 0});

        if (node.has("children") && node.get("children").isJsonArray()) {
            for (JsonElement child : node.getAsJsonArray("children")) {
                if (child.isJsonObject()) {
                    bone.children.add(parseBone(child.getAsJsonObject(), groupsByUuid, elements, assigned, counter));
                } else if (child.isJsonPrimitive()) {
                    String cubeUuid = child.getAsString();
                    ElementData ed = elements.get(cubeUuid);
                    if (ed != null) {
                        bone.cubes.add(ed);
                        assigned.add(cubeUuid);
                    }
                }
            }
        }
        return bone;
    }

    private record FaceRecord(int cubeIdx, String side) {}

    private static void registerFace(
        Map<String, FaceRecord> faceMap,
        Set<String> culledFaces,
        int cubeIdx,
        String side,
        String oppositeSide,
        char axis,
        float planePos,
        float min1,
        float max1,
        float min2,
        float max2
    ) {
        String key = axis + "_" + Math.round(planePos * 100.0f) + "_" +
                     Math.round(min1 * 100.0f) + "_" + Math.round(max1 * 100.0f) + "_" +
                     Math.round(min2 * 100.0f) + "_" + Math.round(max2 * 100.0f);
        FaceRecord existing = faceMap.get(key);
        if (existing != null) {
            if (oppositeSide.equals(existing.side)) {
                culledFaces.add(cubeIdx + ":" + side);
                culledFaces.add(existing.cubeIdx + ":" + existing.side);
            }
        } else {
            faceMap.put(key, new FaceRecord(cubeIdx, side));
        }
    }

    private static ModelPart buildModelPartHierarchy(
        BoneNode bone,
        BoneNode parentBone,
        Map<String, ModelPart> partsByName,
        Map<String, float[]> restRotations,
        Map<String, float[]> restPositions,
        Set<String> siblingNames,
        int texWidth,
        int texHeight
    ) {
        String childName = bone.name;
        int dup = 2;
        while (siblingNames.contains(childName)) {
            childName = bone.name + "_" + (dup++);
        }
        siblingNames.add(childName);

        List<ModelPart.Cube> cubeList = new ArrayList<>();
        Map<String, ModelPart> childrenMap = new LinkedHashMap<>();

        int rotIdx = 0;
        List<Quad> unrotatedBoneQuads = new ArrayList<>();
        List<Quad> unrotatedBoneLod1Quads = new ArrayList<>();
        List<Quad> unrotatedBoneLod2Quads = new ArrayList<>();

        Map<String, FaceRecord> faceMap = new HashMap<>();
        Set<String> culledFaces = new HashSet<>();

        for (int i = 0; i < bone.cubes.size(); i++) {
            ElementData c = bone.cubes.get(i);
            if (c.hasRotation) continue;
            float minX = Math.min(c.from[0], c.to[0]);
            float maxX = Math.max(c.from[0], c.to[0]);
            float minY = Math.min(c.from[1], c.to[1]);
            float maxY = Math.max(c.from[1], c.to[1]);
            float minZ = Math.min(c.from[2], c.to[2]);
            float maxZ = Math.max(c.from[2], c.to[2]);

            registerFace(faceMap, culledFaces, i, "north", "south", 'Z', minZ, minX, maxX, minY, maxY);
            registerFace(faceMap, culledFaces, i, "south", "north", 'Z', maxZ, minX, maxX, minY, maxY);
            registerFace(faceMap, culledFaces, i, "west",  "east",  'X', minX, minY, maxY, minZ, maxZ);
            registerFace(faceMap, culledFaces, i, "east",  "west",  'X', maxX, minY, maxY, minZ, maxZ);
            registerFace(faceMap, culledFaces, i, "down",  "up",    'Y', minY, minX, maxX, minZ, maxZ);
            registerFace(faceMap, culledFaces, i, "up",    "down",  'Y', maxY, minX, maxX, minZ, maxZ);
        }

        for (int i = 0; i < bone.cubes.size(); i++) {
            ElementData c = bone.cubes.get(i);
            if (c.hasRotation) {
                float cDx = -(c.origin[0] - bone.origin[0]);
                float cDy = -(c.origin[1] - bone.origin[1]);
                float cDz = c.origin[2] - bone.origin[2];

                float cRx = (float) Math.toRadians(-c.rotation[0]);
                float cRy = (float) Math.toRadians(-c.rotation[1]);
                float cRz = (float) Math.toRadians(c.rotation[2]);

                float x0 = -(c.to[0] - c.origin[0]);
                float x1 = -(c.from[0] - c.origin[0]);
                float y0 = -(c.to[1] - c.origin[1]);
                float y1 = -(c.from[1] - c.origin[1]);
                float z0 = c.from[2] - c.origin[2];
                float z1 = c.to[2] - c.origin[2];

                List<Quad> quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, Collections.emptySet(), i, 0);
                List<Quad> lod1Quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, Collections.emptySet(), i, 1);
                List<Quad> lod2Quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, Collections.emptySet(), i, 2);
                if (!quads.isEmpty()) {
                    BbPerFaceCube subCube = new BbPerFaceCube(
                        quads.toArray(new Quad[0]),
                        lod1Quads.toArray(new Quad[0]),
                        lod2Quads.toArray(new Quad[0])
                    );
                    ModelPart subPart = new ModelPart(Collections.singletonList(subCube), Collections.emptyMap());
                    subPart.setPos(cDx, cDy, cDz);
                    subPart.setRotation(cRx, cRy, cRz);
                    subPart.setInitialPose(PartPose.offsetAndRotation(cDx, cDy, cDz, cRx, cRy, cRz));
                    childrenMap.put("rc_" + (rotIdx++) + "_" + c.name, subPart);
                }
            } else {
                float x0 = -(c.to[0] - bone.origin[0]);
                float x1 = -(c.from[0] - bone.origin[0]);
                float y0 = -(c.to[1] - bone.origin[1]);
                float y1 = -(c.from[1] - bone.origin[1]);
                float z0 = c.from[2] - bone.origin[2];
                float z1 = c.to[2] - bone.origin[2];

                List<Quad> quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, culledFaces, i, 0);
                List<Quad> lod1Quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, culledFaces, i, 1);
                List<Quad> lod2Quads = createQuadsForElement(c, x0, x1, y0, y1, z0, z1, texWidth, texHeight, culledFaces, i, 2);
                if (!quads.isEmpty()) {
                    unrotatedBoneQuads.addAll(quads);
                }
                if (!lod1Quads.isEmpty()) {
                    unrotatedBoneLod1Quads.addAll(lod1Quads);
                }
                if (!lod2Quads.isEmpty()) {
                    unrotatedBoneLod2Quads.addAll(lod2Quads);
                }
            }
        }

        if (!unrotatedBoneQuads.isEmpty()) {
            cubeList.add(new BbPerFaceCube(
                unrotatedBoneQuads.toArray(new Quad[0]),
                unrotatedBoneLod1Quads.toArray(new Quad[0]),
                unrotatedBoneLod2Quads.toArray(new Quad[0])
            ));
        }

        Set<String> childSiblingNames = new HashSet<>();
        for (BoneNode child : bone.children) {
            String cName = child.name;
            ModelPart childPart = buildModelPartHierarchy(child, bone, partsByName, restRotations, restPositions, childSiblingNames, texWidth, texHeight);
            childrenMap.put(cName, childPart);
        }

        ModelPart bonePart = new ModelPart(cubeList, childrenMap);

        float pX, pY, pZ, rX, rY, rZ;
        if (parentBone == null) {
            pX = -bone.origin[0];
            pY = 24.0f - bone.origin[1];
            pZ = bone.origin[2];
            rX = (float) Math.toRadians(-bone.rotation[0]);
            rY = (float) Math.toRadians(-bone.rotation[1]);
            rZ = (float) Math.toRadians(bone.rotation[2]);
        } else {
            pX = -(bone.origin[0] - parentBone.origin[0]);
            pY = -(bone.origin[1] - parentBone.origin[1]);
            pZ = bone.origin[2] - parentBone.origin[2];
            rX = (float) Math.toRadians(-bone.rotation[0]);
            rY = (float) Math.toRadians(-bone.rotation[1]);
            rZ = (float) Math.toRadians(bone.rotation[2]);
        }

        bonePart.setPos(pX, pY, pZ);
        bonePart.setRotation(rX, rY, rZ);
        bonePart.setInitialPose(PartPose.offsetAndRotation(pX, pY, pZ, rX, rY, rZ));

        partsByName.put(bone.name.toLowerCase(), bonePart);
        partsByName.put(childName.toLowerCase(), bonePart);
        if (bone.uuid != null) {
            partsByName.put(bone.uuid.toLowerCase(), bonePart);
        }
        restRotations.put(bone.name.toLowerCase(), new float[]{rX, rY, rZ});
        restRotations.put(childName.toLowerCase(), new float[]{rX, rY, rZ});
        restPositions.put(bone.name.toLowerCase(), new float[]{pX, pY, pZ});
        restPositions.put(childName.toLowerCase(), new float[]{pX, pY, pZ});

        return bonePart;
    }

    private static boolean isCavityElement(ElementData c) {
        if (c.faces != null) {
            for (float[] uva : c.faces.values()) {
                if (uva != null && uva.length >= 4) {
                    if (uva[0] >= 126.0f && uva[1] >= 126.0f && uva[2] <= 129.0f && uva[3] <= 129.0f) {
                        return true;
                    }
                }
            }
        }
        String name = c.name != null ? c.name.toLowerCase() : "";
        return name.contains("cavity") || name.contains("void") || name.contains("black") || name.contains("inside");
    }

    private static List<Quad> createQuadsForElement(
        ElementData c,
        float x0, float x1, float y0, float y1, float z0, float z1,
        int texW, int texH,
        Set<String> culledFaces,
        int cubeIdx,
        int lodLevel
    ) {
        float dx = Math.abs(c.to[0] - c.from[0]);
        float dy = Math.abs(c.to[1] - c.from[1]);
        float dz = Math.abs(c.to[2] - c.from[2]);
        float vol = dx * dy * dz;
        float maxDim = Math.max(dx, Math.max(dy, dz));
        boolean isCavity = isCavityElement(c);

        if (!isCavity) {
            if (lodLevel == 1 && vol < 1.0f && maxDim < 1.5f) {
                return Collections.emptyList();
            }
            if (lodLevel >= 2 && vol < 8.0f && maxDim < 3.0f) {
                return Collections.emptyList();
            }
        }

        List<Quad> quads = new ArrayList<>(6);
        if (c.faces == null || c.faces.isEmpty()) {
            createBoxUvQuads(c, x0, x1, y0, y1, z0, z1, texW, texH, quads, culledFaces, cubeIdx, lodLevel);
            return quads;
        }

        String elemName = c.name != null ? c.name.toLowerCase() : "";
        boolean isVoxel = (lodLevel > 0) && (elemName.contains("vxl") || elemName.contains("grid") || elemName.contains("stick") || elemName.contains("strip"));

        for (var entry : c.faces.entrySet()) {
            String side = entry.getKey().toLowerCase();
            if (culledFaces.contains(cubeIdx + ":" + side)) {
                continue;
            }

            // In LOD 1/2 of directional voxel shells, skip sub-pixel edge crevices
            if (isVoxel) {
                if (elemName.contains("north") && !side.equals("north") && !side.equals("south")) continue;
                if (elemName.contains("south") && !side.equals("north") && !side.equals("south")) continue;
                if (elemName.contains("east")  && !side.equals("east")  && !side.equals("west"))  continue;
                if (elemName.contains("west")  && !side.equals("east")  && !side.equals("west"))  continue;
                if (elemName.contains("up")    && !side.equals("up")    && !side.equals("down"))  continue;
                if (elemName.contains("down")  && !side.equals("up")    && !side.equals("down"))  continue;
            }

            float[] uva = entry.getValue();
            if (uva == null || uva.length < 4) continue;
            float u1 = uva[0], v1 = uva[1], u2 = uva[2], v2 = uva[3];

            // Protect internal black cavity UV [127, 127, 128, 128]
            boolean isCavityUv = (u1 >= 126.0f && v1 >= 126.0f && u2 <= 129.0f && v2 <= 129.0f);

            // Skip dummy invisible swatches [0, 0, 1, 1] or empty [0, 0, 0, 0], but never cavity
            if (!isCavityUv && Math.abs(u1) < 1e-3f && Math.abs(v1) < 1e-3f && u2 <= 1.01f && v2 <= 1.01f) {
                continue;
            }

            float uA = u1 / (float) texW;
            float vA = v1 / (float) texH;
            float uB = u2 / (float) texW;
            float vB = v2 / (float) texH;

            switch (side) {
                case "north" -> quads.add(new Quad(
                    x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0,
                    uB, vA, uA, vA, uA, vB, uB, vB, 0, 0, -1
                ));
                case "south" -> quads.add(new Quad(
                    x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1,
                    uB, vA, uA, vA, uA, vB, uB, vB, 0, 0, 1
                ));
                case "east" -> quads.add(new Quad(
                    x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0,
                    uB, vA, uA, vA, uA, vB, uB, vB, -1, 0, 0
                ));
                case "west" -> quads.add(new Quad(
                    x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1,
                    uB, vA, uA, vA, uA, vB, uB, vB, 1, 0, 0
                ));
                case "up" -> quads.add(new Quad(
                    x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0,
                    uB, vA, uA, vA, uA, vB, uB, vB, 0, -1, 0
                ));
                case "down" -> quads.add(new Quad(
                    x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1,
                    uB, vA, uA, vA, uA, vB, uB, vB, 0, 1, 0
                ));
            }
        }
        return quads;
    }

    private static void createBoxUvQuads(
        ElementData c,
        float x0, float x1, float y0, float y1, float z0, float z1,
        int texW, int texH,
        List<Quad> quads,
        Set<String> culledFaces,
        int cubeIdx,
        int lodLevel
    ) {
        float dx = Math.abs(c.to[0] - c.from[0]);
        float dy = Math.abs(c.to[1] - c.from[1]);
        float dz = Math.abs(c.to[2] - c.from[2]);
        float vol = dx * dy * dz;
        float maxDim = Math.max(dx, Math.max(dy, dz));
        boolean isCavity = isCavityElement(c);

        if (!isCavity) {
            if (lodLevel == 1 && vol < 1.0f && maxDim < 1.5f) return;
            if (lodLevel >= 2 && vol < 8.0f && maxDim < 3.0f) return;
        }

        float w = Math.abs(x1 - x0);
        float h = Math.abs(y1 - y0);
        float d = Math.abs(z1 - z0);
        float u = c.u, v = c.v;

        if (!culledFaces.contains(cubeIdx + ":north")) {
            // NORTH (z0): u+d, v+d to u+d+w, v+d+h
            float u1 = (u + d) / (float) texW, v1 = (v + d) / (float) texH;
            float u2 = (u + d + w) / (float) texW, v2 = (v + d + h) / (float) texH;
            quads.add(new Quad(x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u2, v1, u1, v1, u1, v2, u2, v2, 0, 0, -1));
        }

        if (!culledFaces.contains(cubeIdx + ":south")) {
            // SOUTH (z1): u+2d+w, v+d to u+2d+2w, v+d+h
            float u1 = (u + 2*d + w) / (float) texW; float u2 = (u + 2*d + 2*w) / (float) texW;
            float v1 = (v + d) / (float) texH; float v2 = (v + d + h) / (float) texH;
            quads.add(new Quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u2, v1, u1, v1, u1, v2, u2, v2, 0, 0, 1));
        }

        if (!culledFaces.contains(cubeIdx + ":east")) {
            // EAST (x0): u, v+d to u+d, v+d+h
            float u1 = u / (float) texW; float u2 = (u + d) / (float) texW;
            float v1 = (v + d) / (float) texH; float v2 = (v + d + h) / (float) texH;
            quads.add(new Quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u2, v1, u1, v1, u1, v2, u2, v2, -1, 0, 0));
        }

        if (!culledFaces.contains(cubeIdx + ":west")) {
            // WEST (x1): u+d+w, v+d to u+2d+w, v+d+h
            float u1 = (u + d + w) / (float) texW; float u2 = (u + 2*d + w) / (float) texW;
            float v1 = (v + d) / (float) texH; float v2 = (v + d + h) / (float) texH;
            quads.add(new Quad(x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u2, v1, u1, v1, u1, v2, u2, v2, 1, 0, 0));
        }

        if (!culledFaces.contains(cubeIdx + ":up")) {
            // UP (y0): u+d, v to u+d+w, v+d
            float u1 = (u + d) / (float) texW; float u2 = (u + d + w) / (float) texW;
            float v1 = v / (float) texH; float v2 = (v + d) / (float) texH;
            quads.add(new Quad(x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0, u2, v1, u1, v1, u1, v2, u2, v2, 0, -1, 0));
        }

        if (!culledFaces.contains(cubeIdx + ":down")) {
            // DOWN (y1): u+d+w, v to u+2d+w, v+d
            float u1 = (u + d + w) / (float) texW; float u2 = (u + 2*d + w) / (float) texW;
            float v1 = v / (float) texH; float v2 = (v + d) / (float) texH;
            quads.add(new Quad(x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1, u2, v1, u1, v1, u1, v2, u2, v2, 0, 1, 0));
        }
    }

    private static ResourceLocation loadTexture(String modelName, JsonObject root, File parentDir) {
        String cleanName = modelName.replaceAll("[^a-zA-Z0-9_.-]", "_").toLowerCase();
        ResourceLocation dynLoc = ResourceLocation.fromNamespaceAndPath("settledlands", "bbmodel/" + cleanName);

        byte[] pngBytes = null;

        // 1. Try embedded base64 in textures array
        if (root.has("textures") && root.get("textures").isJsonArray()) {
            for (JsonElement tItem : root.getAsJsonArray("textures")) {
                if (!tItem.isJsonObject()) continue;
                JsonObject tObj = tItem.getAsJsonObject();
                if (tObj.has("source") && tObj.get("source").isJsonPrimitive()) {
                    String src = tObj.get("source").getAsString();
                    int commaIdx = src.indexOf("base64,");
                    if (commaIdx >= 0) {
                        try {
                            String b64 = src.substring(commaIdx + 7).trim();
                            pngBytes = Base64.getDecoder().decode(b64);
                            break;
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }

        // 2. Try external .png file in same folder
        if (pngBytes == null && parentDir != null) {
            File extPng = new File(parentDir, modelName + ".png");
            if (extPng.exists()) {
                try {
                    pngBytes = Files.readAllBytes(extPng.toPath());
                } catch (Throwable ignored) {}
            }
        }

        // 3. Register DynamicTexture in Minecraft TextureManager if client is active
        if (pngBytes != null) {
            try {
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getTextureManager() != null) {
                    try (InputStream is = new ByteArrayInputStream(pngBytes)) {
                        NativeImage img = NativeImage.read(is);
                        DynamicTexture dynTex = new DynamicTexture(img);
                        mc.getTextureManager().register(dynLoc, dynTex);
                        return dynLoc;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return ResourceLocation.fromNamespaceAndPath("settledlands", "textures/entity/guide_npc.png");
    }

    private static float[] readFloat3(JsonArray arr, float[] dflt) {
        if (arr == null || arr.size() < 3) return dflt;
        return new float[]{
            arr.get(0).getAsFloat(),
            arr.get(1).getAsFloat(),
            arr.get(2).getAsFloat()
        };
    }

    private static float[] readVectorPoint(JsonObject kf) {
        float x = 0.0f, y = 0.0f, z = 0.0f;
        if (kf.has("data_points") && kf.get("data_points").isJsonArray()) {
            JsonArray pts = kf.getAsJsonArray("data_points");
            if (pts.size() > 0) {
                JsonElement first = pts.get(0);
                if (first != null && first.isJsonObject()) {
                    JsonObject pt = first.getAsJsonObject();
                    x = parseNumber(pt.get("x"));
                    y = parseNumber(pt.get("y"));
                    z = parseNumber(pt.get("z"));
                } else if (first != null && first.isJsonPrimitive()) {
                    x = parseNumber(pts.size() > 0 ? pts.get(0) : null);
                    y = parseNumber(pts.size() > 1 ? pts.get(1) : null);
                    z = parseNumber(pts.size() > 2 ? pts.get(2) : null);
                }
            }
        }
        return new float[]{x, y, z};
    }

    private static float[] readVectorHandle(JsonObject kf, String propName) {
        if (!kf.has(propName)) return null;
        JsonElement el = kf.get(propName);
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonPrimitive()) {
            float v = parseNumber(el);
            return new float[]{v, v, v};
        }
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() == 0) return null;
            if (arr.get(0).isJsonObject()) {
                JsonObject pt = arr.get(0).getAsJsonObject();
                return new float[]{parseNumber(pt.get("x")), parseNumber(pt.get("y")), parseNumber(pt.get("z"))};
            }
            float x = arr.size() > 0 ? parseNumber(arr.get(0)) : 0.0f;
            float y = arr.size() > 1 ? parseNumber(arr.get(1)) : x;
            float z = arr.size() > 2 ? parseNumber(arr.get(2)) : x;
            return new float[]{x, y, z};
        }
        if (el.isJsonObject()) {
            JsonObject pt = el.getAsJsonObject();
            return new float[]{parseNumber(pt.get("x")), parseNumber(pt.get("y")), parseNumber(pt.get("z"))};
        }
        return null;
    }

    private static String parseString(JsonElement el, String fallback) {
        if (el == null || el.isJsonNull()) return fallback;
        try {
            if (el.isJsonPrimitive()) {
                return el.getAsString().trim();
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    private static float parseNumber(JsonElement el) {
        if (el == null || el.isJsonNull()) return 0.0f;
        try {
            if (el.isJsonPrimitive()) {
                com.google.gson.JsonPrimitive p = el.getAsJsonPrimitive();
                if (p.isNumber()) return p.getAsFloat();
                if (p.isString()) return Float.parseFloat(p.getAsString().trim());
            }
        } catch (Throwable ignored) {}
        return 0.0f;
    }
}
