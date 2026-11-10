'use strict';

const $ = (selector) => document.querySelector(selector);
const data = JSON.parse($('#model-data').textContent);
const parts = {
  head: '头部', body: '躯干', rightArm: '右臂', leftArm: '左臂', rightLeg: '右腿', leftLeg: '左腿'
};
const views = { angled: [-28, 18], front: [0, 0], left: [90, 0], back: [180, 0], right: [-90, 0] };
const faceCorners = {
  down: [[0,0,1], [0,0,0], [1,0,0], [1,0,1]],
  up: [[0,1,0], [0,1,1], [1,1,1], [1,1,0]],
  north: [[1,1,0], [1,0,0], [0,0,0], [0,1,0]],
  south: [[0,1,1], [0,0,1], [1,0,1], [1,1,1]],
  west: [[0,1,0], [0,0,0], [0,0,1], [0,1,1]],
  east: [[1,1,1], [1,0,1], [1,0,0], [1,1,0]]
};
const armFaces = {
  west: [0, 0, 0], east: [7, 1, 0], north: [4, 0, 1], up: [4, 0, 1],
  down: [7, 1, 1], south: [11, 1, 1]
};
const DEG_TO_RAD = Math.PI / 180;

function rotate(point, rotation) {
  if (!rotation) return point;
  const axis = 'xyz'.indexOf(rotation.axis);
  const first = (axis + 1) % 3, second = (axis + 2) % 3;
  const origin = rotation.origin, angle = rotation.angle * DEG_TO_RAD;
  const result = [...point];
  const a = point[first] - origin[first], b = point[second] - origin[second];
  result[first] = origin[first] + a * Math.cos(angle) - b * Math.sin(angle);
  result[second] = origin[second] + a * Math.sin(angle) + b * Math.cos(angle);
  return result;
}

function armUV(uv, face, part, layer) {
  const origin = part === 'rightArm' ? 40 : layer ? 48 : 32;
  const [start, shift, grows] = armFaces[face];
  const remap = (u) => Math.round((u + shift + grows * (u - origin - start) / 3) * 1e6) / 1e6;
  return [remap(uv[0]), uv[1], remap(uv[2]), uv[3]];
}

function makeTemplate(wide) {
  const used = new Uint8Array(4096);
  const regions = Object.fromEntries(Object.keys(parts).map((part) => [part, [new Set(), new Set()]]));
  const elements = [], faces = [], vertices = [];
  for (const {part, layer, element} of data.boxes) {
    const box = {...element, faces: {}};
    for (const [side, face] of Object.entries(element.faces)) {
      const original = face.uv.map((value) => value * 4);
      const uv = wide && part.endsWith('Arm') ? armUV(original, side, part, layer) : original;
      const [u0, v0, u1, v1] = uv;
      box.faces[side] = {...face, uv: uv.map((value) => value / 4)};
      for (let y = Math.min(v0, v1); y < Math.max(v0, v1); y++) {
        for (let x = Math.min(u0, u1); x < Math.max(u0, u1); x++) {
          const index = y * 64 + x;
          regions[part][layer].add(index);
          used[index] |= 1 << layer;
        }
      }
      const corners = faceCorners[side].map((corner) => {
        const point = corner.map((end, axis) => end ? element.to[axis] : element.from[axis]);
        return rotate(point, element.rotation).map((value) => value - 8);
      });
      const normal = unitCross(corners[0], corners[1], corners[2]);
      const center = corners[0].map((value, axis) => (value + corners[2][axis]) / 2);
      const uvCorners = [[u0,v0], [u0,v1], [u1,v1], [u1,v0]];
      const turn = (face.rotation || 0) / 90;
      faces.push({offset: vertices.length / 8, layer, center});
      for (const index of [0, 1, 2, 0, 2, 3]) {
        const coord = uvCorners[(index + turn) % 4].map((value) => value / 64);
        vertices.push(...corners[index], ...coord, ...normal);
      }
    }
    elements.push(box);
  }
  return {used, regions, elements, faces, vertices: new Float32Array(vertices)};
}

function unitCross(origin, first, second) {
  const [ax, ay, az] = first.map((value, axis) => value - origin[axis]);
  const [bx, by, bz] = second.map((value, axis) => value - origin[axis]);
  const normal = [ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx];
  const size = Math.hypot(...normal);
  return normal.map((value) => value / size);
}

