/*
 * Copyright (C) 2011 Everit Kft. (http://www.everit.biz)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.everit.persistence.lqmg;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

/**
 * The main class of the LQMG.
 */
public final class LQMGMain {

  public static final String ARG_BUNDLES = "bundles";

  public static final String ARG_CAPABILITY = "capability";

  public static final String ARG_DEFAULT_SCHEMA = "defaultSchema";

  public static final String ARG_HACK_WIRES = "hackWires";

  public static final String ARG_INNER_CLASSES_FOR_KEYS = "innerClassesForKeys";

  public static final String ARG_LQMG_CONFIG_XML = "configurationXML";

  public static final String ARG_OUTPUT_FOLDER = "outputFolder";

  public static final String ARG_PACKAGES = "packages";

  private static String evaluateMandatoryOptionValue(final String key,
      final CommandLine commandLine,
      final Options options) {
    String result = commandLine.getOptionValue(key);
    if (result == null) {
      LQMGMain.printHelp(options);
      IllegalArgumentException e =
          new IllegalArgumentException("Missing mandatory argument: " + key);
      e.setStackTrace(new StackTraceElement[0]);
      throw e;
    }
    return result;
  }

  /**
   * The command line processor.
   *
   * @param args
   *          the arguments.
   */
  public static void main(final String[] args) {
    Options options = new Options();
    options.addOption("b", ARG_BUNDLES, true, "Location to the bundles separated by semicolon");
    options.addOption("p", ARG_PACKAGES, true,
        "Package names separated by comma that should be generated."
            + " If not defined, all packages will be generated.");
    options.addOption("cp", ARG_CAPABILITY, true,
        "Expression that is used to select the first capability based on the"
            + " capabilities that are provided by the bundles.");
    options.addOption("o", ARG_OUTPUT_FOLDER, true, "Path of the folder where the classes should be"
        + " generated");
    options.addOption("c", ARG_LQMG_CONFIG_XML, true,
        "Path of an optional configuration XML that can override"
            + " configurations coming from the capabilities");
    options.addOption("h", ARG_HACK_WIRES, true,
        "Whether to try redeploy bundles with unsatisfied constraintsin"
            + " the way that their requirements are changed to be optional. Default: true");
    options.addOption("h", ARG_INNER_CLASSES_FOR_KEYS, true,
        "Whether to generate inner classes for constraints. Default: true");
    options.addOption("ds", ARG_DEFAULT_SCHEMA, true,
        "Default schema where tables will be generated. This schema will be passed in the "
            + "constructor of the generated metadata classes where the liquibase changelog file "
            + "does not contain schema information. It is recommended to define a unique value "
            + "and use the runtime schema renaming functionality of QueryDSL.");

    CommandLineParser commandLineParser = new BasicParser();
    CommandLine commandLine;
    try {
      commandLine = commandLineParser.parse(options, args, true);
    } catch (ParseException e) {
      LQMGMain.printHelp(options);
      RuntimeException returnE = new RuntimeException(e);
      returnE.setStackTrace(new StackTraceElement[0]);
      throw returnE;
    }

    String outputFolder =
        LQMGMain.evaluateMandatoryOptionValue(ARG_OUTPUT_FOLDER, commandLine, options);
    String bundles = LQMGMain.evaluateMandatoryOptionValue(ARG_BUNDLES, commandLine, options);
    String changelog = LQMGMain.evaluateMandatoryOptionValue(ARG_CAPABILITY, commandLine, options);
    String packages = commandLine.getOptionValue(ARG_PACKAGES);
    String configurationXMLPath = commandLine.getOptionValue(ARG_LQMG_CONFIG_XML);
    String hackWires = commandLine.getOptionValue(ARG_HACK_WIRES);
    String innerClassesForKeys = commandLine.getOptionValue(ARG_INNER_CLASSES_FOR_KEYS);
    String defaultSchema = commandLine.getOptionValue(ARG_DEFAULT_SCHEMA);

    GenerationProperties generationProps =
        new GenerationProperties(changelog, bundles.split("\\;"), outputFolder);

    generationProps.setConfigurationPath(configurationXMLPath);
    generationProps.setDefaultSchema(defaultSchema);

    if (packages != null) {
      generationProps.setPackages(packages.split("\\,"));
    }

    if (hackWires != null) {
      generationProps.setHackWires(Boolean.valueOf(hackWires));
    }

    if (innerClassesForKeys != null) {
      generationProps.setHackWires(Boolean.valueOf(innerClassesForKeys));
    }

    LQMG.generate(generationProps);
  }

  private static void printHelp(final Options options) {
    HelpFormatter helperFormatter = new HelpFormatter();
    helperFormatter.printHelp("java -jar lqmg.jar", options);
  }

  private LQMGMain() {
  }

}
