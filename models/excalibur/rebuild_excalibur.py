"""Rebuild only Excalibur in the open Blender project.

blender -b Excalibur_and_Avalon.blend -P rebuild_excalibur.py -- --publish
For an inspection copy, pass --draft PATH and optionally --atlas PATH instead.
"""
import argparse
import math
from pathlib import Path
import sys

import bmesh
import bpy
from mathutils import Matrix, Vector
from mathutils.bvhtree import BVHTree
from mathutils.geometry import tessellate_polygon


parser = argparse.ArgumentParser()
parser.add_argument("--draft", type=Path)
parser.add_argument("--atlas", type=Path)
parser.add_argument("--publish", action="store_true")
args = parser.parse_args(sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else [])
source = Path(__file__).resolve().parent
project = source / "Excalibur_and_Avalon.blend"
resources = source.parents[1] / "src/main/resources/assets/starcrowndoll"
if not args.draft and not args.publish:
    parser.error("Pass --draft PATH or --publish")
destination = project if args.publish else args.draft.resolve()
atlas_path = args.atlas or (resources / "textures/item/excalibur_sword_atlas.png")
sword = bpy.data.collections["Excalibur · sword"]
sheath = bpy.data.collections["Avalon · scabbard"]
scene = bpy.context.scene
unit = 1.28 * (15 / 16) / 466
for obj in list(sword.objects):
    bpy.data.objects.remove(obj, do_unlink=True)
bpy.data.orphans_purge(do_recursive=True)

atlas = bpy.data.images.load(str(atlas_path.resolve()), check_existing=False)
atlas.name = "excalibur_sword_atlas.png"
atlas.pack()


