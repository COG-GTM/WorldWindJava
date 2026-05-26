# Windsurf demo — run-of-show

What this file is: the operator script for the Cascade-in-Windsurf portion of the Wednesday demo. Vignette 2 of the demo plan. Open this file in the IDE at the start of the vignette so the audience sees it sitting in the repo.

The scenario: an engineer at the program office wants to add a new MIL-STD-2525 tactical graphic to the operator console — an OPIR sensor field-of-regard cone for a space-based infrared sensor, and a missile-launch detection track corridor. He doesn't write WorldWind code every day. He opens Windsurf and asks Cascade.

Total target time on screen: **6–8 minutes**, then hand off to Devin.

---

## Setup before the demo starts

1. Windsurf open. Repo at `COG-GTM/WorldWindJava` (this fork), `develop` branch.
2. `AGENTS.md` open in a side tab so we can point at it when we say "the operating rules live in the repo, not in slideware."
3. `src/gov/nasa/worldwind/symbology/milstd2525/MilStd2525GraphicFactory.java` open in the main tab.
4. Cascade panel open and ready. Memory enabled so it picks up `AGENTS.md` automatically.

---

## Beat 1 — Comprehend the factory (~90 seconds)

**Type into Cascade:**

> *Explain how `MilStd2525GraphicFactory` decides which class to instantiate for a given symbol identifier. Walk me through the registration flow and point me at the closest existing graphic implementations to the one I want to build: a sensor field-of-regard cone.*

**What Cascade should produce:**

- A walkthrough of `MilStd2525GraphicFactory.createGraphic()` and the SIDC dispatch.
- A pointer to `AbstractMilStd2525TacticalGraphic` as the base class for new graphics.
- Closest existing analogs: `TacticalCircle` (parameterized geometry), `MilStd2525PointGraphic` (point-anchored placement), `TacticalRoute` (path-with-width pattern relevant to the track corridor).
- File:line references the engineer can click.

**What to say while it runs:**

> "Cascade just read 81 files in the `milstd2525` package and a few hundred more in `symbology`. The engineer didn't have to know the codebase. He pointed at the problem and asked. The audit trail of what Cascade looked at and why is visible right here — same chain of custody as a human PR review."

**If Cascade drifts:** point it back with: *"Stay inside `gov.nasa.worldwind.symbology.milstd2525/`. The factory is `MilStd2525GraphicFactory`. Start there."*

---

## Beat 2 — Scaffold the new graphic (~3 minutes)

**Type into Cascade:**

> *Scaffold a new tactical graphic `OPIRSensorFieldOfRegard` under `gov.nasa.worldwind.symbology.milstd2525.graphics.areas`. It extends `AbstractMilStd2525TacticalGraphic`. Inputs: sensor position (lat, lon, altitude), slant range, half-angle, azimuth, elevation. Render it as a cone projected onto the globe. Follow `TacticalCircle` and `TacticalQuad` as reference patterns. Honor the `AGENTS.md` rules: no-arg constructor for declarative instantiation, namespaced String constants (extend `SymbologyConstants` / `MilStd2525Constants`), JOGL buffer `nativeByteOrder()`, OpenGL state protected with `OpenGLStackHandler` and try/finally, `WorldWind.setOfflineMode` respected, no external dependencies. Register the new graphic in `MilStd2525GraphicFactory` so it round-trips by SIDC. Stop after the scaffold — do not implement the math yet.*

**What Cascade should produce:**

- A new file at `src/gov/nasa/worldwind/symbology/milstd2525/graphics/areas/OPIRSensorFieldOfRegard.java` extending `AbstractMilStd2525TacticalGraphic`.
- A no-arg constructor.
- Setters for the six geometric inputs.
- Override stubs for the render-side methods (geometry generation, attribute application, pick support) — stubs only, with TODOs marking the math.
- A modification to `MilStd2525GraphicFactory` adding the SIDC dispatch for the new type.
- A new namespaced constant in `MilStd2525Constants` for the SIDC.

**What to say while it runs:**

