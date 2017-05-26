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

package com.chimerapps.moshigenerator;

import com.squareup.javapoet.*;
import com.squareup.moshi.JsonAdapter;
import com.squareup.moshi.JsonReader;
import com.squareup.moshi.JsonWriter;
import com.squareup.moshi.Moshi;
import org.jetbrains.annotations.Nullable;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

/**
 * @author Nicola Verbeeck
 *         Date 23/05/2017.
 */
@SuppressWarnings("WeakerAccess")
public class AdapterGenerator {

	private final MoshiAnnotatedClass mClazz;
	private final Filer mFiler;

	public AdapterGenerator(final MoshiAnnotatedClass clazz, final Filer filer) {
		mClazz = clazz;
		mFiler = filer;
	}

	public void generate() throws IOException {
		final MethodSpec from = createFromJson();
		final MethodSpec to = createToJson();
		final MethodSpec constructor = createConstructor();

		final TypeSpec.Builder adapterClassBuilder = TypeSpec.classBuilder(mClazz.getElement().getSimpleName().toString() + "Adapter");
		adapterClassBuilder.superclass(ParameterizedTypeName.get(ClassName.get(JsonAdapter.class), ClassName.get(mClazz.getElement())));
		adapterClassBuilder.addModifiers(Modifier.PUBLIC);

		adapterClassBuilder.addField(FieldSpec.builder(Moshi.class, "moshi", Modifier.PRIVATE, Modifier.FINAL).build());
		adapterClassBuilder.addField(FieldSpec.builder(JsonAdapter.Factory.class, "factory", Modifier.PRIVATE, Modifier.FINAL).build());
		adapterClassBuilder.addField(FieldSpec.builder(Type.class, "type", Modifier.PRIVATE, Modifier.FINAL).build());
		adapterClassBuilder.addField(FieldSpec.builder(ParameterizedTypeName.get(ClassName.get(Set.class), WildcardTypeName.subtypeOf(Annotation.class)), "annotations", Modifier.PRIVATE, Modifier.FINAL).build());
		adapterClassBuilder.addMethod(constructor);
		adapterClassBuilder.addMethod(from);
		adapterClassBuilder.addMethod(to);

		if (mClazz.generatesFactory()) {
			final TypeSpec.Builder factoryClassBuilder = TypeSpec.classBuilder(mClazz.getElement().getSimpleName().toString() + "AdapterFactory");
			factoryClassBuilder.addSuperinterface(JsonAdapter.Factory.class);
			factoryClassBuilder.addModifiers(Modifier.PUBLIC);
			factoryClassBuilder.addMethod(createFactoryMethod());

			JavaFile.builder(mClazz.getPackage(), factoryClassBuilder.build()).indent("\t").build().writeTo(mFiler);
		}

		JavaFile.builder(mClazz.getPackage(), adapterClassBuilder.build())
				.indent("\t")
				.build().writeTo(mFiler);
	}

	private MethodSpec createToJson() {
		return MethodSpec.methodBuilder("toJson")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addException(IOException.class)
				.returns(TypeName.VOID)
				.addParameter(ParameterSpec.builder(JsonWriter.class, "writer", Modifier.FINAL).build())
				.addParameter(ParameterSpec.builder(ClassName.get(mClazz.getElement()), "value", Modifier.FINAL).build())
				.addCode(CodeBlock.builder().addStatement("moshi.nextAdapter(factory, type, annotations).toJson(writer, value)").build())
				.build();
	}

	private MethodSpec createFromJson() throws IOException {
		return MethodSpec.methodBuilder("fromJson")
				.addAnnotation(Override.class)
				.addModifiers(Modifier.PUBLIC)
				.addException(IOException.class)
				.returns(ClassName.get(mClazz.getElement()))
				.addParameter(ParameterSpec.builder(JsonReader.class, "reader", Modifier.FINAL).build())
				.addCode(createReaderBlock())
				.build();
	}

	private MethodSpec createConstructor() {
		return MethodSpec.constructorBuilder()
				.addParameter(com.squareup.moshi.Moshi.class, "moshi", Modifier.FINAL)
				.addParameter(JsonAdapter.Factory.class, "factory", Modifier.FINAL)
				.addParameter(Type.class, "type", Modifier.FINAL)
				.addParameter(ParameterizedTypeName.get(ClassName.get(Set.class), WildcardTypeName.subtypeOf(Annotation.class)), "annotations", Modifier.FINAL)
				.addStatement("this.moshi = moshi")
				.addStatement("this.factory = factory")
				.addStatement("this.type = type")
				.addStatement("this.annotations = annotations")
				.build();
	}

	private MethodSpec createFactoryMethod() {
		return MethodSpec.methodBuilder("create")
				.addModifiers(Modifier.PUBLIC)
				.addAnnotation(Override.class)
				.returns(ParameterizedTypeName.get(ClassName.get(JsonAdapter.class), WildcardTypeName.subtypeOf(Object.class)))
				.addParameter(Type.class, "type")
				.addParameter(ParameterizedTypeName.get(ClassName.get(Set.class), WildcardTypeName.subtypeOf(Annotation.class)), "annotations")
				.addParameter(com.squareup.moshi.Moshi.class, "moshi")
				.addCode(createFactoryBlock())
				.build();
	}

