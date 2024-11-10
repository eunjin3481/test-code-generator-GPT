package com.testCodeGenerator.dialog

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.ui.components.JBScrollPane
import com.testCodeGenerator.dto.GeneratedTestCode
import com.testCodeGenerator.service.ChatGptService
import java.awt.BorderLayout
import javax.swing.Action
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JSplitPane
import javax.swing.SwingUtilities

class TestCodeGeneratorDialog(
    private val editor: Editor,
    private val project: Project,
    private val selectedCode: String
) : DialogWrapper(project) {
    private val loadingPanel = JBLoadingPanel(BorderLayout(), disposable)
    private val generateButton = JButton("Generate Test Code")
    private val editorFactory: EditorFactory = EditorFactory.getInstance()
    private val originalCodeEditor: EditorEx =
        editorFactory.createViewer(editorFactory.createDocument(selectedCode.trimIndent()), project) as EditorEx
    private val generatedTestCodeEditor: EditorEx =
        editorFactory.createEditor(editorFactory.createDocument(""), project) as EditorEx
    private val chatGptService = ChatGptService()
    private val fileExtension = FileDocumentManager.getInstance().getFile(editor.document)?.extension ?: "txt"

    val onGenerate = { generatedCode: String ->
        replaceEditorToGeneratedCode(editor, generatedCode)
    }

    init {
        title = "Test Code Generator"
        isResizable = true
        initEditors()
        init()
        setSize(1300, 600)
    }

    override fun createActions(): Array<Action> = emptyArray()

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())

        val originalCodeLabel = JBLabel("Original Code:")
        val generatedTestCodeLabel = JBLabel("Generated Test Code:")

        originalCodeLabel.border = BorderFactory.createEmptyBorder(0, 10, 10, 0)
        generatedTestCodeLabel.border = BorderFactory.createEmptyBorder(0, 10, 10, 0)

        val originalCodeScrollPane = JBScrollPane(originalCodeEditor.component)
        val generatedTestCodeScrollPane = JBScrollPane(generatedTestCodeEditor.component)

        val originalCodePanel = JPanel(BorderLayout())
        originalCodePanel.add(originalCodeLabel, BorderLayout.NORTH)
        originalCodePanel.add(originalCodeScrollPane, BorderLayout.CENTER)

        val generatedTestCodePanel = JPanel(BorderLayout())
        generatedTestCodePanel.add(generatedTestCodeLabel, BorderLayout.NORTH)
        generatedTestCodePanel.add(generatedTestCodeScrollPane, BorderLayout.CENTER)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, originalCodePanel, generatedTestCodePanel)
        splitPane.apply {
            resizeWeight = 0.5
            isContinuousLayout = true
            dividerSize = 15
        }
        SwingUtilities.invokeLater { splitPane.setDividerLocation(0.5) }

        val contentPanel = JPanel(BorderLayout())
        contentPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        contentPanel.add(splitPane, BorderLayout.CENTER)

        generateButton.isEnabled = false
        generateButton.addActionListener {
            onGenerate.invoke(generatedTestCodeEditor.document.text)
            close(OK_EXIT_CODE)
        }

        val buttonPanel = JPanel()
        buttonPanel.add(generateButton)

        loadingPanel.add(contentPanel)
        loadingPanel.add(buttonPanel, BorderLayout.SOUTH)

        panel.add(loadingPanel, BorderLayout.CENTER)

        return panel
    }

    override fun show() {
        Thread {
            runCatching {
                chatGptService.generateTestCode(fileExtension, selectedCode)
            }.fold(
                onSuccess = { generatedTestCode ->
                    SwingUtilities.invokeLater {
                        updateDialogWithGeneratedTestCode(generatedTestCode)
                        setLoading(false)
                    }
                },
                onFailure = { exception ->
                    SwingUtilities.invokeLater {
                        Messages.showErrorDialog(
                            project,
                            "Failed to generate test code: ${exception.message}",
                            "Test Code Generation Error"
                        )
                        setLoading(false)
                        super.close(OK_EXIT_CODE)
                    }
                }
            )
        }.start()

        setLoading(true)
        super.show()
    }

    private fun updateDialogWithGeneratedTestCode(generatedTestCode: GeneratedTestCode) {
        updateGeneratedTestCode(generatedTestCode.code)
    }
    private fun setLoading(isLoading: Boolean) {
        if (isLoading) {
            loadingPanel.startLoading()
            generateButton.isEnabled = false
        } else {
            loadingPanel.stopLoading()
            generateButton.isEnabled = true
        }
    }

    private fun updateGeneratedTestCode(generatedCode: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            generatedTestCodeEditor.document.setText(generatedCode.trimIndent())
        }
    }

    private fun initEditors() {
        val fileType = FileTypeManager.getInstance().getFileTypeByExtension(fileExtension)
        val editorHighlighterFactory = EditorHighlighterFactory.getInstance()

        originalCodeEditor.apply {
            highlighter = editorHighlighterFactory.createEditorHighlighter(
                fileType,
                EditorColorsManager.getInstance().globalScheme,
                project
            )
            isOneLineMode = false
            isEmbeddedIntoDialogWrapper = true
        }

        generatedTestCodeEditor.apply {
            highlighter = editorHighlighterFactory.createEditorHighlighter(
                fileType,
                EditorColorsManager.getInstance().globalScheme,
                project
            )
            isOneLineMode = false
            isEmbeddedIntoDialogWrapper = true
        }
    }

    private fun replaceEditorToGeneratedCode(editor: Editor, generatedCode: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            with(editor.document) {
                val selectionModel = editor.selectionModel
                val startOffset = selectionModel.selectionStart
                val endOffset = selectionModel.selectionEnd

                replaceString(startOffset, endOffset, generatedCode)

                PsiDocumentManager.getInstance(project).commitDocument(this)

                PsiDocumentManager.getInstance(project).getPsiFile(this)
                    ?.let { file ->
                        CodeStyleManager.getInstance(project).reformatText(file, startOffset, endOffset)
                    }
            }
        }
    }
}