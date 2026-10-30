import { test, expect } from '@playwright/test';

test('la aplicación carga y muestra su título', async ({ page }) => {
  await page.goto('/');
  await expect(page.locator('h1')).toContainText('Aplicación');
});
