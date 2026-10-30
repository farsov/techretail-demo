import { expect, test, type Page } from '@playwright/test';

const ruta = '/api/productos/exportaciones/excel';
const ns = 'http://schemas.openxmlformats.org/spreadsheetml/2006/main';
const cabeceras = [
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

// Libro XLSX de prueba con valores conocidos, sin depender de un endpoint de alta fuera del SPEC.
function libroCatalogo(): Buffer {
  const texto = (columna: string, fila: number, valor: string) =>
    `<c r="${columna}${fila}" t="inlineStr"><is><t>${valor}</t></is></c>`;
  const numero = (columna: string, fila: number, valor: string) =>
    `<c r="${columna}${fila}" t="n"><v>${valor}</v></c>`;
  const encabezado = cabeceras
    .map((nombre, indice) => texto(String.fromCharCode(65 + indice), 1, nombre))
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
  const hoja = `<?xml version="1.0" encoding="UTF-8"?><worksheet xmlns="${ns}"><sheetData><row r="1">${encabezado}</row><row r="2">${producto}</row></sheetData></worksheet>`;
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
    const rutaArchivo = Buffer.from(nombre);
    const datos = Buffer.from(contenido);
    const crc = crc32(datos);
    const local = Buffer.alloc(30);
    local.writeUInt32LE(0x04034b50, 0);
    local.writeUInt16LE(20, 4);
    local.writeUInt32LE(crc, 14);
    local.writeUInt32LE(datos.length, 18);
    local.writeUInt32LE(datos.length, 22);
    local.writeUInt16LE(rutaArchivo.length, 26);
    locales.push(local, rutaArchivo, datos);
    const central = Buffer.alloc(46);
    central.writeUInt32LE(0x02014b50, 0);
    central.writeUInt16LE(20, 4);
    central.writeUInt16LE(20, 6);
    central.writeUInt32LE(crc, 16);
    central.writeUInt32LE(datos.length, 20);
    central.writeUInt32LE(datos.length, 24);
    central.writeUInt16LE(rutaArchivo.length, 28);
    central.writeUInt32LE(desplazamiento, 42);
    centrales.push(central, rutaArchivo);
    desplazamiento += local.length + rutaArchivo.length + datos.length;
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

function xmlHoja(libro: Buffer): string {
  let posicion = 0;
  while (libro.readUInt32LE(posicion) === 0x04034b50) {
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

async function prepararDescarga(
  page: Page,
  comprobar: (url: URL, autorizacion: string | undefined) => void,
) {
  await page.route('**/api/dev/token*', (solicitud) =>
    solicitud.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ token: 'token-de-prueba', expira_en: 3600 }),
    }),
  );
  await page.route('**' + ruta + '*', (solicitud) => {
    comprobar(new URL(solicitud.request().url()), solicitud.request().headers()['authorization']);
    return solicitud.fulfill({
      status: 200,
      headers: {
        'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'content-disposition': 'attachment; filename="catalogo-productos.xlsx"',
      },
      body: libroCatalogo(),
    });
  });
  await page.goto('/catalogo-de-productos');
}

test('HU-123/C1: sin filtros se descarga el Excel con encabezados y productos del catálogo', async ({
  page,
}) => {
  let peticiones = 0;
  await prepararDescarga(page, (url, autorizacion) => {
    peticiones++;
    expect(url.search).toBe('');
    expect(autorizacion).toBe('Bearer token-de-prueba');
  });
  const descargaEsperada = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const descarga = await descargaEsperada;
  expect(descarga.suggestedFilename()).toBe('catalogo-productos.xlsx');
  const flujo = await descarga.createReadStream();
  expect(flujo).not.toBeNull();
  const fragmentos: Buffer[] = [];
  for await (const fragmento of flujo!) fragmentos.push(Buffer.from(fragmento));
  const libro = Buffer.concat(fragmentos);
  expect(libro.subarray(0, 2).toString()).toBe('PK');
  const hoja = xmlHoja(libro);
  expect(hoja.match(/<row r="\d+">/g)).toHaveLength(2);
  for (const columna of cabeceras) expect(hoja).toContain(`<t>${columna}</t>`);
  expect(hoja).toContain('<t>CAFE-901</t>');
  expect(peticiones).toBe(1);
  await expect(page.getByRole('status')).toContainText('Se ha iniciado la descarga');
});

test('HU-123/C2: el Excel descargado conserva el precio base y el stock registrados', async ({
  page,
}) => {
  let peticiones = 0;
  await prepararDescarga(page, (url, autorizacion) => {
    peticiones++;
    expect(url.searchParams.get('codigo')).toBe('CAFE-901');
    expect(url.searchParams.size).toBe(1);
    expect(autorizacion).toBe('Bearer token-de-prueba');
  });
  await page.getByLabel('Código').fill('CAFE-901');
  const descargaEsperada = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Descargar Excel' }).click();
  const descarga = await descargaEsperada;
  const flujo = await descarga.createReadStream();
  expect(flujo).not.toBeNull();
  const fragmentos: Buffer[] = [];
  for await (const fragmento of flujo!) fragmentos.push(Buffer.from(fragmento));
  const hoja = xmlHoja(Buffer.concat(fragmentos));
  expect(hoja).toContain('<c r="A2" t="n"><v>901</v></c>');
  expect(hoja).toContain('<c r="B2" t="inlineStr"><is><t>CAFE-901</t>');
  expect(hoja).toContain('<c r="G2" t="n"><v>123.45</v></c>');
  expect(hoja).toContain('<c r="I2" t="n"><v>12345678901</v></c>');
  expect(peticiones).toBe(1);
});
