package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.security.Security
import org.http4k.core.Request
import ray.eldath.offgrid.component.InboundUser

abstract class ContractHandler(val credentials: Credentials, val optionalSecurity: Security) {

    abstract fun compile(): ContractRoute
}

typealias Credentials = (Request) -> InboundUser