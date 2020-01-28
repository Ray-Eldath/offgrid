package ray.eldath.offgrid.core

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.HttpHandler
import org.http4k.core.then
import org.http4k.filter.MetricFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.bind
import org.http4k.server.ApacheServer
import org.http4k.server.asServer
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import ray.eldath.offgrid.component.ApiExceptionHandler
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.dao.OffgridTables
import ray.eldath.offgrid.handler.ContractHandler
import ray.eldath.offgrid.handler.Login
import ray.eldath.offgrid.handler.Test


object Core {
    private val allRoutes = arrayListOf<ContractHandler>()

    private val security = BearerSecurity
    private val credentials = BearerSecurity.credentials

    init {
        allRoutes += Login(credentials, security)
        allRoutes += Test(credentials, security)
    }

    private val metrics = SimpleMeterRegistry() // TODO: test only. substitute for a suitable one.

    fun prepareDatabase() {
        val datasource = HikariConfig().apply {
            poolName = "offgrid"
            jdbcUrl = System.getenv("OFFGRID_BACKEND_JDBC_URL")
            username = "offgrid"
            password = System.getenv("OFFGRID_DATABASE_PASSWORD")
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            metricRegistry = metrics
        }.let { HikariDataSource(it) }

        Database.connect(datasource)
        //
        transaction {
            SchemaUtils.createMissingTablesAndColumns(*OffgridTables.tables)
        }
    }

    private const val ROOT = ""
    private val filterChain by lazy {
        ServerFilters.CatchLensFailure
            .then(MetricFilters.Server.RequestCounter(metrics))
            .then(ApiExceptionHandler.filter)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Offgrid...")
        prepareDatabase()
        val globalRenderer = OpenApi3(ApiInfo("Offgrid", "v1.0", "Backend API for Offgrid."), Jackson)
        val descPath = "/swagger.json"

        val http: HttpHandler =
            ROOT bind contract {
                renderer = globalRenderer
                descriptionPath = descPath
                routes += allRoutes.map { it.compile() }
                preSecurityFilter = filterChain
            }

        http.asServer(ApacheServer(8080)).start()
        println("Offgrid now ready for requests.")
    }
}