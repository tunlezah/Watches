# Design

Watches ships three complete designs. Each one is a full visual system rather than a colour swap, with its own type, colour, shape, rule language, ornament and motion. You choose between them in **Settings › Appearance**, where each design is shown as a live specimen drawn in its own style.

All three share the same screens, components and accessibility floor:
- touch targets of at least 48 dp;
- text of at least 4.5:1 against its background;
- control edges of about 3:1;
- nothing smaller than 11 sp.

`DesignContrastTest` checks the colour rules for every design, mode and accent.

In code, a design is a `DirectionStyle` (layout and ornament), a `WatchTypography` (type roles) and a `WatchColors` palette, all in `shared/ui/.../theme`. Components read these semantic tokens and never ask which design is active. To change a design, edit its entries in `styleFor`, `typographyFor` and `Palettes`.

---

## Atelier: the catalogue

**Idea.** A fine-watch auction catalogue, in the manner of Phillips or Christie's. Each watch is a lot, photographed on a warm plate and described in a calm editorial voice. Nothing shouts.

| | |
| --- | --- |
| Ground | Warm graphite `#121110` at night, ivory paper `#F5F1E8` by day |
| Ink | Bone `#EDE7DC` on graphite, near-black `#1B1915` on ivory |
| House accent | Champagne. Also suggested: Bordeaux, Racing green, Sunburst blue |
| Display | Cormorant Garamond Medium, lining figures: screen titles, watch names, section heads |
| Voice | Cormorant Italic: subtitles, counts, catalogue numbers |
| Text and data | Instrument Sans, tabular figures |
| Labels | Small capitals, 11 sp, +16% tracking |
| Buttons | Tracked capitals in Instrument Sans Medium |
| Shape | Square corners throughout. No pills. |

**Signature details**
- **Masthead rule.** A thick–thin double rule (1.5 dp and 0.75 dp, 2.5 dp apart) under each top-level title, as in a printed catalogue.
- **Accent dash.** A short champagne dash (20 × 1.5 dp) marks the active tab, the active navigation item and every spec section heading.
- **Catalogue numbers.** Each tile carries "No. 7" in italic, opposite the maker's name.
- **Plates.** Photos sit on a warm vignette in a slightly tall frame (0.82:1), like a catalogue plate.
- **Hairlines.** Hairlines divide, and panels are only a faint change of paper. Secondary and tonal buttons are a hairline frame, never a tinted block.

**Motion.** Slow and quiet. Fades, a very slight press, no bounce.

---

## Instrument: the data sheet

**Idea.** A tool watch's technical data sheet, and the instruments it's designed to sit beside, such as a cockpit panel or a dive computer. Everything is measured, numbered and aligned.

| | |
| --- | --- |
| Ground | True black `#000000` for OLED; drafting white `#F3F4F5` with white panels by day |
| Ink | `#F2F4F5` on black, `#0B0C0D` on white |
| House accent | Lume. Also suggested: Tropical amber (old radium), Ice blue, Bordeaux (signal red) |
| Display | Archivo Expanded Bold and ExtraBold, in capitals |
| Labels, references, counts, buttons | IBM Plex Mono, in capitals |
| Values and text | IBM Plex Sans, tabular figures |
| Shape | 2 dp corners, 1 dp frames |

**Signature details**
- **Minute track.** A scale of tick marks under each top-level title: one every 6 dp, every fifth taller. The first major tick is a lume pip, like the marker at twelve on a bezel.
- **Numbering.** Everything is zero-padded and counted: tiles `01`, sections `01 CASE … 08 FIELDS`, totals `008 WATCHES`.
- **Dotted leaders.** These join every spec label to its value, as on a printed data sheet.
- **Registration marks.** Corner ticks frame the hero photo, like a viewfinder.
- **Status LEDs.** Status is a small square light, not a dot.
- **Frames, not fills.** Secondary and tonal buttons are a 1 dp frame. Only the main action is a solid lume block.

**Motion.** Quick, linear and precise. Short tweens, no springs.

---

## Gallery: the white room

**Idea.** A modern gallery. Each watch stands on its own soft pedestal, labelled like a wall card, with light, air and calm around it. The references are Scandinavian design and Bauhaus restraint.

| | |
| --- | --- |
| Ground | Nordic daylight `#F4F6F9` with white cards; midnight `#0D1117` at night |
| Ink | `#0E131A` by day, `#EEF2F7` at night |
| House accent | Sunburst blue. Also suggested: Ice blue, Racing green, Bordeaux |
| Type | Manrope throughout: Light at display sizes with −3% tracking, SemiBold for card titles, Regular for text, tabular figures |
| Case | Sentence case everywhere. No capitals, no tracking. |
| Shape | Generous radii: cards 22 dp, photo pedestals 16 dp, the hero 28 dp. Chips and buttons are pills. |

**Signature details**
- **Depth.** Cards and pedestals float on a wide, very soft shadow in daylight, and on tonal steps at night. There are no rules.
- **Wall-card captions.** Title, then "Maker · Reference", then size and movement in a quieter grey.
- **Floating navigation.** A pill bar that hovers above the content.
- **Whitespace as ornament.** No other decoration.

**Motion.** Soft. A gentle spring on press, and smooth fades.

---

## Accents

There are seven accents: Champagne, Lume, Ice blue, Racing green, Bordeaux, Sunburst blue and Tropical amber. Each design suggests four that suit it, with its house colour first. The other three are a tap away under "More colours".

Switching design switches to that design's house accent. Every accent has a dark and a light value, and each value reaches 4.5:1 against every design's ground.
