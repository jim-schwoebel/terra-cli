package bio.terra.cli.businessobject;

import bio.terra.cli.exception.SystemException;
import bio.terra.cli.exception.UserActionableException;
import bio.terra.cli.serialization.persisted.PDWorkspace;
import bio.terra.cli.service.SamService;
import bio.terra.cli.service.WorkspaceManagerService;
import bio.terra.cli.service.utils.CrlUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.workspace.model.CloneWorkspaceResult;
import bio.terra.workspace.model.ClonedWorkspace;
import bio.terra.workspace.model.CloudPlatform;
import bio.terra.workspace.model.Folder;
import bio.terra.workspace.model.Properties;
import bio.terra.workspace.model.Property;
import bio.terra.workspace.model.ResourceDescription;
import bio.terra.workspace.model.WorkspaceDescription;
import com.google.api.services.cloudresourcemanager.v3.model.Binding;
import com.google.api.services.cloudresourcemanager.v3.model.GetIamPolicyRequest;
import com.google.api.services.cloudresourcemanager.v3.model.Policy;
import com.google.api.services.cloudresourcemanager.v3.model.SetIamPolicyRequest;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal representation of a workspace. An instance of this class is part of the current context
 * or state.
 */
public class Workspace {
  private static final Logger logger = LoggerFactory.getLogger(Workspace.class);
  private UUID uuid;
  private String userFacingId;
  private CloudPlatform cloudPlatform;
  private String googleProjectId;

  /** Build an instance of this class from the WSM client library WorkspaceDescription object. */
  private Workspace(WorkspaceDescription wsmObject) {
    this.uuid = wsmObject.getId();
    this.userFacingId = wsmObject.getUserFacingId();
    if (wsmObject.getGcpContext() != null) {
      cloudPlatform = CloudPlatform.GCP;
      googleProjectId = wsmObject.getGcpContext().getProjectId();
    } else if (wsmObject.getAzureContext() != null) {
      cloudPlatform = CloudPlatform.AZURE;
    }
  }

  /** Build an instance of this class from the serialized format on disk. */
  public Workspace(PDWorkspace configFromDisk) {
    this.uuid = configFromDisk.uuid;
    this.userFacingId = configFromDisk.userFacingId;
    this.googleProjectId = configFromDisk.googleProjectId;
    this.cloudPlatform = configFromDisk.cloudPlatform;
  }

  /** Create a new workspace and set it as the current workspace. */
  public static Workspace create(
      String userFacingId,
      CloudPlatform cloudPlatform,
      String name,
      String description,
      Map<String, String> properties,
      String spendProfile) {

    // call WSM to create the workspace object and backing Google context
    WorkspaceDescription createdWorkspace =
        WorkspaceManagerService.fromContext()
            .createWorkspace(
                userFacingId, cloudPlatform, name, description, properties, spendProfile);
    logger.info("Created workspace: {}", createdWorkspace);

    // convert the WSM object to a CLI object
    Workspace workspace = new Workspace(createdWorkspace);

    // update the global context with the current workspace
    Context.setWorkspace(workspace);

    // fetch the pet SA email for the user + this workspace
    // do this here so we have them stored locally before the user tries to run an app in the
    // workspace. this is so we pay the cost of a SAM round-trip ahead of time, instead of slowing
    // down an app call
    Context.requireUser().fetchPetSaEmail();

    return workspace;
  }

  /** Load an existing workspace and set it as the current workspace. */
  public static Workspace load(String userFacingId) {
    Workspace workspace = Workspace.getByUserFacingId(userFacingId);

    // update the global context with the current workspace
    Context.setWorkspace(workspace);

    // fetch the pet SA email for the user + this workspace
    // do this here so we have them stored locally before the user tries to run an app in the
    // workspace. this is so we pay the cost of a SAM round-trip ahead of time, instead of slowing
    // down an app call
    Context.requireUser().fetchPetSaEmail();

    return workspace;
  }

  /** Fetch an existing workspace by uuid, with resources populated */
  public static Workspace get(UUID id) {
    // call WSM to fetch the existing workspace object and backing Google context
    WorkspaceDescription loadedWorkspace = WorkspaceManagerService.fromContext().getWorkspace(id);
    logger.info("Loaded workspace: {}", loadedWorkspace);
    return new Workspace(loadedWorkspace);
  }

