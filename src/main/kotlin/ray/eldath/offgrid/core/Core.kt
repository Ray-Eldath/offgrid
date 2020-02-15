package ray.eldath.offgrid.core

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.http4k.contract.contract
import org.http4k.contract.openapi.ApiInfo
import org.http4k.contract.openapi.v3.OpenApi3
import org.http4k.core.*
import org.http4k.filter.CorsPolicy
import org.http4k.filter.DebuggingFilters
import org.http4k.filter.MetricFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.Jackson
import org.http4k.routing.bind
import org.http4k.server.ApacheServer
import org.http4k.server.asServer
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import ray.eldath.offgrid.component.ApiExceptionHandler
import ray.eldath.offgrid.component.BearerSecurity
import ray.eldath.offgrid.handler.*
import ray.eldath.offgrid.util.RouteTag
import java.io.File


object Core {
    var debug = false
        private set
    val enableDebug: Boolean
        get() {
            debug = true
            (LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.DEBUG
            System.err.println("debug mode is enabled.")
            return true
        }

    val security = BearerSecurity
    val credentials = BearerSecurity.credentials

    private val allRoutes = arrayListOf<ContractHandler>()

    init {
        allRoutes += Login(credentials, security)
        allRoutes += Logout(credentials, security)
        allRoutes += Register(credentials, security)
        allRoutes += ConfirmEmail.ValidateUrlToken(credentials, security)
        allRoutes += ConfirmEmail.SubmitUserApplication(credentials, security)
        allRoutes += ResetPassword.Invoke(credentials, security)
        allRoutes += ResetPassword.Verify(credentials, security)
        allRoutes += ResetPassword.Submit(credentials, security)

        allRoutes += ListUserApplications(credentials, security)
        allRoutes += ApproveUserApplication(credentials, security)
        allRoutes += RejectUserApplication(credentials, security)
        allRoutes += ResetUserApplication(credentials, security)

        allRoutes += ListUsers(credentials, security)
        allRoutes += ModifyUser(credentials, security)
        allRoutes += DeleteUser(credentials, security)

        allRoutes += Self(credentials, security)
        allRoutes += DeleteSelf(credentials, security)

        allRoutes += Echo(credentials, security)
        allRoutes += Require(credentials, security)
    }

    private val metrics = SimpleMeterRegistry() // TODO: test only. substitute for a suitable one.
    val jooqContext: DSLContext by lazy {
        if (!debug)
            HikariConfig().apply {
                poolName = "offgrid"
                jdbcUrl = getEnv("OFFGRID_BACKEND_JDBC_URL")
                username = "offgrid"
                password = getEnv("OFFGRID_DATABASE_PASSWORD")
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                metricRegistry = metrics
            }.let { DSL.using(HikariDataSource(it), SQLDialect.MYSQL) }
        else DSL.using("jdbc:mysql://localhost:3306/offgrid", "offgrid", "1234")
    }

    private const val ROOT = ""
    private val filterChain by lazy {
        (if (debug) {
            DebuggingFilters.PrintRequest(debugStream = true)
                .also { System.err.println("filter PrintRequest is installed.") }
                .then(ServerFilters.Cors(CorsPolicy(listOf("*"), listOf("*"), Method.values().toList())))
                .also {
                    System.err.println(
                        "filter CORS with unsafe global permissive is installed. note that CORS may still needed " +
                                "through inverse proxy in production, but global permissive should be limited to debug only."
                    )
                }
        } else Filter.NoOp)
            .then(ServerFilters.CatchLensFailure)
            .then(MetricFilters.Server.RequestCounter(metrics))
            .then(ApiExceptionHandler.filter)
    }

    @JvmStatic
    fun main(args: Array<String>) {
        println("Starting Offgrid...")
        if (args.size == 1 && args[0] == "debug")
            enableDebug
        loadEnv()
        jooqContext // init after env are loaded
        val globalRenderer = OpenApi3(ApiInfo("Offgrid", "v1.0", "Backend API for Offgrid."), Jackson)
        val descPath = "/swagger.json"

        val http: HttpHandler =
            ROOT bind contract {
                renderer = globalRenderer
                descriptionPath = descPath
                routes += allRoutes
                    .map { it.compile() }
                    .let { r -> if (debug) r else r.filterNot { it.tags.contains(RouteTag.Debug) } }
                preSecurityFilter = filterChain
            }

        http.asServer(ApacheServer(8080)).start()
        println("Offgrid now ready for requests.")
    }

    fun loadEnv() {
        val file = File(System.getProperty("user.dir") + "/.env")
        if (!file.exists())
            return

        file.bufferedReader().useLines { seq ->
            seq.map { it.split("=") }
                .filter { it.size >= 2 }
                .forEach {
                    System.setProperty("offgrid.env.${it[0]}", it.drop(1).joinToString(""))
                }
        }
    }

    fun getEnv(key: String): String =
        if (System.getenv(key) != null)
            System.getenv(key)
        else
            System.getProperty("offgrid.env.$key")
}