package bio.terra.cli.context;

import bio.terra.cli.utils.FileUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This POJO class represents an instance of the Terra CLI global context. This is intended
 * primarily for authentication-related context values that will span multiple workspaces.
 */
public class GlobalContext {
  private static final Logger logger = LoggerFactory.getLogger(GlobalContext.class);

  public static final Path DEFAULT_GLOBAL_CONTEXT_DIR =
      Paths.get(System.getProperty("user.home"), ".terra-cli");
  public static final String GLOBAL_CONTEXT_FILENAME = "global_context.json";
  public static final String PET_SA_KEYS_DIRNAME = "pet_SA_keys";

  private Path globalContextDir;
  // TODO: this needs to change to a list when we support multiple identity contexts
  private String singleUserId;
  private String petSAId;
  private String currentTerraUserId;
  private String currentTerraUserName;
  private String samUri;

  public GlobalContext() {}

  /** Constructor that allows overriding the default global context directory. */
  private GlobalContext(Path globalContextDir) {
    this.globalContextDir = globalContextDir;
    this.singleUserId = "singleuser";
    this.petSAId = "petSA";
    this.samUri = "https://sam.dsde-dev.broadinstitute.org";
  }

  /**
   * Read in an instance of this class from a JSON-formatted file in the {@link
   * #DEFAULT_GLOBAL_CONTEXT_DIR} directory. If there is no existing file, this method will write
   * one with default values.
   *
   * @return an instance of this class
   */
  public static GlobalContext readFromFile() {
    // TODO: allow overriding the global context directory path (maybe with an environment variable
    // or optional CLI flag?)
    Path globalContextDir = DEFAULT_GLOBAL_CONTEXT_DIR;

    // try to read in an instance of the global context file
    GlobalContext globalContext = null;
    try {
      globalContext =
          FileUtils.readOutputFileIntoJavaObject(
              globalContextDir, GLOBAL_CONTEXT_FILENAME, GlobalContext.class);
    } catch (IOException ioEx) {
      logger.error("Error reading in global context file.", ioEx);
    }

    // if the global context file does not exist, return an object with default values
    if (globalContext == null) {
      globalContext = new GlobalContext(globalContextDir);
    }

    return globalContext;
  }

  /**
   * Write an instance of this class to a JSON-formatted file in the {@link
   * #DEFAULT_GLOBAL_CONTEXT_DIR} directory.
   */
  public void writeToFile() {
    try {
      FileUtils.writeJavaObjectToFile(DEFAULT_GLOBAL_CONTEXT_DIR, GLOBAL_CONTEXT_FILENAME, this);
    } catch (IOException ioEx) {
      logger.error("Error persisting global context.", ioEx);
    }
  }

  /** Getter for the global context directory. */
  public Path getGlobalContextDir() {
    return globalContextDir;
  }

  /**
   * Getter for the sub-directory of the global context directory that holds the pet SA key files.
   */
  public Path resolvePetSAKeyDir() {
    return globalContextDir.resolve(PET_SA_KEYS_DIRNAME);
  }

  /** Getter for the SAM URI. */
  public String getSamUri() {
    return samUri;
  }

  /** Setter for the SAM URI. */
  public void setSamUri(String samUri) {
    this.samUri = samUri;
  }

  /** Getter for the single user id. */
  public String getSingleUserId() {
    return singleUserId;
  }

  /** Getter for the single pet SA id. */
  public String getPetSAId() {
    return petSAId;
  }

  /** Getter for the current Terra user id. */
  public String getCurrentTerraUserId() {
    return currentTerraUserId;
  }

  /** Setter for the current Terra user id. */
  public void setCurrentTerraUserId(String currentTerraUserId) {
    this.currentTerraUserId = currentTerraUserId;
  }

  /** Getter for the current Terra user name. */
  public String getCurrentTerraUserName() {
    return currentTerraUserName;
  }

  /** Setter for the current Terra user name. */
  public void setCurrentTerraUserName(String currentTerraUserName) {
    this.currentTerraUserName = currentTerraUserName;
  }
}
