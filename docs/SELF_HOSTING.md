# Watches on a home server

Watches can run on a home server, such as a TrueNAS box, and be used from any browser on your home network: a laptop, a tablet or a phone. It is the same app as on the Mac, in a container that shows its window in a web page. The screens, the designs and the backup files are the same as on the phones and the Mac, so a backup moves freely between all of them.

It is small for what it is: a 220 MB download, about 500 MB on disk, and about 300 MB of memory, with almost no processor use while you aren't using it.

The plan and its decisions are in [`SELF_HOSTING_PLAN.md`](SELF_HOSTING_PLAN.md).

## What you need

- **A server running Docker.** TrueNAS SCALE 24.10 or later does: its apps are Docker containers. Any other x86-64 Linux machine with Docker works too, with [`docker/compose.yaml`](../docker/compose.yaml).
- **A browser on the same network.** Any modern one.

## Install on TrueNAS

1. **Make the datasets.** Under Datasets, add one called `watches` (inside an `apps` dataset, for example), with four datasets inside it: `data`, `backups`, `import` and `config`. Give each to the `apps` user and group (568): Edit Permissions, set both to `apps`, and apply to child datasets.
2. **Install the app.** Apps › Discover Apps, then Install via YAML (under Custom App or the ⋮ menu, depending on the TrueNAS version). Name it `watches` and paste [`docker/compose.yaml`](../docker/compose.yaml), then change:
   - `/mnt/tank/apps/watches`: to where your datasets are;
   - `TZ`: to your time zone, such as `Australia/Sydney`;
   - `WEB_AUTHENTICATION_PASSWORD`: to a password of your own.
3. **Open it** at `https://<your-server>:5800`. The browser warns about the certificate the first time, because the server made its own. Accept it, then log in with the username and password from step 2.

To add it to a phone's home screen, open the same address and use the browser's "Add to Home Screen".

## Using it

- **It's one screen**, and it resizes to fit your browser: a laptop gets the wide layout, a phone the phone layout. Two browsers open at once see the same screen, so use one at a time.
- **The panel on the left edge** of the page (the small handle) has the file manager (the folder), the clipboard, log out, and on a phone the keyboard: tap a field in the app, then the keyboard button.
- **Adding photos and documents.** Put the files in the `import` folder first: upload them with the page's file manager (left panel › the folder › `import` › the upload button), or copy them into the `import` dataset over an SMB share. Then in the app: Choose photos, and pick Import in the dialog's sidebar. iPhone photos (HEIC) and WebP are read, as on the phones, and a photo's location is removed when it is stored.
- **Lookup** works as on the phones. Its requests come from the server.

What the server doesn't do, as on the Mac today: the camera, service reminders and the app lock. The page's login stands in for the lock. PDFs can be added and kept, but their pages aren't shown in the app yet, and the server has no other app to open them in: read them on the phone or the Mac.

## Backups

Back up by hand, as on the phones:

- **Back up now:** Settings › Backup & restore › Back up now. The dialog opens in the backups folder. Take the file away with the page's file manager (left panel › the folder › `backups` › the file's download button), or over an SMB share of the `backups` dataset.
- **Restore:** put a backup in the backups folder (upload it with the file manager, or copy it over SMB), then Settings › Backup & restore › Restore from a backup.

**Worth setting up once on TrueNAS: snapshots.** Data Protection › Periodic Snapshot Tasks › Add, on the `watches` dataset, recursive, daily, kept for two to four weeks. It costs almost nothing, needs no setup in the app and is safe while the app runs. To go back to a snapshot: stop the app, roll back the `data` dataset, start the app.

Backup files aren't encrypted. Keep the `backups` dataset private, and encrypt any copy that leaves your home.

## Phone, Mac and server

Each device has its own collection; nothing syncs between them. To carry changes from one to another, back up on the device you changed and restore the file on the others with **Merge**:
- Merge matches watches by their ID, adds what is missing, and for a watch changed on both sides keeps the more recently edited one. It tells you which watches differed.
- Merge never deletes. A watch deleted on one device stays on the others until it is deleted there too.
- **Replace** makes the device an exact copy of the backup instead.

