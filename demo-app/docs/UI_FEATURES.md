# UI Features — Demo App

Angular 21 enterprise demo application. All components are standalone signals-based; there is no NgRx or traditional `@NgModule`.

---

## Table of Contents

1. [Authentication](#1-authentication)
2. [Shell Layout](#2-shell-layout)
3. [Role-Based Access Control](#3-role-based-access-control)
4. [Dashboard](#4-dashboard)
5. [User Management](#5-user-management)
6. [Role Management](#6-role-management)
7. [Document Management](#7-document-management)
8. [Reports](#8-reports)
9. [Settings](#9-settings)
10. [Profile](#10-profile)
11. [Access Denied Page](#11-access-denied-page)
12. [Shared Components](#12-shared-components)
13. [Routing & Guards](#13-routing--guards)
14. [Theme System](#14-theme-system)

---

## 1. Authentication

**Route:** `/login`  
**Guard:** `guestGuard` — redirects authenticated users away from the login page.

### Demo Credentials

| Role          | Email               | Password     |
|---------------|---------------------|--------------|
| Administrator | admin@demo.com      | admin123     |
| Manager       | manager@demo.com    | manager123   |
| Viewer        | viewer@demo.com     | viewer123    |

### Behaviour
- Credentials are validated against an in-memory user list (`AuthService`).
- On success the user object (without password) is written to `sessionStorage` under the key `demo_user`.
- Session is restored on page reload via `restoreSession()`.
- Logout clears `sessionStorage` and navigates to `/login`.

---

## 2. Shell Layout

**Component:** `Shell` → wraps all authenticated pages.

### Header
- Displays the application logo/title.
- **Hamburger button** (top-left): calls `LayoutService.toggle()` to collapse/expand the sidebar.
- **User dropdown** (top-right):
  - Shows avatar, full name, and role badge at the top of the menu.
  - Links: My Profile, Settings (Admin only), Logout.

### Sidebar
- Vertical navigation with Bootstrap Icons.
- Nav items are filtered at runtime via `auth.can(permission)`:
  - **Dashboard** — always visible.
  - **Users** — requires `usersView`.
  - **Roles** — requires `rolesView`.
  - **Document Management** — always visible.
  - **Reports** — requires `rolesView`.
  - *(Settings nav item currently commented out; route still accessible via header dropdown.)*
- Active route is highlighted with `RouterLinkActive`.

### Sidebar Collapse
- `LayoutService` exposes two signals: `mobileOpen` (overlay on narrow screens) and `collapsed` (width-0 on ≥ 768 px).
- Both are toggled together by the single hamburger button.
- CSS: `.shell.sidebar-collapsed .sidebar { width: 0; overflow: hidden; }` (≥ 768 px only).

### Footer
- Static copyright / branding bar.

---

## 3. Role-Based Access Control

Three built-in roles with the following permission matrix:

| Permission     | Administrator | Manager | Viewer |
|----------------|:---:|:---:|:---:|
| `usersView`    | ✓   | ✓   | ✗  |
| `usersCreate`  | ✓   | ✓   | ✗  |
| `usersEdit`    | ✓   | ✓   | ✗  |
| `usersDelete`  | ✓   | ✗   | ✗  |
| `rolesView`    | ✓   | ✓   | ✗  |
| `rolesEdit`    | ✓   | ✗   | ✗  |

**`AuthService.can(permission)`** is the single source of truth used by:
- Route guards (`permGuard`)
- Sidebar nav filtering
- Dashboard quick-action cards
- Column visibility in data tables
- Action buttons (upload, delete, etc.)

---

## 4. Dashboard

**Route:** `/dashboard`

- Greeting card with the logged-in user's name and avatar (generated via `ui-avatars.com`).
- **Quick-action cards** — only cards the user has permission to access are shown:
  - User Management (`usersView`)
  - Role Management (`rolesView`)
  - Document Management (always)
  - My Profile (always)
- **Document category summary** — lists categories the user's role is permitted to view, showing `documentCount` per category.
- Data loaded from `DocumentCategoryStore.loadAll()` on `ngOnInit`.

---

## 5. User Management

**Route:** `/users` — guarded by `permGuard('usersView')`

### User List Page
- `DataTable` with columns: Name, Email, Role, Status, Created At, Actions.
- Client-side sort, text/dropdown filter, and pagination.
- **Role Permissions column** — hidden for roles without `rolesView` (Viewer).
- **Add User** button — visible only when `usersCreate` is true.
- Row actions:
  - Edit (pencil icon) — opens inline form dialog; requires `usersEdit`.
  - Delete (trash icon) — opens confirmation dialog; requires `usersDelete`.

### User Form (dialog)
- Fields: Full Name, Email, Role (dropdown), Status (Active/Inactive).
- Inline validation with Bootstrap feedback classes.
- Implemented as a `DialogFormBody` component rendered inside `<app-dialog>`.

---

## 6. Role Management

**Route:** `/roles` — guarded by `permGuard('rolesView')`

### Role List Page
- `DataTable` with columns: Name, Description, Permissions, Users, Built-in, Created At, Actions.
- **Permissions column** — shows a compact badge group listing enabled permissions.
- **Role Permissions column** — hidden for Viewer role.
- **Add Role** button — visible only when `rolesEdit` is true.
- Row actions:
  - Edit — opens form dialog; disabled for built-in roles; requires `rolesEdit`.
  - Delete — confirmation dialog; disabled for built-in roles; requires `rolesEdit`.

### Role Form (dialog)
- Fields: Name, Description, Permission checkboxes (grouped: Users / Roles).

---

## 7. Document Management

**Route:** `/documents`  
Available to all authenticated users; per-category visibility is role-filtered.

### Category List Page (`/documents`)
- Lists document categories in a `DataTable`.
- Columns: Name, Description, Permissions (per-role canView/canUpload/canDelete badges), Document Count.
- **Permission column** — hidden for Viewer role.
- **Visible categories** — filtered by `cat.permissions.find(p => p.roleId === user.roleId)?.canView`.
- **Add Category** button — visible when `rolesEdit`.
- Row actions: Edit, Delete (permission-gated).

### Document List Page (`/documents/:categoryId`)
- Shows documents within the selected category.
- Breadcrumb / back link to Categories.
- `DataTable` with columns: Name, Type, Size, Uploaded By, Date, Actions.
- Server-side–style pagination (page state via signal, `controlledPage` input).
- **Upload Document** button — visible when `canUpload` for the user's role in that category.
- Row actions:
  - View (eye icon) — opens `<app-doc-preview>` dialog for in-browser preview.
  - Delete (trash icon) — visible when `canDelete`.

### Document Preview Dialog
- Displays document metadata and an inline preview area.
- Supports PDF, image, and text file types.

---

## 8. Reports

**Route:** `/reports` — guarded by `permGuard('rolesView')`

Built with **Highcharts** via a shared `<app-hc-chart>` wrapper component.

### Charts

| # | Title | Chart Type | Data Source |
|---|-------|-----------|-------------|
| 1 | Document Upload Trend | Area spline | Static — last 12 months, 3 series |
| 2 | Documents per Category | Column | Live — `DocumentCategoryStore.categories()` |
| 3 | User Role Distribution | Donut (pie with innerSize 55%) | Static — 3 roles |
| 4 | Storage Used by Category | Horizontal bar | Static — 3 categories |

### Upload Trend — Current Month Indicator
- X-axis categories are generated dynamically as a rolling 12-month window ending at the current month.
- A red dashed `plotLine` is always placed at index `11` (the current month) with the label "Current month".

### Highcharts Global Defaults
Set once via `Highcharts.setOptions()` (in `hc-chart.ts`):
- Font family matches Bootstrap.
- Credits disabled.
- Custom colour palette aligned with Bootstrap's theme colours.

---

## 9. Settings

**Route:** `/settings` — guarded by `permGuard('rolesEdit')` (Administrator only)  
**Service:** `SettingsService` — `providedIn: 'root'`; loaded at app startup so the saved theme is applied before the shell renders.

### Sections

| Section | Controls |
|---------|----------|
| Appearance | Theme: Light / Dark / System (browser) — applied immediately on selection |
| Localization | Language dropdown (en, vi, fr, de, ja), Date format dropdown |
| Data Tables | Default page size (5, 10, 25, 50) |
| Notifications | Email, Push, Desktop toggles |

### Persistence
- Settings saved to `localStorage` under the key `app_settings`.
- Loaded at startup with `DEFAULTS` as fallback for missing keys.
- **Save Changes** writes all fields via `SettingsService.update()`.
- **Reset to Defaults** restores `DEFAULTS` and re-persists.

### Theme Application
- Theme radio changes are applied instantly via `valueChanges` subscription (no need to hit Save).
- `applyTheme()` sets `data-theme` attribute on `<html>`:
  - `"system"` resolves via `window.matchMedia('(prefers-color-scheme: dark)')`.

---

## 10. Profile

**Route:** `/profile` — available to all authenticated users.

Two cards rendered on one page:

### Personal Information
- Editable field: **Full Name**.
- Email displayed read-only.
- Role badge displayed read-only.
- Save calls `AuthService.updateName()` — updates the in-memory user list and `sessionStorage`.

### Change Password
- Fields: Current Password, New Password, Confirm New Password.
- Client-side validation: min length 6, new ≠ current, confirm must match new.
- Save calls `AuthService.changePassword()` — validates current password before updating.
- Form resets on success; error alert shown on wrong current password.

---

## 11. Access Denied Page

**Route:** `/access-denied`

- Shown when a user navigates to a route protected by `permGuard` without the required permission.
- Displays a 403 graphic, the user's current role badge, and a "Back to Dashboard" button.
- Guard redirects to `/access-denied` (instead of the login page) when the user is authenticated but lacks permission.

---

## 12. Shared Components

All shared components are exported from `src/app/shared/shared.imports.ts` as the `SHARED_IMPORTS` barrel array.

### `<app-btn>` — Reusable Button

| Input | Type | Default | Description |
|-------|------|---------|-------------|
| `variant` | `BtnVariant` | `'primary'` | Bootstrap button colour variant |
| `size` | `'sm' \| 'lg' \| ''` | `''` | Bootstrap size modifier |
| `type` | `'button' \| 'submit' \| 'reset'` | `'button'` | HTML button type |
| `icon` | `string` | `''` | Leading Bootstrap Icon class (e.g. `bi-save`) |
| `iconEnd` | `string` | `''` | Trailing icon class |
| `iconOnly` | `boolean` | `false` | Suppresses the `me-2` margin when there is no label |
| `loading` | `boolean` | `false` | Shows a spinner; disables the button |
| `disabled` | `boolean` | `false` | Disables the button |
| `block` | `boolean` | `false` | Adds `w-100` |
| `pill` | `boolean` | `false` | Adds `rounded-pill` |
| `flat` | `boolean` | `false` | Adds `border-0` (toolbar-style) |

Output: `btnClick` (MouseEvent) — not emitted when disabled or loading.

`:host` uses `display: contents` so the component is transparent to parent flex/grid layout.

---

### `<app-hc-chart>` — Highcharts Wrapper

| Input | Type | Description |
|-------|------|-------------|
| `options` | `Highcharts.Options` (required) | Chart configuration |
| `height` | `string` | CSS height of the container (default `'350px'`) |

- Chart created in `ngAfterViewInit`; subsequent `options` signal changes applied via `chart.update()` inside an `effect()`.
- `ResizeObserver` calls `chart.reflow()` whenever the container size changes.
- Cleaned up (`disconnect`, `destroy`) in `ngOnDestroy`.

---

### `<app-data-table>` — Generic Data Table

| Feature | Details |
|---------|---------|
| Columns | `TableColumn[]` — configurable label, key, sortable, filterable, align, formatter, cellTemplate |
| Sorting | Client-side single-column sort; cycles asc → desc → none |
| Filtering | Per-column; text input or dropdown (via `filterType` / `filterOptions`) |
| Pagination | External — emits `stateChange`; `controlledPage` input for server-side mode |
| Empty state | Custom `emptyMessage` input |
| Loading | Skeleton/spinner overlay via `loading` input |

---

### `<app-dialog>` — Modal Dialog

- Service-driven: `DialogService.open(config)` returns `Promise<boolean>`.
- Supports `bodyComponent` (any component) rendered as the dialog body.
- `DialogFormBody` interface: body component implements `onConfirm(): Promise<boolean>` to control close behaviour.
- Confirm button variant maps to dialog `type` (`info` → primary, `danger` → danger, etc.).

---

### `<app-pagination>`

Standalone pagination bar with page buttons, total count display, and disabled states.

---

### `<app-page-layout>`

Standard page chrome: title, subtitle, and an `actions` named content slot for header-area buttons.

---

### `<app-doc-preview>`

In-dialog document viewer. Renders a preview depending on the document MIME type.

---

### `<app-divider>`

Thin horizontal rule with configurable `spacing` and `thickness` inputs.

---

### `AppDatePipe`

Formats dates using the `DateFormat` setting from `SettingsService`.

---

## 13. Routing & Guards

```
/login               guestGuard      Login page
/                    authGuard        Shell wrapper
  /dashboard                          Dashboard
  /users             permGuard(usersView)   User list
  /roles             permGuard(rolesView)   Role list
  /reports           permGuard(rolesView)   Reports
  /documents                          Category list
  /documents/:id                      Document list
  /profile                            Profile
  /settings          permGuard(rolesEdit)   Settings
  /access-denied                      403 page
  /**                                 → /dashboard
```

### Guard Behaviour
| Guard | Behaviour |
|-------|-----------|
| `authGuard` | Redirects unauthenticated users to `/login` |
| `guestGuard` | Redirects authenticated users to `/dashboard` |
| `permGuard(p)` | If authenticated but `!auth.can(p)` → redirects to `/access-denied` |

---

## 14. Theme System

| Theme value | Resolved appearance |
|-------------|-------------------|
| `'light'`   | Light mode (`data-theme="light"`) |
| `'dark'`    | Dark mode (`data-theme="dark"`) |
| `'system'`  | Follows `prefers-color-scheme` media query |

`SettingsService` is injected in the root `AppComponent` so `applyTheme()` runs before any child component renders, preventing a flash of the wrong theme on startup.

CSS variables / overrides keyed off `[data-theme="dark"]` are defined in `src/styles/styles.scss`.

---

*Last updated: 2026-05-10*
