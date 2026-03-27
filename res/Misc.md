# Miscellaneous Notes – Assignment 6
All required functionality, program modes, and design constraints from the assignment specification have been fully satisfied.
The GUI is responsive, user-friendly, and consistent with the underlying model.
Documentation, interfaces, exceptions, and access modifiers have been updated to address prior grading feedback.

## 1. Design Changes Since Assignment 5
This assignment adds a complete Swing-based GUI while preserving the text and headless modes 
from Assignment 5. The key design goal was to integrate a graphical interface without modifying 
or polluting the model, and while keeping controllers and views clearly separated.

### New GUI Architecture
Added GuiView interface and a Swing implementation SwingCalendarView.
Added GuiFeatures interface defining all controller actions available to the GUI.
Added GuiCalendarController that handles GUI behavior separately from the text controller.
Updated CalendarRunner:
No arguments → launch GUI
--mode interactive → CLI
--mode headless <file> → script execution
This keeps each mode independent and preserves the Assignment 5 structure.

### MVC Separation Improvements
- Model: unchanged, pure business logic, no Swing or IO.
- View: responsible only for displaying months, events, dialogs, and error messages.
- Controller: performs navigation and all user-driven actions.
This satisfies both MVC and SOLID (especially SRP and ISP).

### Multiple Calendars & Time Zones
GUI includes:
- Calendar dropdown
- "New Calendar" button
- Automatic timezone handling via model API
- Header shows the active calendar’s name and timezone.

### Month View Enhancements
- Interactive month grid of buttons.
- Navigation arrows and "Today".
- Month header like:
Default (America/New_York) – NOVEMBER 2025
- Selected date is highlighted.
- Days with events display a subtle indicator dot.

### Event Creation
- "Create Event" dialog:
    - Uses selected day automatically.
    - Title, description, location, start time, end time, privacy.
    - "All day" locks times at 8:00–17:00 and greys out fields.
- UI avoids unnecessary typing to meet the "user-friendly interactions" requirement.
  Recurring Events

### "Create Recurring Event" dialog includes:
- Weekday checkboxes
    - Either count or until date (never both)
    - Mini date picker for selecting the until date
    - All-day and privacy options
- Recurrence uses RecurrenceRule, Weekday, and consistent seriesId for all generated events.

### Event Editing
Select a date -> select an event from the list → edit dialog.
User may edit:
Title
Description
Location
Start/end time
Date
Privacy
Edit scopes supported:
This event only
This and after
Entire series
When the date changes:
All selected events shift by the same number of days.
Series edits use seriesId so unrelated one-off events with the same title are not affected.
Non-series events fall back to name-based matching, satisfying the requirement to "edit multiple events with the same name".

### Delete Event (Extra)
Added a simple "Delete Selected Event" button.
Not required by the assignment, but improves usability.
Confirms before deleting.
Event Display Improvements
Event list now shows:
09:00 AM – 10:00 AM  Meeting
or for all-day:
08:00 AM – 05:00 PM  Holiday (All day)
Far more readable than raw toString() formatting.

## 2. Features Implemented vs Assignment Requirements

### Implemented

- **Multiple modes of execution**
    - `--mode headless <script>`: reads and executes command files line-by-line using the existing text controller.
    - `--mode interactive`: provides the CLI interface exactly as in Assignment 5.
    - No arguments: launches the Swing GUI with a default calendar.

- **Calendars & time zones**
    - A default calendar is created in the system time zone on startup.
    - User can create additional calendars with arbitrary `ZoneId` values.
    - GUI clearly shows the active calendar name and zone in the header.

- **Month view & navigation**
    - Month grid for the active calendar.
    - Header shows `<Calendar Name> (Zone) – MONTH YEAR`.
    - Buttons: previous month, today, next month.

- **Day selection & viewing events**
    - Clicking a date selects it and refreshes the right-hand event list for that day.
    - Days with events show a dot marker in the month grid.

- **Create single event**
    - Uses selected date by default but allows changing date and times.
    - Inputs: title (required), description, location, start time, end time, privacy (Public/Private), and All-day checkbox.
    - Basic validation: non-empty title; start < end; user-friendly error messages.

- **Create recurring event**
    - Uses selected date as base.
    - Supports:
        - Weekday selection (M–Sun).
        - Either occurrence count or end date (via mini date picker).
        - All-day behavior and privacy.
    - Series share a `seriesId` in the model.

- **Edit events**
    - User selects a date and then an event from the list.
    - Edit dialog allows updating title, description, location, privacy, time and date.
    - Edit scope options:
        - This event only
        - This and after
        - Entire series
    - Series edits:
        - If the original belongs to a series (`seriesId` present), edits apply only to that series, not one-off events with the same name.
        - If standalone (no `seriesId`), scope operations fall back to name-based matching.
        - When the date is changed, all targets are shifted by the same number of days.
        - Conflicts with non-series events cause a rollback and an error message.

- **Delete event (extra)**
    - Deletion of the selected event after a confirmation dialog.
    - View refreshes to keep the selected day.

- **Graceful error handling**
    - All controllers capture `IllegalArgumentException` and related runtime errors and route them through `view.showError` or CLI messages.
    - Error messages are user-facing and do not expose stack traces or internal state.

- **Code structure & style**
    - Model, view, and controllers remain separated.
    - Text and GUI controllers are isolated behind interfaces (`TextController`, `GuiFeatures`, `GuiView`).
    - No Swing code in the model; no persistence or business logic in the view.
    - Access modifiers and Checkstyle-oriented layout followed across new files.

### Not Implemented / Known Limitations

- **No persistence across runs**  
  Events are stored in memory only; exiting the program loses the current calendar state. (A TA mentioned that events “must be saved”; here that means all successful create/edit operations are immediately reflected in the in-memory model, not that they are written to disk.)

- **GUI testing**  
  GUI components are not tested with automated UI tests; instead, logic is covered by controller/model tests, and GUI behavior is manually verified.

- **No advanced views**  
  Weekly/daily agenda views and drag-and-drop editing are not implemented. The primary view is month-based with a per-day event list.

If needed, we can document any additional limitations here.

## 3. Notes for Graders

- **Please test all three modes**:
    - Headless with a script file.
    - Interactive CLI.
    - Swing GUI by running the JAR with no arguments.

- **Event editing behavior**:
    - “This and after” and “Entire series” use `seriesId` when present to avoid accidentally editing unrelated one-off events with the same name.
    - Date edits apply a consistent day-shift to all affected occurrences.
    - Internally, "This and after" and "Entire series" operate on events that have the same name and belong to the same series (via seriesId when present). This still satisfies the requirement that "multiple events with the same name… can be edited together," while avoiding surprising edits to unrelated events that merely share the same title.

- **Delete feature**:
    - The delete button is an extra feature and not required by the assignment, but it should work reliably for single-event deletion.

## 4. Pattern choice

- The application is structured primarily as an MVC design. The CalendarModel and its implementation 
encapsulate all calendar and event logic, the CLI and Swing classes implement views (CliView, 
GuiView/SwingCalendarView), and CalendarController / GuiCalendarController act as controllers that 
interpret user input and update the model and view. 
- MVC was chosen over variants like MVP or MVVM because we need to support multiple views (headless, interactive text, GUI) on top of a shared model, 
and because Swing and the course materials are already aligned with MVC.
- Introducing additional patterns such as full Command or Presenter layers would have added extra indirection without a clear benefit at 
this scale.