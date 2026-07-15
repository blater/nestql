package blater.nestql.inputreader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InputTypeTest {
  @Test
  void determinesInputTypeFromFilenameExtension() {
    assertEquals(InputType.XML, InputType.fromFilename("input.xml"));
    assertEquals(InputType.JSON, InputType.fromFilename("input.json"));
    assertEquals(InputType.JSON, InputType.fromFilename("INPUT.JSON"));
    assertEquals(InputType.YAML, InputType.fromFilename("input.yaml"));
    assertEquals(InputType.YAML, InputType.fromFilename("input.yml"));
    assertEquals(InputType.YAML, InputType.fromFilename("INPUT.YAML"));
    assertEquals(InputType.CSV, InputType.fromFilename("input.csv"));
    assertEquals(InputType.CSV, InputType.fromFilename("INPUT.CSV"));
    assertEquals(InputType.PARQUET, InputType.fromFilename("input.parquet"));
    assertEquals(InputType.PARQUET, InputType.fromFilename("INPUT.PARQUET"));
  }

  @Test
  void keepsEmptyFilenameCompatibleWithEmptyXmlHierarchyBehavior() {
    assertEquals(InputType.XML, InputType.fromFilename(""));
  }

  @Test
  void rejectsUnknownInputFileExtension() {
    assertThrows(IllegalArgumentException.class, () -> InputType.fromFilename("input.txt"));
  }
}
