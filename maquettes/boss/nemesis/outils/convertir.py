# Conversion du GLB Meshy de NÉMÉSIS (voxel, rig Mixamo) en modèle Minecraft.
#
#   blender -b --factory-startup --python convertir.py -- <source.glb> <dossier_sortie> [hauteur_en_voxels]
#
# Sortie :
#   nemesis.bbmodel           projet Blockbench (os, cubes, texture, 7 animations)
#   nemesis-anim.json         données pour le plugin : os, pivots, matrices par tick (20 i/s)
#   pack/assets/orbitmmo/...  un modèle d'objet par os + la texture (pour des ItemDisplay)
#   apercu/*.png              rendus de contrôle reconstruits à partir des cubes générés
#
# Repères Minecraft : 1 voxel = 1 unité de modèle (1/16 de bloc à l'échelle 1), Y en haut,
# avant du boss = -Z (nord), droite du boss = +X, pieds à Y = 0, centré en X/Z.
import bpy, sys, os, json, math, uuid, base64, zlib, struct
import numpy as np
from mathutils import Vector, Matrix

argv = sys.argv[sys.argv.index("--") + 1:]
SRC, OUT = argv[0], argv[1]
RES = int(argv[2]) if len(argv) > 2 else 64
NS, NOM = "orbitmmo", "nemesis"
os.makedirs(OUT, exist_ok=True)

bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=SRC)
for o in list(bpy.data.objects):
    if o.name.startswith("Icosphere"):
        bpy.data.objects.remove(o)
arm = next(o for o in bpy.data.objects if o.type == 'ARMATURE')
mobj = next(o for o in bpy.data.objects if o.type == 'MESH')
scene = bpy.context.scene

# ---------------------------------------------------------------- os Minecraft
OS = ["corps", "torse", "tete", "orbe",
      "bras_g", "avantbras_g", "bras_d", "avantbras_d",
      "cuisse_g", "jambe_g", "cuisse_d", "jambe_d"]
PARENT = {"corps": None, "torse": "corps", "tete": "torse", "orbe": "tete",
          "bras_g": "torse", "avantbras_g": "bras_g", "bras_d": "torse", "avantbras_d": "bras_d",
          "cuisse_g": "corps", "jambe_g": "cuisse_g", "cuisse_d": "corps", "jambe_d": "cuisse_d"}
REP = {"corps": "Hips", "torse": "Spine2", "tete": "Head", "orbe": "Head",
       "bras_g": "LeftArm", "avantbras_g": "LeftForeArm", "bras_d": "RightArm", "avantbras_d": "RightForeArm",
       "cuisse_g": "LeftUpLeg", "jambe_g": "LeftLeg", "cuisse_d": "RightUpLeg", "jambe_d": "RightLeg"}
PIVOT_DE = dict(REP, torse="Spine1")   # le torse plie à la taille, pas sous le cou
BI = {b: i for i, b in enumerate(OS)}

def bone_of(n):
    n = n.split(":")[-1]
    if n in ("Hips", "Spine"): return "corps"
    if n in ("Spine1", "Spine2", "LeftShoulder", "RightShoulder"): return "torse"
    if n in ("Neck", "Head", "HeadTop_End", "headfront"): return "tete"
    for side, s in (("Left", "g"), ("Right", "d")):
        if n == side + "Arm": return "bras_" + s
        if n.startswith(side + "ForeArm") or n.startswith(side + "Hand"): return "avantbras_" + s
        if n == side + "UpLeg": return "cuisse_" + s
        if n.startswith(side + "Leg") or n.startswith(side + "Foot") or n.startswith(side + "Toe"): return "jambe_" + s
    return "corps"

def pbone(short):
    for b in arm.pose.bones:
        if b.name.split(":")[-1] == short: return b
    raise KeyError(short)

# ---------------------------------------------------------------- maillage au repos
arm.data.pose_position = 'REST'
bpy.context.view_layer.update()
dg = bpy.context.evaluated_depsgraph_get()
ev = mobj.evaluated_get(dg)
me = ev.to_mesh()
me.calc_loop_triangles()
nv = len(me.vertices)
P = np.empty(nv * 3, np.float64); me.vertices.foreach_get("co", P); P = P.reshape(-1, 3)
mw = np.array(mobj.matrix_world)
P = P @ mw[:3, :3].T + mw[:3, 3]

