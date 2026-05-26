# AGENTS.md — Operating rules for agents working in this fork

This file governs **any AI agent** (Devin, Cascade in Windsurf, or otherwise) that touches code in this repository. It carries forward NASA's existing `CONTRIBUTING.md` and `Design and Coding Guidelines.html` rules, and adds the anti-risk operating model required for federal mission-critical baselines.

If a rule here ever appears to conflict with `CONTRIBUTING.md` or `Design and Coding Guidelines.html`, **NASA's existing rule wins** — this file is additive, never subtractive.

---

## 1. Operating posture — non-negotiable

This is an **anti-risk operating model**, not a productivity push. Every speed gain is reinvested into testing, review, traceability, and human control.

- You are working on a **Cognition-side fork** of `COG-GTM/WorldWindJava`. **Never push to upstream NASA branches.**
- Every change goes through a PR. No direct commits to `develop` or `main`.
- Devin Review runs on every change before the PR is opened to a human reviewer.
- The existing JUnit suite (`ant -f release-build.xml`) must stay green on every PR.
- **Do not modify a test to make it pass.** A red test means the production change is wrong, not the test.
- **Public API contract is preserved.** No changes to class signatures, method signatures, public field declarations, or package visibility in `gov.nasa.worldwind.*`. Downstream consumers cannot break.
- When in doubt, prefer the **smaller, more conservative change**.

---

## 2. The build is Ant. Stay there.

- The build entrypoint is `ant -f release-build.xml`.
- The JDK target lives in `release-build.properties` as `worldwind.jdk=<version>`. To retarget, change that one line — do not edit `release-build.macros.xml` or `release-build.xml` directly.
- **Do not migrate to Gradle or Maven.** Too risky for this audience.
- **Do not introduce JPMS module declarations (`module-info.java`).** Same risk argument.
- **Apache NetBeans config (`nbproject/`) is checked in.** Do not delete or reformat it.
- **No external dependencies beyond JOGL.** Only standard JRE + JOGL. If you think you need another library, you don't — write it yourself or stop.

---

## 3. JDK 21 modernization — what to change, what to leave alone

When modernizing Java 8 / Java 11 patterns to Java 21 idioms:

