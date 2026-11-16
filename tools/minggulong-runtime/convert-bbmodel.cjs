const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");
const zlib = require("node:zlib");

const [inputPath, outputPath] = process.argv.slice(2);
if (!inputPath || !outputPath) {
  throw new Error("Usage: node convert-bbmodel.cjs <character-only.bbmodel> <runtime-model.json.gz>");
}

const sourceBytes = fs.readFileSync(inputPath);
const source = JSON.parse(sourceBytes.toString("utf8"));
const groupByUuid = new Map((source.groups || []).map((group) => [group.uuid, group]));
const elementByUuid = new Map((source.elements || []).map((element) => [element.uuid, element]));
const bones = [];
const cubes = [];

function appendOutliner(entries, parent) {
  for (const entry of entries || []) {
    const uuid = typeof entry === "string" ? entry : entry.uuid;
    const group = groupByUuid.get(uuid);
    if (group) {
      const index = bones.length;
      bones.push({
        uuid: group.uuid,
        name: group.name,
        parent,
        origin: group.origin || [0, 0, 0],
        rotation: group.rotation || [0, 0, 0],
        children: [],
        cubes: []
      });
      if (parent >= 0) bones[parent].children.push(index);
      appendOutliner(typeof entry === "string" ? [] : entry.children, index);
      continue;
    }

    const element = elementByUuid.get(uuid);
    if (!element || element.type !== "cube" || parent < 0) continue;
    const index = cubes.length;
    const faces = {};
    for (const [side, face] of Object.entries(element.faces || {})) {
      if (!face || face.texture === null) continue;
      faces[side] = [
        ...(face.uv || [0, 0, 0, 0]),
        face.rotation || 0
      ];
    }
    cubes.push({
      name: element.name,
      bone: parent,
      from: element.from,
      to: element.to,
      origin: element.origin || [0, 0, 0],
      rotation: element.rotation || [0, 0, 0],
      faces
    });
    bones[parent].cubes.push(index);
  }
}

appendOutliner(source.outliner, -1);
const uuidIndex = new Map(bones.map((bone, index) => [bone.uuid, index]));

function pointValues(point, fallback) {
  return ["x", "y", "z"].map((axis, index) => {
    const value = point?.[axis];
    const number = value === undefined ? fallback[index] : Number(value);
    return Number.isFinite(number) ? number : fallback[index];
  });
}

const animations = (source.animations || []).map((animation) => {
  const tracks = [];
  for (const [uuid, animator] of Object.entries(animation.animators || {})) {
    const index = uuidIndex.get(uuid);
    if (index === undefined) continue;
    const channels = { position: [], rotation: [], scale: [] };
    for (const keyframe of animator.keyframes || []) {
      const channel = channels[keyframe.channel];
      if (!channel) continue;
      const fallback = keyframe.channel === "scale" ? [1, 1, 1] : [0, 0, 0];
      const value = pointValues(keyframe.data_points?.[0], fallback);
      const time = Number(keyframe.time);
      if (!Number.isFinite(time)) continue;
      const last = channel[channel.length - 1];
      if (last && Math.abs(last[0] - time) < 1e-7) channel[channel.length - 1] = [time, ...value];
      else channel.push([time, ...value]);
    }
    for (const channel of Object.values(channels)) channel.sort((a, b) => a[0] - b[0]);
    if (Object.values(channels).some((channel) => channel.length > 0)) tracks.push([index, channels]);
  }
  return {
    name: animation.name,
    length: animation.length,
    loop: animation.loop === "loop",
    tracks
  };
});

const model = {
  format: "starcrowndoll.minggulong.runtime.v1",
  sourceSha256: crypto.createHash("sha256").update(sourceBytes).digest("hex"),
  texture: [source.resolution.width, source.resolution.height],
  bounds: source.elements.reduce((bounds, element) => {
    if (element.type !== "cube") return bounds;
    for (let axis = 0; axis < 3; axis++) {
      bounds[axis] = Math.min(bounds[axis], element.from[axis]);
      bounds[axis + 3] = Math.max(bounds[axis + 3], element.to[axis]);
    }
    return bounds;
  }, [Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity]),
  bones,
  cubes,
  animations
};

if (bones.length !== 370 || cubes.length !== 480 || animations.length !== 22) {
  throw new Error(`Unexpected source shape: ${bones.length} bones, ${cubes.length} cubes, ${animations.length} animations`);
}

const compressed = zlib.gzipSync(Buffer.from(JSON.stringify(model)), { level: 9 });
fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, compressed);
console.log(JSON.stringify({
  outputPath: path.resolve(outputPath),
  sourceSha256: model.sourceSha256,
  bones: bones.length,
  cubes: cubes.length,
  animations: animations.length,
  compressedBytes: compressed.length
}, null, 2));
