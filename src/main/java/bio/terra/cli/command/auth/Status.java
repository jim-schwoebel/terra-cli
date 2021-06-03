package bio.terra.cli.command.auth;

import bio.terra.cli.Context;
import bio.terra.cli.User;
import bio.terra.cli.command.shared.BaseCommand;
import bio.terra.cli.command.shared.options.Format;
import bio.terra.cli.serialization.command.CommandAuthStatus;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/** This class corresponds to the third-level "terra auth status" command. */
@Command(name = "status", description = "Print details about the currently authorized account.")
public class Status extends BaseCommand {

  @CommandLine.Mixin Format formatOption;

  /**
   * Populate the current user in the global context and print out a subset of the TerraUser
   * properties.
   */
  @Override
  protected void execute() {
    // check if current user is defined
    Optional<User> currentUserOpt = Context.getUser();
    CommandAuthStatus authStatusReturnValue;
    if (!currentUserOpt.isPresent()) {
      authStatusReturnValue = CommandAuthStatus.createWhenCurrentUserIsUndefined();
    } else {
      User currentUser = currentUserOpt.get();
      authStatusReturnValue =
          CommandAuthStatus.createWhenCurrentUserIsDefined(
              currentUser.getEmail(),
              currentUser.getProxyGroupEmail(),
              !currentUser.requiresReauthentication());
    }

    formatOption.printReturnValue(
        authStatusReturnValue, returnValue -> this.printText(returnValue));
  }

  /** Print this command's output in text format. */
  private void printText(CommandAuthStatus returnValue) {
    // check if current user is defined
    if (returnValue.userEmail == null) {
      OUT.println("No current Terra user defined.");
    } else {
      OUT.println("Current Terra user: " + returnValue.userEmail);
      OUT.println("Current Terra user's proxy group: " + returnValue.proxyGroupEmail);

      // check if the current user needs to re-authenticate (i.e. is logged out)
      OUT.println("LOGGED " + (returnValue.loggedIn ? "IN" : "OUT"));
    }
  }

  /** This command never requires login. */
  @Override
  protected boolean requiresLogin() {
    return false;
  }
}
