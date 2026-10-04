# Настройка и обслуживание

## PostgreSQL

Основной сценарий описан в [README](../README.md). `local-db.ps1` использует PostgreSQL 17 и порт 55432. Иной каталог `bin` передайте через `-PgBin`. Если порт занят, используйте отдельные базы существующего PostgreSQL, не останавливая чужой сервер:

```powershell
# psql/createdb должны быть в PATH; укажите свой административный логин.
createdb -h 127.0.0.1 -p 5432 -U postgres -W gamestore
createdb -h 127.0.0.1 -p 5432 -U postgres -W gamestore_test
$env:GAMESTORE_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/gamestore'
$env:GAMESTORE_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5432/gamestore_test'
$credential = Get-Credential -Message 'Пользователь PostgreSQL с правами на отдельные базы проекта'
$env:GAMESTORE_DB_USER = $credential.UserName
$env:GAMESTORE_DB_PASSWORD = $credential.GetNetworkCredential().Password
.\mvnw.cmd -B verify
java -cp .\target\game-store-1.0.0.jar gamestore.server.ServerMain
```

Выбранному пользователю нужны права создания таблиц в рабочей базе и схем в тестовой. Названия должны быть свободны или принадлежать проекту. Не используйте общие production-базы. Скрипт локального кластера сам создаёт подходящего пользователя.

Реквизиты задаются в окне сервера/сборки; клиенту доступ к БД не нужен. `GAMESTORE_HOST` / `GAMESTORE_PORT` по умолчанию — `127.0.0.1` / `1234`; при изменении задайте одинаковые значения в обоих окнах. `.env.example` — справочник, автоматического чтения `.env` нет.

## Миграции и покупка

Flyway применяет `src/main/resources/db/migration/` при старте. Отдельный запуск:

```powershell
java -cp .\target\game-store-1.0.0.jar gamestore.server.ServerMain migrate
```

V1–V5 создают магазин, каталог, профиль, ограничения ника и обязательный email для новых аккаунтов. Старые данные сохраняются. Перед будущими изменениями существующей базы сделайте резервную копию средствами PostgreSQL.

Сервер использует BigDecimal/NUMERIC и блокировку строк. Заказ со снимками цен, списание, результат оплаты, библиотека и очистка корзины сохраняются одной транзакцией. Ошибка откатывает изменения. Недостаточный баланс сохраняет отказ без списания. UUID защищает повторный запрос; после отказа новая покупка требует нового UUID. Пополнения также идемпотентны.

При обрыве связи клиент позволяет повторить UUID в текущем сеансе. После полного закрытия клиента проверьте историю и библиотеку: незавершённый запрос локально между запусками не сохраняется.

## Старые заказы

`orders.txt` — необязательный локальный файл, в чистом клоне его нет. [Пример формата](legacy-orders.example.txt) предназначен для изучения. Формат не содержит логина, цены и даты; автоматическое сопоставление небезопасно.

Сначала зарегистрируйте пользователя и явно сопоставьте его логин со старым ID. Предпросмотр:

```powershell
. .\scripts\use-local-db.ps1
java -cp .\target\game-store-1.0.0.jar gamestore.server.ServerMain import-orders .\orders.txt 3 your_login
```

Применение после проверки:

```powershell
java -cp .\target\game-store-1.0.0.jar gamestore.server.ServerMain import-orders .\orders.txt 3 your_login --apply --ack-reconstructed
```

Импорт помечается `IMPORTED` / `LEGACY` и использует текущую цену и дату с указанием реконструкции. Баланс не списывается. Повтор того же файла/ID не дублирует заказы; неверная строка или неизвестная игра откатывает импорт. Сохраните оригинальный файл.

## Исходники

Maven собирает только `src/main/java` / `src/test/java`, ресурсы — `src/main/resources` и `images`. Необязательная резервная копия `legacy/` исключена из Git, сборки и Java-диагностики VS Code. Приложение от неё не зависит.

`.local-db/`, `.tools/`, `target/`, `.env` и `orders.txt` исключены из Git. Для общей сети нужны защищённый транспорт и отдельная настройка эксплуатации.
