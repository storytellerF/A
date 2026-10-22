const elementCommands = new Set([
  'getElementRect', 'getLocation', 'getSize', 'getAttribute', 'getText',
  'elementDisplayed', 'elementEnabled', 'click', 'findElement', 'findElements',
  'findElementFromElement', 'findElementsFromElement', 'getPageSource',
]);

function isNativeDesktopDriver(driver) {
  const caps = driver?.caps || driver?.opts || {};
  const automation = caps.automationName || caps['appium:automationName'];
  if (driver?.currentContext && driver.currentContext !== 'NATIVE_APP') return false;
  if (typeof driver?.isIEContext === 'function' && driver.isIEContext()) return false;
  return String(caps.platformName).toLowerCase() === 'windows' &&
    String(automation).toLowerCase() === 'desktopdriver';
}

async function withUiaErrors(next, driver, command, args, StaleElementError) {
  try {
    return await next();
  } catch (error) {
    const isDesktop = isNativeDesktopDriver(driver);
    const isElementCommand = elementCommands.has(command) ||
      (command === 'execute' && String(args[0]).startsWith('windows:'));
    // Microsoft UIAutomationCoreApi.h: UIA_E_ELEMENTNOTAVAILABLE, not a subscriber failure.
    // https://learn.microsoft.com/windows/win32/winauto/uiauto-error-codes
    if (isDesktop && isElementCommand && /\b(?:0x80040201|UIA_E_ELEMENTNOTAVAILABLE)\b/i.test(error.message || '')) {
      const stale = new StaleElementError(error.message);
      stale.cause = error;
      throw stale;
    }
    throw error;
  }
}

module.exports = {withUiaErrors, isNativeDesktopDriver};
