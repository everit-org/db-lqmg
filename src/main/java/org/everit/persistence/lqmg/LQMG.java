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

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.ServiceLoader;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.osgi.framework.internal.core.FrameworkProperties;
import org.everit.persistence.liquibase.ext.osgi.EOSGiResourceAccessor;
import org.everit.persistence.liquibase.ext.osgi.LiquibaseEOSGiConstants;
import org.everit.persistence.liquibase.ext.osgi.util.BundleResource;
import org.everit.persistence.liquibase.ext.osgi.util.LiquibaseOSGiUtil;
import org.everit.persistence.lqmg.internal.ConfigPath;
import org.everit.persistence.lqmg.internal.ConfigurationContainer;
import org.everit.persistence.lqmg.internal.EquinoxHackUtilImpl;
import org.everit.persistence.lqmg.internal.HackUtil;
import org.everit.persistence.lqmg.internal.LQMGNamingStrategy;
import org.everit.persistence.lqmg.internal.liquibase.LQMGChangeExecListener;
import org.h2.Driver;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;
import org.osgi.framework.wiring.FrameworkWiring;

import com.querydsl.sql.codegen.MetaDataExporter;
import com.querydsl.sql.codegen.NamingStrategy;

import liquibase.Liquibase;
import liquibase.changelog.visitor.ChangeExecListener;
import liquibase.database.AbstractJdbcDatabase;
import liquibase.database.ObjectQuotingStrategy;
import liquibase.database.core.H2Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ResourceAccessor;

/**
 * This class responsible for generate QueryDSL JAVA classes.
 */
public final class LQMG {

  public static final String CAPABILITY_LQMG_CONFIG_RESOURCE = "lqmg.config.resource";

  private static HackUtil frameworkUtil = new EquinoxHackUtilImpl();

  /**
   * The {@link Logger} instance for logging.
   */
  private static final Logger LOGGER = Logger.getLogger(LQMG.class.getName());

  private static void checkMatchingBundleResourceSize(final GenerationProperties parameters,
      final List<BundleResource> bundleResources) {
    if (bundleResources.size() > 1) {
      LOGGER.log(Level.WARNING,
          "Found multiple bundles containing matching capabilities for schema"
              + " expression: '" + parameters.capability
              + "'. Using the first one from list: "
              + bundleResources.toString());
    }
  }

  private static String createDataBaseURL(final String defaultSchema) {
    StringBuilder sb = new StringBuilder("jdbc:h2:mem:;DATABASE_TO_UPPER=false");
    if (defaultSchema != null) {
      sb.append(";INIT=CREATE SCHEMA IF NOT EXISTS \"").append(defaultSchema)
          .append("\"\\;SET SCHEMA \"")
          .append(defaultSchema).append("\"");
    }
    return sb.toString();
  }

  private static File createTempDirectory() throws IOException {
    final File temp = File.createTempFile("lqmg-",
        Long.toString(System.nanoTime()));

    if (!(temp.delete())) {
      throw new IOException("Could not delete temp file: "
          + temp.getAbsolutePath());
    }

    if (!(temp.mkdir())) {
      throw new IOException("Could not create temp directory: "
          + temp.getAbsolutePath());
    }

    return temp;
  }

  private static void deleteFolder(final File folder) {

    if (folder == null) {
      return;
    }

    File[] files = folder.listFiles();
    if (files != null) { // some JVMs return null for empty dirs
      for (File f : files) {
        if (f.isDirectory()) {
          LQMG.deleteFolder(f);
        } else {
          if (!f.delete()) {
            LOGGER.warning("Failed to delete file [" + f.getAbsolutePath() + "]");
          }
        }
      }
    }
    if (!folder.delete()) {
      LOGGER.warning("Failed to delete folder [" + folder.getAbsolutePath() + "]");
    }
  }

  private static void exportMetaData(final GenerationProperties parameters,
      final Connection connection, final ConfigurationContainer configurationContainer)
      throws SQLException {

    LOGGER.log(Level.INFO, "Start meta data export.");

    MetaDataExporter metaDataExporter = new MetaDataExporter();

    NamingStrategy namingStrategy =
        new LQMGNamingStrategy(configurationContainer, parameters.packages);

    metaDataExporter.setNamePrefix("");
    metaDataExporter.setNameSuffix("");
    metaDataExporter.setNamingStrategy(namingStrategy);
    metaDataExporter.setSchemaToPackage(true);
    metaDataExporter.setTargetFolder(new File(parameters.targetFolder));
    metaDataExporter.setInnerClassesForKeys(parameters.innerClassesForKeys);
    metaDataExporter.export(connection.getMetaData());

    LOGGER.log(Level.INFO, "Finish meta data export.");
  }

