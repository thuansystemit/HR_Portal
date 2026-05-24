# Kibana Setup Guide — Backend Log Monitoring

## Prerequisites

- Docker and Docker Compose installed
- ELK stack containers defined in `demo-app-backend/docker-compose.yml`
- Backend running with `SPRING_PROFILES_ACTIVE=prod` to enable the Logstash TCP appender

---

## Part 1 — Enable xpack Security (Prevent Guest Access)

By default the stack ships with `xpack.security.enabled=false`. Any browser that reaches port 5601 can read all logs without a password. The steps below turn security on and create a dedicated read-only user.

### 1.1 Update `docker-compose.yml`

Three services need changes: `elasticsearch`, `logstash`, and `kibana`.

**`elasticsearch` — enable security and update the healthcheck:**

```yaml
# Before
  elasticsearch:
    environment:
      - xpack.security.enabled=false
      - xpack.security.enrollment.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - cluster.name=demo-app-cluster
      - ELASTIC_PASSWORD=Change_Me_Now!
    healthcheck:
      test: ["CMD-SHELL", "curl -s http://localhost:9200/_cluster/health | grep -q ..."]

# After
  elasticsearch:
    environment:
      - xpack.security.enabled=true           # <-- changed
      - xpack.security.enrollment.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
      - cluster.name=demo-app-cluster
      - ELASTIC_PASSWORD=Change_Me_Now!       # built-in superuser password
    healthcheck:
      test: ["CMD-SHELL", "curl -s -u elastic:Change_Me_Now! http://localhost:9200/_cluster/health | grep -q '\"status\":\"green\"\\|\"status\":\"yellow\"'"]
      # ^^^ auth added — without this the healthcheck fails when security is on
```

**`logstash` — add Elasticsearch credentials as env vars:**

```yaml
# Before
  logstash:
    environment:
      - LS_JAVA_OPTS=-Xms256m -Xmx256m
      - xpack.monitoring.enabled=false

# After
  logstash:
    environment:
      - LS_JAVA_OPTS=-Xms256m -Xmx256m
      - xpack.monitoring.enabled=false
      - ELASTICSEARCH_USER=elastic            # <-- added
      - ELASTICSEARCH_PASSWORD=Change_Me_Now! # <-- added
```

**`kibana` — enable security and set the correct service account username:**

```yaml
# Before
  kibana:
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=false
      - ELASTICSEARCH_USERNAME=kibana_system  # may have been wrong (e.g. "darkness")
      - ELASTICSEARCH_PASSWORD=KibanaSystem1!
      - xpack.encryptedSavedObjects.encryptionKey=a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6

# After
  kibana:
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
      - xpack.security.enabled=true           # <-- changed
      - ELASTICSEARCH_USERNAME=kibana_system  # must be exactly "kibana_system"
      - ELASTICSEARCH_PASSWORD=KibanaSystem1! # must match step 1.3 below
      - xpack.encryptedSavedObjects.encryptionKey=a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6
      # ^^^ 32+ chars — silences the "Missing encryption key" warning in Kibana logs
```

> **Pick strong passwords.** Replace `Change_Me_Now!` and `KibanaSystem1!` with values from a password manager before deploying to any shared environment.

### 1.2 Update `logstash/pipeline/logstash.conf`

When security is enabled, Logstash must authenticate before writing to Elasticsearch. Add `user` and `password` to the output block:

```conf
# Before
output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    index => "demo-app-%{+YYYY.MM.dd}"
    manage_template => true
    template_name => "demo-app"
    template_overwrite => true
  }
}

# After
output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    user     => "${ELASTICSEARCH_USER:elastic}"   # <-- added; reads from env var
    password => "${ELASTICSEARCH_PASSWORD}"        # <-- added; reads from env var
    index => "demo-app-%{+YYYY.MM.dd}"
    manage_template => true
    template_name => "demo-app"
    template_overwrite => true
  }
}
```

### 1.3 Recreate the Elasticsearch container only

Recreate Elasticsearch first — Kibana and Logstash cannot start until we set the `kibana_system` password (step 1.4).

