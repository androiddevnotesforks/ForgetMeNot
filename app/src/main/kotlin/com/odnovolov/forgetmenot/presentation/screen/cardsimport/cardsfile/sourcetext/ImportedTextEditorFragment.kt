package com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.sourcetext

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.doOnLayout
import androidx.core.view.isVisible
import com.brackeys.ui.editorkit.listener.OnUndoRedoChangedListener
import com.brackeys.ui.editorkit.span.ErrorSpan
import com.brackeys.ui.editorkit.widget.TextProcessor
import com.odnovolov.forgetmenot.R
import com.odnovolov.forgetmenot.presentation.common.*
import com.odnovolov.forgetmenot.presentation.common.base.BaseFragment
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.CharsetAdapter
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.CharsetItem
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.CardsImportDiScope
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.CardsFileFragment
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.ControllingTheScrollPosition
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.cardsfile.sourcetext.ImportedTextEditorEvent.EncodingWasSelected
import com.odnovolov.forgetmenot.presentation.screen.cardsimport.editor.getEditorColorScheme
import kotlinx.android.synthetic.main.fragment_cards_file.*
import kotlinx.android.synthetic.main.fragment_imported_text_editor.*
import kotlinx.android.synthetic.main.popup_charsets.view.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.charset.Charset

class ImportedTextEditorFragment : BaseFragment(), ControllingTheScrollPosition {
    companion object {
        const val ARG_ID = "ARG_ID"
        const val MAX_TEXT_LENGTH_TO_EDIT = 50_000
        const val MAX_ERROR_LINES_TO_SHOW = 50
        const val STATE_CHARSET_POPUP = "STATE_CHARSET_POPUP"

        fun create(id: Long) = ImportedTextEditorFragment().apply {
            arguments = Bundle(1).apply {
                putLong(ARG_ID, id)
            }
        }
    }

