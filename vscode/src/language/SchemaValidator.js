'use strict';

const { MarkupParser } = require('../markup/MarkupParser');

/**
 * The severities of a check, as the extension maps them to editor diagnostics.
 *
 * @example
 * problem.severity === Severity.Error;
 */
const Severity = Object.freeze({ Error: 'error', Warning: 'warning' });

/** A C# identifier, as each part of a route must be. */
const IDENTIFIER = /^[\p{L}_][\p{L}\p{Nd}_]*$/u;

/**
 * Whether a value contains an @expression; `@@` is an escaped @, and the @ of an e-mail address is text.
 *
 * @param {string} value An attribute's value.
 * @returns {boolean} True when the build evaluates part of it.
 * @example
 * hasExpression('Hi @Model.Name'); // true
 * hasExpression('me@example.com'); // false
 */
function hasExpression(value) {
    return /(^|[^\p{L}\p{Nd}@])@(?=[\p{L}_(])/u.test(value.replace(/@@/g, ''));
}

/** A hex colour, such as #f80 or #ff8800. */
const HEX_COLOUR = /^#(?:[0-9a-f]{3}|[0-9a-f]{6})$/i;

/** A length: auto, *, a column count or a percentage (at most 100, checked separately). */
const LENGTH = /^(?:auto|\*|(\d{1,9})(%?))$/i;

/**
 * Rules the schema does not express: attributes of which exactly one must be written.
 *
 * @example
 * EXCLUSIVE_ATTRIBUTES.option.attributes; // ['nav', 'quit', 'back']
 */
const EXCLUSIVE_ATTRIBUTES = Object.freeze({
    option: {
        attributes: ['nav', 'quit', 'back'],
        code: 'CVW109',
        message: "<option> needs exactly one of 'nav' (e.g. nav=\"Home/Index\"), 'quit' or 'back'."
    }
});

/**
 * Checks the markup of a view against the schema while it is typed, when the `cvw-preview` tool (which reports the
 * build's own diagnostics) is not installed. It checks what the schema knows: unknown elements and attributes,
 * elements in the wrong place, missing required attributes, values outside their choices or range, and unclosed
 * elements. Values with `@expressions` are left to the build. Codes match the generator's CVWxxx diagnostics where
 * one applies.
 *
 * @example
 * const problems = new SchemaValidator(schema).validate('<box border="dotted" />');
 * problems[0].message; // "'dotted' is not a valid border: expected one of 'single', 'double', …"
 */
class SchemaValidator {
    /**
     * @param {import('../schema/MarkupSchema').MarkupSchema} schema The markup schema.
     * @example
     * const validator = new SchemaValidator(MarkupSchema.load());
     */
    constructor(schema) {
        this._schema = schema;
    }

    /**
     * Checks a view.
     *
     * @param {string} text The view's text.
     * @returns {{start: number, end: number, severity: string, code: string | null, message: string}[]} The problems,
     *     in source order.
     * @example
     * validator.validate(text).filter(p => p.severity === Severity.Error);
     */
    validate(text) {
        const problems = [];
        for (const element of MarkupParser.parse(text, this._schema).allElements()) {
            this._checkElement(element, problems);
        }
        return problems.sort((a, b) => a.start - b.start);
    }

    /** @private */
    _checkElement(element, problems) {
        const nameRange = { start: element.start, end: element.nameEnd };
        const schemaElement = this._schema.element(element.name);
        if (!schemaElement) {
            problems.push(problem(nameRange, Severity.Error, 'CVW105',
                `Unknown element <${element.name}>. To show a literal '<', write &lt; instead.`));
            return;
        }

        this._checkPlacement(element, schemaElement, nameRange, problems);

        if (element.startTagEnd < 0) {
            problems.push(problem(nameRange, Severity.Error, null, `The start tag of <${element.name}> is not finished with > or />.`));
        } else if (!element.isClosed && schemaElement.content !== 'void') {
            problems.push(problem(nameRange, Severity.Error, null, `<${element.name}> is not closed: add </${element.name}>.`));
        }

        const seen = new Set();
        for (const attribute of element.attributes) {
            const range = { start: attribute.start, end: attribute.nameEnd };
            const schemaAttribute = schemaElement.attributes.find(a => a.name === attribute.name);
            if (!schemaAttribute) {
                problems.push(problem(range, Severity.Error, 'CVW100', `<${element.name}> has no attribute '${attribute.name}'. ` +
                    `Valid attributes: ${schemaElement.attributes.map(a => a.name).join(', ')}.`));
                continue;
            }
            if (seen.has(schemaAttribute.description)) {
                problems.push(problem(range, Severity.Error, null, `'${attribute.name}' is set twice on <${element.name}>.`));
            }
            seen.add(schemaAttribute.description); // aliases (color, colour) share a description
            if (attribute.unterminated) {
                problems.push(problem(range, Severity.Error, null, `The value of '${attribute.name}' has no closing quote.`));
                continue;
            }
            const valueProblem = SchemaValidator.checkValue(schemaAttribute, attribute.value);
            if (valueProblem) {
                const valueRange = attribute.valueStart >= 0 ? { start: attribute.valueStart, end: attribute.valueEnd } : range;
                problems.push(problem(valueRange.end > valueRange.start ? valueRange : range, Severity.Error, null, valueProblem));
            }
        }

        if (schemaElement.content === 'elements' && element.textRanges.length > 0) {
            const allowed = (schemaElement.children ?? []).map(c => `<${c}>`).join(', ');
            problems.push(problem(element.textRanges[0], Severity.Error, 'CVW110',
                `Text and expressions are not allowed directly inside <${element.name}>; it may only contain ${allowed}.`));
        }

        const exclusive = EXCLUSIVE_ATTRIBUTES[element.name];
        if (exclusive && exclusive.attributes.filter(name => element.attribute(name)).length !== 1) {
            problems.push(problem(nameRange, Severity.Error, exclusive.code, exclusive.message));
        }

        const missing = schemaElement.attributes.filter(a => a.required
            && !schemaElement.attributes.some(b => b.description === a.description && element.attribute(b.name)));
        for (const attribute of missing.filter((a, i, all) => all.findIndex(b => b.description === a.description) === i)) {
            problems.push(problem(nameRange, Severity.Error, null, `<${element.name}> needs the attribute '${attribute.name}'.`));
        }
    }

    /** Elements that may not appear inside their parent (or at the top level). @private */
    _checkPlacement(element, schemaElement, range, problems) {
        const parentName = element.parent ? element.parent.name : null;
        if (element.parent && !this._schema.element(parentName)) {
            return; // the unknown parent is reported already
        }
        const allowed = this._schema.elementsAllowedIn(parentName).some(e => e.name === element.name);
        if (allowed) {
            return;
        }
        let message;
        if (schemaElement.parents && !schemaElement.parents.includes(parentName)) {
            message = `<${element.name}> must be inside ${schemaElement.parents.map(p => `<${p}>`).join(' or ')}.`;
        } else if (schemaElement.topLevelOnly) {
            message = `<${element.name}> must be at the top level of the view, not inside <${parentName}>.`;
        } else {
            message = `<${element.name}> cannot be inside <${parentName}>.`;
        }
        problems.push(problem(range, Severity.Error, null, message));
    }

    /**
     * Checks a literal value against what an attribute accepts.
     *
     * @param {object} attribute An attribute from the schema.
     * @param {string | null} value The written value; null for a flag written without one.
     * @returns {string | null} What is wrong, or null when the value is fine or contains an expression.
     * @example
     * SchemaValidator.checkValue(schema.attribute('box', 'padding'), '99'); // "'99' is not a valid padding: …"
     */
    static checkValue(attribute, value) {
        if (value === null) {
            return attribute.flag || attribute.kind === 'boolean' ? null : `'${attribute.name}' needs a value.`;
        }
        if (hasExpression(value)) {
            return null; // an expression: only the build can check it
        }
        const check = VALUE_CHECKS[attribute.kind];
        return !check || check(value.trim(), attribute) ? null
            : `'${value}' is not a valid ${attribute.name}: expected ${attribute.accepts}.`;
    }
}

/**
 * Whether a literal is accepted, per attribute kind, mirroring each AttributeType.TryTranslateLiteral of the generator.
 * Kinds without a check here (text, route, model) are left to the build.
 *
 * @example
 * VALUE_CHECKS.length('50%'); // true
 */
const VALUE_CHECKS = Object.freeze({
    choice: (text, attribute) => (attribute.values ?? []).some(v => v.toLowerCase() === text.toLowerCase()),
    boolean: text => /^(true|false)$/i.test(text),
    integer: (text, attribute) => {
        if (!/^[+-]?\d{1,10}$/.test(text)) {
            return false;
        }
        const number = Number(text);
        return number >= (attribute.minimum ?? -2147483648) && number <= (attribute.maximum ?? 2147483647);
    },
    number: text => /^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(text),
    'number-list': text => text.split(/[,\s]+/).filter(Boolean).every(n => /^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(n)),
    colour: (text, attribute) => {
        if (text === '' || text.toLowerCase() === 'default') {
            return true;
        }
        if (text.startsWith('#')) {
            return HEX_COLOUR.test(text);
        }
        return (attribute.values ?? []).includes(text.replace(/[-_ ]/g, '').toLowerCase());
    },
    length: text => {
        const match = LENGTH.exec(text);
        return Boolean(match) && (!match[2] || Number(match[1]) <= 100);
    },
    route: text => {
        const query = text.indexOf('?');
        const parts = (query < 0 ? text : text.slice(0, query)).split('/');
        if (parts.length > 3 || !parts.every(part => IDENTIFIER.test(part))) {
            return false;
        }
        // Route values: key=value pairs separated by &, each with a key
        return query < 0 || text.slice(query + 1).split('&').every(pair => pair.length === 0 || (pair[0] !== '=' && pair.trim().length > 0));
    },
    date: text => {
        const lower = text.toLowerCase();
        if (/^today(?:[+-]\d{1,9})?$/.test(lower)) {
            return true;
        }
        const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(lower);
        if (!match) {
            return false;
        }
        const date = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3])));
        return date.getUTCFullYear() === Number(match[1]) && date.getUTCMonth() === Number(match[2]) - 1
            && date.getUTCDate() === Number(match[3]);
    }
});

/** A problem record. */
function problem(range, severity, code, message) {
    return { start: range.start, end: range.end, severity, code, message };
}

module.exports = { SchemaValidator, Severity, hasExpression };
