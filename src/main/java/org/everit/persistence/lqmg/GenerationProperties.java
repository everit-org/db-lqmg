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

/**
 * This class store all parameters which is required to the JAVA classes generation.
 */
public class GenerationProperties {

  /**
   * The paths to the bundles (directory path of jar file path).
   */
  public final String[] bundleLocations;

  /**
   * A schema name with optional filter expression that points to a bundle schema capability. E.g.:
   * userMgmt;filter:="(version=2)"
   */
  public String capability;

  /**
   * Optional path of a configuration XML. In case this configuration exists, the rules in it are
   * stronger than the rules specified at the capability attributes in the bundles.
   */
  public String configurationPath;

  /**
   * Comma separated list of contexts Liquibase is running under.
   */
  public String contexts;

  /**
   * Default schema on SQL Connection to be used during generating tables.
   */
  public String defaultSchema;

  /**
   * If true, LQMG will update the unresolved bundles in the way that all of their unsatisfied
   * requirements will be modified to be optional.
   */
  public boolean hackWires = true;

  /**
   * If true, inner classes will be generated for foreign and primary keys.
   */
  public boolean innerClassesForKeys = true;

  /**
   * Comma separated list of java packages that should be generated. Null means that all packages
   * should be generated. Classes for tables coming from Liquibase (changelog and lock tables) are
   * never generated.
   */
  public String[] packages = new String[0];

  /**
   * The folder where source will be generated to.
   */
  public String targetFolder;

  /**
   * The simple constructor.
   *
   * @param capability
   *          A schema name with optional capability filter where liquibase should start searching
   *          for changesets.
   * @param targetFolder
   *          the folder where source will be generated to.
   */
  public GenerationProperties(final String capability, final String[] bundleLocations,
      final String targetFolder) {
    this.targetFolder = targetFolder;
    this.bundleLocations = bundleLocations;
    this.capability = capability;
  }

  public void setCapability(final String schema) {
    capability = schema;
  }

  public void setConfigurationPath(final String configurationPath) {
    this.configurationPath = configurationPath;
  }

  public void setContexts(final String contexts) {
    this.contexts = contexts;
  }

  public void setDefaultSchema(final String defaultSchema) {
    this.defaultSchema = defaultSchema;
  }

  public void setHackWires(final boolean strict) {
    hackWires = strict;
  }

  public void setInnerClassesForKeys(final boolean innerClassesForKeys) {
    this.innerClassesForKeys = innerClassesForKeys;
  }

  public void setPackages(final String[] packages) {
    validatePackages(packages);
    this.packages = packages;
  }

  public void setTargetFolder(final String targetFolder) {
    this.targetFolder = targetFolder;
  }

  private void validatePackages(final String[] packages) {
    if (packages == null) {
      throw new IllegalArgumentException(
          "Packages cannot be null. In case all packages should be included, a"
              + " zero length array should be used.");
    }
  }

}
