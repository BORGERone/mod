#!/usr/bin/env python3
"""End-to-end JVM 21 verification of Build 252 first-person HnS sword swings.

Executes genuine bytecode from:
  - SettledLands JAR (`PlayerBodyRenderer`, `FirstPersonSwordBridge252`, `FirstPersonPalEmfAccess252`)
  - PAL 1.1.6 (`PlayerAnimManager`, `PlayerAnimationProcessor`, `PlayerAnimationController`,
    `PlayerAnimatorLoader`, `PlayerModelMixin`, `PlayerRendererMixin`, `ItemInHandLayerMixin`,
    `RenderUtil`, `FirstPersonMode`)
  - Hackers and Slashers 2.0-beta2.5-bettercombat-test (`player_animations/*.json`,
    `BladeTracker`, `TrailSystem`, `distancePointToAABB`)
  - Entity Model Features 3.3.12 (`EMFModelPartRoot`, `EMFAnimationApi`)
  - Entity Texture Features 7.2.6
  - EMF Compat Core 2.3.2 (`EMFCompatCore`, `EMFCompatAnimationHook`, `CrouchNormalizer`,
    `PoseSnapshot`, `FirstPersonModelCompat`, `EMFCompatConfig`)
  - Touch'n Motion 1.0.4 (`TouchNMotionHook`, `ModelSpace`)
  - Gson 2.10.1 (compiled from official source archive) + JOML 1.10.5

Minecraft/NeoForge client classes and individual TnM feature providers are test doubles;
this is NOT a full Minecraft client, Mixin runtime, or visual test. No external GPL/ARR
JARs are committed or packaged.
"""
import argparse, hashlib, os, pathlib, shutil, subprocess, sys, tarfile, tempfile, zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path[:0] = [str(ROOT / 'tools'), str(ROOT / 'tests')]
import build_joml248
from jvm_fresh_motion250 import merge
from jvm_real_tnm104_249 import generate, MANUAL_SRC

SHA = {
    'pal': 'e41ee06000fc95444a482018ec24b3f1e79eb111ca6b8ca119e61ceabf9136c5',
    'hns': '0e1e54fc03adcd59bd1d46f821d42788bc8d9757cd8f41ed118cfc30c241a7bb',
    'gson_source': 'e358710d663ca799101a51d0568ccc6dbd30be9bc55b1a85b1a21a93e96e8a9b',
    'tnm': '3754909bf0d12445b18a60de315619734c1229bcc621f88f315ccf076701ab56',
    'core': '7fbf5a1f7fc65c8adc29092aa08e212c4a43c6ff82d92af99f90f0066914e951',
    'emf': '3ba0bf7b487660920d12cc0ff6cb5de1763f024a080fe0cd7d64e1c8e0f7f190',
    'etf': 'b47173468febc21bb565af19a82bcd2f851e34ecb333660cdc48a8c37c27c1cd',
}


def sha(p: pathlib.Path) -> str:
    return hashlib.sha256(p.read_bytes()).hexdigest()


