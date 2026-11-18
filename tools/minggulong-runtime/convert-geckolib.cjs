const crypto = require("node:crypto");
const fs = require("node:fs");
const path = require("node:path");

const [inputPath, geoPath, animationPath, profile = "minggulong"] = process.argv.slice(2);
const enkidu = profile === "enkidu";
if (!inputPath || !geoPath || !animationPath) {
  throw new Error("Usage: node convert-geckolib.cjs <character-only.bbmodel> <minggulong.geo.json> <minggulong.animation.json>");
}

const sourceBytes = fs.readFileSync(inputPath);
const source = JSON.parse(sourceBytes.toString("utf8"));
const groups = [...(source.groups || [])];
function collectInline(entries) {
  for (const entry of entries || []) {
    if (typeof entry === "object") {
      if (!groups.some(group => group.uuid === entry.uuid)) groups.push(entry);
      collectInline(entry.children);
    }
  }
}
if (enkidu) collectInline(source.outliner);
const groupByUuid = new Map(groups.map(group => [group.uuid, group]));
const elementByUuid = new Map((source.elements || []).map(element => [element.uuid, element]));
const bonesByUuid = new Map();
const bones = [];

function number(value, fallback = 0) {
  const result = Number(value);
  return Number.isFinite(result) ? result : fallback;
}

function point(value, fallback) {
  if (!value) return fallback.slice();
  return [number(value.x, fallback[0]), number(value.y, fallback[1]), number(value.z, fallback[2])];
}

// Keep GeckoLib/Molang expressions live; converting them through Number() loses
// the wing membrane shear that is evaluated from query.anim_time at render time.
function signedAnimationValue(value, sign, fallback) {
  if (typeof value === "string") {
    const expression = value.trim();
    if (expression.length) {
      if (!expression.includes(";")) return sign < 0 ? `-(${expression})` : expression;

      const prefix = expression.startsWith("0;") ? "" : "0;";
      if (sign >= 0) return `${prefix}${expression}`;

      // GeckoLib's Molang parser splits top-level statements on semicolons.
      // Keep compound expressions at the top level and negate only the final
      // return value so temporary assignments remain valid.
      const returnMatch = expression.match(/^(.*?)(?:return\s+)(.+?)(;?)$/is);
      if (returnMatch) {
        const [, statements, result, terminator] = returnMatch;
        return `${prefix}${statements}return -(${result})${terminator}`;
      }

      return `${prefix}${expression}`;
    }
  }
  return number(value, fallback) * sign;
}

function cubeFaces(element) {
  const result = {};
  for (const [side, face] of Object.entries(element.faces || {})) {
    if (!face || face.texture === null || !Array.isArray(face.uv) || face.uv.length < 4) continue;
    const uv = face.uv.map(number);
    if (enkidu) {
      if (!Number.isInteger(face.texture) || !source.textures[face.texture]) throw new Error("Missing face texture");
      uv[0] += face.texture * 256;
      uv[2] += face.texture * 256;
    }
    result[side] = {
      uv: [uv[0], uv[1]],
      uv_size: [uv[2] - uv[0], uv[3] - uv[1]]
    };
    if (face.rotation) result[side].uv_rotation = face.rotation;
    // Bedrock's top/bottom vertex order is reversed relative to BB CubeFace.
    // Match Blockbench's native Bedrock codec, including signed UV rectangles.
    if (side === "up" || side === "down") {
      result[side].uv = [uv[2], uv[3]];
      result[side].uv_size = [uv[0] - uv[2], uv[1] - uv[3]];
    }
  }
  return result;
}

function appendOutliner(entries, parent = null) {
  for (const entry of entries || []) {
    const uuid = typeof entry === "string" ? entry : entry.uuid;
    const group = groupByUuid.get(uuid);
    if (group) {
      const bone = {
        name: group.name,
        ...(parent ? { parent } : {}),
        // Raw Bedrock pivots are absolute model-space coordinates. GeckoLib
        // applies T(pivot)*R*S*T(-pivot) at each level; do not export local deltas.
        // BB's native Bedrock codec mirrors X and negates X/Y Euler angles.
        pivot: point({
          x: -number(group.origin?.[0]),
          y: number(group.origin?.[1]),
          z: number(group.origin?.[2])
        }, [0, 0, 0]),
        rotation: [-number(group.rotation?.[0]), -number(group.rotation?.[1]), number(group.rotation?.[2])],
        cubes: []
      };
      const index = bones.length;
      bones.push(bone);
      bonesByUuid.set(uuid, index);
      const children = typeof entry === "string" ? group.children : entry.children;
      appendOutliner(children, group.name);
      continue;
    }
    const element = elementByUuid.get(uuid);
    if (!element || element.type !== "cube" || !parent) continue;
    const from = (element.from || [0, 0, 0]).map(number);
    const to = (element.to || from).map(number);
    const size = to.map((value, axis) => value - from[axis]);
    const cube = {
      origin: [-to[0], from[1], from[2]],
      size,
      uv: cubeFaces(element)
    };
    if (element.inflate) cube.inflate = number(element.inflate);
    if (element.mirror_uv) cube.mirror = true;
    if (element.rotation && element.rotation.some(value => number(value) !== 0)) {
      cube.rotation = [-number(element.rotation[0]), -number(element.rotation[1]), number(element.rotation[2])];
      const pivot = (element.origin || [0, 0, 0]).map(number);
      cube.pivot = [-pivot[0], pivot[1], pivot[2]];
    }
    const bone = bones.find(candidate => candidate.name === parent);
    if (bone) bone.cubes.push(cube);
  }
}

