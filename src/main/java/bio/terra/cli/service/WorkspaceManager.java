package bio.terra.cli.service;

import bio.terra.cli.context.CloudResource;
import bio.terra.cli.context.GlobalContext;
import bio.terra.cli.context.TerraUser;
import bio.terra.cli.context.WorkspaceContext;
import bio.terra.cli.service.utils.GoogleCloudStorage;
import bio.terra.cli.service.utils.WorkspaceManagerService;
import bio.terra.workspace.model.IamRole;
import bio.terra.workspace.model.RoleBindingList;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.cloud.storage.Bucket;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
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
    WorkspaceDescription createdWorkspace =
        new WorkspaceManagerService(globalContext.server, currentUser).createWorkspace();
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
    WorkspaceDescription existingWorkspace =
        new WorkspaceManagerService(globalContext.server, currentUser)
            .getWorkspace(workspaceIdParsed);
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
    new WorkspaceManagerService(globalContext.server, currentUser)
        .deleteWorkspace(workspaceContext.getWorkspaceId());
    logger.info("deleted workspace: id={}, {}", workspace.getId(), workspace);

    // unset the workspace in the current context
    // note that this state is persisted to disk. it will be useful for code called in the same or a
    // later CLI command/process
    workspaceContext.updateWorkspace(null);

    return workspace.getId();
  }

  /**
   * Add a user to the workspace that is mounted to the current directory. Possible roles are
   * defined by the WSM client library.
   *
   * @param userEmail the user to add
   * @param iamRole the role to assign the user
   * @throws RuntimeException if there is no workspace currently mounted
   */
  public void addUserToWorkspace(String userEmail, IamRole iamRole) {
    // check that there is a workspace currently mounted
    workspaceContext.requireCurrentWorkspace();

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to add a user + role to the existing workspace
    new WorkspaceManagerService(globalContext.server, currentUser)
        .grantIamRole(workspaceContext.getWorkspaceId(), userEmail, iamRole);
    logger.info(
        "added user to workspace: id={}, user={}, role={}",
        workspaceContext.getWorkspaceId(),
        userEmail,
        iamRole);
  }

  /**
   * Remove a user + role from the workspace that is mounted to the current directory. Possible
   * roles are defined by the WSM client library.
   *
   * @param userEmail the user to remove
   * @param iamRole the role to remove from the user
   * @throws RuntimeException if there is no workspace currently mounted
   */
  public void removeUserFromWorkspace(String userEmail, IamRole iamRole) {
    // check that there is a workspace currently mounted
    workspaceContext.requireCurrentWorkspace();

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to remove a user + role from the existing workspace
    new WorkspaceManagerService(globalContext.server, currentUser)
        .removeIamRole(workspaceContext.getWorkspaceId(), userEmail, iamRole);
    logger.info(
        "removed user from workspace: id={}, user={}, role={}",
        workspaceContext.getWorkspaceId(),
        userEmail,
        iamRole);
  }

  /**
   * List the roles in a workspace and all the users that have each role.
   *
   * @return a map of roles to the list of users that have that role
   */
  public RoleBindingList listUsersOfWorkspace() {
    // check that there is a workspace currently mounted
    workspaceContext.requireCurrentWorkspace();

    // check that there is a current user, we will use their credentials to communicate with WSM
    TerraUser currentUser = globalContext.requireCurrentTerraUser();

    // call WSM to get the users + roles for the existing workspace
    return new WorkspaceManagerService(globalContext.server, currentUser)
        .getRoles(workspaceContext.getWorkspaceId());
  }

  /**
   * Lookup a controlled resource by its name. Names are unique within a workspace.
   *
   * @param resourceName name of resource to lookup
   * @return the cloud resource object
   * @throws RuntimeException if the resource is not controlled (e.g. external bucket)
   */
  public CloudResource getControlledResource(String resourceName) {
    // TODO: change this method to call WSM controlled resource endpoints once they're ready
    CloudResource cloudResource = workspaceContext.getCloudResource(resourceName);
    if (!cloudResource.isControlled) {
      throw new RuntimeException(resourceName + " is not a controlled resource.");
    }
    return cloudResource;
  }

  /**
   * Create a new controlled resource in the workspace.
   *
   * @param resourceType type of resource to create
   * @param resourceName name of resource to create
   * @return the cloud resource that was created
   */
  public CloudResource createControlledResource(
      CloudResource.Type resourceType, String resourceName) {
    // TODO: change this method to call WSM controlled resource endpoints once they're ready
    if (!isValidEnvironmentVariableName(resourceName)) {
      throw new RuntimeException(
          "Resource name can contain only alphanumeric and underscore characters.");
    }

    // create the bucket by calling GCS directly
    String bucketName = workspaceContext.getGoogleProject() + "-" + resourceName;
    Bucket bucket =
        new GoogleCloudStorage(
                globalContext.requireCurrentTerraUser(), workspaceContext.getGoogleProject())
            .createBucket(bucketName);

    // persist the cloud resource locally
    CloudResource resource =
        new CloudResource(resourceName, "gs://" + bucket.getName(), resourceType, true);
    workspaceContext.addCloudResource(resource);

    return resource;
  }

  /**
   * Check if the name only contains alphanumeric and underscore characters.
   *
   * @param name string to check
   * @return true if the string is a valid environment variable name
   */
  private static boolean isValidEnvironmentVariableName(String name) {
    return !Pattern.compile("[^a-zA-Z0-9_]").matcher(name).find();
  }

  /**
   * Delete an existing controlled resource in the workspace.
   *
   * @param resourceName name of resource to delete
   * @return the cloud resource object that was removed
   */
  public CloudResource deleteControlledResource(String resourceName) {
    // TODO: change this method to call WSM controlled resource endpoints once they're ready
    // delete the bucket by calling GCS directly
    CloudResource resource = getControlledResource(resourceName);
    new GoogleCloudStorage(
            globalContext.requireCurrentTerraUser(), workspaceContext.getGoogleProject())
        .deleteBucket(resource.cloudId);

    // remove the cloud resource and persist the updated list locally
    workspaceContext.removeCloudResource(resourceName);

    return resource;
  }

  /**
   * List the controlled resources in a workspace.
   *
   * @return a list of controlled resources in the workspace
   */
  public List<CloudResource> listResources() {
    // TODO: change this method to call WSM controlled resource endpoints once they're ready

    return workspaceContext.listControlledResources();
  }
}