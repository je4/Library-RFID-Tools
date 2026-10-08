---
sessionId: session-261008-161650-eesw
---

# Requirements

### Overview & Goals
Fully align the project and tooling with Java 25 LTS/current runtime. Ensure consistent Java 25 toolchain resolution, update compilation and execution scripts (`run-inventory.ps1` and `build-installer.ps1`), purge residual classes, and align documentation with Java 25.

### Scope
#### In Scope
- Enforcing Java 25 compilation in `run-inventory.ps1` and `build-installer.ps1` (with `--release 25`).
- Consistent Java toolchain resolution (`JAVA_HOME` and system `PATH` pointing to JDK 25).
- Cleaning outdated class files in `target\classes` to guarantee clean Java 25 bytecode.
- Updating `build-installer.ps1` fallback paths to prioritize JDK 25.
- Updating documentation in `readme.md`.
- Verifying compilation and execution.

#### Out of Scope
- Changing Java source code business logic in `src/main/java`.
- Modifying third-party FEIG binaries in `lib/`.

### Delivery Steps

### ✓ Step 1: Update run-inventory.ps1 and clean target classes
Update `run-inventory.ps1` to support clean recompilation targeting Java 25 (`--release 25`) with robust toolchain lookup, and purge existing `target\classes`.

### ✓ Step 2: Update build-installer.ps1 and readme.md, then verify execution
Update `build-installer.ps1` with Java 25 parameters/lookups and `readme.md` references, then test execution of `run-inventory.ps1` and `run-inventory.bat`.