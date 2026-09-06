---
name: Modern Tactile Skeuomorphism
colors:
  surface: '#fbf9f3'
  surface-dim: '#dcdad4'
  surface-bright: '#fbf9f3'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f6f3ed'
  surface-container: '#f0eee8'
  surface-container-high: '#eae8e2'
  surface-container-highest: '#e4e2dc'
  on-surface: '#1b1c18'
  on-surface-variant: '#59413b'
  inverse-surface: '#30312d'
  inverse-on-surface: '#f3f1eb'
  outline: '#8d716a'
  outline-variant: '#e1bfb7'
  surface-tint: '#ad3310'
  primary: '#a9310e'
  on-primary: '#ffffff'
  primary-container: '#cb4925'
  on-primary-container: '#fffbff'
  inverse-primary: '#ffb5a1'
  secondary: '#4952c1'
  on-secondary: '#ffffff'
  secondary-container: '#828bfd'
  on-secondary-container: '#101890'
  tertiary: '#0d6947'
  on-tertiary: '#ffffff'
  tertiary-container: '#31835f'
  on-tertiary-container: '#f5fff6'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#ffdbd1'
  primary-fixed-dim: '#ffb5a1'
  on-primary-fixed: '#3c0800'
  on-primary-fixed-variant: '#881f00'
  secondary-fixed: '#e0e0ff'
  secondary-fixed-dim: '#bec2ff'
  on-secondary-fixed: '#00036b'
  on-secondary-fixed-variant: '#2f38a8'
  tertiary-fixed: '#a2f4c8'
  tertiary-fixed-dim: '#86d7ad'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#fbf9f3'
  on-background: '#1b1c18'
  surface-variant: '#e4e2dc'
typography:
  display-lg:
    fontFamily: Newsreader
    fontSize: 48px
    fontWeight: '600'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Newsreader
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.015em
  headline-lg:
    fontFamily: Newsreader
    fontSize: 36px
    fontWeight: '600'
    lineHeight: 44px
    letterSpacing: -0.015em
  headline-lg-mobile:
    fontFamily: Newsreader
    fontSize: 26px
    fontWeight: '600'
    lineHeight: 34px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Newsreader
    fontSize: 28px
    fontWeight: '500'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Newsreader
    fontSize: 22px
    fontWeight: '500'
    lineHeight: 30px
  title-lg:
    fontFamily: Manrope
    fontSize: 18px
    fontWeight: '700'
    lineHeight: 26px
    letterSpacing: -0.01em
  title-md:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Manrope
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 26px
  body-md:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 22px
  label-lg:
    fontFamily: Manrope
    fontSize: 14px
    fontWeight: '600'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Manrope
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
  label-sm:
    fontFamily: Manrope
    fontSize: 11px
    fontWeight: '700'
    lineHeight: 14px
    letterSpacing: 0.04em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  space-2xs: 4px
  space-xs: 8px
  space-sm: 12px
  space-md: 16px
  space-lg: 24px
  space-xl: 32px
  space-2xl: 48px
  space-3xl: 64px
  space-4xl: 96px
  gutter-mobile: 16px
  gutter-tablet: 24px
  gutter-desktop: 32px
  margin-mobile: 16px
  margin-tablet: 32px
  margin-desktop: 48px
---

## Brand & Style

This design system expresses modern skeuomorphism: a tactile, volumetric, hyper-refined aesthetic combining the physical fidelity of machined materials with modern digital typography and spacious layout structures. It eschews both flat design and overly nostalgic, heavy-handed skeuomorphism in favor of precise physical cues—soft directional light, debossed interaction states, polished micro-bevels, and layered elevation planes.

### Personality & Audience
- **Tactile & Substantial:** Interfaces feel physically milled, molded, or stamped out of solid composite matter.
- **Warm & Grounded:** Built upon a calm, warm-grey canvas that rejects harsh sterile whites in favor of organic gallery warmth.
- **Precision Engineered:** Every shadow, highlight, and surface transition obeys an uncompromising 135° (top-left) directional light model.
- **Target Audience:** Modern collaborative teams, high-craft creative studios, and product organizations seeking high emotional fidelity, sensory feedback, and distinct visual character without sacrificing accessibility or performance.

## Colors

The palette is engineered around an organic, tactile warm base complemented by vibrant energetic accents and dedicated specular lighting values.

### Base Canvases & Surfaces
- **Base Canvas (`#EFEDE7`):** The foundational substrate upon which all components are stamped or raised.
- **Surface Raised (`#F6F4EF`):** Resting platform for cards, containers, and interactive elements.
- **Surface Floating (`#F9F7F2`):** Elevated overlays, modals, popovers, and floating toolbars.
- **Border Hairline (`rgba(44, 42, 40, 0.08)`): Milled separation boundary to define component perimeters under diffused light.

