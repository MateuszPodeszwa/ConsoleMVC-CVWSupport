# TODO — ConsoleMVC .cvw Plugin

## Tier 1 — Essential (Must Have) -- COMPLETE

- [x] File type registration (`.cvw` extension, icon, language)
- [x] Syntax highlighting (directives + C# code body)
- [x] Code completion (directives + code body basics)
- [x] Error highlighting (missing `@model`, empty args, missing return)
- [x] Brace matching (individual { } ( ) [ ] pairs) and commenter

## Tier 2 — Important (Should Have) -- COMPLETE (frontend scope)

- [x] Navigation: gutter icons on .cvw files linking to controllers
- [x] Navigation: Go to Related between .cvw views and controllers
- [x] Live templates: `cvw`, `navto`, `navaction`, `navquit`
- [x] File templates: "New ConsoleMVC View" with @model scaffold
- [ ] Refactoring support (rename/move model updates `.cvw` files) — requires Phase 2 backend

## Tier 3 — Nice to Have -- PARTIAL

- [x] Structure view (@model, @using, NavigationResult targets)
- [x] Editor tab title: "Controller/ActionView" format
- [x] Brace matching: individual { } ( ) [ ] token types
- [ ] Inspections/quick-fixes (model type mismatch, missing view) — requires Phase 2 backend
- [ ] Formatting (C# formatter on code body) — requires Phase 2 backend

## Backend (Phase 2) — NOT STARTED

- [ ] ReSharper backend component for deep C# analysis
- [ ] Virtual document generation / language injection
- [ ] Type resolution for `@model` directive (Ctrl+Click to type definition)
- [ ] Full C# completion in code body via backend
- [ ] Semantic error checking (type mismatches, unresolved references)
- [ ] Refactoring (rename/move model class updates .cvw files)
- [ ] Inspections (model type mismatch with controller's View() call)
