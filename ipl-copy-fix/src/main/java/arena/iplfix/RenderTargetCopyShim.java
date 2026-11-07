package arena.iplfix;

import com.mojang.blaze3d.pipeline.RenderTarget;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL45C;

import java.nio.IntBuffer;

/**
 * Format tolerant copies between two {@link RenderTarget}s.
 *
 * <p>Immersive Portals copies the main framebuffer into (and back out of) its deferred
 * portal framebuffer with {@code glCopyImageSubData} in
 * {@code qouteall.imm_ptl.core.compat.iris_compatibility.IPIrisHelper}. That call requires
 * identical internal formats for source and destination textures. When an Iris shader pack
 * (Eclipse being one of them) replaces the main framebuffer attachments, the formats no
 * longer match and every copy fails with
 * {@code GL_INVALID_OPERATION: source and destination internal formats are not compatible}
 * — several thousand times per minute. Depth is then never carried into the portal
 * compositing pass (portals render through walls) and the handoff back to the Iris final
 * pass is broken (lighting around portals collapses to unlit / sun-less).
 *
 * <p>This shim keeps the fast path when formats do match (named framebuffer blit, which the
 * driver executes as a plain copy) and falls back to legal cross-format paths otherwise:
 * <ul>
 *   <li>color: {@code glBlitNamedFramebuffer} — the spec allows conversion between
 *       color-renderable formats,</li>
 *   <li>depth: a fullscreen triangle pass writing {@code gl_FragDepth} from the source depth
 *       texture — the only portable way to move depth between different depth formats.</li>
 * </ul>
 *
 * <p>Everything is best effort: any failure disables the shim so Immersive Portals falls back
 * to its own behaviour instead of breaking rendering.
 */
public final class RenderTargetCopyShim {

    // Enum values used through raw ints where the LWJGL constant name varies between
    // GL version classes, so this class compiles against plain MC 1.21.1 / LWJGL 3.3.3.
    private static final int GL_INTERNAL_FORMAT = 0x1003;
    private static final int GL_TEXTURE_COMPARE_MODE = 0x884C;
    private static final int GL_NONE = 0x0;
    private static final int GL_CURRENT_PROGRAM = 0x8B8D;
    private static final int GL_VERTEX_ARRAY_BINDING = 0x85B5;
    private static final int GL_ACTIVE_TEXTURE = 0x84E0;
    private static final int GL_TEXTURE_BINDING_2D = 0x8069;
    private static final int GL_DEPTH_FUNC = 0x0B74;
    private static final int GL_DEPTH_WRITEMASK = 0x0B72;
    private static final int GL_COLOR_WRITEMASK = 0x0C23;
    private static final int GL_VIEWPORT = 0x0BA2;

    private static final String VERTEX_SOURCE = """
            #version 150 core
            void main() {
                float x = (gl_VertexID == 1) ? 3.0 : -1.0;
                float y = (gl_VertexID == 2) ? 3.0 : -1.0;
                gl_Position = vec4(x, y, 0.0, 1.0);
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 150 core
            uniform sampler2D uDepth;
            uniform vec2 uSize;
            void main() {
                vec2 uv = gl_FragCoord.xy / uSize;
                gl_FragDepth = texture(uDepth, uv).r;
            }
            """;

    private static boolean disabled = false;
    private static int failureCount = 0;

    private static boolean shaderTried = false;
    private static int program = 0;
    private static int vao = 0;
    private static int depthUniform = -1;
    private static int sizeUniform = -1;

    private RenderTargetCopyShim() {}

    public static boolean isActive() {
        return !disabled;
    }

    /** Color copy — always legal through a named blit with conversion. */
    public static boolean copyColor(RenderTarget from, RenderTarget to) {
        if (disabled) {
            return false;
        }
        try {
            blit(from, to, GL11C.GL_COLOR_BUFFER_BIT);
            return true;
        } catch (Throwable t) {
            noteFailure(t, "copyColor");
            return false;
        }
    }

