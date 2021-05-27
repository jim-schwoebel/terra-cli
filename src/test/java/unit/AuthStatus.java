package unit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.text.IsEmptyString.emptyOrNullString;
import static org.hamcrest.text.IsEqualIgnoringCase.equalToIgnoringCase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import bio.terra.cli.command.auth.Status;
import harness.TestCommand;
import harness.TestUsers;
import harness.baseclasses.ClearContextUnit;
import java.io.IOException;
import org.hamcrest.CoreMatchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Tests for the `terra auth status` command. */
@Tag("unit")
public class AuthStatus extends ClearContextUnit {
  @Test
  @DisplayName("auth status includes user email and says logged in")
  void authStatusWhenLoggedIn() throws IOException {
    // select a test user and login
    TestUsers testUser = TestUsers.chooseTestUser();
    testUser.login();

    // `terra auth status --format=json`
    TestCommand.Result cmd = TestCommand.runCommand("auth", "status", "--format=json");

    // check that it says logged in and includes the user & proxy emails
    assertEquals(0, cmd.exitCode);
    Status.AuthStatusReturnValue authStatus =
        TestCommand.readObjectFromStdOut(cmd, Status.AuthStatusReturnValue.class);
    assertThat(
        "auth status email matches test user",
        authStatus.userEmail,
        equalToIgnoringCase(testUser.email));
    assertThat(
        "auth status includes proxy group email",
        authStatus.proxyGroupEmail,
        CoreMatchers.not(emptyOrNullString()));
    assertTrue(authStatus.loggedIn, "auth status indicates user is logged in");
  }

  @Test
  @DisplayName("auth status does not include user email and says logged out")
  void authStatusWhenLoggedOut() throws IOException {
    // `terra auth status --format=json`
    TestCommand.Result cmd = TestCommand.runCommand("auth", "status", "--format=json");

    // check that it says logged out and doesn't include user or proxy emails
    assertEquals(0, cmd.exitCode);
    Status.AuthStatusReturnValue authStatus =
        TestCommand.readObjectFromStdOut(cmd, Status.AuthStatusReturnValue.class);
    assertThat(
        "auth status email is empty", authStatus.userEmail, CoreMatchers.is(emptyOrNullString()));
    assertThat(
        "auth status proxy group email is empty",
        authStatus.proxyGroupEmail,
        CoreMatchers.is(emptyOrNullString()));
    assertFalse(authStatus.loggedIn, "auth status indicates user is logged out");
  }

  @Test
  @DisplayName("auth status changes after logout")
  void authRevokeChanges() throws IOException {
    // select a test user and login
    TestUsers testUser = TestUsers.chooseTestUser();
    testUser.login();

    // `terra auth status --format=json`
    TestCommand.Result cmd = TestCommand.runCommand("auth", "status", "--format=json");

    // check that it says logged in
    assertEquals(0, cmd.exitCode);
    Status.AuthStatusReturnValue authStatus =
        TestCommand.readObjectFromStdOut(cmd, Status.AuthStatusReturnValue.class);
    assertTrue(authStatus.loggedIn, "auth status indicates user is logged in");

    // `terra auth revoke`
    cmd = TestCommand.runCommand("auth", "revoke");

    // `terra auth status --format=json`
    cmd = TestCommand.runCommand("auth", "status", "--format=json");

    // check that it says logged out
    assertEquals(0, cmd.exitCode);
    authStatus = TestCommand.readObjectFromStdOut(cmd, Status.AuthStatusReturnValue.class);
    assertFalse(authStatus.loggedIn, "auth status indicates user is logged out");
  }
}