```bash
cd demo-app-backend
docker compose up -d --force-recreate elasticsearch

# Poll until healthy (~15 s)
until docker compose ps elasticsearch | grep -q "healthy"; do sleep 2; done
docker compose ps elasticsearch   # confirms "healthy"
```

### 1.4 Set the `kibana_system` built-in user password

`kibana_system` is the internal service account Kibana uses to talk to Elasticsearch — it is **not** for human login. Set its password to match `ELASTICSEARCH_PASSWORD` in `docker-compose.yml`.

```bash
curl -s -X POST \
  -u elastic:Change_Me_Now! \
  -H "Content-Type: application/json" \
  http://localhost:9200/_security/user/kibana_system/_password \
  -d '{"password": "KibanaSystem1!"}'
```

Expected response: `{}`

### 1.5 Recreate Kibana and Logstash

Both containers depend on the security-enabled Elasticsearch and must be recreated together.

```bash
docker compose up -d --force-recreate kibana logstash

# Poll until Kibana is healthy (~30 s)
until docker compose ps kibana | grep -q "healthy"; do sleep 3; done
docker compose ps kibana logstash   # both should be "healthy"
```

---

## Part 2 — Create a Dedicated Log-Viewer Account

Never share the `elastic` superuser for day-to-day log browsing. Create a least-privilege account.

### 2.1 Create the `log-viewer` user

```bash
curl -s -X POST \
  -u elastic:Change_Me_Now! \
  -H "Content-Type: application/json" \
  http://localhost:9200/_security/user/log-viewer \
  -d '{
    "password" : "LogViewer2026!",
    "roles"    : ["viewer", "monitoring_user"],
    "full_name": "Log Viewer",
    "email"    : "log-viewer@internal"
  }'
```

Expected response: `{"created":true}`

The `viewer` role (built-in since Elasticsearch 8.x) allows read-only access to all Kibana apps and all indices. The `monitoring_user` role grants access to index stats and cluster health panels.

> **Note:** `kibana_viewer` does not exist in Elasticsearch 8.x. The correct read-only role is `viewer`.

### 2.2 (Optional) Restrict to specific indices

If you want to lock the account down to only the `demo-app-*` indices, create a custom role first:

```bash
curl -s -X PUT \
  -u elastic:Change_Me_Now! \
  -H "Content-Type: application/json" \
  http://localhost:9200/_security/role/demo-app-log-reader \
  -d '{
    "indices": [
      {
        "names"      : ["demo-app-*"],
        "privileges" : ["read", "view_index_metadata"]
      }
    ]
  }'
```

Then reference that role when creating the user:

```bash
curl -s -X POST \
  -u elastic:Change_Me_Now! \
  -H "Content-Type: application/json" \
  http://localhost:9200/_security/user/log-viewer \
  -d '{
    "password" : "LogViewer2026!",
    "roles"    : ["viewer", "demo-app-log-reader"],
    "full_name": "Log Viewer"
  }'
```

---

## Part 3 — Create the Data View in Kibana

### 3.1 Log in

Open http://localhost:5601 in a browser. Log in with `log-viewer / LogViewer2026!`.

### 3.2 Navigate to Data Views

1. Click the hamburger menu (top-left).
2. Go to **Stack Management → Kibana → Data Views**.
3. Click **Create data view**.

### 3.3 Fill in the form

| Field | Value |
|---|---|
| Name | `demo-app-logs` |
| Index pattern | `demo-app-*` |
| Timestamp field | `@timestamp` |

Click **Save data view to Kibana**.

> **Daily index pattern:** Logstash creates one index per day (`demo-app-2026.05.24`, `demo-app-2026.05.23`, …). The wildcard `demo-app-*` covers all days in a single view.

---

## Part 4 — Browse Logs in Discover

1. Open **Discover** from the hamburger menu.
2. Select the `demo-app-logs` data view from the dropdown (top-left).
3. Set the time range (top-right) — e.g., **Last 24 hours**.

### 4.1 Available Log Fields

