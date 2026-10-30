---
name: EDI Spark
description: A clear, capable design system for effortless retailer EDI.
colors:
  exchange-indigo-deep: "oklch(0.18 0.1 270)"
  exchange-indigo: "oklch(0.27 0.15 270)"
  exchange-indigo-action: "oklch(0.45 0.18 270)"
  exchange-indigo-mid: "oklch(0.62 0.15 270)"
  exchange-indigo-pale: "oklch(0.94 0.025 270)"
  clear-white: "oklch(1 0 0)"
  quiet-surface: "oklch(0.96 0.012 270)"
  technical-ink: "oklch(0.18 0.035 270)"
  supporting-ink: "oklch(0.45 0.025 270)"
  connection-mint: "oklch(0.88 0.13 155)"
  connection-mint-deep: "oklch(0.54 0.15 155)"
  danger: "oklch(0.55 0.19 25)"
  warning-amber: "oklch(0.72 0.14 75)"
typography:
  page-title:
    fontFamily: "Source Sans 3, Segoe UI, sans-serif"
    fontSize: "1.5rem"
    fontWeight: 600
    lineHeight: 1.25
    letterSpacing: "-0.025em"
  section-title:
    fontFamily: "Source Sans 3, Segoe UI, sans-serif"
    fontSize: "1rem"
    fontWeight: 600
    lineHeight: 1.35
  body:
    fontFamily: "Source Sans 3, Segoe UI, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.45
  label:
    fontFamily: "Source Sans 3, Segoe UI, sans-serif"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.35
  document-reference:
    fontFamily: "ui-monospace, SFMono-Regular, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.35
rounded:
  control: "0.375rem"
  container: "0.5rem"
  pill: "999px"
spacing:
  xs: "0.25rem"
  sm: "0.5rem"
  md: "0.75rem"
  base: "1rem"
  lg: "1.5rem"
  xl: "2rem"
  2xl: "3rem"
components:
  app-shell:
    sidebarWidth: "15rem"
    headerHeight: "4rem"
    backgroundColor: "{colors.clear-white}"
  button-primary:
    backgroundColor: "{colors.connection-mint}"
    textColor: "{colors.technical-ink}"
    rounded: "{rounded.control}"
    height: "2.5rem"
  data-table:
    backgroundColor: "{colors.clear-white}"
    textColor: "{colors.technical-ink}"
    rounded: "{rounded.container}"
    rowPadding: "0.75rem 1rem"
---

# Design System: EDI Spark Product

## Creative north star

**The Clear Exchange, at working density.** EDI Spark should feel like a brightly lit logistics control room after the paperwork and protocol complexity have been handled: every document is visible, every exception has a next action, and routine exchange requires little thought.

The product shares the website’s Source Sans 3 voice, committed indigo infrastructure, signal mint, compact corners, and flat ruled composition. It adapts those brand ingredients to Linear-like product density in light mode: a persistent navigation rail, aligned page grid, continuous document streams, compact filters, and focused exception panels. The system is architectural and rule-led rather than card-led.

## Colour

True white is the reading surface. Quiet Surface separates navigation, table headings, hover states, and secondary regions. Exchange Indigo identifies infrastructure; Exchange Indigo Action handles navigation, links, and focus. Connection Mint identifies meaningful primary actions, successful delivery, and healthy connections. Danger and Warning Amber are reserved for genuine operational states and always appear with an icon and text.

**Yellow is prohibited throughout the product, including the logo.** Purple gradients, neon effects, glass, and decorative colour are also prohibited.

## Typography

Source Sans 3 is the shared brand and product family. The dashboard uses a fixed, compact scale rather than the website’s fluid display scale: 24px page titles, 16px section titles, 14px body and table content, and 12px metadata. Page titles use 600 rather than oversized display weight. Use tabular numerals for times, counts, and metrics. Use the system monospace stack only for genuine document, interchange, or control references.

Sentence case is mandatory. Do not use tiny tracked uppercase eyebrows. Labels must retain at least WCAG 2.2 AA contrast on their actual surface.

## Layout and density

Desktop uses a 15rem persistent sidebar and 4rem utility header. Main content shares one aligned rail with a maximum width of 96rem. Operational rows use 12px vertical and 16px horizontal padding. Related controls sit 8–12px apart; separate functional regions use 24–32px.

The dashboard prioritizes a chronological document stream. Exceptions occupy a narrower, sticky rail at wide breakpoints and move above the stream on smaller screens. Mobile uses a navigation drawer and document cards that preserve status, retailer, reference, direction, and time rather than merely hiding table columns.

## Components and interaction

- Containers use a single border or tonal surface, never a decorative border-plus-shadow combination.
- Functional containers use 6–8px radii. Pills are reserved for compact counts or statuses.
- Every status combines icon, text, and colour.
- Controls provide visible focus, clear hover/active feedback, useful empty states, and specific action labels.
- Motion communicates state in 150–200ms and respects reduced-motion preferences. There are no page-load sequences.
- Search and filters work immediately; document rows retain a clear, keyboard-reachable view action.

## Product rules

### Do

- Lead with documents requiring attention and explain the next action in plain English.
- Keep routine exchanges dense, stable, and easy to scan.
- Preserve complete document traceability through references, related references, retailer, direction, status, and time.
- Reveal raw EDI and mapping detail progressively, only when useful.
- Use realistic operational states and empty-state guidance.

### Don’t

- Don’t recreate dense traditional enterprise EDI software or developer tooling.
- Don’t use generic revenue metrics, identical stat-card grids, decorative charts, or empty dashboard panels.
- Don’t use yellow, gradients, glassmorphism, wide shadows, oversized radii, or decorative grid backgrounds.
- Don’t communicate status through colour alone.
- Don’t make controls look interactive unless they work.
