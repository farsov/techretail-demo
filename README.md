# Aplicación

Monorepo generado por la fábrica de software.

- `backend/`: Spring Boot 4.1 (Java 21), Maven. `mvn test` ejecuta las pruebas; `mvn package` compila.
- `frontend/`: Angular 22. `npm test` ejecuta las pruebas; `npm run build` compila.
- `docs/`: SPEC.md de cada módulo y notas de arquitectura.

Cada módulo de negocio vive en su rama `feat/<modulo>` y llega a `main` por Pull Request.
