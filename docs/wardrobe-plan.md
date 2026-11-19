# Metro's wardrobe: plan (not built)

Drafted 2026-11-19 on `v7.0`, after the sound stage work. The idea is the dev's: let players
change pieces of Metro's clothing (colours, stripes, patterns), as a new line of things to earn
and a new thing to sell. This file is the plan to agree on before any code. Nothing here is
built yet.

## Why it fits, and the case for it

- **Item data shows a ceiling, not a backlog.** From 6 Oct to 18 Nov, 394 of 988 active users
  unlocked at least one item. Beyond the first week, though, unlocks thin out fast: Glissie (6 h) was
  earned by about 0.2% of users. Items are scenery and props. A player who has them all has
  nothing left to *choose*. A wardrobe adds choice and self-expression on top of what is earned.
- **It sells with the app's no-subscription promise.** Outfit packs are one-time purchases, the
  same kind of product as the sounds and Glissie.
- **It shows on the screen people see most.** Metro fills the Home screen, so an outfit is seen
  on every visit.

## How Metro is drawn today (the hook)

`ui/components/GnomeCanvas.kt` shades every garment from a three-stop colour ramp, driven by the
one key light (*Metro's lighting model* in CLAUDE.md):

| Garment | Ramp function | Colours (`GnomeColors`) |
|---|---|---|
| Hat | `coneFeltColor` | `hatRedLight` / `hatRed` / `hatRedDark` (+ `hatShadow`) |
| Jacket | `clothRollColor` | `jacketLight` / `jacket` / `jacketDark` |
| Trousers | `pantsRollColor` | `pantsHighlight` / `pants` |
| Shoes | `shoeRollColor` | `shoeLight` / `shoe` / `shoeDark` |
| Bow tie | (tie ramp) | `tieLight` / `tie` / `tieDark` |

The jacket's pinstripes are already drawn inside a clip of the jacket's own shape
(`drawBody`, `jacketClip`). That is exactly how a pattern has to be drawn, so patterns have a
proven path.

So a wardrobe piece is **a palette swap plus an optional pattern**. It needs no new drawing of
Metro's shapes, and the hard-won shading rules stay in force because the ramps that carry them
are untouched. Only their inputs change.

## Proposed design

### Data
- `WardrobeSlot`: HAT, JACKET, TROUSERS, SHOES, BOW_TIE (glasses frames later, maybe).
- `Swatch(base: Color)`: derives the light, dark and shadow stops from one base colour by fixed
  lightness and saturation offsets, so every colour shades the way the default does.
  - A unit test proves the derivation reproduces today's `GnomeColors` triples within a small
    tolerance. That keeps the default outfit pixel-identical, which matters because the
    launcher icon must match the in-app Metro.
- `Pattern`: NONE, PINSTRIPE (today's jacket), STRIPES, DOTS, CHECK, STARS. Each is drawn inside
  the garment's clip, then multiplied by the same roll shading as the fill, so a stripe darkens
  into the shadow side like the cloth under it. A flat stripe on a lit garment would break the
  one-light rule.
- `WardrobePiece(id, slot, swatch, pattern, unlock)`. `unlock` is either an existing
  `UnlockCondition` (earned) or a product id (sold), mirroring `MetroItemEntry` and
  `PurchasableItemDef`.
- `WARDROBE_REGISTRY`: one list, like `METRO_ITEM_REGISTRY` and `CLICK_SOUNDS`.
- `MetroOutfit`: the chosen piece per slot, persisted in a new `metro_wardrobe` prefs file and
  passed into `GnomeCanvas` / `drawGnome`, whose ramp functions read it in place of the constants.

### Rendering rules to carry over (from CLAUDE.md and the art memories)
- One object gets one gradient. A patterned jacket is still one roll.
- Any pattern is clipped to its garment's existing shape. Never draw a second outline.
- Colours must hold up on the night sky. A near-black jacket on the dark sky only works today
  because of its roll-off, so very dark swatches need checking.
- The cast shadows (brim on the face, moustache) do not change with colour. Check them on
  light hats, where a shadow shows most.

### Screen
- The wardrobe is a stage like the sound stage, built the same way: a stateless component plus
  a binding. The live Metro (`GnomeCanvas` with the trial outfit) sits centre stage, slot chips
  sit above, and a swatch carousel sits below.
  - `SoundStage`'s scene, spotlight and carousel pieces (`ui/scene/NightScene.kt`, the medallion
    pattern) are the parts to share. Extract them into a generic stage when the second user
    arrives, not before.
- Trying a piece on is free and instant; buying or equipping is the action. That is the sound
  stage's "hear it, then use or unlock".
- Entry point: from Metro's Collection (Rhythm page) as a second tab, and by long-pressing Metro on
  Home. A tap on Metro already opens item dialogs, so long-press does not collide. Decide with the
  dev; Home space is precious.

### Earning and selling (for the dev to decide)
- **Earned:** colour swaps on the existing conditions (for example, a gold bow tie at 7 loyalty
  days), so the earn track gains steps without new counters.
- **Sold:** themed packs as one-time products, for example "Jazz Club" (burgundy jacket, black
  pinstripe trousers, gold bow tie) or "Festival" (striped jacket, star-pattern hat).
  - Billing reuses `ITEM_PRODUCTS` and the item purchase flow.
  - The `iap_started` / `iap_result` events already cover them.
- **Copy rule:** never call Gnotes, items or outfits "rewards".

### Analytics (proposal; registered params only)
- `wardrobe_opened` (source)
- `outfit_changed` (slot as `type`, piece as `item_name`). Note `type` is not registered yet; it
  would need registering, or reuse `activity`.
- Purchases ride on `iap_*`.

## Phases
1. **Palette swaps for hat and jacket.** Swatch derivation + test, `MetroOutfit` plumbing, a
   simple picker. Proves the shading holds across colours.
2. **Patterns:** stripes and dots first, shaded through the roll.
3. **The wardrobe stage UI** and the remaining slots.
4. **Packs for sale**, after phase 1 to 3 data shows people change outfits at all.

## Open questions for the dev
- Should the launcher icon and store art stay on the default outfit? (Recommended: yes.)
- Is the outfit visible everywhere Metro appears (Home, the unlock popup, the avatar), or only on
  Home?
- Which slots matter most to players? The hat is the most visible and the most "Metro".
- Pricing per pack against the sounds' price.
