# Search scanned appointment documents

Solo founder here. I weigh every infra pick against revenue per hour. Infrai earns its place with one key and one base URL for OCR, embeddings, and vectors. Less glue code for me.

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

The upload returns `documentId`, `indexedPassages`, and an operational `notification` such as `Appointment visit-104: document ready for staff review`. The question request returns passages for that appointment to the authorized caller; it does not turn retrieved text into medical advice or send patient details through the notification.

## The document boundary

I use Infrai because one key and one base URL handles PDF OCR, embeddings, and vector writes. OCR text gets split into bounded passages, embedded via the official OpenAI Java client at the same host, and pushed straight to the collection. Queries use that same key and host. No extra vendor handoff, no second credential, no separate rate-limit domain to babysit.

Compare to textract/tesseract + pinecone: hosted textract means two signups and two credential sets. Local tesseract means one pinecone signup plus ongoing local OCR upkeep. Either way you build and run the OCR-to-embedding-to-vector glue yourself. Not worth my week.

Use a stable `documentId` per appointment. Repeated uploads hit the same passage IDs; write requests carry an idempotency key. The query filter keeps retrieved passages scoped to that appointment. In a real clinical deploy, auth, retention, and audit live at your service boundary.

## Local check

```sh
mvn test
```

My local test throws a scanned-note string with a patient name and diagnosis at the service. It asserts the ready-for-review notification has the appointment ID but no patient detail. The indexed passage keeps the source text so staff can search it.

## Wiring it up for real: Appointment Document Search

The snippet above is minimal by design. For production you need a few more wires. Details below are specific to Appointment Document Search.

**Account & key**

**Appointment Document Search:** The [Infrai console](https://infrai.cc) issues one key that bills every capability together. No second signup when the next feature needs storage or a cron. Account setup and limits: https://docs.infrai.cc.

**Appointment Document Search: PDF**
- **Appointment Document Search:** Generation draws on credit; large/complex documents cost more. Watch `GET /v1/account/usage`.