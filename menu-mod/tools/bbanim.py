"""Blockbench .bbmodel → Java item model(s), with bones and animations baked into frames.

Minecraft item models have no bones and no animation, but since 26.x an element may be turned on all
three axes (``"rotation": {"x", "y", "z", "origin"}``, the same Z·Y·X Euler order Blockbench uses).
So any pose of a Blockbench model can be written as a plain item model: every cube gets its bone
chain applied and becomes a box turned about its own centre. An animation is sampled at a fixed
rate and every sample becomes one model (a frame); the item picks the frame by the first
``custom_model_data`` string ("f0", "f1", ...), which the server (and the MineHouse client mod)
step through. Animated textures (a strip of frames in Blockbench) get a ``.png.mcmeta`` and
animate on every client by themselves.

Conventions follow Blockbench 5 (``js/animations/timeline_animators.js``, ``js/formats/bbmodel.js``):
a bone sits at its origin, keyframe rotation is added to its Euler angles (degrees, order ZYX),
position is added in pixels, scale multiplies. Projects saved before format 5.0 stored X and Y of
rotation and X of position negated; they are flipped back on load, as Blockbench does.
"""
import base64
import math
import re
import struct

ID = [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


class BBError(Exception):
    pass


# ------------------------------------------------------------------ small 4x4 maths
def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def tr(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def sc(x, y, z):
    return [[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]]


def rot_zyx(rx, ry, rz):
    """Euler angles in degrees, matrix Rz·Ry·Rx (three.js order 'ZYX', JOML rotationZYX)."""
    a, b, c = (math.radians(v) for v in (rx, ry, rz))
    ca, sa, cb, sb, cc, s_c = math.cos(a), math.sin(a), math.cos(b), math.sin(b), math.cos(c), math.sin(c)
    return [
        [cb * cc, sa * sb * cc - ca * s_c, ca * sb * cc + sa * s_c, 0],
        [cb * s_c, sa * sb * s_c + ca * cc, ca * sb * s_c - sa * cc, 0],
        [-sb, sa * cb, ca * cb, 0],
        [0, 0, 0, 1],
    ]


def euler_zyx(r):
    """Inverse of rot_zyx for a pure rotation (3x3 part)."""
    sb = max(-1.0, min(1.0, -r[2][0]))
    b = math.asin(sb)
    if abs(sb) < 0.999999:
        a = math.atan2(r[2][1], r[2][2])
        c = math.atan2(r[1][0], r[0][0])
        # The same turn has a second solution (b' = pi - b); keep the one with smaller X and Z.
        a2, b2, c2 = a + math.pi, math.pi - b, c + math.pi
        a2, b2, c2 = (math.atan2(math.sin(v), math.cos(v)) for v in (a2, b2, c2))
        if abs(a2) + abs(c2) < abs(a) + abs(c) - 1e-9:
            a, b, c = a2, b2, c2
    else:  # gimbal lock: put it all on X
        a = math.atan2(-r[1][2], r[1][1])
        c = 0.0
    return [math.degrees(a), math.degrees(b), math.degrees(c)]


def apply(m, p):
    return [sum(m[i][k] * p[k] for k in range(3)) + m[i][3] for i in range(3)]


# The client builds checked-in numeric tracks; no expressions execute at build time.
def numeric_keyframe(value):
    if value is None or value == "":
        return 0.0
    if isinstance(value, (int, float)):
        return float(value)
    s = str(value).strip().replace("\n", "")
    try:
        return float(s)
    except ValueError as e:
        raise BBError(f"non-numeric keyframe '{s}'; bake expressions before importing") from e


# ------------------------------------------------------------------ keyframes
class Track:
    """One channel (rotation / position / scale) of one bone."""

    def __init__(self, keyframes, length, loop, old_format, channel):
        self.channel = channel
        self.length = length
        self.loop = loop
        self.kfs = sorted(keyframes, key=lambda k: float(k.get("time", 0)))
        self.flip = [1, 1, 1]
        if old_format and channel in ("rotation", "position"):
            self.flip = [-1, -1, 1] if channel == "rotation" else [-1, 1, 1]

    def value(self, kf, axis, point, t):
        pts = kf.get("data_points") or [{}]
        p = pts[min(point, len(pts) - 1)]
        return numeric_keyframe(p.get("xyz"[axis])) * self.flip[axis]

    def at(self, t):
        if not self.kfs:
            return None
        before = after = None
        bt = at_ = 0.0
        for kf in self.kfs:
            k = float(kf.get("time", 0))
            if k < t:
                if before is None or k > bt:
                    before, bt = kf, k
            elif after is None or k < at_:
                after, at_ = kf, k
        if self.loop == "loop" and len(self.kfs) >= 2:
            if before is None:
                before = self.kfs[-1]
                bt = float(before.get("time", 0)) - self.length
            if after is None:
                after = self.kfs[0]
                at_ = float(after.get("time", 0)) + self.length
        eps = 1 / 1200
        if before is not None and abs(bt - t) < eps:
            return [self.value(before, i, 0, t) for i in range(3)]
        if after is not None and abs(at_ - t) < eps:
            return [self.value(after, i, 0, t) for i in range(3)]
        if before is None:
            return [self.value(after, i, 0, t) for i in range(3)]
        if after is None or before.get("interpolation") == "step":
            return [self.value(before, i, 1, t) for i in range(3)]
        alpha = (t - bt) / (at_ - bt) if at_ != bt else 0
        bi = before.get("interpolation") or "linear"
        ai = after.get("interpolation") or "linear"
        if bi == "linear" and ai in ("linear", "step"):
            return [self.value(before, i, 1, t) + (self.value(after, i, 0, t) - self.value(before, i, 1, t)) * alpha
                    for i in range(3)]
        if "catmullrom" in (bi, ai):
            # As Blockbench: the neighbours of the two keys, wrapping round in a loop.
            k = sorted(self.kfs, key=lambda x: float(x.get("time", 0)))
            j = next(n for n, x in enumerate(k) if x is before)
            bp = k[j - 1] if j - 1 >= 0 else None
            ap = k[j + 2] if j + 2 < len(k) else None
            if self.loop == "loop" and len(k) >= 3:
                bp = bp if bp is not None else k[-2]
                ap = ap if ap is not None else k[1]
            out = []
            for i in range(3):
                pts = []
                if bp is not None and len(before.get("data_points") or [1]) == 1:
                    pts.append(self.value(bp, i, 1, t))
                pts.append(self.value(before, i, 1, t))
                pts.append(self.value(after, i, 0, t))
                if ap is not None and len(after.get("data_points") or [1]) == 1:
                    pts.append(self.value(ap, i, 0, t))
                u = (alpha + (1 if bp is not None else 0)) / (len(pts) - 1)
                out.append(_spline(pts, u))
            return out
        if "bezier" in (bi, ai):
            return [_bezier(before, after, bt, at_, i, self.value(before, i, 1, t), self.value(after, i, 0, t), t, self.flip[i])
                    for i in range(3)]
        return [self.value(before, i, 1, t) + (self.value(after, i, 0, t) - self.value(before, i, 1, t)) * alpha
                for i in range(3)]


def _spline(pts, u):
    """THREE.SplineCurve.getPoint(u).y: uniform Catmull-Rom through the points."""
    n = len(pts)
    p = (n - 1) * u
    i = int(math.floor(p))
    w = p - i
    p0 = pts[i - 1 if i > 0 else i]
    p1 = pts[min(i, n - 1)]
    p2 = pts[min(i + 1, n - 1)]
    p3 = pts[min(i + 2, n - 1)]
    v0 = (p2 - p0) * 0.5
    v1 = (p3 - p1) * 0.5
    t2, t3 = w * w, w * w * w
    return (2 * p1 - 2 * p2 + v0 + v1) * t3 + (-3 * p1 + 3 * p2 - 2 * v0 - v1) * t2 + v0 * w + p1


def _bezier(before, after, bt, at_, axis, v0, v1, t, flip):
    gap = at_ - bt
    h0 = min(max((before.get("bezier_right_time") or [0, 0, 0])[axis], 0), gap)
    h1 = min(max((after.get("bezier_left_time") or [0, 0, 0])[axis], -gap), 0)
    pts = [(bt, v0),
           (bt + h0, v0 + (before.get("bezier_right_value") or [0, 0, 0])[axis] * flip),
           (at_ + h1, v1 + (after.get("bezier_left_value") or [0, 0, 0])[axis] * flip),
           (at_, v1)]
    # Solve x(s) = t by bisection (x is monotonic for clamped handles).
    lo, hi = 0.0, 1.0
    for _ in range(40):
        mid = (lo + hi) / 2
        if _cubic([p[0] for p in pts], mid) < t:
            lo = mid
        else:
            hi = mid
    return _cubic([p[1] for p in pts], (lo + hi) / 2)


def _cubic(c, s):
    m = 1 - s
    return m * m * m * c[0] + 3 * m * m * s * c[1] + 3 * m * s * s * c[2] + s * s * s * c[3]


# ------------------------------------------------------------------ the project
def png_size(data):
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise BBError("texture is not a PNG")
    return struct.unpack(">II", data[16:24])


def _ver(v):
    try:
        return tuple(int(x) for x in str(v).split(".")[:2])
    except ValueError:
        return (0, 0)


class Project:
    def __init__(self, bb, name="model"):
        self.bb = bb
        meta = bb.get("meta", {})
        self.format = meta.get("model_format") or ("bedrock_old" if meta.get("bone_rig") else "java_block")
        self.old = _ver(meta.get("format_version", "5.0")) < (5, 0)
        # Java Block/Item projects are in item model space (0..16); the others are centred on 0.
        self.shift = [0, 0, 0] if self.format == "java_block" else [8, 0, 8]
        res = bb.get("resolution", {"width": 16, "height": 16})
        self.textures, self.pngs, self.uv = {}, {}, {}
        for i, tex in enumerate(bb.get("textures", [])):
            src = tex.get("source", "")
            if not src.startswith("data:image/png;base64,"):
                raise BBError(f"texture {tex.get('name')} is not embedded in {name}; save the project with textures")
            data = base64.b64decode(src.split(",", 1)[1])
            tname = re.sub(r"[^a-z0-9_]", "_", str(tex.get("name", f"t{i}")).lower().removesuffix(".png")) or f"t{i}"
            while tname in self.pngs:
                tname += "_"
            self.pngs[tname] = data
            self.textures[str(i)] = tname
            uw = tex.get("uv_width") or res.get("width", 16)
            uh = tex.get("uv_height") or res.get("height", 16)
            self.uv[str(i)] = (16 / uw, 16 / uh)
            w, h = png_size(data)
            # A strip of frames: Blockbench shows one frame of uv_width x uv_height scaled to the image width.
            frame_h = w * uh / uw
            if h > frame_h + 0.5 and abs(h / frame_h - round(h / frame_h)) < 1e-6:
                anim = {"frametime": max(1, int(tex.get("frame_time") or 1))}
                if tex.get("frame_interpolate"):
                    anim["interpolate"] = True
                if abs(frame_h - w) > 0.5:
                    anim["width"], anim["height"] = w, int(round(frame_h))
                order = _frame_order(tex, int(round(h / frame_h)))
                if order:
                    anim["frames"] = order
                self.pngs[tname + ".mcmeta"] = ('{"animation": ' + _json(anim) + "}\n").encode()

        # Groups (bones): Blockbench 5 keeps them in "groups", older files inline in the outliner.
        self.groups = {g["uuid"]: dict(g) for g in bb.get("groups", []) if "uuid" in g}
        self.chain = {}  # element uuid -> [group dicts, root first]

        def walk(nodes, chain):
            for node in nodes:
                if isinstance(node, str):
                    self.chain[node] = chain
                elif isinstance(node, dict):
                    g = dict(self.groups.get(node.get("uuid"), {}))
                    g.update({k: v for k, v in node.items() if k != "children"})
                    if g.get("visibility") is False:
                        hide(node.get("children", []))
                        continue
                    self.groups[g["uuid"]] = g
                    walk(node.get("children", []), chain + [g])

        def hide(nodes):
            for node in nodes:
                if isinstance(node, str):
                    self.chain[node] = None
                elif isinstance(node, dict):
                    hide(node.get("children", []))

        walk(bb.get("outliner", []), [])
        self.cubes = [el for el in bb.get("elements", [])
                      if el.get("type", "cube") == "cube" and el.get("visibility") is not False
                      and self.chain.get(el.get("uuid"), []) is not None]
        skipped = [el for el in bb.get("elements", []) if el.get("type", "cube") != "cube"]
        if skipped:
            print(f"  note: {len(skipped)} non-cube element(s) skipped (meshes, locators); item models only take cubes")

    # -------------------------------------------------- animations
    def animations(self):
        return [a.get("name", "?") for a in self.bb.get("animations", [])]

    def animation(self, name=None):
        anims = self.bb.get("animations", [])
        if not anims:
            return None
        if name:
            for a in anims:
                if a.get("name") == name:
                    return a
            raise BBError(f"no animation '{name}'; there are: {', '.join(self.animations())}")
        # A looping one first ("idle" if there is one), else the first.
        loops = [a for a in anims if a.get("loop") == "loop"]
        for a in loops:
            if "idle" in a.get("name", "").lower():
                return a
        return (loops or anims)[0]

    def tracks(self, anim):
        """group uuid -> {channel: Track}"""
        out = {}
        length = float(anim.get("length") or 0)
        for uuid, animator in (anim.get("animators") or {}).items():
            if animator.get("type", "bone") != "bone":
                continue
            by = {}
            for kf in animator.get("keyframes", []):
                by.setdefault(kf.get("channel"), []).append(kf)
            target = uuid
            if target not in self.groups:  # older files may key by name
                target = next((g["uuid"] for g in self.groups.values() if g.get("name") == animator.get("name")), None)
            if target is None:
                continue
            out[target] = {ch: Track(kfs, length, anim.get("loop"), self.old, ch)
                           for ch, kfs in by.items() if ch in ("rotation", "position", "scale")}
        return out

    # -------------------------------------------------- posing
    def bone_matrix(self, chain, tracks, t):
        m = ID
        for g in chain:
            o = g.get("origin", [0, 0, 0])
            r = list(g.get("rotation", [0, 0, 0]))
            p = [0, 0, 0]
            s = [1, 1, 1]
            ch = tracks.get(g["uuid"], {}) if tracks else {}
            if t is not None:
                if "rotation" in ch:
                    v = ch["rotation"].at(t)
                    r = [r[i] + v[i] for i in range(3)]
                if "position" in ch:
                    p = ch["position"].at(t)
                if "scale" in ch:
                    s = ch["scale"].at(t)
            m = mul(m, tr(o[0] + p[0], o[1] + p[1], o[2] + p[2]))
            m = mul(m, rot_zyx(*r))
            m = mul(m, sc(*(v if abs(v) > 1e-5 else 1e-5 for v in s)))
            m = mul(m, tr(-o[0], -o[1], -o[2]))
        return m

    def elements(self, tracks=None, t=None):
        """The item model's elements in this pose (t=None: as modelled)."""
        out = []
        for el in self.cubes:
            chain = self.chain.get(el.get("uuid"), [])
            bone = self.bone_matrix(chain, tracks, t)
            inflate = el.get("inflate", 0) or 0
            f = [el["from"][i] - inflate for i in range(3)]
            to = [el["to"][i] + inflate for i in range(3)]
            crot = el.get("rotation", [0, 0, 0]) or [0, 0, 0]
            corigin = el.get("origin", [8, 8, 8])
            plain = bone == ID or all(abs(bone[i][j] - ID[i][j]) < 1e-9 for i in range(4) for j in range(4))
            if plain:
                box_from, box_to = f, to
                angles, origin = list(crot), list(corigin)
            else:
                m = mul(bone, mul(tr(*corigin), mul(rot_zyx(*crot), tr(-corigin[0], -corigin[1], -corigin[2]))))
                centre = [(f[i] + to[i]) / 2 for i in range(3)]
                half = [(to[i] - f[i]) / 2 for i in range(3)]
                cols = [[m[r][c] for r in range(3)] for c in range(3)]
                scale = [math.sqrt(sum(v * v for v in col)) or 1e-9 for col in cols]
                rot = [[m[r][c] / scale[c] for c in range(3)] for r in range(3)]
                c2 = apply(m, centre)
                half = [half[i] * scale[i] for i in range(3)]
                box_from = [c2[i] - half[i] for i in range(3)]
                box_to = [c2[i] + half[i] for i in range(3)]
                angles, origin = euler_zyx(rot), c2
            box_from = [box_from[i] + self.shift[i] for i in range(3)]
            box_to = [box_to[i] + self.shift[i] for i in range(3)]
            origin = [origin[i] + self.shift[i] for i in range(3)]
            e = {"from": _r(box_from), "to": _r(box_to)}
            angles = [_wrap(a) for a in angles]
            nz = [i for i in range(3) if abs(angles[i]) > 1e-4]
            if len(nz) == 1 and abs(angles[nz[0]]) <= 45 + 1e-6:
                e["rotation"] = {"angle": _r1(angles[nz[0]]), "axis": "xyz"[nz[0]], "origin": _r(origin)}
            elif nz:
                e["rotation"] = {"x": _r1(angles[0]), "y": _r1(angles[1]), "z": _r1(angles[2]), "origin": _r(origin)}
            e["faces"] = {}
            for face, fd in (el.get("faces") or {}).items():
                if fd.get("texture") is None:
                    continue
                key = str(fd["texture"])
                su, sv = self.uv.get(key, (1, 1))
                u1, v1, u2, v2 = fd["uv"]
                fo = {"uv": _r([u1 * su, v1 * sv, u2 * su, v2 * sv]), "texture": f"#{key}"}
                if fd.get("rotation"):
                    fo["rotation"] = fd["rotation"]
                if fd.get("tint", -1) >= 0:
                    fo["tintindex"] = fd["tint"]
                e["faces"][face] = fo
            if e["faces"]:
                out.append(e)
        return out

    def frames(self, anim, fps, closed=False):
        """The animation sampled fps times a second. A loop leaves out its end (it is the start
        again); closed (played once) keeps it, so it stops on its last pose."""
        length = float(anim.get("length") or 0)
        if length <= 0:
            raise BBError(f"animation '{anim.get('name')}' has no length")
        tracks = self.tracks(anim)
        n = max(1, int(round(length * fps)))
        return [self.elements(tracks, k * length / n) for k in range(n + (1 if closed else 0))]

    def model(self, elements):
        model = {"textures": dict(self.textures), "elements": elements}
        if self.textures:
            model["textures"]["particle"] = next(iter(self.textures.values()))
        if self.bb.get("display"):
            model["display"] = self.bb["display"]
        return model


def _frame_order(tex, count):
    kind = tex.get("frame_order_type", "loop")
    custom = str(tex.get("frame_order") or "").strip()
    if custom:
        try:
            return [int(x) for x in custom.replace(",", " ").split()]
        except ValueError:
            return None
    if kind == "backwards":
        return list(range(count - 1, -1, -1))
    if kind == "back_and_forth":
        return list(range(count)) + list(range(count - 2, 0, -1))
    return None


def _json(o):
    import json
    return json.dumps(o)


def _wrap(a):
    a = math.fmod(a, 360)
    if a > 180:
        a -= 360
    if a <= -180:
        a += 360
    return a


def _r(v):
    return [round(x, 4) + 0.0 for x in v]


def _r1(x):
    return round(x, 3) + 0.0


def bounds(models):
    """Smallest and largest coordinate over the elements of all models."""
    lo, hi = math.inf, -math.inf
    for els in models:
        for e in els:
            for k in ("from", "to"):
                lo = min(lo, *e[k])
                hi = max(hi, *e[k])
    return lo, hi


def shrink(models, k):
    """Scale every element about the block centre (8, 8, 8) by k (to fit the -16..32 limit)."""
    def s(p):
        return _r([8 + (x - 8) * k for x in p])
    for els in models:
        for e in els:
            e["from"], e["to"] = s(e["from"]), s(e["to"])
            if "rotation" in e:
                e["rotation"]["origin"] = s(e["rotation"]["origin"])
