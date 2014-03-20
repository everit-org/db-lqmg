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

import com.mysema.query.codegen.EntityType;
import com.mysema.query.sql.codegen.MetaDataExporter;

public class LQMGMetadataExporter extends MetaDataExporter {

    @Override
    protected EntityType createEntityType(String schemaName, String tableName, String className) {
        EntityType entityType = super.createEntityType(schemaName, tableName, className);
        // entityType.getData().remove("schema");
        // TODO remove the schema from data if necessary
        return entityType;
    }
}