const templates = {slim: makeTemplate(false), wide: makeTemplate(true)};
const atlas = $('#atlas').getContext('2d');
const skinCanvas = document.createElement('canvas');
skinCanvas.width = skinCanvas.height = 64;
const skinContext = skinCanvas.getContext('2d', {willReadFrequently: true});
let template = templates.slim;
let skin = null;
let revision = 0;
let angles = [...views.angled];

function whitePixels(mold) {
  const rgba = new Uint8ClampedArray(64 * 64 * 4);
  mold.used.forEach((layers, index) => {
    if (!layers) return;
    const base = Boolean(layers & 1);
    rgba.set(base ? [245, 246, 252, 255] : [201, 206, 225, 255], index * 4);
  });
  return rgba;
}

function stats(indices, rgba) {
  const counts = {visible: 0, black: 0, white: 0, partial: 0, total: 0};
  for (const index of indices) {
    counts.total++;
    const offset = index * 4;
    const [r, g, b, alpha] = rgba.subarray(offset, offset + 4);
    if (!alpha) continue;
    counts.visible++;
    if (alpha < 255) counts.partial++;
    if (r === 0 && g === 0 && b === 0) counts.black++;
    if (r === 255 && g === 255 && b === 255) counts.white++;
  }
  return counts;
}

function reportLayer(cell, counts) {
  if (counts.visible === 0) {
    cell.className = 'empty';
    cell.textContent = '全透明';
  } else if (!counts.partial && (counts.black === counts.total || counts.white === counts.total)) {
    cell.className = 'warning';
    cell.textContent = counts.black ? '整片纯黑 · 核对' : '整片纯白 · 核对';
  } else {
    cell.textContent = `${counts.visible}/${counts.total} 像素${counts.partial ? ' · 含半透明' : ''}`;
  }
}

function inspect(rgba) {
  atlas.putImageData(new ImageData(rgba, 64, 64), 0, 0);
  const unmapped = [];
  template.used.forEach((used, index) => {
    if (used) return;
    unmapped.push(index);
    if (!$('#show-padding').checked) return;
    const x = index % 64, y = index >> 6;
    atlas.fillStyle = (x + y) % 2 ? '#a581f08c' : '#9772d44d';
    atlas.fillRect(x, y, 1, 1);
  });
  const padding = stats(unmapped, rgba);
  $('#padding-report').textContent = `未映射 ${padding.total} 像素，其中可见纯黑 ${padding.black}、纯白 ${padding.white}。`;
  const table = $('#layers');
  table.replaceChildren();
  let outerCount = 0;
  for (const [part, label] of Object.entries(parts)) {
    const row = table.insertRow();
    row.insertCell().textContent = label;
    const [base, outer] = template.regions[part].map((indices) => stats(indices, rgba));
    reportLayer(row.insertCell(), base);
    reportLayer(row.insertCell(), outer);
    if (outer.visible) outerCount++;
  }
  $('#layer-summary').textContent = skin ? `${outerCount}/6 部位有外层像素` : '白模 · 六部位双层';
}

function download(blob, name) {
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = name;
  link.click();
  setTimeout(() => URL.revokeObjectURL(link.href), 1000);
}

async function loadSkin(blob, name, layout) {
  const current = ++revision;
  const error = $('#file-error');
  error.hidden = true;
  try {
    const header = new DataView(await blob.slice(0, 24).arrayBuffer());
    if (header.byteLength !== 24 || header.getUint32(0) !== 0x89504e47 || header.getUint32(4) !== 0x0d0a1a0a) {
      throw new Error('请选择 PNG 皮肤文件。');
    }
    if (header.getUint32(16) !== 64 || header.getUint32(20) !== 64) {
      throw new Error('当前白模使用 64 × 64 原版皮肤布局。请选用该尺寸的原始 PNG。');
    }
    const bitmap = await createImageBitmap(blob, {colorSpaceConversion: 'none', premultiplyAlpha: 'none'});
    if (current !== revision) { bitmap.close(); return; }
    skinContext.clearRect(0, 0, 64, 64);
    skinContext.drawImage(bitmap, 0, 0);
    skin?.bitmap.close();
    skin = {bitmap, rgba: skinContext.getImageData(0, 0, 64, 64).data, name};
    if (layout) $('#arm-model').value = layout;
    update();
  } catch (reason) {
    if (current !== revision) return;
    error.textContent = reason instanceof DOMException ? '无法解码这张 PNG，请检查文件是否完整。' : reason.message;
    error.hidden = false;
  }
}