def compile_env(java: pathlib.Path, ref_jar: pathlib.Path, pal: pathlib.Path, hns: pathlib.Path,
                gson_source: pathlib.Path, tnm: pathlib.Path, core: pathlib.Path,
                emf: pathlib.Path, etf: pathlib.Path, tmp: pathlib.Path):
    fake_ref = tmp / 'auto_ref'
    fake_ref.mkdir()
    r = subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(ref_jar),
         'dev/settledlands/model/BbModel#getName', '--java', str(java)],
        env=dict(os.environ, TMPDIR=str(fake_ref)), capture_output=True, text=True,
    )
    assert r.returncode == 0 and 'DECLARED dev/settledlands/model/BbModel#getName' in r.stdout, (r.stdout, r.stderr)
    fake_hns = tmp / 'auto_hns'
    fake_hns.mkdir()
    r2 = subprocess.run(
        [sys.executable, str(ROOT / 'tools/jvm_verify.py'), str(hns),
         'net/dndats/hackersandslashers/impl/combat/mechanics/combo/TrailSystem#getCurrentRenderFrame',
         'net/dndats/hackersandslashers/impl/combat/mechanics/combo/BladeTracker#isEffectivelyAttacking',
         '--java', str(java)],
        env=dict(os.environ, TMPDIR=str(fake_hns)), capture_output=True, text=True,
    )
    assert r2.returncode == 0 and 'DECLARED net/dndats/hackersandslashers/impl/combat/mechanics/combo/BladeTracker#isEffectivelyAttacking' in r2.stdout, (r2.stdout, r2.stderr)
    autostub = tmp / 'autostub'
    autostub.mkdir()
    shutil.copytree(next(fake_ref.glob('jvmverify-*')), autostub, dirs_exist_ok=True)
    shutil.copytree(next(fake_hns.glob('jvmverify-*')), autostub, dirs_exist_ok=True)
    for n in ('java', 'javax', 'jdk', 'sun', 'org/joml', 'com/zigythebird', 'net/dndats', 'com/google/gson'):
        shutil.rmtree(autostub / n, ignore_errors=True)

    with tarfile.open(gson_source, 'r:gz') as t:
        for m in t.getmembers():
            assert not m.issym() and not m.islnk() and (tmp / 'gson' / m.name).resolve().is_relative_to((tmp / 'gson').resolve())
        t.extractall(tmp / 'gson')
    sr = next((tmp / 'gson').iterdir()) / 'gson/src'
    gout = tmp / 'gsonclasses'
    gout.mkdir()
    template = sr / 'main/java-templates/com/google/gson/internal/GsonBuildConfig.java'
    gen = tmp / 'GsonBuildConfig.java'
    gen.write_text(template.read_text().replace('${project.version}', '2.10.1'))

    compiler = ROOT / 'tools/vendor/ecj-3.46.100.jar'
    common = [str(java), '-jar', str(compiler), '-21', '-encoding', 'UTF-8', '-g:none', '-nowarn']
    subprocess.run(common + ['-d', str(gout), *map(str, sorted((sr / 'main/java/com').rglob('*.java'))), str(gen)], check=True)

    with zipfile.ZipFile(pal) as z:
        mocha = tmp / 'mocha.jar'
        mocha.write_bytes(z.read('META-INF/jars/mochafloats-4.1.0.jar'))
        javassist = tmp / 'javassist.jar'
        javassist.write_bytes(z.read('META-INF/jars/javassist-3.30.2-GA.jar'))
    assert sha(mocha) == '583f287565ea75c84df35573a9d449648e214c0a949418c1d7efcf276fe067bc'
    assert sha(javassist) == '0877af9f34806369cff06c0f74fad9135a8a322d4c654ee754b301fb440b1ebf'

    joml = tmp / 'joml'
    build_joml248.build(java, joml)

    generated = tmp / 'generated'
    with zipfile.ZipFile(tnm) as z:
        assert generate(generated, z) == 25
    keep = (
        'strm/touchnmotion/compat/FirstPersonHead.java',
        'strm/touchnmotion/compat/ShadowPass.java',
        'strm/touchnmotion/platform/Platform.java',
        'net/minecraft/world/entity/animal/horse/AbstractHorse.java',
        'org/slf4j/Logger.java',
        'org/slf4j/LoggerFactory.java',
        'strm/touchnmotion/footgrounding/compat/HorseFootGrounding.java',
        'traben/entity_model_features/models/animation/state/EMFBipedPose.java',
    )
    for name in keep:
        p = generated / name
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(MANUAL_SRC[name] + '\n')
    cape_src = (
        'package strm.touchnmotion.cape; import org.joml.Matrix4f;import java.util.*;'
        'import java.util.function.Function;import net.minecraft.client.model.geom.ModelPart;'
        'import net.minecraft.world.entity.player.Player; public class CapeFollow { '
        'public static Matrix4f pose(ModelPart p){return new Matrix4f();} '
        'public static void keep(UUID id,Matrix4f m,ModelPart p){} '
        'public static Matrix4f capeFrame(Player p,float f){return null;} '
        'public static void carry(UUID id,Function<String,ModelPart> f,Matrix4f a,Matrix4f b){} }'
    )
    p_cape = generated / 'strm/touchnmotion/cape/CapeFollow.java'
    p_cape.parent.mkdir(parents=True, exist_ok=True)
    p_cape.write_text(cape_src + '\n')

    merged = merge(
        ROOT / 'tests/java249',
        ROOT / 'tests/java250',
        generated,
        ROOT / 'tests/java250-realemf',
        ROOT / 'tests/java251',
        ROOT / 'tests/java251-hns',
        ROOT / 'tests/java252-fp',
    )
    excluded = {
        'dev/settledlands/model/BbModel.java',
        'strm/touchnmotion/TouchNMotionHook.java',
        'testing/Bridge249Scenarios.java',
        'testing/Math249Scenario.java',
        'testing/Fresh250Scenarios.java',
        'testing/RealTnmFresh250Scenario.java',
        'testing/Motion251Scenario.java',
        'testing/RealHns251Scenario.java',
        'traben/entity_model_features/EMFAnimationApi.java',
    }
    src = [p for name, p in merged.items() if name not in excluded]
    for rel in sorted(merged):
        if rel in excluded:
            continue
        base = rel[:-5]
        parent = (autostub / base).parent
        if parent.exists():
            stem = pathlib.Path(base).name
            for f in list(parent.glob(stem + '.class')) + list(parent.glob(stem + '$*.class')):
                f.unlink()
    out = tmp / 'out'
    out.mkdir()
    compile_cp = ':'.join(map(str, [ref_jar, pal, hns, core, emf, etf, tnm, autostub, gout, mocha, javassist, joml]))
    subprocess.run(common + ['-cp', compile_cp, '-d', str(out), *map(str, sorted(src))], check=True)
    # Ensure genuine EMFModelPartRoot from EMF 3.3.12 loads at runtime instead of compile stub
    stub_root = out / 'traben/entity_model_features/models/parts/EMFModelPartRoot.class'
    if stub_root.exists():
        stub_root.unlink()
    return out, autostub, gout, mocha, javassist, joml