zmin, zmax = P[:, 2].min(), P[:, 2].max()
cx = (P[:, 0].min() + P[:, 0].max()) / 2
cy = (P[:, 1].min() + P[:, 1].max()) / 2
VOX = (zmax - zmin) / RES
C3 = Matrix(((-1, 0, 0), (0, 0, 1), (0, 1, 0)))          # Blender -> Minecraft (X=-x, Y=z, Z=y)
A = (Matrix.Diagonal((1 / VOX,) * 3) @ C3).to_4x4() @ Matrix.Translation((-cx, -cy, -zmin))
A_inv = A.inverted()
Cn = np.array(C3) / VOX
Q = (P - np.array([cx, cy, zmin])) @ Cn.T                  # sommets en unités Minecraft
print(f"voxel = {VOX*100:.2f} cm, hauteur {RES} voxels")

# poids par os Minecraft
gmap = {g.index: BI[bone_of(g.name)] for g in mobj.vertex_groups}
Wv = np.zeros((nv, len(OS)), np.float32)
for v in mobj.data.vertices:
    for g in v.groups:
        if g.group in gmap: Wv[v.index, gmap[g.group]] += g.weight

# texture
mat = mobj.data.materials[0]
img = None
for n in mat.node_tree.nodes:
    if n.type == 'TEX_IMAGE' and n.image and "metallic" not in n.image.name and "normal" not in n.image.name:
        img = n.image
TW, TH = img.size
tex = np.empty(TW * TH * 4, np.float32); img.pixels.foreach_get(tex); tex = tex.reshape(TH, TW, 4)
print("texture", img.name, TW, TH)

uvl = me.uv_layers.active.data
UV = np.empty(len(uvl) * 2, np.float32); uvl.foreach_get("uv", UV); UV = UV.reshape(-1, 2)

# ---------------------------------------------------------------- échantillonnage de la surface
pat_cache = {}
def pattern(n):
    if n not in pat_cache:
        s, t = [], []
        for i in range(n + 1):
            for j in range(n + 1 - i):
                s.append(i / n); t.append(j / n)
        pat_cache[n] = (np.array(s), np.array(t))
    return pat_cache[n]

pts, uvs, wts = [], [], []
for tri in me.loop_triangles:
    vi = list(tri.vertices); li = list(tri.loops)
    a, b, c = Q[vi[0]], Q[vi[1]], Q[vi[2]]
    L = max(np.linalg.norm(b - a), np.linalg.norm(c - a), np.linalg.norm(c - b))
    n = max(1, int(math.ceil(L * 3)))
    s, t = pattern(n)
    r = 1 - s - t
    pts.append(np.outer(r, a) + np.outer(s, b) + np.outer(t, c))
    uvs.append(np.outer(r, UV[li[0]]) + np.outer(s, UV[li[1]]) + np.outer(t, UV[li[2]]))
    wts.append(np.outer(r, Wv[vi[0]]) + np.outer(s, Wv[vi[1]]) + np.outer(t, Wv[vi[2]]))
pts = np.concatenate(pts); uvs = np.concatenate(uvs); wts = np.concatenate(wts)
print("échantillons", len(pts))

px = np.clip(((uvs[:, 0] % 1.0) * TW).astype(int), 0, TW - 1)
py = np.clip(((uvs[:, 1] % 1.0) * TH).astype(int), 0, TH - 1)
col = tex[py, px, :3]

vox = np.floor(pts).astype(int)
g0 = vox.min(0) - 1
dims = vox.max(0) - g0 + 2
idx = vox - g0
lin = (idx[:, 0] * dims[1] + idx[:, 1]) * dims[2] + idx[:, 2]
# couleur du voxel = teinte majoritaire de ses échantillons (8 niveaux par canal), moyennée
code = np.clip((col * 8).astype(int), 0, 7)
key = lin.astype(np.int64) * 512 + (code[:, 0] * 64 + code[:, 1] * 8 + code[:, 2])
uk, ik, ck = np.unique(key, return_inverse=True, return_counts=True)
csum = np.zeros((len(uk), 3)); np.add.at(csum, ik, col)
kv = uk // 512
order = np.lexsort((-ck, kv))
kv_s = kv[order]
first = np.ones(len(kv_s), bool); first[1:] = kv_s[1:] != kv_s[:-1]
surf_lin = kv_s[first]
surf_col = (csum[order] / ck[order][:, None])[first]
# os : somme des poids des échantillons du voxel
uniq, inv = np.unique(lin, return_inverse=True)
wsum = np.zeros((len(uniq), len(OS)), np.float32)
np.add.at(wsum, inv, wts)
surf_bone = wsum.argmax(1)        # même ordre que uniq == surf_lin (lin trié)
assert np.array_equal(uniq, surf_lin)

