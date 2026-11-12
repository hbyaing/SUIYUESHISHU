import fs from 'node:fs';import path from 'node:path';import zlib from 'node:zlib';import {spawnSync} from 'node:child_process';import {fileURLToPath} from 'node:url';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..'),forge=path.join(root,'forge-1.20.1'),build=path.join(forge,'build','preview-export');
fs.mkdirSync(build,{recursive:true});
function run(exe,args){const r=spawnSync(exe,args,{stdio:'inherit'});if(r.error)throw r.error;if(r.status!==0)throw Error(exe+' failed: '+r.status)}
run('javac',['-encoding','UTF-8','-d',build,path.join(forge,'src/main/java/dev/minggulong/client/HuangWuGeometry.java'),path.join(root,'scripts/HuangWuExport.java')]);
const bin=path.join(build,'frames.bin');run('java',['-cp',build,'HuangWuExport',bin]);
fs.writeFileSync(path.join(root,'preview/frames.bin.gz'),zlib.gzipSync(fs.readFileSync(bin),{level:9}));
console.log('Updated preview/frames.bin.gz from Forge geometry.');