  /** Fetch an existing workspace by userFacingId, with resources populated */
  public static Workspace getByUserFacingId(String userFacingId) {
    // call WSM to fetch the existing workspace object and backing Google context
    WorkspaceDescription loadedWorkspace =
        WorkspaceManagerService.fromContext().getWorkspaceByUserFacingId(userFacingId);
    logger.info("Loaded workspace: {}", loadedWorkspace);
    return new Workspace(loadedWorkspace);
  }

  /**
   * List all workspaces that the current user has read access to.
   *
   * @param offset the offset to use when listing workspaces (zero to start from the beginning)
   * @param limit the maximum number of workspaces to return
   * @return list of workspaces
   */
  public static List<WorkspaceDescription> list(int offset, int limit) {
    // fetch the list of workspaces from WSM
    return WorkspaceManagerService.fromContext().listWorkspaces(offset, limit).getWorkspaces();
  }

  public static Properties stringMapToProperties(@Nullable Map<String, String> map) {
    Properties properties = new Properties();
    if (map == null) {
      return properties;
    }
    for (Map.Entry<String, String> entry : map.entrySet()) {
      Property property = new Property().key(entry.getKey()).value(entry.getValue());
      properties.add(property);
    }
    return properties;
  }

  private static Map<String, String> propertiesToStringMap(Properties properties) {
    return properties.stream().collect(Collectors.toMap(Property::getKey, Property::getValue));
  }

  /**
   * Update the mutable properties of the current workspace.
   *
   * @param userFacingId optional user-facing ID
   * @param name optional display name
   * @param description optional description
   * @throws UserActionableException if there is no current workspace
   */
  public Workspace update(
      @Nullable String userFacingId, @Nullable String name, @Nullable String description) {
    // call WSM to update the existing workspace object
    WorkspaceDescription updatedWorkspace =
        WorkspaceManagerService.fromContext()
            .updateWorkspace(uuid, userFacingId, name, description);
    logger.info("Updated workspace: {}", updatedWorkspace);

    // convert the WSM object to a CLI object
    Workspace workspace = new Workspace(updatedWorkspace);

    // update the global context with the current workspace
    Context.setWorkspace(workspace);
    return workspace;
  }

  /**
   * Update the mutable properties of the current workspace.
   *
   * @param properties properties for updating
   * @throws UserActionableException if there is no current workspace
   */
  public Workspace updateProperties(Map<String, String> properties) {
    // call WSM to update the existing workspace object
    WorkspaceDescription updatedWorkspaceProperties =
        WorkspaceManagerService.fromContext().updateWorkspaceProperties(uuid, properties);
    logger.info("Updated workspace properties: {}", updatedWorkspaceProperties);

    // convert the WSM object to a CLI object
    Workspace workspace = new Workspace(updatedWorkspaceProperties);

    // update the global context with the current workspace
    Context.setWorkspace(workspace);
    return workspace;
  }

  /** Delete the current workspace. */
  public void delete() {
    // call WSM to delete the existing workspace object
    WorkspaceManagerService.fromContext().deleteWorkspace(uuid);
    logger.info("Deleted workspace: {}", this);

    // delete the pet SA email for the user
    Context.requireUser().deletePetSaEmail();

    // unset the workspace in the current context
    Context.setWorkspace(null);
  }

  /**
   * Delete the mutable properties of the current workspace.
   *
   * @param propertyKeys properties for deleting
   * @throws UserActionableException if there is no current workspace
   */
  public Workspace deleteProperties(List<String> propertyKeys) {
    WorkspaceDescription deletedWorkspaceProperties =
        WorkspaceManagerService.fromContext().deleteWorkspaceProperties(uuid, propertyKeys);
    logger.info("Deleted workspace properties: {}", propertyKeys);

    // convert the WSM object to a CLI object
    Workspace workspace = new Workspace(deletedWorkspaceProperties);

    // update the global context with the current workspace
    Context.setWorkspace(workspace);
    return workspace;
  }

  /** Enable the current user and their pet to impersonate their pet SA in this workspace. */
  public void enablePet() {
    WorkspaceManagerService.fromContext().enablePet(uuid);
  }

