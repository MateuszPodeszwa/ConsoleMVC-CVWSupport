'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');
const { SchemaValidator, hasExpression } = require('../src/language/SchemaValidator');
const { schema, fixture } = require('./helpers');

const validator = new SchemaValidator(schema);
const messages = text => validator.validate(text).map(p => p.message);
const only = text => {
    const problems = validator.validate(text);
    assert.equal(problems.length, 1, JSON.stringify(problems));
    return problems[0];
};

test('the template\'s views have no problems', () => {
    for (const name of ['FeaturesView.cvw', 'StatusView.cvw', 'GreetView.cvw', '_Layout.cvw']) {
        assert.deepEqual(messages(fixture(name)), [], name);
    }
});

test('an unknown element inside an element is CVW105, on its tag', () => {
    const text = '<box><blink /></box>';
    const problem = only(text);
    assert.equal(problem.code, 'CVW105');
    assert.equal(text.slice(problem.start, problem.end), '<blink');
});

test('an unknown attribute is CVW100 and lists the valid ones', () => {
    const problem = only('<text size="2">x</text>');
    assert.equal(problem.code, 'CVW100');
    assert.match(problem.message, /Valid attributes: class, color/);
});

test('values are checked against their kind', () => {
    assert.match(only('<box border="dotted"><text>x</text></box>').message, /'dotted' is not a valid border/);
    assert.match(only('<box padding="21"><text>x</text></box>').message, /not a valid padding/);
    assert.match(only('<text color="puce">x</text>').message, /not a valid color/);
    assert.match(only('<box width="101%"><text>x</text></box>').message, /not a valid width/);
    assert.match(only('<date name="d" min="tomorrow" />').message, /not a valid min/);
    assert.match(only('<option key="1" nav="Home/Index/Extra/More">x</option>'.replace(/^/, '<menu>') + '</menu>').message, /not a valid nav/);
});

test('valid literal values pass', () => {
    const view = [
        '<box border="Double" padding="20" width="100%" color="#f80" title-colour="dark-grey"><text>x</text></box>',
        '<text color="" wrap="TRUE">x</text>',
        '<date name="d" min="today-30" max="2026-02-28" />',
        '<sparkline values="1, 2.5 -3" />',
        '<menu><option key="1" nav="Orders/Details?id=42&amp;tab=x">x</option></menu>'
    ].join('\n');
    assert.deepEqual(messages(view), []);
});

test('an impossible date is refused', () => {
    assert.match(only('<date name="d" default="2026-02-30" />').message, /not a valid default/);
});

test('values with expressions are left to the build', () => {
    assert.deepEqual(messages('<box padding="@Model.Padding" border="@Model.Border"><text>x</text></box>'), []);
    assert.ok(hasExpression('a @(b) c'));
    assert.ok(!hasExpression('me@example.com'));
    assert.ok(!hasExpression('@@literal'));
});

test('missing required attributes are reported once per attribute', () => {
    assert.match(only('<partial />').message, /needs the attribute 'name'/);
});

test('an option needs exactly one of nav, quit and back (CVW109)', () => {
    assert.equal(only('<menu><option key="1">x</option></menu>').code, 'CVW109');
    assert.equal(only('<menu><option key="1" quit back>x</option></menu>').code, 'CVW109');
});

test('elements in the wrong place are reported', () => {
    assert.match(only('<row><cell>x</cell></row>').message, /<row> must be inside <table>/);
    assert.match(only('<box><menu><option key="q" quit>x</option></menu></box>').message, /must be at the top level/);
    assert.match(only('<table><text>x</text></table>').message, /cannot be inside <table>/);
});

test('text directly inside an element that holds only elements is CVW110', () => {
    const problem = only('<table>oops<row><cell>x</cell></row></table>');
    assert.equal(problem.code, 'CVW110');
});

test('unclosed elements and unfinished tags are reported', () => {
    assert.match(only('<box><text>x</text>').message, /<box> is not closed/);
    assert.match(messages('<text bold')[0], /not finished/);
});

test('an attribute written twice, or through its alias, is reported', () => {
    assert.match(only('<text color="red" colour="blue">x</text>').message, /set twice/);
});

test('a missing closing quote is reported', () => {
    assert.match(messages('<text color="red>x</text>\n<br />').join(' '), /no closing quote/);
});
