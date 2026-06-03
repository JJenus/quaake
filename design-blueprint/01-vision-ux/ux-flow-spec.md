# UX Flow — Screen-by-Screen Specification

**Status:** Design spec (v1) · **Audience:** designers, developers, and AI agents building the interface.
**Companion docs:** *Data Source Matrix*, *Fit Score Normalization Spec*.

This document describes the full user journey screen by screen: purpose, key elements, the **calm** design choice, the **gentle-gamified** moment, and the loading/empty/missing-data states for each.

---

## 0. Design principles (apply to every screen)

1. **Calm = the visual layer.** Soft palette, generous whitespace, one primary action per screen, smooth transitions, no red-alert urgency. Reference feel: Headspace, not a trading app.
2. **Gamified = discovery, not pressure.** Reward *understanding* (scores revealing, insights unlocking, profile completing) — never urgency (no countdowns, "3 people viewing," scarcity nudges).
3. **The score explains itself.** Every number can be tapped to reveal *why*. No black boxes.
4. **Honesty over false precision.** Missing or coarse data is shown as lower confidence, never hidden or faked.
5. **One concept per screen.** If a screen needs two ideas, it's probably two screens.

### Top-level flow

```
   ┌─────────────┐
   │  Onboarding │  → sets priorities + weights + deal-breakers
   │ (Priorities)│
   └──────┬──────┘
          │
          ▼
   ┌─────────────┐      ┌──────────────────┐
   │  Discovery  │◄────►│  Property Profile │  → animated Fit Score, radar, "why", layers
   │ (Map/List)  │      └────────┬─────────┘
   └──────┬──────┘               │
          │            save / select-to-compare
          ▼                      ▼
   ┌─────────────┐      ┌──────────────────┐
   │   Saved /   │─────►│   Comparison     │  → 2–4 side by side, overlaid radars
   │   Profile   │      └──────────────────┘
   └─────────────┘
   (adjust priorities here → all scores re-compute live)
```

---

## 1. Onboarding — Priorities Setup

The most important flow: it produces the **weights** that personalize every score. Keep it short (under ~90 seconds) and feel like self-discovery, not a form.

### 1a. Welcome
- **Purpose:** set tone, state the value in one line.
- **Elements:** soft full-screen visual, one headline ("Don't just find a house — understand the life around it"), single **Begin** button.
- **Calm:** no sign-up wall yet; nothing to read but one sentence.
- **Gamified:** subtle entrance animation; a sense of a journey starting.

### 1b. Intent & location
- **Purpose:** capture where, buy/rent, and budget.
- **Elements:** location search (autocomplete), buy/rent toggle, budget input or range slider.
- **Calm:** three fields, one per visual block, progressive.
- **State — no location data:** allow a broad region; warn gently that detail varies by area.

### 1c. Choose what matters
- **Purpose:** select the dimensions to score on.
- **Elements:** tappable cards — Flood safety, Schools, Worship center, Affordability, Air quality, Safety, Markets, Hospital access, Transit, Parks. Tap to add to "what matters."
- **Calm:** cards, not checkboxes; pick as few or many as you like.
- **Gamified:** each selected card animates into a growing "your profile" tray at the bottom.

### 1d. Weight them
- **Purpose:** turn selections into weights (sum to 1, handled internally).
- **Elements:** drag-to-rank list *or* a small set of sliders ("How much does each matter?").
- **Calm:** relative ranking, not numeric percentages shown to the user.
- **Gamified:** as the user reorders, a live mini-radar preview reshapes — instant feedback that this is *their* shape.

### 1e. Deal-breakers (hard filters)
- **Purpose:** capture must-haves that *filter*, not just weight (e.g. "under budget," "hospital within 15 km").
- **Elements:** optional toggles with thresholds.
- **Note:** maps directly to §10 of the normalization spec — these remove properties before scoring.

### 1f. Profile ready
- **Purpose:** confirm and reward completion.
- **Elements:** "Your home-fit profile is ready" + a clean summary radar of their priorities + **See matches**.
- **Gamified:** the profile radar draws itself in; a quiet sense of completion (no points, no confetti — calm reward).

---

## 2. Discovery — Map / List

Where users browse scored properties.

- **Purpose:** present matches ranked by *their* Fit Score.
- **Elements:**
  - Map/List toggle (map with score-colored pins; list with cards).
  - **Property card:** photo, area + price band, **Fit Score badge** (animates/counts up as the card enters view), and 2–3 plain-language top reasons ("Low flood risk · School 8 min away").
  - Sort (default: Fit Score) and active deal-breaker filters shown as removable chips.