    /**
     * Depth (+stencil) copy. Blit when the depth formats match, shader pass when they do not.
     * Stencil cannot be moved through a shader pass; it is only copied when a plain depth
     * blit is legal, which matches how the compatibility renderer uses it (its stencil tests
     * are disabled while compositing).
     */
    public static boolean copyDepthStencil(RenderTarget from, RenderTarget to, boolean copyDepth, boolean copyStencil) {
        if (disabled) {
            return false;
        }
        try {
            boolean formatsMatch = depthFormatsMatch(from, to);
            int mask = 0;
            if (formatsMatch) {
                if (copyDepth) {
                    mask |= GL11C.GL_DEPTH_BUFFER_BIT;
                }
                if (copyStencil) {
                    mask |= GL11C.GL_STENCIL_BUFFER_BIT;
                }
            }
            if (mask != 0) {
                blit(from, to, mask);
            }
            if (copyDepth && !formatsMatch) {
                if (!shaderDepthCopy(from.getDepthTextureId(), to)) {
                    return false;
                }
            }
            return true;
        } catch (Throwable t) {
            noteFailure(t, "copyDepthStencil");
            return false;
        }
    }

    private static void blit(RenderTarget from, RenderTarget to, int mask) {
        GL45C.glBlitNamedFramebuffer(
            from.frameBufferId, to.frameBufferId,
            0, 0, from.width, from.height,
            0, 0, to.width, to.height,
            mask, GL11C.GL_NEAREST
        );
    }

    private static boolean depthFormatsMatch(RenderTarget from, RenderTarget to) {
        int source = from.getDepthTextureId();
        int dest = to.getDepthTextureId();
        if (source == 0 || dest == 0) {
            return true;
        }
        return internalFormat(source) == internalFormat(dest);
    }

    private static int internalFormat(int texture) {
        return GL45C.glGetTextureLevelParameteriv(texture, 0, GL_INTERNAL_FORMAT);
    }

