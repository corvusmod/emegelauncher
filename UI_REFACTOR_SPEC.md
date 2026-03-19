# UI Refactor Specification

## Overview
4-screen ViewPager with horizontal swipe navigation. Main is the default screen shown on launch.

## Screen Order
```
Graphs <── Main (default) ──> Apps ──> Other
```
Page indicator dots at the bottom.

---

## Screen 1: Graphs

Current GraphsActivity content embedded as a ViewPager page.

**Tabs:** Dashboard | Energy | Charging | Health | Tires | Climate | Trip | G-Meter

### Dashboard tab (redesigned):
```
┌──────────┬──────────┐
│  Speed   │ Battery  │
│  gauge   │  gauge   │
├──────────┼──────────┤
│   RPM    │   Econ   │
│  gauge   │  gauge   │
├──────────┴──────────┤
│ Power Flow graph    │
│ (+consume / -regen) │
├─────────────────────┤
│ G-Force graph       │
├─────────────────────┤
│ Range: XXX km       │
│ Gear: D  Mode: Eco  │
└─────────────────────┘
```

Other tabs unchanged.

---

## Screen 2: Main (default)

6 large buttons in 3x2 grid.

```
┌─────────────────┬─────────────────┐
│    Weather      │    Battery      │
│ ☁ 18°C outside │  66% · 280 km  │
│ Cabin: 22°C *  │  ~5.9 days     │
│                 │ (opens charger) │
├─────────────────┼─────────────────┤
│      Map        │     Music       │
│  (opens nav)    │  (opens music)  │
├─────────────────┼─────────────────┤
│     Radio       │     Phone       │
│  (opens radio)  │  (opens phone)  │
└─────────────────┴─────────────────┘
```

- **Weather**: weather description + outside temp + cabin temp (from cloud, hidden if unavailable). Press opens weather app (com.saicmotor.weathers)
- **Battery**: SOC %, range, standby estimate, BMS raw. Press opens charging app or Graphs Charging tab
- **Map/Music/Radio/Phone**: launch respective apps
- **Bottom info bar**: Drive mode (Eco/Normal/Sport/Winter) + Regen level (1-3) + saved profile restore tap

\* Cabin temp only shown when cloud data is available.

```
┌─────────────────────────────────┐
│  Mode: Eco  │  Regen: 2  │ ⟳  │
└─────────────────────────────────┘
```

---

## Screen 3: Apps

### Top half: App list
Scrollable grid of user-installed apps (4 columns).

### Bottom half: Mixed button layout
```
┌────────┬────────┬────────┬────────┐
│CarPlay │Android │ Video  │  360   │
│        │ Auto   │        │  View  │
├────────┴──┬─────┴──┬─────┴────────┤
│    Car    │ System │   Launcher   │
│  Settings │Settings│   Settings   │
├───────────┼────────┼──────────────┤
│  Rescue   │Touch-  │    Manual    │
│   Call    │ point  │              │
└───────────┴────────┴──────────────┘
```
Row 1: 4 buttons (media/connectivity)
Row 2: 3 buttons (settings)
Row 3: 3 buttons (utilities)

---

## Screen 4: Other

### Buttons (top, 2x4 grid)
```
┌───────────────┬───────────────┐
│  Diagnostics  │ Vehicle Info  │
├───────────────┼───────────────┤
│   Location    │     TBox      │
├───────────────┼───────────────┤
│   Controls    │    Cloud      │
│  (quick ctrl) │   (iSMART)   │
├───────────────┼───────────────┤
│ Find My Car   │               │
│ (horn+lights) │               │
└───────────────┴───────────────┘
```

### Info section (below buttons)
- Cloud status: logged in / not logged in
- 12V battery voltage (cloud)
- Cabin temperature (cloud)
- Force TBox Wake button
- Overlay toggle (enable/disable floating buttons)

---

## Preserved features
- Auto theme (dark/light from car night mode)
- Overlay service (back + recent apps)
- Navigation proxy (geo: intent handler)
- Cloud auto-query on start
- Profile auto-restore on start
- Weather broadcast receiver
- All polling timers (5s main, 2s graphs)
