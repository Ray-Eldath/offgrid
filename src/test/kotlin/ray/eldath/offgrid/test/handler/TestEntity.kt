package ray.eldath.offgrid.test.handler

import org.http4k.core.Response
import org.http4k.core.with
import org.junit.jupiter.api.*
import ray.eldath.offgrid.factory.CreateEntityFactory
import ray.eldath.offgrid.factory.CreateEntityFactory.CreateEntity.Companion.CreateEndpointResponse
import ray.eldath.offgrid.factory.Entities
import ray.eldath.offgrid.factory.ListEntityFactory
import ray.eldath.offgrid.handler.CreateEndpoint
import ray.eldath.offgrid.handler.DeleteEndpoint
import ray.eldath.offgrid.handler.ListEndpoint
import ray.eldath.offgrid.handler.ModifyEndpoint
import ray.eldath.offgrid.test.*
import ray.eldath.offgrid.test.Context.MockSecurity
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestEntity {
    companion object {
        private val mock = Entities.EntityName.mock

        private val createEndpoint = CreateEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
        private val listEndpoint = ListEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
        private val modifyEndpoint = ModifyEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()
        private val deleteEndpoint = DeleteEndpoint(MockSecurity.mockCredentials, MockSecurity).compile()

        fun createEndpoint(): CreateEndpointResponse =
            createEndpoint(
                "/endpoint".PUT()
                    .with(Entities.EntityName.lens of mock)
            ).expectOk().parse(CreateEntityFactory.CreateEntity.responseLens)

        fun listEndpoints(): ListEntityFactory.ListEntity.Companion.ListResponse =
            listEndpoint("/endpoint".GET()).expectOk().parse(ListEntityFactory.ListEntity.responseLens)

        fun deleteEndpoint(entityId: String): Response =
            deleteEndpoint("/endpoint/$entityId".DELETE())
    }

    private lateinit var createResponse: CreateEndpointResponse

    @Test
    @Order(1)
    fun `should create successfully`() {
        createResponse = createEndpoint()
    }

    @Test
    @Order(2)
    fun `should be listed correctly`() {
        val resp = listEndpoints()

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
            "/endpoint/${createResponse.id}".PATCH()
                .with(Entities.EntityName.lens of mock.copy(name = modifiedName))
        ).expectOk()

        // list again
        expectThat(listEndpoints().result).any {
            get { name }.isEqualTo(modifiedName)
        }
    }

    @Test
    @Order(Int.MAX_VALUE)
    fun `should be deleted successfully`() {
        deleteEndpoint(createResponse.id).expectOk()

        // list again
        expectThat(listEndpoints().result).all {
            get { id }.isNotEqualTo(createResponse.id)
        }
    }

    @BeforeAll
    fun `prepare database`() {
        TestDatabase.`prepare database`()
    }
}