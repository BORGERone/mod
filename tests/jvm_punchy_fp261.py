#!/usr/bin/env python3
"""Exercise direct PlayerModel hand rendering under the Build 261 first-person context."""
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tests'), str(ROOT / 'tools')]
import jvm_npc_fp_corpse256 as base

scenario_key = 'testing/NpcFpCorpse256Scenario.java'
scenario = base.OVERRIDES_256[scenario_key]
old_anim_assert = '''        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo,
            "Active full-body melee combo clip (" + attackClip + ") must NOT be overwritten by FA locomotion");
'''
new_anim_assert = '''        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo + 1,
            "Active full-body melee combo must keep its custom clip while the FA gait layer remains active");
'''
assert scenario.count(old_anim_assert) == 1
scenario = scenario.replace(old_anim_assert, new_anim_assert, 1)
gait_check = '''        ok(Math.abs(bbRightLeg.xRot) > 0.05f && Math.abs(bbLeftLeg.xRot) > 0.05f
                && bbRightLeg.xRot * bbLeftLeg.xRot < 0.0f,
            "FA gait must keep both legs moving under an active full-body melee clip");
'''
needle_gait = new_anim_assert
assert scenario.count(needle_gait) == 1
scenario = scenario.replace(needle_gait, needle_gait + gait_check, 1)
scenario = scenario.replace('EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo + 1,\n            "Finished one-shot customAnim',
                          'EMFModelPartRoot.ANIMATE_COUNT == faBeforeCombo + 2,\n            "Finished one-shot customAnim', 1)
old_talk_assert = '''        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeTalk && ItemInHandRenderer.CALLS.size() == 2,
            "Talking GuideNpc must preserve talk clip + render held items");
'''
new_talk_assert = '''        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeTalk + 1 && ItemInHandRenderer.CALLS.size() == 2,
            "Talking GuideNpc must keep FA/TnM active, layer the talk pose, and render held items");
'''
assert scenario.count(old_talk_assert) == 1
scenario = scenario.replace(old_talk_assert, new_talk_assert, 1)
hurt_needle = '''        // Talking NPC uses BbModel talk animation + held items
        int faBeforeTalk = EMFModelPartRoot.ANIMATE_COUNT;
'''
hurt_insert = '''        // Damage followed by dialogue must stay on the same Fresh/TnM pipeline.
        int faBeforeHurt = EMFModelPartRoot.ANIMATE_COUNT;
        npc.hurtTime = 5;
        ivy.render(npc, 30f, 0.5f, new PoseStack(), buf, 0xF000F0, 0);
        ok(EMFModelPartRoot.ANIMATE_COUNT == faBeforeHurt + 1,
            "Hurt GuideNpc must keep FA/TnM active while the hurt pose is applied");
        npc.hurtTime = 0;

        // Talking NPC uses BbModel talk animation + held items
        int faBeforeTalk = EMFModelPartRoot.ANIMATE_COUNT;
'''
assert scenario.count(hurt_needle) == 1
scenario = scenario.replace(hurt_needle, hurt_insert, 1)
needle = '''        // Outside PAL first-person pass (e.g. 3rd person or armor layer), AgeableListModel.renderToBuffer is NOT canceled
'''
insert = '''        // Punchy-style path: render a PlayerModel during ItemInHandRenderer's hand pass,
        // without PAL's marker or PlayerRenderer.renderRightHand / RenderArmEvent.
        FirstPersonMode.setFirstPersonPass(false);
        dev.settledlands.npc.client.FirstPersonPossession256.pushFirstPersonRenderContext(lp, buf, 0xF000F0);
        try {
            AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT = 0;
            ModelPart.RENDERS.clear();
            buf.requested.clear();
            pm.rightArm.visible = true;
            pm.leftArm.visible = false;
            pm.rightArm.setPos(-5.1f, 2.4f, -0.8f);
            pm.rightArm.setRotation(-0.75f, 0.25f, -0.1f);
            dev.settledlands.npc.client.FirstPersonPossession256.pushPlayerRenderContext(pr, lp, buf, 0xF000F0);
            try {
                pm.renderToBuffer(new PoseStack(), new com.mojang.blaze3d.vertex.VertexConsumer() {}, 0xF000F0, 0, -1);
            } finally {
                dev.settledlands.npc.client.FirstPersonPossession256.popPlayerRenderContext();
            }
            ok(AgeableListModel.STEVE_RENDER_TO_BUFFER_COUNT == 0,
                "Punchy PlayerModel first-person pass must replace Steve model hands");
            ok(ModelPart.RENDERS.size() == 1 && ModelPart.RENDERS.get(0).part == bbRightArm,
                "Punchy pass must render the possessed NPC's animated right arm");
            ModelPart.RenderRecord punchyArm = ModelPart.RENDERS.get(0);
            ok(Math.abs(punchyArm.xRot - (-0.75f)) < 1e-4f
                    && Math.abs(punchyArm.yRot - 0.25f) < 1e-4f
                    && ivy.getTextureLocation().equals(buf.requested.get(0).texture),
                "Punchy arm animation and NPC texture must survive PlayerModel retargeting");
        } finally {
            dev.settledlands.npc.client.FirstPersonPossession256.popPlayerRenderContext();
        }

        // Outside PAL first-person pass (e.g. 3rd person or armor layer), AgeableListModel.renderToBuffer is NOT canceled
'''
assert scenario.count(needle) == 1
base.OVERRIDES_256[scenario_key] = scenario.replace(needle, insert, 1)
base.OVERRIDES_256['net/minecraft/client/renderer/MultiBufferSource.java'] = '''
package net.minecraft.client.renderer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
public interface MultiBufferSource {
    VertexConsumer getBuffer(RenderType type);
    final class Recording implements MultiBufferSource {
        public final List<RenderType> requested = new ArrayList<>();
        public static final class TaggedConsumer implements VertexConsumer {
            public final RenderType renderType;
            public TaggedConsumer(RenderType rt) { this.renderType = rt; }
        }
        @Override public VertexConsumer getBuffer(RenderType type) {
            requested.add(type);
            return new TaggedConsumer(type);
        }
    }
    final class BufferSource implements MultiBufferSource {
        @Override public VertexConsumer getBuffer(RenderType type) { return new VertexConsumer() {}; }
    }
}
'''

if __name__ == '__main__':
    base.main()
