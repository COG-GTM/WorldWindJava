# Windsurf prompts for this repo

Windsurf (with Cascade) reads the whole codebase, follows `AGENTS.md`, and executes complex tasks in the IDE while the engineer reviews each chunk before accepting. Every prompt below is **copy-paste ready** and produces a real artifact in this repo. Each one is built around the same idea: **trade engineer time for more review, not less.** That's how this becomes a de-risking tool instead of a productivity tool.

---

## Prompt 1 — Extend the symbology surface (capability extension)

> *Walk me through how `MilStd2525GraphicFactory` registers and instantiates tactical graphics, then scaffold a new `OPIRSensorFieldOfRegard` graphic under `gov.nasa.worldwind.symbology.milstd2525.graphics.areas`. It extends `AbstractMilStd2525TacticalGraphic`. Inputs: sensor position (lat, lon, altitude), slant range, half-angle, azimuth, elevation. Use `TacticalCircle` and `TacticalQuad` as reference patterns. Honor `AGENTS.md` end-to-end: no-arg constructor, namespaced constants in `MilStd2525Constants`, `OpenGLStackHandler` in the render path, JOGL `nativeByteOrder()`, offline-mode check, no external dependencies. Register the new type in `MilStd2525GraphicFactory`. Stop after the scaffold — do not implement the geometry math yet.*

**De-risk angle:** the engineer never touched WorldWind before. Cascade enforces the no-arg-constructor, namespaced-constants, and `OpenGLStackHandler` rules from `AGENTS.md` automatically. The engineer reviews each chunk in the IDE before accepting. New capability added; existing API contract untouched.

---

## Prompt 2 — Find risk before you write code (risk inventory)

> *Scan this repo and produce a markdown report with: (a) every public method in `gov.nasa.worldwind.*` annotated `@Deprecated`, with file:line and one-sentence reason for deprecation, (b) every place those deprecated methods are still called internally, (c) the non-deprecated equivalent for each. Do not change any code. Save the report to `docs/agents/deprecation-inventory.md`.*

**De-risk angle:** zero code change, full visibility into latent debt. The report becomes the input to a follow-up Devin session that performs the actual swaps under the per-PR audit rules in `AGENTS.md` §6.

---

## Prompt 3 — Surface OpenGL state-handling violations (rendering safety)

> *Find every method in `gov.nasa.worldwind.render/` and `gov.nasa.worldwind.layers/` that calls into a GL context but does not wrap the GL state changes in a `try`/`finally` with `gov.nasa.worldwind.util.OpenGLStackHandler`. For each finding, give file:line, the GL call site, and the smallest correct wrapping. Save the report to `docs/agents/opengl-state-audit.md`. Do not change code.*

**De-risk angle:** WorldWind's existing rule (from `Design and Coding Guidelines.html`) is "OpenGL state changes must be bracketed." Cascade audits the whole repo for compliance in under a minute. Fixes are reviewed and merged one at a time under §6 — never in bulk.

---

## Prompt 4 — Find offline-mode contract violations (operational safety)

> *Find every network resource access in this repo that does not first check `WorldWind.getOfflineMode()` (or the equivalent contract on `WorldWindow`). For each, give file:line, the URL or resource being accessed, and the minimal correct check to add. Save the report to `docs/agents/offline-mode-audit.md`. Do not change code.*

**De-risk angle:** offline-mode is a contract the rest of the codebase depends on. Cascade verifies every call site honors it. Violations get fixed one PR at a time, with the audit trail that `AGENTS.md` §6 requires.

---

## Prompt 5 — Threading-aware modernization scan (Java 21 prep)

> *Find every anonymous `Runnable` or `Callable` in `gov.nasa.worldwind.*`. Categorize each as: (A) safely convertible to a lambda — pure body, no `this` binding, no non-final outer captures, (B) convertible with care — review threading semantics first, (C) leave alone — touches `TaskManager`, `Retriever`, or other concurrency primitives where the anonymous-class identity matters. Produce a markdown report at `docs/agents/runnable-conversion-plan.md` with the category and reasoning for each. Do not change any code.*

**De-risk angle:** the >90% lambda conversion target in `AGENTS.md` §9 only counts when each conversion is safe. Cascade produces the categorization; humans approve the (A) set, debate the (B) set, and leave (C) alone. The plan is itself the audit artifact.

---

## Prompt 6 — Add an example, the right way (operator-facing extension)

> *Add a new example under `gov.nasa.worldwindx.examples.symbology.OPIRAndMissileTrack` that renders one `OPIRSensorFieldOfRegard` and one `MissileTrackCorridor` over the Korean peninsula. Use `ApplicationTemplate` as the base, `RenderableLayer` for the graphics, and follow the conventions in the existing `TacticalGraphics.java` example. No GUI builders. No external dependencies. Add a brief Javadoc header describing what the example shows.*

**De-risk angle:** examples are how operators learn the API. Cascade follows the existing example conventions instead of inventing new ones. The new operator-facing artifact passes the same NASA conventions every other example follows.

---

## How this de-risks the work

- **Read more, write less.** Half the prompts above (2, 3, 4, 5) produce a report, not a diff. The engineer sees the risk surface before any code changes.
- **Every change is reviewed.** Cascade writes inline; the engineer accepts chunk-by-chunk. Then a PR. Then Devin Review. Then a human reviewer. Four gates.
- **Existing rules win.** `AGENTS.md` defers to NASA's `CONTRIBUTING.md` and `Design and Coding Guidelines.html` on every conflict. Cascade enforces both.
- **Speed is reinvested into testing, audit, and review** — never traded against them. That's the operating premise.

For the autonomous-execution side of any of these prompts (geometry math, full Java 21 modernization across all 20 packages, multi-PR rollups), hand the same prompt to Devin with the audit chain `AGENTS.md` §6 requires.
