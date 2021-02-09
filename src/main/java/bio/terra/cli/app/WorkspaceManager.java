package bio.terra.cli.app;

import bio.terra.cli.model.GlobalContext;
import bio.terra.cli.model.TerraUser;
import bio.terra.cli.model.WorkspaceContext;
import bio.terra.cli.utils.WorkspaceManagerUtils;
import bio.terra.workspace.client.ApiClient;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.WorkspaceDescription;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** This class manipulates the workspace properties of the workspace context object. */
public class WorkspaceManager {
  private static final Logger logger = LoggerFactory.getLogger(WorkspaceManager.class);

  private final GlobalContext globalContext;
  private final WorkspaceContext workspaceContext;

  public WorkspaceManager(GlobalContext globalContext, WorkspaceContext workspaceContext) {
    this.globalContext = globalContext;
    this.workspaceContext = workspaceContext;
  }

  /** Create a new workspace. */
  public void createWorkspace() {
    // check that there is no existing workspace already mounted
    if (!workspaceContext.isEmpty()) {
      throw new RuntimeException("There is already a workspace mounted to this directory.");
    }

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to create the workspace object and backing Google context
    ApiClient wsmClient =
        WorkspaceManagerUtils.getClientForTerraUser(currentUser, globalContext.server);
    WorkspaceDescription createdWorkspace = WorkspaceManagerUtils.createWorkspace(wsmClient);
    logger.info("created workspace: id={}, {}", createdWorkspace.getId(), createdWorkspace);

    // update the workspace context with the current workspace
    // note that this state is persisted to disk. it will be useful for code called in the same or a
    // later CLI command/process
    workspaceContext.updateWorkspace(createdWorkspace);
  }

  /**
   * Fetch an existing workspace and mount it to the current directory.
   *
   * @throws RuntimeException if there is already a different workspace mounted to the current
   *     directory
   */
  public void mountWorkspace(String workspaceId) {
    // check that the workspace id is a valid UUID
    UUID workspaceIdParsed = UUID.fromString(workspaceId);

    // check that either there is no workspace currently mounted, or its id matches this one
    if (!(workspaceContext.isEmpty()
        || workspaceContext.getWorkspaceId().equals(workspaceIdParsed))) {
      throw new RuntimeException(
          "There is already a different workspace mounted to this directory.");
    }

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to fetch the existing workspace object and backing Google context
    ApiClient wsmClient =
        WorkspaceManagerUtils.getClientForTerraUser(currentUser, globalContext.server);
    WorkspaceDescription existingWorkspace =
        WorkspaceManagerUtils.getWorkspace(wsmClient, workspaceIdParsed);
    logger.info("existing workspace: id={}, {}", existingWorkspace.getId(), existingWorkspace);

    // update the workspace context with the current workspace
    // note that this state is persisted to disk. it will be useful for code called in the same or a
    // later CLI command/process
    workspaceContext.updateWorkspace(existingWorkspace);
  }

  /**
   * Delete the workspace that is mounted to the current directory.
   *
   * @return the deleted workspace id
   * @throws RuntimeException if there is no workspace currently mounted
   */
  public UUID deleteWorkspace() {
    // check that there is a workspace currently mounted
    workspaceContext.requireCurrentWorkspace();

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to delete the existing workspace object
    WorkspaceDescription workspace = workspaceContext.terraWorkspaceModel;
    ApiClient wsmClient =
        WorkspaceManagerUtils.getClientForTerraUser(currentUser, globalContext.server);
    WorkspaceManagerUtils.deleteWorkspace(wsmClient, workspaceContext.getWorkspaceId());
    logger.info("deleted workspace: id={}, {}", workspace.getId(), workspace);

    // unset the workspace in the current context
    // note that this state is persisted to disk. it will be useful for code called in the same or a
    // later CLI command/process
    workspaceContext.updateWorkspace(null);

    return workspace.getId();
  }

  /**
   * Add a user to the workspace that is mounted to the current directory.
   *
   * @throws RuntimeException if there is no workspace currently mounted
   */
  public void addUserToWorkspace(String userEmail, IamRole iamRole) {
    // check that there is a workspace currently mounted
    workspaceContext.requireCurrentWorkspace();

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to add a user + role to the existing workspace
    ApiClient wsmClient =
        WorkspaceManagerUtils.getClientForTerraUser(currentUser, globalContext.server);
    WorkspaceManagerUtils.grantIamRole(
        wsmClient, workspaceContext.getWorkspaceId(), userEmail, iamRole);
    logger.info(
        "added user to workspace: id={}, user={}, role={}",
        workspaceContext.getWorkspaceId(),
        userEmail,
        iamRole);
  }
}