  /**
   * Generate the JAVA classes to QueryDSL from LiquiBase XML.
   *
   * @param parameters
   *          the parameters for the generation. See more {@link GenerationProperties}.
   */
  public static void generate(final GenerationProperties parameters) {
    Framework osgiContainer = null;
    File tempDirectory = null;
    try {
      tempDirectory = LQMG.createTempDirectory();
      osgiContainer =
          LQMG.startOSGiContainer(parameters.bundleLocations, tempDirectory.getAbsolutePath());
      List<BundleResource> bundleResources = LiquibaseOSGiUtil
          .findBundlesBySchemaExpression(parameters.capability,
              osgiContainer.getBundleContext(), Bundle.RESOLVED);

      if (bundleResources.size() == 0) {
        if (parameters.hackWires) {
          LOGGER.info(
              "No matching bundle found. Trying to find unresolved bundles and hack their wires.");
          frameworkUtil.hackBundles(osgiContainer, tempDirectory);
          FrameworkWiring frameworkWiring = osgiContainer.adapt(FrameworkWiring.class);
          frameworkWiring.resolveBundles(null);

          bundleResources = LiquibaseOSGiUtil
              .findBundlesBySchemaExpression(parameters.capability,
                  osgiContainer.getBundleContext(), Bundle.RESOLVED);
        } else {
          LOGGER.severe("No matching bundle found. Probably setting hackWires to true would help");
        }
        if (bundleResources.size() == 0) {
          LQMG.throwCapabilityNotFound(parameters, osgiContainer);
        }
      }

      LQMG.checkMatchingBundleResourceSize(parameters, bundleResources);

      BundleResource bundleResource = bundleResources.get(0);
      LQMG.tryCodeGeneration(parameters, bundleResource.bundle, bundleResource.attributes);

    } catch (IOException e) {

      LOGGER.log(Level.SEVERE, "Could not create temp directory", e);
      return;

    } catch (BundleException e) {

      LOGGER.log(Level.SEVERE, "Could not start embedded OSGi framework", e);

    } finally {

      LQMG.stopFramework(osgiContainer);
      LQMG.deleteFolder(tempDirectory);
    }

  }

  private static void logUnresolvedBundles(final Framework osgiContainer) {
    BundleContext systemBundleContext = osgiContainer.getBundleContext();
    Bundle[] bundles = systemBundleContext.getBundles();

    for (Bundle bundle : bundles) {
      if (bundle.getState() == Bundle.INSTALLED) {
        try {
          bundle.start();
        } catch (BundleException e) {
          LOGGER.log(Level.WARNING, "The bundle " + bundle.toString() + " could not be resolved",
              e);
        }
      }
    }
  }

  /**
   * HACK to make Equinox using the classloader of the system even if LQMG is called multiple times.
   */
  private static void resetFrameworkProperties() {
    // FIXME avoid having to do this hack!!! equinox internal classes should be available via the
    // jvm classloader
    Class<FrameworkProperties> clazz = FrameworkProperties.class;
    try {
      Field propertiesField = clazz.getDeclaredField("properties");
      propertiesField.setAccessible(true);
      propertiesField.set(null, null);
    } catch (NoSuchFieldException e) {
      throw new RuntimeException(e);
    } catch (SecurityException e) {
      throw new RuntimeException(e);
    } catch (IllegalArgumentException e) {
      throw new RuntimeException(e);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    }
  }

  private static Framework startOSGiContainer(final String[] bundleLocations,
      final String tempDirPath) throws BundleException {

    FrameworkFactory frameworkFactory = ServiceLoader
        .load(FrameworkFactory.class).iterator().next();

    Map<String, String> config = new HashMap<String, String>();
    config.put("osgi.configuration.area", tempDirPath);
    config.put("osgi.baseConfiguration.area", tempDirPath);
    config.put("osgi.sharedConfiguration.area", tempDirPath);
    config.put("osgi.instance.area", tempDirPath);
    config.put("osgi.user.area", tempDirPath);
    config.put("osgi.hook.configurators.exclude",
        "org.eclipse.core.runtime.internal.adaptor.EclipseLogHook");

    LQMG.resetFrameworkProperties();
    Framework framework = frameworkFactory.newFramework(config);
    framework.start();

    BundleContext systemBundleContext = framework.getBundleContext();
    for (String bundleLocation : bundleLocations) {
      try {
        systemBundleContext.installBundle(bundleLocation);
      } catch (BundleException e) {
        LOGGER.log(Level.WARNING, "Could not start bundle " + bundleLocation, e);
      }
    }
    FrameworkWiring frameworkWiring = framework
        .adapt(FrameworkWiring.class);
    frameworkWiring.resolveBundles(null);

    return framework;
  }

