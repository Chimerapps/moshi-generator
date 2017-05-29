/*
 *    Copyright 2017 - Chimerapps BVBA
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.chimerapps.moshigenerator

import com.squareup.javapoet.*
import com.squareup.moshi.*
import java.io.IOException
import java.lang.reflect.Type
import javax.annotation.processing.Filer
import javax.lang.model.element.Modifier
import javax.lang.model.element.VariableElement
import javax.lang.model.util.Elements

/**
 * @author Nicola Verbeeck
 * *         Date 23/05/2017.
 */
@SuppressWarnings("WeakerAccess")
class AdapterGenerator(private val clazz: MoshiAnnotatedClass, private val filer: Filer, val elementUtils: Elements) {

    @Throws(AnnotationError::class, IOException::class)
    fun generate() {
        val from = createFromJson()
        val to = createToJson()
        val constructor = createConstructor()

        val adapterClassBuilder = TypeSpec.classBuilder(clazz.element.simpleName.toString() + "Adapter")
        adapterClassBuilder.superclass(ParameterizedTypeName.get(ClassName.get(JsonAdapter::class.java), ClassName.get(clazz.element)))
        adapterClassBuilder.addModifiers(Modifier.PUBLIC)
        adapterClassBuilder.addOriginatingElement(clazz.element)
        adapterClassBuilder.addJavadoc("Generated using moshi-generator")

        adapterClassBuilder.addField(FieldSpec.builder(Moshi::class.java, "moshi", Modifier.PRIVATE, Modifier.FINAL).build())
        adapterClassBuilder.addField(FieldSpec.builder(JsonAdapter.Factory::class.java, "factory", Modifier.PRIVATE, Modifier.FINAL).build())
        adapterClassBuilder.addField(FieldSpec.builder(Type::class.java, "type", Modifier.PRIVATE, Modifier.FINAL).build())
        adapterClassBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(Set::class.java), WildcardTypeName.subtypeOf(Annotation::class.java)), "annotations", Modifier.PRIVATE, Modifier.FINAL).build())
        adapterClassBuilder.addMethod(constructor)
        adapterClassBuilder.addMethod(from)
        adapterClassBuilder.addMethod(to)

        if (clazz.generatesFactory()) {
            MoshiFactoryGenerator(clazz.element.simpleName.toString() + "AdapterFactory",
                    clazz.packageName,
                    listOf(ClassName.bestGuess("${clazz.packageName}.${clazz.element.simpleName}")),
                    filer,
                    elementUtils)
                    .generate()
        }

        JavaFile.builder(clazz.packageName, adapterClassBuilder.build())
                .indent("\t")
                .build().writeTo(filer)
    }

    private fun createToJson(): MethodSpec {
        return MethodSpec.methodBuilder("toJson")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .addException(IOException::class.java)
                .returns(TypeName.VOID)
                .addParameter(ParameterSpec.builder(JsonWriter::class.java, "writer", Modifier.FINAL).build())
                .addParameter(ParameterSpec.builder(ClassName.get(clazz.element), "value", Modifier.FINAL).build())
                .addCode(createWriterBlock())
                .build()
    }

    private fun createFromJson(): MethodSpec {
        return MethodSpec.methodBuilder("fromJson")
                .addAnnotation(Override::class.java)
                .addModifiers(Modifier.PUBLIC)
                .addException(IOException::class.java)
                .returns(ClassName.get(clazz.element))
                .addParameter(ParameterSpec.builder(JsonReader::class.java, "reader", Modifier.FINAL).build())
                .addCode(createReaderBlock())
                .build()
    }

    private fun createConstructor(): MethodSpec {
        return MethodSpec.constructorBuilder()
                .addParameter(com.squareup.moshi.Moshi::class.java, "moshi", Modifier.FINAL)
                .addParameter(JsonAdapter.Factory::class.java, "factory", Modifier.FINAL)
                .addParameter(Type::class.java, "type", Modifier.FINAL)
                .addParameter(ParameterizedTypeName.get(ClassName.get(Set::class.java), WildcardTypeName.subtypeOf(Annotation::class.java)), "annotations", Modifier.FINAL)
                .addStatement("this.moshi = moshi")
                .addStatement("this.factory = factory")
                .addStatement("this.type = type")
                .addStatement("this.annotations = annotations")
                .build()
    }

    private fun createReaderBlock(): CodeBlock {
        val builder = CodeBlock.builder()

        val fields = clazz.fields
        for (variableElement in fields) {
            builder.addStatement("\$T \$N = null", TypeName.get(variableElement.asType()).box(), variableElement.simpleName.toString())
        }
        builder.addStatement("reader.beginObject()")
        builder.beginControlFlow("while (reader.hasNext())")
        builder.addStatement("final \$T _name = reader.nextName()", ClassName.get(String::class.java))
        builder.beginControlFlow("switch (_name)")
        for (variableElement in fields) {
            builder.add("case \$S: ", getJsonFieldName(variableElement))
            generateReader(builder, variableElement)
            builder.addStatement("break")
        }
        builder.addStatement("default: reader.skipValue()")
        builder.endControlFlow()
        builder.endControlFlow()
        builder.addStatement("reader.endObject()")

        generateNullChecks(builder, fields)

        builder.add("return new \$T(", ClassName.get(clazz.element))

        fields.forEachIndexed { index, variableElement ->
            if (index != 0) {
                builder.add(", ")
            }
            builder.add("\$N", variableElement.simpleName.toString())
        }

        builder.addStatement(")")
        return builder.build()
    }

    private fun createWriterBlock(): CodeBlock {
        val builder = CodeBlock.builder()

        if (clazz.generatesWriter()) {
            builder.addStatement("writer.beginObject()")

            val fields = clazz.fields
            for (variableElement in fields) {
                if (clazz.isIncludedInToJson(variableElement.simpleName.toString())) {
                    builder.addStatement("writer.name(\$S)", getJsonFieldName(variableElement))
                    generateWriter(builder, variableElement)
                }
            }

            builder.addStatement("writer.endObject()");
        } else {
            builder.addStatement("moshi.nextAdapter(factory, type, annotations).toJson(writer, value)")
        }
        return builder.build()
    }

    private fun generateNullChecks(builder: CodeBlock.Builder, fields: List<VariableElement>) {
        for (field in fields) {
            if (!isNullable(field)) { //No annotation -> required
                builder.beginControlFlow("if (\$N == null)", field.simpleName.toString())
                builder.addStatement("throw new \$T(\$S)", ClassName.get(IOException::class.java), getJsonFieldName(field) + " is non-optional but was not found in the json")
                builder.endControlFlow()
            }
        }
    }

    private fun generateReader(builder: CodeBlock.Builder, variableElement: VariableElement) {
        val typeName = TypeName.get(variableElement.asType())
        if (typeName.isPrimitive || typeName.isBoxedPrimitive) {
            generatePrimitiveReader(builder, typeName, variableElement)
        } else if (typeName == ClassName.get(String::class.java)) {
            builder.addStatement("\$N = reader.nextString()", variableElement.simpleName.toString())
        } else {
            generateDelegatedReader(builder, typeName, variableElement)
        }
    }

    private fun generateWriter(builder: CodeBlock.Builder, variableElement: VariableElement) {
        val typeName = TypeName.get(variableElement.asType())
        if (typeName.isPrimitive || typeName.isBoxedPrimitive || typeName == ClassName.get(String::class.java)) {
            generatePrimitiveWriter(builder, variableElement)
        } else {
            generateDelegatedWriter(builder, typeName, variableElement)
        }
    }

    private fun generatePrimitiveReader(builder: CodeBlock.Builder, typeName: TypeName, variableElement: VariableElement) {
        val primitive = typeName.unbox()

        var method: String? = null
        if (primitive == TypeName.BOOLEAN) {
            method = "nextBoolean"
        } else if (primitive == TypeName.BYTE) {
            throw AnnotationError("Byte not supported")
        } else if (primitive == TypeName.SHORT) {
            builder.addStatement("\$N = (short)reader.nextInt()", variableElement.simpleName.toString())
        } else if (primitive == TypeName.INT) {
            method = "nextInt"
        } else if (primitive == TypeName.LONG) {
            method = "nextLong"
        } else if (primitive == TypeName.CHAR) {
            throw AnnotationError("Char not supported")
        } else if (primitive == TypeName.FLOAT) {
            builder.addStatement("\$N = (float)reader.nextDouble()", variableElement.simpleName.toString())
        } else if (primitive == TypeName.DOUBLE) {
            method = "nextDouble"
        }

        if (method != null) {
            builder.addStatement("\$N = reader.\$N()", variableElement.simpleName.toString(), method)
        }
    }

    private fun generatePrimitiveWriter(builder: CodeBlock.Builder, variableElement: VariableElement) {
        builder.addStatement("writer.value(value.${valueAccessor(variableElement)})")
    }

    private fun generateDelegatedReader(builder: CodeBlock.Builder, typeName: TypeName, variableElement: VariableElement) {
        val subBuilder = CodeBlock.builder()
        subBuilder.beginControlFlow("")
        subBuilder.addStatement("final \$T _adapter = moshi.adapter(" + makeType(typeName) + ")", ParameterizedTypeName.get(ClassName.get(JsonAdapter::class.java), typeName))
        subBuilder.addStatement("\$N = _adapter.fromJson(reader)", variableElement.simpleName.toString())
        subBuilder.endControlFlow()
        builder.add(subBuilder.build())
    }

    private fun generateDelegatedWriter(builder: CodeBlock.Builder, typeName: TypeName, variableElement: VariableElement) {
        val subBuilder = CodeBlock.builder()
        subBuilder.beginControlFlow("")
        subBuilder.addStatement("final \$T _adapter = moshi.adapter(" + makeType(typeName) + ")", ParameterizedTypeName.get(ClassName.get(JsonAdapter::class.java), typeName))
        subBuilder.addStatement("_adapter.toJson(writer, value.${valueAccessor(variableElement)})")
        subBuilder.endControlFlow()
        builder.add(subBuilder.build())
    }

    private fun makeType(typeName: TypeName): String {
        if (typeName is ParameterizedTypeName) {
            val parameterizedTypeName = typeName

            val builder = StringBuilder("com.squareup.moshi.Types.newParameterizedType(")
            builder.append(parameterizedTypeName.rawType.toString())
            builder.append(".class, ")

            parameterizedTypeName.typeArguments.forEachIndexed { index, typeArgument ->
                if (index != 0) {
                    builder.append(", ")
                }
                builder.append(makeType(typeArgument))
            }
            builder.append(')')
            return builder.toString()
        } else if (typeName is WildcardTypeName) {
            return makeType(typeName.upperBounds[0])
        } else {
            return typeName.toString() + ".class"
        }
    }

    private fun isNullable(field: VariableElement): Boolean {
        field.annotationMirrors.forEach {
            when (it.annotationType.toString()) {
                INTELLIJ_NULLABLE -> return true
                ANDROID_NULLABLE -> return true
            }
        }
        return false
    }

    private fun getJsonFieldName(variableElement: VariableElement): String {
        val annotation = variableElement.getAnnotation(Json::class.java) ?: return variableElement.simpleName.toString()
        return annotation.name
    }

    private fun valueAccessor(variableElement: VariableElement): String {
        val name = variableElement.simpleName.toString()
        if (clazz.hasVisibleField(name)) {
            return name
        }
        return "get${name.capitalize()}()"
    }

    companion object {
        private val INTELLIJ_NULLABLE = "org.jetbrains.annotations.Nullable"
        private val ANDROID_NULLABLE = "android.support.annotation.Nullable"
    }

}
