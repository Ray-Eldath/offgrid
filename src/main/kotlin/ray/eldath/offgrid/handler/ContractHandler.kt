package ray.eldath.offgrid.handler

import org.http4k.contract.ContractRoute
import org.http4k.core.Request
import ray.eldath.offgrid.component.InboundUser

interface ContractHandler {
    fun compile(): ContractRoute
}

typealias Credentials = (Request) -> InboundUser