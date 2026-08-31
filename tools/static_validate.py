#!/usr/bin/env python3
"""Fast resource-tree sanity checks that require no Minecraft/Gradle runtime."""
from __future__ import annotations
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources"
errors: list[str] = []

json_files = sorted(RES.rglob("*.json"))
for path in json_files:
    try:
        json.loads(path.read_text(encoding="utf-8"))
    except Exception as exc:
        errors.append(f"invalid JSON: {path.relative_to(ROOT)}: {exc}")

# Project Golf model references in blockstates and item/block parent models.
def check_model_ref(ref: str, source: Path) -> None:
    if ref.startswith("minecraft:"):
        return
    if ref.startswith("projectgolf:"):
        namespace, rel = ref.split(":", 1)
        target = RES / "assets" / namespace / "models" / f"{rel}.json"
        if not target.exists():
            errors.append(f"missing model {ref} referenced by {source.relative_to(ROOT)}")

for path in sorted((RES / "assets/projectgolf/blockstates").glob("*.json")):
    data = json.loads(path.read_text())
    for variant in data.get("variants", {}).values():
        variants = variant if isinstance(variant, list) else [variant]
        for item in variants:
            if isinstance(item, dict) and "model" in item:
                check_model_ref(item["model"], path)

for path in sorted((RES / "assets/projectgolf/models").rglob("*.json")):
    data = json.loads(path.read_text())
    parent = data.get("parent")
    if isinstance(parent, str):
        check_model_ref(parent, path)

# Every registered gameplay block should have blockstate/model/item model/loot except dev OOB loot.
blocks_java = (ROOT / "src/main/java/dev/projectgolf/registry/GolfBlocks.java").read_text()
import re
registered = re.findall(r'BLOCKS\.register(?:SimpleBlock)?\("([a-z0-9_]+)"', blocks_java)
for name in registered:
    expected = [
        RES / f"assets/projectgolf/blockstates/{name}.json",
        RES / f"assets/projectgolf/models/block/{name}.json",
        RES / f"assets/projectgolf/models/item/{name}.json",
    ]
    for target in expected:
        if not target.exists():
            errors.append(f"registered block {name} missing {target.relative_to(ROOT)}")

rpl_tag = RES / "data/ritchiesprojectilelib/tags/entity_type/precise_motion.json"
if not rpl_tag.exists():
    errors.append("missing RPL precise_motion tag")
else:
    vals = json.loads(rpl_tag.read_text()).get("values", [])
    if "projectgolf:golf_ball" not in vals:
        errors.append("RPL precise_motion tag does not contain projectgolf:golf_ball")

lang = json.loads((RES / "assets/projectgolf/lang/en_us.json").read_text())
for key in [
    "item.projectgolf.golf_ball", "item.projectgolf.driver", "item.projectgolf.wood",
    "item.projectgolf.iron", "item.projectgolf.wedge", "item.projectgolf.putter",
    "item.projectgolf.debug_wand"
]:
    if key not in lang:
        errors.append(f"missing lang key {key}")
for name in registered:
    key = f"block.projectgolf.{name}"
    if key not in lang:
        errors.append(f"missing lang key {key}")

# Cheap source/config regression checks for exact 1.21.1 choices we already audited.
all_java = "\n".join(path.read_text(encoding="utf-8") for path in (ROOT / "src/main/java").rglob("*.java"))
if "event.tick.ClientTickEvent" in all_java:
    errors.append("wrong ClientTickEvent package for NeoForge 1.21.1")
if "DeferredRegister.createEntities" in all_java:
    errors.append("newer createEntities convenience API leaked into 1.21.1 source")
if "EntityDataSerializers.OPTIONAL_UUID" not in all_java:
    errors.append("golf ball owner is not synchronized with OPTIONAL_UUID")
if "ball.isStationary()" not in all_java:
    errors.append("client ball preview does not require a stationary ball")

build_gradle = (ROOT / "build.gradle").read_text(encoding="utf-8")
if "runtimeClasspath.extendsFrom localRuntime" not in build_gradle:
    errors.append("build.gradle missing official-MDK localRuntime wiring")
if "org.junit.jupiter" not in build_gradle:
    errors.append("JUnit regression test dependency missing")
if "unitTest" not in build_gradle or "enable()" not in build_gradle or 'testedMod = mods.projectgolf' not in build_gradle:
    errors.append("ModDevGradle unitTest wiring missing; src/test will not see Minecraft classes")
if "org.junit.platform:junit-platform-launcher" not in build_gradle:
    errors.append("JUnit platform launcher runtime dependency missing")
if 'testRuntimeOnly "com.rbasamoyai:ritchiesprojectilelib:2.1.2+mc.1.21.1-neoforge"' not in build_gradle:
    errors.append("required RPL dependency missing from unit-test runtime classpath")
if "gameTestServer" in build_gradle:
    errors.append("gameTestServer run is advertised without registered GameTests")
if "getAllEntities()" in all_java:
    errors.append("whole-world entity scanning leaked into Project Golf source")
if "EntityDataSerializers.BOOLEAN" not in (ROOT / "src/main/java/dev/projectgolf/entity/GolfBallEntity.java").read_text(encoding="utf-8"):
    errors.append("in-hole state is not synchronized to clients")
if "PlayerEvent.Clone" not in all_java:
    errors.append("round state is not copied across death respawns")
if "STATIONARY_RECHECK_TICKS" not in all_java:
    errors.append("resting-ball sleep/recheck path missing")
if "setSwingHand(false)" not in all_java:
    errors.append("club RMB cancellation may leak vanilla hand swing/use")


# Alpha visual/usability guards.
creative_tabs = ROOT / "src/main/java/dev/projectgolf/registry/GolfCreativeTabs.java"
if not creative_tabs.exists():
    errors.append("Project Golf creative tab source missing")
else:
    creative_src = creative_tabs.read_text(encoding="utf-8")
    for required in ["GOLF_BALL", "DRIVER", "WOOD", "IRON", "WEDGE", "PUTTER", "GOLF_CUP"]:
        if required not in creative_src:
            errors.append(f"creative tab missing {required}")
if "GolfCreativeTabs.TABS.register(modBus)" not in all_java:
    errors.append("Project Golf creative tab is not registered on the mod bus")
if "ARDEN GOLF" in all_java:
    errors.append("legacy ARDEN GOLF HUD text remains after Project Golf rename")
if "addAlwaysVisibleParticle" not in all_java:
    errors.append("high-visibility trajectory/ball particle path missing")
if "PERFECT_ACCURACY_WINDOW" not in (ROOT / "src/main/java/dev/projectgolf/entity/GolfBallEntity.java").read_text(encoding="utf-8"):
    errors.append("server-authoritative perfect-shot visual trigger missing")
if "LANDING_MARKER_TICKS" not in all_java:
    errors.append("temporary landing marker system missing")
if "itemGroup.projectgolf.main" not in lang:
    errors.append("missing Project Golf creative-tab lang key")

green_tag = json.loads((RES / "data/projectgolf/tags/block/green.json").read_text())
if "projectgolf:golf_cup" not in green_tag.get("values", []):
    errors.append("golf cup must retain putting-green surface physics")

if errors:
    print(f"FAIL: {len(errors)} problem(s)")
    for err in errors:
        print(" -", err)
    sys.exit(1)

print(f"PASS: parsed {len(json_files)} JSON resources; checked {len(registered)} blocks, RPL tag, source API guards and test wiring")
