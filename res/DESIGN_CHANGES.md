# Design adjustments for Assignment 5

## Multi-calendar support
What: Introduced MultiCalendarModelImpl and internal CalendarData objects.
Why: Assignment 5 requires creating, switching, and exporting multiple calendars.
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
- Assignment 4 only required a single calendar; everything lived in one model.
- Assignment 5 requires multiple independent calendars with unique names.
- Centralizing per-calendar data (events + timezone) into `CalendarData` 
    keeps the model consistent and avoids scattering calendar state across the code.
- A dedicated multi-calendar model keeps all calendar management logic in the model 
    layer and preserves the controller as a thin coordinator (MVC).

## Timezone support
What: Ability to change a calendar’s timezone and convert event times across calendars.
Why: Assignment 5 specifies timezone-aware operations when copying and listing events.
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
- Assignment 5 requires each calendar to be associated with an IANA timezone.
- Storing the timezone per calendar (instead of per event) matches the requirement 
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
Why: Assignment 5 introduces more user actions that must be dispatched by the controller.
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
    - Validates that a calendar is "in use" before executing any event commands from Assignment 4.
- The existing parsing logic was extended, not replaced:
    - Assignment 4 commands still work but are scoped to the current calendar.

### Why
- New features (multi-calendar, timezones, copy, export) are introduced only through new commands; the controller 
    is the right place to parse them.
- The controller now enforces context (a selected calendar), which matches the text in Assignment 5.
- Injecting `CalendarExporter` keeps the controller independent of a specific export implementation.

## Enhanced Error Handling / Validation
What: Additional exceptions, input validation, and controller error outputs.
Why: Assignment 5 adds more types of invalid user inputs and edge cases.
Where: CalendarController error branches, MultiCalendarModelImpl validation checks.

## Model - event types reuse

### What stayed the same
- `CalendarEvent`, `EventSeries`, and `RecurrenceRule` are reused from Assignment 4 with minimal changes.
- Event creation, editing, uniqueness rules, and series handling follow the same logic as in Assignment 4.
- Existing methods for checking busy status, printing events, and editing events are reused inside the multi-calendar 
    model by first resolving the active calendar.

### Why
- The Assignment 4 design already captured the core event and recurrence logic.
- Reusing these types avoids duplication and reduces the risk of breaking previously correct behavior.
- Only the calendar "container" layer changed; the event/series layer remains stable.

## Export architecture (CSV + iCal)
What: Added CalendarExporter and CalendarExporterImpl.
Why: Assignment 5 requires exporting a calendar to a file (both csv and ical/ics).
Where: New exporter classes; CalendarController.doExport.

### What changed
- Introduced a `CalendarExporter` interface with methods like:
    - `writeCsv(Path, List<CalendarEvent>, ZoneId)`
    - `writeICal(Path, List<CalendarEvent>, ZoneId)`
- Implemented `CalendarExporterImpl` to handle:
    - CSV export (existing Assignment 4 behavior).
    - New iCal export according to the required format.
- `CalendarController` now receives a `CalendarExporter` in its constructor.
- The `export cal` command:
    - Inspects the file extension (`.csv` or `.ical`/`.ics`).
    - Calls the appropriate exporter method.
    - Prints the absolute path returned by the exporter.

### Why
- Assignment 5 requires supporting both CSV and iCal formats and auto-detecting by filename.
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