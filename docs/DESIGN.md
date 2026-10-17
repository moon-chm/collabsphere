---
name: CollabSphere Print
colors:
  paper: '#FBF5EA'
  ink: '#1E2A5A'
  white: '#FFFFFF'
  leaf: '#8CC63F'
  leaf-dark: '#3F7D20'
  coral: '#F28B6C'
  riso-red: '#E2432F'
  red-dark: '#B8321F'
  sky: '#5BC0EB'
  sun: '#F6D365'
typography:
  hero:
    fontFamily: Fraunces
    fontSize: 64px
    fontWeight: '700'
    lineHeight: 58px
    letterSpacing: -0.02em
  h1:
    fontFamily: Fraunces
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 38px
    letterSpacing: -0.02em
  h2:
    fontFamily: Fraunces
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 32px
  h3:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 24px
  body:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '700'
    lineHeight: 20px
    letterSpacing: 0.01em
  caption:
    fontFamily: Manrope
    fontSize: 13px
    fontWeight: '500'
    lineHeight: 18px
  tiny:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.08em
rounded:
  small: 12px
  card: 24px
  sheet: 28px
  full: 9999px
tokens:
  outline: 2dp
  outline-dense: 1.5dp
  shadow-offset: 4dp
  button-height: 56dp
  touch-target: 48dp
  side-padding: 20dp
---

## Brand & Style

CollabSphere uses a retro risograph, mid-century flat print look. Every screen should read like a two-colour print on warm paper: navy ink outlines, flat fills, and hard offset shadows. It never uses gradients, blur or soft drop shadows. Organic blobs, halftone dots, paper grain and a slightly misregistered coral headline give it warmth and a hand-made feel.

The source of truth is `ui/theme/PrintStyle.kt`. Shared print helpers for older call sites live in `ui/theme/SkeuomorphicModifiers.kt`. Dialogs are in `view/components/WorkspaceDialog.kt`, and chat surfaces are in `view/components/ChatComposerKit.kt`.

Dark mode keeps the earlier dark theme. The print style applies to light mode only.

## Colors

| Token | Hex | Use |
|---|---|---|
| `PrintPaper` | `#FBF5EA` | Screen background, sheets, inputs, quiet fills |
| `PrintInk` | `#1E2A5A` | All text, icons, outlines and shadows; solid primary buttons; own chat bubbles |
| `PrintWhite` | `#FFFFFF` | Raised cards and rows sitting on paper |
| `PrintLeaf` | `#8CC63F` | Positive and done states, tasks, channel markers |
| `PrintLeafDark` | `#3F7D20` | Success text and icons on light fills |
| `PrintCoral` | `#F28B6C` | Brand accent, misregistration layer, mentions, unread badges, send button |
| `PrintRisoRed` | `#E2432F` | Destructive fills |
| `PrintRedDark` | `#B8321F` | Destructive and error text and icons |
| `PrintSky` | `#5BC0EB` | DMs, information, secondary accent |
| Sun | `#F6D365` | Levels, badges, sticky notes |

Rules:
- Put ink text on bright fills (leaf, coral, sky, sun). Never put white text on them.
- Use paper text only on ink fills.
- Map legacy accents with `printAccentFor(color)` and icon tints with `printIconTint(color)`.
- Priority uses HIGH → coral, MEDIUM → sky, LOW → leaf.

## Typography

- **Display (`Hero`, `H1`, `H2`):** Fraunces Bold with tight tracking. Use these for screen titles and onboarding/auth headlines. Major titles use `MisregisteredTitle`, which draws an ink layer over a coral layer offset by a couple of dp. The two layers settle into place with a spring on entrance.
- **UI (`H3`, `Body`, `Label`, `Caption`, `Tiny`):** Manrope. `Tiny` is the uppercase eyebrow and badge style with wide tracking.
- **Minimum size:** 12sp. Nothing smaller should ship outside the decorative onboarding mock-ups.

## Shape, Outline & Depth

There is exactly one elevation model: a hard offset print shadow.

- **`Modifier.printSurface(shape, fill, shadow, outline, shadowOffset, pressProgress)`:** Draws a solid ink shadow offset 4dp down and right, then the fill, then a 2dp ink outline. It clips content to the shape.
  - Cards and buttons use the defaults.
  - Small chips use `shadow = null` and a `1.5.dp` outline.
  - Accent buttons can use a coral shadow under an ink fill.