| Field | Type | Description |
|---|---|---|
| `@timestamp` | date | Log event time |
| `level` | keyword | `INFO`, `WARN`, `ERROR`, `DEBUG` |
| `logger_name` | keyword | Java class that emitted the log |
| `message` | text | Log message text |
| `requestId` | keyword | Per-request UUID (MDC) |
| `traceId` | keyword | Distributed trace ID (MDC) |
| `userId` | keyword | Authenticated user UUID (MDC) |
| `method` | keyword | HTTP method: `GET`, `POST`, etc. |
| `path` | keyword | Request path: `/api/v1/...` |
| `statusCode` | integer | HTTP response status |
| `durationMs` | long | Request duration in milliseconds |
| `clientIp` | keyword | Caller IP address |

Click **Add field** next to any field name in the left panel to add it as a table column.

### 4.2 KQL Filter Examples

Paste these into the search bar at the top of Discover.

```kql
# All errors
level: "ERROR"

# All requests that took longer than 500 ms
durationMs > 500

# Requests to the knowledge ingest endpoint
path: "/api/v1/knowledge/ingest"

# 4xx and 5xx responses
statusCode >= 400

# Trace all events for a single request
requestId: "3fa85f64-5717-4562-b3fc-2c963f66afa6"

# Errors from a specific class
logger_name: "com.demo.app.knowledge.service.KnowledgeService" AND level: "ERROR"

# All document extraction activity
path: "/api/v1/cv-candidates" OR path: "/api/v1/knowledge/ingest"
```

---

## Part 5 — (Optional) Save a Dashboard

1. From **Discover**, click **Save** (top-right) and name the search (e.g., `Backend Errors Last 24h`).
2. Open **Dashboards → Create dashboard**.
3. Click **Add from library**, select your saved search.
4. Add visualizations: click **Create visualization**, choose the `demo-app-logs` data view.
   - Bar chart of `level` over time — useful for error rate monitoring.
   - Top 10 slowest paths: Metric aggregation on `durationMs` grouped by `path`.
5. Click **Save** — name it `Backend Overview`.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `no handler found for uri /_security/...` | `xpack.security.enabled=false` still active | Apply step 1.1 and recreate the Elasticsearch container |
| Elasticsearch healthcheck fails after enabling security | Healthcheck curl has no auth | Add `-u elastic:<password>` to the healthcheck `test` command (step 1.1) |
| Kibana shows "Unable to connect to Elasticsearch" | Wrong `ELASTICSEARCH_PASSWORD` or username not `kibana_system` | Re-run step 1.4, verify `ELASTICSEARCH_USERNAME=kibana_system`, recreate kibana |
| Logstash stops indexing after enabling security | `user`/`password` missing from `logstash.conf` output block | Apply step 1.2 and recreate the Logstash container |
| `security_exception: action … is unauthorized` | User lacks required role | Add missing role via `_security/user/<name>` PUT |
| No documents in Data View | Logstash not running or backend not in `prod` profile | `docker compose up -d logstash`; check `SPRING_PROFILES_ACTIVE=prod` |
| `kibana_system` credentials rejected at browser login | `kibana_system` is not a human login account | Use the `log-viewer` account for browser login |
| Encryption key warning in Kibana logs | `xpack.encryptedSavedObjects.encryptionKey` not set | Add a 32+ char key to the kibana env block (step 1.1) |

---

## Quick Reference — Useful curl Commands

```bash
# Check Elasticsearch health (with auth)
curl -u elastic:Change_Me_Now! "http://localhost:9200/_cluster/health?pretty"

# List indices
curl -u elastic:Change_Me_Now! "http://localhost:9200/_cat/indices/demo-app-*?v"

# List all users
curl -u elastic:Change_Me_Now! "http://localhost:9200/_security/user?pretty"

# Delete a user
curl -s -X DELETE -u elastic:Change_Me_Now! http://localhost:9200/_security/user/log-viewer

# Reset a user's password
curl -s -X POST \
  -u elastic:Change_Me_Now! \
  -H "Content-Type: application/json" \
  http://localhost:9200/_security/user/log-viewer/_password \
  -d '{"password": "NewPassword2026!"}'
```
