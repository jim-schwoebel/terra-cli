package bio.terra.cli.command.resources;

import bio.terra.cli.auth.AuthenticationManager;
import bio.terra.cli.context.CloudResource;
import bio.terra.cli.context.GlobalContext;
import bio.terra.cli.context.WorkspaceContext;
import bio.terra.cli.service.WorkspaceManager;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra resources delete" command. */
@Command(name = "delete", description = "Delete an existing controlled resource.")
public class Delete implements Callable<Integer> {

  @CommandLine.Option(
      names = "--name",
      required = true,
      description = "The name of the resource, scoped to the workspace.")
  private String name;

  @Override
  public Integer call() {
    GlobalContext globalContext = GlobalContext.readFromFile();
    WorkspaceContext workspaceContext = WorkspaceContext.readFromFile();

    new AuthenticationManager(globalContext, workspaceContext).loginTerraUser();
    CloudResource resource =
        new WorkspaceManager(globalContext, workspaceContext).deleteControlledResource(name);

    System.out.println(resource.type + " successfully deleted: " + resource.cloudId);
    System.out.println("Workspace resource successfully removed: " + resource.name);

    return 0;
  }
}