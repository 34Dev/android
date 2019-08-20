/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.lang.proguardR8

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.testing.caret
import com.google.common.truth.Truth.assertThat
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase

class ProguardR8CompletionContributorTest : JavaCodeInsightFixtureTestCase() {

  override fun setUp() {
    StudioFlags.R8_SUPPORT_ENABLED.override(true);
    super.setUp()
  }

  override fun tearDown() {
    StudioFlags.R8_SUPPORT_ENABLED.clearOverride()
    super.tearDown()
  }

  fun testFlagCompletion() {

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -k$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("keepattributes")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -$caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }).contains("allowaccessmodification")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class myClass {
          -k$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    // No flag suggestions within JAVA specification
    assertThat(keys).isEmpty()
  }

  fun testClassTypeAutoCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -koop $caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification header
    assertThat(keys).isEmpty()

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after keep flags
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("class", "interface", "enum")


    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -if $caret
    """.trimIndent())

    keys = myFixture.completeBasic()

    // after if flag
    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("class", "interface", "enum")
  }


  fun testFieldMethodWildcardsAutoCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        <$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // Don't appear outside class specification body.
    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("<")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          <$caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("<fields>", "<init>", "<methods>", "<clinit>")
  }


  fun testFieldMethodModifiersAutoCompletion() {
    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        pu$caret
    """.trimIndent())

    var keys = myFixture.completeBasic()

    // don't appear outside class specification body
    assertThat(keys).isEmpty()
    assertThat(myFixture.editor.document.text).isEqualTo("pu")

    myFixture.configureByText(ProguardR8FileType.INSTANCE, """
        -keep class * {
          $caret
        }
    """.trimIndent())

    keys = myFixture.completeBasic()

    assertThat(keys).isNotEmpty()
    assertThat(keys.map { it.lookupString }.toList()).containsExactly("public", "private", "protected",
                                                                      "static", "synchronized", "native", "abstract", "strictfp",
                                                                      "volatile", "transient", "final")
  }

}