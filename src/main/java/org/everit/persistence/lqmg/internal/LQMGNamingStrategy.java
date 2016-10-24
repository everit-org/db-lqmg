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
package org.everit.persistence.lqmg.internal;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.bind.JAXBElement;

import org.everit.persistence.lqmg.LQMGException;
import org.everit.persistence.lqmg.internal.schema.xml.AbstractNamingRuleType;
import org.everit.persistence.lqmg.internal.schema.xml.ClassNameRuleType;
import org.everit.persistence.lqmg.internal.schema.xml.PropertyMappingType;
import org.everit.persistence.lqmg.internal.schema.xml.PropertyMappingsType;
import org.everit.persistence.lqmg.internal.schema.xml.RegexRuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.querydsl.codegen.EntityType;
import com.querydsl.sql.SchemaAndTable;
import com.querydsl.sql.codegen.DefaultNamingStrategy;
import com.querydsl.sql.codegen.support.ForeignKeyData;

/**
 * Customized {@link com.querydsl.sql.codegen.NamingStrategy} for LQMG.
 */
public class LQMGNamingStrategy extends DefaultNamingStrategy {

  /**
   * Constants for DB attributes.
   */
  private enum DBAttributeType {

    COLUMN("column"), FOREIGN_KEY("foreignKey"), PRIMARY_KEY("primaryKey");

    private String typeName;

    DBAttributeType(final String typeName) {
      this.typeName = typeName;
    }

    public String getTypeName() {
      return typeName;
    }
  }

  private static final String FK_PREFIX = "fk_";

  private static final Logger LOGGER = LoggerFactory.getLogger(LQMGNamingStrategy.class);

  private static final String PK_PREFIX = "pk_";

  private final Map<SchemaAndTable, String> classNameCache = new HashMap<>();

  private final ConfigurationContainer configurationContainer;

  private final Set<String> packages;

  public LQMGNamingStrategy(final ConfigurationContainer configurationContainer,
      final String[] packages) {
    this.configurationContainer = configurationContainer;
    this.packages = new HashSet<String>(Arrays.asList(packages));
  }

  @Override
  public String getClassName(final SchemaAndTable schemaAndTable) {

    String simpleName = classNameCache.get(schemaAndTable);
    if (simpleName != null) {
      return simpleName;
    }

    ConfigValue<? extends AbstractNamingRuleType> configValue =
        configurationContainer.findConfigForEntity(schemaAndTable);
    if (configValue == null) {
      return null;
    }

    AbstractNamingRuleType namingRule = configValue.namingRule;
    if (namingRule instanceof RegexRuleType) {
      RegexRuleType regexRule = (RegexRuleType) namingRule;
      String regex = regexRule.getRegex();
      String replacement = regexRule.getReplacement();
      Pattern pattern = configurationContainer.getPatternByRegex(regex);
      Matcher matcher = pattern.matcher(schemaAndTable.getTable());
      String replacedEntityName = matcher.replaceAll(replacement);
      simpleName = super.getClassName(replacedEntityName);
    } else if (namingRule instanceof ClassNameRuleType) {
      simpleName = ((ClassNameRuleType) namingRule).getClazz();
    }

    String prefix = namingRule.getPrefix();
    if (prefix != null) {
      simpleName = prefix + simpleName;
    }

    String suffix = namingRule.getSuffix();
    if (suffix != null) {
      simpleName = simpleName + suffix;
    }

    classNameCache.put(schemaAndTable, simpleName);

    if (simpleName == null) {
      throw new LQMGException(
          "Cannot resolve class name for '" + schemaAndTable.getTable() + "' table in '"
              + schemaAndTable.getSchema() + "' schema.",
          null);
    }
    return simpleName;
  }

  @Override
  public String getDefaultVariableName(final EntityType entityType) {

    SchemaAndTable schemaAndTable = getSchemaAndTable(entityType);

    String propertyName = getClassName(schemaAndTable);
    ConfigValue<? extends AbstractNamingRuleType> config =
        configurationContainer.findConfigForEntity(schemaAndTable);

    AbstractNamingRuleType namingRule = config.getNamingRule();
    String prefix = namingRule.getPrefix();
    String suffix = namingRule.getSuffix();

    if (prefix != null) {
      propertyName = propertyName.substring(prefix.length());
    }

    if (suffix != null) {
      propertyName = propertyName.substring(0, propertyName.length() - suffix.length());
    }

    propertyName = Character.toLowerCase(propertyName.charAt(0)) + propertyName.substring(1);
    return escape(entityType, propertyName);
  }

  private List<JAXBElement<PropertyMappingType>> getForeignKeyAndPrimaryKeyAndColumnForEntity(
      final EntityType entityType) {

    SchemaAndTable schemaAndTable = getSchemaAndTable(entityType);

    ConfigValue<? extends AbstractNamingRuleType> configValue =
        configurationContainer.findConfigForEntity(schemaAndTable);

    if (configValue == null) {
      return null;
    }

    AbstractNamingRuleType namingRule = configValue.getNamingRule();
    if (!(namingRule instanceof ClassNameRuleType)) {
      return null;
    }

    PropertyMappingsType propertyMappings = ((ClassNameRuleType) namingRule).getPropertyMappings();
    if (propertyMappings == null) {
      return null;
    }

    return propertyMappings.getPrimaryKeyAndForeignKeyAndColumn();
  }

