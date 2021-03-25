package bio.terra.cli.command.config;

import bio.terra.cli.command.config.getvalue.Browser;
import bio.terra.cli.command.config.getvalue.Image;
import bio.terra.cli.command.config.getvalue.Logging;
import picocli.CommandLine.Command;

/**
 * This class corresponds to the third-level "terra config get-value" command. This command is not
 * valid by itself; it is just a grouping keyword for it sub-commands.
 */
@Command(
    name = "get-value",
    description = "Get a configuration property value.",
    subcommands = {Browser.class, Image.class, Logging.class})
public class GetValue {}