function showWhite() {
  ++revision;
  skin?.bitmap.close();
  skin = null;
  $('#skin-file').value = '';
  $('#file-error').hidden = true;
  update();
}

function makeRenderer(canvas) {
  const gl = canvas.getContext('webgl', {alpha: true, antialias: true, preserveDrawingBuffer: true});
  if (!gl) return null;
  const compile = (type, source) => {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader));
    return shader;
  };
  const program = gl.createProgram();
  gl.attachShader(program, compile(gl.VERTEX_SHADER, `
    attribute vec3 position, normal;
    attribute vec2 uv;
    uniform vec2 yaw, pitch, size;
    varying vec2 texel;
    varying float light;
    void main() {
      float x = yaw.x * position.x + yaw.y * position.z;
      float z = -yaw.y * position.x + yaw.x * position.z;
      float y = pitch.x * position.y + pitch.y * z;
      float depth = -pitch.y * position.y + pitch.x * z;
      gl_Position = vec4(-x * size.x, y * size.y + .02, depth / 32., 1.);
      light = .72 + .28 * max(0., dot(normal, vec3(-.36, .80, -.48)));
      texel = uv;
    }`));
  gl.attachShader(program, compile(gl.FRAGMENT_SHADER, `
    precision mediump float;
    uniform sampler2D textureMap;
    uniform float opacity, alphaPass, flatLight;
    varying vec2 texel;
    varying float light;
    void main() {
      vec4 color = texture2D(textureMap, texel);
      color.a *= opacity;
      if (color.a <= 0. || (alphaPass == 0. && color.a < .999) || (alphaPass == 1. && color.a >= .999)) discard;
      gl_FragColor = vec4(color.rgb * mix(light, 1., flatLight), color.a);
    }`));
  gl.linkProgram(program);
  if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program));
  gl.useProgram(program);
  const uniforms = Object.fromEntries(['yaw', 'pitch', 'size', 'opacity', 'alphaPass', 'flatLight'].map((name) => [name, gl.getUniformLocation(program, name)]));
  const buffer = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
  for (const [name, size, offset] of [['position', 3, 0], ['uv', 2, 12], ['normal', 3, 20]]) {
    const location = gl.getAttribLocation(program, name);
    gl.enableVertexAttribArray(location);
    gl.vertexAttribPointer(location, size, gl.FLOAT, false, 32, offset);
  }
  const texture = gl.createTexture();
  gl.bindTexture(gl.TEXTURE_2D, texture);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.enable(gl.DEPTH_TEST);
  gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);

  return {
    upload(rgba) {
      gl.bufferData(gl.ARRAY_BUFFER, template.vertices, gl.STATIC_DRAW);
      if (skin) gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, skin.bitmap);
      else gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, 64, 64, 0, gl.RGBA, gl.UNSIGNED_BYTE, new Uint8Array(rgba));
    },
    draw() {
      const {width, height} = canvas.getBoundingClientRect();
      const pixelRatio = Math.min(devicePixelRatio, 2);
      canvas.width = Math.round(width * pixelRatio);
      canvas.height = Math.round(height * pixelRatio);
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
      const [yaw, pitch] = angles.map((angle) => angle * DEG_TO_RAD);
      const [cy, sy, cp, sp] = [Math.cos(yaw), Math.sin(yaw), Math.cos(pitch), Math.sin(pitch)];
      const size = Math.min(.085, .095 * width / height);
      gl.uniform2f(uniforms.yaw, cy, sy);
      gl.uniform2f(uniforms.pitch, cp, sp);
      gl.uniform2f(uniforms.size, size * height / width, size);
      gl.uniform1f(uniforms.flatLight, Number($('#flat-light').checked));
      const visible = [$('#show-base').checked, $('#show-outer').checked];
      const drawFace = (face) => {
        if (!visible[face.layer]) return;
        gl.uniform1f(uniforms.opacity, !skin && face.layer && visible[0] ? .53 : 1);
        gl.drawArrays(gl.TRIANGLES, face.offset, 6);
      };
      gl.disable(gl.BLEND);
      gl.uniform1f(uniforms.alphaPass, 0);
      template.faces.forEach(drawFace);
      const depth = ({center: [x, y, z]}) => (-sy * x + cy * z) * cp - y * sp;
      gl.enable(gl.BLEND);
      gl.depthMask(false);
      gl.uniform1f(uniforms.alphaPass, 1);
      [...template.faces].sort((a, b) => depth(b) - depth(a)).forEach(drawFace);
      gl.depthMask(true);
    }
  };
}