### Ink & Typography
- **Ink Primary (`#2C2A28`):** High-contrast, rich warm-charcoal for principal reading and primary iconography.
- **Ink Muted (`#7A7570`):** Soft stone tone for secondary metadata, disabled indicators, and subtle structural labels.

### Chromatic Accents & Semantic States
- **Primary Coral (`#F0633D` to `#D6501F`):** Tactile push triggers, focal actions, and active highlighted interactions. Rendered with subtle directional gradient shading.
- **Secondary Indigo (`#4C55C4` to `#6B74E0`):** Collaboration presence markers, deep navigation states, and systemic focus rings.
- **Success / Online Mint (`#4E9E78`):** Active statuses, positive deltas, and live connectivity indicators.
- **Warning Amber (`#E0A030`):** Pending states, advisory notifications, and resource warnings.
- **Destructive Red (`#D64545` to `#B23434`):** Irreversible or destructive primary actions, executed with a weighted top-down gradient.

### Illumination Primitives
- **Light Source Key:** 135° angled incident light (top-left to bottom-right).
- **Specular Highlight (`rgba(255, 255, 255, 0.85)`): Applied to top-left offsets and top-edge bevel borders.
- **Occlusion Shadow (`rgba(160, 150, 138, 0.55)`): Diffused ambient shadow cast toward the bottom-right.

## Typography

The typographic hierarchy pairs an authoritative editorial serif for titles with a precise geometric sans-serif for UI clarity and density.

- **Headlines & Editorial Voice:** Set in a warm, variable serif with classical proportions. Used for broad headers, milestone metric displays, and introductory section titles to ground the digital surface in physical print heritage.
- **Interface & Mechanical Copy:** Set in Manrope across weights 400 to 800. Provides clear legibility on debossed panels, embossed pill buttons, and compact toolbars.
- **Letter Spacing & Optical Scale:** Display sizes employ tight negative tracking (-0.02em to -0.01em) to preserve cohesion against soft ambient shadows. Smaller UI labels and caps badges utilize wide tracking (+0.02em to +0.04em) to maintain readability under etched or inset lighting.

## Layout & Spacing

The layout is constructed on a 4px mechanical sub-grid grouped into 8px structural steps. Physical skeuomorphic elements demand generous padding around their perimeter to avoid shadow clipping and to let optical light fall naturalistically.

### Grid Architecture
- **Desktop (1200px+):** 12-column fluid grid, 32px gutters, 48px outer page margin. Max content container width clamped to 1440px.
- **Tablet (768px – 1199px):** 8-column fluid grid, 24px gutters, 32px margins. Modules reflow from 3-up to 2-up configurations.
- **Mobile (< 768px):** 4-column fluid grid, 16px gutters, 16px margins. Multi-column cards stack vertically into single tactile plates.

### Spacing Guidelines
- **Extrusion Clearance:** Always ensure an element has at least `16px` (`space-md`) clearance from adjacent structural boundaries to allow the dual outer cast shadows to fall without hard cutoffs.
- **Debossed Cavities:** Inset panels and trays require minimum interior padding of `16px` (`space-md`) on mobile and `24px` (`space-lg`) on desktop to maintain the illusion of depth without crowding child elements.

## Elevation & Depth

Visual hierarchy is communicated through directional light (135°, top-left to bottom-right), simulating stamped, raised, and floating physical planes.

### Illumination Levels
1. **Debossed / Inset (Troughs, Text Fields, Well Areas):**
   - Simulates matter stamped down into the base canvas.
   - Shadow configuration:
     - `inset 4px 4px 8px rgba(160, 150, 138, 0.55)` (top-left inner shadow)
     - `inset -4px -4px 8px rgba(255, 255, 255, 0.85)` (bottom-right inner bounce light)
   - Background: Base Canvas (`#EFEDE7`) or slightly tinted down.

2. **Ground Plane (Base Level):**
   - Flat canvas foundation (`#EFEDE7`) with no cast elevation shadow.

3. **Raised (Cards, Interactive Plates, Standard Buttons):**
   - Surfaces push upward from the ground canvas.
   - Surface color: `#F6F4EF`.
   - Shadow configuration:
     - `-6px -6px 14px rgba(255, 255, 255, 0.85)` (top-left specular halo)
     - `8px 8px 18px rgba(160, 150, 138, 0.55)` (bottom-right core occlusion)
   - Edge Treatment: 1px hairline border of `rgba(44, 42, 40, 0.08)` combined with a subtle top-edge internal highlight: `inset 0 1px 0 rgba(255, 255, 255, 0.9)`.

