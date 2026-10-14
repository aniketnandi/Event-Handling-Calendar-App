# Design History: Single Calendar → Multiple Calendars

## Multi-calendar support
What: Introduced MultiCalendarModelImpl and internal CalendarData objects.
Why: Users need to create, switch between, and export multiple calendars.
Where: calendar.model.MultiCalendarModelImpl, CalendarData, new commands in CalendarController.

### What changed
- Introduced a multi-calendar model implementation (`MultiCalendarModelImpl`) that manages:
    - A collection of calendars, each represented by a `CalendarData` object.
    - Operations to add, rename, remove, and switch calendars.
- Introduced `CalendarData` to group:
    - The calendar’s list of `CalendarEvent`s.
    - The calendar’s `ZoneId` (timezone).
- Extended/updated the `CalendarModel` (or `MultiCalendarModel`) interface with methods such as:
    - `addCalendar(String, ZoneId)`
    - `renameCalendar(String, String)`
    - `changeTimezone(String, ZoneId)`
    - `useCalendar(String)`
    - Calendar-scoped copy methods (`copyEvent`, `copyEventsOn`, `copyEventsBetween`).

### Why
- The first version supported a single calendar; everything lived in one model.
- The app now needs multiple independent calendars with unique names.
- Centralizing per-calendar data (events + timezone) into `CalendarData` 
    keeps the model consistent and avoids scattering calendar state across the code.
- A dedicated multi-calendar model keeps all calendar management logic in the model 
    layer and preserves the controller as a thin coordinator (MVC).

## Timezone support
What: Ability to change a calendar’s timezone and convert event times across calendars.
Why: Copying and listing events must be time-zone aware when copying and listing events.
Where: MultiCalendarModelImpl.changeTimezone, time conversion helpers, controller parsing.

### What changed
- Each calendar now stores a `ZoneId` in `CalendarData`.
- `CalendarModel` / `MultiCalendarModelImpl` gained methods such as:
    - `currentZone()`
    - `changeTimezone(String, ZoneId)`
- Copy operations (`copyEventsOn`, `copyEventsBetween`, `copyEvent`) convert event times 
    between the source calendar’s timezone and the target calendar’s timezone.
- Export methods take the calendar’s timezone into account.

### Why
- Each calendar is associated with an IANA timezone.
- Storing the timezone per calendar (instead of per event) matches the rule 
    "all events in that calendar are assumed to be in its timezone".
- Doing timezone conversion in the model keeps all time logic in one place and avoids 
    duplication in the controller or view.

## MVC
- Model: `MultiCalendarModelImpl`, `CalendarData`, `CalendarEvent`, `RecurrenceRule`
- View: `EventView` / `CliView` (unchanged except for new messages).
- Controller: `CalendarController`, which now coordinates between:
    - The multi-calendar model
    - The exporter
    - The view

## Controller changes
What: CalendarController.parse() now handles many new commands (series, calendars, export, timezone).
Why: The multi-calendar stage introduced more user actions that must be dispatched by the controller.
Where: doCreateCalendar, doUseCalendar, doSeriesCreate, doSeriesEdit, doSeriesDelete, doPrintDay, doExport, etc.

### What changed
- `CalendarController` now:
    - Depends on the multi-calendar model and the `CalendarExporter`.
    - Supports new commands:
        - `create calendar --name <name> --timezone <area/location>`
        - `edit calendar --name <name> --property <name|timezone> <value>`
        - `use calendar --name <name>`
        - `copy event ...`
        - `copy events on ...`
        - `copy events between ...`
    - Validates that a calendar is "in use" before executing any existing event commands.
- The existing parsing logic was extended, not replaced:
    - Existing event commands still work but are scoped to the current calendar.

### Why
- New features (multi-calendar, timezones, copy, export) are introduced only through new commands; the controller 
    is the right place to parse them.
- The controller now enforces context (a selected calendar), so event commands always apply to a known calendar.
- Injecting `CalendarExporter` keeps the controller independent of a specific export implementation.

## Enhanced Error Handling / Validation
What: Additional exceptions, input validation, and controller error outputs.
Why: Multiple calendars introduce more kinds of invalid input and edge cases.
Where: CalendarController error branches, MultiCalendarModelImpl validation checks.

## Model - event types reuse

### What stayed the same
- `CalendarEvent`, `EventSeries`, and `RecurrenceRule` are reused from the single-calendar version with minimal changes.
- Event creation, editing, uniqueness rules, and series handling follow the same logic as in the single-calendar version.
- Existing methods for checking busy status, printing events, and editing events are reused inside the multi-calendar 
    model by first resolving the active calendar.

### Why
- The single-calendar design already captured the core event and recurrence logic.
- Reusing these types avoids duplication and reduces the risk of breaking previously correct behavior.
- Only the calendar "container" layer changed; the event/series layer remains stable.

## Export architecture (CSV + iCal)
What: Added CalendarExporter and CalendarExporterImpl.
Why: Calendars must export to both CSV and iCal/ICS.
Where: New exporter classes; CalendarController.doExport.

### What changed
- Introduced a `CalendarExporter` interface with methods like:
    - `writeCsv(Path, List<CalendarEvent>, ZoneId)`
    - `writeICal(Path, List<CalendarEvent>, ZoneId)`
- Implemented `CalendarExporterImpl` to handle:
    - CSV export (existing single-calendar behavior).
    - New iCal export according to the required format.
- `CalendarController` now receives a `CalendarExporter` in its constructor.
- The `export cal` command:
    - Inspects the file extension (`.csv` or `.ical`/`.ics`).
    - Calls the appropriate exporter method.
    - Prints the absolute path returned by the exporter.

### Why
- Both CSV and iCal formats are supported, picked automatically from the file name.
- Moving export logic out of the model and into a dedicated `CalendarExporter`:
    - Gives the model a single responsibility (event and calendar logic only).
    - Makes it easy to add new export formats later (Open/Closed Principle).
    - Keeps controller code focused on command parsing and delegation.

## Expanded Event Model
What: CalendarEvent now includes recurrence, seriesId, privacy, and new editing methods.
Why: Needed to support series updates, privacy rules, and more detailed event modifications.
Where: CalendarEvent class; extra setters and attribute fields.

## Miscellaneous
- Moved export file I/O out of the model into an exporter service (`calendar.export.CalendarExporterImpl`) to preserve 
    MVC separation.
- Controller now coordinates exporting and prints absolute paths, while the model exposes read-only data getters 
    used for export.
- Introduced explicit packages: model, controller, view, export; added package docs and class Javadocs so the roles 
    are easy to identify.
- Kept `CalendarModel.exportCurrent(Path)` in interface for API compatibility, but the model implementation throws 
    `UnsupportedOperationException` to indicate exporting is now handled by the controller/exporter.