def run_jar(java: pathlib.Path, jar: pathlib.Path, hns: pathlib.Path,
            deps: tuple, expect_broken: bool) -> str:
    out, pal, core, emf, etf, tnm, autostub, gout, mocha, javassist, joml = deps
    cp = ':'.join(map(str, [out, emf, etf, core, tnm, jar, pal, hns, autostub, gout, mocha, javassist, joml]))
    args = [str(java), '-Xverify:all', '-cp', cp, 'testing.FirstPersonSwordScenario252', str(hns)]
    if expect_broken:
        args.append('--expect-broken')
    r = subprocess.run(args, capture_output=True, text=True)
    assert r.returncode == 0, (expect_broken, r.stdout, r.stderr)
    return r.stdout.strip()


def main():
    q = argparse.ArgumentParser()
    for flag in ('java', 'jar', 'pal', 'hns', 'gson-source', 'tnm', 'core', 'emf', 'etf'):
        q.add_argument('--' + flag, required=True, type=pathlib.Path)
    q.add_argument('--base-jar', type=pathlib.Path, default=None,
                   help='Optional build 251 JAR to prove reproduction of the broken state first')
    a = q.parse_args()
    java = a.java.resolve()
    jar = a.jar.resolve()
    pal = a.pal.resolve()
    hns = a.hns.resolve()
    gson_source = a.gson_source.resolve()
    tnm = a.tnm.resolve()
    core = a.core.resolve()
    emf = a.emf.resolve()
    etf = a.etf.resolve()
    for key in SHA:
        val = locals()[key]
        assert sha(val) == SHA[key], f'SHA mismatch for {key}: {sha(val)}'

    with tempfile.TemporaryDirectory(prefix='fpsword252-') as d:
        tmp = pathlib.Path(d)
        out, autostub, gout, mocha, javassist, joml = compile_env(
            java, jar, pal, hns, gson_source, tnm, core, emf, etf, tmp
        )
        deps = (out, pal, core, emf, etf, tnm, autostub, gout, mocha, javassist, joml)
        if a.base_jar is not None:
            broken_msg = run_jar(java, a.base_jar.resolve(), hns, deps, expect_broken=True)
            assert 'CONFIRMED build 251 bug:' in broken_msg, broken_msg
            print(broken_msg, flush=True)
        fixed_msg = run_jar(java, jar, hns, deps, expect_broken=False)
        assert 'PASS build 252 first-person HnS+PAL+EMF+Core+TnM: 39 attacks / 195 frames' in fixed_msg, fixed_msg
        print(fixed_msg, flush=True)


if __name__ == '__main__':
    main()
