package ray.eldath.offgrid.jooq

import org.jooq.codegen.DefaultGeneratorStrategy
import org.jooq.codegen.GeneratorStrategy
import org.jooq.meta.Definition

class SingularPojoGenerationStrategy : DefaultGeneratorStrategy() {
    override fun getJavaClassName(definition: Definition, mode: GeneratorStrategy.Mode): String {
        val str: String = super.getJavaClassName(definition, mode)

        if (mode == GeneratorStrategy.Mode.POJO)
            return if (str.endsWith("ies"))
                str.removeSuffix("ies") + "y"
            else
                str.removeSuffix("s")
        return str
    }
}