#!/bin/sh
# Starts Watches inside the server image (docs/SELF_HOSTING.md). The base image runs this as the
# app's user (USER_ID, GROUP_ID), and starts it again if the app closes.
set -u

# The collection: a mounted folder, so it outlives the container.
export WATCHES_DATA_DIR=/data

# Skia draws in software: there's no GPU here. GTK's file dialogs. A small, compact heap, which still
# has room for a large photo (WATCHES_MAX_MEMORY raises it).
export JAVA_TOOL_OPTIONS="-Dskiko.renderApi=SOFTWARE -Djdk.gtk.version=3 -XX:+UseSerialGC -Xmx${WATCHES_MAX_MEMORY:-512m} ${JAVA_TOOL_OPTIONS:-}"

if ! mkdir -p /data 2>/dev/null || [ ! -w /data ]; then
    echo "Watches can't write to /data. Give user $(id -u) (group $(id -g)) write access to the folder mounted there." >&2
    # Not straight away: the base starts the app again, and a fast loop would only fill the log.
    sleep 60
    exit 1
fi
if ! mountpoint -q /data 2>/dev/null; then
    echo "Warning: no folder is mounted at /data, so the collection will be lost when this container is removed." >&2
fi

# File dialogs (GTK's): Backups and Import in the sidebar, and opening in the current folder rather
# than "Recent". Settings go in a plain file, since there's no settings service here.
config="${XDG_CONFIG_HOME:-$HOME/.config}"
mkdir -p "$config/gtk-3.0" "$config/glib-2.0/settings"
printf 'file:///backups Backups\nfile:///import Import\n' > "$config/gtk-3.0/bookmarks"
keyfile="$config/glib-2.0/settings/keyfile"
grep -q 'startup-mode' "$keyfile" 2>/dev/null || printf "[org/gtk/settings/file-chooser]\nstartup-mode='cwd'\n" >> "$keyfile"
export GSETTINGS_BACKEND=keyfile

# The current folder: where backups are kept.
cd /backups 2>/dev/null || cd /data
exec /opt/watches/bin/Watches
