package com.chimerapps.moshigenerator

import com.squareup.javapoet.*
import com.squareup.moshi.JsonAdapter
import java.lang.reflect.Type
import java.util.logging.Level
import java.util.logging.Logger
import javax.annotation.processing.Filer
import javax.lang.model.element.Modifier
import javax.lang.model.util.Elements

/**
 * @author Nicola Verbeeck
 * @date 26/05/2017.
 */
//TODO log too
class MoshiFactoryGenerator(val className: String,
                            val packageName: String,
                            val adapters: List<TypeName>,
                            val filer: Filer,
                            val elementUtils: Elements,
                            val log: Boolean) {

    fun generate() {
        val factoryClassBuilder = TypeSpec.classBuilder(className)
        factoryClassBuilder.addSuperinterface(JsonAdapter.Factory::class.java)
        factoryClassBuilder.addModifiers(Modifier.PUBLIC)
        factoryClassBuilder.addMethod(generateFactoryMethod())

        if (log) {
            factoryClassBuilder.addField(
                    FieldSpec.builder(Logger::class.java, "LOGGER",
                            Modifier.FINAL,
                            Modifier.PRIVATE,
                            Modifier.STATIC)
                            .initializer("\$T.getLogger(\$S)",
                                    ClassName.get(Logger::class.java),
                                    "$packageName.$className").build())
        }

        adapters.forEach {
            factoryClassBuilder.addOriginatingElement(elementUtils.getTypeElement(it.toString()))
        }
        factoryClassBuilder.addJavadoc("Generated using moshi-generator")

        JavaFile.builder(packageName, factoryClassBuilder.build()).indent("\t").build().writeTo(filer)
    }

    private fun generateFactoryMethod(): MethodSpec {
        return MethodSpec.methodBuilder("create")
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(Override::class.java)
                .returns(ParameterizedTypeName.get(ClassName.get(JsonAdapter::class.java), WildcardTypeName.subtypeOf(Object::class.java)))
                .addParameter(Type::class.java, "type", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Set::class.java), WildcardTypeName.subtypeOf(Annotation::class.java)), "annotations", Modifier.FINAL)
                .addParameter(com.squareup.moshi.Moshi::class.java, "moshi", Modifier.FINAL)
                .addCode(createFactoryBlock())
                .build()
    }

    private fun createFactoryBlock(): CodeBlock {
        val builder = CodeBlock.builder()

        if (log) {
            builder.addStatement("LOGGER.log(\$T.FINE, \"Checking if we have an adapter for type: {0}\", type)", ClassName.get(Level::class.java))
        }

        builder.beginControlFlow("if (!(type instanceof \$T))", TypeName.get(Class::class.java))
        if (log) {
            builder.addStatement("LOGGER.log(\$T.FINE, \"Not a class?!\")", ClassName.get(Level::class.java))
        }
        builder.addStatement("return null")
        builder.endControlFlow()
        if (log) {
            builder.addStatement("LOGGER.log(\$T.FINE, \"Using class name for lookup: {0}\", ((Class)type).getName())", ClassName.get(Level::class.java))
        }
        builder.addStatement("final String _className = (((Class)type).getName())")
        adapters.forEach { adapter ->
            builder.beginControlFlow("if (_className.equals(\$S))", adapter.toString())
            if (log) {
                builder.addStatement("LOGGER.log(\$T.FINE, \"Creating adapter for ${adapter.toString()}!\")", ClassName.get(Level::class.java))
            }
            builder.addStatement("return new \$T(moshi, this, type, annotations)", ClassName.bestGuess(adapter.toString() + "Adapter"))
            builder.endControlFlow()
        }

        builder.addStatement("return null")
        return builder.build()
    }

}