4. **Floating / Suspended (Modals, Context Menus, Tooltips):**
   - Disconnected and floating above the canvas.
   - Surface color: `#F9F7F2`.
   - Shadow configuration:
     - `0 16px 32px rgba(160, 150, 138, 0.45)`
     - `0 4px 8px rgba(160, 150, 138, 0.2)`
     - `0 0 0 1px rgba(44, 42, 40, 0.06)`

5. **Pressed / Active (Buttons During Click, Toggles Engaged):**
   - Transition from raised dual-shadows to instant debossed inset shadows with a `0.98` scale downshift.

## Shapes

The design system employs smooth, pebble-like corner radii that evoke precision CNC machining or molded composite resins. Sharp corners are forbidden; every junction possesses a deliberate bevel or rounded transition.

### Corner Radii Scale
- **Radius XS (10px):** Micro-chips, badges, nested checkboxes, and segmented toggle segments.
- **Radius SM (14px):** Standard buttons, text input fields, tooltips, and compact pills.
- **Radius MD (18px):** Medium cards, alert banners, list group containers, and dropdown sheets.
- **Radius LG (24px):** Primary dashboards modules, master cards, and contextual drawers.
- **Radius XL (28px):** Modals, overlay dialogs, and hero feature surfaces.
- **Full Pill (`9999px`):** Floating status avatars, filter chips, and circular icon triggers.

## Components

### Buttons
- **Primary Coral Button:**
  - Base: Linear gradient from `#F0633D` (135° top-left) to `#D6501F` (bottom-right).
  - Radii: `14px` (`rounded-sm`).
  - Lighting: Top edge rim light `inset 0 1px 1px rgba(255, 255, 255, 0.4)`, bottom drop shadow `4px 6px 12px rgba(214, 80, 31, 0.4)`.
  - Typography: Ink white (`#FFFFFF`), `label-lg`.
  - Pressed State: Inset shadow `inset 2px 2px 4px rgba(0, 0, 0, 0.3)`, scale to `0.98`.
- **Secondary Neutral Button:**
  - Base: Surface Raised (`#F6F4EF`) with 1px border `rgba(44, 42, 40, 0.08)`.
  - Lighting: Raised dual-shadow (`-4px -4px 10px rgba(255, 255, 255, 0.9)`, `4px 6px 12px rgba(160, 150, 138, 0.45)`).
  - Text: Ink Primary (`#2C2A28`).

### Input Fields & Search Bars
- **Container:** Inset debossed trough (`inset 3px 3px 6px rgba(160, 150, 138, 0.4)`, `inset -3px -3px 6px rgba(255, 255, 255, 0.8)`).
- **Background:** Base Canvas (`#EFEDE7`).
- **Radius:** `14px` (`rounded-sm`).
- **Border:** 1px `rgba(44, 42, 40, 0.06)`.
- **Focus State:** Glow border using Secondary Indigo (`#4C55C4`) with `0 0 0 3px rgba(76, 85, 196, 0.2)`.

### Cards & Content Plates
- **Surface:** Surface Raised (`#F6F4EF`).
- **Radius:** `18px` (`rounded-md`) or `24px` (`rounded-lg`).
- **Depth:** Standard raised dual-shadow (`-6px -6px 14px rgba(255, 255, 255, 0.85)`, `8px 8px 18px rgba(160, 150, 138, 0.55)`).
- **Rim:** Top border highlighted with `inset 0 1px 0 rgba(255, 255, 255, 0.9)`.
- **Padding:** `24px` desktop, `16px` mobile.

### Chips & Filter Pills
- **Unselected:** Subtle raised state or flat with 1px hairline border, `9999px` full pill radius.
- **Selected:** Debossed trough state (`inset 2px 2px 4px rgba(160, 150, 138, 0.4)`) with Primary Coral text or pill background fill.

### Checkboxes & Radio Buttons
- **Unchecked Track:** Debossed cavity (`inset 2px 2px 4px rgba(160, 150, 138, 0.5)`, `inset -2px -2px 4px rgba(255, 255, 255, 0.8)`), radius `10px` for checkboxes, `9999px` for radios.
- **Checked Indicator:** Raised embossed jewel or checkmark sitting inside the cavity, tinted in Indigo (`#4C55C4`) or Mint (`#4E9E78`) with a sharp top highlight.

### Lists & Segmented Controls
- **Segmented Control Trough:** Full inset debossed pill container housing raised tactile sliding switches that snap over active options with a raised dual-shadow.
- **List Groups:** Milled separation lines using hairline border (`rgba(44, 42, 40, 0.08)`) with a parallel `1px` white specular highlight underneath (`rgba(255, 255, 255, 0.85)`), creating an engraved groove effect.