	private CodeBlock createReaderBlock() throws IOException {
		final CodeBlock.Builder builder = CodeBlock.builder();

		final List<VariableElement> fields = mClazz.getFields();
		for (final VariableElement variableElement : fields) {
			builder.addStatement("$T $N = null", TypeName.get(variableElement.asType()).box(), variableElement.getSimpleName().toString());
		}
		builder.addStatement("reader.beginObject()");
		builder.beginControlFlow("while (reader.hasNext())");
		builder.addStatement("final $T _name = reader.nextName()", ClassName.get(String.class));
		builder.beginControlFlow("switch (_name)");
		for (final VariableElement variableElement : fields) {

			builder.add("case $S: ", variableElement.getSimpleName().toString());
			generateReader(builder, variableElement);
			builder.addStatement("break");
		}
		builder.addStatement("default: reader.skipValue()");
		builder.endControlFlow();
		builder.endControlFlow();
		builder.addStatement("reader.endObject()");

		generateNullChecks(builder, fields);

		builder.add("return new $T(", ClassName.get(mClazz.getElement()));
		int c = 0;
		for (final VariableElement variableElement : fields) {
			if (c++ != 0) {
				builder.add(", ");
			}
			builder.add("$N", variableElement.getSimpleName().toString());
		}
		builder.addStatement(")");
		return builder.build();
	}

	private void generateNullChecks(final CodeBlock.Builder builder, final List<VariableElement> fields) {
		for (final VariableElement field : fields) {
			if (field.getAnnotation(Nullable.class) == null) { //No annotation -> required
				builder.beginControlFlow("if ($N == null)", field.getSimpleName().toString());
				builder.addStatement("throw new $T($S)", ClassName.get(IOException.class), field.getSimpleName().toString() + " is non-optional but was not found in the json");
				builder.endControlFlow();
			}
		}
	}

	private void generateReader(final CodeBlock.Builder builder, final VariableElement variableElement) throws IOException {
		final TypeMirror mirror = variableElement.asType();

		final TypeName typeName = TypeName.get(mirror);
		if (typeName.isPrimitive() || typeName.isBoxedPrimitive()) {
			generatePrimitive(builder, typeName, variableElement);
		} else if (typeName.equals(ClassName.get(String.class))) {
			builder.addStatement("$N = reader.nextString()", variableElement.getSimpleName().toString());
		} else {
			generateDelegated(builder, typeName, variableElement);
		}
	}

	private void generateDelegated(final CodeBlock.Builder builder, final TypeName typeName, final VariableElement variableElement) {
		final CodeBlock.Builder subBuilder = CodeBlock.builder();
		subBuilder.beginControlFlow("");

		subBuilder.addStatement("final $T _adapter = moshi.adapter(" + makeType(typeName) + ")", ParameterizedTypeName.get(ClassName.get(JsonAdapter.class), typeName));
		subBuilder.addStatement("$N = _adapter.fromJson(reader)", variableElement.getSimpleName().toString());
		subBuilder.endControlFlow();
		builder.add(subBuilder.build());
	}

	private void generatePrimitive(final CodeBlock.Builder builder, final TypeName typeName, final VariableElement variableElement) throws IOException {
		final TypeName primitive = typeName.unbox();

		String method = null;
		if (primitive.equals(TypeName.BOOLEAN)) {
			method = "nextBoolean";
		} else if (primitive.equals(TypeName.BYTE)) {
			throw new IOException("Byte not supported");
		} else if (primitive.equals(TypeName.SHORT)) {
			builder.addStatement("$N = (short)reader.nextInt()", variableElement.getSimpleName().toString());
		} else if (primitive.equals(TypeName.INT)) {
			method = "nextInt";
		} else if (primitive.equals(TypeName.LONG)) {
			method = "nextLong";
		} else if (primitive.equals(TypeName.CHAR)) {
			throw new IOException("Char not supported");
		} else if (primitive.equals(TypeName.FLOAT)) {
			builder.addStatement("$N = (float)reader.nextDouble()", variableElement.getSimpleName().toString());
		} else if (primitive.equals(TypeName.DOUBLE)) {
			method = "nextDouble";
		}

		if (method != null) {
			builder.addStatement("$N = reader.$N()", variableElement.getSimpleName().toString(), method);
		}
	}

	private CodeBlock createFactoryBlock() {
		final CodeBlock.Builder builder = CodeBlock.builder();

		builder.beginControlFlow("if (type == $T.class)", ClassName.get(mClazz.getElement()));

		builder.addStatement("return new $T(moshi, this, type, annotations)", ClassName.bestGuess(mClazz.getElement().getQualifiedName().toString() + "Adapter"));

		builder.endControlFlow();
		builder.addStatement("return null");
		return builder.build();
	}

	private String makeType(final TypeName typeName) {
		if (typeName instanceof ParameterizedTypeName) {
			final ParameterizedTypeName parameterizedTypeName = (ParameterizedTypeName) typeName;

			final StringBuilder builder = new StringBuilder("com.squareup.moshi.Types.newParameterizedType(");
			builder.append(parameterizedTypeName.rawType.toString());
			builder.append(".class, ");

			int c = 0;
			for (final TypeName typeArgument : parameterizedTypeName.typeArguments) {
				if (c++ != 0) {
					builder.append(", ");
				}
				builder.append(makeType(typeArgument));
			}
			builder.append(')');
			return builder.toString();
		} else if (typeName instanceof WildcardTypeName) {
			return makeType(((WildcardTypeName) typeName).upperBounds.get(0));
		} else {
			return typeName.toString() + ".class";
		}
	}

}
