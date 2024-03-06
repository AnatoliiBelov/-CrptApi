package org.example;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;
import okhttp3.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CrptApi{
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CrptApi crptApi = (CrptApi) o;
        return requestLimit == crptApi.requestLimit && timeWindow == crptApi.timeWindow && Objects.equals(httpClient, crptApi.httpClient) && Objects.equals(requestTimestamps, crptApi.requestTimestamps);
    }

    @Override
    public int hashCode() {
        return Objects.hash(httpClient, requestTimestamps, requestLimit, timeWindow);
    }

    private static final String CREATE_DOCUMENT_URL = "https://ismp.crpt.ru/api/v3/lk/documents/create";

    private final OkHttpClient httpClient = new OkHttpClient();
    private final Deque<Long> requestTimestamps = new LinkedList<>();
    ;
    private final int requestLimit;
    private final long timeWindow;

    public CrptApi(TimeUnit timeUnit, int requestLimit) {
        this.requestLimit = requestLimit;
        this.timeWindow = timeUnit.toMillis(1);
        try (ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();) {
            scheduler.scheduleAtFixedRate(this::clearExpiredTimestamps, 0, timeWindow, TimeUnit.MILLISECONDS);
        }
    }
    //метод clearExpiredTimestamps с периодом timeUnit(переведенное в милисекунды) удаляет устаревшие метки времени
    private void clearExpiredTimestamps() {
        synchronized (requestTimestamps) {
            long currentTimestamp = System.currentTimeMillis();
            while (!requestTimestamps.isEmpty() && currentTimestamp - requestTimestamps.getFirst() > timeWindow) {
                requestTimestamps.removeFirst();
            }
        }
    }

    public Response createDocument(Document document, String signature) {
        //Внутри синхронизированного блока добавляется текущая временная метка в очередь и проверяется,
        // не превышено ли ограничение на количество запросов за указанный период времени.
        // Если превышено, выполняется ожидание до окончания временного окна.
        synchronized (requestTimestamps) {
            long currentTimestamp = System.currentTimeMillis();
            requestTimestamps.add(currentTimestamp);
            if (requestTimestamps.size() > requestLimit) {
                long oldestTimestamp = requestTimestamps.getFirst();
                long timeElapsed = currentTimestamp - oldestTimestamp;
                if (timeElapsed < timeWindow) {
                    try {
                        long sleepTime = timeWindow - timeElapsed;
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                requestTimestamps.removeFirst();
            }
        }
        //Сериализуем документ в json, формируем POST запрос и отправляем на API
        try {
            String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(document);
            RequestBody body = RequestBody.create(json, MediaType.parse("application/json"));
            Request request = new Request.Builder()
                    .url(CREATE_DOCUMENT_URL)
                    .post(body)
                    .addHeader("Signature", signature)
                    .build();
            return httpClient.newCall(request).execute();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    //Из задания:
    //В задании необходимо просто сделать вызов указанного метода
    public static void main(String[] args) throws Exception {
        CrptApi api = new CrptApi(TimeUnit.SECONDS, 10);

        // Допустим, что у нас есть объект документа и подпись для него
        Optional<String> result = api.createDocument(new Document(), "signature").body().string().describeConstable();
        System.out.println(result);
    }

    @Data
    @NoArgsConstructor
    private static class Document {
        @JsonProperty("description")
        private CrptApi.Description Description;
        @JsonProperty("doc_id")
        private String DocId;
        @JsonProperty("doc_status")
        private String DocStatus;

        private enum DocType {
            @JsonProperty("doc_type")
            LP_INTRODUCE_GOODS,
            @JsonProperty("doc_type")
            LK_RECEIPT,
            @JsonProperty("doc_type")
            LK_KM_CANCELLATION,
            @JsonProperty("doc_type")
            LP_RETURN
        }

        @JsonProperty("importRequest")
        private boolean ImportRequest;
        @JsonProperty("owner_inn")
        private String OwnerInn;
        @JsonProperty("participant_inn")
        private String ParticipantInn;
        @JsonProperty("producer_inn")
        private String ProducerInn;
        @JsonProperty("production_date")
        private LocalDate ProductionDate;
        @JsonProperty("production_type")
        private String ProductionType;
        @JsonProperty("products")
        private ArrayList<Product> Products;
        @JsonProperty("reg_date")
        private LocalDate RegDate;
        @JsonProperty("reg_number")
        private String RegNumber;

    }


    @Data
    @NoArgsConstructor
    private static class Description {
        @JsonProperty("participantInn")
        private String ParticipantInn;
    }


    @Data
    @NoArgsConstructor
    private static class Product {
        @JsonProperty("certificate_document")
        private String CertificateDocument;
        @JsonProperty("certificate_document_date")
        private LocalDate CertificateDocumentDate;
        @JsonProperty("certificate_document_number")
        private String CertificateDocumentNumber;
        @JsonProperty("owner_inn")
        private String OwnerInn;
        @JsonProperty("producer_inn")
        private String ProducerInn;
        @JsonProperty("production_date")
        private LocalDate ProductionDate;
        @JsonProperty("tnved_code")
        private String TNVEDCode;
        @JsonProperty("uit_code")
        private String UITCode;
        @JsonProperty("uitu_code")
        private String UITUCode;

    }

}
