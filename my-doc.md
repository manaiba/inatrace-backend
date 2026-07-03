# MySQL via Docker

```bash
# Create + run (detached, background)
docker run --name inatrace-mysql -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=inatrace \
  -e MYSQL_USER=inatrace -e MYSQL_PASSWORD=inatrace -p 3306:3306 -d mysql:8.4.10

docker stop inatrace-mysql    # stop (keeps container + data)
docker start inatrace-mysql   # start it back up
docker rm inatrace-mysql      # remove (must be stopped first; frees the name)
docker rm -f -v inatrace-mysql  # force-remove running container + its volume
```

Run in foreground (stream logs, Ctrl-C to stop): drop `-d`. Add `--rm` to auto-delete on exit.

# application.properties

Copy the template, then fill the values below:

```bash
cp src/main/resources/application.properties.template src/main/resources/application.properties
```

Minimum settings for local dev (matches the MySQL container above):

| Property | Value |
|---|---|
| `INATrace.database.name` | `inatrace` |
| `spring.datasource.username` | `inatrace` |
| `spring.datasource.password` | `inatrace` |
| `INATrace.auth.jwtSigningKey` | any secret, e.g. `sign` |
| `INATrace.requestLog.token` | any secret, e.g. `token` |
| `INATrace.fileStorage.root` | a writable local path, e.g. `/home/<you>/inatrace_file_storage` |

Left empty / optional: Beyco, AgStack, exchange-rate, MaxMind GeoIP.

## Email — required even when disabled

`INATrace.mail.sendingEnabled = false` turns off *sending*, but the app still
wires a `MailEngine` bean that **requires** a `JavaMailSender`. Spring only
creates that bean when `spring.mail.host` is set, so the app **fails to start**
if the SMTP lines are commented out (`No qualifying bean of type ...JavaMailSenderImpl`).

So even with sending disabled, uncomment and set at least the host:

```properties
spring.mail.protocol = smtp
spring.mail.host = localhost
spring.mail.port = 1025
INATrace.mail.sendingEnabled = false
```

The sender is lazy — nothing connects to `localhost:1025` at boot, so MailHog is
not needed just to start. Run MailHog only if you want to actually test emails:

```bash
docker run --name inatrace-mailhog -p 1025:1025 -p 8025:8025 -d mailhog/mailhog:v1.0.1  # GUI at :8025
```

# Run the backend

```bash
docker start inatrace-mysql   # ensure DB is up
mvn spring-boot:run           # serves on http://localhost:8080 (API docs: /v3/api-docs)
```

Tables are auto-created on first startup (Hibernate `ddl-auto=update` + Flyway).

# Build & run the container image

Build the image (multi-stage: builds the jar with JDK 17, then a JRE runtime image):

```bash
docker build -t inatrace-be:2.39.0-SNAPSHOT .
# or: ./docker-build.sh inatrace-be 2.39.0-SNAPSHOT [push]
```

The image is **config-free** (`.dockerignore` excludes `application.properties`), so it
needs config injected at runtime. To smoke-test it standalone against the local MySQL,
mount the dev config and use host networking so the container reaches MySQL on `localhost:3306`:

```bash
docker run --rm --name inatrace-be-test \
  --network host \
  -v $(pwd)/src/main/resources/application.properties:/config/application.properties:ro \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=file:/config/ \
  -e INATRACE_FILESTORAGE_ROOT=/tmp/inatrace-fs \
  inatrace-be:2.39.0-SNAPSHOT
```

- `--network host` — app finds MySQL at `localhost:3306` and serves on host `:8080` (no `-p` needed).
- mounted file + `SPRING_CONFIG_ADDITIONAL_LOCATION` — supplies runtime config to the config-free image.
- env vars override any value in the mounted file (here, the file-storage path).

Verify from another terminal: `curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/v3/api-docs` (expect `200`).
