import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import calendar.controller.CalendarController;
import calendar.controller.GuiCalendarController;
import calendar.controller.GuiFeatures;
import calendar.model.MultiCalendarModelImpl;
import calendar.model.entity.CalendarEvent;
import calendar.model.entity.EditScope;
import calendar.model.entity.PrivacyStatus;
import calendar.model.entity.Weekday;
import calendar.view.EventView;
import calendar.view.GuiView;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

/**
 * Tests added after the course. Most close gaps found by PIT mutation testing: behavior
 * that the original suite exercised but never checked, so a mutant could remove it
 * unnoticed. The privacy tests cover a fix to text mode, which used to drop the
 * {@code --privacy} flag and reset events to public when they were edited or copied.
 *
 * <p>Unlike most of the coursework tests, these run against the real
 * {@link MultiCalendarModelImpl} and assert on the resulting model state, not only on the
 * controller's printed messages.</p>
 */
public class ControllerMutationGapTest {
  private static final ZoneId NY = ZoneId.of("America/New_York");

  private MultiCalendarModelImpl model;
  private RecordingView view;
  private CalendarController text;

  /** Fresh model, recording view and text controller for each test. */
  @Before
  public void setUp() {
    model = new MultiCalendarModelImpl();
    view = new RecordingView();
    text = new CalendarController(model, view);
  }

  // ---- text controller ---------------------------------------------------------------------

  @Test
  public void useCalendarSwitchesTheActiveCalendar() {
    run("create calendar --name Work --timezone America/New_York",
        "create calendar --name Home --timezone Europe/London",
        "use calendar --name Home",
        "use calendar --name Work");

    assertTrue(view.output.contains("Using calendar: Home (Europe/London)"));
    assertTrue(view.output.contains("Using calendar: Work (America/New_York)"));
    assertEquals("Work", model.currentCalendarName());
  }

  @Test
  public void copyEventsOnDateCopiesIntoTheTargetCalendar() {
    run("create calendar --name Work --timezone America/New_York",
        "create calendar --name Home --timezone America/New_York",
        "use calendar --name Work",
        "create event \"Review\" from 2025-03-10T10:00 to 2025-03-10T11:00",
        "copy events on 2025-03-10 --target Home to 2025-03-12",
        "use calendar --name Home");

    List<CalendarEvent> copied = eventsOn(LocalDate.of(2025, 3, 12));
    assertEquals(1, copied.size());
    assertEquals("Review", copied.get(0).name());
    assertEquals(LocalDateTime.of(2025, 3, 12, 10, 0), copied.get(0).start());
  }

  @Test
  public void copyEventsBetweenCopiesTheWholeRangeIntoTheTargetCalendar() {
    run("create calendar --name Work --timezone America/New_York",
        "create calendar --name Home --timezone America/New_York",
        "use calendar --name Work",
        "create event \"Mon\" from 2025-03-10T09:00 to 2025-03-10T10:00",
        "create event \"Tue\" from 2025-03-11T09:00 to 2025-03-11T10:00",
        "copy events between 2025-03-10 and 2025-03-11 --target Home to 2025-04-07",
        "use calendar --name Home");

    assertEquals("Mon", eventsOn(LocalDate.of(2025, 4, 7)).get(0).name());
    assertEquals("Tue", eventsOn(LocalDate.of(2025, 4, 8)).get(0).name());
  }

  @Test
  public void editEventsStoresTheValueAfterWith() {
    run("create calendar --name Work --timezone America/New_York",
        "use calendar --name Work",
        "create event \"Standup\" from 2025-01-06T09:00 to 2025-01-06T09:15 "
            + "repeats MWF for 3 times",
        "edit events location \"Standup\" from 2025-01-08T09:00 with Zoom");

    assertEquals("", eventsOn(LocalDate.of(2025, 1, 6)).get(0).location());
    assertEquals("Zoom", eventsOn(LocalDate.of(2025, 1, 8)).get(0).location());
    assertEquals("Zoom", eventsOn(LocalDate.of(2025, 1, 10)).get(0).location());
  }

  @Test
  public void editSeriesWithEmptyQuotesClearsTheValue() {
    run("create calendar --name Work --timezone America/New_York",
        "use calendar --name Work",
        "create event \"Standup\" from 2025-01-06T09:00 to 2025-01-06T09:15 "
            + "repeats MW for 2 times",
        "edit series location \"Standup\" from 2025-01-06T09:00 with Room1",
        "edit series location \"Standup\" from 2025-01-06T09:00 with \"\"");

    assertEquals("", eventsOn(LocalDate.of(2025, 1, 6)).get(0).location());
    assertEquals("", eventsOn(LocalDate.of(2025, 1, 8)).get(0).location());
  }

