import { expect, test, type Page } from '@playwright/test';

const ruta = '/api/productos/exportaciones/excel';
const tipoExcel = 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet';
const columnas = [
  'id',
  'codigo',
  'nombre_comercial',
  'descripcion',
  'categoria',
  'subcategoria',
  'precio_base',
  'moneda',
  'stock',
  'imagen',
  'estado',
];
const ns = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';

// Datos de contrato: el SPEC no ofrece un endpoint para crear productos desde Playwright.
function libroCatalogo(conProducto: boolean): Buffer {
  const texto = (columna: string, fila: number, valor: string) =>
    `<c r="${columna}${fila}" t="inlineStr"><is><t>${valor}</t></is></c>`;
  const numero = (columna: string, fila: number, valor: string) =>
    `<c r="${columna}${fila}" t="n"><v>${valor}</v></c>`;
  const encabezado = columnas
    .map((nombre, i) => texto(String.fromCharCode(65 + i), 1, nombre))
    .join('');
  const producto = [
    numero('A', 2, '901'),
    texto('B', 2, 'CAFE-901'),
    texto('C', 2, 'Cafetera'),
    texto('D', 2, 'Producto de cocina'),
    texto('E', 2, 'Cocina'),
    texto('F', 2, 'Café'),
    numero('G', 2, '123.45'),
    texto('H', 2, 'EUR'),
    numero('I', 2, '12345678901'),
    texto('J', 2, 'foto.png'),
    texto('K', 2, 'ACTIVO'),
  ].join('');
  const hoja = `<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="${ns}"><sheetData><row r="1">${encabezado}</row>${conProducto ? `<row r="2">${producto}</row>` : ''}</sheetData></worksheet>`;
  return zipSinCompresion({
    '[Content_Types].xml':
      '<?xml version="1.0"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/></Types>',
    '_rels/.rels':
      '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>',
    'xl/workbook.xml': `<?xml version="1.0"?><workbook xmlns="${ns}" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Productos" sheetId="1" r:id="rId1"/></sheets></workbook>`,
    'xl/_rels/workbook.xml.rels':
      '<?xml version="1.0"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/></Relationships>',
    'xl/worksheets/sheet1.xml': hoja,
  });
}

function crc32(datos: Buffer): number {
  let crc = 0xffffffff;
  for (const byte of datos) {
    crc ^= byte;
    for (let i = 0; i < 8; i++) crc = (crc >>> 1) ^ (crc & 1 ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function zipSinCompresion(archivos: Record<string, string>): Buffer {
  const locales: Buffer[] = [];
  const centrales: Buffer[] = [];
  let desplazamiento = 0;
  for (const [nombre, contenido] of Object.entries(archivos)) {
    const nombreBinario = Buffer.from(nombre);
    const datos = Buffer.from(contenido);
    const crc = crc32(datos);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(datos.length, 18);
    local.writeUInt32LE(datos.length, 22);
    local.writeUInt16LE(nombreBinario.length, 26);
    locales.push(local, nombreBinario, datos);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(datos.length, 20);
    central.writeUInt32LE(datos.length, 24);
    central.writeUInt16LE(nombreBinario.length, 28);
    central.writeUInt32LE(desplazamiento, 42);
    centrales.push(central, nombreBinario);
    desplazamiento += local.length + nombreBinario.length + datos.length;
  }
  const directorio = Buffer.concat(centrales);
  const fin = Buffer.alloc(22);
  fin.writeUInt32LE(0x06054b50, 0);
  fin.writeUInt16LE(Object.keys(archivos).length, 8);
  fin.writeUInt16LE(Object.keys(archivos).length, 10);
  fin.writeUInt32LE(directorio.length, 12);
  fin.writeUInt32LE(desplazamiento, 16);
  return Buffer.concat([...locales, directorio, fin]);
}

// Lee el XLSX de prueba (ZIP sin comprimir) para comprobar las celdas descargadas por el navegador.
function xmlHoja(libro: Buffer): string {
  let posicion = 0;
  while (posicion + 30 <= libro.length && libro.readUInt32LE(posicion) === 0x04034b50) {
    const longitudNombre = libro.readUInt16LE(posicion + 26);
    const longitudExtra = libro.readUInt16LE(posicion + 28);
    const tamano = libro.readUInt32LE(posicion + 18);
    const nombre = libro.toString('utf8', posicion + 30, posicion + 30 + longitudNombre);
    const inicio = posicion + 30 + longitudNombre + longitudExtra;
    if (nombre === 'xl/worksheets/sheet1.xml')
      return libro.toString('utf8', inicio, inicio + tamano);
    posicion = inicio + tamano;
  }
  throw new Error('Falta la hoja de productos en el XLSX descargado');
}

async function contenidoDescargado(page: Page): Promise<{ nombre: string; hoja: string }> {
  const evento = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const descarga = await evento;
  const flujo = await descarga.createReadStream();
  expect(flujo).not.toBeNull();
  const fragmentos: Buffer[] = [];
  for await (const fragmento of flujo!) fragmentos.push(Buffer.from(fragmento));
  const libro = Buffer.concat(fragmentos);
  expect(libro.subarray(0, 4).toString('hex')).toBe('504b0304');
  return { nombre: descarga.suggestedFilename(), hoja: xmlHoja(libro) };
}

async function simularSesion(page: Page): Promise<void> {
  await page.route('**/api/dev/token*', (interceptada) =>
    interceptada.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ token: 'token-de-prueba', expira_en: 3600 }),
    }),
  );
}

