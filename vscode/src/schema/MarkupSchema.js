'use strict';

const fs = require('node:fs');
const path = require('node:path');

/**
 * The newest schema format this extension understands. A schema with a higher formatVersion changed the meaning of a
 * field, so it is refused rather than misread.
 *
 * @example
 * if (json.formatVersion > SUPPORTED_FORMAT_VERSION) throw new Error('Update the extension');
 */
const SUPPORTED_FORMAT_VERSION = 1;

/**
 * The C-View Markup schema (schema/cvw-schema.json, written by ConsoleMVC's MarkupSchemaExport): elements, attributes,
 * directives, constructs and diagnostics, with lookups by name. Read-only; every editor feature asks it what is valid.
 *
 * @example
 * const schema = MarkupSchema.load();
 * schema.element('box').attributes.map(a => a.name);   // ['class', 'title', 'width', ...]
 * schema.attribute('box', 'border').values;            // ['single', 'double', ...]
 * schema.elementsAllowedIn('table').map(e => e.name);  // ['row']
 */
class MarkupSchema {
    /**
     * Wraps parsed schema JSON.
     *
     * @param {object} json The parsed contents of cvw-schema.json.
     * @throws {Error} When the JSON is not a schema, or is of a newer format.
     * @example
     * const schema = new MarkupSchema(JSON.parse(text));
     */
    constructor(json) {
        if (!json || !Array.isArray(json.elements) || typeof json.formatVersion !== 'number') {
            throw new Error('Not a C-View Markup schema: expected formatVersion and elements.');
        }
        if (json.formatVersion > SUPPORTED_FORMAT_VERSION) {
            throw new Error(`The schema's format ${json.formatVersion} is newer than this extension understands ` +
                `(${SUPPORTED_FORMAT_VERSION}); update the extension.`);
        }

        /** @type {ReadonlyArray<object>} Every element, in the generator's order. */
        this.elements = Object.freeze([...json.elements]);
        /** @type {ReadonlyArray<object>} The directives, such as @model. */
        this.directives = Object.freeze([...(json.directives ?? [])]);
        /** @type {ReadonlyArray<object>} The @ constructs, such as @foreach. */
        this.constructs = Object.freeze([...(json.constructs ?? [])]);
        /** @type {ReadonlyArray<object>} The CVWxxx diagnostics. */
        this.diagnostics = Object.freeze([...(json.diagnostics ?? [])]);

        this._elementsByName = new Map(this.elements.map(e => [e.name, e]));
        this._diagnosticsById = new Map(this.diagnostics.map(d => [d.id, d]));
    }

    /**
     * Reads a schema file; by default the copy shipped with the extension.
     *
     * @param {string} [file] The path of a cvw-schema.json.
     * @returns {MarkupSchema} The schema.
     * @example
     * const schema = MarkupSchema.load(path.join(projectRoot, 'schema', 'cvw-schema.json'));
     */
    static load(file = MarkupSchema.bundledPath) {
        return new MarkupSchema(JSON.parse(fs.readFileSync(file, 'utf8')));
    }

    /**
     * The path of the schema shipped with the extension.
     *
     * @type {string}
     * @example
     * fs.existsSync(MarkupSchema.bundledPath); // true
     */
    static get bundledPath() {
        return path.join(__dirname, '..', '..', 'schema', 'cvw-schema.json');
    }

    /**
     * Finds an element.
     *
     * @param {string} name The tag name, such as "box".
     * @returns {object | undefined} The element, or undefined when the markup has no such element.
     * @example
     * schema.element('text').content; // 'inline'
     */
    element(name) {
        return this._elementsByName.get(name);
    }

    /**
     * Finds an attribute of an element.
     *
     * @param {string} elementName The tag name.
     * @param {string} attributeName The attribute name, such as "border".
     * @returns {object | undefined} The attribute, or undefined.
     * @example
     * schema.attribute('progress', 'value').kind; // 'number'
     */
    attribute(elementName, attributeName) {
        return this.element(elementName)?.attributes.find(a => a.name === attributeName);
    }

    /**
     * Finds a directive.
     *
     * @param {string} name The name with or without the @, such as "model" or "@model".
     * @returns {object | undefined} The directive, or undefined.
     * @example
     * schema.directive('layout').syntax; // '@layout Name'
     */
    directive(name) {
        const wanted = name.startsWith('@') ? name : '@' + name;
        return this.directives.find(d => d.name === wanted);
    }

    /**
     * Finds a diagnostic.
     *
     * @param {string} id The identifier, such as "CVW105".
     * @returns {object | undefined} The diagnostic, or undefined.
     * @example
     * schema.diagnosticById('CVW105').severity; // 'error'
     */
    diagnosticById(id) {
        return this._diagnosticsById.get(id);
    }

    /**
     * The elements that may be written inside a parent, or at the top level of a view.
     *
     * @param {string | null} parentName The parent's tag name, or null for the top level (also inside @if and @foreach).
     * @returns {object[]} The elements allowed there, in the generator's order.
     * @example
     * schema.elementsAllowedIn(null).some(e => e.name === 'menu'); // true
     * schema.elementsAllowedIn('box').some(e => e.name === 'menu'); // false: a menu is top-level only
     */
    elementsAllowedIn(parentName) {
        if (parentName === null) {
            return this.elements.filter(e => e.parents === null);
        }

        const parent = this.element(parentName);
        if (!parent || parent.content === 'void' || parent.content === 'inline') {
            // Inline content holds text and <br /> only
            return parent?.content === 'inline' ? this.elements.filter(e => e.name === 'br') : [];
        }
        if (Array.isArray(parent.children)) {
            return parent.children.map(name => this.element(name)).filter(Boolean);
        }

        return this.elements.filter(e => !e.topLevelOnly && (e.parents === null || e.parents.includes(parentName)));
    }
}

module.exports = { MarkupSchema, SUPPORTED_FORMAT_VERSION };
