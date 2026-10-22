const {BasePlugin} = require('appium/plugin');
const {errors} = require('appium/driver');
const {withUiaErrors} = require('./uia-errors.cjs');
const {elementText} = require('./element-text.cjs');
const {inspectWindows, resizeWindow} = require('./window-ownership.cjs');
const {createNativeWindows} = require('./native-windows.cjs');
const {driverScreenshot} = require('./window-screenshot.cjs');

class DesktopWindowsPlugin extends BasePlugin {
  owners = new WeakMap();
  static executeMethodMap = {
    'desktop: inspectWindows': {command: 'inspect', params: {required: ['processId', 'mainHandle', 'handles']}},
    'desktop: resizeWindow': {command: 'resize', params: {required: ['processId', 'handle', 'width', 'height']}},
  };

  async deleteSession(next, driver) {
    this.owners.delete(driver);
    return next();
  }

  async handle(next, driver, command, ...args) {
    return withUiaErrors(next, driver, command, args, errors.StaleElementReferenceError);
  }

  async execute(next, driver, script, args) {
    return withUiaErrors(() => this.executeMethod(next, driver, script, args),
      driver, 'execute', [script, args], errors.StaleElementReferenceError);
  }

  async getText(next, driver, elementId) {
    return withUiaErrors(() => elementText(next, driver, elementId),
      driver, 'getText', [elementId], errors.StaleElementReferenceError);
  }

  async getScreenshot(next, driver) {
    return driverScreenshot(next, driver, createNativeWindows, this.owners.get(driver));
  }

  async inspect(next, driver, processId, mainHandle, handles) {
    const state = inspectWindows(createNativeWindows(), processId, mainHandle, handles);
    this.owners.set(driver, processId);
    return state;
  }

  async resize(next, driver, processId, handle, width, height) {
    resizeWindow(createNativeWindows(), processId, handle, width, height);
    this.owners.set(driver, processId);
    return null;
  }
}

module.exports = {DesktopWindowsPlugin};