    private var controller: ImportedTextEditorController? = null
    private lateinit var viewModel: ImportedTextEditorViewModel
    private var charsetPopup: PopupWindow? = null
    private var charsetAdapter: CharsetAdapter? = null
    private var errorBlocks: List<ErrorBlock> = emptyList()
    private var lastShownErrorBlock: ErrorBlock? = null
    private val lastShownErrorLine: Int
        get() = lastShownErrorBlock?.lines?.get(0) ?: -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_imported_text_editor, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupView()
        viewCoroutineScope!!.launch {
            val diScope = CardsImportDiScope.getAsync() ?: return@launch
            controller = diScope.importedTextEditorController
            val id = requireArguments().getLong(ARG_ID)
            viewModel = diScope.importedTextEditorViewModel(id)
            editor.language = diScope.syntaxHighlighting
            observeViewModel()
        }
    }

    private fun setupView() {
        editor.colorScheme = getEditorColorScheme(requireContext())
        charsetButton.setOnClickListener {
            showCharsetPopup()
        }
        errorButton.setOnClickListener {
            showNextError()
        }
        undoButton.isEnabled = false
        redoButton.isEnabled = false
        undoButton.setOnClickListener {
            if (editor.canUndo()) editor.undo()
        }
        redoButton.setOnClickListener {
            if (editor.canRedo()) editor.redo()
        }
        editor.onUndoRedoChangedListener = OnUndoRedoChangedListener {
            undoButton.isEnabled = editor.canUndo()
            redoButton.isEnabled = editor.canRedo()
        }
        undoButton.setTooltipTextFromContentDescription()
        redoButton.setTooltipTextFromContentDescription()
    }

    private fun showNextError() {
        if (errorBlocks.isEmpty()) return
        determineNextErrorBlock()
        if (editor.isVisible) {
            editorScrollView.smoothScrollTo(0, determineErrorLineVerticalPosition(), 500)
        }
        errorButton.text = composeErrorMessage()
    }

    private fun determineNextErrorBlock() {
        lastShownErrorBlock = errorBlocks.find { errorBlock: ErrorBlock ->
            errorBlock.lines[0] > lastShownErrorLine
        } ?: errorBlocks.first()
    }

    private fun determineErrorLineVerticalPosition(): Int {
        val errorLineStartIndex: Int = editor.getIndexForStartOfLine(lastShownErrorLine)
        val lineInTermsOfLayout: Int = editor.layout.getLineForOffset(errorLineStartIndex)
        return editor.layout.getLineTop(lineInTermsOfLayout)
    }

    private fun composeErrorMessage(): String {
        val errorOrdinal = errorBlocks.indexOf(lastShownErrorBlock) + 1
        val linesString =
            if (lastShownErrorBlock!!.lines.size > 1) {
                val firstLineNumber = lastShownErrorBlock!!.lines.first() + 1
                val lastLineNumber = lastShownErrorBlock!!.lines.last() + 1
                "lines $firstLineNumber - $lastLineNumber"
            } else {
                val lineNumber = lastShownErrorBlock!!.lines.first() + 1
                "line $lineNumber"
            }
        return "Error $errorOrdinal/${errorBlocks.size}: ${lastShownErrorBlock!!.errorMessage} ($linesString)"
    }

    private fun observeViewModel() {
        with(viewModel) {
            updateTextCommand.observe { text: String ->
                if (text.length <= MAX_TEXT_LENGTH_TO_EDIT) {
                    editor.setTextContent(text)
                    editor.isVisible = true
                    editOffTextView.isVisible = false
                } else {
                    editor.isVisible = false
                    editOffTextView.isVisible = true
                }
            }
            errors.observe { errors: List<ErrorBlock> ->
                if (this@ImportedTextEditorFragment.errorBlocks.isNotEmpty()) {
                    editor.clearErrorLines()
                }
                this@ImportedTextEditorFragment.errorBlocks = errors
                applyErrors(errors)
                if (errors.isEmpty()) {
                    editor.clearErrorLines()
                }
                val numberOfErrors = errors.size
                errorButton.isVisible = numberOfErrors > 0
                errorLineView.isVisible = numberOfErrors > 0
                if (numberOfErrors > 0) {
                    errorButton.text = resources.getQuantityString(
                        R.plurals.source_text_error_button,
                        numberOfErrors,
                        numberOfErrors
                    )
                }
            }
            if (isViewFirstCreated) {
                viewCoroutineScope!!.launch {
                    val errorLinesAtStart = errors.first()
                    this@ImportedTextEditorFragment.errorBlocks = errorLinesAtStart
                    if (errorLinesAtStart.isNotEmpty()) {
                        (parentFragment as CardsFileFragment).viewPager?.run {
                            post { setCurrentItem(1, false) }
                        }
                        editor.doOnLayout {
                            editor.post { showNextError() }
                        }
                    }
                }
            }
            currentCharset.observe { charset: Charset ->
                charsetButton.text = charset.name()
            }
        }
    }

    private fun applyErrors(errors: List<ErrorBlock>) {
        var numberOfErrorLines = 0
        errors.forEach { errorBlock: ErrorBlock ->
            errorBlock.lines.forEach { errorLine: Int ->
                editor.setErrorLine(errorLine + 1)
                numberOfErrorLines++
                if (numberOfErrorLines == MAX_ERROR_LINES_TO_SHOW) return
            }
        }
    }

    private fun TextProcessor.clearErrorLines() {
        text.getSpans(0, text.length, ErrorSpan::class.java).forEach(text::removeSpan)
    }

    private fun showCharsetPopup() {
        requireCharsetPopup().show(anchor = charsetButton, gravity = Gravity.BOTTOM)
        charsetButton.requestLayout()
    }

    private fun requireCharsetPopup(): PopupWindow {
        if (charsetPopup == null) {
            val content: View = View.inflate(requireContext(), R.layout.popup_charsets, null)
            val onItemClicked: (Charset) -> Unit = { charset: Charset ->
                charsetPopup?.dismiss()
                controller?.dispatch(EncodingWasSelected(charset))
            }
            charsetAdapter = CharsetAdapter(onItemClicked)
            content.charsetRecycler.adapter = charsetAdapter
            charsetPopup = DarkPopupWindow(content)
            subscribeCharsetPopupToViewModel()
        }
        return charsetPopup!!
    }

    private fun subscribeCharsetPopupToViewModel() {
        viewCoroutineScope!!.launch {
            val diScope = CardsImportDiScope.getAsync() ?: return@launch
            val id = requireArguments().getLong(ARG_ID)
            val viewModel = diScope.importedTextEditorViewModel(id)
            viewModel.availableCharsets.observe { availableCharsets: List<CharsetItem> ->
                charsetAdapter!!.items = availableCharsets
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun getScrollPosition(): Float {
        val offset: Float = editorScrollView.computeVerticalScrollOffset().toFloat()
        val extent: Int = editorScrollView.computeVerticalScrollExtent()
        val range: Int = editorScrollView.computeVerticalScrollRange()
        return offset / (range - extent)
    }

    @SuppressLint("RestrictedApi")
    override fun scrollTo(scrollPercentage: Float) {
        val extent: Int = editorScrollView.computeVerticalScrollExtent()
        val range: Int = editorScrollView.computeVerticalScrollRange()
        val offset: Int = ((range - extent) * scrollPercentage).toInt()
        editorScrollView.scrollTo(0, offset)
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        savedInstanceState?.run {
            val needToShowCharsetPopup = getBoolean(STATE_CHARSET_POPUP, false)
            if (needToShowCharsetPopup) showCharsetPopup()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val isCharsetPopupShowing = charsetPopup?.isShowing ?: false
        outState.putBoolean(STATE_CHARSET_POPUP, isCharsetPopupShowing)
    }

    override fun onPause() {
        super.onPause()
        editor.hideSoftInput()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        charsetPopup?.dismiss()
        charsetPopup = null
    }
}