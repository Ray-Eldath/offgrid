package ray.eldath.offgrid.handler

import org.http4k.contract.security.Security
import ray.eldath.offgrid.factory.CreateEntityFactory
import ray.eldath.offgrid.factory.DeleteEntityFactory
import ray.eldath.offgrid.factory.ListEntityFactory
import ray.eldath.offgrid.factory.ModifyEntityFactory
import ray.eldath.offgrid.util.EntityType
import ray.eldath.offgrid.util.Permission
import ray.eldath.offgrid.util.RouteTag

class ListEndpoint(credentials: Credentials, security: Security) :
    ContractHandler by ListEntityFactory(credentials, security).makeHandler("/endpoint", EntityType.Endpoint, {
        summary = "List endpoint"
        tags += RouteTag.Endpoint
    }, Permission.ListEndpoint)

class CreateEndpoint(credentials: Credentials, security: Security) :
    ContractHandler by CreateEntityFactory(credentials, security).makeHandler("/endpoint", EntityType.Endpoint, {
        summary = "Create endpoint"
        tags += RouteTag.Endpoint
    }, Permission.CreateEndpoint)

class ModifyEndpoint(credentials: Credentials, security: Security) :
    ContractHandler by ModifyEntityFactory(credentials, security).makeHandler("/endpoint", EntityType.Endpoint, {
        summary = "Modify endpoint"
        tags += RouteTag.Endpoint
    }, Permission.ModifyEndpoint)

class DeleteEndpoint(credentials: Credentials, security: Security) :
    ContractHandler by DeleteEntityFactory(credentials, security).makeHandler("/endpoint", EntityType.Endpoint, {
        summary = "Delete endpoint"
        tags += RouteTag.Endpoint
    }, Permission.DeleteEndpoint)