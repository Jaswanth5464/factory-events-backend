
# ✅ Performance Benchmark - Factory Events Backend System

This document contains benchmark details for the assignment requirement:

✅ Must process a batch of **1000 events in under 1 second** on a standard laptop.  
✅ Must support concurrency (5–20 parallel requests).

---

## 1) 💻 System Specifications

**Machine Used**
- **CPU**: Intel Core i7
- **RAM**: 16 GB
- **OS**: Windows 11
- **Java Version**: Java 17
- **Spring Boot Version**: 3.5.9
- **Database**: PostgreSQL (Local)

---

## 2) ✅ Benchmark Command Used (JUnit Benchmark)

I created and executed this test class:

```

BenchmarkDbInsert1000Test

```

This benchmark test does:
- Loads `events_1000.json` file containing 1000 events
- Parses JSON → DTO list
- Converts DTO → Entity
- Computes **MD5 hash** for payload comparison (dedupe/update logic)
- Performs **batch insert** using JDBC
- Measures time taken after warm-up run

✅ JSON file location:

```

src/test/resources/events_1000.json

````

---

## 3) ⏱️ Benchmark Result (1000 Events Insert)

### ✅ Breakdown Output (Measured on local PostgreSQL)

| Step | Time (ms) |
|------|----------|
| Clear DB (1st) | ~15 ms |
| JSON Parse (File → DTO List) | ~90 ms |
| DTO → Event + MD5 Compute | ~77 ms |
| Warm-up Insert | ~86 ms |
| Clear DB (2nd) | ~2 ms |
| ✅ REAL DB Insert (Measured) | ✅ ~29 ms |
| DB Count Query | ~11 ms |
| ✅ TOTAL (All Steps Combined) | ✅ ~310 ms |

✅ **Final Result:**  
✅ 1000 events processed well under **1 second**  
✅ Requirement satisfied ✅
![img.png](img.png)
---

## 4) 🚀 API Benchmark (Curl Test)

### ✅ POST /events/batch with 1000 events
Command used:

```powershell
Measure-Command {
  curl.exe -s -X POST "http://localhost:8080/events/batch" `
    -H "Content-Type: application/json" `
    --data-binary "@src/test/resources/events_1000.json"
}
````

✅ Observed time:

* ~103 ms on local laptop

---

## 5) ✅ Concurrency Test (10 parallel requests)

To simulate multiple sensors pushing concurrently:

```powershell
$path = "C:\Users\kanam\Downloads\springbootprojects\events\events\src\test\resources\events_1000.json"

Measure-Command {
  $jobs = 1..10 | ForEach-Object {
    Start-Job -ArgumentList $_, $path -ScriptBlock {
      param($i, $p)

      $resp = Invoke-RestMethod -Uri "http://localhost:8080/events/batch" `
        -Method Post `
        -ContentType "application/json" `
        -InFile $p

      "Request-$i => accepted=$($resp.accepted), deduped=$($resp.deduped), updated=$($resp.updated), rejected=$($resp.rejected)"
    }
  }

  $jobs | Wait-Job | Out-Null
  $jobs | Receive-Job
  $jobs | Remove-Job
}
```

✅ This runs **10 parallel requests** and confirms system is thread-safe.

---




![img_1.png](img_1.png)






![img_2.png](img_2.png)




## 6) 🔥 Optimizations Applied

### ✅ 1) JDBC Batch Insert / Batch Update

* Used `jdbcTemplate.batchUpdate()` instead of insert 1000 times one-by-one.
* Minimizes database network round-trips.

### ✅ 2) One-time fetch for existing eventIds

* Checked all existing event IDs using a single query:

    * `SELECT * FROM events WHERE event_id IN (...)`

### ✅ 3) MD5 Hash for Fast Payload Comparison

* Payload comparison is done using an **MD5 hash** stored in DB
* same eventId + same MD5 → dedupe
* same eventId + different MD5 → update decision

### ✅ 4) Connection Pooling (HikariCP)

* Reuses DB connections efficiently
* Faster compared to new connection per request

### ✅ 5) Indexes for Query Performance

Indexes used:

```sql
CREATE INDEX idx_machine_time ON events(machine_id, event_time);
CREATE INDEX idx_factory_line_time ON events(factory_id, line_id, event_time);
CREATE INDEX idx_event_time ON events(event_time);
```

### ✅ 6) Warm-up Run (Important)

* First insert warms JVM + DB cache
* Second run gives real consistent benchmark time

---

✅ Final Conclusion:
✅ System meets the assignment performance target
✅ Batch insert of 1000 events completes under **1 second**
✅ Thread-safe under concurrent requests

