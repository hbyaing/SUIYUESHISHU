"""Export the three item meshes from the open Excalibur and Avalon Blender project."""

from pathlib import Path
import sys

import bpy
from mathutils import Vector


PROJECT = Path(bpy.data.filepath).resolve()
ASSETS = PROJECT.parents[2] / 'src/main/resources/assets/starcrowndoll'
OUTPUT = ASSETS / 'models/item'
UNITS_PER_BLOCK = 466.0
# Blender shows the held length in metres, including the player's 15/16 render scale.
# Export to the model's local frame. The item JSON reapplies the hand transform at runtime.
METRES_PER_UNIT = 1.28 * (15 / 16) / UNITS_PER_BLOCK


def item_parts(collection_name, length_offset=0, hilt_only=False):
    collection = bpy.data.collections[collection_name]
    origin = Vector(collection['studio_offset'])
    for obj in collection.all_objects:
        if obj.type != 'MESH' or (hilt_only and not obj.get('visible_when_sheathed')):
            continue
        yield obj, origin, length_offset


def texture_name(material):
    texture = next(node.image for node in material.node_tree.nodes
                   if node.type == 'TEX_IMAGE' and node.image)
    return Path(texture.name).stem


def export_model(name, parts, grip_pivot, texture_names):
    graph = bpy.context.evaluated_depsgraph_get()
    vertex_offset = uv_offset = normal_offset = face_count = 0
    lines = ['# Excalibur_and_Avalon.blend', 'mtllib excalibur_materials.mtl']

    for index, (obj, origin, length_offset) in enumerate(parts, 1):
        evaluated = obj.evaluated_get(graph)
        mesh = evaluated.to_mesh()
        try:
            mesh.calc_loop_triangles()
            world = evaluated.matrix_world
            normal_matrix = world.to_3x3().inverted().transposed()
            mirrored = world.to_3x3().determinant() < 0
            lines.append(f'o part_{index}')
            for vertex in mesh.vertices:
                x, y, z = (world @ vertex.co - origin) / METRES_PER_UNIT
                point = (x, z + length_offset - grip_pivot, -y)
                lines.append('v ' + ' '.join(f'{0.5 + coord/UNITS_PER_BLOCK:.6f}' for coord in point))
            for loop in mesh.uv_layers.active.data:
                lines.append('vt %.6f %.6f' % tuple(loop.uv))
            for polygon in mesh.polygons:
                x, y, z = (normal_matrix @ polygon.normal).normalized()
                lines.append(f'vn {x:.6f} {z:.6f} {-y:.6f}')
            # Keep the authored corner normals on rounded metal. The flat sword retains its face normals.
            corner_normals = {}
            smooth_normals = {}
            for polygon in mesh.polygons:
                if polygon.use_smooth:
                    for loop in polygon.loop_indices:
                        x, y, z = (normal_matrix @ mesh.corner_normals[loop].vector).normalized()
                        key = (round(x,6), round(z,6), round(-y,6))
                        if key not in smooth_normals:
                            smooth_normals[key] = len(mesh.polygons) + len(smooth_normals) + 1
                            lines.append('vn %.6f %.6f %.6f' % key)
                        corner_normals[loop] = smooth_normals[key]

            triangles = {}
            for triangle in mesh.loop_triangles:
                triangles.setdefault(triangle.polygon_index, []).append(triangle.loops)
            previous_texture = None
            for polygon in mesh.polygons:
                texture = texture_name(mesh.materials[polygon.material_index])
                if texture != previous_texture:
                    lines.append(f'usemtl {texture}')
                    texture_names.add(texture)
                    previous_texture = texture
                faces = [polygon.loop_indices] if len(polygon.vertices) <= 4 else triangles[polygon.index]
                for loops in faces:
                    ordered = reversed(loops) if mirrored else loops
                    corners = []
                    for loop_index in ordered:
                        vertex = vertex_offset + mesh.loops[loop_index].vertex_index + 1
                        uv = uv_offset + loop_index + 1
                        normal = normal_offset + corner_normals.get(loop_index, polygon.index + 1)
                        corners.append(f'{vertex}/{uv}/{normal}')
                    lines.append('f ' + ' '.join(corners))
                    face_count += 1
            vertex_offset += len(mesh.vertices)
            uv_offset += len(mesh.loops)
            normal_offset += len(mesh.polygons) + len(smooth_normals)
        finally:
            evaluated.to_mesh_clear()
    (OUTPUT / (name + '.obj')).write_text('\n'.join(lines) + '\n', encoding='ascii')
    print(f'{name}: {vertex_offset} vertices, {face_count} Forge faces')


textures = set()
sword = 'Excalibur · sword'
scabbard = 'Avalon · scabbard'
if '--avalon-only' in sys.argv:
    # Preserve the sword's existing byte stream when authoring only its scabbard.
    for obj, _, _ in item_parts(sword):
        used = {poly.material_index for poly in obj.data.polygons}
        textures.update(texture_name(obj.data.materials[i]) for i in used)
else:
    export_model('excalibur', item_parts(sword), 62, textures)
if '--sword-only' not in sys.argv:
    export_model('avalon', item_parts(scabbard), 90, textures)
assembled = [*item_parts(scabbard), *item_parts(sword, -144, hilt_only=True)]
export_model('avalon_sheathed', assembled, -76, textures)
materials = [f'newmtl {name}\nKd 1 1 1\nKa 0 0 0\nmap_Kd starcrowndoll:item/{name}\n'
             for name in sorted(textures)]
(OUTPUT / 'excalibur_materials.mtl').write_text('\n'.join(materials), encoding='ascii')