  private static void stopFramework(final Framework osgiContainer) {
    if (osgiContainer != null) {
      try {
        osgiContainer.stop();
        osgiContainer.waitForStop(0);
      } catch (BundleException e) {
        LOGGER.log(Level.SEVERE, "Could not stop embedded OSGi container during code generation",
            e);
      } catch (InterruptedException e) {
        LOGGER.log(Level.SEVERE, "Stopping of embedded OSGi container was interrupted", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  private static void throwCapabilityNotFound(final GenerationProperties parameters,
      final Framework osgiContainer) {

    LQMG.logUnresolvedBundles(osgiContainer);

    throw new LQMGException(
        "Could not find matching capability in any of the bundles for schema expression: "
            + parameters.capability,
        null);
  }

  private static void tryCodeGeneration(
      final GenerationProperties parameters, final Bundle bundle,
      final Map<String, Object> bundleCapabilityAttributes) {

    LOGGER.log(Level.INFO, "Load driver.");
    Driver h2Driver = Driver.load();
    LOGGER.log(Level.INFO, "Loaded driver.");
    Connection connection = null;
    try {
      LOGGER.log(Level.INFO, "Creating connection.");
      String defaultSchema = parameters.defaultSchema;
      String jdbcURL = LQMG.createDataBaseURL(defaultSchema);
      connection = h2Driver.connect(jdbcURL, new Properties());
      LOGGER.log(Level.INFO, "Created connection.");

      LOGGER.log(Level.INFO, "Get database.");
      AbstractJdbcDatabase database = new H2Database();
      database.setCaseSensitive(true);
      database.setObjectQuotingStrategy(ObjectQuotingStrategy.QUOTE_ALL_OBJECTS);
      database.setLiquibaseSchemaName("PUBLIC");
      if (defaultSchema != null) {
        database.setDefaultSchemaName(defaultSchema);
      }
      database.setConnection(new JdbcConnection(connection));

      LOGGER.log(Level.INFO, "Start LiquiBase and update.");

      ResourceAccessor resourceAccessor =
          new EOSGiResourceAccessor(bundle, bundleCapabilityAttributes);

      String schemaResource =
          (String) bundleCapabilityAttributes
              .get(LiquibaseEOSGiConstants.CAPABILITY_ATTR_RESOURCE);
      Liquibase liquibase = new Liquibase(schemaResource, resourceAccessor, database);

      ConfigurationContainer configContainer = new ConfigurationContainer();

      if (parameters.configurationPath != null) {
        configContainer.addConfiguration(new ConfigPath(null, parameters.configurationPath));
      }

      ChangeExecListener lqmgChangeExecListener = new LQMGChangeExecListener(configContainer);
      liquibase.setChangeExecListener(lqmgChangeExecListener);

      String contexts = parameters.contexts;

      if (contexts == null || "".equals(contexts.trim())) {
        contexts = "lqmg";
      } else {
        contexts += ",lqmg";
      }

      liquibase.update(contexts);
      LOGGER.log(Level.INFO, "Finish LiquiBase and update.");

      LQMG.exportMetaData(parameters, connection, configContainer);
    } catch (SQLException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
      // error to create connection.
      // error connection.getMetaData
      // error when export database.
      throw new LQMGException(
          "Error during try to connection the database.", e);
    } catch (LiquibaseException e) {
      LOGGER.log(Level.SEVERE, e.getMessage(), e);
      throw new LQMGException(
          "Error during processing XML file; "
              + parameters.capability,
          e);
    } finally {
      if (connection != null) {
        try {
          connection.close();
          LOGGER.log(Level.INFO, "Connection closed.");
        } catch (SQLException e) {
          LOGGER.log(Level.SEVERE, e.getMessage(), e);
          throw new LQMGException(
              "Closing the connection was unsuccessful.", e);
        }
      }
    }
  }

  /**
   * Simple constructor.
   */
  private LQMG() {
    // private constructor for utility class.
  }

}
