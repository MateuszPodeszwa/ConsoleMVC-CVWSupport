package com.github.mateuszpodeszwa.consolemvc.cvwsupport

import com.intellij.lang.Commenter

/**
 * Comments in views use `@* … *@`, which ConsoleMVC removes from both markup and C#; `//` would be shown as text
 * inside an element. With no line comment, Comment with Line Comment (Ctrl+/) also uses `@* … *@`.
 *
 * Example: commenting `<text>Hi</text>` gives `@*<text>Hi</text>*@`.
 */
class CvwCommenter : Commenter {
    override fun getLineCommentPrefix(): String? = null
    override fun getBlockCommentPrefix(): String = "@*"
    override fun getBlockCommentSuffix(): String = "*@"
    override fun getCommentedBlockCommentPrefix(): String? = null
    override fun getCommentedBlockCommentSuffix(): String? = null
}
