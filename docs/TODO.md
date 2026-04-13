# TODO — ConsoleMVC .cvw Plugin

## Tier 1 — Essential (Must Have)

- [ ] File type registration (`.cvw` extension, icon, language)
- [ ] Syntax highlighting (directives + C# code body)
- [ ] Code completion (directives + code body basics)
- [ ] Error highlighting (missing `@model`, unknown types in directives)

## Tier 2 — Important (Should Have)

- [ ] Navigation (Ctrl+Click on types, controller <-> view navigation)
- [ ] Refactoring support (rename/move model updates `.cvw` files)
- [ ] Live templates (`cvw`, `navto`, `navquit`)
- [ ] File templates ("New ConsoleMVC View")

## Tier 3 — Nice to Have

- [ ] Structure view
- [ ] Inspections and quick-fixes
- [ ] Breadcrumb / tab title
- [ ] Formatting (C# formatter on code body)

## Backend (Phase 2)

- [ ] ReSharper backend component for deep C# analysis
- [ ] Virtual document generation / language injection
- [ ] Type resolution for `@model` directive
- [ ] Full C# completion in code body via backend