  /**
   * Get a resource by name.
   *
   * @throws UserActionableException if there is no resource with that name
   */
  public Resource getResource(String name) {
    Optional<Resource> resourceOpt =
        listResources().stream().filter(resource -> resource.name.equals(name)).findFirst();
    return resourceOpt.orElseThrow(
        () -> new UserActionableException("Resource not found: " + name));
  }

  /** Fetch the list of resources for the current workspace. */
  public List<Resource> listResources() {
    List<ResourceDescription> wsmObjects =
        WorkspaceManagerService.fromContext()
            .enumerateAllResources(uuid, Context.getConfig().getResourcesCacheSize());
    return wsmObjects.stream().map(Resource::deserializeFromWsm).collect(Collectors.toList());
  }

  /** Fetch the list of folders for this workspace */
  public ImmutableList<Folder> listFolders() {
    return WorkspaceManagerService.fromContext().listFolders(uuid);
  }

  /**
   * Clone the current workspace into a new one
   *
   * @param userFacingId - user-facing ID of the new workspace
   * @param name - name of the new workspace
   * @param description - description of the new workspace
   * @param spendProfile - spend profile of the new workspace
   * @return - ClonedWorkspace structure with details on each resource
   */
  public ClonedWorkspace clone(
      String userFacingId,
      @Nullable String name,
      @Nullable String description,
      String spendProfile) {

    CloneWorkspaceResult result =
        WorkspaceManagerService.fromContext()
            .cloneWorkspace(uuid, userFacingId, name, description, spendProfile);
    return result.getWorkspace();
  }

  /**
   * Grant break-glass access to a user of this workspace. The Editor and Project IAM Admin roles
   * are granted to the user's proxy group.
   *
   * @param granteeEmail email of the workspace user requesting break-glass access
   * @param userProjectsAdminCredentials credentials for a SA that has permission to set IAM policy
   *     on workspace projects in this WSM deployment (e.g. WSM application SA)
   * @return the proxy group email of the workspace user that was granted break-glass access
   */
  public String grantBreakGlass(
      String granteeEmail, ServiceAccountCredentials userProjectsAdminCredentials) {
    // fetch the user's proxy group email from SAM
    String granteeProxyGroupEmail = SamService.fromContext().getProxyGroupEmail(granteeEmail);
    logger.debug("granteeProxyGroupEmail: {}", granteeProxyGroupEmail);

    // grant the Editor role to the user's proxy group email on the workspace project
    CloudResourceManagerCow resourceManagerCow =
        CrlUtils.createCloudResourceManagerCow(userProjectsAdminCredentials);
    try {
      Policy policy =
          resourceManagerCow
              .projects()
              .getIamPolicy(googleProjectId, new GetIamPolicyRequest())
              .execute();
      List<Binding> updatedBindings =
          Optional.ofNullable(policy.getBindings()).orElse(new ArrayList<>());
      updatedBindings.add(
          new Binding()
              .setRole("roles/editor")
              .setMembers(ImmutableList.of("group:" + granteeProxyGroupEmail)));
      updatedBindings.add(
          new Binding()
              .setRole("roles/resourcemanager.projectIamAdmin")
              .setMembers(ImmutableList.of("group:" + granteeProxyGroupEmail)));
      policy.setBindings(updatedBindings);
      resourceManagerCow
          .projects()
          .setIamPolicy(googleProjectId, new SetIamPolicyRequest().setPolicy(policy))
          .execute();
    } catch (IOException ioEx) {
      throw new SystemException(
          "Error granting the Editor and Project IAM Admin roles to the user's proxy group.", ioEx);
    }

    return granteeProxyGroupEmail;
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getUserFacingId() {
    return userFacingId;
  }

  public Optional<String> getGoogleProjectId() {
    return Optional.ofNullable(googleProjectId);
  }

  public String getRequiredGoogleProjectId() {
    return getGoogleProjectId()
        .orElseThrow(
            () ->
                new UserActionableException("No GCP project available in the current workspace."));
  }

  public CloudPlatform getCloudPlatform() {
    return cloudPlatform;
  }

  public WorkspaceDescription getWorkspaceDescription() {
    return WorkspaceManagerService.fromContext().getWorkspace(uuid);
  }
}
