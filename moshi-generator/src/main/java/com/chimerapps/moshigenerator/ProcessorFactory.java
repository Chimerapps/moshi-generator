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

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * @author Nicola Verbeeck
 *         Date 23/05/2017
 */
@SuppressWarnings("unused")
public class ProcessorFactory extends AbstractProcessor {

	private static final Logger logger = Logger.getLogger(ProcessorFactory.class.getSimpleName());
	private Filer filer;
	private Messager messager;

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		if (roundEnv.processingOver()) {
			return true;
		}
		logger.info("Starting processing round");
		messager.printMessage(Diagnostic.Kind.NOTE, "Processing starting");

		final List<MoshiAnnotatedClass> classes = new ArrayList<>();
		for (Element element : roundEnv.getElementsAnnotatedWith(GenerateMoshi.class)) {
			if (element.getKind() != ElementKind.CLASS) {
				messager.printMessage(Diagnostic.Kind.ERROR, "Only classes can be annotated with @Moshi (" + element.getSimpleName() + ")");
				return true;
			}

			TypeElement typeElement = (TypeElement) element;
			final MoshiAnnotatedClass clazz = new MoshiAnnotatedClass(typeElement, messager);
			if (!clazz.isValid()) {
				return true;
			}
			classes.add(clazz);
			messager.printMessage(Diagnostic.Kind.NOTE, "Processing class: " + clazz.getElement().getQualifiedName());

			try {
				new AdapterGenerator(clazz, filer).generate();
			} catch (IOException e) {
				messager.printMessage(Diagnostic.Kind.ERROR, e.getMessage());
				e.printStackTrace();
				return true;
			}
		}

		logger.info("Round processing complete");
		messager.printMessage(Diagnostic.Kind.NOTE, "Processing complete");
		return true;
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return new HashSet<>(Collections.singletonList(GenerateMoshi.class.getCanonicalName()));
	}

	@Override
	public SourceVersion getSupportedSourceVersion() {
		return SourceVersion.latestSupported();
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		filer = processingEnv.getFiler();
		messager = processingEnv.getMessager();
		logger.info("Initialized processor");
	}
}