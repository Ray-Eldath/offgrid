package ray.eldath.offgrid.test.handler

import org.http4k.core.*
import org.http4k.lens.BiDiBodyLens
import org.junit.jupiter.api.*
import ray.eldath.offgrid.factory.CreateEntityFactory
import ray.eldath.offgrid.factory.CreateEntityFactory.CreateEntity.Companion.CreateEndpointResponse
import ray.eldath.offgrid.factory.Entities
import ray.eldath.offgrid.factory.ListEntityFactory
import ray.eldath.offgrid.handler.CreateEndpoint
import ray.eldath.offgrid.handler.DeleteEndpoint
import ray.eldath.offgrid.handler.ListEndpoint
import ray.eldath.offgrid.handler.ModifyEndpoint
import ray.eldath.offgrid.test.Context.MockSecurity
import ray.eldath.offgrid.test.TestDatabase
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestEntity {
    private val mock = Entities.EntityName.mock

    private val createEndpoint = CreateEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
    private val listEndpoint = ListEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
    private val modifyEndpoint = ModifyEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
    private val deleteEndpoint = DeleteEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()

    private lateinit var createResponse: CreateEndpointResponse

    @Test
    @Order(1)
    fun `should create successfully`() {
        createResponse =
            createEndpoint(
                req(Method.PUT)
                    .with(Entities.EntityName.lens of mock)
            ).resp(CreateEntityFactory.CreateEntity.responseLens)
    }

    @Test
    @Order(2)
    fun `should be listed correctly`() {
        val resp = listEndpoint(req(Method.GET)).resp(ListEntityFactory.ListEntity.responseLens)

        expect {
            that(resp.total).isGreaterThanOrEqualTo(1)
            that(resp.result).any {
                get { id }.isEqualTo(createResponse.id)
                get { name }.isEqualTo(mock.name)
            }
        }
    }

    @Test
    @Order(3)
    fun `should be renamed successfully`() {
        val modifiedName = "modified-test-entity"

        modifyEndpoint(
            Request(Method.PATCH, "/endpoint/${createResponse.id}")
                .with(Entities.EntityName.lens of mock.copy(name = modifiedName))
        ).expectOk()

        // list again
        val resp = listEndpoint(req(Method.GET)).resp(ListEntityFactory.ListEntity.responseLens)

        expect {
            that(resp.result).any {
                get { name }.isEqualTo(modifiedName)
            }
        }
    }

    @Test
    @Order(Int.MAX_VALUE)
    fun `should be deleted successfully`() {
        deleteEndpoint(Request(Method.DELETE, "/endpoint/${createResponse.id}")).expectOk()

        // list again
        val resp = listEndpoint(req(Method.GET)).resp(ListEntityFactory.ListEntity.responseLens)

        expect {
            that(resp.result).all {
                get { id }.isNotEqualTo(createResponse.id)
            }
        }
    }

    @BeforeAll
    fun `prepare database`() {
        TestDatabase.`prepare database`()
    }

    private fun req(method: Method) = Request(method, "/endpoint")

    private fun Response.expectOk() = also { expectThat(status).isEqualTo(Status.OK) }
    private fun <T> Response.resp(lens: BiDiBodyLens<T>) = lens(expectOk()).also { println(it) }
}