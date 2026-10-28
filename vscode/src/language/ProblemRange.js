'use strict';

/**
 * Turns the line and column of a reported problem into a range of the text to underline: the tag, attribute or word
 * that starts there, or one character.
 *
 * @example
 * ProblemRange.fromLineColumn('<box>\n  <blink />', 2, 3); // { start: 8, end: 14 }: "<blink"
 */
class ProblemRange {
    /**
     * The range for a line and column, both counted from 1.
     *
     * @param {string} text The view's text.
     * @param {number} line The line.
     * @param {number} column The column.
     * @returns {{start: number, end: number}} Offsets in the text.
     * @example
     * ProblemRange.fromLineColumn('<text bold="x">', 1, 7); // the attribute name "bold"
     */
    static fromLineColumn(text, line, column) {
        const start = ProblemRange.offsetOf(text, line, column);
        const token = /^(?:<\/?[\w-]+|@?[\w.-]+|[^\s])/.exec(text.slice(start, start + 256));
        return { start, end: start + (token ? token[0].length : 0) };
    }

    /**
     * The offset of a line and column, clamped to the text.
     *
     * @param {string} text The text.
     * @param {number} line The line, from 1.
     * @param {number} column The column, from 1.
     * @returns {number} The offset.
     * @example
     * ProblemRange.offsetOf('a\nbc', 2, 2); // 3
     */
    static offsetOf(text, line, column) {
        let offset = 0;
        for (let current = 1; current < line; current++) {
            const next = text.indexOf('\n', offset);
            if (next < 0) {
                return text.length;
            }
            offset = next + 1;
        }
        const lineEnd = text.indexOf('\n', offset);
        return Math.min(offset + Math.max(0, column - 1), lineEnd < 0 ? text.length : lineEnd);
    }
}

module.exports = { ProblemRange };
