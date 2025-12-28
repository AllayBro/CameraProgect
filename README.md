# CameraProgect — сквозной сценарий (analytics → catalog → penalty)

## Сервисы и порты

Проект состоит из 3 сервисов:

1) photo-analytics-service
- REST: http://localhost:8081
- SOAP (CXF): http://localhost:8081/ws

2) catalog-service
- REST: http://localhost:8082
- SOAP (CXF): http://localhost:8082/ws

3) penalty-service
- REST: http://localhost:8083
- SOAP (CXF): http://localhost:8083/ws

H2 Console (если включена в application.yml каждого сервиса):
- analytics-service: http://localhost:8081/h2-console
- catalog-service: http://localhost:8082/h2-console
- penalty-service: http://localhost:8083/h2-console

Параметры подключения H2 (по умолчанию в конфиге):
- JDBC URL: `jdbc:h2:mem:<dbName>;DB_CLOSE_DELAY=-1`
- User: `sa`
- Password: пусто

## Важные ссылки (эндпойнты)

Analytics (REST):
- POST http://localhost:8081/api/analytics/sessions?droneId=DRONE-001&operatorId=OP-001
- POST http://localhost:8081/api/analytics/sessions/{sessionId}/manifest
- GET  http://localhost:8081/api/analytics/sessions/{sessionId}

Catalog (REST):
- POST http://localhost:8082/api/catalog/import

Penalty (REST):
- POST http://localhost:8083/api/penalty/check

SOAP (WSDL):
- Analytics: http://localhost:8081/ws (WSDL определяется аннотациями CXF; в браузере обычно виден список сервисов и ссылки на `?wsdl`)
- Catalog:   http://localhost:8082/ws
- Penalty:   http://localhost:8083/ws

## Запуск

Требования:
- Java 17+ (или та версия, которая указана в pom)
- Maven
- IntelliJ IDEA (рекомендуется)

Запуск (3 отдельных конфигурации Run в IntelliJ):
1) запустить penalty-service
2) запустить catalog-service
3) запустить photo-analytics-service

Важно: в application.yml должны быть настроены base-url:
- analytics: `app.catalog.base-url=http://localhost:8082`
- catalog:   `app.penalty.base-url=http://localhost:8083`

## Сквозной сценарий (ожидаемый результат)

Суть: загрузка manifest в analytics должна автоматически запустить цепочку:
analytics → catalog → penalty

Ожидаемый результат:
1) Создалась сессия (analytics сохранил CaptureSessionEntity).
2) После загрузки manifest:
    - analytics распарсил XML и отправил CatalogImportRequestDto в catalog.
    - catalog создал/обновил записи CatalogRecordEntity по recordId=sessionId:fileKey и вызвал penalty.
    - penalty вернул PenaltyCheckResponseDto и сохранил Measurement/Violation/Penalty (идемпотентно по recordId).
3) В БД penalty-service появилась запись решения со сформированным `evidenceXml`.
4) Повторная отправка того же manifest не создаёт дублей (recordId — ключ), а записи обновляются/пропускаются согласно статусу.

## Примеры запросов (IntelliJ HTTP Client)

Файлы:
- examples/manifest.xml
- examples/run.http

Открыть:
- file:./examples/run.http
- file:./examples/manifest.xml

Порядок выполнения:
1) В `examples/run.http` выполнить запрос “START SESSION”.
2) Скопировать `sessionId` из ответа и вставить в переменную `@sessionId = ...` в том же файле.
3) Выполнить “UPLOAD MANIFEST”.
4) Выполнить “CHECK SESSION STATE”.
5) (Опционально) выполнить “CALL PENALTY DIRECTLY”.

## Где смотреть результат

1) penalty-service: H2 Console  
   http://localhost:8083/h2-console  
   Там должны появиться таблицы:
- MEASUREMENT
- VIOLATION
- PENALTY

В таблице PENALTY должно быть заполнено:
- decisionStatus
- amount
- evidenceXml

2) catalog-service: H2 Console  
   http://localhost:8082/h2-console  
   Таблица `CATALOG_RECORD` (или имя, заданное в @Table) должна содержать:
- recordId=sessionId:fileKey
- status (PENALTY_DECIDED при успешном прохождении)
- penaltyDecisionStatus / penaltyRuleCode / penaltyAmount / evidenceXml

## Идемпотентность

Идемпотентность обеспечена ключами:
- catalog-service: CatalogRecordEntity.recordId = sessionId:fileKey (PK)
- penalty-service: Measurement/Violation/Penalty имеют recordId как PK

Повторный импорт одного и того же manifest:
- не создаёт новые записи с другим ключом,
- обновляет существующие строки,
- при наличии статуса PENALTY_DECIDED в catalog повторно penalty не вызывается.


