"""Generates 3D heart mesh (assets/models/heart.binobj) from the HeartGram icon contour.

    python Tools/icons/make_heart_binobj.py
"""
import os
import struct
import numpy as np
from PIL import Image
from shapely.geometry import Polygon, Point
from shapely.prepared import prep
from skimage import measure

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.normpath(os.path.join(HERE, "..", ".."))
ICON_PATH = os.path.join(ROOT, "TMessagesProj", "src", "main", "res", "drawable-xxxhdpi", "exteraless_icon_foreground.png")
OUT_PATH = os.path.join(ROOT, "TMessagesProj", "src", "main", "assets", "models", "heart.binobj")


def load_heart_polygon():
    img = Image.open(ICON_PATH).convert("RGBA")
    alpha = np.asarray(img.getchannel("A"), dtype=np.float64) / 255.0
    contours = measure.find_contours(alpha, 0.85)
    c = max(contours, key=len)
    # Image coordinates: row (y down), col (x right) -> flip Y up for OpenGL
    pts = [(float(x), float(-y)) for y, x in c]
    poly = Polygon(pts).buffer(0).simplify(0.45, preserve_topology=True)
    minx, miny, maxx, maxy = poly.bounds
    cx = (minx + maxx) * 0.5
    cy = (miny + maxy) * 0.5
    scale = 61.0 / max(maxx - minx, maxy - miny)
    coords = [((x - cx) * scale, (y - cy) * scale) for x, y in poly.exterior.coords]
    return Polygon(coords).buffer(0)


def build_heart_mesh(poly):
    # Resample boundary smoothly for clean rim
    boundary = poly.exterior
    length = boundary.length
    n_rim = 160
    rim_pts = []
    for i in range(n_rim):
        p = boundary.interpolate(i * length / n_rim)
        rim_pts.append((p.x, p.y))
    poly_clean = Polygon(rim_pts).buffer(0)
    prep_poly = prep(poly_clean)

    minx, miny, maxx, maxy = poly_clean.bounds
    # Interior grid points
    step = 1.35
    gx = np.arange(minx + step * 0.5, maxx, step)
    gy = np.arange(miny + step * 0.5, maxy, step)
    interior_pts = []
    for y in gy:
        for x in gx:
            pt = Point(x, y)
            if prep_poly.contains(pt) and boundary.distance(pt) > step * 0.42:
                interior_pts.append((float(x), float(y)))

    pts_2d = np.array(rim_pts + interior_pts, dtype=np.float64)
    n_pts = len(pts_2d)

    # Compute distance to boundary for smooth pillow profile
    dists = np.zeros(n_pts, dtype=np.float64)
    for i in range(n_rim, n_pts):
        dists[i] = boundary.distance(Point(pts_2d[i, 0], pts_2d[i, 1]))

    max_d = float(np.max(dists)) if n_pts > n_rim else 1.0
    max_z = 9.2
    z_vals = np.zeros(n_pts, dtype=np.float64)
    # Smooth dome / pillow curve with rounded bevel at the rim
    t = np.clip(dists / max_d, 0.0, 1.0)
    z_vals = max_z * np.sqrt(1.0 - np.power(1.0 - t, 2.25))

    # Delaunay triangulation of 2D points, keeping only triangles inside the heart
    from scipy.spatial import Delaunay
    tri = Delaunay(pts_2d)
    valid_tris = []
    for a, b, c in tri.simplices:
        cx = (pts_2d[a, 0] + pts_2d[b, 0] + pts_2d[c, 0]) / 3.0
        cy = (pts_2d[a, 1] + pts_2d[b, 1] + pts_2d[c, 1]) / 3.0
        if prep_poly.contains(Point(cx, cy)):
            # Ensure CCW winding in XY (+Z normal)
            ax, ay = pts_2d[b] - pts_2d[a]
            bx, by = pts_2d[c] - pts_2d[a]
            if ax * by - ay * bx < 0:
                b, c = c, b
            valid_tris.append((a, b, c))

    # Build front (+Z) and back (-Z) vertices sharing the rim at Z = 0
    # Total vertices: n_rim (shared rim) + 2 * (n_pts - n_rim) (front & back interiors)
    n_int = n_pts - n_rim
    verts = np.zeros((n_rim + 2 * n_int, 3), dtype=np.float64)
    # Rim
    verts[:n_rim, 0] = pts_2d[:n_rim, 0]
    verts[:n_rim, 1] = pts_2d[:n_rim, 1]
    verts[:n_rim, 2] = 0.0
    # Front interior
    verts[n_rim:n_rim + n_int, 0] = pts_2d[n_rim:, 0]
    verts[n_rim:n_rim + n_int, 1] = pts_2d[n_rim:, 1]
    verts[n_rim:n_rim + n_int, 2] = z_vals[n_rim:]
    # Back interior
    verts[n_rim + n_int:, 0] = pts_2d[n_rim:, 0]
    verts[n_rim + n_int:, 1] = pts_2d[n_rim:, 1]
    verts[n_rim + n_int:, 2] = -z_vals[n_rim:]

    def front_idx(idx):
        return idx if idx < n_rim else idx

    def back_idx(idx):
        return idx if idx < n_rim else idx + n_int

    faces = []
    for a, b, c in valid_tris:
        faces.append((front_idx(a), front_idx(b), front_idx(c)))
        # Reverse winding for back face (-Z normal)
        faces.append((back_idx(a), back_idx(c), back_idx(b)))

    # Smooth vertex normals (area-weighted)
    normals = np.zeros_like(verts)
    for ia, ib, ic in faces:
        va, vb, vc = verts[ia], verts[ib], verts[ic]
        fn = np.cross(vb - va, vc - va)
        normals[ia] += fn
        normals[ib] += fn
        normals[ic] += fn
    norms_len = np.linalg.norm(normals, axis=1, keepdims=True)
    norms_len = np.where(norms_len < 1e-8, 1.0, norms_len)
    normals = normals / norms_len

    # UV coordinates in [0, 1]
    uv_w = maxx - minx
    uv_h = maxy - miny
    uvs = np.zeros((len(verts), 2), dtype=np.float64)
    uvs[:, 0] = np.clip((verts[:, 0] - minx) / uv_w, 0.0, 1.0)
    uvs[:, 1] = np.clip((verts[:, 1] - miny) / uv_h, 0.0, 1.0)

    return verts, uvs, normals, faces


