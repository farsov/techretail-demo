# SPEC.md — Catálogo de productos (REQ-056; HU-123)

## 1. Contratos de API

```yaml
openapi: 3.0.3
info:
  title: Catálogo de productos — exportación a Excel
  version: 1.0.0
paths:
  /api/productos/exportaciones/excel:
    get:
      summary: Exportar a Excel los productos del catálogo según los filtros autorizados
      parameters:
        - name: codigo
          in: query
          schema:
            type: string
            maxLength: 64
        - name: nombre_comercial
          in: query
          schema:
            type: string
            maxLength: 255
        - name: categoria
          in: query
          schema:
            type: string
            maxLength: 120
        - name: subcategoria
          in: query
          schema:
            type: string
            maxLength: 120
        - name: estado
          in: query
          schema:
            type: string
            maxLength: 20
      responses:
        '200':
          description: Archivo Excel con los productos autorizados que coinciden con los filtros; sin filtros, incluye todos los productos autorizados.
          headers:
            Content-Disposition:
              description: Indica la descarga del archivo Excel.
              schema:
                type: string
                example: attachment; filename="catalogo-productos.xlsx"
          content:
            application/vnd.openxmlformats-officedocument.spreadsheetml.sheet:
              schema:
                $ref: '#/components/schemas/ExcelFile'
        '400':
          description: Algún filtro tiene un valor inválido.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '401':
          description: No se ha autenticado al solicitante.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '403':
          description: El solicitante no tiene permiso para exportar.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
components:
  schemas:
    ExcelFile:
      type: string
      format: binary
      description: Archivo .xlsx con encabezados y una fila por producto. Las columnas son id, codigo, nombre_comercial, descripcion, categoria, subcategoria, precio_base, moneda, stock, imagen y estado.
    ProblemDetail:
      type: object
      required:
        - type
        - title
        - status
      properties:
        type:
          type: string
          format: uri-reference
        title:
          type: string
        status:
          type: integer
        detail:
          type: string
        instance:
          type: string
          format: uri-reference
```

## 2. Casos límite

- **CL-1:** Sin filtros, la exportación incluye todos los productos que el solicitante está autorizado a consultar.
- **CL-2:** Si no hay productos que exportar, se entrega un Excel válido con encabezados y sin filas de productos.
- **CL-3:** El precio base y el stock de cada fila coinciden con los valores registrados para ese producto, sin redondeos adicionales ni sustituciones.
- **CL-4:** Un filtro que exceda la longitud de su campo o tenga un valor inválido se rechaza con `400` y `ProblemDetail`.
- **CL-5:** Un filtro por estado no permite eludir las restricciones de acceso a productos.
- **CL-6:** Una solicitud sin autenticación recibe `401` y una solicitud sin permiso de exportación recibe `403`, ambas con `ProblemDetail`.
- **CL-7:** Si cambian productos durante la exportación, cada fila refleja un conjunto coherente de datos del producto, sin mezclar valores anteriores y nuevos.
- **CL-8:** Los valores de texto que puedan interpretarse como fórmulas se escriben como datos, no como fórmulas ejecutables de Excel.

## 3. Restricciones

- **Seguridad:** La API comprueba el permiso de exportación en el 100 % de las solicitudes. El Gestor de productos solo puede exportar datos que esté autorizado a consultar; Angular no sustituye esa comprobación.
- **Validación y contenido:** Los filtros respetan las longitudes de los campos de `productos`. El archivo es `.xlsx`, conserva `precio_base` y `stock` registrados e incluye las columnas indicadas en `ExcelFile`; no incorpora `historial_cambios_producto`.
- **Rendimiento:** El objetivo arquitectónico de que el 95 % de las consultas filtradas responda en menos de 500 ms desde la API excluye la transferencia de archivos exportados. La exportación no debe cargar innecesariamente todo el catálogo en memoria.
- **Disponibilidad:** La API y la base de datos tienen un objetivo de disponibilidad de al menos el 99,9 % por mes calendario.
- **Errores:** Las respuestas de error documentadas usan `application/problem+json` y `ProblemDetail`. La ausencia de resultados no es un error `404`.
- **Alcance:** Este contrato cubre únicamente la exportación a Excel de HU-123. No define altas, modificaciones, desactivaciones, consultas independientes ni exportación a CSV, aunque formen parte del proceso general del catálogo.

## 4. Cobertura de criterios

- HU-123/C1 → GET /api/productos/exportaciones/excel, CL-1, CL-2
- HU-123/C2 → GET /api/productos/exportaciones/excel, CL-3