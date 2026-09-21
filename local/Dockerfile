# Builds an Aerospike server image from whatever RPM is dropped in at
# local/server.rpm — an unreleased/internal build, before it's public (see
# ../README.md's "Known, expected limitation" and ../local/README.md).
#
# This file itself is generic and tracked normally: it has no version number,
# no filename to update, and nothing confidential in it. The only thing that
# ever changes when the internal build moves forward is local/server.rpm
# itself, which is gitignored and never touches this repo's history —
# dropping in a new RPM (any version) needs no edits here at all.
FROM amazonlinux:2023

# shadow-utils provides groupadd/useradd, which the RPM's preinstall
# scriptlet needs to create the `aerospike` user — not a declared RPM
# dependency (confirmed via `rpm -i` without --nodeps: no missing-deps
# error once shadow-utils is present, true for both the 8.1.3 and 8.2.0
# builds tested so far), just an unstated assumption.
RUN dnf install -y shadow-utils && dnf clean all

COPY server.rpm /tmp/aerospike-server.rpm
RUN rpm -i /tmp/aerospike-server.rpm && rm -f /tmp/aerospike-server.rpm

EXPOSE 3000 3001 3002 3003

# --fgdaemon, not --foreground: this is the flag the RPM's own systemd unit
# (aerospike.service) uses, documented by `asd --help` as "useful for
# running Aerospike under systemd or Docker" — --foreground is the gdb-
# debugging variant. No vendor entrypoint/templating step here (unlike the
# public image — see ../docker-compose.yml's comment on that mount), so this
# expects the real config mounted directly at /etc/aerospike/aerospike.conf.
ENTRYPOINT ["/usr/bin/asd", "--config-file", "/etc/aerospike/aerospike.conf", "--fgdaemon"]
