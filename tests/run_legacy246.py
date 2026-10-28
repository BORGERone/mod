#!/usr/bin/env python3
"""Run an unmodified legacy JVM fixture against 246 with real stats bytecode.

Only allowed fixture names can be run; attributes and graph/profile are
synthetic. Direct-click-to-possess is intentionally no longer a 246 behavior:
it is covered by the dedicated network and possession bridge fixtures.
"""
import pathlib,runpy,sys
ROOT=pathlib.Path(__file__).resolve().parents[1];sys.path[:0]=[str(ROOT/'tests'),str(ROOT/'tools')]
import wear_fixture245 as x,jvm_npc_cult240 as c,fixture_stats_regression246 as ex
old_wear=x.build;old_cult=c.build_classes
x.build=lambda jar:ex.install(old_wear(jar),jar)
c.build_classes=lambda jar:ex.install(old_cult(jar),jar)
allowed={'jvm_noqueue243.py','jvm_body_wear245.py','jvm_wear_login245.py'}
if len(sys.argv)<2 or sys.argv[1] not in allowed:raise SystemExit('usage: run_legacy246.py FIXTURE [fixture arguments]')
name=sys.argv[1];sys.argv=[str(ROOT/'tests'/name),*sys.argv[2:]];runpy.run_path(sys.argv[0],run_name='__main__')
