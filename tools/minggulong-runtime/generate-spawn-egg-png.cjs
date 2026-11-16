const fs = require('fs');
const zlib = require('zlib');

const width = 16;
const height = 16;
const transparent = [0, 0, 0, 0];
const colors = {
  outline: [17, 8, 10, 255],
  black: [7, 6, 8, 255],
  orange: [232, 90, 22, 255],
  orangeLight: [250, 119, 34, 255],
  red: [168, 23, 34, 255],
  redLight: [206, 42, 45, 255]
};
const pixels = Array.from({length: height}, () => Array.from({length: width}, () => transparent));
const set = (x, y, color) => { if (x >= 0 && x < width && y >= 0 && y < height) pixels[y][x] = colors[color]; };

// Pixel silhouette with a dark rim, orange/red shell, and black central markings.
const rows = {
  2: [7, 8], 3: [6, 7, 8, 9], 4: [5, 6, 7, 8, 9, 10],
  5: [4, 5, 6, 7, 8, 9, 10, 11],
  6: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  7: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  8: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  9: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  10: [3, 4, 5, 6, 7, 8, 9, 10, 11, 12],
  11: [4, 5, 6, 7, 8, 9, 10, 11],
  12: [4, 5, 6, 7, 8, 9, 10, 11],
  13: [5, 6, 7, 8, 9, 10],
  14: [6, 7, 8, 9]
};
for (const [yText, xs] of Object.entries(rows)) {
  const y = Number(yText);
  for (const x of xs) set(x, y, 'orange');
  set(xs[0], y, 'outline');
  set(xs[xs.length - 1], y, 'outline');
}
for (const x of [7, 8]) set(x, 2, 'outline');
for (const x of [6, 7, 8, 9]) set(x, 3, 'orangeLight');
for (const x of [5, 6, 7]) set(x, 4, 'orangeLight');

// Red side plates and a black, branching core mark make the three-color identity readable at 16 px.
for (const [x, y] of [[10, 5], [11, 6], [10, 6], [11, 7], [10, 7], [9, 8], [10, 8], [11, 8], [10, 9], [9, 9], [8, 10], [9, 10], [10, 10], [8, 11], [9, 11]]) set(x, y, 'red');
for (const [x, y] of [[9, 5], [10, 5], [9, 6], [8, 6], [8, 7], [7, 7], [7, 8], [6, 8], [6, 9], [5, 9], [5, 10], [6, 10], [6, 11], [7, 11], [7, 12], [8, 12]]) set(x, y, 'black');
for (const [x, y] of [[4, 7], [4, 8], [5, 6], [5, 7]]) set(x, y, 'redLight');

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function chunk(type, data) {
  const typeBuffer = Buffer.from(type, 'ascii');
  const body = Buffer.concat([typeBuffer, data]);
  const result = Buffer.alloc(12 + data.length);
  result.writeUInt32BE(data.length, 0);
  body.copy(result, 4);
  result.writeUInt32BE(crc32(body), 8 + data.length);
  return result;
}
const scanlines = [];
for (const row of pixels) scanlines.push(Buffer.from([0, ...row.flat()]));
const png = Buffer.concat([
  Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
  chunk('IHDR', Buffer.from([0, 0, 0, width, 0, 0, 0, height, 8, 6, 0, 0, 0])),
  chunk('IDAT', zlib.deflateSync(Buffer.concat(scanlines), {level: 9})),
  chunk('IEND', Buffer.alloc(0))
]);
const output = 'src/main/resources/assets/starcrowndoll/textures/item/minggulong_spawn_egg.png';
fs.mkdirSync(require('path').dirname(output), {recursive: true});
fs.writeFileSync(output, png);
console.log(`Wrote ${output} (${png.length} bytes)`);