- **Pressed state:** Pass `rememberPrintPress(interactionSource)` as `pressProgress`. The surface slides into its shadow as it is pressed.
- **Dense lists:** Rows in long lists (messages, notifications, members) use a 1.5dp outline with no shadow, so the list stays calm.
- **Radii:** 12dp small controls, 18dp task cards, 24dp cards (`PrintTokens.RadiusCard`), 28dp sheet tops, and full pills for chips and buttons.
- **Organic shapes:** `BlobShape(seed, wobble)` gives each avatar, empty state and decorative patch a stable hand-cut outline. Seed it from a stable id so a shape never changes between recompositions.
- **Shadow room:** Leave at least `PrintTokens.ShadowOffset` of padding to the right of and below a shadowed surface, so the shadow is not clipped by its parent.

## Texture & Decoration

- `Modifier.paperGrain(alpha)` adds a speckled paper texture to full-screen backgrounds. Use 0.55 by default and about 0.3 behind chat.
- `HalftonePatch` places an ink dot grid in a corner.
- `PrintStar` is a small four-point sparkle and can twinkle.
- `CollabSphereMark` is the brand mark. It assembles on entrance and spins when tapped.
- `AuthBackdrop` (in `LoginScreen.kt`) is the shared decoration for auth screens.
- Use one or two decorations per screen at most, kept away from interactive content.

## Motion

- **Easing:** Entrances use `PrintTokens.SpringEasing`, an overshooting cubic bezier, for about 400ms.
- **Lists:** Lists animate in with `printListEntrance(key, index, registry)`, which rises and fades each item with a stagger. The registry from `rememberEntranceRegistry()` stops items from animating again when they scroll back into view.
- **Blobs:** Decorative blobs drift slowly with `rememberBlobPhase(durationMillis)`.
- **Reduced motion:** Every animation checks `rememberPrintReducedMotion()`, which is true when the system animator duration scale is 0. When it is true:
  - entrances become a short fade
  - presses snap
  - blobs, stars and the logo stay still

## Components

### Buttons
- **`PrintButton`:** 56dp pill. Primary is an ink fill with paper text. Secondary (`primary = false`) is a white fill with ink text. Both have the 4dp shadow and press into it.
- **`PrintIconButton`:** Circular, at least a 48dp touch target. Use `shadow = false` in headers and toolbars.
- **`SkeuoActionIconButton`:** Shadowless outlined circle for inline row actions. A destructive tint becomes red-dark.
- **`SkeuoHeaderActionButton`:** Compact ink pill with a coral shadow for header actions.
- **Text links:** Underlined ink.

### Chips
- **`PrintChip(text, fill, onClick)`:** Outlined pill. Selected chips use an accent fill or solid ink with paper text. Unselected chips are white.
- **Count badges:** Coral with an ink outline and ink text.

### Inputs
- **`SkeuoTextField`:** Paper or white fill with an ink outline.
- **Focus:** The focus ring (`skeuoFocusRing`) is leaf-dark, or red-dark on error.
- **Cursor and spinner:** Ink.

### Cards & Rows
- **Cards:** `printSurface` with a white fill on paper.
- **Kanban columns:** Paper fill. Each column header has an accent blob dot and a count chip.
- **Rows:** The dense-list style described above.

### Dialogs & Sheets
- **`WorkspaceDialogCard`:** The standard modal. It has an accent icon blob, an H2 title, scrollable content and a close button.
- **`WorkspaceDialogButton`:** The dialog's action button.
- **`PrintConfirmDialog`:** Use it for every destructive or irreversible confirmation.
- **Bottom sheets:** `PrintSheetShape` with `printSheetOutline()` and `PrintSheetHandle()`.

### Chat
Chat surfaces are toned down on purpose so conversations stay readable.
- **Own bubbles:** Solid ink with paper text.
- **Other bubbles:** White with a 1.5dp ink outline.
- **Composer:** `chatComposerDock()` holds the paper input `chatComposerField(focused)` and the coral `chatSendButton`. The send button turns plain paper while disabled.
- **Header:** `chatHeaderBar()` is a paper bar with a 2dp ink bottom rule.
- **Day separators:** Outlined paper pills.

### Empty states
`SkeuoEmptyState` shows a blob-framed icon, an H2 title, a body line, and an optional `PrintButton` action.

## Accessibility

- Touch targets are at least 48dp, or 44dp for inline text links.
- Ink on paper, white, leaf, coral, sky and sun all meet WCAG AA for body text.
- Never rely on colour alone. Unread and selected states also change outline weight, fill or a dot.
- Respect reduced motion as described under Motion.

## Known exceptions

- GitHub previews keep GitHub's brand colours.
- The onboarding simulators are illustrations. They use small decorative type and tinted mock bubbles.
- Dark mode is unchanged.