def write_binobj(path, verts, uvs, normals, faces):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        flat_v = verts.astype(">f4").ravel()
        f.write(struct.pack(">i", len(flat_v)))
        f.write(flat_v.tobytes())

        flat_uv = uvs.astype(">f4").ravel()
        f.write(struct.pack(">i", len(flat_uv)))
        f.write(flat_uv.tobytes())

        flat_n = normals.astype(">f4").ravel()
        f.write(struct.pack(">i", len(flat_n)))
        f.write(flat_n.tobytes())

        # ObjLoader reads numFaces vertices (3 indices per vertex: pos, tex, norm)
        flat_idx = []
        for a, b, c in faces:
            flat_idx.extend([a, a, a, b, b, b, c, c, c])
        num_face_verts = len(faces) * 3
        f.write(struct.pack(">i", num_face_verts))
        f.write(np.array(flat_idx, dtype=">i4").tobytes())


if __name__ == "__main__":
    poly = load_heart_polygon()
    verts, uvs, normals, faces = build_heart_mesh(poly)
    write_binobj(OUT_PATH, verts, uvs, normals, faces)
    print(f"Wrote {OUT_PATH}: verts={len(verts)}, tris={len(faces)}, size={os.path.getsize(OUT_PATH)} bytes")
    print(f"Bounds X=[{verts[:,0].min():.2f}, {verts[:,0].max():.2f}] Y=[{verts[:,1].min():.2f}, {verts[:,1].max():.2f}] Z=[{verts[:,2].min():.2f}, {verts[:,2].max():.2f}]")
