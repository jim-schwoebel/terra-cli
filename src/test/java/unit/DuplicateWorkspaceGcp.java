package unit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cli.serialization.userfacing.UFDuplicatedResource;
import bio.terra.cli.serialization.userfacing.UFDuplicatedWorkspace;
import bio.terra.cli.serialization.userfacing.UFResource;
import bio.terra.cli.serialization.userfacing.UFWorkspace;
import bio.terra.cli.serialization.userfacing.resource.UFBqDataset;
import bio.terra.cli.serialization.userfacing.resource.UFGcsBucket;
import bio.terra.cli.serialization.userfacing.resource.UFGitRepo;
import bio.terra.workspace.model.CloneResourceResult;
import bio.terra.workspace.model.StewardshipType;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.api.services.bigquery.model.DatasetReference;
import harness.TestCommand;
import harness.TestUser;
import harness.baseclasses.ClearContextUnit;
import harness.utils.Auth;
import harness.utils.ExternalBQDatasets;
import harness.utils.TestUtils;
import harness.utils.WorkspaceUtils;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Tag("unit-gcp")
public class DuplicateWorkspaceGcp extends ClearContextUnit {
  private static final TestUser workspaceCreator = TestUser.chooseTestUserWithSpendAccess();
  private static final Logger logger = LoggerFactory.getLogger(DuplicateWorkspaceGcp.class);

  private static final String GIT_REPO_HTTPS_URL =
      "https://github.com/DataBiosphere/terra-workspace-manager.git";
  private static final String GIT_REPO_REF_NAME = "gitrepo_ref";
  private static final int SOURCE_RESOURCE_NUM = 5;
  private static final int DESTINATION_RESOURCE_NUM = 4;

  private static DatasetReference externalDataset;
  private UFWorkspace sourceWorkspace;
  private UFWorkspace destinationWorkspace;

  @Override
  @BeforeAll
  public void setupOnce() throws Exception {
    super.setupOnce();

    // create an external dataset to use for a referenced resource
    externalDataset = ExternalBQDatasets.createDataset();

    // grant the workspace creator access to the dataset
    ExternalBQDatasets.grantReadAccess(
        externalDataset, workspaceCreator.email, ExternalBQDatasets.IamMemberType.USER);

    // grant the user's proxy group access to the dataset so that it will pass WSM's access check
    // when adding it as a referenced resource
    ExternalBQDatasets.grantReadAccess(
        externalDataset, Auth.getProxyGroupEmail(), ExternalBQDatasets.IamMemberType.GROUP);
  }

  @AfterAll
  public static void cleanupOnce() throws IOException {
    if (externalDataset != null) {
      ExternalBQDatasets.deleteDataset(externalDataset);
      externalDataset = null;
    }
  }

  @AfterEach
  public void cleanupEachTime() throws IOException {
    workspaceCreator.login();
    if (sourceWorkspace != null) {
      TestCommand.Result result =
          TestCommand.runCommand(
              "workspace", "delete", "--quiet", "--workspace=" + sourceWorkspace.id);
      sourceWorkspace = null;
      if (0 != result.exitCode) {
        logger.error("Failed to delete source workspace. exit code = {}", result.exitCode);
      }
    }

    if (destinationWorkspace != null) {
      TestCommand.Result result =
          TestCommand.runCommand(
              "workspace", "delete", "--quiet", "--workspace=" + destinationWorkspace.id);
      destinationWorkspace = null;
      if (0 != result.exitCode) {
        logger.error("Failed to delete destination workspace. exit code = {}", result.exitCode);
      }
    }
  }

  /**
   * Check Optional's value is present and return it, or else fail an assertion.
   *
   * @param optional - Optional expression
   * @param <T> - value type of optional
   * @return - value of optional, if present
   */
  @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
  public static <T> T getOrFail(Optional<T> optional) {
    assertTrue(optional.isPresent(), "Optional value was empty.");
    return optional.get();
  }

