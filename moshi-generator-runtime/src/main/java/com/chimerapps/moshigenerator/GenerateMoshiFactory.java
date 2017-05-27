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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Use this annotation to create a factory which registers all the provided moshi factories with a moshi builder. This factory
 * is then used by moshi to create the correct adapters.
 * During code generation, this class helps the system emit warnings for classes which are annotated with {@link GenerateMoshi}
 * but which are not included in the newly generated factory. This ensures that the generated moshi adapters
 * are all known to moshi. Only classes annoated with {@link GenerateMoshi} which have {@link GenerateMoshi#generateFactory()}
 * set to false (the default), will be included in this "mega" factory.
 *
 * @author Nicola Verbeeck
 *         Date 26/05/2017.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateMoshiFactory {

	/**
	 * The classes which are included in this factory
	 *
	 * @return The classes which are included in this factory
	 */
	Class<?>[] value();

	/**
	 * @return The class name of the generated factory
	 */
	String targetClassName() default "MoshiFactory";

	/**
	 * @return The package name of the generated factory. Defaults to the package of the type this annotation is on
	 */
	String targetPackage() default "";

}
