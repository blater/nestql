package blater.nestql;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpTest {
  @Test
  void shortAndLongHelpPrintTheManPage() throws Exception {
    String shortHelp = captureStdout(() -> Main.main("-h"));
    String longHelp = captureStdout(() -> Main.main("--help"));

    assertEquals(shortHelp, longHelp);
    assertTrue(longHelp.startsWith("NESTQL(1)"));
    assertTrue(longHelp.contains("\nSYNOPSIS\n"));
    assertTrue(longHelp.contains("--help <topic>"));
  }

  @Test
  void helpOnHelpListsAvailableTopics() throws Exception {
    String output = captureStdout(() -> Main.main("--help", "help"));

    assertTrue(output.startsWith("HELP\n"));
    assertTrue(output.contains("AVAILABLE HELP TOPICS"));
    assertTrue(output.contains("nestql --help query"));
    assertTrue(output.contains("clear-cache"));
  }

  @Test
  void commandHelpPrintsFocusedTopic() throws Exception {
    String output = captureStdout(() -> Main.main("--help", "query"));

    assertTrue(output.startsWith("QUERY\n"));
    assertTrue(output.contains("nestql <script-file-or-text>"));
    assertTrue(output.contains("nestql --help connection"));
  }

  @Test
  void equalsFormPrintsFocusedTopic() throws Exception {
    String output = captureStdout(() -> Main.main("--help=output"));

    assertTrue(output.startsWith("OUTPUT\n"));
    assertTrue(output.contains("--output <type>"));
  }

  @Test
  void helpShortCircuitsOtherCommandLineProcessing() throws Exception {
    String output = captureStdout(() -> Main.main(
        "-p", "/file/that/does/not/exist.properties", "--help", "cache"));

    assertTrue(output.startsWith("CACHE\n"));
  }

  @Test
  void unknownTopicPointsToTopicListing() throws Exception {
    String output = captureStdout(() -> Main.main("--help", "unknown"));

    assertTrue(output.startsWith("Unknown help topic: unknown"));
    assertTrue(output.contains("nestql --help help"));
  }

  private String captureStdout(ThrowingRunnable runnable) throws Exception {
    PrintStream original = System.out;
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
      System.setOut(capture);
      runnable.run();
    } finally {
      System.setOut(original);
    }
    return output.toString(StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
