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

package com.chimerapps.moshigenerator.example

import com.chimerapps.moshigenerator.GenerateMoshi

/**
 * @author Nicola Verbeeck
 * @date 26/05/2017.
 */
@GenerateMoshi
data class Simple(val name: String, val age: Int, val registered: Boolean)

@GenerateMoshi
data class Nested(val person: Simple)

@GenerateMoshi
data class Generics(val persons: Map<String, List<Nested>>)

@GenerateMoshi
data class WithDefaults(val name: String, val age: Int = 3)