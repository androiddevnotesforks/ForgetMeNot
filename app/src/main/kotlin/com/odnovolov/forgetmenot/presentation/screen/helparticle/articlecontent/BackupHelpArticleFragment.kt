package com.odnovolov.forgetmenot.presentation.screen.helparticle.articlecontent

import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.presentation.screen.helparticle.HelpArticle
import kotlinx.android.synthetic.main.article.*

class BackupHelpArticleFragment : BaseHelpArticleFragmentForSimpleUi() {
    override val layoutRes: Int get() = R.layout.article
    override val helpArticle: HelpArticle get() = HelpArticle.Backup

    override fun setupView() {
        super.setupView()
        articleContentTextView.setText(R.string.article_backup)
    }
}