'use strict';

const vscode = require('vscode');
const { MarkupSchema } = require('./schema/MarkupSchema');
const { CompletionService, CompletionKind } = require('./language/CompletionService');
const { HoverService } = require('./language/HoverService');
const { PreviewController, LANGUAGE_ID } = require('./preview/PreviewController');

/**
 * The characters after which completion opens by itself: a tag, a closing tag, an attribute, a value, an @.
 *
 * @example
 * vscode.languages.registerCompletionItemProvider(selector, provider, ...TRIGGER_CHARACTERS);
 */
const TRIGGER_CHARACTERS = ['<', '/', ' ', '"', '\'', '@'];

/** Editor icons for each kind of suggestion. */
const ITEM_KINDS = {
    [CompletionKind.Element]: vscode.CompletionItemKind.Class,
    [CompletionKind.Attribute]: vscode.CompletionItemKind.Property,
    [CompletionKind.Value]: vscode.CompletionItemKind.EnumMember,
    [CompletionKind.Directive]: vscode.CompletionItemKind.Keyword,
    [CompletionKind.Construct]: vscode.CompletionItemKind.Snippet,
    [CompletionKind.CloseTag]: vscode.CompletionItemKind.Class
};

/**
 * Starts the extension: completion and hovers from the markup schema, and the preview and checks from cvw-preview.
 * Called by VS Code when a `.cvw` file is opened.
 *
 * @param {import('vscode').ExtensionContext} context The extension's context.
 * @example
 * // VS Code calls it; tests call it with a fake context
 * activate(context);
 */
function activate(context) {
    const schema = MarkupSchema.load();
    const completion = new CompletionService(schema);
    const hover = new HoverService(schema);
    const selector = { language: LANGUAGE_ID };

    const controller = new PreviewController(vscode, schema).start();
    context.subscriptions.push(controller);

    context.subscriptions.push(vscode.languages.registerCompletionItemProvider(selector, {
        provideCompletionItems(document, position) {
            const text = document.getText();
            return completion.complete(text, document.offsetAt(position)).map(item => toCompletionItem(document, item));
        }
    }, ...TRIGGER_CHARACTERS));

    context.subscriptions.push(vscode.languages.registerHoverProvider(selector, {
        provideHover(document, position) {
            const found = hover.hover(document.getText(), document.offsetAt(position));
            if (!found) {
                return null;
            }
            const range = new vscode.Range(document.positionAt(found.start), document.positionAt(found.end));
            return new vscode.Hover(new vscode.MarkdownString(found.markdown), range);
        }
    }));

    const showPreview = column => () => {
        const editor = vscode.window.activeTextEditor;
        if (!editor || editor.document.languageId !== LANGUAGE_ID) {
            vscode.window.showInformationMessage('Open a .cvw view to preview it.');
            return;
        }
        controller.showPreview(editor.document, column);
    };
    context.subscriptions.push(
        vscode.commands.registerCommand('consolemvc.showPreview', showPreview(vscode.ViewColumn.Active)),
        vscode.commands.registerCommand('consolemvc.showPreviewToSide', showPreview(vscode.ViewColumn.Beside))
    );
}

/**
 * Converts a suggestion from {@link CompletionService} into a VS Code completion item.
 *
 * @param {import('vscode').TextDocument} document The view.
 * @param {object} item The suggestion.
 * @returns {import('vscode').CompletionItem} The item.
 */
function toCompletionItem(document, item) {
    const result = new vscode.CompletionItem(item.label, ITEM_KINDS[item.kind]);
    result.insertText = new vscode.SnippetString(item.insertText);
    result.range = new vscode.Range(document.positionAt(item.replaceStart), document.positionAt(item.replaceEnd));
    result.detail = item.detail;
    result.sortText = item.sortText;
    // Filter on what is typed in the range: for a close tag that includes "</"
    result.filterText = item.kind === CompletionKind.CloseTag ? item.insertText : item.label;
    if (item.documentation) {
        result.documentation = new vscode.MarkdownString(item.documentation);
    }
    if (item.kind === CompletionKind.Attribute && !item.insertText.endsWith(item.label)) {
        result.command = { command: 'editor.action.triggerSuggest', title: 'Suggest values' };
    }
    return result;
}

/** Nothing to clean up beyond the context's subscriptions. */
function deactivate() {
}

module.exports = { activate, deactivate, TRIGGER_CHARACTERS };
