#!/usr/bin/env python3
"""Makes the small photos the shared photo tests use, and checks what the tests did to them.

    python3 tools/photo_fixtures.py make            writes shared/data/src/commonTest/fixtures/photos
    python3 tools/photo_fixtures.py check <folder>  checks photos the tests cleaned of their location

Needs Pillow and pillow-heif (pip install pillow pillow-heif).

Why a second language: the app removes a photo's location with its own code, and a test that reads
the result back with that same code proves little. `check` reads the cleaned files with Pillow, an
unrelated implementation, and confirms that the location has gone, that the capture date is still
there, and that the picture itself is untouched. On the phones and the Mac, the conformance suite
does the same with the system's own reader.

The location written into the fixtures is the Royal Observatory at Greenwich.
"""

import io
import struct
import sys
from pathlib import Path

from PIL import Image, ImageOps
from PIL.PngImagePlugin import PngInfo

FIXTURES = Path("shared/data/src/commonTest/fixtures/photos")
DATE = "2024:05:17 10:11:12"
W, H = 64, 48

# EXIF orientation: what Pillow must do to the stored pixels to make the picture upright, and
# what turns the upright picture into those stored pixels.
T = Image.Transpose
UPRIGHT_FROM_STORED = {1: None, 2: T.FLIP_LEFT_RIGHT, 3: T.ROTATE_180, 4: T.FLIP_TOP_BOTTOM,
                       5: T.TRANSPOSE, 6: T.ROTATE_270, 7: T.TRANSVERSE, 8: T.ROTATE_90}
STORED_FROM_UPRIGHT = {1: None, 2: T.FLIP_LEFT_RIGHT, 3: T.ROTATE_180, 4: T.FLIP_TOP_BOTTOM,
                       5: T.TRANSPOSE, 6: T.ROTATE_90, 7: T.TRANSVERSE, 8: T.ROTATE_270}