  @Test
  public void helpListsEveryCommand() {
    run("help");

    for (String cmd : Arrays.asList(
        "Commands:",
        "  create calendar --name <calName> --timezone <Area/Location>",
        "  edit calendar --name <calName> --property name <newName>",
        "  edit calendar --name <calName> --property timezone <Area/Location>",
        "  use calendar --name <calName>",
        "  create event \"Title\" from YYYY-MM-DDThh:mm to YYYY-MM-DDThh:mm",
        "  create event \"Title\" on YYYY-MM-DD",
        "  create event ... repeats MTWRFSU for N times",
        "  create event ... repeats MTWRFSU until YYYY-MM-DD",
        "  edit event subject|description|location|start \"Title\" from <start> [to <end>] "
            + "with <value>",
        "  edit events subject|location \"Title\" from <start> with <value>",
        "  print events on YYYY-MM-DD",
        "  show status on YYYY-MM-DDThh:mm",
        "  copy event \"Title\" on <start> --target <cal> to <YYYY-MM-DDThh:mm>",
        "  copy events on <YYYY-MM-DD> --target <cal> to <YYYY-MM-DD>",
        "  copy events between <YYYY-MM-DD> and <YYYY-MM-DD> --target <cal> to <YYYY-MM-DD>")) {
      assertTrue("help is missing: " + cmd, view.output.contains(cmd));
    }
  }

  @Test
  public void interactiveModeReportsUnexpectedErrorsAndKeepsGoing() {
    view.input.add("boom");
    view.input.add("exit");
    view.failOn = "boom";

    text.runInteractive();

    assertTrue(view.output.contains("Error: read failed"));
    assertEquals("bye", view.output.get(view.output.size() - 1));
  }

  // ---- privacy in text mode -----------------------------------------------------------------

  @Test
  public void privacyFlagIsStoredForTimedAllDayAndRecurringEvents() {
    run("create calendar --name Work --timezone America/New_York",
        "use calendar --name Work",
        "create event \"Doctor\" from 2025-05-01T10:00 to 2025-05-01T11:00 --privacy private",
        "create event \"Vacation\" --privacy PRIVATE on 2025-05-02",
        "create event \"Therapy\" from 2025-05-05T17:00 to 2025-05-05T18:00 "
            + "repeats M for 2 times --privacy private",
        "create event \"Lunch\" from 2025-05-01T12:00 to 2025-05-01T13:00 --privacy public",
        "create event \"Gym\" from 2025-05-01T18:00 to 2025-05-01T19:00");

    assertEquals(PrivacyStatus.PRIVATE, find("Doctor", 2025, 5, 1).privacy());
    assertEquals(PrivacyStatus.PRIVATE, find("Vacation", 2025, 5, 2).privacy());
    assertEquals(PrivacyStatus.PRIVATE, find("Therapy", 2025, 5, 5).privacy());
    assertEquals(PrivacyStatus.PRIVATE, find("Therapy", 2025, 5, 12).privacy());
    assertEquals(PrivacyStatus.PUBLIC, find("Lunch", 2025, 5, 1).privacy());
    assertEquals(PrivacyStatus.PUBLIC, find("Gym", 2025, 5, 1).privacy());
    assertTrue(view.output.stream().noneMatch(line -> line.startsWith("Error")));
  }

  @Test
  public void editsAndCopiesKeepAnEventPrivate() {
    run("create calendar --name Work --timezone America/New_York",
        "create calendar --name Home --timezone Europe/London",
        "use calendar --name Work",
        "create event \"Doctor\" from 2025-05-01T10:00 to 2025-05-01T11:00 --privacy private",
        "edit event location \"Doctor\" from 2025-05-01T10:00 to 2025-05-01T11:00 with Clinic",
        "edit event start \"Doctor\" from 2025-05-01T10:00 with 2025-05-01T09:30",
        "copy event \"Doctor\" on 2025-05-01T09:30 --target Home to 2025-05-08T09:30",
        "copy events on 2025-05-01 --target Home to 2025-05-15",
        "copy events between 2025-05-01 and 2025-05-01 --target Home to 2025-05-22");

    CalendarEvent edited = find("Doctor", 2025, 5, 1);
    assertEquals("Clinic", edited.location());
    assertEquals(PrivacyStatus.PRIVATE, edited.privacy());
    run("use calendar --name Home");
    assertEquals(PrivacyStatus.PRIVATE, find("Doctor", 2025, 5, 8).privacy());
    assertEquals(PrivacyStatus.PRIVATE, find("Doctor", 2025, 5, 15).privacy());
    assertEquals(PrivacyStatus.PRIVATE, find("Doctor", 2025, 5, 22).privacy());
  }

