# Usage Guide

## Building and running

From the project root:

```bash
./gradlew jar          # builds build/libs/calendar-1.0.jar (Windows: gradlew.bat jar)
```

| Mode | Command |
| --- | --- |
| GUI | `java -jar build/libs/calendar-1.0.jar` |
| Interactive text mode | `java -jar build/libs/calendar-1.0.jar --mode interactive` |
| Headless (run a script) | `java -jar build/libs/calendar-1.0.jar --mode headless examples/commands.txt` |

You can also run any mode through Gradle without building the JAR:

```bash
./gradlew run --args="--mode interactive"
./gradlew run --args="--mode headless examples/commands.txt"
```

The program prints a usage message and exits if it gets any other arguments.

---

## Using the GUI

The GUI opens when you run the JAR with no arguments.

![Month view](images/gui-month-view.png)

### Layout

- **Top bar**
  - **Calendar** dropdown: picks the active calendar.
  - **New Calendar**: creates a calendar in a time zone you choose.
  - **<**, **Today**, **>**: move between months.
  - The header shows `<Calendar name> (<Zone>) - <Month> <Year>`.
- **Center**
  - Month grid on the left. Each day is a button, the selected day is highlighted, and a dot
    marks days that have events.
  - Event list on the right, showing the selected day's events as
    `hh:mm AM - hh:mm PM  Title`, or `... Title (All day)` for all-day events.
- **Bottom bar**: **Create Event**, **Create Recurring Event**, **Event Editor**,
  **Delete Selected Event**.

### Calendars

- **Default calendar.** On launch, a calendar named `Default` is created in your system's
  time zone, so you can start adding events right away.
- **New calendar.** Click **New Calendar**, enter a name, choose a time zone (for example
  `America/Los_Angeles`) and press **OK**. The new calendar is selected automatically.
- **Switching.** Choose a calendar from the dropdown. The grid and event list update to show
  that calendar's events in its time zone.

### Viewing events

Click a day in the grid. The list on the right shows every event on that day, in the active
calendar's time zone.

### Creating a single event

1. Select a day, then click **Create Event**.
2. Fill in the dialog:
   - **Title** (required), **Description** and **Location** (optional).
   - **Date**: starts as the selected day.
   - **Start / End**: 24-hour `HH:MM`, for example `09:00` or `17:30`.
   - **All day**: greys out the time fields and stores the event as 08:00 to 17:00.
   - **Privacy**: Public or Private.
3. Click **OK**. If something is wrong (no title, end before start, a bad time format, or a
   clash with another event), an error dialog explains what to fix.

![Create event dialog](images/gui-create-event.png)

### Creating a recurring event

1. Select the first day of the series, then click **Create Recurring Event**.
2. Fill in the same fields as for a single event, plus:
   - **Weekdays**: tick each day of the week the event repeats on.
   - **Repeat limit**: either a number of occurrences, or an **until** date picked from the
     mini calendar. You can't set both.
3. Click **OK**. An event is created on every matching date. All of them share a series ID,
   so you can edit them together later.

### Editing events

1. Select a day, select an event in the list, and click **Event Editor**.
2. Change the title, description, location, date, start/end time or privacy.
3. Choose a scope:
   - **This event only**: changes just the selected occurrence.
   - **This and after**: changes this occurrence and every later one in the series.
   - **Entire series**: changes every occurrence in the series. For an event that isn't part
     of a series, this changes every event with the same name.

If you change the date, every affected occurrence moves by the same number of days. If any
of them would then clash with another event, nothing is changed and an error is shown.

### Deleting an event

Select the event and click **Delete Selected Event**, then confirm. This deletes only the
selected occurrence.

---

## Command language (interactive and headless modes)

Dates are `YYYY-MM-DD` and date-times are `YYYY-MM-DDThh:mm`. Weekday letters are
`M T W R F S U` (R = Thursday, U = Sunday).

### Calendars

```text
create calendar --name <name> --timezone <Area/Location>
edit calendar --name <name> --property name <newName>
edit calendar --name <name> --property timezone <Area/Location>
use calendar --name <name>
```

### Creating events

```text
create event "<title>" from <start> to <end>
create event "<title>" on <date>                                   # all-day
create event "<title>" from <start> to <end> repeats <days> for <N> times
create event "<title>" from <start> to <end> repeats <days> until <date>
create event "<title>" on <date> repeats <days> for <N> times
create event "<title>" on <date> repeats <days> until <date>
```

Add `--privacy private` anywhere after the title to mark the event private (the default is
`--privacy public`). Private events stay private when edited or copied, and are exported with
`Private=True` in CSV and `CLASS:PRIVATE` in iCalendar.

### Editing events

`<property>` is one of `subject`, `start`, `end`, `description`, `location`, `status`.

```text
edit event  <property> "<title>" from <start> to <end> with <value>   # one occurrence
edit events <property> "<title>" from <start> with <value>            # this and later
edit series <property> "<title>" from <start> with <value>            # whole series
```

### Deleting events

```text
delete event --name "<title>" --start <dateTime>                      # one occurrence
delete series --name "<title>" --start <dateTime>                     # this and later in the series
```

### Querying

```text
print events on <date>
print events from <start> to <end>
show status on <dateTime>                                             # busy / available
```

### Copying between calendars

Times are converted from the source calendar's time zone to the target's.

```text
copy event "<title>" on <dateTime> --target <calendar> to <dateTime>
copy events on <date> --target <calendar> to <date>
copy events between <date> and <date> --target <calendar> to <date>
```

### Exporting

The file extension picks the format: `.csv` (Google Calendar CSV) or `.ics` / `.ical`
(iCalendar). The program prints the absolute path of the file it wrote.

```text
export cal <file.csv | file.ics | file.ical>
```

### Exiting

```text
exit
```

### Examples

- [`examples/commands.txt`](../examples/commands.txt): a full walkthrough of every command.
  Its output files are in [`examples/sample-exports/`](../examples/sample-exports).
- [`examples/invalid.txt`](../examples/invalid.txt): malformed commands. Each one prints
  `Error: <message>` and the program keeps going.
