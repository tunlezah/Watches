# Watches on a home server (TrueNAS): plan

Status: **proposal, nothing built.** Written against `b74a5d8` (0.8.0, Mac milestones mac-1 and mac-2 built). A throwaway trial (section 3) ran the current Linux build in a container. Nothing from that trial is in the repository.

**Decisions needed before work starts** are in section 9.

## 1. Summary

- **It's possible without changing the app much.** The Mac app is Compose's desktop target, which already runs on Linux and is tested there on every push. Put that build in a container whose base image shows its window in a web page, and Watches runs in a browser. The base image does this with a virtual screen, a VNC server and a noVNC page, the same way many TrueNAS and Unraid apps work. The code, the screens and the backup format all stay the same. On a phone's browser the screen resizes to fit, and the app shows its phone layout.
- **It was tried.** The current build ran in a container as TrueNAS's `apps` user (568) and drew in a browser. It restored a backup and wrote a new one, all through the browser, with no app changes (section 3).
- **Three pieces of work**, each of medium size:
  1. **The container (srv-1):**
     - the image and a compose file for TrueNAS's "Install via YAML";
     - a CI job that builds, tests and publishes the image;
     - a few wording fixes.
  2. **Photos on the server (srv-2):** a Linux image codec on libvips, behind the existing `ImageCodec` interface and held to the existing conformance suite. Without it the server shows photos but can't add or crop them. That is the same limit any Linux build has today.
  3. **Backups that look after themselves (srv-3):**
     - scheduled backups into a backup folder, each one checked and old ones pruned;
     - a weekly test restore;
     - a health check that marks the app unhealthy in TrueNAS when backups stop.
- **Backups in three layers** (section 5):
  - **The app's own ZIPs:** portable, checksummed, and restorable on a phone, a Mac or the server.
  - **ZFS snapshots of the collection:** cheap, with instant rollback.
  - **An encrypted off-site copy of the ZIPs.**

  Section 5.6 is the restore runbook.
- **What it doesn't do:** sync. The server is one more copy of the collection, like the Mac. Phones and the server exchange backups (Merge or Replace) as they do today. A true web app (Compose for Web plus a server API) is possible later, but it means rebuilding the plumbing between the screens and the data (appendix D).

## 2. How it would work

### 2.1 The pieces

```
Browser (laptop, phone) ──HTTPS :5800──► container
                                          ├─ nginx + noVNC page (base image): login, file manager
                                          ├─ virtual screen (Xvnc) + Openbox (base image)
                                          └─ Watches: the Linux desktop build, with its own Java runtime
                                               ├─ /data      the collection: watches.db and files/
                                               ├─ /backups   ZIPs: scheduled ones, and Back up now
                                               └─ /import    photos and documents to add
```

- **Base image:** `jlesage/baseimage-gui`, on the Ubuntu 24.04 (as tried) or Debian 13 variant. It must be a glibc system: Skia's and the bundled SQLite's native libraries need glibc, so Alpine is out. The image is built for one GUI app per container. It gives:
  - the app's window maximised, with no frame;
  - a browser login (bcrypt passwords) and HTTPS;
  - a web file manager limited to chosen folders;
  - `USER_ID`/`GROUP_ID`;
  - a page that can be installed on a phone's home screen.
- **The app:** the output of `:desktopApp:createDistributable`, for Linux x64. It carries its own trimmed Java runtime and is 162 MB. CI builds it with Temurin, as it does now.
- **One copy runs.** The app's single-instance lock (`SingleInstance`) already makes it the only owner of `/data`.

### 2.2 Folders

Each folder in the container is a TrueNAS dataset, owned by `apps` (568).

| In the container | Dataset (suggested) | What it holds | Snapshots | Off-site |
| --- | --- | --- | --- | --- |
| `/data` | `apps/watches/data` | The collection | Hourly for 2 days, daily for 30 | Through the ZIPs |
| `/backups` | `apps/watches/backups` | Backup ZIPs | Daily | Yes, encrypted |
| `/import` | `apps/watches/import` | Photos and documents on their way in | None | No |
| `/config` | `apps/watches/config` | The base image's own state: certificates, logins, browser settings | Daily | No |

### 2.3 Getting files in and out

