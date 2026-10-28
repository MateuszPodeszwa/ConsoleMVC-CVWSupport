'use strict';

/**
 * Formats schema entries as Markdown for hovers and completion details, so both show the same text.
 *
 * @example
 * Documentation.element(schema.element('box'));        // '**`<box>`** A bordered panel …'
 * Documentation.attribute(schema.attribute('box', 'border')); // '**`border`** The border style. …'
 */
class Documentation {
    /**
     * Describes an element: what it is, what it may contain and its attributes.
     *
     * @param {object} element An element from the schema.
     * @returns {string} Markdown.
     * @example
     * Documentation.element(schema.element('menu'));
     */
    static element(element) {
        const facts = [`*Content:* ${Documentation.contentText(element.content)}`];
        if (element.parents) {
            facts.push(`*Only inside:* ${element.parents.map(p => `\`<${p}>\``).join(', ')}`);
        }
        if (element.children) {
            facts.push(`*Holds:* ${element.children.map(c => `\`<${c}>\``).join(', ')}`);
        }
        if (element.topLevelOnly) {
            facts.push('*Only at the top level of a view*, not inside another element.');
        }

        const parts = [`**\`<${element.name}>\`** ${element.description}`, facts.join('  \n')];
        if (element.attributes.length > 0) {
            const rows = element.attributes.map(a =>
                `| \`${a.name}\`${a.required ? ' *(required)*' : ''} | ${Documentation.escapeCell(a.description)} |`);
            parts.push(['| Attribute | Purpose |', '|---|---|', ...rows].join('\n'));
        }
        return parts.join('\n\n');
    }

    /**
     * Describes an attribute: what it does and what it accepts.
     *
     * @param {object} attribute An attribute from the schema.
     * @returns {string} Markdown.
     * @example
     * Documentation.attribute(schema.attribute('progress', 'value'));
     */
    static attribute(attribute) {
        const parts = [`**\`${attribute.name}\`** ${attribute.description}`];
        parts.push(attribute.flag
            ? `*A flag:* write it without a value, such as \`${attribute.name}\`.`
            : `*Accepts:* ${attribute.accepts}`);
        if (attribute.required) {
            parts.push('*Required.*');
        }
        if (attribute.interpolation) {
            parts.push('*May mix text and `@expressions`.*');
        }
        return parts.join('\n\n');
    }

    /**
     * Describes a directive.
     *
     * @param {object} directive A directive from the schema.
     * @returns {string} Markdown.
     * @example
     * Documentation.directive(schema.directive('layout'));
     */
    static directive(directive) {
        return `**\`${directive.syntax}\`**\n\n${directive.description}`;
    }

    /**
     * Words for a content model.
     *
     * @param {string} content One of void, inline, block or elements.
     * @returns {string} A short description.
     * @example
     * Documentation.contentText('void'); // 'none: write it as <name />'
     */
    static contentText(content) {
        switch (content) {
            case 'void': return 'none: write it self-closing, as `<name />`';
            case 'inline': return 'text, `@expressions` and `<br />`';
            case 'block': return 'elements; plain text becomes a `<text>`';
            case 'elements': return 'only its child elements';
            default: return content;
        }
    }

    /** Escapes `|` so a description cannot break a Markdown table. @private */
    static escapeCell(text) {
        return text.replace(/\|/g, '\\|');
    }
}

module.exports = { Documentation };
