package ray.eldath.offgrid.handler

import org.http4k.contract.security.Security
import ray.eldath.offgrid.factory.CreateEntityFactory
import ray.eldath.offgrid.factory.DeleteEntityFactory
import ray.eldath.offgrid.factory.ListEntityFactory
import ray.eldath.offgrid.factory.ModifyEntityFactory
import ray.eldath.offgrid.util.EntityType
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.RouteTag

class ListDataSource(credentials: Credentials, security: Security) :
    ContractHandler by ListEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "List datasource"
        tags += RouteTag.DataSource
    }, Permission.ListDataSource)

class CreateDataSource(credentials: Credentials, security: Security) :
    ContractHandler by CreateEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "Create datasource"
        tags += RouteTag.DataSource
    }, Permission.CreateDataSource)

class ModifyDataSource(credentials: Credentials, security: Security) :
    ContractHandler by ModifyEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "Modify datasource"
        tags += RouteTag.DataSource
    }, Permission.ModifyDataSource)

class DeleteDataSource(credentials: Credentials, security: Security) :
    ContractHandler by DeleteEntityFactory(credentials, security).makeHandler("/datasource", EntityType.DataSource, {
        summary = "Delete datasource"
        tags += RouteTag.DataSource
    }, Permission.DeleteDataSource)