- **Photos and documents:** upload them into `/import` through the web page's file manager, or copy them to an SMB share of that dataset. Then add them in the app as usual: its file dialog shows `/import`.
- **Backups:** Back up now saves into `/backups`; the dialog opens there. Take a backup away through the file manager, or a read-only SMB share of the backups dataset.
- **Phone to server:** on the phone, Back up now. Copy the ZIP into `/backups`. On the server, Restore, then Replace the first time or Merge after that. Server to phone is the same in reverse.

### 2.4 Access and security

The app has no accounts, because it was made for one person's phone, and the desktop build has no app lock. The web page is the front door, so:

- **Login and HTTPS:** `WEB_AUTHENTICATION=1` with `SECURE_CONNECTION=1`. Add the user with the image's `webauth-user` command, which stores a bcrypt hash in `/config`, rather than a password in an environment variable.
- **LAN or VPN only**, for example Tailscale or WireGuard on the TrueNAS. No port forwarding. Behind a reverse proxy with a real certificate, the page can also be installed on a phone's home screen and share the clipboard.
- **One port:** publish 5800 only, never 5900 (plain VNC).
- **File manager:** limited to `/import` and `/backups` (`WEB_FILE_MANAGER_ALLOWED_PATHS`). The web terminal stays off.
- **Privileges:** runs as 568, not root, with `no-new-privileges` (**verify** that the base image's start-up accepts it).
- **The image:** pinned base image by digest, rebuilt when the base updates, and scanned in CI.
- **The app's own network rules are unchanged.** Its only outbound traffic is still Lookup, from `shared/search`, as on the phone. The container adds the inbound web page. `checkNetworkIsolation` doesn't change.
- **Backups aren't encrypted**, by design, as now. Keep `/backups` private and encrypt the off-site copy.

## 3. What the trial showed

The trial ran on a Linux cloud server with Docker. It used this branch (0.8.0), built with `:desktopApp:createDistributable`, on `jlesage/baseimage-gui:ubuntu-24.04-v4`, driven from a headless Chromium.

**What worked:**
- **The self-test passes inside the container:** collection, photo intake (plain-Java codec), backup and restore, CSV and drawing.
- **Running as 568:** the app started, used about 300 MB of memory, and drew at 1280 × 820 in its wide layout. Openbox maximised the window without a frame.
- **Mouse and keyboard** from the browser both reach the app. Text typed into Add watch arrived.
- **Restore through the browser:**
  1. Restore from a backup opened the GTK file dialog.
  2. In `/backups`, choosing the ZIP showed its preview.
  3. Restore brought back 3 watches and 3 photos into `/data`, owned by 568.
- **Back up now:** the save dialog opened in `/backups` with the app's own file name, and wrote a valid ZIP owned by 568.
- **A phone** (iPhone 13 emulation) with `?resize=remote` in the address: the screen followed the phone's viewport, and the app reflowed into its phone layout and was legible. Without the parameter, the base image scales the desktop down on touch devices, which is unreadable.
- **ZFS snapshots are safe.** SQLite runs in WAL mode (`watches.db-wal` was there), so a snapshot taken while the app runs is safe.
- **libvips 8.15** in the image:
  - decodes HEIC (through libheif), WebP and AVIF;
  - reads GPS EXIF;
  - applies a HEIC's own turning and cut (`irot`, `imir`, `clap`), and doesn't apply an iPhone's EXIF turning a second time: `turned-6-iphone.heic` came out 64 × 48 with no orientation.

**What it turned up**, each fixed in srv-1:
1. **The Java runtime must be self-contained**, as Temurin's (which CI uses) is. One cut from Ubuntu's own OpenJDK links to the system's `libjpeg.so.8` and `libgif.so.7`, which Debian doesn't have, and the app then fails on its first photo. CI will check that every native library in the image resolves.
2. **Skia needs `libGL.so.1`** to start, even though it then draws in software. Setting `skiko.renderApi=SOFTWARE` skips the OpenGL attempt.
3. **File dialogs need GTK.** Without it, Java falls back to a bare X11 file dialog. That dialog starts in a system folder, is maximised over the app, and didn't take a typed path in the trial. With GTK 3 installed, Java uses the GTK dialog, sized as a dialog. Two more settings complete the fix:
   - the app starts in `/backups`, so the dialog opens there;
   - Openbox maximises only the window titled "Watches".
4. **The container's clock is UTC** unless `TZ` is set. Backup file names and the backup time use local time.
5. **The base image's `apt-get` fails** on fontconfig, because the image has no `/var/log`. Its own `add-pkg` works.
6. **The screens say "this phone"**, and the error dialog says "this Mac". The Mac plan already notes the wording (`MACOS_PLAN.md` 8.3.8).

**Not tried:**
- TrueNAS itself, and a real phone's browser;
- the web file manager, and HTTPS with a login;
- opening a PDF (Share has no viewer in the container);
- Lookup from inside the container;
- 500 watches over the remote screen.

## 4. What changes in the app

The changes are small on purpose. Nothing in `shared/core` or the archive format changes. Android, iPhone and Mac are unaffected unless noted.

| Change | Where | Size |
| --- | --- | --- |
| Linux codec on libvips | `shared/data` `jvmMain`, beside `MacBridge` | M |
| Photos allowed when a full codec is loaded (the Mac bridge or libvips) | `Limits.jvm.kt` | XS |
| `BackupRepository.verify`: check a ZIP against its manifest without writing | `shared/data` `commonMain` (every platform gains it) | S |
| Scheduled backups: timing, checks, pruning, status | `desktopApp`, new `AutoBackup.kt` | M |
| `--verify-backup <zip>`: a test restore into a temporary folder | `desktopApp` (`Main.kt`, beside `--self-test`) | S |
| The self-test stores the HEIC when libvips is loaded | `SelfTest.kt` | XS |
| Wording: "this computer" rather than "this phone" or "this Mac" on a desktop | `Main.kt`, and the screens through a platform word | S |
| Optional: the backup's snapshot of the tables taken in one read transaction | `BackupRepository.snapshot` | XS |

## 5. Backups and restores

### 5.1 Three layers

1. **The app's ZIPs: the real backup.** Portable, checksummed, and restorable on any device. Written on a schedule into `/backups/auto`.
2. **ZFS snapshots of `/data`:** a quick rollback of the whole collection. They protect against a bad update, or a mistake made between scheduled backups.
3. **Off-site:** a TrueNAS Cloud Sync task, or replication to another TrueNAS, of the backups dataset, encrypted.

That's the usual 3-2-1: the collection, local backups, and one copy elsewhere.

### 5.2 Scheduled backups (srv-3)

They are off unless `WATCHES_BACKUP_DIR` is set. A switch in Settings could come later.

| Variable | Default | Meaning |
| --- | --- | --- |
| `WATCHES_BACKUP_DIR` | Not set: off | Where scheduled backups go, such as `/backups/auto` |
| `WATCHES_BACKUP_AT` | `03:30` | The time each day, in the container's `TZ` |
| `WATCHES_BACKUP_KEEP_DAILY` | `14` | The newest this many are kept |
| `WATCHES_BACKUP_KEEP_MONTHLY` | `12` | As well as the newest of each month, for this many months |
| `WATCHES_BACKUP_VERIFY_DAYS` | `7` | How often a test restore runs (5.3) |

How a scheduled backup runs:
- **The same export as Back up now.** It runs inside the running app through `BackupRepository`, so it shares the repository's lock and never overlaps a restore. If a restore or export is running, it tries again ten minutes later.
- **Missed runs:** if the app was stopped at the scheduled time and the last good backup is over a day old, it runs at start-up.
- **Unchanged collections:** a fingerprint of the row counts, the newest edit time and the file sizes is recorded. If the collection hasn't changed since the last scheduled backup, no new file is written, so a quiet year doesn't leave 365 identical files to keep and send off-site. The check is still recorded, and counts as a success for the health check.
- **Writing safely:**
  1. The backup is written to `….zip.partial` and checked (5.3).
  2. Only then is it renamed to `Watches backup 2026-11-25 0330.zip`.
  3. A failure or cancellation removes the partial file and leaves a log line. Nothing is ever overwritten.
- **Pruning** happens only in the scheduled folder, so backups you saved by hand are never touched.
- **Recording:**
  - the app's own last-backup setting, so Settings › Backup & restore says "Backed up today", as for Back up now;
  - `status.json` in the folder: last attempt, last success, file, counts, size, last test restore, error;
  - one line per run in the container's log, which TrueNAS shows under the app's logs.
- **Health:** the image's health check reads `status.json`. The container turns unhealthy when the last success is over 36 hours old or the last test restore failed, and TrueNAS shows the app as unhealthy.

### 5.3 Checking backups

- **Every scheduled backup** is read back and each file's SHA-256 checked against the manifest. These are the checks a restore makes before it touches anything. The new `BackupRepository.verify` streams the file and writes nothing.
- **A weekly test restore.** The app starts a second copy of itself with `--verify-backup <newest ZIP>`, which:
  1. restores the ZIP into an empty temporary folder with the real restore code (Replace);
  2. checks the counts against the manifest;
  3. renders one photo;
  4. deletes the folder.

  It never touches `/data`, so it doesn't need the collection's lock.
- **The same command runs by hand:** `docker exec <container> /opt/watches/bin/Watches --verify-backup /backups/auto/<file>`. It also proves an old backup still restores in the current version.

### 5.4 ZFS snapshots

This is set up in TrueNAS; no code is involved.
- **A Periodic Snapshot Task** on `apps/watches`, recursive: hourly kept for 2 days, daily kept for 30. Adjust to taste.
- **Safe while the app runs.** SQLite in WAL mode (seen in the trial) recovers a snapshot the way it recovers from a power cut. A restore caught mid-way is finished or undone at the next launch by the restore journal. For a copy made while nothing was changing, use the scheduled ZIP.
- **Before every image update**, take a manual snapshot. TrueNAS doesn't snapshot host-path datasets for you when it updates an app.

### 5.5 Off-site

- **A Cloud Sync task** pushes `apps/watches/backups` each night after the backup time, with TrueNAS's remote encryption turned on. The ZIPs hold receipts, invoices and serial numbers. ZFS replication to another TrueNAS works too.
- **Keep the encryption password off the NAS**, in a password manager.

### 5.6 Restore runbook

| What happened | What to do | What's lost |
| --- | --- | --- |
| A watch deleted by mistake | In the app: Restore › the newest scheduled backup › **Merge**. Merge never deletes, and adds back what's missing. | Edits to that watch since the backup |
| A watch changed by mistake | Merge won't undo it, because the more recently edited side wins. Roll `data` back to a snapshot from before the change, as in the next row. | Changes since the snapshot |
| More than one thing went wrong today | Stop the app, roll `apps/watches/data` back to a snapshot, start the app | Changes since the snapshot |
| An update broke something | Stop the app, roll back `data` to the snapshot taken before the update, set the image tag back to the previous version, start | Changes since the update |
| The dataset or the NAS is lost | Install the app with an empty `/data`, copy a ZIP from off-site into `/backups`, then in the app Restore › **Replace** | Changes since that backup |
| A phone's collection should move to the server | Phone: Back up now. Copy the ZIP into `/backups`. Server: Restore › Replace | Nothing |
| The server's collection should go to a phone | Download the ZIP through the file manager. Phone: Restore | Nothing |

Try the fourth row once, soon after setting up.

### 5.7 Updates, and going back

- **Image tags are app versions** (`0.9.0`), plus `latest`. Pin a version in TrueNAS and update deliberately.
- **A newer version may upgrade the database**, and an older version then won't open it. It says so and changes nothing (`StoredCollection`). It also refuses a backup made by a newer version. So going back means:
  - rolling `data` back to its snapshot from before the update, and using the older tag; or
  - restoring an older ZIP into the older version.

  Either way, take the snapshot before updating (5.4).

### 5.8 Phones and the server

Nothing syncs. Pick one copy as home:
- **The server is home:** use it from any browser, phones included. A phone's own app becomes optional, or a read-only copy refreshed from a server backup now and then.
- **A phone is home:** the server is where the phone's backups are kept and checked. Restoring them onto the server makes them browsable too.

Using both for edits means moving backups both ways with Merge. Merge never deletes, so a watch deleted on one side comes back from the other.

## 6. What needs implementing

### 6.1 It runs on TrueNAS (milestone srv-1)

1. **`docker/Dockerfile`** on the base image, pinned by digest:
   - the runtime libraries, through `add-pkg`: fontconfig, the X libraries, `libgl1`, GTK 3, libvips and its HEIC plugin;
   - the CI-built app in `/opt/watches`;
   - `LANG=C.UTF-8`, and `KEEP_APP_RUNNING=1`, so closing the window restarts the app rather than stopping the container.
2. **`docker/rootfs/`:**
   - `startapp.sh`: sets `WATCHES_DATA_DIR=/data` and the Java options (software drawing, GTK 3, a heap limit), moves to `/backups`, then runs the app;
   - the Openbox rule that maximises only the "Watches" window;
   - the health check (it only reports "running" until srv-3).
3. **A start-up check:** if `/data` isn't writable by 568, the app says so in the log and on screen rather than failing later.
4. **Phones:** document the address with `?resize=remote` as the one to bookmark or install on a phone. Changing the base image's default would need patching its web page, which breaks when the base updates, so it isn't done.
5. **`docker/compose.yaml`** for TrueNAS's Install via YAML (appendix A).
6. **CI, in the Android workflow:**
   - upload the Linux app as an artifact;
   - a new job builds the image;
   - checks every native library resolves (`ldd`);
   - runs `--self-test` inside the image;
   - starts it and screenshots the page at desktop and phone size;
   - scans the image.
   On `main` and `v*` tags it publishes to GHCR (`ghcr.io/tunlezah/watches`), tagged with the version and `latest`.
7. **Wording:** "this computer" in place of "this phone" or "this Mac" where the desktop build shows it, starting with the error dialog and the welcome and backup screens. This is shared with the Mac's 8.3.8.
8. **`docs/SELF_HOSTING.md`:**
   - datasets and permissions;
   - installing the app;
   - logins and HTTPS;
   - the first restore from a phone backup;
   - updates;
   - section 5's runbook;
   - the TrueNAS checklist (appendix C).

**Done when:**
- the image from CI runs on your TrueNAS;
- you log in from a laptop and a phone;
- you restore a phone backup;
- Back up now lands in `/backups`.

### 6.2 Photos on the server (milestone srv-2)

1. **`VipsImageCodec`** in `shared/data` `jvmMain`: libvips' C API through JNA. The app already ships JNA, and `MacBridge` shows the pattern.
   - **Probe:** reads the header only: width, height and turning. libheif turns a HEIC upright itself, so a HEIC reports `Orientation.NORMAL`.
   - **Render:**
     1. load, shrinking while loading where the plan allows;
     2. cut the stored region;
     3. turn and flip;
     4. scale to exactly the plan's size;
     5. fill transparent areas with black;
     6. convert to sRGB;
     7. save as JPEG at the given quality, with no metadata.

   **Fallback, if JNA proves awkward** (libvips' API uses variable arguments): call the `vips` command for each step. It's slower per photo but fine for one person.
2. **Choosing a codec:** the Mac bridge, then libvips (when `libvips.so.42` loads and reads HEIC), then plain Java. `platformLimits.photos` is null when the bridge or libvips is loaded.
3. **Tests:** Linux CI installs libvips and the HEIC plugin. `testImageProcessor()` picks libvips when told to, and the whole `PhotoConformanceTest` runs against it, HEIC cases included. In the image, the self-test must store the HEIC.
4. **Memory:** a Java heap limit (about 1 GB) and a container limit (2 GB). libvips works outside the Java heap.

**Done when:**
- the conformance suite passes on libvips in CI;
- in the container, an iPhone HEIC can be added and cropped, and shows upright without its location.

### 6.3 Backups that look after themselves (milestone srv-3)

1. **`BackupRepository.verify(open)`:** streams the ZIP and checks every file against the manifest.
2. **`AutoBackup`** in `desktopApp`, as 5.2 describes:
   - the schedule, and catching up on a missed run;
   - skipping an unchanged collection;
   - write to `.partial`, check, rename;
   - pruning;
   - `status.json`, the log line, and the last-backup setting.
3. **`--verify-backup <zip>`:** the test restore (5.3), started weekly by `AutoBackup` as a separate process.
4. **The health check** in the image reads `status.json`.
5. **Optional:** `WATCHES_RESTORE_ON_EMPTY=<zip>`. When `/data` holds no collection at start-up, that backup is restored before the window opens, for rebuilding a server unattended.
6. **Tests:**
   - the schedule, pruning and skip rules, with a fake clock (`TimeSource` exists);
   - `verify` catching a changed byte, and a missing file;
   - `--verify-backup` on the Mac-made reference archive (`fixtures/archives/mac.zip`) and on a fresh one.

**Done when:**
- a week of scheduled backups is in `/backups/auto`, pruned as set;
- `status.json` reports healthy, and a test restore has passed;
- TrueNAS shows the app unhealthy when the folder is made read-only.

### 6.4 Order

srv-1, then srv-3, then srv-2. srv-1 gives a usable server straight away. srv-3 protects whatever goes into it. srv-2 is only urgent if the server is where photos get added (decision 2 in section 9).

## 7. Tests

Every existing test stays as it is, on every platform. New:

| Test | Where | Checks |
| --- | --- | --- |
| Container smoke test | Linux CI | Every library resolves; the self-test passes in the image; the page answers; screenshots at desktop and phone size |
| Photo conformance on libvips | Linux CI | The same 31 fixtures and expectations as Android, iPhone and Mac |
| Backup scheduling and checks | Linux CI (desktop tests) | Timing, catching up, skipping, pruning, `verify`, `--verify-backup` |
| On your TrueNAS | You, once per milestone | Appendix C |

## 8. Risks

| Risk | Effect | What reduces it |
| --- | --- | --- |
| A remote screen on a phone feels less direct than an app: some latency, and the on-screen keyboard has to be opened from the page's control bar | Usable, not native | The phone layout works (section 3). Try a real phone first in srv-1, before more work goes in. If noVNC feels poor, the LinuxServer.io Selkies base streams over WebRTC and handles touch better (appendix D). |
| The base image changes or is abandoned | A rebuild on another base | Pinned digest. The image is widely used, and the Selkies base is an alternative. The app doesn't depend on either. |
| Different libvips or libheif versions decode differently | Photo bugs | The conformance suite runs against the image's own libraries |
| One password guards everything | Exposure if the port is forwarded | LAN or VPN only, HTTPS, a strong password, a restricted file manager |
| Image size (about 1 GB on disk) and memory (about 400 MB, more while handling photos) | Matters only on a very small NAS | Measured in CI; a memory limit in the compose file |
| Phone and server copies drift apart | Confusion about which is right | Pick a home copy (5.8). Merge never deletes. |
| The GHCR package is private while the repository is private | TrueNAS can't pull the image | Make the package public (it holds no data or secrets), or give TrueNAS a read-only token |

## 9. Decisions for you

1. **Approach:** the desktop build in a browser, as planned here, or a true web app (appendix D, much larger).
2. **Which copy is home** (5.8): the server, or a phone with the server keeping its backups. This decides how soon srv-2 is needed.
3. **Access:** LAN only, a VPN, or a reverse proxy with your own domain and certificate.
4. **Backups:** the time and how many to keep (5.2), and the off-site target, such as Backblaze B2, S3 or another NAS.
5. **The image:** a public or private package on GHCR.

## 10. Not in this plan

- Sync between phones and the server.
- More than one user, or accounts.
- Service reminders from the server, by email or push. That would need network code outside `shared/search`, so it is a decision of its own.
- ARM images. TrueNAS SCALE runs on x86-64.
- PDF pages inside the app. As on the Mac, a PDF opens in another app; the image could carry a viewer later.
- The app lock on the server. The web login takes its place.

## Appendix A: compose file sketch

For TrueNAS › Apps › Discover › Custom App › Install via YAML. Create the datasets first, owned by `apps` (568).

```yaml
services:
  watches:
    image: ghcr.io/tunlezah/watches:0.9.0     # a version, not latest
    restart: unless-stopped
    ports:
      - "5800:5800"                          # web page only; never 5900
    environment:
      USER_ID: "568"
      GROUP_ID: "568"
      TZ: "Etc/UTC"                          # set your own, e.g. Australia/Sydney
      SECURE_CONNECTION: "1"
      WEB_AUTHENTICATION: "1"                # add the user with: webauth-user add <name>
      WEB_FILE_MANAGER: "1"
      WEB_FILE_MANAGER_ALLOWED_PATHS: "/import,/backups"
      WATCHES_BACKUP_DIR: "/backups/auto"    # srv-3
      WATCHES_BACKUP_AT: "03:30"
    volumes:
      - /mnt/tank/apps/watches/config:/config
      - /mnt/tank/apps/watches/data:/data
      - /mnt/tank/apps/watches/backups:/backups
      - /mnt/tank/apps/watches/import:/import
    security_opt:
      - no-new-privileges:true               # verify with the base image (2.4)
    mem_limit: 2g
```

## Appendix B: image sketch

The trial's image, with its fixes:

```dockerfile
FROM jlesage/baseimage-gui:ubuntu-24.04-v4@sha256:<pinned>
ENV LANG=C.UTF-8
RUN add-pkg fontconfig libfreetype6 libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
      libgl1 libasound2t64 libgtk-3-0t64 libvips42t64 libheif-plugin-libde265
COPY Watches /opt/watches        # :desktopApp:createDistributable, built by CI with Temurin
COPY rootfs/ /                   # startapp.sh, the Openbox rule, the health check
RUN set-cont-env APP_NAME "Watches" && set-cont-env KEEP_APP_RUNNING 1
```

```sh
#!/bin/sh
# /startapp.sh
export WATCHES_DATA_DIR=/data
export JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE -Djdk.gtk.version=3 -Xmx1g"
cd /backups
exec /opt/watches/bin/Watches
```

```xml
<!-- /etc/openbox/main-window-selection.xml: maximise the app, not its dialogs -->
<Type>normal</Type>
<Title>Watches</Title>
```

## Appendix C: TrueNAS checklist

**Setting up**
- [ ] Datasets `apps/watches/{config,data,backups,import}`, owned by `apps` (568).
- [ ] App installed from appendix A's YAML; the login user added; the page opens over HTTPS.
- [ ] From a laptop: the wide layout, typing, and a file dialog showing `/backups`.
- [ ] From a phone, at `…:5800/?resize=remote`: the phone layout, and the keyboard.
- [ ] A phone backup copied into `/backups` and restored (Replace).
- [ ] Back up now lands in `/backups`.

**Backups**
- [ ] A Periodic Snapshot Task on `apps/watches`.
- [ ] A Cloud Sync task (encrypted) or replication for `apps/watches/backups`.
- [ ] srv-3: a scheduled backup appears the next morning; `status.json` reports healthy.
- [ ] Drill: roll `data` back to a snapshot and start.
- [ ] Drill: an empty `/data`, then restore from the off-site copy.

**Updating**
- [ ] Snapshot, change the tag, start, check. Going back once, to prove it works.

## Appendix D: alternatives considered

**A true web app: Compose for Web (Wasm) in front of a server.** The screens are Compose Multiplatform, and Compose for Web has been in Beta since 1.9, so the UI could compile for the browser. The plumbing behind the screens could not come along:
- **The data layer:** the view models call eight concrete repository classes directly (`WatchRepository`, `PhotoRepository`, `BackupRepository` and others), reading live Room flows. Each would need:
  - an interface;
  - an implementation over HTTP and WebSockets;
  - a server endpoint.
- **Photos** would load by URL rather than by file path.
- **Lookup** would move behind the server, since browsers can't call those sites directly.
- **File dialogs** would become uploads and downloads.
- **The network:** the UI would need it, which `checkNetworkIsolation` forbids by design.

That is weeks of work and a second architecture to keep up. It's worth it only if a web app that feels native on a phone matters more than reusing what exists. The container in this plan doesn't block it: the data folder and the backups would carry over.

**Compose for Web with the collection kept in the browser.** The collection would live in one browser's storage rather than on the NAS, which defeats the point.

**LinuxServer.io's Selkies base image** instead of jlesage's:
- **For:** it streams over WebRTC, handles touch and resizing better, and has a file-transfer sidebar.
- **Against:** it runs a full desktop stack, passwordless `sudo` is on by default, and its own documentation calls its login a convenience rather than a safeguard.

It stays the fallback if the noVNC page feels poor on a phone (section 8).

**No container at all.** Keep the phones as the only copies and save backups to an SMB share on the TrueNAS: Back up now › Files › the share. It needs no work at all and covers backups, but gives no browser access. It can be combined with this plan.
