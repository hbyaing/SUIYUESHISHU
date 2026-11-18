"""Run a single interactive Forge demonstration in its own copy of the QA world."""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import time
import uuid
from dataclasses import asdict
from pathlib import Path


REPO = Path(__file__).resolve().parents[2]
WORK = REPO.parent
VERSION = "1.20.72"
GAME = WORK / f"ea-draw-charge-{VERSION}"
FORGE = "1.20.1-Forge_47.4.28"
WORLD = "Enuma QA"
GAME_DATA = Path("D:/我的世界/.minecraft")
JAVA = Path("C:/Program Files/Eclipse Adoptium/jdk-17.0.20.101-hotspot/bin/javaw.exe")
TOOL_CACHE = WORK / "ea-demo-recording-tools"


def allowed(obj):
    features = {"has_custom_resolution": True, "is_quick_play_singleplayer": True}
    if "rules" not in obj:
        return True
    result = False
    for rule in obj["rules"]:
        os_rule = rule.get("os", {})
        if os_rule.get("name", "windows") != "windows" or os_rule.get("arch", "amd64") not in ("amd64", "x86_64"):
            continue
        if all(features.get(k, False) == v for k, v in rule.get("features", {}).items()):
            result = rule["action"] == "allow"
    return result


def launch_command():
    ver = GAME_DATA / "versions" / FORGE
    manifest = json.loads((ver / (FORGE + ".json")).read_text(encoding="utf-8-sig"))
    libs = [GAME_DATA / "libraries" / lib["downloads"]["artifact"]["path"] for lib in manifest["libraries"]
            if allowed(lib) and lib.get("downloads", {}).get("artifact", {}).get("path")]
    libs.append(ver / (FORGE + ".jar"))
    for path in [*libs, JAVA]:
        if not path.is_file():
            raise FileNotFoundError(path)
    player = "EnumaQA"
    uid = uuid.UUID(bytes=hashlib.md5(("OfflinePlayer:" + player).encode()).digest(), version=3).hex
    values = dict(natives_directory=str(ver / (FORGE + "-natives")), launcher_name="Codex QA", launcher_version="1",
                  classpath=os.pathsep.join(map(str, libs)), library_directory=str(GAME_DATA / "libraries"),
                  classpath_separator=os.pathsep, version_name=FORGE, auth_player_name=player, game_directory=str(GAME),
                  assets_root=str(GAME_DATA / "assets"), assets_index_name=manifest["assetIndex"]["id"], auth_uuid=uid,
                  auth_access_token="0", clientid="", auth_xuid="", user_type="legacy", version_type="release",
                  resolution_width="1280", resolution_height="720", quickPlaySingleplayer=WORLD)

    def args(key):
        output = []
        for arg in manifest["arguments"][key]:
            if isinstance(arg, dict):
                if not allowed(arg):
                    continue
                arg = arg["value"]
            for value in arg if isinstance(arg, list) else [arg]:
                output.append(re.sub(r"\$\{([^}]+)\}", lambda m: values[m[1]], value))
        return output
    return [str(JAVA), "-Xmx3G", "-Dfile.encoding=UTF-8", "-Dfml.earlyprogresswindow=false",
            *args("jvm"), manifest["mainClass"], *args("game")]


def prepare():
    GAME.mkdir(exist_ok=True)
    if (GAME / "client.pid").is_file():
        import ctypes
        pid = int((GAME / "client.pid").read_text())
        kernel = ctypes.windll.kernel32
        kernel.OpenProcess.restype = ctypes.c_void_p
        handle = kernel.OpenProcess(0x100000, False, pid)
        if handle:
            try:
                if kernel.WaitForSingleObject(ctypes.c_void_p(handle), 0) == 258:
                    raise RuntimeError(f"The isolated demonstration client is already running (PID {pid}).")
            finally:
                kernel.CloseHandle(ctypes.c_void_p(handle))
    save = GAME / "saves" / WORLD
    if not save.exists():
        shutil.copytree(WORK / "yule-attack-fix-1.20.45/world", save, ignore=shutil.ignore_patterns("session.lock"))
    mods = GAME / "mods"
    mods.mkdir(exist_ok=True)
    for src in [REPO / "build/libs" / f"YiShenDaiGuan-1.20.1-{VERSION}.jar",
                REPO / "build/libs" / f"YiShenCombatQA-{VERSION}-test.jar",
                REPO / "libs/geckolib-forge-1.20.1-4.8.4-mesh.9.jar",
                REPO / "libs/curios-forge-5.14.1+1.20.1.jar"]:
        shutil.copy2(src, mods / src.name)
    (GAME / "config").mkdir(exist_ok=True)
    (GAME / "config/fml.toml").write_text('earlyWindowProvider=""\n', encoding="utf8")
    (GAME / "config/starcrowndoll-ea.toml").write_text("chantTicks = 521\n", encoding="utf8")
    (GAME / "options.txt").write_text(
        "version:3465\nfullscreen:false\nrenderDistance:8\nsimulationDistance:5\nguiScale:2\nlang:zh_cn\n"
        "maxFps:60\npauseOnLostFocus:false\nfov:0.0\ntutorialStep:none\ngraphicsMode:1\nparticles:0\n"
        "autoJump:false\nsoundCategory_music:0.0\nsoundCategory_master:1.0\nsoundCategory_player:1.0\n",
        encoding="utf8")
    # Keep the previous evidence intact when the viewer deliberately starts another run.
    evidence = [path for mask in ("ea-demo.*", "ea-recording.json", "ea-live.mp4", "console.log")
                for path in GAME.glob(mask)]
    shots = list((GAME / "screenshots").glob("ea-*.png"))
    if evidence or shots:
        previous = GAME / "previous" / str(time.time_ns())
        previous.mkdir(parents=True)
        for path in evidence + shots:
            path.rename(previous / path.name)
    (GAME / "enable-ea-demo").touch()