- **Calm:** soft pin colors (not alarm-red); cards breathe; lazy-loaded so nothing feels heavy.
- **Gamified:** scores count up on scroll-in — the small repeated "reveal" that makes browsing feel alive.
- **States:**
  - *Loading:* skeleton cards, score badge shows a gentle pulse.
  - *Empty (filters too tight):* suggest relaxing a specific deal-breaker, with the count it would unlock.
  - *Low data area:* badge shows confidence ("72 · partial data") rather than hiding the property.

---

## 3. Property Profile — the core experience

Where the product's value lands. Structured as the **five layers** from the concept, with the Fit Score on top.

### 3a. Hero
- **Elements:** photo carousel, name/area, price band, and the **large Fit Score** that **counts up + the radar draws in** on entry.
- **Calm:** one hero, lots of air, score not screaming.
- **Gamified:** the score animation is *the* signature moment — earned understanding, revealed.

### 3b. Neighborhood shape (radar/spider chart)
- **Purpose:** instant recognizable profile — spiky = polarizing, round = balanced.
- **Elements:** radar of all sub-scores; tap a spoke to jump to that layer.
- **Gamified:** each property has a memorable "shape" the user starts to read at a glance.

### 3c. "Why this score" breakdown
- **Purpose:** explainability.
- **Elements:** friendly contribution list — top boosters and top drags in plain language ("Scores high because flooding is rare and a mosque is 4 min away; held back by price").
- **Calm:** prose, not a raw data table.

### 3d. Layered detail (expandable sections)
Each layer shows its **sub-score**, a one-line plain-language reading, **confidence/source level**, and a relevant micro-visual:
1. **Risk & environment** — flood (with "floods ~1 in 10 years" framing), other hazards, air quality, elevation. Confidence and data tier shown honestly.
2. **Daily life & proximity** — mini-map with amenities pinned; per-type walk/drive times.
3. **Livability** — safety (with its geographic level labelled, e.g. "city-level"), noise, walkability, commute to a pinned place.
4. **Cost** — price vs. budget, vs. local market band.
5. **Compare hook** — "Add to comparison" lives here and in the action bar.

### 3e. Action bar (persistent)
- **Save**, **Compare (select)**, **Share**.
- **Calm:** three actions, no upsell clutter.
- **States:** missing-data layers render as "Not enough data here yet" with reduced confidence — never blank or faked.

---

## 4. Comparison View

The "select and compare multiple properties" feature.

- **Purpose:** compare 2–4 saved/selected properties across all layers.
- **Elements:**
  - Header row: each property's photo, price band, **Fit Score**.
  - **Overlaid radar charts** (semi-transparent, one color each) — see where shapes diverge.
  - **Sub-score matrix:** rows = layers, columns = properties, cells color-coded so the per-layer winner is obvious at a glance.
  - **"Best for you" summary:** highlights the trade-off in words ("A is safer from floods; B is cheaper and closer to schools").
- **Calm:** progressive disclosure — start with radars + headline scores; expand the full matrix only on demand so it never overwhelms on a phone.
- **Gamified:** the overlaid radars make differences *visible and satisfying* to spot.
- **States:**
  - *< 2 selected:* prompt to add another, with quick picks from saved.
  - *Uneven data:* greyed cells with confidence notes so comparisons stay honest.

---

## 5. Saved / Profile / Settings

- **Saved properties:** grid of cards with current Fit Scores; quick-select into Comparison.
- **Adjust priorities:** re-open the weighting screen; **all scores recompute live** — a powerful, gently gamified "watch the world re-rank to you" moment.
- **Alerts (later phase):** notify on new matches above a Fit Score threshold — framed as discovery, not urgency.
- **Account:** sign-in deferred until the user wants to save (no early wall).

---

## 6. Cross-screen behaviors

- **Score animation:** consistent count-up + radar-draw everywhere a Fit Score first appears.
- **Confidence:** always paired with the score (`"79 · based on 4 of 5 factors"`), per §11 of the normalization spec.
- **Transitions:** shared-element transition on the Fit Score badge from card → profile hero (it "grows" into place) — reinforces continuity and calm.
- **Color language:** a soft sequential scale for scores (no harsh red/green); reserve any stronger tone only for genuine hazard flags, used sparingly.
- **Performance:** sub-scores precomputed server-side (PostGIS); weights applied at request time so every screen feels instant — essential to the calm feel.

---

## 7. Phase mapping (build order)

- **Phase 1 (MVP):** Onboarding (1a–1f) → Discovery list → Property Profile with 2–3 well-sourced layers (flood + proximity + cost) → working animated Fit Score.
- **Phase 2:** Comparison view, full five layers, radar charts, map view.
- **Phase 3:** Saved, live re-weighting, alerts, accounts, richer micro-interactions.