N = int(dims.prod())
surface = np.zeros(N, bool); surface[surf_lin] = True
surface = surface.reshape(dims)
colg = np.zeros((N, 3), np.float32); colg[surf_lin] = surf_col; colg = colg.reshape(*dims, 3)
boneg = np.full(N, -1, np.int16); boneg[surf_lin] = surf_bone; boneg = boneg.reshape(dims)

# ---------------------------------------------------------------- remplissage de l'intérieur
def shifts(a, fill):
    out = []
    for ax in range(3):
        for d in (1, -1):
            s = np.roll(a, d, axis=ax)
            sl = [slice(None)] * 3
            sl[ax] = 0 if d == 1 else -1
            s[tuple(sl)] = fill
            out.append(s)
    return out

ext = np.zeros(dims, bool)
ext[0, :, :] = ext[-1, :, :] = ext[:, 0, :] = ext[:, -1, :] = ext[:, :, 0] = ext[:, :, -1] = True
ext &= ~surface
while True:
    grow = ext.copy()
    for s in shifts(ext, False): grow |= s
    grow &= ~surface
    if grow.sum() == ext.sum(): break
    ext = grow
solid = ~ext
print("voxels surface", int(surface.sum()), "pleins", int(solid.sum()))

# os et couleur de l'intérieur : propagés depuis la surface
assigned = surface.copy()
while True:
    todo = solid & ~assigned
    if not todo.any(): break
    changed = False
    for ax in range(3):
        for d in (1, -1):
            nb_as = np.roll(assigned, d, axis=ax); nb_b = np.roll(boneg, d, axis=ax); nb_c = np.roll(colg, d, axis=ax)
            m = todo & nb_as
            if m.any():
                boneg[m] = nb_b[m]; colg[m] = nb_c[m]; assigned |= m; todo &= ~m; changed = True
    if not changed: break

# ---------------------------------------------------------------- morceaux détachés -> orbe
from collections import deque
lab = np.zeros(dims, np.int32); comps = []
sx, sy, sz = dims
for start in zip(*np.nonzero(solid)):
    if lab[start]: continue
    cid = len(comps) + 1; q = deque([start]); lab[start] = cid; members = []
    while q:
        x, y, z = q.popleft(); members.append((x, y, z))
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                for dz in (-1, 0, 1):
                    nx, ny, nz = x + dx, y + dy, z + dz
                    if 0 <= nx < sx and 0 <= ny < sy and 0 <= nz < sz and solid[nx, ny, nz] and not lab[nx, ny, nz]:
                        lab[nx, ny, nz] = cid; q.append((nx, ny, nz))
    comps.append(members)
comps.sort(key=len, reverse=True)
print("morceaux", [len(c) for c in comps[:8]])
head_y = None
for c in comps[1:]:
    ys = [m[1] for m in c]
    cyv = sum(ys) / len(ys) + g0[1]
    if len(c) >= 4 and cyv > RES * 0.8:
        for m in c: boneg[m] = BI["orbe"]
    elif len(c) < 4:
        for m in c: solid[m] = False; boneg[m] = -1
boneg[~solid] = -1

# ---------------------------------------------------------------- pivots (unités Minecraft)
def to_mc(p):
    return A @ Vector(p)
piv = {}
for b in OS:
    if b == "orbe":
        m = np.argwhere(boneg == BI["orbe"])
        piv[b] = Vector((m.mean(0) + g0 + 0.5)) if len(m) else to_mc(arm.matrix_world @ arm.data.bones[pbone("Head").name].head_local)
    else:
        bone = arm.data.bones[pbone(PIVOT_DE[b]).name]
        piv[b] = to_mc(arm.matrix_world @ bone.head_local)
piv = {b: Vector([round(c * 4) / 4 for c in v]) for b, v in piv.items()}

