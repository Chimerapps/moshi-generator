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
 * Marks this class for generating a moshi adapter. Classes annotated with this annotation must have a single constructor
 * taking all the fields of that class. No fields will be set outside of this constructor
 *
 * @author Nicola Verbeeck
 * Date 23/05/2017.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface GenerateMoshi {

	/**
	 * Flag indicating if the code generator should create a separate moshi factory that creates the adapter.
	 *
	 * @return True if the processor should create a separate moshi factory fot this class/adapter
	 */
	boolean generateFactory() default false;

	/**
	 * Flag indicating if json writer code should be generated. When false, the adapter will delegate writing json
	 * to the "next adapter". When generating the writer, the system will try direct field access OR a javabean style
	 * getter (getField_Name)
	 *
	 * @return True if you wish to generate json writer code in the adapter, false to have the adapter delegate
	 */
	boolean generateWriter() default true;

	/**
	 * Flag indicating if the generator should insert debug logs into the generated adapter. Useful for debugging when
	 * you cannot attach a debugger
	 *
	 * @return True if the generate should generate log statements for this adapter
	 */
	boolean debugLogs() default false;
}