    private static boolean shaderDepthCopy(int sourceDepthTexture, RenderTarget dest) {
        if (sourceDepthTexture == 0 || !ensureProgram()) {
            return false;
        }

        IntBuffer previousViewport = BufferUtils.createIntBuffer(4);
        IntBuffer previousColorMask = BufferUtils.createIntBuffer(4);

        int previousMinFilter = GL11C.GL_NEAREST;
        int previousMagFilter = GL11C.GL_NEAREST;
        int previousCompareMode = GL_NONE;
        boolean sourceTextureBound = false;

        int previousProgram = GL11C.glGetInteger(GL_CURRENT_PROGRAM);
        int previousDrawFramebuffer = GL30C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousVertexArray = GL30C.glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousActiveTexture = GL13C.glGetInteger(GL_ACTIVE_TEXTURE);
        int previousTexture = GL11C.glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousDepthFunc = GL11C.glGetInteger(GL_DEPTH_FUNC);
        boolean previousDepthTest = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean previousStencilTest = GL11C.glIsEnabled(GL11C.GL_STENCIL_TEST);
        boolean previousDepthMask = GL11C.glGetBoolean(GL_DEPTH_WRITEMASK);
        GL11C.glGetIntegerv(GL_VIEWPORT, previousViewport);
        GL11C.glGetBooleanv(GL_COLOR_WRITEMASK, previousColorMask);

        try {
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, dest.frameBufferId);
            GL11C.glViewport(0, 0, dest.width, dest.height);
            GL11C.glColorMask(false, false, false, false);
            GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            GL11C.glDepthFunc(GL11C.GL_ALWAYS);
            GL11C.glDepthMask(true);
            GL11C.glDisable(GL11C.GL_STENCIL_TEST);

            GL30C.glBindVertexArray(vao);
            GL20C.glUseProgram(program);

            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, sourceDepthTexture);
            sourceTextureBound = true;
            previousMinFilter = GL11C.glGetTexParameteriv(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER);
            previousMagFilter = GL11C.glGetTexParameteriv(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER);
            previousCompareMode = GL11C.glGetTexParameteriv(GL11C.GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, GL11C.GL_NEAREST);
            GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_NONE);

            GL20C.glUniform1i(depthUniform, 0);
            GL20C.glUniform2f(sizeUniform, dest.width, dest.height);

            GL11C.glDrawArrays(GL11C.GL_TRIANGLES, 0, 3);

            GL20C.glUseProgram(0);
            return true;
        } catch (Throwable t) {
            noteFailure(t, "shaderDepthCopy");
            return false;
        } finally {
            GL30C.glBindVertexArray(previousVertexArray);
            GL13C.glActiveTexture(previousActiveTexture);
            if (sourceTextureBound) {
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, sourceDepthTexture);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, previousMinFilter);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, previousMagFilter);
                GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, previousCompareMode);
            }
            GL11C.glBindTexture(GL11C.GL_TEXTURE_2D, previousTexture);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
            GL11C.glViewport(previousViewport.get(0), previousViewport.get(1), previousViewport.get(2), previousViewport.get(3));
            GL11C.glColorMask(
                previousColorMask.get(0) != 0, previousColorMask.get(1) != 0,
                previousColorMask.get(2) != 0, previousColorMask.get(3) != 0
            );
            GL11C.glDepthFunc(previousDepthFunc);
            GL11C.glDepthMask(previousDepthMask);
            if (previousDepthTest) {
                GL11C.glEnable(GL11C.GL_DEPTH_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_DEPTH_TEST);
            }
            if (previousStencilTest) {
                GL11C.glEnable(GL11C.GL_STENCIL_TEST);
            } else {
                GL11C.glDisable(GL11C.GL_STENCIL_TEST);
            }
            GL20C.glUseProgram(previousProgram);
        }
    }

    private static boolean ensureProgram() {
        if (program != 0) {
            return true;
        }
        if (shaderTried) {
            return false;
        }
        shaderTried = true;

        int vertexShader = compile(GL20C.GL_VERTEX_SHADER, VERTEX_SOURCE);
        int fragmentShader = compile(GL20C.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE);
        if (vertexShader == 0 || fragmentShader == 0) {
            return false;
        }

        int candidate = GL20C.glCreateProgram();
        GL20C.glAttachShader(candidate, vertexShader);
        GL20C.glAttachShader(candidate, fragmentShader);
        GL20C.glLinkProgram(candidate);
        GL20C.glDetachShader(candidate, vertexShader);
        GL20C.glDetachShader(candidate, fragmentShader);
        GL20C.glDeleteShader(vertexShader);
        GL20C.glDeleteShader(fragmentShader);

        if (GL20C.glGetProgrami(candidate, GL20C.GL_LINK_STATUS) != GL11C.GL_TRUE) {
            IplCopyFixMod.LOGGER.error("Depth copy program link failed: {}", GL20C.glGetProgramInfoLog(candidate));
            GL20C.glDeleteProgram(candidate);
            return false;
        }

        depthUniform = GL20C.glGetUniformLocation(candidate, "uDepth");
        sizeUniform = GL20C.glGetUniformLocation(candidate, "uSize");
        vao = GL30C.glGenVertexArrays();
        program = candidate;
        return true;
    }

    private static int compile(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) != GL11C.GL_TRUE) {
            IplCopyFixMod.LOGGER.error("Depth copy shader compile failed: {}", GL20C.glGetShaderInfoLog(shader));
            GL20C.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void noteFailure(Throwable t, String what) {
        failureCount++;
        if (failureCount <= 5) {
            IplCopyFixMod.LOGGER.error("Copy shim failed in {}, falling back to Immersive Portals' own copy after {} failures", what, failureCount, t);
        }
        if (failureCount >= 20) {
            disabled = true;
            IplCopyFixMod.LOGGER.error("Copy shim disabled after {} failures", failureCount);
        }
    }
}
