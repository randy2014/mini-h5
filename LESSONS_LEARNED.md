# Lessons Learned

This document records practical lessons from building, deploying, and debugging Mini H5.

## Docker Desktop

### Symptom

Docker Desktop showed:

```text
Virtualization support not detected
```

### Cause

The local machine needed virtualization/WSL support to be enabled and restarted.

### Resolution

- Enabled required Windows virtualization/WSL prerequisites.
- Rebooted the machine.
- Rechecked Docker Desktop after reboot.

### Lesson

Before debugging application Docker issues, confirm Docker engine health first. If Docker Desktop cannot start, application-level compose work is blocked.

## VPS SSH

### Symptom

SSH connections repeatedly failed with errors such as:

```text
key_exchange_identification: read: Connection reset by peer
Connection closed by authenticating user
```

### Cause

The server SSH service and startup/socket configuration were unstable under repeated connections. The cloud host also had provider firewall rules and local SSH daemon behavior to verify.

### Resolution

- Confirmed cloud firewall allowed SSH.
- Verified `sshd` was listening.
- Adjusted SSH daemon config.
- Used a dedicated SSH port `2222`.
- Increased startup limits such as `MaxStartups`.
- Restarted SSH cleanly.

### Lesson

Do not work around SSH problems by avoiding them. For CI/CD and operations, SSH must be reliable and observable.

## GitHub Repository and CI/CD

### Symptom

Manual VPS patching/building became tempting when deploy verification was slow.

### Cause

Early production setup was still forming, and direct VPS commands felt faster.

### Resolution

- Established GitHub Actions as the normal deploy path.
- Kept VPS manual operations limited to runtime checks, DB inspection, cleanup, and emergency recovery.
- Used commit SHA in production `.env` for traceability.

### Lesson

The project should not drift into hand-patched production. If code behavior changes, it should come from Git and CI/CD.

## Docker Build on VPS

### Symptom

VPS build failed with Dockerfile metadata/TLS timeout or missing build context errors.

### Cause

- Compose paths and Docker build contexts were initially wrong.
- Docker Hub access could be slow or unstable.
- Some images required better registry/network handling.

### Resolution

- Corrected compose build contexts.
- Kept deploy compose paths stable under `/opt/mini-h5`.
- Re-ran deployment after context fixes.

### Lesson

For multi-project repos, build context paths must be explicit and tested. A compose file that works locally may still fail after rsync if paths differ.

## MySQL Client Compatibility

### Symptom

Navicat failed with:

```text
Authentication plugin 'caching_sha2_password' cannot be loaded
```

### Cause

Older client compatibility issue with MySQL 8 default authentication.

### Resolution

- Ran MySQL container with native password compatibility:

```text
--mysql-native-password=ON
```

- Connected via SSH tunnel because MySQL is bound to `127.0.0.1`.

### Lesson

GUI database tools often fail before the database itself is wrong. Check auth plugin and tunnel settings separately.

## MySQL Disk Growth

### Symptom

VPS disk reached about 90% usage, while application tables appeared much smaller.

### Cause

MySQL binlog grew to tens of GB because crawler writes generated many large binary logs. Raw crawler tables also grew, but binlog was the main disk pressure.

### Resolution

- Confirmed table sizes with `information_schema`.
- Checked `/var/lib/mysql` with `du`.
- Listed binary logs with `SHOW BINARY LOGS`.
- Cleared raw crawler staging tables.
- Purged old binary logs.
- Set binlog retention to 1 day:

```sql
SET PERSIST binlog_expire_logs_seconds = 86400;
```

### Lesson

When MySQL disk usage looks impossible from table size alone, check binlog immediately. Crawler-heavy workloads can create huge logs quickly.

## Crawler Raw Data

### Symptom

Crawler raw tables accumulated many records, including failed or outdated staging data.

### Cause

The crawler design intentionally stages data before merge, but retention was not strict enough during experimentation.

### Resolution

- Decided raw crawler data is temporary.
- Failed raw data does not need long-term retention at this stage.
- Emergency cleanup can truncate raw staging tables after confirming no active tasks.

### Lesson

Staging tables must have a retention policy. Otherwise, successful experimentation can still break the VPS through disk pressure.

## Stuck Crawler Tasks

### Symptom

New scheduled tasks did not start because old tasks remained active.

### Cause

The scheduler avoids starting duplicate active tasks for the same source. A stale `RUNNING` or `MERGING` task blocked new work.

### Resolution

- Checked active task states.
- Marked stale tasks as failed with a clear message.
- Restarted or let scheduler continue after stale state was cleared.

### Lesson

Schedulers need stale-task recovery. A single stuck task should not block all future collection indefinitely.

## Source Research

### Symptom

Several candidate sources looked promising but did not provide a complete, stable public collection chain.

### Cause

Some sources expose IDs/catalogs but not stable正文. Others require login, have encoded content, partial free chapters, or dynamic decoding.

### Resolution

- Stopped broad source chasing.
- Focused on one source with a proven chain: `23qb_public`.
- Required source validation before integration.

### Lesson

Crawler development must be source-chain first, code second. A source is not usable until book metadata, catalog, complete chapter正文, and merge into H5 are all verified.

## Qidian and Shuqi Exploration

### Symptom

Qidian and Shuqi did not meet the immediate public collection goal.

### Cause

- Qidian public web did not provide the desired complete public download/正文 path for the selected goal.
- Shuqi free chapter chain could be partially explored, but it was not the best stable source for immediate H5 data.

### Resolution

- Disabled Qidian/Shuqi schedules.
- Kept `23qb_public` as the active source.

### Lesson

Do not keep unstable experimental sources enabled. They pollute data and make task results harder to reason about.

## Chapter Completeness

### Symptom

Some collected chapter lists or chapter ranges looked wrong.

### Cause

Potential issues include source missing pages, source category/list behavior, pagination, or incomplete chapter extraction.

### Resolution

- Made正文 completeness a core crawler requirement.
- Required pagination support for sources that split one chapter into multiple pages.
- Added duplicate-skip and mapping principles.

### Lesson

Counting chapters is not enough. The content chain must verify readable正文 and sequence quality.

## H5 Reading Experience

### Symptom

The H5 app worked functionally but felt inconsistent: navigation gaps, style mismatch, detail/reader return issues, and reading-history positioning issues.

### Cause

Initial work prioritized getting backend and crawler flow running. UX polish came later.

### Resolution

- Started a global UI consistency pass.
- Added home navigation to detail/reader flows.
- Kept reading history and scroll restoration as high-priority items.

### Lesson

For a reading product, user experience is not secondary. Once content flows, UI consistency and reading continuity become core product quality.

## Admin Design

### Symptom

Direct SQL and manual operations were needed for many data/crawler actions.

### Cause

Admin capability was still basic compared with operational needs.

### Resolution

- Decided admin must eventually manage crawler schedules, task status, article/category, user, VIP, payment, and on/off shelf operations.

### Lesson

Admin is not just a nice-to-have. It is how crawler/content quality becomes operable without direct database access.

## Documentation

### Symptom

Context was spread across conversation, code, screenshots, VPS operations, and ad hoc commands.

### Cause

The project evolved quickly across several directions: architecture, crawler research, deployment, UX, database cleanup.

### Resolution

Added root-level project documents:

- `PROJECT_CONTEXT.md`
- `DEPLOYMENT.md`
- `CRAWLER_DESIGN.md`
- `TODO.md`
- `ROADMAP.md`
- `DECISIONS.md`
- `LESSONS_LEARNED.md`

### Lesson

Fast-moving projects need written memory. Otherwise, the team repeats old debates and rediscovers solved problems.

