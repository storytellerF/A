/*
 * This is a private project. All rights reserved.
 */

plugins { kotlin("jvm") }
kotlin { jvmToolchain(21) }
dependencies { compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.4.21") }
