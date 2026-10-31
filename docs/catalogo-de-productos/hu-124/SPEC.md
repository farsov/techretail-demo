# SPEC.md — Catálogo de productos (REQ-056, HU-124)

## 1. Contratos de API

```yaml
openapi: 3.0.3
info:
  title: Catálogo de productos — exportación CSV (REQ-056, HU-124)
  version: 1.0.0
paths:
  /api/productos/exportaciones/csv:
    get:
      summary: Exportar a CSV los productos del catálogo según los filtros autorizados
      parameters:
        - name: categoria
          in: query
          required: false
          schema:
            type: string
            maxLength: 120
        - name: subcategoria
          in: query
          required: false
          schema:
            type: string
            maxLength: 120
        - name: estado
          in: query
          required: false
          schema:
            type: string
            maxLength: 20
      responses:
        '200':
          description: >
            Archivo CSV en UTF-8. Sin filtros, incluye los productos del catálogo
            accesibles para el usuario. La primera fila contiene, en este orden,
            codigo,nombre_comercial,categoria,subcategoria,precio_base,moneda,stock,estado.
            Cada valor procede del campo homónimo de productos.
          headers:
            Content-Disposition:
              description: Indica la descarga del archivo.
              schema:
                type: string
              example: attachment; filename="productos.csv"
          content:
            text/csv:
              schema:
                type: string
                format: binary
        '400':
          description: Filtro inválido.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '401':
          description: No se ha autenticado el usuario.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
        '403':
          description: El usuario no tiene permiso para exportar o aplicar el filtro solicitado.
          content:
            application/problem+json:
              schema:
                $ref: '#/components/schemas/ProblemDetail'
components:
  schemas:
    ProblemDetail:
      type: object
      properties:
        type:
          type: string
          format: uri
        title:
          type: string
        status:
          type: integer
          format: int32
        detail:
          type: string
        instance:
          type: string
          format: uri
      required:
        - title
        - status
```

## 2. Casos límite

- **CL-1:** Si no hay productos accesibles que coincidan con la consulta, la exportación devuelve un CSV válido con la cabecera y sin filas de datos.
- **CL-2:** Sin filtros, la exportación incluye los productos del catálogo accesibles para el usuario, sin exigir que aplique uno.
- **CL-3:** Un filtro vacío, excesivamente largo o con un estado no permitido se rechaza con `400`; un filtro no autorizado se rechaza con `403`.
- **CL-4:** Una solicitud sin autenticación recibe `401`, y una solicitud sin permiso de exportación recibe `403`, sin entregar el archivo.
- **CL-5:** `precio_base` y `stock` se exportan desde los valores registrados, sin redondeos adicionales ni conversiones de cantidad, incluso en los límites de sus tipos `numeric(15,2)` e `integer`.
- **CL-6:** Si un producto cambia durante la generación del archivo, cada fila refleja una versión coherente de sus datos; no se mezclan valores anteriores y nuevos de un mismo producto.
- **CL-7:** Los valores de texto que contengan comas, comillas o saltos de línea se escapan para conservar la estructura del CSV.

## 3. Restricciones

- **Seguridad:** Spring Boot comprueba en la API la autenticación, el permiso de exportación y la autorización de los filtros antes de entregar datos. Angular no sustituye estas comprobaciones.
- **Datos y formato:** El archivo usa UTF-8 y CSV con separador coma, cabecera y escapado de campos. `precio_base` corresponde a `productos.precio_base` y `stock` a `productos.stock`; Angular descarga el archivo sin recalcular esos valores.
- **Validación y errores:** Los filtros opcionales respetan los límites de las columnas correspondientes. Los errores se devuelven como `ProblemDetail` con `application/problem+json`; una consulta sin resultados no es un error.
- **Rendimiento y disponibilidad:** El objetivo arquitectónico de que el 95 % de las consultas filtradas responda en menos de 500 ms excluye la transferencia de archivos exportados. La API y la base de datos deben alcanzar una disponibilidad mensual de al menos el 99,9 %.
- **Alcance:** Esta especificación cubre únicamente la exportación CSV de HU-124. No define altas, modificaciones, desactivaciones, exportación Excel ni endpoints adicionales, aunque formen parte del proceso general del catálogo.

## 4. Cobertura de criterios

- HU-124/C1 → GET /api/productos/exportaciones/csv, CL-1, CL-2
- HU-124/C2 → GET /api/productos/exportaciones/csv, CL-5, CL-6