# ---------------------------------------------------------------- fusion en boîtes par os
DIRS = {"north": (2, -1), "south": (2, 1), "west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1)}
used = np.zeros(dims, bool)
boxes = {b: [] for b in OS}
for x in range(sx):
    for y in range(sy):
        for z in range(sz):
            bi = boneg[x, y, z]
            if bi < 0 or used[x, y, z]: continue
            def ok(x0, x1, y0, y1, z0, z1):
                blk = boneg[x0:x1, y0:y1, z0:z1]
                return (blk == bi).all() and not used[x0:x1, y0:y1, z0:z1].any()
            x1 = x + 1
            while x1 < sx and ok(x1, x1 + 1, y, y + 1, z, z + 1): x1 += 1
            z1 = z + 1
            while z1 < sz and ok(x, x1, y, y + 1, z1, z1 + 1): z1 += 1
            y1 = y + 1
            while y1 < sy and ok(x, x1, y1, y1 + 1, z, z1): y1 += 1
            used[x:x1, y:y1, z:z1] = True
            boxes[OS[bi]].append((x, x1, y, y1, z, z1))
print("boîtes", {b: len(v) for b, v in boxes.items()}, "total", sum(len(v) for v in boxes.values()))

# ---------------------------------------------------------------- faces visibles et atlas
def cell_uv(face, box, x, y, z):
    x0, x1, y0, y1, z0, z1 = box
    if face == "north": return x1 - 1 - x, y1 - 1 - y
    if face == "south": return x - x0, y1 - 1 - y
    if face == "west":  return z - z0, y1 - 1 - y
    if face == "east":  return z1 - 1 - z, y1 - 1 - y
    if face == "up":    return x - x0, z - z0
    return x - x0, z1 - 1 - z   # down

def face_cells(face, box):
    x0, x1, y0, y1, z0, z1 = box
    ax, d = DIRS[face]
    rng = [list(range(x0, x1)), list(range(y0, y1)), list(range(z0, z1))]
    rng[ax] = [(x0, y0, z0)[ax] if d < 0 else (x1, y1, z1)[ax] - 1]
    for x in rng[0]:
        for y in rng[1]:
            for z in rng[2]:
                yield x, y, z

def face_size(face, box):
    x0, x1, y0, y1, z0, z1 = box
    dx, dy, dz = x1 - x0, y1 - y0, z1 - z0
    return {"north": (dx, dy), "south": (dx, dy), "west": (dz, dy), "east": (dz, dy), "up": (dx, dz), "down": (dx, dz)}[face]

faces = []   # (bone, boxindex, face, w, h, cells[(a,b,rgb)])
for b in OS:
    bi = BI[b]
    for k, box in enumerate(boxes[b]):
        for face, (ax, d) in DIRS.items():
            vis = False; cells = []
            for (x, y, z) in face_cells(face, box):
                n = [x, y, z]; n[ax] += d
                nb = boneg[n[0], n[1], n[2]] if (0 <= n[0] < sx and 0 <= n[1] < sy and 0 <= n[2] < sz) else -1
                if nb != bi: vis = True
                a, bb = cell_uv(face, box, x, y, z)
                cells.append((a, bb, colg[x, y, z]))
            if vis:
                w, h = face_size(face, box)
                faces.append([b, k, face, w, h, cells, None])
print("faces visibles", len(faces))

PAD = 1
area = sum((f[3] + 2 * PAD) * (f[4] + 2 * PAD) for f in faces)
AW = 64
while AW * AW < area * 1.25: AW *= 2
def pack(W):
    order = sorted(range(len(faces)), key=lambda i: (-(faces[i][4]), -(faces[i][3])))
    x = y = rowh = 0
    pos = {}
    for i in order:
        w, h = faces[i][3] + 2 * PAD, faces[i][4] + 2 * PAD
        if w > W: return None
        if x + w > W: x = 0; y += rowh; rowh = 0
        pos[i] = (x, y); x += w; rowh = max(rowh, h)
    return pos, y + rowh
pos, used_h = pack(AW)
AH = 16
while AH < used_h: AH *= 2
atlas = np.zeros((AH, AW, 4), np.uint8)
for i, f in enumerate(faces):
    ox, oy = pos[i]
    w, h = f[3], f[4]
    blk = np.zeros((h, w, 3), np.float32)
    for (a, bb, c) in f[5]: blk[bb, a] = c
    blk = np.pad(blk, ((PAD, PAD), (PAD, PAD), (0, 0)), mode="edge")
    atlas[oy:oy + h + 2 * PAD, ox:ox + w + 2 * PAD, :3] = np.clip(blk * 255 + 0.5, 0, 255).astype(np.uint8)
    atlas[oy:oy + h + 2 * PAD, ox:ox + w + 2 * PAD, 3] = 255
    f[6] = (ox + PAD, oy + PAD, ox + PAD + w, oy + PAD + h)
print("atlas", AW, "x", AH)

def png_bytes(arr):
    h, w, _ = arr.shape
    raw = b"".join(b"\x00" + arr[r].tobytes() for r in range(h))
    def chunk(t, d): return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + \
        chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
PNG = png_bytes(atlas)

# ---------------------------------------------------------------- animations (matrices par tick)
def mc_world(b):
    pb = pbone(REP[b])
    W = arm.matrix_world @ pb.matrix @ pb.bone.matrix_local.inverted() @ arm.matrix_world.inverted()
    return A @ W @ A_inv

NOMS = {"Walking": "marche", "Running": "course", "Charged_Ground_Slam": "frappe_sol",
        "Charged_Spell_Cast": "sort_charge", "Charged_Spell_Cast_1": "sort_charge_long",
        "mage_soell_cast_2": "sort_2", "mage_soell_cast_3": "sort_3"}
arm.data.pose_position = 'POSE'
if arm.animation_data is None: arm.animation_data_create()
fps = scene.render.fps / scene.render.fps_base
anims = {}
for act in bpy.data.actions:
    arm.animation_data.action = act
    try:
        if arm.animation_data.action_slot is None and len(act.slots): arm.animation_data.action_slot = act.slots[0]
    except AttributeError:
        pass
    f0, f1 = act.frame_range
    dur = (f1 - f0) / fps
    ticks = max(1, int(round(dur * 20)))
    frames = []
    for t in range(ticks + 1):
        f = f0 + t / 20 * fps
        scene.frame_set(int(math.floor(f)), subframe=f - math.floor(f))
        frames.append({b: mc_world(b) for b in OS})
    nom = NOMS.get(act.name, act.name.lower())
    anims[nom] = {"source": act.name, "ticks": ticks, "frames": frames}
    hips = [fr["corps"].translation for fr in frames]
    print(f"anim {nom:18s} {ticks:3d} ticks  déplacement du bassin x[{min(h.x for h in hips):.1f},{max(h.x for h in hips):.1f}] "
          f"y[{min(h.y for h in hips):.1f},{max(h.y for h in hips):.1f}] z[{min(h.z for h in hips):.1f},{max(h.z for h in hips):.1f}]")

# ---------------------------------------------------------------- modèles d'objet (un par os)
pack_root = os.path.join(OUT, "pack", "assets", NS)
for d in ("items/boss/" + NOM, "models/item/boss/" + NOM, "textures/item/boss"):
    os.makedirs(os.path.join(pack_root, d), exist_ok=True)
with open(os.path.join(pack_root, "textures/item/boss", NOM + ".png"), "wb") as fh: fh.write(PNG)

faces_by = {}
for f in faces: faces_by.setdefault((f[0], f[1]), {})[f[2]] = f[6]

rt_bones = []
for b in OS:
    if not boxes[b]: continue
    p = piv[b]
    ext_max = 0.0
    for (x0, x1, y0, y1, z0, z1) in boxes[b]:
        for c, pc in zip(((x0 + g0[0], x1 + g0[0]), (y0 + g0[1], y1 + g0[1]), (z0 + g0[2], z1 + g0[2])), p):
            ext_max = max(ext_max, abs(c[0] - pc), abs(c[1] - pc))
    k = min(1.0, 23.5 / ext_max)
    els = []
    for bi_, box in enumerate(boxes[b]):
        fs = faces_by.get((b, bi_))
        if not fs: continue
        x0, x1, y0, y1, z0, z1 = box
        lo = [(x0 + g0[0] - p.x) * k + 8, (y0 + g0[1] - p.y) * k + 8, (z0 + g0[2] - p.z) * k + 8]
        hi = [(x1 + g0[0] - p.x) * k + 8, (y1 + g0[1] - p.y) * k + 8, (z1 + g0[2] - p.z) * k + 8]
        el = {"from": [round(v, 4) for v in lo], "to": [round(v, 4) for v in hi], "faces": {}}
        for face, r in fs.items():
            el["faces"][face] = {"uv": [round(r[0] * 16 / AW, 5), round(r[1] * 16 / AH, 5),
                                        round(r[2] * 16 / AW, 5), round(r[3] * 16 / AH, 5)], "texture": "#0"}
        els.append(el)
    model = {"textures": {"0": f"{NS}:item/boss/{NOM}", "particle": f"{NS}:item/boss/{NOM}"}, "elements": els}
    with open(os.path.join(pack_root, "models/item/boss", NOM, b + ".json"), "w") as fh: json.dump(model, fh, separators=(",", ":"))
    with open(os.path.join(pack_root, "items/boss", NOM, b + ".json"), "w") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": f"{NS}:item/boss/{NOM}/{b}"}}, fh)
    rt_bones.append({"nom": b, "parent": PARENT[b], "pivot": [round(c, 3) for c in p], "echelle_modele": round(k, 5),
                     "item_model": f"{NS}:boss/{NOM}/{b}", "cubes": len(els)})

def mat_pack(M):
    t = M.translation; q = M.to_quaternion()
    return [round(t.x, 3), round(t.y, 3), round(t.z, 3), round(q.x, 5), round(q.y, 5), round(q.z, 5), round(q.w, 5)]

runtime = {
    "nom": NOM, "unite": "1 unité = 1/16 de bloc à l'échelle 1 ; matrices = os dans l'espace du modèle (avant = -Z)",
    "hauteur": RES, "texture": f"{NS}:item/boss/{NOM}", "os": rt_bones,
    "formule": "transformation de l'ItemDisplay = Echelle(globale) * M(os, tick) * Translation(pivot) * Echelle(1/echelle_modele)",
    "animations": {n: {"source": a["source"], "ticks": a["ticks"],
                       "images": [{b: mat_pack(fr[b]) for b in OS if any(rb["nom"] == b for rb in rt_bones)} for fr in a["frames"]]}
                   for n, a in anims.items()}}
with open(os.path.join(OUT, NOM + "-anim.json"), "w", encoding="utf-8") as fh:
    json.dump(runtime, fh, ensure_ascii=False, separators=(",", ":"))

# ---------------------------------------------------------------- projet Blockbench
def U(): return str(uuid.uuid4())
elements, group_children = [], {b: [] for b in OS}
for b in OS:
    for bi_, box in enumerate(boxes[b]):
        fs = faces_by.get((b, bi_))
        if not fs: continue
        x0, x1, y0, y1, z0, z1 = box
        u = U()
        el = {"name": f"{b}_{bi_}", "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
              "allow_mirror_modeling": True,
              "from": [x0 + int(g0[0]), y0 + int(g0[1]), z0 + int(g0[2])], "to": [x1 + int(g0[0]), y1 + int(g0[1]), z1 + int(g0[2])],
              "autouv": 0, "color": BI[b] % 8, "origin": list(piv[b]), "faces": {}, "type": "cube", "uuid": u}
        for face in DIRS:
            r = fs.get(face)
            el["faces"][face] = {"uv": list(r), "texture": 0} if r else {"uv": [0, 0, 0, 0], "texture": None}
        elements.append(el); group_children[b].append(u)
guid = {b: U() for b in OS}
def group(b):
    kids = list(group_children[b]) + [group(c) for c in OS if PARENT[c] == b]
    return {"name": b, "origin": list(piv[b]), "rotation": [0, 0, 0], "color": BI[b] % 8, "uuid": guid[b], "export": True,
            "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True, "autouv": 0, "children": kids}

bb_anims = []
for n, a in anims.items():
    animators = {}
    for b in OS:
        if not group_children[b] and b != "corps": continue
        rk, pk = [], []
        for t, fr in enumerate(a["frames"]):
            Wc = fr[b]; Wp = fr[PARENT[b]] if PARENT[b] else Matrix.Identity(4)
            L = Wp.inverted() @ Wc
            R = L.to_3x3().normalized()
            p = piv[b]
            tr = L.translation - p + R @ p
            e = R.to_euler('XYZ')
            time = round(t / 20, 4)
            rk.append({"channel": "rotation", "data_points": [{"x": str(round(-math.degrees(e.x), 3)), "y": str(round(-math.degrees(e.y), 3)),
                                                               "z": str(round(math.degrees(e.z), 3))}],
                       "uuid": U(), "time": time, "color": -1, "interpolation": "linear"})
            pk.append({"channel": "position", "data_points": [{"x": str(round(-tr.x, 3)), "y": str(round(tr.y, 3)), "z": str(round(tr.z, 3))}],
                       "uuid": U(), "time": time, "color": -1, "interpolation": "linear"})
        animators[guid[b]] = {"name": b, "type": "bone", "keyframes": rk + pk}
    bb_anims.append({"uuid": U(), "name": n, "loop": "loop" if n in ("marche", "course") else "once", "override": False,
                     "length": round(a["ticks"] / 20, 4), "snapping": 20, "selected": False, "anim_time_update": "",
                     "blend_weight": "", "start_delay": "", "loop_delay": "", "animators": animators})

bbmodel = {
    "meta": {"format_version": "4.10", "model_format": "free", "box_uv": False},
    "name": NOM, "model_identifier": NOM, "visible_box": [4, 6, 2], "variable_placeholders": "",
    "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
    "resolution": {"width": AW, "height": AH},
    "elements": elements, "outliner": [group("corps")],
    "textures": [{"path": "", "name": NOM + ".png", "folder": "", "namespace": "", "id": "0", "width": AW, "height": AH,
                  "uv_width": AW, "uv_height": AH, "particle": False, "use_as_default": True, "layers_enabled": False,
                  "render_mode": "default", "render_sides": "auto", "frame_time": 1, "frame_order_type": "loop",
                  "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": False,
                  "uuid": U(), "source": "data:image/png;base64," + base64.b64encode(PNG).decode()}],
    "animations": bb_anims}
with open(os.path.join(OUT, NOM + ".bbmodel"), "w", encoding="utf-8") as fh:
    json.dump(bbmodel, fh, separators=(",", ":"))

# ---------------------------------------------------------------- aperçus reconstruits depuis les cubes
CORN = {  # coins (dans l'ordre u0v0, u1v0, u1v1, u0v1) en fonction de la boîte
    "north": lambda X0, X1, Y0, Y1, Z0, Z1: [(X1, Y1, Z0), (X0, Y1, Z0), (X0, Y0, Z0), (X1, Y0, Z0)],
    "south": lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y0, Z1), (X0, Y0, Z1)],
    "west":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z0), (X0, Y1, Z1), (X0, Y0, Z1), (X0, Y0, Z0)],
    "east":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X1, Y1, Z1), (X1, Y1, Z0), (X1, Y0, Z0), (X1, Y0, Z1)],
    "up":    lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X0, Y1, Z1)],
    "down":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y0, Z0), (X0, Y0, Z0)]}
