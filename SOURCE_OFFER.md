# Corresponding Source Offer

FinGrind release bundles and container images include executable code governed by the GNU General Public License, version 2, and other licenses that require or may require source availability. This includes the linked Azul Zulu/OpenJDK runtime and, for the container image, components inherited from Alpine Linux.

For every such object-code component distributed by FinGrind, Ervins Strauhmanis, the FinGrind copyright holder and distributor, offers any third party a complete machine-readable copy of the corresponding source code, including the scripts used to control compilation and installation, under the same license terms. Electronic delivery is available without charge. If physical transfer is requested, the charge will not exceed the cost of physically performing that transfer.

This offer remains valid until at least three years after the last date on which Ervins Strauhmanis distributes the corresponding FinGrind release bundle or container image.

Request source at <https://github.com/resoltico/FinGrind/issues/new>. Identify the FinGrind release or container digest, operating system, architecture, and requested component. Requests for the complete source set corresponding to an artifact are accepted; a requester does not need to identify every individual package.

The FinGrind application source and the vendored SQLite3 Multiple Ciphers source are also available in the source archive for the matching FinGrind release at <https://github.com/resoltico/FinGrind/releases>. The linked Java module closure is recorded in `runtime/release`, its source JDK vendor/build identity is copied verbatim to `runtime/provenance/source-jdk-release`, and its governing license, exceptions, and module-specific third-party notices are indexed under `runtime/legal/`. A container additionally records the verified input JDK binary archive name and digest; that binary digest is provenance, not Corresponding Source. A container records its exact Alpine package, license identifier, source-package origin, and Alpine packaging commit in `/opt/fingrind/doc/ALPINE-PACKAGES.tsv`; `LICENSE-ALPINE-CONTAINER-COMPONENTS` reproduces the reviewed component notices and source routes.

This offer also covers still-available historical FinGrind bundles and container images. A historical request is mapped from the identified release asset or OCI digest and that artifact's own runtime/package provenance; current dependency versions are never substituted for historical object code.

Azul's licensing information for Zulu 26.32 (OpenJDK 26.0.2.1+1) is available at <https://docs.azul.com/core/tpls/august-2026/zulu26_jdk_tpl.html>. The accompanying `NOTICE-ZULU-26.32.203` preserves the pinned build's vendor-level licensing and upstream source-request statement. Azul separately states that source covered by source-availability obligations is available from Azul on request. This FinGrind offer is independent of that upstream statement and does not rely on the GPLv2 noncommercial pass-through alternative.

This offer does not alter the license governing any component, grant trademark rights, make a patent-clearance representation, or provide a warranty.
