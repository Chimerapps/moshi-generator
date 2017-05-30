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

import java.util.*
import javax.annotation.processing.*
import javax.lang.model.SourceVersion
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

/**
 * @author Nicola Verbeeck
 * *         Date 23/05/2017
 */
@SuppressWarnings("unused")
class ProcessorFactory : AbstractProcessor() {
    private lateinit var filer: Filer
    private lateinit var messager: Messager
    private lateinit var elementUtils: Elements
    private lateinit var typeUtils: Types
    private lateinit var logger: SimpleLogger

    override fun process(annotations: Set<TypeElement>, roundEnv: RoundEnvironment): Boolean {
        try {
            if (roundEnv.processingOver()) {
                return true
            }
            logger.logInfo("Starting processing round")

            try {
                val classes = processDataClasses(roundEnv)
                processFactory(roundEnv, classes)
            } catch (annotationError: Exception) {
                logger.logError(annotationError.message ?: "<Unknown error> ${annotationError.javaClass.canonicalName}", annotationError)
                messager.printMessage(Diagnostic.Kind.ERROR, annotationError.message ?: "<Unknown error> ${annotationError.javaClass.canonicalName}")
            }

            logger.logInfo("Round processing complete")
            messager.printMessage(Diagnostic.Kind.NOTE, "Processing complete")
        } catch(e: Exception) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Failed to execute round: $e")
            logger.logError(e.message ?: "", e)
        }

        return true
    }

    private fun processDataClasses(roundEnv: RoundEnvironment): List<MoshiAnnotatedClass> {
        val classes = ArrayList<MoshiAnnotatedClass>()

        for (element in roundEnv.getElementsAnnotatedWith(GenerateMoshi::class.java)) {
            if (element.kind !== ElementKind.CLASS) {
                throw AnnotationError("Only classes can be annotated with @GenerateMoshi (${element.simpleName})")
            }

            val typeElement = element as TypeElement
            val clazz = MoshiAnnotatedClass(typeElement, elementUtils, typeUtils)
            clazz.checkValid()

            classes.add(clazz)
            messager.printMessage(Diagnostic.Kind.NOTE, "Processing class: ${clazz.element.qualifiedName}")

            AdapterGenerator(clazz, filer, elementUtils, logger).generate()
        }
        return classes
    }

    private fun processFactory(roundEnv: RoundEnvironment, classes: List<MoshiAnnotatedClass>) {
        val knownClasses = hashSetOf<String>()

        for (element in roundEnv.getElementsAnnotatedWith(GenerateMoshiFactory::class.java)) {
            val clazz = MoshiFactoryAnnotatedClass(element, logger)

            MoshiFactoryGenerator(clazz.className, clazz.targetPackage, clazz.moshiClasses, filer, elementUtils).generate()

            clazz.moshiClasses.forEach {
                if (knownClasses.contains(it.toString())) {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Class '$it' is registered in multiple factories")
                } else {
                    knownClasses.add(it.toString())
                }
            }
        }

        classes.filter { !knownClasses.contains(it.element.qualifiedName.toString()) && !it.generatesFactory() }
                .forEach {
                    messager.printMessage(Diagnostic.Kind.WARNING, "Class '${it.element.qualifiedName}' is not registered in any factory")
                }
    }

    override fun getSupportedSourceVersion(): SourceVersion {
        return SourceVersion.latestSupported()
    }

    override fun getSupportedAnnotationTypes(): Set<String> {
        return setOf(GenerateMoshi::class.java.canonicalName, GenerateMoshiFactory::class.java.canonicalName)
    }

    override fun init(processingEnv: ProcessingEnvironment) {
        super.init(processingEnv)
        filer = processingEnv.filer
        messager = processingEnv.messager
        elementUtils = processingEnv.elementUtils
        typeUtils = processingEnv.typeUtils
        logger = SimpleLogger(messager)
        logger.logInfo("Initialized processor")
    }

}