NORM = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

# scène de contrôle : mêmes lumières et même caméra pour l'original et pour les cubes
scene.view_settings.view_transform = 'Standard'
world = bpy.data.worlds.new("W"); scene.world = world; world.use_nodes = True
world.node_tree.nodes["Background"].inputs[0].default_value = (0.05, 0.08, 0.16, 1)
world.node_tree.nodes["Background"].inputs[1].default_value = 0.8
H = RES * VOX
TGT0 = A_inv @ Vector((0, RES * 0.5, 0))
TGT = A_inv @ Vector((0, RES * 0.48, 0))
def light(nm, loc, e, c):
    ld = bpy.data.lights.new(nm, 'AREA'); ld.energy = e; ld.size = H; ld.color = c
    lo = bpy.data.objects.new(nm, ld); scene.collection.objects.link(lo)
    lo.location = TGT0 + loc
    lo.rotation_euler = (TGT0 - lo.location).to_track_quat('-Z', 'Y').to_euler()
light("key", Vector((H, -1.4 * H, 0.8 * H)), 150 * H * H, (1, 0.95, 0.88))
light("rim", Vector((-1.4 * H, H, 0.5 * H)), 120 * H * H, (0.6, 0.75, 1))
light("fill", Vector((-H, -1.4 * H, -0.2 * H)), 60 * H * H, (1, 1, 1))
cd = bpy.data.cameras.new("cam"); cd.lens = 50
cam = bpy.data.objects.new("cam", cd); scene.collection.objects.link(cam); scene.camera = cam
scene.render.resolution_x = 800; scene.render.resolution_y = 800
os.makedirs(os.path.join(OUT, "apercu"), exist_ok=True)

