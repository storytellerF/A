'use strict';

const { BaseDriver } = require('@appium/base-driver');
const { spawn, execSync, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

const HELPER = path.join(__dirname, 'atspi_helper.py');
const ATSPI_TIMEOUT = 20;

class LinuxDriver extends BaseDriver {
  constructor(opts = {}) {
    super(opts);
    this.appProcess = null;
    this.appPid = null;
    this.locatorStrategies = ['xpath', 'name', 'class name'];
  }

  // ── Session ──────────────────────────────────────────────────────────────

  async createSession(w3cCaps1, w3cCaps2, w3cCaps3) {
    const [sessionId, caps] = await super.createSession(w3cCaps1, w3cCaps2, w3cCaps3);
    const app = caps.app || this.opts.app;
    if (!app) throw new Error('Capability "app" (path to executable) is required');

    const appArgs = caps.appArguments || this.opts.appArguments || [];
    const args = Array.isArray(appArgs) ? appArgs : appArgs.split(' ').filter(Boolean);

    this.log.info(`Launching app: ${app} ${args.join(' ')}`);
    this.appProcess = spawn(app, args, {
      detached: false,
      env: { ...process.env, ...(caps.appEnv || {}) },
    });
    this.appPid = this.appProcess.pid;
    this.log.info(`App started with PID ${this.appPid}`);

    this.appProcess.stdout?.on('data', (data) => {
      this.log.info(`[app stdout] ${data.toString().trimEnd()}`);
    });
    this.appProcess.stderr?.on('data', (data) => {
      this.log.warn(`[app stderr] ${data.toString().trimEnd()}`);
    });

    this.appProcess.on('exit', (code) => {
      this.log.info(`App process exited with code ${code}`);
    });

    // Wait until the app window appears in AT-SPI2 tree
    const result = await this._atspi({ action: 'wait_for_app', pid: this.appPid, timeout: ATSPI_TIMEOUT });
    if (!result.ok) {
      throw new Error(`App window did not appear in AT-SPI2 tree within ${ATSPI_TIMEOUT}s`);
    }
    this.log.info(`App window found in AT-SPI2: ${result.name}`);
    return [sessionId, caps];
  }

  async deleteSession() {
    if (this.appProcess) {
      try { this.appProcess.kill('SIGTERM'); } catch (_) {}
      this.appProcess = null;
      this.appPid = null;
    }
    await super.deleteSession();
  }

  // ── Element finding ───────────────────────────────────────────────────────

  async findElOrEls(strategy, selector, mult) {
    this.log.info(`Finding element(s) by ${strategy}: "${selector}"`);
    const atspiStrategy = this._mapStrategy(strategy, selector);
    const result = await this._atspi({
      action: 'find_elements',
      pid: this.appPid,
      strategy: atspiStrategy.strategy,
      selector: atspiStrategy.selector,
      timeout: ATSPI_TIMEOUT,
    });
    if (!result.ok) throw new Error(`findElOrEls failed: ${result.error}`);
    if (result.elements.length === 0) {
      if (mult) return [];
      const source = await this._atspi({ action: 'page_source', pid: this.appPid });
      if (source.ok) {
        this.log.warn(`No element found for ${strategy}="${selector}". Page source:\n${this._treeToXml(source.tree, 0)}`);
      }
      throw new Error(`No element found for ${strategy}="${selector}"`);
    }
    const els = result.elements.map((e) => {
      const encoded = `${e.id}:${e.cx}:${e.cy}:${e.name}`;
      return {
        ELEMENT: encoded,
        'element-6066-11e4-a52e-4f735466cecf': encoded,
      };
    });
    return mult ? els : els[0];
  }

  _mapStrategy(strategy, selector) {
    if (strategy === 'xpath') {
      return { strategy: 'xpath', selector };
    }
    if (strategy === 'name') {
      return { strategy: 'name', selector };
    }
    if (strategy === 'class name') {
      return { strategy: 'name', selector };
    }
    return { strategy: 'name', selector };
  }

  _parseElement(elId) {
    const decoded = decodeURIComponent(elId.replace(/\+/g, '%20'));
    const [, cx, cy, name] = decoded.split(':');
    return { cx: parseInt(cx, 10), cy: parseInt(cy, 10), name };
  }

  // ── Element interaction ───────────────────────────────────────────────────

  async click(elementId) {
    const { cx, cy, name } = this._parseElement(elementId);
    if (name) {
      const result = await this._atspi({
        action: 'click_element',
        pid: this.appPid,
        strategy: 'name',
        selector: name,
        timeout: ATSPI_TIMEOUT,
      });
      if (result.ok) {
        this.log.info(`Clicked element "${name}" via AT-SPI action "${result.action}"`);
        return;
      }
      this.log.warn(`AT-SPI click failed for "${name}": ${result.error || 'unknown error'}`);
    }
    this.log.info(`Clicking at (${cx}, ${cy})`);
    this._xdotool('mousemove', '--sync', cx, cy);
    this._xdotool('click', 1);
  }

  async setValue(value, elementId) {
    const { cx, cy, name } = this._parseElement(elementId);
    const text = Array.isArray(value) ? value.join('') : String(value);
    const result = await this._atspi({
      action: 'set_text',
      pid: this.appPid,
      strategy: name ? 'name' : 'xpath',
      selector: name || '//text-field',
      text,
      timeout: ATSPI_TIMEOUT,
    });
    if (result.ok) {
      this.log.info(`Set text via AT-SPI editable text${name ? ` for "${name}"` : ''}`);
      return;
    }
    this.log.warn(`AT-SPI set text failed: ${result.error || 'unknown error'}`);
    this._xdotool('mousemove', '--sync', cx, cy);
    this._xdotool('click', 1);
    await this._sleep(100);
    // Clear existing content then type
    this._xdotool('key', '--clearmodifiers', 'ctrl+a');
    this._xdotool('type', '--clearmodifiers', '--delay', '20', '--', text);
  }

  async getText(elementId) {
    const { name } = this._parseElement(elementId);
    return name;
  }

  async getAttribute(attr, elementId) {
    const { name } = this._parseElement(elementId);
    return name;
  }

  async getLocation(elementId) {
    const { cx, cy } = this._parseElement(elementId);
    return { x: cx, y: cy };
  }

  async elementDisplayed(elementId) {
    return true;
  }

  // ── Navigation ────────────────────────────────────────────────────────────

  async back() {
    this._xdotool('key', 'alt+Left');
  }

  // ── Page source ───────────────────────────────────────────────────────────

  async getPageSource() {
    const result = await this._atspi({ action: 'page_source', pid: this.appPid });
    if (!result.ok) return '<error/>';
    return this._treeToXml(result.tree, 0);
  }

  _treeToXml(node, depth) {
    if (!node) return '';
    const indent = '  '.repeat(depth);
    const role = (node.role || 'unknown').replace(/[^a-zA-Z0-9_-]/g, '_');
    const name = (node.name || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    const value = (node.value || '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
    const boundsAttr = node.bounds
      ? ` x="${node.bounds.x}" y="${node.bounds.y}" width="${node.bounds.w}" height="${node.bounds.h}"`
      : '';
    const children = (node.children || []).map((c) => this._treeToXml(c, depth + 1)).join('\n');
    if (children) {
      return `${indent}<${role} name="${name}" value="${value}"${boundsAttr}>\n${children}\n${indent}</${role}>`;
    }
    return `${indent}<${role} name="${name}" value="${value}"${boundsAttr}/>`;
  }

  // ── Status ────────────────────────────────────────────────────────────────

  async getStatus() {
    return { ready: true, message: 'Linux driver ready' };
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  _xdotool(...args) {
    const strArgs = args.map(String);
    this.log.debug(`xdotool ${strArgs.join(' ')}`);
    const result = spawnSync('xdotool', strArgs, { encoding: 'utf8' });
    if (result.status !== 0) {
      this.log.warn(`xdotool exited ${result.status}: ${result.stderr}`);
    }
    return result.stdout;
  }

  async _atspi(cmd) {
    return new Promise((resolve, reject) => {
      const proc = spawn('python3', [HELPER], { stdio: ['pipe', 'pipe', 'pipe'] });
      let stdout = '';
      let stderr = '';
      proc.stdout.on('data', (d) => { stdout += d; });
      proc.stderr.on('data', (d) => { stderr += d; });
      proc.on('close', (code) => {
        if (stderr) this.log.debug(`atspi_helper stderr: ${stderr.trim()}`);
        try {
          resolve(JSON.parse(stdout));
        } catch (e) {
          reject(new Error(`atspi_helper parse error (code ${code}): ${stdout || stderr}`));
        }
      });
      proc.stdin.write(JSON.stringify(cmd));
      proc.stdin.end();
    });
  }

  _sleep(ms) {
    return new Promise((r) => setTimeout(r, ms));
  }
}

module.exports = { LinuxDriver };