async function simularExcel(
  page: Page,
  conProducto: boolean,
  comprobar: (url: URL, token: string | undefined) => void,
): Promise<void> {
  await simularSesion(page);
  await page.route('**' + ruta + '*', (interceptada) => {
    expect(interceptada.request().method()).toBe('GET');
    comprobar(
      new URL(interceptada.request().url()),
      interceptada.request().headers()['authorization'],
    );
    return interceptada.fulfill({
      status: 200,
      headers: {
        'content-type': tipoExcel,
        'content-disposition': 'attachment; filename="catalogo-productos.xlsx"',
      },
      body: libroCatalogo(conProducto),
    });
  });
  await page.goto('/catalogo-de-productos');
}

test('HU-123/C1: dado el catálogo sin filtros, al exportar se descarga un Excel con productos', async ({
  page,
}) => {
  let solicitudes = 0;
  await simularExcel(page, true, (url, token) => {
    solicitudes++;
    expect(url.pathname).toBe(ruta);
    expect(url.search).toBe('');
    expect(token).toBe('Bearer token-de-prueba');
  });
  const descarga = await contenidoDescargado(page);
  expect(descarga.nombre).toBe('catalogo-productos.xlsx');
  expect(descarga.hoja.match(/<row r="\d+">/g)).toHaveLength(2);
  for (const columna of columnas) expect(descarga.hoja).toContain(`<t>${columna}</t>`);
  expect(descarga.hoja).toContain('<c r="B2" t="inlineStr"><is><t>CAFE-901</t>');
  expect(solicitudes).toBe(1);
  await expect(page.getByRole('status')).toContainText('Se ha iniciado la descarga');
});

test('HU-123/C2: dados productos con precio y stock, el Excel filtrado conserva los valores registrados', async ({
  page,
}) => {
  const filtros = {
    codigo: 'CAFE-901',
    nombre_comercial: 'Cafetera',
    categoria: 'Cocina',
    subcategoria: 'Café',
    estado: 'ACTIVO',
  };
  let solicitudes = 0;
  await simularExcel(page, true, (url, token) => {
    solicitudes++;
    expect(url.pathname).toBe(ruta);
    expect(Object.fromEntries(url.searchParams)).toEqual(filtros);
    expect(token).toBe('Bearer token-de-prueba');
  });
  for (const [etiqueta, valor] of [
    ['Código', filtros.codigo],
    ['Nombre comercial', filtros.nombre_comercial],
    ['Categoría', filtros.categoria],
    ['Subcategoría', filtros.subcategoria],
    ['Estado', filtros.estado],
  ])
    await page.getByLabel(etiqueta, { exact: true }).fill(valor);
  const descarga = await contenidoDescargado(page);
  expect(descarga.nombre).toBe('catalogo-productos.xlsx');
  expect(descarga.hoja.match(/<row r="\d+">/g)).toHaveLength(2);
  expect(descarga.hoja).toContain('<c r="A2" t="n"><v>901</v></c>');
  expect(descarga.hoja).toContain('<c r="B2" t="inlineStr"><is><t>CAFE-901</t>');
  expect(descarga.hoja).toContain('<c r="G2" t="n"><v>123.45</v></c>');
  expect(descarga.hoja).toContain('<c r="I2" t="n"><v>12345678901</v></c>');
  expect(solicitudes).toBe(1);
});

