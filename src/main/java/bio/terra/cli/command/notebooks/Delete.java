package bio.terra.cli.command.notebooks;

import bio.terra.cli.app.AuthenticationManager;
import bio.terra.cli.app.DockerToolsManager;
import bio.terra.cli.model.GlobalContext;
import bio.terra.cli.model.WorkspaceContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine;

/** This class corresponds to the third-level "terra notebooks delete" command. */
@CommandLine.Command(
    name = "delete",
    description = "Delete an AI Notebook instance within your workspace.")
public class Delete implements Callable<Integer> {

  @CommandLine.Parameters(index = "0", description = "The name of the notebook instance.")
  private String instanceName;

  @CommandLine.Option(
      names = "location",
      defaultValue = "us-central1-a",
      description = "The Google Cloud location of the instance, e.g. us-central1-a")
  private String location;

  @Override
  public Integer call() {
    GlobalContext globalContext = GlobalContext.readFromFile();
    WorkspaceContext workspaceContext = WorkspaceContext.readFromFile();
    workspaceContext.requireCurrentWorkspace();

    AuthenticationManager authenticationManager =
        new AuthenticationManager(globalContext, workspaceContext);
    authenticationManager.loginTerraUser();

    String command = "gcloud notebooks instances delete $INSTANCE_NAME --location=$LOCATION";
    Map<String, String> envVars = new HashMap<>();
    envVars.put("INSTANCE_NAME", instanceName);
    envVars.put("LOCATION", location);

    String logs =
        new DockerToolsManager(globalContext, workspaceContext)
            .runToolCommand(
                command, /* workingDir =*/ null, envVars, /* bindMounts =*/ new HashMap<>());

    System.out.println(logs);
    return 0;
  }
}