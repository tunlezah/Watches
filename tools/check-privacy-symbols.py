#!/usr/bin/env python3
"""Checks that the app's privacy manifest declares every "required reason" API its binaries use.

    tools/check-privacy-symbols.py <app or xcarchive> [PrivacyInfo.xcprivacy]

App Store Connect refuses uploads that use one of these APIs without declaring it. The Kotlin
framework is linked statically, so the app's own binary also carries whatever Kotlin, Compose,
SQLite and Skia call. Runs on macOS (it needs Xcode's nm and strings). Fails when an API is used
but its category isn't declared; warns when a category is declared but nothing seems to use it.
Test frameworks and test bundles that Xcode copies into an app built for testing, and the Debug
previews stub, don't ship, so they're left out.
"""
import plistlib
import subprocess
import sys
from pathlib import Path

# Apple's list ("Describing use of required reason API"). C functions are matched against the
# binary's imported symbols; Foundation constants against symbols too; selectors against strings.
CATEGORIES = {
    "NSPrivacyAccessedAPICategoryFileTimestamp": {
        "functions": ["stat", "fstat", "lstat", "fstatat", "getattrlist", "fgetattrlist", "getattrlistbulk", "getattrlistat"],
        "names": ["NSFileCreationDate", "NSFileModificationDate", "NSURLContentModificationDateKey", "NSURLCreationDateKey",
                  "fileCreationDate", "fileModificationDate"],
    },
    "NSPrivacyAccessedAPICategorySystemBootTime": {
        "functions": ["mach_absolute_time"],
        "names": ["systemUptime"],
    },
    "NSPrivacyAccessedAPICategoryDiskSpace": {
        "functions": ["statfs", "fstatfs", "statvfs", "fstatvfs", "getattrlist", "fgetattrlist", "getattrlistat"],
        "names": ["NSFileSystemFreeSize", "NSFileSystemSize", "NSURLVolumeAvailableCapacityKey",
                  "NSURLVolumeAvailableCapacityForImportantUsageKey", "NSURLVolumeAvailableCapacityForOpportunisticUsageKey",
                  "NSURLVolumeTotalCapacityKey"],
    },
    "NSPrivacyAccessedAPICategoryActiveKeyboards": {
        "functions": [],
        "names": ["activeInputModes"],
    },
    "NSPrivacyAccessedAPICategoryUserDefaults": {
        "functions": [],
        "names": ["NSUserDefaults"],
    },
}

MACHO_MAGIC = {b"\xcf\xfa\xed\xfe", b"\xce\xfa\xed\xfe", b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca"}


def find_app(path: Path) -> Path:
    if path.suffix == ".app":
        return path
    apps = sorted(path.glob("Products/Applications/*.app"))  # an .xcarchive
    if not apps:
        sys.exit(f"No .app found in {path}")
    return apps[0]


def binaries(app: Path):
    """The main executable and any other Mach-O file in the bundle, such as Xcode's Debug *.debug.dylib."""
    for p in sorted(app.rglob("*")):
        if p.is_file() and not p.is_symlink():
            with open(p, "rb") as f:
                if f.read(4) in MACHO_MAGIC:
                    yield p


def test_only(rel: Path) -> bool:
    """Xcode's test frameworks (XCTest, XCUIAutomation, Swift Testing...), hosted test bundles and
    the Debug previews stub: inside the .app CI tests, never in what ships."""
    for part in rel.parts:
        if part.endswith(".xctest") or part == "Testing.framework" or (part.startswith("XC") and part.endswith(".framework")):
            return True
    return rel.name.startswith("libXCTest") or rel.name == "__preview.dylib"


def imported_symbols(binary: Path) -> set:
    """Imported symbol names without the leading underscore. An Objective-C class the binary uses is
    imported as OBJC_CLASS_$_Name, so its bare name is added too (NSUserDefaults, for one)."""
    out = subprocess.run(["xcrun", "nm", "-u", "-j", str(binary)], capture_output=True, text=True, check=True).stdout
    names = {line.strip().lstrip("_") for line in out.splitlines() if line.strip()}
    return names | {n.removeprefix("OBJC_CLASS_$_") for n in names if n.startswith("OBJC_CLASS_$_")}


def strings_in(binary: Path) -> set:
    out = subprocess.run(["xcrun", "strings", "-a", str(binary)], capture_output=True, text=True, errors="replace", check=True).stdout
    return set(out.split())


def main() -> int:
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    app = find_app(Path(sys.argv[1]))
    manifest_path = Path(sys.argv[2]) if len(sys.argv) > 2 else app / "PrivacyInfo.xcprivacy"
    with open(manifest_path, "rb") as f:
        manifest = plistlib.load(f)
    declared = {}
    for entry in manifest.get("NSPrivacyAccessedAPITypes", []):
        declared[entry["NSPrivacyAccessedAPIType"]] = entry.get("NSPrivacyAccessedAPITypeReasons", [])

    found, skipped = {}, []
    for b in binaries(app):
        rel = b.relative_to(app.parent)
        if test_only(b.relative_to(app)):
            skipped.append(rel)
            continue
        found[rel] = (imported_symbols(b), strings_in(b))
    if not found:
        sys.exit(f"No binaries found in {app}")
    print("Scanned: " + ", ".join(str(f) for f in found))
    if skipped:
        print("Left out (test or preview only): " + ", ".join(str(f) for f in skipped))

    failed = False
    for category, apis in CATEGORIES.items():
        uses = {}  # API -> the binaries that use it
        for rel, (symbols, strings) in found.items():
            for api in apis["functions"]:
                if api in symbols:
                    uses.setdefault(api, []).append(rel.name)
            for api in apis["names"]:
                if api in symbols or api in strings:
                    uses.setdefault(api, []).append(rel.name)
        used = ", ".join(f"{api} ({', '.join(files)})" for api, files in uses.items())
        name = category.removeprefix("NSPrivacyAccessedAPICategory")
        if uses and category not in declared:
            print(f"FAIL  {name}: uses {used} but the manifest doesn't declare it")
            failed = True
        elif uses and not declared[category]:
            print(f"FAIL  {name}: declared without a reason")
            failed = True
        elif uses:
            print(f"ok    {name}: uses {used}; declared with {', '.join(declared[category])}")
        elif category in declared:
            print(f"warn  {name}: declared, but nothing in the binary seems to use it")
        else:
            print(f"ok    {name}: not used")
    unknown = set(declared) - set(CATEGORIES)
    for category in sorted(unknown):
        print(f"warn  {category}: declared, but this check doesn't know it")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
