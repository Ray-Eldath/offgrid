package ray.eldath.offgrid.test.handler

import org.http4k.core.Response
import org.http4k.core.with
import org.junit.jupiter.api.*
import ray.eldath.offgrid.handler.TagEntity
import ray.eldath.offgrid.handler.UntagEntity
import ray.eldath.offgrid.test.*
import ray.eldath.offgrid.test.Context.MockSecurity
import strikt.api.expectThat
import strikt.assertions.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TestTagEntity {
    private val tag = TagEntity(MockSecurity.mockCredentials, MockSecurity).compile()
    private val untag = UntagEntity(MockSecurity.mockCredentials, MockSecurity).compile()

    private val tagIds = arrayListOf<Int>()
    private val created by lazy {
        TestEntity.createEndpoint()
    }

    @Test
    @Order(1)
    fun `should tag an entity successfully`() {
        val tags = listOf("unit-testing", "ray-eldath", "phosphorus")
        val responses = tags.map { tag(created.id, it) }

        expectThat(responses).println().all { expectOk() }

        tagIds.addAll(responses.map { it.parse(TagEntity.responseLens).id })

        // now list
        expectThat(TestEntity.listEndpoints().result)
            .filter { it.id == created.id }.get { tags }.println()
            .containsExactlyInAnyOrder(tags)
    }

    @Test
    @Order(2)
    fun `should untag an entity successfully`() {
        val responses = tagIds.map { untag(it) }

        expectThat(responses).all { expectOk() }

        // now list
        expectThat(TestEntity.listEndpoints().result)
            .filter { it.id == created.id }.println()
            .flatMap { it.tags ?: listOf() }
            .isEmpty()
    }


    private fun tag(entityUUID: String, name: String): Response =
        tag(
            "/entity/$entityUUID/tag".POST()
                .with(TagEntity.requestLens of TagEntity.TagEntityRequest(name))
        )

    private fun untag(tagId: Int): Response = untag("/entity/tags/$tagId".DELETE())

    @BeforeAll
    fun `prepare database and Endpoint`() {
        TestDatabase.`prepare database`()
        created
    }

    @AfterAll
    fun clean() {
        TestEntity.deleteEndpoint(created.id)
    }
}