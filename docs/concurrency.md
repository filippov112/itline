## Проблема lost update и её решение

> **Воспроизведение**: Тест - `LostUpdateReproductionTest`.

Два потока читают запись (version отсутствует), оба меняют name, оба сохраняют.
Итог: в БД одно из имён, второе изменение потеряно, оба запроса "успешны".

Причина: `UPDATE records SET name = ? WHERE id = ?` не проверяет,
что данные не изменились с момента чтения. Уровень изоляции
READ COMMITTED (Postgres по умолчанию) это допускает.

**Решение**: optimistic locking через @Version.
`UPDATE ... WHERE id = ? AND version = ?` обновит 0 строк,
если кто-то успел раньше -> OptimisticLockingFailureException -> 409.