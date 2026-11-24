# Watches on a home server: the desktop build, shown in a web page (docs/SELF_HOSTING.md).
#
# Built from the repository's root, after the Linux app is packaged (CI does both):
#   ./gradlew :desktopApp:createDistributable
#   docker build -f docker/Dockerfile --build-arg VERSION=0.8.0 -t watches .
#
# The package must carry a self-contained Java runtime such as Temurin's, as CI's does. One cut from
# a Linux distribution's own OpenJDK links to that system's JPEG library, and the check below fails.

# jlesage's base runs one GUI app per container and shows it in a web page: a virtual screen, a VNC
# server, noVNC, nginx, a login and a file manager. Debian, because the app's native libraries (Skia,
# SQLite) need glibc.
FROM jlesage/baseimage-gui:debian-13-v4

ARG VERSION=dev

# What the app needs beyond the base: fonts and the X libraries Java draws with, GTK for the file
# dialogs, and libheif (with its HEVC plugin) and libwebp for photos in HEIC and WebP.
# Skia's library won't load without libGL.so.1, but the app draws in software, so only the small
# OpenGL dispatch library goes in, not Mesa's drivers, which would add about 200 MB.
RUN add-pkg \
        fontconfig libfreetype6 libxext6 libxrender1 libxtst6 libxi6 libxrandr2 \
        libgtk-3-0t64 \
        libheif1 libheif-plugin-libde265 libwebp7 \
 && cd /tmp \
 && apt-get update \
 && apt-get download libgl1 libglx0 libglvnd0 \
 && for deb in *.deb; do dpkg-deb -x "$deb" /; done \
 && rm -rf /tmp/*.deb /var/lib/apt/lists/* \
 && ldconfig

# The browser tab's and home screen's icon, from the app's own.
COPY iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon.png /tmp/icon.png
RUN install_app_icon.sh file:///tmp/icon.png && rm /tmp/icon.png

# The page resizes the app's screen to fit the browser, so a phone gets the app's phone layout. The
# base scales a large screen down instead on touch devices and small windows, which is unreadable
# for an app with layouts of its own. (sed-patch fails the build if the base's page has changed.)
RUN sed-patch "s/resize = 'scale';/resize = 'remote';/g" /opt/noVNC/app/ui.js

COPY desktopApp/build/compose/binaries/main/app/Watches /opt/watches
COPY docker/rootfs/ /

# Every native library the app loads must be here. libjvm is found by the runtime itself, and the
# sound library is never used.
RUN missing="$(for lib in /opt/watches/lib/app/*.so /opt/watches/lib/runtime/lib/*.so /opt/watches/lib/runtime/lib/server/*.so; do \
        ldd "$lib" | grep 'not found' | grep -v -e 'libjvm\.so' -e 'libasound\.so' | sed "s|^|$lib:|"; done)" \
 && if [ -n "$missing" ]; then echo "Missing libraries:"; echo "$missing"; exit 1; fi \
 && ldconfig -p | grep -q 'libheif\.so\.1 ' && ldconfig -p | grep -q 'libwebp\.so\.7 ' \
 && mkdir -p /data /backups /import \
 && chmod 0777 /data /backups /import \
 && chmod +x /startapp.sh

ENV LANG=C.UTF-8 \
    WATCHES_SYSTEM_IMAGE_LIBS=required

RUN set-cont-env APP_NAME "Watches" \
 && set-cont-env APP_VERSION "$VERSION" \
 && set-cont-env DOCKER_IMAGE_VERSION "$VERSION" \
 && set-cont-env KEEP_APP_RUNNING 1

LABEL org.opencontainers.image.title="Watches" \
      org.opencontainers.image.description="A private watch collection, in your browser" \
      org.opencontainers.image.source="https://github.com/tunlezah/Watches" \
      org.opencontainers.image.version="$VERSION"