  @Override
  public String getPackage(final String basePackage, final SchemaAndTable schemaAndTable) {

    String simpleName = getClassName(schemaAndTable);
    if (simpleName == null) {
      return super.getPackage(basePackage, schemaAndTable);
    }

    ConfigValue<? extends AbstractNamingRuleType> configValue =
        configurationContainer.findConfigForEntity(schemaAndTable);
    AbstractNamingRuleType namingRule = configValue.getNamingRule();
    String packageName = namingRule.getPackage();

    return packageName;
  }

  @Override
  public String getPropertyName(final String columnName, final EntityType entityType) {
    String propertyName =
        getPropertyNameFromMapping(columnName, entityType, DBAttributeType.COLUMN);
    if (propertyName != null) {
      return propertyName;
    } else {
      return super.getPropertyName(columnName, entityType);
    }
  }

  @Override
  public String getPropertyNameForForeignKey(final String fkName, final EntityType entityType) {
    String localFkName = fkName;
    String propertyName =
        getPropertyNameFromMapping(localFkName, entityType, DBAttributeType.FOREIGN_KEY);
    if (propertyName != null) {
      return propertyName;
    } else {
      if (localFkName.toLowerCase(Locale.ENGLISH).startsWith(FK_PREFIX)) {
        localFkName = localFkName.substring(FK_PREFIX.length()) + "_" + localFkName.substring(0, 2);
      }
      return getSuperPropertyName(localFkName, entityType);
    }
  }

  @Override
  public String getPropertyNameForPrimaryKey(final String pkName, final EntityType entityType) {
    String localPkName = pkName;
    String propertyName =
        getPropertyNameFromMapping(localPkName, entityType, DBAttributeType.PRIMARY_KEY);
    if (propertyName != null) {
      return propertyName;
    } else {
      if (localPkName.toLowerCase(Locale.ENGLISH).startsWith(PK_PREFIX)) {
        localPkName = localPkName.substring(PK_PREFIX.length()) + "_" + localPkName.substring(0, 2);
      }
      return getSuperPropertyName(localPkName, entityType);
    }
  }

  private String getPropertyNameFromMapping(final String originalName,
      final EntityType entityType,
      final DBAttributeType dbAttributeType) {

    List<JAXBElement<PropertyMappingType>> foreignKeyAndPrimaryKeyAndColumn =
        getForeignKeyAndPrimaryKeyAndColumnForEntity(entityType);

    if (foreignKeyAndPrimaryKeyAndColumn == null) {
      return null;
    }

    PropertyMappingType selectedMapping = null;
    Iterator<JAXBElement<PropertyMappingType>> iterator =
        foreignKeyAndPrimaryKeyAndColumn.iterator();
    while (iterator.hasNext() && (selectedMapping == null)) {
      JAXBElement<?> jaxbElement = iterator.next();
      if (dbAttributeType.getTypeName().equals(jaxbElement.getName().getLocalPart())) {
        PropertyMappingType propertyMapping = (PropertyMappingType) jaxbElement.getValue();
        if (propertyMapping.getName().equals(originalName)) {
          selectedMapping = propertyMapping;
        }
      }
    }

    if (selectedMapping != null) {
      return selectedMapping.getProperty();
    } else {
      return null;
    }
  }

  private SchemaAndTable getSchemaAndTable(final EntityType entityType) {
    String tableName = entityType.getData().get("table").toString();
    String schemaName = (String) entityType.getData().get("schema");

    SchemaAndTable schemaAndTable = new SchemaAndTable(schemaName, tableName);
    return schemaAndTable;
  }

  private String getSuperPropertyName(final String columnName, final EntityType entityType) {
    return super.getPropertyName(columnName, entityType);
  }

  private boolean shouldGenerate(final SchemaAndTable schemaAndTable) {

    ConfigValue<? extends AbstractNamingRuleType> configValue =
        configurationContainer.findConfigForEntity(schemaAndTable);
    if (configValue == null) {
      LOGGER.info("No configuration for table '" + schemaAndTable.getTable() + "' in schema '"
          + schemaAndTable.getSchema());
      return false;
    }

    AbstractNamingRuleType namingRule = configValue.getNamingRule();

    String javaPackage = namingRule.getPackage();
    if ((packages.size() > 0) && !packages.contains(javaPackage)) {
      LOGGER.info("Java package '" + javaPackage + "' is not included, ignoring table '"
          + schemaAndTable.getTable() + "' from schema '." + schemaAndTable.getSchema());
      return false;
    }

    return true;
  }

  @Override
  public boolean shouldGenerateClass(final SchemaAndTable schemaAndTable) {

    if (shouldGenerate(schemaAndTable)) {
      return super.shouldGenerateClass(schemaAndTable);
    }

    return false;
  }

  @Override
  public boolean shouldGenerateForeignKey(final SchemaAndTable schemaAndTable,
      final ForeignKeyData foreignKeyData) {

    String fkSchemaName = foreignKeyData.getSchema();
    String fkTableName = foreignKeyData.getTable();
    SchemaAndTable fkSchemaAndTable = new SchemaAndTable(fkSchemaName, fkTableName);

    if (shouldGenerate(fkSchemaAndTable)) {
      return super.shouldGenerateForeignKey(schemaAndTable, foreignKeyData);
    }

    return false;
  }

}