def shot(name, ang, dist=2.3):
    a = math.radians(ang)
    cam.location = TGT + Vector((math.sin(a) * dist * H, -math.cos(a) * dist * H, H * 0.07))
    cam.rotation_euler = (TGT - cam.location).to_track_quat('-Z', 'Y').to_euler()
    scene.render.filepath = os.path.join(OUT, "apercu", name + ".png")
    bpy.ops.render.render(write_still=True)

# l'original, à mi-parcours de chaque animation
ANGLE = 30
for act in bpy.data.actions:
    nom = NOMS.get(act.name, act.name.lower())
    arm.animation_data.action = act
    try:
        if arm.animation_data.action_slot is None and len(act.slots): arm.animation_data.action_slot = act.slots[0]
    except AttributeError:
        pass
    t = anims[nom]["ticks"] // 2
    f = act.frame_range[0] + t / 20 * fps
    scene.frame_set(int(math.floor(f)), subframe=f - math.floor(f))
    shot(f"original_{nom}", ANGLE)
bpy.data.objects.remove(mobj); bpy.data.objects.remove(arm)

# les cubes générés
CORN = {  # coins (dans l'ordre u0v0, u1v0, u1v1, u0v1) en fonction de la boîte
    "north": lambda X0, X1, Y0, Y1, Z0, Z1: [(X1, Y1, Z0), (X0, Y1, Z0), (X0, Y0, Z0), (X1, Y0, Z0)],
    "south": lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z1), (X1, Y1, Z1), (X1, Y0, Z1), (X0, Y0, Z1)],
    "west":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z0), (X0, Y1, Z1), (X0, Y0, Z1), (X0, Y0, Z0)],
    "east":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X1, Y1, Z1), (X1, Y1, Z0), (X1, Y0, Z0), (X1, Y0, Z1)],
    "up":    lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y1, Z0), (X1, Y1, Z0), (X1, Y1, Z1), (X0, Y1, Z1)],
    "down":  lambda X0, X1, Y0, Y1, Z0, Z1: [(X0, Y0, Z1), (X1, Y0, Z1), (X1, Y0, Z0), (X0, Y0, Z0)]}
