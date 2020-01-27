package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.contract.security.Security
import org.http4k.core.Request
import ray.eldath.offgrid.dao.User

abstract class ContractHandler(val credentials: (Request) -> User, val optionalSecurity: Security) {

    abstract fun compile(): ContractRoute
}

typealias Credentials = (Request) -> User