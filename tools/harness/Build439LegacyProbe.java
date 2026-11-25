import com.pockyl.diegesis.api.OpenSide;
import com.pockyl.diegesis.api.scene.PlaceSurface;
import com.pockyl.diegesis.api.scene.PlacingOptions;
import com.pockyl.diegesis.api.state.ItemPose;
import com.pockyl.diegesis.api.state.SlotRef;
import com.pockyl.diegesis.api.state.StationState;
import com.pockyl.diegesis.impl.client.ClientStation;
import com.pockyl.diegesis.impl.client.scene.NodeInstance;
import com.pockyl.diegesis.impl.client.scene.PlacedItem;
import com.pockyl.diegesis.impl.client.scene.PlacedShape;
import com.pockyl.diegesis.impl.client.scene.SceneInstance;
import dev.settledlands.chest.ChestDiegesisCompat436;
import dev.settledlands.chest.ChestOpenWatch401;
import dev.settledlands.chest.DiegesisScatterClient439;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector2f;

/**
 * Verifies Build 439 against the reduced Diegesis 1.21.1 beta.12..15 API surface
 * (no PlaceSurface#flatLies, no Placing#leastHalf, no DiegesisClient#addBehavior):
 * scatterFreeSpot must use safe fallbacks and ChestOpenWatch401.onClientTick -> tickWatch()
 * must commit scattered poses when the station becomes focused.
 */
public final class Build439LegacyProbe {

    private static void assertTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void main(String[] args) throws Exception {
        // Should be a safe no-op without DiegesisClient#addBehavior
        DiegesisScatterClient439.registerBehavior();

        BlockPos pos = new BlockPos(50, 70, 50);
        StationState state = new StationState();
        CompoundTag sd = state.data();
        sd.putLong(ChestDiegesisCompat436.SCATTER_SEED_KEY, 0xABCDEFF01234L);
        state.setData(sd);

        List<PlaceSurface> surfaces = List.of(
                new PlaceSurface(new Vector2f(1F, 1F), new Vector2f(15F, 12F), 8F, 5F, EnumSet.noneOf(OpenSide.class)),
                new PlaceSurface(new Vector2f(1F, 1F), new Vector2f(15F, 12F), 1F, 6F, EnumSet.noneOf(OpenSide.class))
        );
        PlacingOptions opts = new PlacingOptions(4.0F, new float[]{0.5F, 1.6F}, surfaces);
        ClientStation station = new ClientStation(pos, state);
        SceneInstance scene = new SceneInstance(station, opts);
        station.scene = scene;

        Item dummy = new Item();
        for (int i = 0; i < 12; i++) {
            PlacedShape shape = (i % 2 == 0)
                    ? new PlacedShape(true, 0.5F, 0.5F, 0.04F)
                    : new PlacedShape(false, 0.45F, 0.45F, 0.45F);
            scene.placed.add(new NodeInstance(new PlacedItem(SlotRef.of("items", i), new ItemStack(dummy, 1), shape)));
        }
        scene.update(state, 0.0F);

        int yaws = 0;
        for (NodeInstance n : scene.placed) {
            ItemPose p = n.placed().pose();
            assertTrue(p != null, "legacy pose missing");
            if (Math.abs(p.rotation().y()) > 0.05F) yaws++;
        }
        assertTrue(yaws >= 4, "expected varied yaw in legacy mode, got " + yaws);

        station.focused = true;
        PacketDistributor.SENT_TO_SERVER.clear();
        for (int t = 0; t < 4; t++) {
            ChestOpenWatch401.onClientTick((net.neoforged.neoforge.client.event.ClientTickEvent.Post) null);
        }
        assertTrue(PacketDistributor.SENT_TO_SERVER.size() == 1,
                "expected tickWatch() fallback to send ScatterPoses when focused, got "
                        + PacketDistributor.SENT_TO_SERVER.size());
        System.out.println("Build439LegacyProbe: Diegesis beta.12-15 fallback + tickWatch PASS (yaws=" + yaws + ")");
    }
}
