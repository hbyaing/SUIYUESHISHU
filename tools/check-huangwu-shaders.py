"""Use Khronos glslangValidator (not vendor extensions) to check GLSL 150 and the old regression."""
import argparse, pathlib, subprocess, tempfile

p=argparse.ArgumentParser();p.add_argument('--validator',default='glslangValidator');args=p.parse_args()
root=pathlib.Path(__file__).resolve().parents[1]
shader=root/'src/main/resources/assets/starcrowndoll/shaders/core'
with tempfile.TemporaryDirectory(prefix='huangwu-glsl-') as tmp:
    tmp=pathlib.Path(tmp)
    frag=(shader/'huangwu.fsh').read_text(encoding='utf-8-sig')
    assert 'float noise3(' not in frag, 'Built-in noise3 collision returned'
    (tmp/'huangwu.vert').write_text((shader/'huangwu.vsh').read_text(encoding='utf-8-sig'),encoding='utf8')
    (tmp/'huangwu.frag').write_text(frag,encoding='utf8')
    subprocess.run([args.validator,'-l',str(tmp/'huangwu.vert'),str(tmp/'huangwu.frag')],check=True)
    bad=frag.replace('scHwNoise3D(', 'noise3(')
    assert bad!=frag
    (tmp/'old-collision.frag').write_text(bad,encoding='utf8')
    result=subprocess.run([args.validator,str(tmp/'old-collision.frag')],capture_output=True,text=True)
    assert result.returncode!=0 and 'overloaded functions must have the same return type' in result.stdout,result.stdout
    print('PASS: fixed vertex/fragment link as GLSL 150; original return-type collision is rejected.')
