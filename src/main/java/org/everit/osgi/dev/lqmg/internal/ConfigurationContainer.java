/**
 * This file is part of Everit - Liquibase-QueryDSL Model Generator.
 *
 * Everit - Liquibase-QueryDSL Model Generator is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Everit - Liquibase-QueryDSL Model Generator is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Everit - Liquibase-QueryDSL Model Generator.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.everit.osgi.dev.lqmg.internal;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.everit.osgi.dev.lqmg.LQMGException;
import org.everit.osgi.dev.lqmg.schema.LQMGAbstractEntityType;
import org.everit.osgi.dev.lqmg.schema.LQMGEntitiesType;
import org.everit.osgi.dev.lqmg.schema.LQMGEntityType;
import org.everit.osgi.dev.lqmg.schema.LQMGNamingRuleType;
import org.everit.osgi.dev.lqmg.schema.LQMGType;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

public class ConfigurationContainer {

    private static class NullConfigValue extends ConfigValue<LQMGAbstractEntityType> {

        public NullConfigValue() {
            super(null, null, null);
        }

    }

    private final Map<ConfigKey, ConfigValue<? extends LQMGAbstractEntityType>> cache =
            new HashMap<ConfigKey, ConfigValue<? extends LQMGAbstractEntityType>>();

    private final Map<ConfigKey, ConfigValue<LQMGEntityType>> entityConfigs =
            new HashMap<ConfigKey, ConfigValue<LQMGEntityType>>();

    private final JAXBContext jaxbContext;

    private final Map<ConfigKey, ConfigValue<LQMGEntityType>> mainEntityConfigs =
            new HashMap<ConfigKey, ConfigValue<LQMGEntityType>>();

    private final Map<ConfigKey, ConfigValue<LQMGNamingRuleType>> mainNamingRuleConfigs =
            new HashMap<ConfigKey, ConfigValue<LQMGNamingRuleType>>();

    private final Map<ConfigKey, ConfigValue<LQMGNamingRuleType>> namingRuleConfigs =
            new HashMap<ConfigKey, ConfigValue<LQMGNamingRuleType>>();

    private final Map<String, Pattern> patternsByRegex = new HashMap<String, Pattern>();

    private final Set<ConfigPath> processedConfigs = new HashSet<ConfigPath>();

    public ConfigurationContainer() {
        try {
            this.jaxbContext = JAXBContext.newInstance(LQMGType.class.getPackage().getName(), this.getClass()
                    .getClassLoader());
        } catch (JAXBException e) {
            throw new LQMGException("Could not create JAXBContext for configuration", e);
        }
    }

    public void addConfiguration(ConfigPath configPath) {
        if (!processedConfigs.add(configPath)) {
            // If the config file is already processed, just return
            return;
        }
        Bundle bundle = configPath.getBundle();
        String resource = configPath.getResource();
        BundleWiring bundleWiring = bundle.adapt(BundleWiring.class);
        ClassLoader classLoader = bundleWiring.getClassLoader();
        URL configurationURL = classLoader.getResource(resource);
        if (configurationURL == null) {
            throw new LQMGException("Configuration file not found on the specified path '" + resource + "' in bundle "
                    + bundle.toString(), null);
        }
        LQMGType lqmgType = unmarshalConfiguration(configurationURL);
        processLQMGType(lqmgType, resource, bundle);
    }

    private <T extends LQMGAbstractEntityType> void addValueToConfigMap(ConfigKey configKey,
            ConfigValue<T> configValue,
            Map<ConfigKey, ConfigValue<T>> configMap) {

        ConfigValue<? extends LQMGAbstractEntityType> cachedValue = cache.get(configKey);
        if (cachedValue != null) {
            cache.remove(configKey);
        }

        ConfigValue<T> existingValue = configMap.get(configKey);
        if (existingValue != null) {
            StringBuilder sb = new StringBuilder("Configuration is defined more than once: ").append(
                    configKey.toString()).append("\n");

            Bundle bundle = configValue.getBundle();
            if (bundle != null) {
                sb.append("  Bundle: ").append(bundle.toString()).append("; ");
            }
            sb.append("Path: ").append(configValue.getConfigurationXMLPath()).append("\n");

            Bundle existingValueBundle = existingValue.getBundle();
            if (existingValueBundle != null) {
                sb.append("  Bundle: ").append(existingValueBundle.toString()).append("; ");
            }
            sb.append("Path: ").append(existingValue.getConfigurationXMLPath());

            throw new LQMGException(sb.toString(), null);
        }
        configMap.put(configKey, configValue);
    }

    public LQMGAbstractEntityType findConfigForKey(ConfigKey key) {
        ConfigValue<? extends LQMGAbstractEntityType> configValue = cache.get(key);
        if (configValue != null) {
            if (configValue instanceof NullConfigValue) {
                return null;
            } else {
                return configValue.getEntityConfiguration();
            }
        }

        configValue = findEntityConfigInMap(key, mainEntityConfigs);
        if (configValue != null) {
            return configValue.getEntityConfiguration();
        }

        return null;
    }

    private ConfigValue<LQMGEntityType> findEntityConfigInMap(final ConfigKey key,
            final Map<ConfigKey, ConfigValue<LQMGEntityType>> map) {

        ConfigValue<LQMGEntityType> configValue = map.get(key);
        if (configValue != null) {
            return configValue;
        }
        if (key.getSchemaName() != null) {
            ConfigKey nullSchemaConfigKey = new ConfigKey(null, key.getEntity());
            return map.get(nullSchemaConfigKey);
        }
        return null;
    }

    private ConfigValue<LQMGNamingRuleType> findNamingRuleInMap(final ConfigKey key,
            final Map<ConfigKey, ConfigValue<LQMGNamingRuleType>> map) {

        String entityName = key.getEntity();

        if (key.getSchemaName() != null) {
            String schemaName = key.getSchemaName();
            for (Entry<ConfigKey, ConfigValue<LQMGNamingRuleType>> entry : map.entrySet()) {
                ConfigKey entryKey = entry.getKey();
                if (schemaName.equals(entryKey.getSchemaName())) {

                }
            }
        }

        return null;
    }

    private boolean matches(String regex, String value) {
        Pattern pattern = patternsByRegex.get(regex);
        if (pattern == null) {
            pattern = Pattern.compile(regex);
            patternsByRegex.put(regex, pattern);
        }
        Matcher matcher = pattern.matcher(value);
        // TODO
        return matcher.matches();
    }

    private void processLQMGType(LQMGType lqmgType, String xmlConfigurationPath, Bundle bundle) {
        String defaultPackageName = lqmgType.getDefaultPackage();
        String defaultSchemaName = lqmgType.getDefaultSchema();

        LQMGEntitiesType entities = lqmgType.getEntities();
        if (entities != null) {
            List<LQMGAbstractEntityType> entityAndEntitySet = entities.getEntityAndNamingRule();
            for (LQMGAbstractEntityType lqmgAbstractEntity : entityAndEntitySet) {
                if (lqmgAbstractEntity.getPackage() == null) {
                    lqmgAbstractEntity.setPackage(defaultPackageName);
                }

                if (lqmgAbstractEntity.getSchema() == null) {
                    lqmgAbstractEntity.setSchema(defaultSchemaName);
                }

                if (lqmgAbstractEntity.isUseSchema() == null) {
                    lqmgAbstractEntity.setUseSchema(lqmgType.isDefaultUseSchema());
                }

                if (lqmgAbstractEntity instanceof LQMGEntityType) {
                    LQMGEntityType lqmgEntity = (LQMGEntityType) lqmgAbstractEntity;
                    ConfigKey configKey = new ConfigKey(lqmgEntity.getSchema(),
                            lqmgEntity.getName());
                    ConfigValue<LQMGEntityType> configValue = new ConfigValue<LQMGEntityType>(lqmgEntity,
                            bundle, xmlConfigurationPath);

                    if (bundle == null) {
                        addValueToConfigMap(configKey, configValue, mainEntityConfigs);
                    } else {
                        addValueToConfigMap(configKey, configValue, entityConfigs);
                    }
                } else {
                    LQMGNamingRuleType lqmgEntitySet = (LQMGNamingRuleType) lqmgAbstractEntity;
                    ConfigKey configKey = new ConfigKey(lqmgEntitySet.getSchema(),
                            lqmgEntitySet.getRegex());
                    ConfigValue<LQMGNamingRuleType> configValue = new ConfigValue<LQMGNamingRuleType>(lqmgEntitySet,
                            bundle, xmlConfigurationPath);

                    if (bundle == null) {
                        addValueToConfigMap(configKey, configValue, mainNamingRuleConfigs);
                    } else {
                        addValueToConfigMap(configKey, configValue, namingRuleConfigs);
                    }
                }
            }
        }
    }

    private LQMGType unmarshalConfiguration(URL configurationURL) {
        Unmarshaller unmarshaller;
        try {
            unmarshaller = jaxbContext.createUnmarshaller();

            @SuppressWarnings("unchecked")
            JAXBElement<LQMGType> rootElement = (JAXBElement<LQMGType>) unmarshaller.unmarshal(configurationURL);
            return rootElement.getValue();
        } catch (JAXBException e) {
            throw new LQMGException("Could not unmarshal LQMG configuration: " + configurationURL.toExternalForm(), e);
        }
    }
}