appendOutliner(source.outliner || []);
if (!enkidu && bones.length !== 370) throw new Error(`Expected 370 bones, got ${bones.length}`);
const cubes = bones.reduce((sum, bone) => sum + bone.cubes.length, 0);
if (cubes !== (enkidu ? source.elements.length : 480)) throw new Error(`Unexpected cube count: ${cubes}`);

const bounds = (source.elements || []).reduce((result, element) => {
  if (element.type !== "cube") return result;
  for (let axis = 0; axis < 3; axis++) {
    result[axis] = Math.min(result[axis], number(element.from?.[axis]));
    result[axis + 3] = Math.max(result[axis + 3], number(element.to?.[axis]));
  }
  return result;
}, [Infinity, Infinity, Infinity, -Infinity, -Infinity, -Infinity]);

const geo = {
  format_version: "1.12.0",
  "minecraft:geometry": [{
      description: {
        identifier: "geometry." + profile,
        texture_width: enkidu ? source.textures.length * 256 : source.resolution.width,
        texture_height: source.resolution.height,
        visible_bounds_width: (bounds[3] - bounds[0]) / 16 + 1,
        visible_bounds_height: (bounds[4] - bounds[1]) / 16 + 1,
        visible_bounds_offset: [0, (bounds[1] + bounds[4]) / 32, 0]
      },
      bones
    }],
  _starcrowndoll: {
    source_sha256: crypto.createHash("sha256").update(sourceBytes).digest("hex"),
    cube_count: cubes,
    bone_count: bones.length,
    ...(!enkidu ? {charge_anchor: [5.11132431, 6.826404572, -19.981182098]} : {})
  }
};

const boneNames = new Map();
for (const group of groups) boneNames.set(group.uuid, group.name);
const animations = {};
let preservedMolangValues = 0;
for (const animation of source.animations || []) {
  const animationName = `animation.${profile}.${animation.name.toLowerCase()}`;
  const animationBones = {};
  for (const [uuid, animator] of Object.entries(animation.animators || {})) {
    const boneName = boneNames.get(uuid);
    if (!boneName) continue;
    const tracks = {};
    for (const keyframe of animator.keyframes || []) {
      const channel = keyframe.channel;
      if (!["position", "rotation", "scale"].includes(channel)) continue;
      const target = tracks[channel] || {};
      const data = keyframe.data_points?.[0];
      if (!data) continue;
      const fallback = channel === "scale" ? [1, 1, 1] : [0, 0, 0];
      const value = [
        signedAnimationValue(data.x, channel === "rotation" || channel === "position" ? -1 : 1, fallback[0]),
        signedAnimationValue(data.y, channel === "rotation" ? -1 : 1, fallback[1]),
        signedAnimationValue(data.z, 1, fallback[2])
      ];
      preservedMolangValues += value.filter(component => typeof component === "string" && !/^-?\d+(?:\.\d+)?$/.test(component)).length;
      target[String(number(keyframe.time))] = value;
      tracks[channel] = target;
    }
    if (Object.keys(tracks).length) animationBones[boneName] = tracks;
  }
  animations[animationName] = {
    loop: animation.loop === "loop",
    animation_length: number(animation.length),
    bones: animationBones
  };
}

const animationDocument = {
  format_version: "1.8.0",
  animations,
  geckolib_format_version: 2,
  _starcrowndoll: { source_sha256: geo._starcrowndoll.source_sha256, animation_count: Object.keys(animations).length }
};

fs.mkdirSync(path.dirname(geoPath), { recursive: true });
fs.mkdirSync(path.dirname(animationPath), { recursive: true });
fs.writeFileSync(geoPath, JSON.stringify(geo));
fs.writeFileSync(animationPath, JSON.stringify(animationDocument));
console.log(JSON.stringify({ geoPath: path.resolve(geoPath), animationPath: path.resolve(animationPath), bones: bones.length, cubes, animations: Object.keys(animations).length, preservedMolangValues }, null, 2));
