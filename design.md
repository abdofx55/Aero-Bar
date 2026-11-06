# Aero Bar - Design Specification

*Inherits base visual guidelines from `../aero_design_system.md`*
*Target Hardware:* Dongfeng Shine Portrait Screen (960px × 1280px, 3:4 aspect ratio).

---

## Strict Negative Constraints (Stitch Guidelines)
- **NO smartphone status bar:** Do NOT draw WiFi, battery, signal bars, or clock.
- **NO duplicate bottom bars:** The bar being designed IS the system dock bar itself.
- **Fixed LTR direction:** Back, Home, and Recents are strictly on the left. Apps trigger is on the right.
- **Dark Mode Only:** Pure OLED Black (`#000000`) and Obsidian (`#1C2230`) with Electric Cyan (`#00D2FF`) active vectors.

---

## Screen Definitions to Generate in Stitch:

### 1. Screen 1: Persistent Bottom Aero Bar (Closed State)
- **Concept:** Represents the cockpit canvas with the Aero Bar anchored at the bottom (approx 8-10% height, ~80-90px).
- **Bar Layout (Left to Right):**
  - **Left Navigation Cluster:** 3 clean touch icons in a subtle glass capsule:
    1. `Back` (Chevron left)
    2. `Home` (Minimalist car or home glyph)
    3. `Recents` (Square or dual overlapping window glyph)
  - **Center:** Pinned Apps Slot (shown with subtle empty dashed placeholders or 2-3 pinned luxury app icons like Maps and Music with cyan dot indicator).
  - **Right Navigation Cluster:**
    - `Apps >>` (Grid or chevron app drawer icon in Electric Cyan glow).
- **Background:** Solid Pure Black (`#000000`) with a subtle 1px hairline cyan top border (`rgba(0, 210, 255, 0.2)`).

### 2. Screen 2: Floating App Drawer Panel (~70% Screen)
- **Concept:** Triggered when the user taps `Apps >>`. A luxury frosted glass floating overlay appears in the center of the portrait screen.
- **Drawer Contents:**
  - **Header:** Title "Applications" in Space Grotesk on left, and a subtle "Sort by: Recent" tag on right.
  - **App Grid (4 columns × 4-5 rows):** Clean automotive-scale app tiles (Maps, VLC, Radio, Settings, Spotify, Camera, Chrome, Weather, File Manager, etc.).
  - **Bottom Action Area:** A subtle frosted button "Hidden Apps (2)" allowing access to hidden applications.
- **Styling:** Frosted glass (`#1C2230` with backdrop blur, 20dp rounded corners, cyan hairline glow).
- **Bottom:** The Aero Bar remains visible underneath at the bottom with the `Apps >>` icon in active glowing state.

### 3. Screen 3: Floating Recent Apps Overlay
- **Concept:** Triggered when tapping the `Recents` button in the Aero Bar.
- **Contents:**
  - **Header:** "Recent Apps" title with a prominent "Clear All" button on the right in cyan outline.
  - **Card Carousel:** 3-4 large vertical preview cards representing running background apps (e.g., Google Maps, Media Player, Radio):
    - Each card displays: App icon, App name, and a prominent **X close button** on top-right.
    - A visual hint showing swipe left to dismiss.
- **Background:** Dimmed dark cockpit void (`#0A0D14`) with the Aero Bar anchored below (Recents button active).

### 4. Screen 4: App Long-Press Context Menu
- **Concept:** In the Floating App Drawer, long-pressing an app icon pops up an automotive context menu.
- **Menu Items:**
  1. **Pin to Bar** (with pin icon)
  2. **Hide App** (with eye-off icon)
  3. **App Details** (with info icon)
  4. **Uninstall** (with trash icon in Sport Crimson `#FF2D55`)
- **Styling:** Floating dark obsidian pill card with glowing cyan border directly beside the selected app.

### 5. Screen 5: Hidden Apps Management Panel
- **Concept:** Opened from "Hidden Apps" in the Drawer.
- **Contents:**
  - **Header:** Back arrow + Title "Hidden Apps".
  - **List of hidden apps:** Each row shows app icon, name, and a prominent **"Unhide"** button (Cyan outlined).
