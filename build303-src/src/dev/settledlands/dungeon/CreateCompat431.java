package dev.settledlands.dungeon;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Build 431: optional reflection bridge to Create ({@code mc1.21.1}).
 *
 * <p>All Create and Minecraft {@code Rotation}/{@code Mirror}/{@code Direction$Axis} lookups use
 * cached {@code Class.forName} handles so Settled Lands compiles cleanly without a compile-time
 * Create dependency, passes strict Phase-D API checks, and silently no-ops when Create is absent.</p>
 */
public final class CreateCompat431 {

    private static final Logger LOG = LoggerFactory.getLogger("settledlands");

    private static volatile boolean initialized;
    private static volatile boolean available;

    private static Class<?> clsStructureTransform;
    private static Constructor<?> ctorStructureTransform;
    private static Method mTransformApplyState;
    private static Object axisY;
    private static Object mirrorNone;
    private static Object[] rotations;

    private static Class<?> clsSmartBe;
    private static Method mSmartSendData;
    private static Class<?> clsKineticBe;
    private static Method mKineticAttach;
    private static Field fKineticPreventSpeedUpdate;
    private static Field fKineticUpdateSpeed;
    private static Class<?> clsGenKineticBe;
    private static Method mGenUpdateRotation;
    private static Field fGenReActivateSource;
    private static Class<?> clsWindmillBe;
    private static Class<?> clsBeltBe;
    private static Method mBeltGetController;
    private static Field fBeltLength;
    private static Class<?> clsBeltBlock;
    private static Method mBeltInitBelt;
    private static Class<?> clsFluidTankBe;
    private static Method mTankUpdateConn;
    private static Class<?> clsItemVaultBe;
    private static Method mVaultUpdateConn;

    private CreateCompat431() {}

