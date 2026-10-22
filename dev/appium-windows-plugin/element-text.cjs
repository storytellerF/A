const {isNativeDesktopDriver} = require('./uia-errors.cjs');
const isTrue = value => value === true || String(value).toLowerCase() === 'true';

async function elementText(next, driver, elementId) {
  if (!isNativeDesktopDriver(driver)) return next();
  if (await driver.getProperty('ControlType', elementId) !== 'Edit') return next();
  if (isTrue(await driver.getProperty('IsPassword', elementId))) return next();
  if (!isTrue(await driver.getProperty('IsValuePatternAvailable', elementId))) return next();
  return driver.execute('windows: getValue', [{elementId}]);
}

module.exports = {elementText};
