package bio.terra.cli.command.app;

import bio.terra.cli.app.ToolsManager;
import bio.terra.cli.model.GlobalContext;
import bio.terra.cli.model.WorkspaceContext;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra app set-image" command. */
@Command(
    name = "set-image",
    description = "Set the Docker image to use for launching applications.")
public class SetImage implements Callable<Integer> {

  @CommandLine.Parameters(index = "0", description = "image id or tag")
  private String imageId;

  @Override
  public Integer call() {
    GlobalContext globalContext = GlobalContext.readFromFile();
    WorkspaceContext workspaceContext = WorkspaceContext.readFromFile();

    String prevImageId = globalContext.dockerImageId;
    new ToolsManager(globalContext, workspaceContext).updateImageId(imageId);

    if (globalContext.dockerImageId.equals(prevImageId)) {
      System.out.println("Docker image unchanged from " + globalContext.dockerImageId + ".");
    } else {
      System.out.println(
          "Docker image changed from " + prevImageId + " to " + globalContext.dockerImageId);
    }

    return 0;
  }
}
