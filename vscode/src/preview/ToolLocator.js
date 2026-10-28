'use strict';

const os = require('node:os');
const path = require('node:path');

/**
 * The command name of the ConsoleMVC.Preview tool, and the default of the consolemvc.preview.path setting.
 *
 * @example
 * new PreviewRunner(DEFAULT_COMMAND);
 */
const DEFAULT_COMMAND = 'cvw-preview';

/**
 * Lists where to look for `cvw-preview`: the configured command, and when it is the default, also the folder where
 * `dotnet tool install -g` puts tools (~/.dotnet/tools), which is often missing from the PATH of an editor started
 * from the desktop.
 *
 * @example
 * ToolLocator.candidates('cvw-preview'); // ['cvw-preview', '/home/me/.dotnet/tools/cvw-preview']
 */
class ToolLocator {
    /**
     * The commands to try, in order.
     *
     * @param {string} configured The consolemvc.preview.path setting.
     * @param {{platform?: string, home?: string}} [machine] The platform and home folder; tests pass their own.
     * @returns {string[]} The commands.
     * @example
     * ToolLocator.candidates('C:\\tools\\cvw-preview.exe'); // ['C:\\tools\\cvw-preview.exe']
     */
    static candidates(configured, machine = {}) {
        const command = (configured || '').trim() || DEFAULT_COMMAND;
        if (command !== DEFAULT_COMMAND) {
            return [command];
        }
        const platform = machine.platform ?? process.platform;
        const home = machine.home ?? os.homedir();
        const join = platform === 'win32' ? path.win32.join : path.posix.join;
        const executable = platform === 'win32' ? DEFAULT_COMMAND + '.exe' : DEFAULT_COMMAND;
        return [command, join(home, '.dotnet', 'tools', executable)];
    }
}

module.exports = { ToolLocator, DEFAULT_COMMAND };
