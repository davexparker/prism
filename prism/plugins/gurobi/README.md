# Gurobi LP Solver Plugin

This plugin provides a Gurobi backend for PRISM's LP-based MDP model checking.
It is compiled conditionally — only when `lib/gurobi.jar` is present.

## Installation

1. Obtain a Gurobi licence and download the Gurobi software package.
2. Copy the following files into PRISM's `lib/` directory:
   - `gurobi.jar` — the Gurobi Java SDK
   - `libgurobi130.dylib` (macOS) / `libgurobi130.so` (Linux) — Gurobi native lib
   - `libGurobiJni130.dylib` (macOS) / `libGurobiJni130.so` (Linux) — JNI bridge
3. Run `make prism_java` from the `prism/` directory.
   This produces `lib/gurobi-plugin.jar` automatically.

The plugin is then active at runtime. To select it:

```
./bin/prism model.pm props.pctl -ex -lpsolver gurobi
```

## Pre-built artifact

If you have a pre-built `gurobi-plugin.jar` (e.g., from CI), drop it into `lib/`.
No `make` invocation needed — `lib/*` is on the runtime classpath.

## Removing the plugin

Delete `lib/gurobi-plugin.jar`. PRISM will revert to lpsolve for LP solving.

## IDE development (IntelliJ)

To edit `GurobiSolver.java` with full IDE support:

1. Ensure `lib/gurobi.jar` is present (it is picked up automatically as a project
   library via the existing `lib/` library entry).
2. In IntelliJ: **File → Project Structure → Modules → prism → Sources**,
   click **+** and add `plugins/gurobi/src` as a source folder.
3. Run `make prism_java` once to produce `lib/gurobi-plugin.jar`; subsequent
   changes to `GurobiSolver.java` can be compiled within IntelliJ directly.
