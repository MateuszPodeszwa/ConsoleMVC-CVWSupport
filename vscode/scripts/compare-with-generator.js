'use strict';

/**
 * Compares the extension's SchemaValidator with ConsoleMVC's own source generator, through cvw-preview: it writes
 * thousands of one-line views (every attribute of every element with valid, invalid and edge values), checks each
 * with both, and lists the lines where one reports an error and the other does not. Run it after syncing a new schema.
 *
 * @example
 * node scripts/compare-with-generator.js ~/.dotnet/tools/cvw-preview
 */

const { spawnSync } = require('node:child_process');
const { MarkupSchema } = require('../src/schema/MarkupSchema');
const { SchemaValidator } = require('../src/language/SchemaValidator');

const tool = process.argv[2] || 'cvw-preview';
const schema = MarkupSchema.load();
const validator = new SchemaValidator(schema);

/** Values tried for every attribute, besides its own values and range edges. */
const COMMON_VALUES = ['', 'junk', '0', '1', '-1', '5', '40', '50%', '100%', '150%', '*', 'auto', '2*', '1.5', '#ff8800',
    '#FFF', '#12345', 'red', 'Red', 'DARKGREY', 'true', 'false', 'yes', '2026-01-01', '2026-02-30', 'today+7', '1, 2, 3',
    '1 2', '1,,2', 'Home/Index', 'Admin/Users/Index', 'A/B/C/D', 'Orders/Details?id=42', 'x?=1'];

/** One required attribute with a value its kind accepts. */
function requiredAttributes(element, except) {
    return element.attributes
        .filter(a => a.required && !a.flag && a.description !== except?.description)
        .filter((a, i, all) => all.findIndex(b => b.description === a.description) === i)
        .map(a => ` ${a.name}="${a.kind === 'route' ? 'Index' : a.kind === 'choice' ? a.values[0] : a.kind === 'number-list' ? '1' : 'k'}"`)
        .join('');
}

/** The smallest valid markup for an element, with its required attributes and a child where it needs one. */
function sample(name) {
    const element = schema.element(name);
    const attributes = requiredAttributes(element) + (name === 'option' ? ' quit' : '');
    if (element.content === 'void') {
        return `<${name}${attributes} />`;
    }
    return `<${name}${attributes}>${element.children ? sample(element.children[0]) : 'x'}</${name}>`;
}

const lines = [];
const labels = [];
for (const element of schema.elements) {
    if (element.name === 'partial' || element.name === 'body') {
        continue; // they need files and a layout
    }
    for (const attribute of element.attributes.filter(a => !a.flag)) {
        const values = new Set([...COMMON_VALUES, ...(attribute.values ?? [])]);
        for (const edge of [attribute.minimum, attribute.maximum]) {
            if (edge !== undefined) {
                values.add(String(edge));
                values.add(String(edge + (edge === attribute.minimum ? -1 : 1)));
            }
        }
        for (const value of values) {
            const extra = requiredAttributes(element, attribute) + (element.name === 'option' && attribute.name !== 'nav' ? ' quit' : '');
            const content = element.content === 'void' ? null : element.children ? sample(element.children[0]) : 'x';
            let markup = content === null
                ? `<${element.name} ${attribute.name}="${value}"${extra} />`
                : `<${element.name} ${attribute.name}="${value}"${extra}>${content}</${element.name}>`;
            if (element.parents) {
                markup = `<${element.parents[0]}>${markup}</${element.parents[0]}>`;
            }
            lines.push(markup);
            labels.push(`<${element.name} ${attribute.name}="${value}">`);
        }
    }
}

// The tool stops reporting after many errors, so views are checked 40 lines at a time
const tool_ = new Map();
const ours = new Map();
const add = (map, line, message) => map.set(line, [...(map.get(line) ?? []), message]);
for (let first = 0; first < lines.length; first += 40) {
    const text = lines.slice(first, first + 40).join('\n');
    const result = spawnSync(tool, ['--stdin', '--width', '200'], { input: text });
    if (result.error) {
        console.error(`Cannot run ${tool}: ${result.error.message}`);
        process.exit(2);
    }
    for (const line of result.stderr.toString().split('\n')) {
        const match = /^(\d+):\d+: error (\w+): (.*)/.exec(line);
        if (match) {
            add(tool_, Number(match[1]) + first, `${match[2]} ${match[3]}`);
        }
    }
    for (const problem of validator.validate(text)) {
        add(ours, text.slice(0, problem.start).split('\n').length + first, problem.message);
    }
}

let differences = 0;
for (let line = 1; line <= lines.length; line++) {
    if (tool_.has(line) !== ours.has(line)) {
        differences++;
        const [who, messages] = tool_.has(line) ? ['generator only', tool_.get(line)] : ['extension only', ours.get(line)];
        console.log(`${who.padEnd(15)} ${labels[line - 1]}  ${messages.join(' | ')}`);
    }
}
console.log(`${lines.length} views compared, ${differences} differences`);
process.exitCode = differences > 0 ? 1 : 0;