  @Test
  public void exportMarksPrivateEvents() throws Exception {
    Path dir = Files.createTempDirectory("privacy-export");
    Path csv = dir.resolve("out.csv");
    Path ics = dir.resolve("out.ics");
    run("create calendar --name Work --timezone UTC",
        "use calendar --name Work",
        "create event \"Doctor\" from 2025-05-01T10:00 to 2025-05-01T11:00 --privacy private",
        "create event \"Lunch\" from 2025-05-01T12:00 to 2025-05-01T13:00",
        "export cal " + csv,
        "export cal " + ics);

    List<String> rows = Files.readAllLines(csv);
    assertTrue(rows.stream().anyMatch(r -> r.startsWith("Doctor,") && r.endsWith(",True")));
    assertTrue(rows.stream().anyMatch(r -> r.startsWith("Lunch,") && r.endsWith(",False")));
    String ical = Files.readString(ics);
    assertEquals(1, ical.split("CLASS:PRIVATE", -1).length - 1);
    String doctor = ical.substring(ical.indexOf("SUMMARY:Doctor"));
    assertTrue(doctor.indexOf("CLASS:PRIVATE") < doctor.indexOf("END:VEVENT"));
  }

  // ---- GUI controller ----------------------------------------------------------------------

  @Test
  public void guiEditThisAndAfterKeepsTheEditedEventsAndSelectsTheirNewDay() {
    RecordingGuiView gui = new RecordingGuiView();
    GuiCalendarController ctl = guiController(gui);
    ctl.createRecurringEvent(at(2025, 1, 6, 9), at(2025, 1, 6, 10), "Gym", "", "", false,
        EnumSet.of(Weekday.M), 3, null, PrivacyStatus.PUBLIC);
    CalendarEvent second = eventsOn(LocalDate.of(2025, 1, 13)).get(0);
    ctl.selectDay(LocalDate.of(2025, 2, 1));

    ctl.editEvent(second, renamed(second, "Swim", 1), EditScope.THIS_AND_AFTER);

    assertEquals("Gym", eventsOn(LocalDate.of(2025, 1, 6)).get(0).name());
    assertEquals("Swim", eventsOn(LocalDate.of(2025, 1, 14)).get(0).name());
    assertEquals("Swim", eventsOn(LocalDate.of(2025, 1, 21)).get(0).name());
    assertEquals(LocalDate.of(2025, 1, 14), gui.selectedDay);
  }

  @Test
  public void guiEditEntireSeriesKeepsTheEditedEventsAndSelectsTheirNewDay() {
    RecordingGuiView gui = new RecordingGuiView();
    GuiCalendarController ctl = guiController(gui);
    ctl.createRecurringEvent(at(2025, 1, 6, 9), at(2025, 1, 6, 10), "Gym", "", "", false,
        EnumSet.of(Weekday.M), 2, null, PrivacyStatus.PUBLIC);
    CalendarEvent first = eventsOn(LocalDate.of(2025, 1, 6)).get(0);
    ctl.selectDay(LocalDate.of(2025, 2, 1));

    ctl.editEvent(first, renamed(first, "Swim", 2), EditScope.ENTIRE_SERIES);

    assertEquals("Swim", eventsOn(LocalDate.of(2025, 1, 8)).get(0).name());
    assertEquals("Swim", eventsOn(LocalDate.of(2025, 1, 15)).get(0).name());
    assertTrue(eventsOn(LocalDate.of(2025, 1, 6)).isEmpty());
    assertEquals(LocalDate.of(2025, 1, 8), gui.selectedDay);
  }

  @Test
  public void guiEditThisAndAfterRestoresTheSeriesWhenTheEditWouldClash() {
    RecordingGuiView gui = new RecordingGuiView();
    GuiCalendarController ctl = guiController(gui);
    ctl.createRecurringEvent(at(2025, 1, 6, 9), at(2025, 1, 6, 10), "Gym", "", "", false,
        EnumSet.of(Weekday.M), 2, null, PrivacyStatus.PUBLIC);
    ctl.createSingleEvent(at(2025, 1, 14, 9), at(2025, 1, 14, 10), "Dentist", "", "", false,
        PrivacyStatus.PUBLIC);
    CalendarEvent first = eventsOn(LocalDate.of(2025, 1, 6)).get(0);

    ctl.editEvent(first, renamed(first, "Gym", 1), EditScope.THIS_AND_AFTER);

    assertTrue(gui.error != null);
    assertEquals("Gym", eventsOn(LocalDate.of(2025, 1, 6)).get(0).name());
    assertEquals("Gym", eventsOn(LocalDate.of(2025, 1, 13)).get(0).name());
    assertEquals(1, eventsOn(LocalDate.of(2025, 1, 14)).size());
  }

