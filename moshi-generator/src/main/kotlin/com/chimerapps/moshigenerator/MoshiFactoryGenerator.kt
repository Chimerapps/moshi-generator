package com.chimerapps.moshigenerator

import com.squareup.javapoet.*
import com.squareup.moshi.JsonAdapter
import java.lang.reflect.Type
import javax.annotation.processing.Filer
import javax.lang.model.element.Modifier
import javax.lang.model.util.Elements

/**
 * @author Nicola Verbeeck
 * @date 26/05/2017.
 */
class MoshiFactoryGenerator(val className: String,
                            val packageName: String,
                            val adapters: List<TypeName>,
                            val filer: Filer,
                            val elementUtils: Elements) {

    fun generate() {
        val factoryClassBuilder = TypeSpec.classBuilder(className)
        factoryClassBuilder.addSuperinterface(JsonAdapter.Factory::class.java)
        factoryClassBuilder.addModifiers(Modifier.PUBLIC)
        factoryClassBuilder.addMethod(generateFactoryMethod())

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

        builder.beginControlFlow("switch (type.getTypeName())")
        adapters.forEach {
            builder.addStatement("case \$S: return new \$T(moshi, this, type, annotations)", it.toString(), ClassName.bestGuess(it.toString() + "Adapter"))
        }
        builder.endControlFlow()
        builder.addStatement("return null")
        return builder.build()
    }

}
