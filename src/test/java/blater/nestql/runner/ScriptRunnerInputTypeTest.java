package blater.nestql.runner;

import blater.nestql.inputreader.InputType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScriptRunnerInputTypeTest {
  @Test
  void determinesInputTypeFromFilenameExtension() {
    assertEquals(InputType.XML, ScriptRunner.inputTypeFor("input.xml"));
    assertEquals(InputType.JSON, ScriptRunner.inputTypeFor("input.json"));
    assertEquals(InputType.JSON, ScriptRunner.inputTypeFor("INPUT.JSON"));
    assertEquals(InputType.YAML, ScriptRunner.inputTypeFor("input.yaml"));
    assertEquals(InputType.YAML, ScriptRunner.inputTypeFor("input.yml"));
    assertEquals(InputType.YAML, ScriptRunner.inputTypeFor("INPUT.YAML"));
    assertEquals(InputType.CSV, ScriptRunner.inputTypeFor("input.csv"));
    assertEquals(InputType.CSV, ScriptRunner.inputTypeFor("INPUT.CSV"));
    assertEquals(InputType.PARQUET, ScriptRunner.inputTypeFor("input.parquet"));
    assertEquals(InputType.PARQUET, ScriptRunner.inputTypeFor("INPUT.PARQUET"));
  }

  @Test
  void keepsEmptyFilenameCompatibleWithEmptyXmlHierarchyBehavior() {
    assertEquals(InputType.XML, ScriptRunner.inputTypeFor(""));
  }

  @Test
  void rejectsUnknownInputFileExtension() {
    assertThrows(IllegalArgumentException.class, () -> ScriptRunner.inputTypeFor("input.txt"));
  }
}