def surface(name, metal, roughness, coat):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    shader = next(node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    shader.inputs["Metallic"].default_value = metal
    shader.inputs["Roughness"].default_value = roughness
    shader.inputs["Coat Weight"].default_value = coat
    shader.inputs["Coat Roughness"].default_value = .23
    texture = material.node_tree.nodes.new("ShaderNodeTexImage")
    texture.image = atlas
    material.node_tree.nodes.active = texture
    material.node_tree.links.new(texture.outputs["Color"], shader.inputs["Base Color"])
    return material


steel = surface("Excalibur · tempered steel", .70, .30, .08)
gold = surface("Excalibur · satin gilt", .62, .31, .12)
blue = surface("Excalibur · blue grip lacquer", .25, .245, .17)
crest_metal = surface("Excalibur · gold and fired enamel", .43, .27, .15)
guard_finish = surface("Excalibur · polished guard and blue enamel", .83, .23, .15)
# Gold reflects more light than the indigo channel cast into its face.
guard_nodes = guard_finish.node_tree.nodes
guard_links = guard_finish.node_tree.links
guard_texture = guard_nodes.active
guard_shader = next(n for n in guard_nodes if n.type == "BSDF_PRINCIPLED")
guard_channels = guard_nodes.new("ShaderNodeSeparateColor")
guard_links.new(guard_texture.outputs["Color"], guard_channels.inputs["Color"])
for key, enamel_value, gilt_value in (("Metallic", .20, .83), ("Roughness", .32, .225)):
    ramp = guard_nodes.new("ShaderNodeMapRange")
    ramp.inputs["From Min"].default_value = .065
    ramp.inputs["From Max"].default_value = .38
    ramp.inputs["To Min"].default_value = enamel_value
    ramp.inputs["To Max"].default_value = gilt_value
    guard_links.new(guard_channels.outputs["Red"], ramp.inputs["Value"])
    guard_links.new(ramp.outputs["Result"], guard_shader.inputs[key])
guard_nodes.active = guard_texture


def pixel(x, y):
    return ((x + .5) / 512, 1 - (y + .5) / 1024)


def swatch(x, index, z):
    return pixel(x + (index % 3) - 1, 888 + max(0., min(1., z / 170)) * 80)


class Part:
    def __init__(self):
        self.vertices = []
        self.faces = []
        self.uv = []
        self.smooth = []

    def vertex(self, x, depth, z):
        self.vertices.append((x, depth, z))
        return len(self.vertices) - 1

    def face(self, indices, texcoords, smooth=False):
        self.faces.append(tuple(indices))
        self.uv.append(tuple(texcoords))
        self.smooth.append(smooth)

    def finish(self, name, material, sheathed=False):
        mesh = bpy.data.meshes.new(name)
        mesh.from_pydata([(x * unit, -d * unit, z * unit) for x, d, z in self.vertices], [], self.faces)
        mesh.materials.append(material)
        layer = mesh.uv_layers.new(name="Excalibur · shared atlas")
        for poly, texcoords, smooth in zip(mesh.polygons, self.uv, self.smooth):
            poly.use_smooth = smooth
            for loop, uv in zip(poly.loop_indices, texcoords):
                layer.data[loop].uv = uv
        bm = bmesh.new()
        bm.from_mesh(mesh)
        bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
        assert all(edge.is_manifold for edge in bm.edges), name
        bm.to_mesh(mesh)
        bm.free()
        mesh.update()
        obj = bpy.data.objects.new(name, mesh)
        sword.objects.link(obj)
        obj.location = sword["studio_offset"]
        if sheathed:
            obj["visible_when_sheathed"] = True
        return obj


# Each shoulder and central ridge is shared by the planes on both sides.
# A single terminal vertex closes the taper. The spine runs to that vertex.
p = Part()
cross = [(-1, 0), (-.83, .295), (-.61, .455), (0, 1), (.61, .455), (.83, .295),
         (1, 0), (.83, -.295), (.61, -.455), (0, -1), (-.61, -.455), (-.83, -.295)]
tip_start, tip_bend, tip_end = 552., 580., 626.
tip_width, tip_ratio = 14.3, 2.9 / 14.3


def grind_profile(z):
    """Return the half-width and its tangent for the convex tip grind."""
    t = z - tip_start
    if z <= tip_bend:
        return tip_width - .0184*t - .00030*t*t, -.0184 - .00060*t
    t = z - tip_bend
    span = tip_end - tip_bend
    curvature = (13.5496 - .0352*span) / (span*span)
    return 13.5496 - .0352*t - curvature*t*t, -.0352 - 2*curvature*t


blade_stations = [(140, 19.9, 4.05), (218, 19.0, 3.98), (348, 17.5, 3.58),
                  (469, 15.66, 3.23)]
for station in (552, 580, 585, 590, 595, 600, 605, 610, 615, 620, 623):
    width, _ = grind_profile(station)
    blade_stations.append((station, width, width*tip_ratio))
rows = [[p.vertex(nx * w, nd * d, z) for nx, nd in cross] for z, w, d in blade_stations]


def blade_uv(fraction, height, reverse):
    if height >= tip_start:
        # Above the shoulder the map uses physical X and Z. Its silver field
        # narrows with the blade. Both triangles of every quad use the exact
        # same planar map, including the last triangles at the shared point.
        width, _ = grind_profile(height)
        fraction *= width / tip_width
    return pixel(8 + 207 * (.5 + (-1 if reverse else 1) * fraction * .5),
                 8 + 1007 * (626 - height) / 486)


for i, (a, b) in enumerate(zip(rows, rows[1:])):
    for j in range(len(cross)):
        k = (j + 1) % len(cross)
        uv = [blade_uv(cross[v][0], blade_stations[row][0], j >= 6)
              for row, v in ((i, j), (i, k), (i + 1, k), (i + 1, j))]
        p.face((a[j], a[k], b[k], b[j]), uv)
tip = p.vertex(0, 0, 626)
for j, vertex in enumerate(rows[-1]):
    k = (j + 1) % len(cross)
    p.face((vertex, rows[-1][k], tip), (blade_uv(cross[j][0], blade_stations[-1][0], j >= 6),
           blade_uv(cross[k][0], blade_stations[-1][0], j >= 6), blade_uv(0, 626, j >= 6)))
p.face(rows[0], [swatch(422, i, 140) for i in range(len(cross))])
blade = p.finish("01 · Excalibur | forged blade and central ridge", steel)
blade_mesh = blade.data
split_normals = [None] * len(blade_mesh.loops)
for polygon in blade_mesh.polygons:
    verts = tuple(polygon.vertices)
    levels = [blade_mesh.vertices[v].co.z / unit for v in verts]
    if min(levels) < tip_start - .001:
        for loop in polygon.loop_indices:
            split_normals[loop] = polygon.normal.copy()
        continue
    # Keep a hard split between neighbouring longitudinal grinds, while
    # sharing their analytical tangent across successive transverse sections.
    polygon.use_smooth = True
    indices = {v % len(cross) for v in verts if v != tip}
    facet = next(j for j in indices if (j+1) % len(cross) in indices)
    x0, y0 = cross[facet]
    x1, y1 = cross[(facet+1) % len(cross)]
    tangent_across = Vector((x1-x0, -(y1-y0)*tip_ratio, 0))
    for loop in polygon.loop_indices:
        v = blade_mesh.loops[loop].vertex_index
        z = blade_mesh.vertices[v].co.z / unit
        _, slope = grind_profile(z)
        along = Vector((x0*slope, -y0*tip_ratio*slope, 1))
        split_normals[loop] = tangent_across.cross(along).normalized()
blade_mesh.normals_split_custom_set(split_normals)
blade_mesh.update()


def turned_piece(name, stations, material, kind):
    p = Part()
    count = 16
    start, end = stations[0][0], stations[-1][0]
    rings = []
    for z, w, d, _ in stations:
        rings.append([p.vertex(w * math.sin(j * math.tau / count), d * math.cos(j * math.tau / count), z)
                      for j in range(count)])

    def uv(i, j, swatch_id):
        t = (stations[i][0] - start) / (end - start)
        if kind == "grip":
            return pixel(226 + 281 * j / count, 842 - 322 * t)
        return pixel(swatch_id + 10 * math.cos(j * math.tau / count), 991 - 111 * t)

    for i, (a, b) in enumerate(zip(rings, rings[1:])):
        for j in range(count):
            k = (j + 1) % count
            shade = stations[i][3]
            p.face((a[j], a[k], b[k], b[j]),
                   [uv(v, h, shade) for v, h in ((i, j), (i, j+1), (i+1, j+1), (i+1, j))], True)
    for row, ring in ((0, rings[0]), (-1, rings[-1])):
        centre = p.vertex(0, 0, stations[row][0])
        for j in range(count):
            k = (j + 1) % count
            p.face((ring[j], ring[k], centre), (swatch(278, j, 20), swatch(278, k, 20), swatch(242, j, 18)))
    return p.finish(name, material, True)


grip = turned_piece("02 · Excalibur | waisted indigo grip",
    [(7.9, 10.7, 8.15, 0), (12.8, 11.05, 8.68, 0), (26, 10.46, 8.28, 0),
     (43, 9.28, 7.65, 0), (62, 8.88, 7.46, 0), (78, 9.15, 7.72, 0),
     (94, 10.09, 8.24, 0), (108, 11.64, 9.15, 0), (115.5, 12.0, 9.48, 0)], blue, "grip")
turned_piece("03 · Excalibur | turned pommel and inset rim",
    [(0, 8.04, 6.67, 278), (.65, 10.73, 8.11, 242), (2.45, 12.0, 9.28, 278),
     (6.95, 12.31, 9.47, 278), (8.15, 12.05, 9.32, 314), (8.77, 11.61, 8.91, 314),
     (9.5, 11.8, 9.05, 242), (10.8, 11.70, 8.93, 278), (11.49, 10.67, 8.43, 278)], gold, "gold")
turned_piece("04 · Excalibur | flared grip collar",
    [(111.7, 11.63, 9.34, 314), (112.25, 12.62, 10.06, 242), (113.78, 13.06, 10.44, 278),
     (117.82, 12.72, 10.19, 278), (119.83, 11.8, 9.98, 278), (121.9, 10.69, 9.48, 314)],
    gold, "gold")


# Both wings and the hub form one forging. The quillon terminal slopes towards
# the blade. Rounded boundary facets surround two shallow convex gold fields.
p = Part()
right = [(0, 0, 120.8, 143.7, 11.0),
         (16, 14, 121.54, 144.72, 10.67), (25, 21.5, 122.7, 145.94, 10.20),
         (32, 27, 124.0, 147.4, 9.70),
         (46, 39, 127.7, 150.9, 8.65), (53, 45.0, 130.2, 153.18, 8.0),
         (59, 50, 132.5, 155.2, 7.56), (65.8, 55.5, 135.5, 158.0, 7.1),
         (71.4, 60.35, 138.3, 161.15, 6.68), (72.35, 60.88, 139.0, 161.48, 5.94)]
stations = [(-a, -b, low, high, d) for a, b, low, high, d in reversed(right[1:])] + right
section = [(0,.0), (.025,.68), (.081,.91), (.18,.986), (.53,1.045), (.86,.988),
           (.948,.90), (.983,.66), (1,0), (.983,-.66), (.948,-.90), (.86,-.988),
           (.53,-1.045), (.18,-.986), (.081,-.91), (.025,-.68)]
rings = []
for xl, xu, low, high, depth in stations:
    rings.append([p.vertex(xl + (xu-xl)*v, d*depth, low+(high-low)*v) for v, d in section])


def guard_uv(index, reverse):
    x, _, z = p.vertices[index]
    return pixel(225 + (x * (-1 if reverse else 1) + 72.4) * 282 / 144.8, 166 - (z - 120) * 158 / 42)


for a, b in zip(rings, rings[1:]):
    for j in range(len(section)):
        k = (j+1) % len(section)
        face = (a[j], a[k], b[k], b[j])
        if j in (1, 2, 3, 4, 5, 6, 9, 10, 11, 12, 13, 14):
            uv = [guard_uv(v, j >= 8) for v in face]
        else:
            uv = [swatch(278 if j in (0,15) else 242, i, p.vertices[v][2]) for i,v in enumerate(face)]
        p.face(face, uv, True)
for ring in (rings[0], rings[-1]):
    centre = p.vertex(*(sum(p.vertices[i][axis] for i in ring)/len(ring) for axis in range(3)))
    for j in range(len(section)):
        k = (j+1)%len(section)
        p.face((ring[j], ring[k], centre), [swatch(278, v, 155) for v in (j,k,2)])
guard = p.finish("05 · Excalibur | crowned swept crossguard", guard_finish, True)


# Concave silhouette from the hilt photograph. The two gold prongs emerge
# from the central socket. Relief and the blue fields share one painted front.
p = Part()
half = [(0,142), (21.4,142), (21.7,154), (20.3,181.5), (18.6,188),
        (17.9,182), (17.6,154), (16.3,155.1), (16.05,199.3), (15.95,205),
        (13.0,209.5), (8.3,215), (3.8,220), (0,226)]
loop = half + [(-x,z) for x,z in reversed(half[1:-1])]


def inset(points, amount):
    result = []
    for i, here in enumerate(points):
        h, a, b = Vector(here), Vector(points[i-1]), Vector(points[(i+1)%len(points)])
        ab, bc = (h-a).normalized(), (b-h).normalized()
        na, nb = Vector((-ab.y,ab.x)), Vector((-bc.y,bc.x))
        bisector = (na+nb).normalized()
        distance = min(1.5, amount / max(.33, bisector.dot(na)))
        result.append(tuple(h + bisector*distance))
    return result


inner = inset(loop, .51)
out_front, in_front, out_back, in_back = [], [], [], []
for (x,z),(xi,zi) in zip(loop,inner):
    d = 5.57 - abs(x)*.061 - max(0,z-200)*.016
    di = 5.57 - abs(xi)*.061 - max(0,zi-200)*.016
    out_front.append(p.vertex(x,d-.72,z))
    in_front.append(p.vertex(xi,di,zi))
    out_back.append(p.vertex(x,-d+.72,z))
    in_back.append(p.vertex(xi,-di,zi))


def crest_uv(vertex, reverse=False):
    x,_,z = p.vertices[vertex]
    return pixel(230 + ((-x if reverse else x) + 21.7) * 183 / 43.4, 504 - (z-142)*324/84)


for j in range(len(loop)):
    k=(j+1)%len(loop)
    for face, reverse in (((out_front[j],out_front[k],in_front[k],in_front[j]),False),
                          ((out_back[j],out_back[k],in_back[k],in_back[j]),True)):
        p.face(face, [crest_uv(v,reverse) for v in face])
    face=(out_front[j],out_front[k],out_back[k],out_back[j])
    p.face(face,[swatch(278,q,p.vertices[v][2]) for q,v in enumerate(face)])

for ids, reverse in ((in_front,False),(in_back,True)):
    for half_ids in (ids[:len(half)], [ids[0],*reversed(ids[len(half)-1:])]):
        poly = [Vector((p.vertices[v][0],p.vertices[v][2],0)) for v in half_ids]
        for triplet in tessellate_polygon([poly]):
            face = [half_ids[j] for j in triplet]
            p.face(face,[crest_uv(v,reverse) for v in face])
crest = p.finish("06 · Excalibur | royal-blue and gold sword socket",crest_metal)


bpy.context.view_layer.update()
shell = next(o for o in sheath.objects if o.type == "MESH")
def collision_mesh(obj, translation):
    origin = Vector(sheath["studio_offset"] if obj is shell else sword["studio_offset"])
    vertices = [obj.matrix_world @ v.co - origin + translation for v in obj.data.vertices]
    return BVHTree.FromPolygons(vertices, [tuple(f.vertices) for f in obj.data.polygons])

shell_bvh = collision_mesh(shell, Vector((0,0,0)))
for part in (blade, crest):
    overlaps = shell_bvh.overlap(collision_mesh(part, Vector((0,0,-144*unit))))
    print("Blade cavity intersections", part.name, len(overlaps))
    assert not overlaps, part.name
assert abs(blade.dimensions.z / unit - 486) < .001
grip["grip_axis"] = "Centrally aligned with the original two player hand positions."
guard["design_reference"] = "Aniplex USA Excalibur PROPLICA standard gallery 1, Good Smile Saber Triumphant Excalibur rear."
scene["sword_finish"] = "Independent six-piece mesh; all game surfaces share an opaque 512 × 1024 atlas."

# Start the editable file on the sword in a textured orthographic view.
for obj in bpy.context.selected_objects:
    obj.select_set(False)
bpy.context.view_layer.objects.active = guard
view = Vector((.23,1,-.09)).to_track_quat("-Z","Y") @ Matrix.Rotation(math.radians(64),4,"Z").to_quaternion()
for screen in bpy.data.screens:
    for area in screen.areas:
        if area.type == "VIEW_3D":
            space = area.spaces.active
            space.region_3d.view_rotation = view
            space.region_3d.view_location = Vector((.10, 0, 310*unit))
            space.region_3d.view_distance = 1.54
            space.region_3d.view_perspective = "ORTHO"
            space.shading.type = "SOLID"
            space.shading.color_type = "TEXTURE"
            space.shading.light = "STUDIO"
            space.shading.show_shadows = True
            space.overlay.show_overlays = False
scene.view_settings.view_transform="AgX"
scene.view_settings.look="AgX - Medium High Contrast"
scene.view_settings.exposure=-.48
camera=scene.camera
centre=Vector((0,0,313*unit))
camera.location=centre+Vector((-.32,-2.90,.26))
camera.rotation_euler=((centre-camera.location).to_track_quat("-Z","Y").to_matrix()
                       @Matrix.Rotation(math.radians(58),3,"Z")).to_euler()
camera.data.type="ORTHO"
bpy.context.view_layer.update()
visible=[camera.matrix_world.inverted()@(o.matrix_world@v.co) for o in [*sword.objects,*sheath.objects]
         for v in o.data.vertices]
x0,x1=min(v.x for v in visible),max(v.x for v in visible)
y0,y1=min(v.y for v in visible),max(v.y for v in visible)
camera.location+=camera.rotation_euler.to_matrix()@Vector(((x0+x1)/2,(y0+y1)/2,0))
camera.data.ortho_scale=max(x1-x0,(y1-y0)*1550/950)*1.12
scene.render.resolution_x=1550
scene.render.resolution_y=950
scene.render.resolution_percentage=100
bpy.context.preferences.filepaths.save_version=0
bpy.ops.wm.save_as_mainfile(filepath=str(destination),compress=True)
if args.publish:
    exporter = source / "export_forge.py"
    sys.argv.append("--sword-only")
    exec(compile(exporter.read_bytes(),str(exporter),"exec"),{"__name__":"__main__"})