    public static boolean isCreateAvailable() {
        if (!initialized) {
            synchronized (CreateCompat431.class) {
                if (!initialized) {
                    init();
                    initialized = true;
                }
            }
        }
        return available;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void init() {
        try {
            ClassLoader cl = CreateCompat431.class.getClassLoader();
            clsStructureTransform = Class.forName(
                    "com.simibubi.create.content.contraptions.StructureTransform", false, cl);
            Class<?> clsAxis = Class.forName("net.minecraft.core.Direction$Axis", false, cl);
            Class<?> clsRotation = Class.forName("net.minecraft.world.level.block.Rotation", false, cl);
            Class<?> clsMirror = Class.forName("net.minecraft.world.level.block.Mirror", false, cl);

            axisY = Enum.valueOf((Class) clsAxis, "Y");
            mirrorNone = Enum.valueOf((Class) clsMirror, "NONE");
            rotations = new Object[] {
                    Enum.valueOf((Class) clsRotation, "NONE"),
                    Enum.valueOf((Class) clsRotation, "CLOCKWISE_90"),
                    Enum.valueOf((Class) clsRotation, "CLOCKWISE_180"),
                    Enum.valueOf((Class) clsRotation, "COUNTERCLOCKWISE_90")
            };
            ctorStructureTransform = clsStructureTransform.getConstructor(
                    BlockPos.class, clsAxis, clsRotation, clsMirror);
            mTransformApplyState = clsStructureTransform.getMethod("apply", BlockState.class);

            clsSmartBe = loadOpt("com.simibubi.create.foundation.blockEntity.SmartBlockEntity", cl);
            mSmartSendData = methodOpt(clsSmartBe, "sendData");

            clsKineticBe = loadOpt("com.simibubi.create.content.kinetics.base.KineticBlockEntity", cl);
            mKineticAttach = methodOpt(clsKineticBe, "attachKinetics");
            fKineticPreventSpeedUpdate = fieldOpt(clsKineticBe, "preventSpeedUpdate");
            fKineticUpdateSpeed = fieldOpt(clsKineticBe, "updateSpeed");

            clsGenKineticBe = loadOpt("com.simibubi.create.content.kinetics.base.GeneratingKineticBlockEntity", cl);
            mGenUpdateRotation = methodOpt(clsGenKineticBe, "updateGeneratedRotation");
            fGenReActivateSource = fieldOpt(clsGenKineticBe, "reActivateSource");

            clsWindmillBe = loadOpt("com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity", cl);

            clsBeltBe = loadOpt("com.simibubi.create.content.kinetics.belt.BeltBlockEntity", cl);
            mBeltGetController = methodOpt(clsBeltBe, "getController");
            fBeltLength = fieldOpt(clsBeltBe, "beltLength");

            clsBeltBlock = loadOpt("com.simibubi.create.content.kinetics.belt.BeltBlock", cl);
            mBeltInitBelt = methodOpt(clsBeltBlock, "initBelt", Level.class, BlockPos.class);

            clsFluidTankBe = loadOpt("com.simibubi.create.content.fluids.tank.FluidTankBlockEntity", cl);
            mTankUpdateConn = methodOpt(clsFluidTankBe, "updateConnectivity");

            clsItemVaultBe = loadOpt("com.simibubi.create.content.logistics.vault.ItemVaultBlockEntity", cl);
            mVaultUpdateConn = methodOpt(clsItemVaultBe, "updateConnectivity");

            available = true;
        } catch (Throwable ignored) {
            available = false;
        }
    }

    private static Class<?> loadOpt(String name, ClassLoader cl) {
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method methodOpt(Class<?> cls, String name, Class<?>... params) {
        if (cls == null) return null;
        try {
            Method m = cls.getMethod(name, params);
            m.setAccessible(true);
            return m;
        } catch (Throwable ignored) {
            try {
                Method m = cls.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static Field fieldOpt(Class<?> cls, String name) {
        if (cls == null) return null;
        try {
            Field f = cls.getField(name);
            f.setAccessible(true);
            return f;
        } catch (Throwable ignored) {
            try {
                Field f = cls.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    /**
     * Rotates an unrotated {@link BlockState} around the Y axis using Create's native
     * {@code StructureTransform#apply(BlockState)} when Create is present, returning {@code null}
     * on non-Create environments or unsupported transforms so callers fall back to property rotation.
     */
    public static BlockState transformBlockState(BlockState unrotatedState, int turns) {
        int t = turns & 3;
        if (t == 0 || unrotatedState == null || !isCreateAvailable()) {
            return unrotatedState;
        }
        try {
            Object st = ctorStructureTransform.newInstance(
                    new BlockPos(0, 0, 0), axisY, rotations[t], mirrorNone);
            Object res = mTransformApplyState.invoke(st, unrotatedState);
            if (res instanceof BlockState bs) {
                return bs;
            }
        } catch (Throwable ex) {
            LOG.warn("settledlands: Create StructureTransform fallback for state: " + ex.getMessage());
        }
        return null;
    }

    public static boolean isCreateBlockEntity(BlockEntity be, String rawId) {
        if (rawId != null && rawId.startsWith("create:")) {
            return true;
        }
        if (be == null || !isCreateAvailable()) {
            return false;
        }
        if (clsSmartBe != null && clsSmartBe.isInstance(be)) {
            return true;
        }
        return be.getClass().getName().startsWith("com.simibubi.create.");
    }

    /**
     * Post-placement activation hook for Create block entities within a single completed room.
     *
     * <p>Runs after all blocks, block entities, and {@code create:super_glue} entities in the room
     * are in place so belts initialize complete chains, kinetic networks propagate immediately,
     * generators re-activate, and client states sync via {@link Level#sendBlockUpdated}.</p>
     */
    public static void activateCreateRoom(ServerLevel level, List<BlockPos> createPositions) {
        if (level == null || createPositions == null || createPositions.isEmpty()) return;
        boolean createLoaded = isCreateAvailable();
        Level lvl = (Level) level;

        // Pass 1: initialize belts missing a controller and attach non-generator kinetics
        for (BlockPos pos : createPositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            if (createLoaded) {
                if (clsBeltBe != null && clsBeltBe.isInstance(be)) {
                    try {
                        Object ctrl = mBeltGetController != null ? mBeltGetController.invoke(be) : null;
                        int beltLen = fBeltLength != null ? fBeltLength.getInt(be) : 0;
                        boolean validCtrl = ctrl instanceof BlockPos cp && level.getBlockEntity(cp) != null;
                        if ((ctrl == null || beltLen <= 0 || !validCtrl) && mBeltInitBelt != null) {
                            mBeltInitBelt.invoke(null, lvl, pos);
                            be = level.getBlockEntity(pos);
                            if (be == null) continue;
                        }
                    } catch (Throwable ex) {
                        LOG.warn("settledlands: BeltBlock.initBelt failed at " + pos + ": " + ex.getMessage());
                    }
                }
                if (clsFluidTankBe != null && clsFluidTankBe.isInstance(be) && mTankUpdateConn != null) {
                    try {
                        mTankUpdateConn.invoke(be);
                    } catch (Throwable ignored) {
                    }
                }
                if (clsItemVaultBe != null && clsItemVaultBe.isInstance(be) && mVaultUpdateConn != null) {
                    try {
                        mVaultUpdateConn.invoke(be);
                    } catch (Throwable ignored) {
                    }
                }
                if (clsKineticBe != null && clsKineticBe.isInstance(be)
                        && (clsGenKineticBe == null || !clsGenKineticBe.isInstance(be))) {
                    try {
                        if (fKineticPreventSpeedUpdate != null) fKineticPreventSpeedUpdate.setInt(be, 0);
                        if (fKineticUpdateSpeed != null) fKineticUpdateSpeed.setBoolean(be, true);
                        if (mKineticAttach != null) mKineticAttach.invoke(be);
                    } catch (Throwable ex) {
                        LOG.warn("settledlands: attachKinetics failed at " + pos + ": " + ex.getMessage());
                    }
                }
            }
        }

        // Pass 2: re-activate kinetic generators (except WindmillBearingBlockEntity which queues assembly in tick)
        if (createLoaded && clsGenKineticBe != null) {
            for (BlockPos pos : createPositions) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be == null || !clsGenKineticBe.isInstance(be)) continue;
                if (clsWindmillBe != null && clsWindmillBe.isInstance(be)) continue;
                try {
                    if (fGenReActivateSource != null) {
                        fGenReActivateSource.setBoolean(be, true);
                    }
                    if (mGenUpdateRotation != null) {
                        mGenUpdateRotation.invoke(be);
                    }
                } catch (Throwable ex) {
                    LOG.warn("settledlands: generator wake failed at " + pos + ": " + ex.getMessage());
                }
            }
        }

        // Pass 3: push SmartBlockEntity data & block updates to clients
        for (BlockPos pos : createPositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            if (createLoaded && clsSmartBe != null && clsSmartBe.isInstance(be) && mSmartSendData != null) {
                try {
                    mSmartSendData.invoke(be);
                } catch (Throwable ignored) {
                }
            }
            try {
                be.setChanged();
                BlockState st = lvl.getBlockState(pos);
                if (st != null) {
                    level.sendBlockUpdated(pos, st, st, 2);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
