# Список тестов

## Конкурентность

- `src\test\java\com\example\itline\concurrency\LostUpdateReproductionTest.java`

| |`lostUpdate_withoutVersioning`|`withVersioning_secondWriterConflicts`|
|---|---|---|
|Цель|Своими руками увидеть, что без защиты одно обновление теряется|Убедиться, что с `@Version` конфликт ловится и данные не теряются|
|Транзакции|Чтение + запись в одной транзакции на поток (`TransactionTemplate`)|То же: чтение + запись в одной транзакции на поток|
|Барьер|`CyclicBarrier(2)` внутри транзакции, между `findById` и `setName`|То же|
|Ожидаемый результат потоков|Оба потока успешно завершаются|Ровно один успешен, второй получает `OptimisticLockingFailureException`|
|Проверка исключений|`Future.get()` для каждого потока — ни один не должен упасть|`Future.get()` для каждого: один OK, один — `ExecutionException` с причиной `OptimisticLockingFailureException`|
|Проверка данных в БД|`plusCount == 1` — одно обновление потеряно (lost update воспроизведён)|`plusCount == 1` — одно обновление применено, второе отклонено (не потеряно)|
|Проверка версии|Не проверяется (поля `version` ещё нет)|`result.getVersion() == 1L` — версия инкрементирована ровно один раз, а не два|
|Что доказывает|«Без `@Version` система теряет данные при гонке»|«С `@Version` система не теряет данные: конфликт обнаружен, второй писатель отклонён»|
 

## Unit-тесты

### Сервис:

- `src\test\java\com\example\itline\unit\RecordServiceTest.java`

| Метод | Тест | Проверяет |
|---|---|---|
| `findAll` | `findAll_returnsRepositoryResult` | делегирование в репозиторий |
| `findAll` | `findAll_empty` | пустой результат |
| `create` | `create_mapsFieldsAndSaves` | маппинг DTO, `save`, возврат |
| `update` | `update_notFound` | 404, `save` не вызван |
| `update` | `update_staleVersion` | 409, `save` не вызван, поля не изменены |
| `update` | `update_success` | поля обновлены, `save` вызван |
| `update` | `update_doesNotTouchVersion` | `version` не трогается сервисом |
| `delete` | `delete_notFound` | 404, `delete` не вызван |
| `delete` | `delete_staleVersion` | 409, `delete` не вызван |
| `delete` | `delete_success` | `delete` вызван с нужной сущностью |

### Контроллер:
- `src\test\java\com\example\itline\unit\RecordControllerTest.java`

| Метод | Тест | Проверяет |
|---|---|---|
| GET | `list_returns200AndArray` | 200, JSON-массив, поля и типы |
| GET | `list_empty` | 200, `[]` |
| POST | `create_returns201` | 201, тело с `id`, `version` |
| POST | `create_blankName_returns400` | 400, `fields.name`, сервис не вызван |
| POST | `create_badIcon_returns400` | 400, `fields.icon` |
| POST | `create_blankDescription_returns400` | 400, `fields.description` |
| POST | `create_missingIcon_returns400` | 400, `fields.icon` |
| POST | `create_malformedJson_returns400` | 400 на битый JSON |
| POST | `create_missingActive_defaultsFalse` | `active=false` по умолчанию |
| PUT | `update_returns200` | 200, поля и `version` |
| PUT | `update_missingVersion_returns400` | 400, `fields.version` |
| PUT | `update_negativeVersion_returns400` | 400, `fields.version` |
| PUT | `update_staleVersion_returns409` | **409**, `error=conflict` |
| PUT | `update_notFound_returns404` | 404, `error=not_found` |
| PUT | `update_optimisticLockFromFlush_returns409` | **409**, не 500 (страж) |
| DELETE | `delete_returns204` | 204, пустое тело |
| DELETE | `delete_missingVersion_returns400` | 400 |
| DELETE | `delete_staleVersion_returns409` | 409 |
| DELETE | `delete_notFound_returns404` | 404 |