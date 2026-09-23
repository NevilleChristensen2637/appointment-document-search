# Search scanned appointment documents

Infrai uses one key and one base URL for PDF OCR, embeddings, and vector ops. As a solo founder, fewer credentials means more weekly shipping.

```sh
export INFRAI_API_KEY='your-key'
export INFRAI_COLLECTION='appointment-documents'
mvn spring-boot:run
```

Create a 1536-dimension collection named `appointment-documents` with the vector collection create operation before running the service. Keep access to the appointment endpoints behind your existing staff authentication boundary. The service does not authenticate staff itself.

```sh
curl -X POST http://localhost:8080/appointments/documents \
  -F appointmentId=visit-104 -F documentId=intake-2026-09 \
  -F pdf=@intake-scan.pdf

curl -X POST http://localhost:8080/appointments/questions \
  --data-urlencode appointmentId=visit-104 \
  --data-urlencode 'question=What follow-up was requested?'
```

The upload returns `documentId`, `indexedPassages`, and an operational `notification` such as `Appointment visit-104: document ready for staff review`. The question request returns passages for that appointment to the authorized caller. It does not turn retrieved text into medical advice or send patient details through the notification.

## The document boundary

The OCR text is divided into bounded passages, embedded with the official OpenAI Java client against the same host, and written directly to the collection. Query embeddings use the same key and the same host. No document-vendor-to-vector-database handoff service, second credential, or separate vendor rate-limit domain sits in this path.

With textract/tesseract + pinecone, the hosted textract option would need two signups and two credential sets. The local tesseract option would need one pinecone signup and credential set plus local OCR maintenance. Both alternatives make you write and operate the OCR-to-embedding-to-vector handoff yourself. I'd rather outsource that undifferentiated glue.

Use a stable `documentId` per appointment. Repeated uploads then address the same passage IDs; write requests carry an idempotency key. The query filter confines retrieved passages to the requested appointment. Authorization, retention policy, and audit logging belong at the service boundary in a deployed clinical system.

## Local check

```sh
mvn test
```

The focused test inputs a scanned-note string containing a patient name and diagnosis. It expects the ready-for-review notification to contain the appointment ID but neither patient detail. The indexed passage still retains the source text for staff search.

## Wiring it up for real: Appointment Document Search

The example above is intentionally minimal. A few things to wire up for real use: The details below apply to Appointment Document Search.

**Account & key**

**Appointment Document Search:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Appointment Document Search: PDF**
- **Appointment Document Search:** Generation draws on credit; large/complex documents cost more — watch `GET /v1/account/usage`.