**Convert when safe:**
- Anonymous inner classes implementing single-abstract-method interfaces (`Runnable`, `Callable`, `ActionListener`, `MouseListener`, `KeyListener`, `ChangeListener`, `PropertyChangeListener`, `WindowListener`) → lambdas / method references. **Flag (don't auto-convert)** any case that captures a non-final outer reference, relies on `this` binding inside the anonymous class, or touches non-obvious threading semantics.
- `if (x instanceof Foo) { Foo f = (Foo) x; ... }` → pattern matching for `instanceof`.
- Chained `if/else if` on type or value → switch expressions / pattern matching for `switch` where readability improves.
- Plain-data classes (private final fields + constructor + getters, no behavior) → `record`s. **But** verify NASA's "no-arg constructor for declarative instantiation" rule still holds for any class loaded by `gov.nasa.worldwind.Configuration`.
- Closed, exhaustively-known interface hierarchies → `sealed` + `permits`. Do not seal anything a downstream consumer might legitimately extend.
- Deprecated API *usage* (not the deprecated declarations themselves) — `new Integer(int)` → `Integer.valueOf(int)`, `java.util.Date` arithmetic → `java.time`, etc.
- Multi-line string literals → text blocks where readability benefits.
- Short single-shot file I/O → `Files.readString` / `Files.writeString`.

**Do not touch:**
- The JOGL / OpenGL native binding layer (`com.jogamp.*` adjacent code). Compatibility with native bindings outweighs idiom.
- Public deprecated API **declarations**. You may stop using them internally; the symbols stay on the surface.
- Drive-by reformatting, import reordering, or cosmetic-only changes outside lines you're actively modifying.

---

## 4. NASA conventions you must respect (carried forward from `CONTRIBUTING.md`)

These are existing repo rules. They take precedence over anything else.

- **Most major classes need a no-argument constructor.** The declarative instantiation mechanism (`Configuration`, factory loaders) relies on this. **Before converting any such class to a `record` or changing its constructor signature, verify it is not loaded by name.**
- **WorldWind never crashes.** Always catch exceptions at the highest entry point from the runtime (UI listeners, thread `run()` methods).
- **Within a rendering pass, do not touch disk or network.** Use `TaskManager` and `Retriever` to fork off threads.
- **OpenGL state must be protected** with `try/finally` and `gov.nasa.worldwind.util.OpenGLStackHandler`.
- **JOGL buffers** must have `nativeByteOrder()` set. Use `com.jogamp.common.nio.Buffers`.
- **Offline mode** (`WorldWind.setOfflineMode`) must be checked before any network resource access.
- **Log all exceptional conditions before rethrowing.** Use `java.util.logging` only. Wrap nothing — class/method capture relies on direct call sites.
- **Use i18n via `MessageStrings.properties`** for all exception and log messages. `Logging.getMessage("packageOfClass.className.nameOfString")`.
- **Concurrency:** use `java.util.concurrent` collections + blocking queues. Atomic for simple variables only.
- **No GUI builders.** Examples and applications stay hand-written.

---

## 5. MIL-STD-2525 symbology — domain rules

When extending `gov.nasa.worldwind.symbology.milstd2525/`:

- The entry point is **`MilStd2525GraphicFactory`** (implements `TacticalGraphicFactory`).
- New tactical graphics extend **`AbstractMilStd2525TacticalGraphic`**. Closest existing patterns to study before adding: `TacticalCircle`, `TacticalQuad`, `TacticalRoute`, `MilStd2525PointGraphic`.
- Register new graphic types in `MilStd2525GraphicFactory` so they round-trip through the factory by SIDC (Symbol Identification Code) or equivalent type key.
- Constants are namespaced Strings (per NASA convention) — extend `SymbologyConstants` / `MilStd2525Constants`, do not introduce loose enums.
- Pick/select behavior must work: confirm the new graphic responds to `WorldWindow` picking.
- Add an example to `gov.nasa.worldwindx.examples.symbology/` for every new graphic. The example uses `ApplicationTemplate` and `RenderableLayer`. No GUI builders.
- Modifier handling routes through `MilStd2525ModifierRetriever` / `MilStd2525Util`. Do not invent parallel modifier paths.

---

## 6. Per-PR requirements (this is the audit trail)

Every PR description **must** contain:

1. **Prompt block.** The exact prompt that generated the change (so the work is reproducible from text).
2. **Session URL.** Devin session URL or equivalent agent session reference.
3. **Devin Review verdict.** Pass / pass-with-notes / blocked, plus any unresolved findings called out explicitly.
4. **Plain-English rationale.** 2–3 sentences describing what changed and why, written for a reviewer who has not read the code.
5. **Metrics block.** Before/after counts for the package, where applicable:
   - Anonymous-inner-class conversions
   - `instanceof` → pattern-match conversions
   - Records introduced
   - Sealed hierarchies introduced
   - Deprecated-API usage removals
   - Text-block conversions
6. **Public API delta.** Either "No public API surface changes" or an explicit list of each delta with justification.

PRs without all six blocks should be rejected on style alone.

---

## 7. Branching

- Branch naming: `devin/<topic>-<package>` (e.g. `devin/java21-symbology`, `devin/symbology-opir-cone`).
- PRs open against this fork's `develop` branch — **not** upstream NASA `develop`.
- One PR per package for modernization work; one PR per feature for capability extension work. **No mega-PRs.**

---

## 8. Build & test gates

Before opening any PR:

```
ant -f release-build.xml         # green build under the configured JDK
ant -f release-build.xml test    # full JUnit suite green
```

If `test` target name differs, follow `release-build.xml` for the canonical target list.

---

## 9. Completeness gate for multi-package work

When the work spans the whole repo (e.g., a full JDK migration), the agent must run and include in its final summary:

```
find src -name "*.java" | wc -l                                  # expected: 1727
grep -rn "new Runnable()" --include="*.java" src/ | wc -l        # target: <= 15 (>90% of 148)
grep -rn "new ActionListener()" --include="*.java" src/ | wc -l  # target: <= 18 (>90% of 185)
```

Every one of the 20 top-level packages (`ogc`, `worldwindx.examples`, `formats`, `util`, `worldwindx.applications`, `render`, `symbology`, root `gov.nasa.worldwind`, `layers`, `data`, `geom`, `view`, `globes`, `terrain`, `event`, `cache`, `retrieve`, `animation`, `awt`, `wms`) must be accounted for — either with a modernization PR or with a written `NO-CHANGE` justification. No silent skips.

A `MODERNIZATION-ROLLUP.md` at the repo root collects: every PR URL, the package it covers, aggregate metric deltas, CI status, the `NO-CHANGE` justifications, and the completeness-gate output verbatim.

---

## 10. What "done" means

An agent declares done **only** when:

- All PRs are opened (not merged — merging is reserved for the human reviewer).
- Every PR carries the six required description blocks (§6).
- The completeness gate (§9) was run and its output is in the rollup.
- All 20 packages are accounted for.
- Green CI on every open PR.

If any one of these fails, the work is not done — keep going.
