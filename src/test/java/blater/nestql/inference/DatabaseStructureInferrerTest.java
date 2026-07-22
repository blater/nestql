package blater.nestql.inference;

import blater.nestql.testsupport.H2Database;
import org.junit.jupiter.api.Test;

import java.util.List;

import static blater.nestql.inference.DatabaseStructure.KeyEvidence.LOGICAL_LINK_KEY;
import static blater.nestql.inference.DatabaseStructure.KeyEvidence.PRIMARY_KEY;
import static blater.nestql.inference.DatabaseStructure.KeyEvidence.UNIQUE_INDEX;
import static blater.nestql.inference.DatabaseStructure.RelationshipEvidence.DECLARED_FOREIGN_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseStructureInferrerTest {
  @Test
  void targetIdentityIncludesVisibleUserButNoCredentialSecret() throws Exception {
    try (H2Database database = new H2Database()) {
      DatabaseTargetIdentity identity = DatabaseTargetIdentity.from(database.connection());

      assertTrue(identity.identityText().contains("user=SA"));
      assertFalse(identity.identityText().toLowerCase().contains("password"));
      assertFalse(identity.identityText().contains("jdbc.password"));
      assertEquals(
          "jdbc:sqlserver://db;user=report;password=<redacted>;databaseName=work",
          DatabaseTargetIdentity.sanitizeUrl(
              "jdbc:sqlserver://db;user=report;password=secret;databaseName=work"));
    }
  }

  @Test
  void discoversDeclaredCompositeAndLogicalKeysAndRelationships() throws Exception {
    try (H2Database database = new H2Database()) {
      database.execute(
          "create table person (id integer primary key, name varchar(80))",
          "create table project (tenant_id integer not null, project_id integer not null, name varchar(80), "
              + "primary key (tenant_id, project_id))",
          "create table membership (person_id integer not null, tenant_id integer not null, project_id integer not null, "
              + "constraint uq_membership unique(person_id, tenant_id, project_id), "
              + "foreign key(person_id) references person(id), "
              + "foreign key(tenant_id, project_id) references project(tenant_id, project_id))",
          "create table assignment (person_id integer not null, tenant_id integer not null, project_id integer not null, "
              + "foreign key(person_id) references person(id), "
              + "foreign key(tenant_id, project_id) references project(tenant_id, project_id))");

      DatabaseStructure structure = new DatabaseStructureInferrer().infer(database.connection());

      DatabaseStructure.Relation project = structure.relation("project").orElseThrow();
      assertEquals(PRIMARY_KEY, project.preferredKey().orElseThrow().evidence());
      assertEquals(List.of("TENANT_ID", "PROJECT_ID"), project.preferredKey().orElseThrow().columns());

      DatabaseStructure.Relation membership = structure.relation("membership").orElseThrow();
      assertTrue(membership.candidateKeys().stream().anyMatch(key ->
          key.evidence() == UNIQUE_INDEX
              && key.columns().equals(List.of("PERSON_ID", "TENANT_ID", "PROJECT_ID"))));
      DatabaseStructure.Relation assignment = structure.relation("assignment").orElseThrow();
      assertTrue(assignment.candidateKeys().stream().anyMatch(key -> key.evidence() == LOGICAL_LINK_KEY));
      assertEquals(2, structure.relationships().stream()
          .filter(relationship -> relationship.source().equals(membership.id()))
          .filter(relationship -> relationship.evidence() == DECLARED_FOREIGN_KEY)
          .count());
    }
  }

  @Test
  void infersConventionalKeyAndTypeCompatibleRelationshipWithoutReadingRows() throws Exception {
    try (H2Database database = new H2Database()) {
      database.execute(
          "create table users (id integer, name varchar(80))",
          "create table addresses (address_id integer, user_id integer, line varchar(80))");

      DatabaseStructure structure = new DatabaseStructureInferrer().infer(database.connection());

      DatabaseStructure.Relation users = structure.relation("users").orElseThrow();
      assertEquals(List.of("ID"), users.preferredKey().orElseThrow().columns());
      assertTrue(structure.relationships().stream().anyMatch(relationship ->
          relationship.source().name().equalsIgnoreCase("addresses")
              && relationship.target().name().equalsIgnoreCase("users")
              && relationship.sourceColumns().equals(List.of("USER_ID"))));
    }
  }
}