> "Watch the inline diff. Cascade is writing exactly what an engineer who knows this codebase would write. It's reading `AGENTS.md` and respecting the rules — no-arg constructor, namespaced constants, OpenGL state handler. The engineer reviews each chunk in the IDE before accepting. Human in the loop, all the way through."

**Acceptance check before accepting:**

- Does it have a no-arg constructor? (Required by NASA's declarative instantiation rule.)
- Is the SIDC constant added to `MilStd2525Constants`? (Required by the namespaced-constants rule.)
- Does it use `OpenGLStackHandler` in the render path? (Required by the OpenGL-state-protection rule.)
- Is the factory registration present?
- Are there `TODO` comments where the math is missing? (We want the scaffold honest about what's not done.)

If any are missing, ask Cascade: *"Re-check against `AGENTS.md` §5 and add what's missing."*

---

## Beat 3 — The hand-off to Devin (~90 seconds)

This is the moment the audience sees what Windsurf does *vs.* what Devin does.

**Say it out loud:**

> "The scaffold is good. The geometry math, the corridor, the example app, the unit tests, the 3D render screenshot — that's hours of work. The engineer doesn't sit and grind through it. He hands it to Devin."

**Type into Cascade:**

> *Open an Ask Devin session with this prompt: "Complete the OPIR sensor field-of-regard cone implementation that's currently scaffolded on this branch. Implement the cone geometry math, add a `MissileTrackCorridor` companion graphic with confidence-shaded tapered swath rendering, add an example app under `gov.nasa.worldwindx.examples.symbology.OPIRAndMissileTrack` that places one of each over the Korean peninsula, generate unit + functional tests, and run them. Follow `AGENTS.md` end to end. Open a PR. Include the standard six description blocks."*

**What the audience sees:**

- Cascade hands off cleanly. The session URL appears. Devin is now running on its own machine, in parallel, without blocking the engineer.
- The engineer goes back to his other work. The audit chain (Cascade prompt → Devin session → Devin Review verdict → PR → CI) is one continuous lineage.

---

## What "done" looks like for this vignette

- A new file `OPIRSensorFieldOfRegard.java` exists in `gov.nasa.worldwind.symbology.milstd2525.graphics.areas`, with no-arg constructor, setters, and scaffold methods.
- `MilStd2525GraphicFactory` and `MilStd2525Constants` are updated.
- An Ask Devin session is open with the completion prompt above.
- `AGENTS.md` was visibly the source of authority for every guardrail Cascade applied.

---

## Failure modes and what to say if they happen

| If this happens... | Say this and do this |
|---|---|
| Cascade ignores `AGENTS.md` | "Hold on — let me re-anchor it." Then type: *"Re-read `AGENTS.md` and apply §4 (NASA conventions) and §5 (MIL-STD-2525 domain rules) before continuing."* |
| Cascade adds an external dependency | "That's a `AGENTS.md` §2 violation. Cascade, remove the external dependency. Use only JRE + JOGL." |
| Cascade strips a no-arg constructor | "That breaks declarative instantiation. Cascade, restore the no-arg constructor — `AGENTS.md` §4." |
| Cascade writes a placeholder unit test that doesn't actually exercise anything | Skip it. Say: "We don't ship that. Tests come from Devin in the next step, not from the IDE scaffold." |
| The 3D globe doesn't render the new graphic when Devin's done | This is the Vignette 2 finale. If it fails on Wednesday, fall back to the pre-recorded screenshot stored at `docs/demo-assets/opir-cone-render.png` (we generate this in advance). Never demo a blank gray map. |

---

## Why this lands with this audience

- The engineer never wrote WorldWind code. The audience watches him ask plain-English questions and get a working scaffold in 5 minutes. That's the "I wish I had that yesterday" moment.
- `AGENTS.md` is visible, in-repo, and enforced. The customer sees the operating model is a real artifact, not slideware.
- The hand-off to Devin shows the two tools complement each other: Windsurf is where the engineer sits, Devin is what scales the engineer's intent. Same audit chain, same anti-risk gates.
- MIL-STD-2525 is the customer's daily symbology. The graphic we're adding (OPIR sensor FOR + missile track corridor) maps directly to their mission.

That's the whole vignette. After this, hand back to the Wednesday demo flow at step 4 (anti-risk recap).