def get_capturekit():
    sys.path.insert(0, str(TOOL_CACHE))
    try:
        import capturekit
    except ImportError:
        subprocess.run([sys.executable, "-m", "pip", "install", "--disable-pip-version-check",
                        "--target", str(TOOL_CACHE), "capturekit==0.1.0"], check=True)
        import capturekit
    return capturekit


def wait_marker(name, process, limit):
    target = GAME / name
    deadline = time.monotonic() + limit
    while time.monotonic() < deadline:
        if target.is_file():
            value = target.read_text(encoding="utf8").strip()
            if value:
                return value
        if process.poll() is not None:
            raise RuntimeError(f"Game exited before {name}. See {GAME / 'console.log'}")
        time.sleep(.08)
    raise TimeoutError(f"Waiting for {name}. The interactive game remains open; see {GAME / 'console.log'}")


def main():
    command = launch_command()
    prepare()
    try:
        ck = get_capturekit()
    except Exception as error:
        ck = None
        print(f"Recording backend unavailable: {error}. Using in-game frames and sound events.", flush=True)
    with (GAME / "console.log").open("wb") as log:
        process = subprocess.Popen(command, cwd=GAME, stdin=subprocess.DEVNULL, stdout=log, stderr=subprocess.STDOUT)
    (GAME / "client.pid").write_text(str(process.pid))
    print(json.dumps(dict(pid=process.pid, gameDirectory=str(GAME)), ensure_ascii=False), flush=True)
    handle = int(wait_marker("ea-demo.window", process, 360))
    recorder = None
    metadata = dict(pid=process.pid, window=handle, source="framebuffer-stills", audio="game-events")
    if ck:
        try:
            config = ck.RecordingConfig(fps=30, output_size=(1280, 720),
                                        encoder=ck.EncoderConfig(encoder="libx264", quality=18, preset="veryfast"))
            recorder = ck.Recorder(ck.WindowSource(handle, client_area_only=True, include_cursor=False),
                                   [ck.ProcessAudioSource(process.pid)], str(GAME / "ea-live.mp4"), config)
            recorder.start()
            # capturekit 0.1 records WGC and WASAPI on QPC. This maps its true media epoch to the Java wall clock.
            epoch = (time.time_ns() - (ck.now_ns() - recorder._start_wall)) // 1_000_000
            metadata.update(source="game-window-WGC", audio="game-process-WASAPI", epochMillis=epoch)
        except Exception as error:
            metadata["startError"] = str(error)
            recorder = None
            print(f"Recording fallback: {error}", flush=True)
    meta_path = GAME / "ea-recording.json"
    meta_path.write_text(json.dumps(metadata, indent=2), encoding="utf8")
    if not recorder:
        (GAME / "ea-demo.frame-sequence").touch()
    (GAME / "ea-demo.record").touch()
    try:
        status = wait_marker("ea-demo.complete", process, 220)
    finally:
        if recorder:
            try:
                result = recorder.stop()
                metadata["stats"] = asdict(result.stats)
                if result.error:
                    metadata["captureError"] = str(result.error)
            except Exception as error:
                metadata["captureError"] = str(error)
            meta_path.write_text(json.dumps(metadata, indent=2, default=str), encoding="utf8")
    print(f"{status} | {GAME}", flush=True)
    return 0 if status=="PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