  @Test
  @DisplayName("duplicate workspace with platform GCP")
  public void duplicateWorkspaceGcp() throws IOException, InterruptedException {
    workspaceCreator.login();

    // create a workspace
    sourceWorkspace =
        WorkspaceUtils.createWorkspace(workspaceCreator, Optional.of(getCloudPlatform()));

    // Add a bucket resource
    UFGcsBucket sourceBucket =
        TestCommand.runAndParseCommandExpectSuccess(
            UFGcsBucket.class,
            "resource",
            "create",
            "gcs-bucket",
            "--name=" + "bucket_1",
            "--bucket-name=" + UUID.randomUUID()); // cloning defaults to COPY_RESOURCE

    // Add another bucket resource with COPY_NOTHING
    UFGcsBucket copyNothingBucket =
        TestCommand.runAndParseCommandExpectSuccess(
            UFGcsBucket.class,
            "resource",
            "create",
            "gcs-bucket",
            "--name=" + "bucket_2",
            "--bucket-name=" + UUID.randomUUID(),
            "--cloning=COPY_NOTHING");

    // Add a dataset resource
    UFBqDataset sourceDataset =
        TestCommand.runAndParseCommandExpectSuccess(
            UFBqDataset.class,
            "resource",
            "create",
            "bq-dataset",
            "--name=dataset_1",
            "--dataset-id=dataset_1",
            "--description=The first dataset.",
            "--cloning=COPY_RESOURCE");

    UFBqDataset datasetReference =
        TestCommand.runAndParseCommandExpectSuccess(
            UFBqDataset.class,
            "resource",
            "add-ref",
            "bq-dataset",
            "--name=dataset_ref",
            "--project-id=" + externalDataset.getProjectId(),
            "--dataset-id=" + externalDataset.getDatasetId(),
            "--cloning=COPY_REFERENCE");

    UFGitRepo gitRepositoryReference =
        TestCommand.runAndParseCommandExpectSuccess(
            UFGitRepo.class,
            "resource",
            "add-ref",
            "git-repo",
            "--name=" + GIT_REPO_REF_NAME,
            "--repo-url=" + GIT_REPO_HTTPS_URL,
            "--cloning=COPY_REFERENCE");

    // Update workspace name. This is for testing PF-1623.
    TestCommand.runAndParseCommandExpectSuccess(
        UFWorkspace.class, "workspace", "update", "--new-name=update_name");

    // Duplicate the workspace
    UFDuplicatedWorkspace duplicatedWorkspace =
        TestCommand.runAndParseCommandExpectSuccess(
            UFDuplicatedWorkspace.class,
            "workspace",
            "duplicate",
            "--new-id=" + TestUtils.appendRandomNumber("duplicated-workspace-id"),
            "--name=duplicated_workspace",
            "--description=A duplicate.");

    assertEquals(
        sourceWorkspace.id,
        duplicatedWorkspace.sourceWorkspace.id,
        "Correct source workspace ID for duplicate.");
    destinationWorkspace = duplicatedWorkspace.destinationWorkspace;
    assertThat(
        "There are 5 duplicated resources",
        duplicatedWorkspace.resources,
        hasSize(SOURCE_RESOURCE_NUM));

    UFDuplicatedResource bucketDuplicatedResource =
        getOrFail(
            duplicatedWorkspace.resources.stream()
                .filter(cr -> sourceBucket.id.equals(cr.sourceResource.id))
                .findFirst());
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        bucketDuplicatedResource.result,
        "bucket duplicated succeeded");
    assertNotNull(
        bucketDuplicatedResource.destinationResource, "Destination bucket resource was created");

    UFDuplicatedResource copyNothingBucketDuplicatedResourcec =
        getOrFail(
            duplicatedWorkspace.resources.stream()
                .filter(cr -> copyNothingBucket.id.equals(cr.sourceResource.id))
                .findFirst());
    assertEquals(
        CloneResourceResult.SKIPPED,
        copyNothingBucketDuplicatedResourcec.result,
        "COPY_NOTHING resource was skipped.");
    assertNull(
        copyNothingBucketDuplicatedResourcec.destinationResource,
        "Skipped resource has no destination resource.");

    UFDuplicatedResource datasetDuplicatedResource =
        getOrFail(
            duplicatedWorkspace.resources.stream()
                .filter(cr -> datasetReference.id.equals(cr.sourceResource.id))
                .findFirst());
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        datasetDuplicatedResource.result,
        "Dataset reference duplicate succeeded.");
    assertNotNull(
        datasetDuplicatedResource.destinationResource,
        "Dataset reference duplicated resource null.");
    assertEquals(
        StewardshipType.REFERENCED,
        datasetDuplicatedResource.destinationResource.stewardshipType,
        "Dataset reference has correct stewardship type.");

    UFDuplicatedResource datasetDupliccatedResource =
        getOrFail(
            duplicatedWorkspace.resources.stream()
                .filter(cr -> sourceDataset.id.equals(cr.sourceResource.id))
                .findFirst());
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        datasetDupliccatedResource.result,
        "Dataset duplicate succeeded.");
    assertNotNull(
        datasetDupliccatedResource.destinationResource, "Dataset duplicated resource null.");
    assertEquals(
        "The first dataset.",
        datasetDupliccatedResource.destinationResource.description,
        "Dataset description matches.");

    UFDuplicatedResource gitRepoDuplicatedResource =
        getOrFail(
            duplicatedWorkspace.resources.stream()
                .filter(cr -> gitRepositoryReference.id.equals(cr.sourceResource.id))
                .findFirst());
    assertEquals(
        CloneResourceResult.SUCCEEDED,
        gitRepoDuplicatedResource.result,
        "Git repo duplicate succeeded");
    assertNotNull(
        gitRepoDuplicatedResource.destinationResource, "GitRepo duplicated resource null.");
    assertEquals(
        GIT_REPO_REF_NAME,
        gitRepoDuplicatedResource.destinationResource.name,
        "Resource type matches GIT_REPO");

    // Switch to the new workspace from the duplicate
    TestCommand.runCommandExpectSuccess(
        "workspace", "set", "--id=" + duplicatedWorkspace.destinationWorkspace.id);

    // Validate resources
    List<UFResource> resources =
        TestCommand.runAndParseCommandExpectSuccess(new TypeReference<>() {}, "resource", "list");
    assertThat(
        "Destination workspace has three resources.", resources, hasSize(DESTINATION_RESOURCE_NUM));
  }
}
