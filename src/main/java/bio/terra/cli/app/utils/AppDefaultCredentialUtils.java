package bio.terra.cli.app.utils;

import bio.terra.cli.businessobject.Context;
import bio.terra.cli.exception.UserActionableException;
import com.google.auth.oauth2.ComputeEngineCredentials;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ImpersonatedCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.common.annotations.VisibleForTesting;
import java.io.IOException;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Utilities for working with Google application default credentials. */
public class AppDefaultCredentialUtils {
  private static final Logger logger = LoggerFactory.getLogger(AppDefaultCredentialUtils.class);

  // the Java system property that allows tests to specify a SA key file for the ADC and gcloud
  // credentials
  @VisibleForTesting
  public static final String ADC_OVERRIDE_SYSTEM_PROPERTY = "TERRA_GOOGLE_CREDENTIALS";

  /** Return the file backing the current application default credentials, null if none is found. */
  public static Path getADCBackingFile() {
    // 1. check the GOOGLE_APPLICATION_CREDENTIALS env var
    // this path, if set, typically points to a SA key file
    String envVar = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
    if (envVar != null) {
      Path envVarPath = Path.of(envVar);
      if (envVarPath.toFile().exists()) {
        return envVarPath.toAbsolutePath();
      }
    }

    // 2. check $HOME/.config/gcloud/application_default_credentials.json
    // this path, if it exists, typically points to user credentials generated by `gcloud auth
    // application-default login`
    Path gcloudConfigPath =
        Path.of(
            System.getProperty("user.home"), ".config/gcloud/application_default_credentials.json");
    if (gcloudConfigPath.toFile().exists()) {
      return gcloudConfigPath.toAbsolutePath();
    }

    // there is no file backing ADC
    return null;
  }

  /**
   * Tests can set a Java system property to point to a SA key file. Then we can use this to set
   * ADC, without requiring a metadata server or a gcloud auth application-default login.
   */
  public static Path getADCOverrideFileForTesting() {
    String appDefaultKeyFile = System.getProperty(ADC_OVERRIDE_SYSTEM_PROPERTY);
    if (appDefaultKeyFile == null || appDefaultKeyFile.isEmpty()) {
      return null;
    }
    logger.warn(
        "Application default credentials file set by system property. This is expected when testing, not during normal operation.");
    Path adcBackingFile = Path.of(appDefaultKeyFile).toAbsolutePath();
    logger.info("adcBackingFile: {}", adcBackingFile);
    return adcBackingFile;
  }

  /**
   * Throw an exception if the application default credentials are not defined or do not match the
   * current user or their pet SA
   */
  public static void throwIfADCDontMatchContext() {
    if (!doADCMatchContext()) {
      throw new UserActionableException(
          "Application default credentials do not match the user or pet SA emails.");
    }
  }

  /**
   * Return true if the application default credentials match the pet SA for the current
   * user+workspace, or if they are end-user credentials, which we can't validate directly. Throw an
   * exception if they are not defined.
   */
  private static boolean doADCMatchContext() {
    GoogleCredentials appDefaultCreds = getADC();

    if (appDefaultCreds instanceof UserCredentials) {
      logger.info("ADC are end-user credentials. Skipping account/email validation.");
      return true;
    }

    String email = null;
    if (appDefaultCreds instanceof ServiceAccountCredentials) {
      email = ((ServiceAccountCredentials) appDefaultCreds).getClientEmail();
    } else if (appDefaultCreds instanceof ComputeEngineCredentials) {
      email = ((ComputeEngineCredentials) appDefaultCreds).getAccount();
    } else if (appDefaultCreds instanceof ImpersonatedCredentials) {
      email = ((ImpersonatedCredentials) appDefaultCreds).getAccount();
    }
    return email != null && email.equalsIgnoreCase(Context.requireUser().getPetSaEmail());
  }

  /** Get the application default credentials. Throw an exception if they are not defined. */
  private static GoogleCredentials getADC() {
    try {
      return GoogleCredentials.getApplicationDefault();
    } catch (IOException ioEx) {
      throw new UserActionableException("Application default credentials are not defined.", ioEx);
    }
  }
}