def reference(mode="RGB"):
    """The upright picture every fixture shows: a different flat colour in each corner."""
    image = Image.new(mode, (W, H))
    colours = {(0, 0): (200, 30, 30), (1, 0): (30, 160, 60), (0, 1): (40, 60, 200), (1, 1): (220, 200, 40)}
    for (cx, cy), colour in colours.items():
        fill = colour + (255,) if mode == "RGBA" else colour
        image.paste(fill, (cx * W // 2, cy * H // 2, (cx + 1) * W // 2, (cy + 1) * H // 2))
    return image


# ---- a small TIFF writer, so the EXIF bytes are known exactly and come in both byte orders -------

SIZES = {1: 1, 2: 1, 3: 2, 4: 4, 5: 8, 7: 1}


def _value(order, kind, values):
    if kind == 2:
        return values.encode("ascii") + b"\0"
    if kind in (1, 7):
        return bytes(values)
    if kind == 3:
        return b"".join(struct.pack(order + "H", v) for v in values)
    if kind == 4:
        return b"".join(struct.pack(order + "I", v) for v in values)
    if kind == 5:
        return b"".join(struct.pack(order + "II", n, d) for n, d in values)
    raise ValueError(kind)


def _ifd(order, entries, at):
    """One directory placed at offset `at`: its bytes, with out-of-line values after the entries."""
    entries = sorted(entries)
    data_at = at + 2 + 12 * len(entries) + 4
    head, data = struct.pack(order + "H", len(entries)), b""
    for tag, kind, values in entries:
        raw = _value(order, kind, values)
        count = len(raw) // SIZES[kind]
        if len(raw) <= 4:
            field = raw.ljust(4, b"\0")
        else:
            field = struct.pack(order + "I", data_at + len(data))
            data += raw + (b"\0" if len(raw) % 2 else b"")
        head += struct.pack(order + "HHI", tag, kind, count) + field
    return head + struct.pack(order + "I", 0) + data


def exif(order="<", orientation=1, gps=True):
    """A TIFF block as cameras write it: the main directory, the EXIF one, and the GPS one."""
    main = [(0x0112, 3, [orientation]), (0x0132, 2, DATE)]
    sub = [(0x9003, 2, DATE)]
    where = [
        (0x0000, 1, [2, 3, 0, 0]),
        (0x0001, 2, "N"), (0x0002, 5, [(51, 1), (28, 1), (3834, 100)]),
        (0x0003, 2, "W"), (0x0004, 5, [(0, 1), (0, 1), (554, 100)]),
        (0x0005, 1, [0]), (0x0006, 5, [(46, 1)]),
        (0x0007, 5, [(10, 1), (11, 1), (12, 1)]), (0x001D, 2, "2024:05:17"),
    ]
    # Two passes: the directories' sizes first, then their bytes with the pointers filled in.
    pointers = [(0x8769, 4, [0])] + ([(0x8825, 4, [0])] if gps else [])
    main_size = len(_ifd(order, main + pointers, 8))
    sub_at = 8 + main_size
    gps_at = sub_at + len(_ifd(order, sub, sub_at))
    pointers = [(0x8769, 4, [sub_at])] + ([(0x8825, 4, [gps_at])] if gps else [])
    block = (b"II" if order == "<" else b"MM") + struct.pack(order + "HI", 42, 8)
    block += _ifd(order, main + pointers, 8) + _ifd(order, sub, sub_at)
    if gps:
        block += _ifd(order, where, gps_at)
    return block


def xmp(gps=True):
    place = ' exif:GPSLatitude="51,28.639N" exif:GPSLongitude="0,0.0923W"' if gps else ""
    height = "<exif:GPSAltitude>46/1</exif:GPSAltitude>" if gps else ""
    return (
        '<?xpacket begin="﻿" id="W5M0MpCehiHzreSzNTczkc9d"?>'
        '<x:xmpmeta xmlns:x="adobe:ns:meta/"><rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">'
        '<rdf:Description rdf:about="" xmlns:exif="http://ns.adobe.com/exif/1.0/" xmlns:xmp="http://ns.adobe.com/xap/1.0/"'
        f'{place} xmp:CreateDate="2024-05-17T10:11:12">{height}</rdf:Description></rdf:RDF></x:xmpmeta>'
        '<?xpacket end="w"?>'
    ).encode("utf-8")


def stored(orientation, mode="RGB"):
    turn = STORED_FROM_UPRIGHT[orientation]
    return reference(mode) if turn is None else reference(mode).transpose(turn)


def make():
    import pillow_heif

    pillow_heif.register_heif_opener()
    FIXTURES.mkdir(parents=True, exist_ok=True)
    for old in FIXTURES.glob("*"):
        if old.suffix in (".jpg", ".png", ".webp", ".heic", ".gif", ".bmp", ".tiff"):
            old.unlink()

    def jpeg(name, image, exif=None, **extra):
        # In a JPEG the block sits in a segment of its own, behind a six-byte label.
        if exif is not None:
            extra["exif"] = b"Exif\0\0" + exif
        image.save(FIXTURES / name, "JPEG", quality=95, subsampling=0, **extra)

    # Location in EXIF, in both byte orders (an iPhone writes "MM", most Android phones "II").
    jpeg("gps-exif-ii.jpg", stored(1), exif=exif("<"))
    jpeg("gps-exif-mm.jpg", stored(6), exif=exif(">", orientation=6))
    # Location in XMP only, beside EXIF that has none.
    jpeg("gps-xmp.jpg", stored(1), exif=exif("<", gps=False), xmp=xmp())
    # Nothing to remove.
    jpeg("no-gps.jpg", stored(1), exif=exif(">", gps=False))
    jpeg("plain.jpg", stored(1))

    info = PngInfo()
    info.add_itxt("XML:com.adobe.xmp", xmp().decode("utf-8"))
    stored(1).save(FIXTURES / "gps.png", "PNG", exif=exif(">"), pnginfo=info)
    stored(1).save(FIXTURES / "gps.webp", "WEBP", lossless=True, exif=exif("<"), xmp=xmp())
    stored(1).save(FIXTURES / "gps.heic", "HEIF", quality=90, exif=exif(">"), xmp=xmp())

    # A HEIC keeps its turning in the container (an "irot" box), not in EXIF. An iPhone writes the box
    # and repeats the turning in EXIF (orientation 6), which a decoder that obeyed both would apply
    # twice. This library writes a file the same way. The second file is the first with EXIF's
    # orientation set to 1, which is the other way writers leave it.
    stored(6).save(FIXTURES / "turned-6-iphone.heic", "HEIF", quality=90, exif=exif(">", orientation=6, gps=False))
    plain_exif = bytearray((FIXTURES / "turned-6-iphone.heic").read_bytes())
    at = plain_exif.find(bytes.fromhex("011200030000000100060000"))
    assert at > 0 and b"irot" in plain_exif, "expected the container's own rotation, and EXIF orientation 6"
    plain_exif[at + 9] = 1
    (FIXTURES / "turned-6.heic").write_bytes(bytes(plain_exif))

    # Transparent on the right half: a rendition has no transparency, and shows black there.
    see_through = reference("RGBA")
    see_through.paste((0, 0, 0, 0), (W // 2, 0, W, H))
    see_through.save(FIXTURES / "alpha.png", "PNG")

    # Formats only some systems open: the app keeps a JPEG made from them instead.
    stored(1).save(FIXTURES / "other.gif", "GIF")
    stored(1).save(FIXTURES / "other.bmp", "BMP")
    stored(1).save(FIXTURES / "other.tiff", "TIFF")

    # The eight turnings: each file's stored pixels are turned so that the upright picture is the same.
    for orientation in range(1, 9):
        jpeg(f"turned-{orientation}.jpg", stored(orientation), exif=exif("<", orientation=orientation, gps=False))

    for path in sorted(FIXTURES.glob("*")):
        if path.is_file():
            print(f"{path.stat().st_size:7d}  {path}")
    verify_fixtures()


def location_of(path):
    """What Pillow finds: (GPS tags in EXIF beyond the version, GPS text in XMP)."""
    import pillow_heif

    pillow_heif.register_heif_opener()
    with Image.open(path) as image:
        image.load()
        tags = dict(image.getexif().get_ifd(0x8825))
        tags.pop(0, None)
        packet = image.info.get("xmp") or image.info.get("XML:com.adobe.xmp") or b""
        if isinstance(packet, str):
            packet = packet.encode("utf-8")
        import re

        text = [m.group(0) for m in re.finditer(rb'exif:GPS\w+\s*=\s*"[^"]*[^" ][^"]*"|<exif:GPS\w+>[^<]*[^< ][^<]*<', packet)]
        return tags, text


def date_of(path):
    with Image.open(path) as image:
        ex = image.getexif()
        return ex.get(0x0132), ex.get_ifd(0x8769).get(0x9003)


def pixels_of(path):
    with Image.open(path) as image:
        return image.convert("RGBA").tobytes()


def verify_fixtures():
    """The fixtures are what they claim to be, by Pillow's reading."""
    for name in ("gps-exif-ii.jpg", "gps-exif-mm.jpg", "gps.png", "gps.webp", "gps.heic"):
        tags, _ = location_of(FIXTURES / name)
        assert 2 in tags and 4 in tags, f"{name} should carry a location in EXIF: {tags}"
    for name in ("gps-xmp.jpg", "gps.png", "gps.webp", "gps.heic"):
        _, text = location_of(FIXTURES / name)
        assert text, f"{name} should carry a location in XMP"
    for name in ("no-gps.jpg", "plain.jpg"):
        assert location_of(FIXTURES / name) == ({}, []), name
    for name in ("turned-6.heic", "turned-6-iphone.heic"):
        with Image.open(FIXTURES / name) as image:
            assert image.size == (W, H), (name, image.size)
            got = image.convert("RGB").getpixel((8, 8))
            assert all(abs(a - b) < 40 for a, b in zip(got, (200, 30, 30))), (name, got)
    for orientation in range(1, 9):
        with Image.open(FIXTURES / f"turned-{orientation}.jpg") as image:
            upright = ImageOps.exif_transpose(image)
            assert upright.size == (W, H), (orientation, upright.size)
            for (x, y), want in {(8, 8): (200, 30, 30), (56, 8): (30, 160, 60), (8, 40): (40, 60, 200), (56, 40): (220, 200, 40)}.items():
                got = upright.convert("RGB").getpixel((x, y))
                assert all(abs(a - b) < 12 for a, b in zip(got, want)), (orientation, (x, y), got, want)
    print("fixtures verified")


def check(folder):
    """Each cleaned photo, against the fixture it was made from."""
    folder = Path(folder)
    cleaned = sorted(p for p in folder.glob("*") if p.is_file())
    if not cleaned:
        sys.exit(f"No cleaned photos in {folder}: the tests that write them didn't run.")
    failures = 0
    for path in cleaned:
        original = FIXTURES / path.name
        problems = []
        tags, text = location_of(path)
        if tags:
            problems.append(f"EXIF still has GPS tags {sorted(tags)}")
        if text:
            problems.append(f"XMP still has {text}")
        if path.stat().st_size != original.stat().st_size:
            problems.append("its length changed")
        if date_of(path) != date_of(original):
            problems.append(f"its dates changed: {date_of(original)} -> {date_of(path)}")
        if pixels_of(path) != pixels_of(original):
            problems.append("the picture changed")
        print(("FAILED  " if problems else "ok      ") + path.name + ("" if not problems else ": " + "; ".join(problems)))
        failures += bool(problems)
    if failures:
        sys.exit(f"{failures} of {len(cleaned)} cleaned photos are wrong")
    print(f"{len(cleaned)} cleaned photos: no location, dates kept, pictures untouched")


if __name__ == "__main__":
    if len(sys.argv) == 2 and sys.argv[1] == "make":
        make()
    elif len(sys.argv) == 3 and sys.argv[1] == "check":
        check(sys.argv[2])
    else:
        sys.exit(__doc__)