  @Test
  public void guiEditEntireSeriesRestoresTheSeriesWhenTheEditWouldClash() {
    RecordingGuiView gui = new RecordingGuiView();
    GuiCalendarController ctl = guiController(gui);
    ctl.createRecurringEvent(at(2025, 1, 6, 9), at(2025, 1, 6, 10), "Gym", "", "", false,
        EnumSet.of(Weekday.M), 2, null, PrivacyStatus.PUBLIC);
    ctl.createSingleEvent(at(2025, 1, 14, 9), at(2025, 1, 14, 10), "Dentist", "", "", false,
        PrivacyStatus.PUBLIC);
    CalendarEvent first = eventsOn(LocalDate.of(2025, 1, 6)).get(0);

    ctl.editEvent(first, renamed(first, "Gym", 1), EditScope.ENTIRE_SERIES);

    assertTrue(gui.error != null);
    assertEquals("Gym", eventsOn(LocalDate.of(2025, 1, 6)).get(0).name());
    assertEquals("Gym", eventsOn(LocalDate.of(2025, 1, 13)).get(0).name());
    assertEquals(1, eventsOn(LocalDate.of(2025, 1, 14)).size());
  }

  @Test
  public void guiRecurringEventWithZeroCountUsesTheUntilDate() {
    RecordingGuiView gui = new RecordingGuiView();
    GuiCalendarController ctl = guiController(gui);

    ctl.createRecurringEvent(at(2025, 1, 6, 9), at(2025, 1, 6, 10), "Gym", "", "", false,
        EnumSet.of(Weekday.M), 0, LocalDate.of(2025, 1, 20), PrivacyStatus.PUBLIC);

    assertEquals(null, gui.error);
    assertEquals(3, model.listEvents(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 31))
        .size());
  }

  // ---- helpers -----------------------------------------------------------------------------

  private void run(String... commands) {
    List<String> script = new ArrayList<>(Arrays.asList(commands));
    script.add("exit");
    text.runHeadless(script);
  }

  private CalendarEvent find(String name, int y, int m, int d) {
    return eventsOn(LocalDate.of(y, m, d)).stream()
        .filter(ev -> ev.name().equals(name))
        .findFirst()
        .orElseThrow(() -> new AssertionError("no '" + name + "' on " + y + "-" + m + "-" + d));
  }

  private GuiCalendarController guiController(RecordingGuiView gui) {
    GuiCalendarController ctl = new GuiCalendarController(model, gui);
    ctl.createCalendar("Personal", NY);
    ctl.useCalendar("Personal");
    return ctl;
  }

  private List<CalendarEvent> eventsOn(LocalDate day) {
    return model.listEvents(day, day);
  }

  private static LocalDateTime at(int y, int m, int d, int h) {
    return LocalDateTime.of(y, m, d, h, 0);
  }

  /** A copy of {@code ev} with a new name, moved later by {@code days} days. */
  private static CalendarEvent renamed(CalendarEvent ev, String name, int days) {
    return new CalendarEvent(name, ev.description(), ev.location(),
        ev.start().plusDays(days), ev.end().plusDays(days),
        ev.recurrence(), ev.seriesId(), ev.privacy());
  }

  /** Text view that records output and replays scripted input. */
  private static final class RecordingView implements EventView {
    final List<String> output = new ArrayList<>();
    final Deque<String> input = new ArrayDeque<>();
    String failOn;

    @Override
    public void println(String s) {
      output.add(s);
    }

    @Override
    public void print(String s) {
      // The interactive prompt; not needed for assertions.
    }

    @Override
    public void error(String message) {
      output.add("Error: " + message);
    }

    @Override
    public String readLine() {
      String line = input.poll();
      if (line != null && line.equals(failOn)) {
        throw new IllegalStateException("read failed");
      }
      return line;
    }
  }

  /** GUI view that remembers the last state it was asked to show. */
  private static final class RecordingGuiView implements GuiView {
    LocalDate selectedDay;
    String error;

    @Override
    public void setFeatures(GuiFeatures features) {
      // Not used.
    }

    @Override
    public void showMonth(YearMonth month, LocalDate selectedDay,
                          List<CalendarEvent> eventsForDay, String calendarName,
                          ZoneId calendarZone) {
      this.selectedDay = selectedDay;
    }

    @Override
    public void showError(String message) {
      error = message;
    }

    @Override
    public void showInfo(String message) {
      // Not used.
    }

    @Override
    public void setVisible(boolean visible) {
      // Not used.
    }
  }
}