NORM = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

apng = os.path.join(OUT, "apercu", "_atlas.png")
with open(apng, "wb") as fh: fh.write(PNG)
aimg = bpy.data.images.load(apng)
pm = bpy.data.materials.new("mc"); pm.use_nodes = True
nt = pm.node_tree; bsdf = nt.nodes["Principled BSDF"]; bsdf.inputs["Roughness"].default_value = 1.0
tn = nt.nodes.new("ShaderNodeTexImage"); tn.image = aimg; tn.interpolation = 'Closest'
nt.links.new(tn.outputs["Color"], bsdf.inputs["Base Color"])
bobj = {}
for b in OS:
    verts, polys, uvs_ = [], [], []
    for bi_, box in enumerate(boxes[b]):
        fs = faces_by.get((b, bi_))
        if not fs: continue
        x0, x1, y0, y1, z0, z1 = box
        X0, X1, Y0, Y1, Z0, Z1 = x0 + g0[0], x1 + g0[0], y0 + g0[1], y1 + g0[1], z0 + g0[2], z1 + g0[2]
        for face, r in fs.items():
            cs = CORN[face](X0, X1, Y0, Y1, Z0, Z1)
            uv = [(r[0], r[1]), (r[2], r[1]), (r[2], r[3]), (r[0], r[3])]
            cb = [A_inv @ Vector(c) for c in cs]
            nrm = (cb[1] - cb[0]).cross(cb[2] - cb[0])
            outw = (A_inv.to_3x3() @ Vector(NORM[face]))
            if nrm.dot(outw) < 0: cs = cs[::-1]; uv = uv[::-1]
            base = len(verts); verts += [tuple(float(c) for c in v) for v in cs]; polys.append(list(range(base, base + 4)))
            uvs_ += [(u / AW, 1 - v / AH) for (u, v) in uv]
    if not polys: continue
    mesh = bpy.data.meshes.new(b); mesh.from_pydata(verts, [], polys)
    uvlay = mesh.uv_layers.new(); uvlay.data.foreach_set("uv", [c for p_ in uvs_ for c in p_])
    mesh.materials.append(pm)
    ob = bpy.data.objects.new(b, mesh); scene.collection.objects.link(ob); bobj[b] = ob

def pose(fr):
    for b, ob in bobj.items():
        ob.matrix_world = A_inv @ (fr[b] if fr else Matrix.Identity(4))

pose(None)
shot("repos_face", 0); shot("repos_trois_quarts", 35); shot("repos_dos", 180)
for n, a in anims.items():
    fr = a["frames"]
    pose(fr[a["ticks"] // 2])
    shot(f"cubes_{n}", ANGLE)
print("FINI")
