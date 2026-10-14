import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Robot;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Drives the real calendar GUI with a robot and records frames for the README demo GIF.
 *
 * <p>Re-record from the project root (Linux, needs Xvfb and ffmpeg):</p>
 * <pre>
 * ./gradlew classes
 * javac -cp build/classes/java/main -d build/demo tools/demo/RecordDemo.java
 * xvfb-run -a -s "-screen 0 1280x800x24" java -Duser.timezone=America/New_York \
 *     -cp build/demo:build/classes/java/main RecordDemo build/demo/frames
 * ffmpeg -framerate 14 -i build/demo/frames/f%05d.png -vf "split[a][b];[a]palettegen=\
 *     max_colors=128:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
 *     -loop 0 docs/images/demo.gif
 * </pre>
 */
public final class RecordDemo {
  private static Robot robot;
  private static Rectangle area;
  private static volatile boolean recording = true;
  private static File outDir;

  public static void main(String[] args) throws Exception {
    outDir = new File(args[0]);
    outDir.mkdirs();
    robot = new Robot();
    robot.setAutoDelay(0);

    CalendarRunner.main(new String[0]);
    JFrame frame = waitFor(() -> {
      for (Window w : Window.getWindows()) {
        if (w instanceof JFrame && w.isShowing()) {
          return (JFrame) w;
        }
      }
      return null;
    });
    SwingUtilities.invokeAndWait(() -> frame.setSize(1120, 760));
    sleep(1500);
    AtomicReference<Rectangle> r = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      Point p = frame.getRootPane().getLocationOnScreen();
      r.set(new Rectangle(p.x, p.y, frame.getRootPane().getWidth(),
          frame.getRootPane().getHeight()));
    });
    area = r.get();
    System.out.println("capture area " + area);
    robot.mouseMove(area.x + 700, area.y + 400);

    Thread recorder = new Thread(RecordDemo::record);
    recorder.setDaemon(true);
    recorder.start();
    try {
      script(frame);
    } catch (Throwable t) {
      t.printStackTrace();
      recording = false;
      System.exit(1);
    }
    recording = false;
    recorder.join();
    System.exit(0);
  }

  private static void script(JFrame frame) throws Exception {
    sleep(1800);

    // 1. Single event on the 15th.
    click(day(frame, 15));
    sleep(500);
    click(button(frame, "Create Event"));
    Window dlg = dialog("Create Event");
    type(field(dlg, "Title:"), "Team Sync");
    type(field(dlg, "Location:"), "Room 4B");
    retype(field(dlg, "Start time (HH:MM):"), "10:00");
    retype(field(dlg, "End time (HH:MM):"), "10:30");
    sleep(400);
    click(button(dlg, "OK"));
    sleep(1800);

    // 2. Recurring standup: Mon/Wed/Fri, 10 times, starting Monday the 19th.
    click(day(frame, 19));
    sleep(500);
    click(button(frame, "Create Recurring Event"));
    dlg = dialog("Create Recurring Event");
    type(field(dlg, "Title:"), "Standup");
    click(button(dlg, "Mon (M)"));
    click(button(dlg, "Wed (W)"));
    click(button(dlg, "Fri (F)"));
    type(field(dlg, "Count (number of occurrences, optional):"), "10");
    retype(field(dlg, "Until date (YYYY-MM-DD, optional):"), "");
    retype(field(dlg, "End time (HH:MM):"), "09:15");
    sleep(400);
    click(button(dlg, "OK"));
    sleep(1500);
    click(day(frame, 23));
    sleep(1500);

    // 3. Second calendar in another time zone, then switch back.
    click(button(frame, "New Calendar"));
    dlg = dialog("Create New Calendar");
    type(field(dlg, "Calendar name:"), "London Office");
    retype(field(dlg, "Time zone ID (e.g., America/New_York):"), "Europe/London");
    sleep(400);
    click(button(dlg, "OK"));
    sleep(2000);
    JComboBox<?> selector = find(frame, c -> c instanceof JComboBox, JComboBox.class);
    click(selector);
    sleep(700);
    clickListItem(frame, "Default");
    sleep(1500);

    // 4. Month navigation: the series continues into next month.
    click(button(frame, ">"));
    sleep(1600);
    click(button(frame, "Today"));
    sleep(2200);
  }

  // ---- recording -------------------------------------------------------------------------------

  private static void record() {
    int i = 0;
    long frameMs = 100;
    long next = System.currentTimeMillis();
    while (recording) {
      BufferedImage img = robot.createScreenCapture(area);
      Point p = MouseInfo.getPointerInfo().getLocation();
      drawCursor(img, p.x - area.x, p.y - area.y);
      try {
        ImageIO.write(img, "png", new File(outDir, String.format("f%05d.png", i++)));
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      next += frameMs;
      long wait = next - System.currentTimeMillis();
      if (wait > 0) {
        sleep(wait);
      }
    }
  }

  private static void drawCursor(BufferedImage img, int x, int y) {
    Graphics2D g = img.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    Polygon arrow = new Polygon(
        new int[] {0, 0, 4, 7, 9, 6, 11},
        new int[] {0, 16, 12, 19, 18, 11, 11}, 7);
    arrow.translate(x, y);
    g.setColor(Color.WHITE);
    g.setStroke(new BasicStroke(2f));
    g.drawPolygon(arrow);
    g.setColor(Color.BLACK);
    g.fillPolygon(arrow);
    g.dispose();
  }

  // ---- input -----------------------------------------------------------------------------------

  private static void moveTo(Point target) {
    Point from = MouseInfo.getPointerInfo().getLocation();
    int steps = 14;
    for (int s = 1; s <= steps; s++) {
      double t = (double) s / steps;
      double e = t * t * (3 - 2 * t);
      robot.mouseMove((int) (from.x + (target.x - from.x) * e),
          (int) (from.y + (target.y - from.y) * e));
      sleep(22);
    }
  }

  private static void click(Component c) throws Exception {
    AtomicReference<Point> center = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      Point p = c.getLocationOnScreen();
      center.set(new Point(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2));
    });
    moveTo(center.get());
    sleep(150);
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    sleep(60);
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    sleep(250);
  }

  private static void clickListItem(Container root, String text) throws Exception {
    JList<?> list = waitFor(() -> findOrNull(root, c -> c instanceof JList && c.isShowing(),
        JList.class));
    AtomicReference<Point> pt = new AtomicReference<>();
    SwingUtilities.invokeAndWait(() -> {
      for (int i = 0; i < list.getModel().getSize(); i++) {
        if (text.equals(String.valueOf(list.getModel().getElementAt(i)))) {
          Rectangle r = list.getCellBounds(i, i);
          Point o = list.getLocationOnScreen();
          pt.set(new Point(o.x + r.x + r.width / 2, o.y + r.y + r.height / 2));
        }
      }
    });
    moveTo(pt.get());
    sleep(200);
    robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
    robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
  }

  private static void type(JTextField f, String text) throws Exception {
    click(f);
    String base = f.getText();
    for (int i = 1; i <= text.length(); i++) {
      String s = base + text.substring(0, i);
      SwingUtilities.invokeAndWait(() -> f.setText(s));
      sleep(70);
    }
    sleep(200);
  }

  private static void retype(JTextField f, String text) throws Exception {
    click(f);
    SwingUtilities.invokeAndWait(() -> f.setText(""));
    sleep(150);
    for (int i = 1; i <= text.length(); i++) {
      String s = text.substring(0, i);
      SwingUtilities.invokeAndWait(() -> f.setText(s));
      sleep(70);
    }
    sleep(200);
  }

  // ---- component lookup ------------------------------------------------------------------------

  private static Window dialog(String title) {
    return waitFor(() -> {
      for (Window w : Window.getWindows()) {
        if (w instanceof java.awt.Dialog && w.isShowing()
            && title.equals(((java.awt.Dialog) w).getTitle())) {
          return w;
        }
      }
      return null;
    });
  }

  private static AbstractButton button(Container root, String text) {
    return waitFor(() -> findOrNull(root,
        c -> c instanceof AbstractButton && c.isShowing()
            && text.equals(((AbstractButton) c).getText()), AbstractButton.class));
  }

  private static AbstractButton day(Container root, int n) {
    String plain = String.valueOf(n);
    String html = ">" + n + "</div>";
    return waitFor(() -> findOrNull(root, c -> {
      if (!(c instanceof AbstractButton) || !c.isShowing()) {
        return false;
      }
      String t = ((AbstractButton) c).getText();
      return plain.equals(t) || (t != null && t.contains(html));
    }, AbstractButton.class));
  }

  /** The text field that directly follows the label with the given text. */
  private static JTextField field(Container root, String label) {
    return waitFor(() -> {
      List<Component> all = new ArrayList<>();
      collect(root, all);
      for (int i = 0; i < all.size(); i++) {
        if (all.get(i) instanceof JLabel && label.equals(((JLabel) all.get(i)).getText())) {
          for (int j = i + 1; j < all.size(); j++) {
            if (all.get(j) instanceof JTextField) {
              return (JTextField) all.get(j);
            }
          }
        }
      }
      return null;
    });
  }

  private static <T> T find(Container root, Predicate<Component> p, Class<T> type) {
    return waitFor(() -> findOrNull(root, p, type));
  }

  private static <T> T findOrNull(Container root, Predicate<Component> p, Class<T> type) {
    List<Component> all = new ArrayList<>();
    collect(root, all);
    for (Component c : all) {
      if (p.test(c)) {
        return type.cast(c);
      }
    }
    return null;
  }

  private static void collect(Container c, List<Component> out) {
    for (Component child : c.getComponents()) {
      out.add(child);
      if (child instanceof Container) {
        collect((Container) child, out);
      }
    }
  }

  private interface Lookup<T> {
    T get() throws Exception;
  }

  private static <T> T waitFor(Lookup<T> lookup) {
    for (int i = 0; i < 100; i++) {
      try {
        AtomicReference<T> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
          try {
            ref.set(lookup.get());
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        if (ref.get() != null) {
          return ref.get();
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      sleep(100);
    }
    throw new IllegalStateException("component not found");
  }

  private static void sleep(long ms) {
    try {
      Thread.sleep(ms);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private RecordDemo() {
  }

  static {
    try {
      new Robot();
    } catch (AWTException e) {
      throw new ExceptionInInitializerError(e);
    }
  }
}
