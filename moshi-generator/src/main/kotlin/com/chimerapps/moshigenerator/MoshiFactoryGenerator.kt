package com.chimerapps.moshigenerator

import com.squareup.javapoet.*
import com.squareup.javapoet.WildcardTypeName
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
        factoryClassBuilder.addModifiers(Modifier.PUBLIC)
        factoryClassBuilder.addSuperinterface(JsonAdapter.Factory::class.java)

        factoryClassBuilder.addField(FieldSpec.builder(
                ParameterizedTypeName.get(ClassName.get(Map::class.java),
                        TypeName.get(Type::class.java),
                        ParameterizedTypeName.get(ClassName.get(BaseGeneratedAdapter::class.java),
                                WildcardTypeName.subtypeOf(Any::class.java))),
                "_adapters", Modifier.FINAL, Modifier.PRIVATE)
                .build())

        factoryClassBuilder.addMethod(generateConstructor())
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

    private fun generateConstructor(): MethodSpec {
        val builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)

        builder.addStatement("_adapters = new \$T()",
                ParameterizedTypeName.get(ClassName.get(HashMap::class.java),
                        TypeName.get(Type::class.java),
                        ParameterizedTypeName.get(ClassName.get(BaseGeneratedAdapter::class.java),
                                WildcardTypeName.subtypeOf(Any::class.java))))
        adapters.forEach {
            builder.addStatement("_adapters.put(\$T.class, new \$T(this, \$T.class))", it, ClassName.bestGuess(it.toString() + "Adapter"), it)
        }

        return builder.build()
    }

    private fun createFactoryBlock(): CodeBlock {
        val builder = CodeBlock.builder()

        builder.addStatement("\$T adapter = _adapters.get(type)",
                ParameterizedTypeName.get(ClassName.get(BaseGeneratedAdapter::class.java),
                        WildcardTypeName.subtypeOf(Any::class.java)))

        builder.beginControlFlow("if (adapter != null)")
                .addStatement("adapter.setMoshi(moshi)")
                .addStatement("return adapter")
                .nextControlFlow("else")
                .addStatement("return null")
                .endControlFlow()

        return builder.build()
    }

}
