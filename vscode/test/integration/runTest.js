'use strict';

/**
 * Runs the integration tests inside a real VS Code (downloaded by @vscode/test-electron): the extension is loaded from
 * this folder, a small ConsoleMVC project is opened, and test/integration/suite.js drives the editor.
 * The preview uses the real tool when CVW_PREVIEW names it, otherwise a fake one (Linux and macOS).
 * On Linux without a display, run it under xvfb-run.
 *
 * @example
 * CVW_PREVIEW=~/.dotnet/tools/cvw-preview xvfb-run -a npm run test:integration
 */

const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { runTests } = require('@vscode/test-electron');

async function main() {
    const root = path.join(__dirname, '..', '..');
    const scratch = fs.mkdtempSync(path.join(os.tmpdir(), 'cvw-vscode-'));

    // A project with a layout, a partial view and a view
    const project = path.join(scratch, 'Shop');
    const write = (relative, text) => {
        const file = path.join(project, relative);
        fs.mkdirSync(path.dirname(file), { recursive: true });
        fs.writeFileSync(file, text);
    };
    write('Views/Shared/_Layout.cvw', '<text>Shop header</text>\n<body />\n');
    write('Views/Shared/Banner.cvw', '<text>Banner partial</text>\n');
    write('Views/Home/IndexView.cvw', '@model Shop.Models.HomeViewModel\n\n<box title="@Model.Title" border="double">\n    <text>Welcome</text>\n</box>\n<partial name="Banner" />\n');

    // The tool: the real one, or a script that runs the fake
    let tool = process.env.CVW_PREVIEW;
    if (!tool) {
        tool = path.join(scratch, 'cvw-preview');
        const fake = path.join(root, 'test', 'fakes', 'fake-preview.js');
        fs.writeFileSync(tool, `#!/bin/sh\nexec "${process.execPath}" "${fake}" "$@"\n`, { mode: 0o755 });
    }

    // User settings: machine-scoped settings such as the tool's path can only be set here
    const userData = path.join(scratch, 'user-data');
    fs.mkdirSync(path.join(userData, 'User'), { recursive: true });
    fs.writeFileSync(path.join(userData, 'User', 'settings.json'), JSON.stringify({
        'consolemvc.preview.path': tool,
        'consolemvc.preview.delay': 50,
        'security.workspace.trust.enabled': false
    }));

    await runTests({
        extensionDevelopmentPath: root,
        extensionTestsPath: path.join(__dirname, 'suite.js'),
        extensionTestsEnv: { CVW_REAL_TOOL: process.env.CVW_PREVIEW ? '1' : '' },
        launchArgs: [project, '--user-data-dir', userData, '--disable-extensions', '--disable-workspace-trust', '--skip-welcome', '--skip-release-notes']
    });
}

main().catch(error => {
    console.error(error);
    process.exit(1);
});
