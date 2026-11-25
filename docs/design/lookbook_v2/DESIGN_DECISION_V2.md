# ADR-DESIGN-V2 — ShowMeOutfit `v2.0.0` & `v2.1.0` UX, Design & Scope Decisions

| Attribute | Value |
| :--- | :--- |
| **Date** | 2026-11-25 |
| **Status** | `APPROVED (Rescoped for Sprint 6 v2.0.0 Release)` |
| **Target Releases** | **Milestone 2 (`v2.0.0` — End of Sprint 6)** & **Milestone 2.1 (`v2.1.0` — Sprint 7)** |
| **Participants** | Product Owner (User), Principal Android Architect, Lead Mobile UX & Fashion Designer |

---

## 1. Context (Post-`v1.1.0` User Feedback & Release Rescope)
1. **Garment visuals:** *"Outfit icon too simple, not real and not attractive"* (`v1.1.0` shipped 5–7 path flat geometric XML vectors at `64.dp` without texture, depth, or women's fit silhouettes).
2. **Weather text density:** *"Should minimize weather text, replace by common icon like sunny, rainy, etc."* (`v1.1.0` rendered zero sky/metric icons and 4 redundant prose blocks).
3. **Cohesive outfit preview:** *"Nice to have: outfit together to see how it looks like"* (`v1.1.0` isolated layers in separate 2-column Bento cards and accessories in a separate list).
4. **Release Cadence Rescope:** Ship **`v2.0.0` at the end of Sprint 6** bundling the 3 UX/Lookbook upgrades **plus Virtual Closet Inventory** (catalog-backed Room DB `v2` wardrobe management); defer **On-Device Camera/WebP Media** and **Smart Closet Engine (`PersonalWardrobeEngine`)** to **`v2.1.0` (Sprint 7)**.

---

## 2. Approved Product & Design Decisions

| # | Decision Area | Approved Choice | Rationale |
| :-: | :--- | :--- | :--- |
| **D1** | **Release Split (`v2.0.0` vs `v2.1.0`)** | **`v2.0.0` (End of Sprint 6, 25 SP):** Realistic Lookbook + Icon-First Weather + `[Look ⇄ Grid]` + **Virtual Closet Inventory** (Room DB `v2`, catalog-backed CRUD, category filters, `EN`/`VI`/`FR`).<br>**`v2.1.0` (Sprint 7, 20 SP):** On-Device Camera/Gallery WebP Media + **Smart Closet Compatibility Engine** (`PersonalWardrobeEngine`). | Delivers a major `v2.0.0` milestone in a single sprint where users immediately get realistic visuals, the Outfit Lookbook, and a working Virtual Closet to catalog their clothes—while isolating camera/file I/O and custom matching algorithms into `v2.1.0`. |
| **D2** | **Garment & Accessory Art Direction** | **Style A — Photorealistic Studio Flat-Lay Cutouts** (`512×512` WebP, `45` assets incl. `8` women's fit cuts, `~750 KB` APK delta) (`mockup_style_a_photorealistic_selected.jpg`) | Solves *"not real"* feedback immediately, powers preset silhouettes inside `v2.0.0` Virtual Closet Inventory, and ensures zero visual clash when `v2.1.0` adds real user clothing photos. |
| **D3** | **Minimal Icon-First Weather UX** | **Unified Top Weather Capsule (`~72dp`)** (`13`-vector duotone pack `< 12 KB`) | Consolidates `[📍 City ▾]`, sky icon, current/min–max temp, and icon-only peak chips (`💧`, `☀️`, `💨`) into one top bar; unfolds `DiurnalTimelineBar` inline on tap; deletes prose summary and bottom drawer. |
| **D4** | **"Outfit Together" Composition** | **Flat-Lay Style Board Hero + `[✨ Look ⇄ ⊞ Grid]` Switcher** | Composes Base, Mid/Outer, Bottoms, Footwear, and Accessories together on a theme-safe studio mat (`#FAF8F5` Light / `#2B2930` + `7%` radial spotlight Dark) with tappable item/trigger chips below and `fontScale >= 1.5f` auto-fallback to 1-column Grid. |

---

## 3. Visual Reference Artifacts
- **Selected Direction (Style A — Note TopAppBar Hanger Icon for `v2.0.0` Virtual Closet):** [`mockup_style_a_photorealistic_selected.jpg`](mockup_style_a_photorealistic_selected.jpg)
- **Evaluated Alternative (Style B):** [`mockup_style_b_editorial_vector_reference.jpg`](mockup_style_b_editorial_vector_reference.jpg)