test('HU-123/CL-2: dado un filtro sin coincidencias, se descarga un Excel válido solo con encabezados', async ({
  page,
}) => {
  let solicitudes = 0;
  await simularExcel(page, false, (url, token) => {
    solicitudes++;
    expect(url.searchParams.get('codigo')).toBe('SIN-RESULTADOS');
    expect([...url.searchParams.keys()]).toEqual(['codigo']);
    expect(token).toBe('Bearer token-de-prueba');
  });
  await page.getByLabel('Código').fill('SIN-RESULTADOS');
  const descarga = await contenidoDescargado(page);
  expect(descarga.nombre).toBe('catalogo-productos.xlsx');
  expect(descarga.hoja.match(/<row r="\d+">/g)).toHaveLength(1);
  for (const columna of columnas) expect(descarga.hoja).toContain(`<t>${columna}</t>`);
  expect(descarga.hoja).not.toContain('r="2"');
  expect(solicitudes).toBe(1);
  await expect(page.getByRole('status')).toContainText('Se ha iniciado la descarga');
});

test('HU-123/CL-6: sin autenticación la API real rechaza la exportación con ProblemDetail 401', async ({
  request,
}) => {
  const respuesta = await request.get(ruta);
  expect(respuesta.status()).toBe(401);
  expect(respuesta.headers()['content-type']).toContain('application/problem+json');
  const problema = await respuesta.json();
  expect(problema).toEqual(
    expect.objectContaining({ type: expect.any(String), title: expect.any(String), status: 401 }),
  );
});

test('HU-123/CL-6: un usuario sin permiso no puede exportar ni filtrando por estado', async ({
  request,
}) => {
  const sesion = await request.get('/api/dev/token?usuario=lector-e2e&roles=LECTOR');
  expect(sesion.ok()).toBeTruthy();
  const { token } = await sesion.json();
  expect(typeof token).toBe('string');
  const respuesta = await request.get(ruta, {
    params: { estado: 'ACTIVO' },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(respuesta.status()).toBe(403);
  expect(respuesta.headers()['content-type']).toContain('application/problem+json');
  const problema = await respuesta.json();
  expect(problema).toEqual(
    expect.objectContaining({ type: expect.any(String), title: expect.any(String), status: 403 }),
  );
  expect(problema.detail).toContain('permiso');
});

test('HU-123/CL-6: si la API deniega el permiso, la pantalla informa y no descarga', async ({
  page,
}) => {
  await simularSesion(page);
  let solicitudes = 0;
  await page.route('**' + ruta + '*', (interceptada) => {
    solicitudes++;
    expect(interceptada.request().headers()['authorization']).toBe('Bearer token-de-prueba');
    return interceptada.fulfill({
      status: 403,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        type: 'urn:problema:catalogo:acceso-denegado',
        title: 'Acceso denegado',
        status: 403,
        detail: 'No tienes permiso para exportar el catálogo de productos.',
      }),
    });
  });
  await page.goto('/catalogo-de-productos');
  const descargas: string[] = [];
  page.on('download', (descarga) => descargas.push(descarga.suggestedFilename()));
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  await expect(page.getByRole('alert')).toContainText('No tienes permiso para exportar');
  expect(solicitudes).toBe(1);
  expect(descargas).toEqual([]);
  await expect(page.getByRole('status')).toHaveCount(0);
});