const canvas = $('#scene');
let renderer;
try { renderer = makeRenderer(canvas); }
catch (reason) { console.error(reason); }
$('#graphics-error').hidden = Boolean(renderer);

function update() {
  template = templates[$('#arm-model').value];
  const rgba = skin?.rgba || whitePixels(template);
  $('#source-name').textContent = skin?.name || '坐姿白模';
  $('#source-tag').textContent = skin ? '原皮肤 UV' : '模板预览';
  inspect(rgba);
  renderer?.upload(rgba);
  renderer?.draw();
}

for (const button of document.querySelectorAll('[data-view]')) {
  button.addEventListener('click', () => {
    angles = [...views[button.dataset.view]];
    document.querySelectorAll('[data-view]').forEach((view) => view.setAttribute('aria-pressed', view === button));
    renderer?.draw();
  });
}
let drag;
canvas.addEventListener('pointerdown', (event) => {
  drag = [event.clientX, event.clientY, ...angles];
  canvas.setPointerCapture(event.pointerId);
});
canvas.addEventListener('pointermove', (event) => {
  if (!drag) return;
  angles = [drag[2] - (event.clientX - drag[0]) * .45, Math.max(-75, Math.min(75, drag[3] + (event.clientY - drag[1]) * .4))];
  document.querySelectorAll('[data-view]').forEach((view) => view.setAttribute('aria-pressed', 'false'));
  renderer?.draw();
});
canvas.addEventListener('lostpointercapture', () => { drag = null; });

const fileInput = $('#skin-file');
fileInput.addEventListener('change', () => {
  const file = fileInput.files[0];
  if (file) loadSkin(file, file.name);
});
const dropZone = $('#drop-zone');
dropZone.addEventListener('dragover', (event) => { event.preventDefault(); dropZone.classList.add('over'); });
dropZone.addEventListener('dragleave', () => dropZone.classList.remove('over'));
dropZone.addEventListener('drop', (event) => {
  event.preventDefault();
  dropZone.classList.remove('over');
  const file = event.dataTransfer.files[0];
  if (file) loadSkin(file, file.name);
});
$('#blank').addEventListener('click', showWhite);
for (const button of document.querySelectorAll('[data-skin]')) {
  button.addEventListener('click', async () => {
    const blob = await (await fetch(data.samples[button.dataset.skin])).blob();
    fileInput.value = '';
    loadSkin(blob, button.textContent, 'slim');
  });
}
$('#arm-model').addEventListener('change', update);
$('#show-padding').addEventListener('change', () => inspect(skin?.rgba || whitePixels(template)));
for (const id of ['show-base', 'show-outer', 'flat-light']) $(`#${id}`).addEventListener('change', () => renderer?.draw());
$('#save-model').addEventListener('click', () => {
  const model = {ambientocclusion: false, textures: {skin: 'starcrowndoll:block/doll_template', particle: '#skin'}, elements: template.elements};
  download(new Blob([JSON.stringify(model, null, 2) + '\n'], {type: 'application/json'}), `doll_template_${$('#arm-model').value}.json`);
});
$('#save-texture').addEventListener('click', () => {
  const exported = document.createElement('canvas');
  exported.width = exported.height = 64;
  exported.getContext('2d').putImageData(new ImageData(whitePixels(template), 64, 64), 0, 0);
  exported.toBlob((blob) => download(blob, 'doll_template.png'));
});
new ResizeObserver(() => renderer?.draw()).observe(canvas);
update();
