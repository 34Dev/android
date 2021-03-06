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
package com.android.tools.idea.lang.databinding.reference

import com.android.tools.idea.databinding.DataBindingMode
import com.android.tools.idea.lang.databinding.model.PsiModelClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

/**
 * Reference for literals that does not support [resolveTo] on purpose.
 *
 * For example:
 * In data binding expression "@{var.call(123)}", "123" is a literal expression that has null [resolveTo] value so that
 * "Go to Declaration" is disabled as required. However, as a parameter in "var.call", "123" should have an int [resolvedType]
 * so that we can choose the correct method with acceptable parameters,
 */
internal class PsiLiteralReference(element: PsiElement, private val type: PsiType) : DbExprReference(element, null) {

  override val resolvedType: PsiModelClass
    get() = PsiModelClass(type, DataBindingMode.fromPsiElement(element))

  override val memberAccess = PsiModelClass.MemberAccess.ALL_MEMBERS
}
