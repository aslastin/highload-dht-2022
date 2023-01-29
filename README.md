# 2022-highload-dht
Курсовой проект 2022 года [курса "Проектирование высоконагруженных систем"](https://polis.vk.company/curriculum/program/discipline/1444/) [VK Образования](https://polis.vk.company/).

## Этап 1. HTTP + storage (deadline 2022-09-28 23:59:59 MSK)
### Fork
[Форкните проект](https://help.github.com/articles/fork-a-repo/), склонируйте и добавьте `upstream`:
```
$ git clone git@github.com:<username>/2022-highload-dht.git
Cloning into '2022-highload-dht'...
...
$ git remote add upstream git@github.com:polis-vk/2022-highload-dht.git
$ git fetch upstream
From github.com:polis-vk/2022-highload-dht
 * [new branch]      master     -> upstream/master
```

### Make
Так можно запустить тесты:
```
$ ./gradlew test
```

А вот так -- сервер:
```
$ ./gradlew run
```

### Develop
Откройте в IDE -- [IntelliJ IDEA Community Edition](https://www.jetbrains.com/idea/) нам будет достаточно.

**ВНИМАНИЕ!** При запуске тестов или сервера в IDE необходимо передавать Java опцию `-Xmx128m`.

В своём Java package `ok.dht.test.<username>` реализуйте интерфейсы [`Service`](src/main/java/ok/dht/Service.java) и [`ServiceFactory.Factory`](src/main/java/ok/dht/test/ServiceFactory.java) и поддержите следующий HTTP REST API протокол:
* HTTP `GET /v0/entity?id=<ID>` -- получить данные по ключу `<ID>`. Возвращает `200 OK` и данные или `404 Not Found`.
* HTTP `PUT /v0/entity?id=<ID>` -- создать/перезаписать (upsert) данные по ключу `<ID>`. Возвращает `201 Created`.
* HTTP `DELETE /v0/entity?id=<ID>` -- удалить данные по ключу `<ID>`. Возвращает `202 Accepted`.

Используем свою реализацию `DAO` из весеннего курса `2022-nosql-lsm`, либо берём референсную реализацию, если курс БД не был завершён.

Проведите нагрузочное тестирование с помощью [wrk2](https://github.com/giltene/wrk2) в **одно соединение**:
* `PUT` запросами на **стабильной** нагрузке (`wrk2` должен обеспечивать заданный с помощью `-R` rate запросов)
* `GET` запросами на **стабильной** нагрузке по **наполненной** БД

Почему не `curl`/F5, можно узнать [здесь](http://highscalability.com/blog/2015/10/5/your-load-generator-is-probably-lying-to-you-take-the-red-pi.html) и [здесь](https://www.youtube.com/watch?v=lJ8ydIuPFeU).

Приложите полученный консольный вывод `wrk2` для обоих видов нагрузки.

Отпрофилируйте приложение (CPU и alloc) под `PUT` и `GET` нагрузкой с помощью [async-profiler](https://github.com/Artyomcool/async-profiler).
Приложите SVG-файлы FlameGraph `cpu`/`alloc` для `PUT`/`GET` нагрузки.

**Объясните** результаты нагрузочного тестирования и профилирования и приложите **текстовый отчёт** (в Markdown).

Продолжайте запускать тесты и исправлять ошибки, не забывая [подтягивать новые тесты и фиксы из `upstream`](https://help.github.com/articles/syncing-a-fork/).
Если заметите ошибку в `upstream`, заводите баг и присылайте pull request ;)

### Report
Когда всё будет готово, присылайте pull request со своей реализацией и оптимизациями на review.
Не забывайте **отвечать на комментарии в PR** (в том числе автоматизированные) и **исправлять замечания**!

## Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-1/src/main/java/ok/dht/test/slastin)
- [Report](https://github.com/aslastin/2022-highload-dht/blob/stage-1/src/main/java/ok/dht/test/slastin/reports/hw1/report.md)

## Этап 2. Асинхронный сервер (deadline 2022-10-05 23:59:59 MSK)

Вынесите **обработку** запросов в отдельный `ExecutorService` с ограниченной очередью, чтобы разгрузить `SelectorThread`ы HTTP сервера.

Проведите нагрузочное тестирование с помощью [wrk2](https://github.com/giltene/wrk2) с **большим количеством соединений** (не меньше 64) `PUT` и `GET` запросами.

Отпрофилируйте приложение (CPU, alloc и lock) под `PUT` и `GET` нагрузкой с помощью [async-profiler](https://github.com/Artyomcool/async-profiler).

### Report
Когда всё будет готово, присылайте pull request с изменениями, результатами нагрузочного тестирования и профилирования, а также анализом результатов **по сравнению с предыдущей** (синхронной) версией.

### Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-2/src/main/java/ok/dht/test/slastin)
- [Report](https://github.com/aslastin/2022-highload-dht/blob/stage-2/src/main/java/ok/dht/test/slastin/reports/stage2/report.md)

## Этап 3. Шардирование (bonus deadline 2022-10-12 23:59:59 MSK, hard deadline 2022-10-19 23:59:59 MSK)

Реализуем горизонтальное масштабирование через поддержку **кластерных конфигураций**, состоящих из нескольких узлов, взаимодействующих друг с другом через реализованный HTTP API.
Для этого в `ServiceConfig` передаётся статическая "топология", представленная в виде множества координат **всех** узлов кластера в формате `http://<host>:<port>`.

Кластер распределяет ключи между узлами **детерминированным образом**.
В кластере хранится **только одна** копия данных.
Нода, получившая запрос, **проксирует** его на узел, отвечающий за обслуживание соответствующего ключа.
Таким образом, общая ёмкость кластера равна суммарной ёмкости входящих в него узлов.

Реализуйте один из алгоритмов распределения данных между узлами, например, [consistent hashing](https://en.wikipedia.org/wiki/Consistent_hashing), [rendezvous hashing](https://en.wikipedia.org/wiki/Rendezvous_hashing) или что-то другое по согласованию с преподавателем.

### Report
Присылайте pull request со своей реализацией поддержки кластерной конфигурации на review.
Не забудьте нагрузить, отпрофилировать и проанализировать результаты профилирования под нагрузкой.
С учётом шардирования набор тестов расширяется, поэтому не забывайте **подмёрдживать upstream**.

### Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-3/src/main/java/ok/dht/test/slastin)
- [Report](https://github.com/aslastin/2022-highload-dht/blob/stage-3/src/main/java/ok/dht/test/slastin/reports/stage3/report.md)

## Этап 4. Репликация (deadline 2022-10-26 23:59:59 MSK)

Реализуем поддержку хранения [нескольких реплик](https://en.wikipedia.org/wiki/Replication_(computing)) данных в кластере для обеспечения отказоустойчивости.

HTTP API расширяется query-параметрами `from` и `ack`, содержащими количество узлов, которые должны подтвердить операцию, чтобы она считалась выполненной успешно.
* `ack` -- сколько ответов нужно получить
* `from` -- от какого количества узлов

Таким образом, теперь узлы должны поддерживать расширенный протокол (совместимый с предыдущей версией):
* HTTP `GET /v0/entity?id=<ID>[&ack=<ACK>&from=<FROM>]` -- получить данные по ключу `<ID>`. Возвращает:
    * `200 OK` и данные, если ответили хотя бы `ack` из `from` реплик
    * `404 Not Found`, если ни одна из `ack` реплик, вернувших ответ, не содержит данные (либо **самая свежая версия** среди `ack` ответов -- это tombstone)
    * `504 Not Enough Replicas`, если не получили `200`/`404` от `ack` реплик из всего множества `from` реплик

* HTTP `PUT /v0/entity?id=<ID>[&ack=<ACK>&from=<FROM>]` -- создать/перезаписать (upsert) данные по ключу `<ID>`. Возвращает:
    * `201 Created`, если хотя бы `ack` из `from` реплик подтвердили операцию
    * `504 Not Enough Replicas`, если не набралось `ack` подтверждений из всего множества `from` реплик

* HTTP `DELETE /v0/entity?id=<ID>[&ack=<ACK>&from=<FROM>]` -- удалить данные по ключу `<ID>`. Возвращает:
    * `202 Accepted`, если хотя бы `ack` из `from` реплик подтвердили операцию
    * `504 Not Enough Replicas`, если не набралось `ack` подтверждений из всего множества `from` реплик

Если параметр `replicas` не указан, то в качестве `ack` используется значение по умолчанию, равное **кворуму** от количества узлов в кластере, а `from` равен общему количеству узлов в кластере, например:
* `1/1` для кластера из одного узла
* `2/2` для кластера из двух узлов
* `2/3` для кластера из трёх узлов
* `3/4` для кластера из четырёх узлов
* `3/5` для кластера из пяти узлов

Выбор узлов-реплик (множества `from`) для каждого `<ID>` является **детерминированным**:
* Множество узлов-реплик для фиксированного ID и меньшего значения `from` является строгим подмножеством для большего значения `from`
* При `PUT` не сохраняется больше копий данных, чем указано в `from` (т.е. не стоит писать лишние копии данных на все реплики)

Фактически, с помощью параметра `replicas` клиент выбирает, сколько копий данных он хочет хранить, а также
уровень консистентности при выполнении последовательности операций для одного ID.

Таким образом, обеспечиваются следующие примеры инвариантов (список не исчерпывающий):
* `GET` с `1/2` всегда вернёт данные, сохранённые с помощью `PUT` с `2/2` (даже при недоступности одной реплики при `GET`)
* `GET` с `2/3` всегда вернёт данные, сохранённые с помощью `PUT` с `2/3` (даже при недоступности одной реплики при `GET`)
* `GET` с `1/2` "увидит" результат `DELETE` с `2/2` (даже при недоступности одной реплики при `GET`)
* `GET` с `2/3` "увидит" результат `DELETE` с `2/3` (даже при недоступности одной реплики при `GET`)
* `GET` с `1/2` может не "увидеть" результат `PUT` с `1/2`
* `GET` с `1/3` может не "увидеть" результат `PUT` с `2/3`
* `GET` с `1/2` может вернуть данные несмотря на предшествующий `DELETE` с `1/2`
* `GET` с `1/3` может вернуть данные несмотря на предшествующий `DELETE` с `2/3`
* `GET` с `ack` равным `quorum(from)` "увидит" результат `PUT`/`DELETE` с `ack` равным `quorum(from)` даже при недоступности **<** `quorum(from)` реплик

### Report
Присылайте pull request со своей реализацией поддержки кластерной конфигурации на review.
Не забудьте нагрузить, отпрофилировать и проанализировать результаты профилирования под нагрузкой.
С учётом репликации набор тестов расширяется, поэтому не забывайте **подмёрдживать upstream**.

### Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-4/src/main/java/ok/dht/test/slastin)
- [Report](https://github.com/aslastin/2022-highload-dht/blob/stage-4/src/main/java/ok/dht/test/slastin/reports/stage4/report.md)

## Этап 5. Асинхронное взаимодействие (bonus deadline 2022-11-02 23:59:59 MSK, hard deadline 2022-11-09 23:59:59 MSK)

Переключаем внутреннее взаимодействие узлов на асинхронный `java.net.http.HttpClient`.
**Параллельно** отправляем запросы репликам и собираем **самые быстрые** ответы на `CompletableFuture`.

Проведите нагрузочное тестирование с помощью [wrk2](https://github.com/giltene/wrk2) в несколько соединений.

Отпрофилируйте приложение (CPU, alloc и **lock**) под нагрузкой и сравните результаты latency и профилирования по сравнению с неасинхронной версией.

Присылайте pull request со своей реализацией на review.

### Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-5/src/main/java/ok/dht/test/slastin)
- [Report](https://github.com/aslastin/2022-highload-dht/blob/stage-5/src/main/java/ok/dht/test/slastin/reports/stage5/report.md)

## Этап 6. Range-запросы (deadline 2022-11-16 23:59:59 MSK)

Реализуйте получение **диапазона данных текущего узла** с помощью HTTP `GET /v0/entities?start=<ID>[&end=<ID>]`, который возвращает:
* Статус код `200 OK`
* Возможно пустой **отсортированный** (по ключу) набор **ключей** и **значений** в диапазоне ключей от **обязательного** `start` (включая) до **опционального** `end` (не включая)
* Используйте [Chunked transfer encoding](https://en.wikipedia.org/wiki/Chunked_transfer_encoding)
* Чанки в формате `<key>\n<value>`

Диапазон должен отдаваться в **потоковом режиме** без формирования всего ответа в памяти.

### Report
После прохождения модульных тестов, присылайте pull request с изменениями.

### Solution

- [Implementation](https://github.com/aslastin/2022-highload-dht/tree/stage-6/src/main/java/ok/dht/test/slastin)

## Этап 7. Бонусный (deadline 2022-11-30 23:59:59 MSK)

Фичи, которые позволяют получить дополнительные баллы (при условии **добавления набора тестов**, демонстрирующих корректность, где применимо):
* Развёрнутая конструктивная обратная связь по курсу: достоинства и недостатки курса, сложность тем, предложения по улучшению
* Кластерные range-запросы с учётом шардирования и репликации
* Read repair при обнаружении расхождений между репликами
* Expire: возможность указания [времени жизни записей](https://en.wikipedia.org/wiki/Time_to_live)
* Server-side processing: трансформация данных с помощью скрипта, запускаемого на узлах кластера через API
* Нагрузочное тестирование при помощи [Y!CSB](https://github.com/brianfrankcooper/YCSB)
* Нагрузочное тестирование при помощи [Yandex.Tank](https://overload.yandex.net)
* Регулярный автоматический фоновый compaction (модульные и нагрузочные тесты)
* Hinted handoff [по аналогии с Cassandra](https://cassandra.apache.org/doc/latest/operating/hints.html)
* Устранение неконсистентностей между репликами [по аналогии с Cassandra](https://www.datastax.com/blog/advanced-repair-techniques) [nodetool repair](https://docs.datastax.com/en/archived/cassandra/2.0/cassandra/operations/ops_repair_nodes_c.html), например, на основе [Merkle Tree](https://en.wikipedia.org/wiki/Merkle_tree)
* Тесты для consistent/rendezvous hashing, демонстрирующие равномерность распределения данных
* Блочная компрессия данных на основе LZ4/zSTD/...
* Что-нибудь **своё**?

Перед началом работ продумайте и согласуйте с преподавателем её технический дизайн и получите вспомогательные материалы.