Working on one device at a time, then carrying a backup across, keeps them in step.

## Updating

Images are published by the Android workflow to GitHub's container registry, as `ghcr.io/tunlezah/watches`: `latest` from the repository's default branch, and each version (such as `0.9.0`) from its `v` tag. The first time one is published, check its visibility on GitHub (your profile › Packages › watches › Package settings) and make it public if it isn't: a public image is free to pull and needs no login on the server. The image holds the app only, never a collection.

- **To update**, take a snapshot of the `watches` dataset, then in TrueNAS Apps › watches › Edit, change the image's tag, or with `latest`, pull the newer image.
- **To go back**, roll the `data` dataset back to the snapshot from before the update and use the older tag. A newer version may upgrade the collection, and an older version then refuses to open it, says so and changes nothing.

Using a version tag rather than `latest` keeps updates in your hands.

## Settings

All are environment variables in the compose file.

| Setting | Default | What it does |
| --- | --- | --- |
| `USER_ID`, `GROUP_ID` | `1000` | Who the app runs as. On TrueNAS, `568`, the apps user that owns the datasets. |
| `TZ` | `Etc/UTC` | Time zone, for the times in backups' names. |
| `SECURE_CONNECTION` | `0` | `1` serves the page over HTTPS. Required for the login. |
| `WEB_AUTHENTICATION` | `0` | `1` puts a login in front of the app. |
| `WEB_AUTHENTICATION_USERNAME`, `WEB_AUTHENTICATION_PASSWORD` | | The login. More users can be added with `webauth-user add <name>` in the container's shell. |
| `WEB_FILE_MANAGER` | `0` | `1` adds the file manager to the page's left panel. |
| `WEB_FILE_MANAGER_ALLOWED_PATHS` | | The folders it may use: `/backups,/import`. |
| `DARK_MODE` | `0` | `1` darkens the page around the app (the app has its own appearance settings). |
| `WATCHES_MAX_MEMORY` | `512m` | The app's memory ceiling, for very large photos. Raise `mem_limit` with it. |

The base image ([jlesage/baseimage-gui](https://github.com/jlesage/docker-baseimage-gui)) has more, documented there.

| Folder | Holds |
| --- | --- |
| `/data` | The collection: its database and every photo and document. |
| `/backups` | Backup files. |
| `/import` | Photos and documents on their way in. |
| `/config` | The page's own settings, logins and certificate. |

## Security

- **Keep it on your home network.** Don't forward port 5800 from your router. To reach it from outside, use a VPN such as Tailscale or WireGuard.
- **Use the login.** The app has no accounts, so the page's login is what keeps others on your network out.
- **Your own certificate** replaces the self-signed one: put `web-privkey.pem` and `web-fullchain.pem` in the `config` dataset's `certs` folder.
- **What it runs as:** the app user (568), not root, with `no-new-privileges`. The file manager reaches only `/backups` and `/import`.
- **Network:** the app's only outbound requests are Lookup's, as on the phone. Every one is listed in Settings › Request log.

## Troubleshooting

- **The app's log** is under TrueNAS Apps › watches › Logs. Lines from the app start with `[app]`.
- **"Watches can't write to /data"** in the log: give the `apps` user (568) write access to the `data` dataset.
- **"No folder is mounted at /data"**: the collection would be lost with the container. Check the `volumes` in the compose file.
- **A large photo fails to add:** raise `WATCHES_MAX_MEMORY` (to `1g`, say) and `mem_limit` (to `2g`).

## Building the image yourself

With JDK 21 (Temurin, whose Java runtime carries its own image libraries) and Docker:

```sh
./gradlew :desktopApp:createDistributable
docker build -f docker/Dockerfile --build-arg VERSION=0.9.0 -t watches .
docker run --rm --entrypoint /opt/watches/bin/Watches watches --self-test /tmp/self-test
```

The last line checks the image without opening a window: it makes a collection, adds a HEIC photo, backs it up and restores it, and draws a frame. CI does the same on every push.
