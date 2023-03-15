package cz.iusupart;

import okhttp3.*;
import org.codehaus.jackson.map.ObjectMapper;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;
import org.codehaus.jackson.node.ObjectNode;

public class CrptAPI {

    private final long intervalUnitsInNanos;
    private final int requestLimit;
    private long lastRequestTimeInNanos;
    private final Semaphore semaphore;
    private final ObjectMapper objectMapper;
    private final String baseUrl = "https://ismp.crpt.ru";
    private final OkHttpClient okHttpClient;
    private long lastTokenGenerationTimeInNanos;
    private String authToken;
    private final long expireDateInNanos = TimeUnit.HOURS.toNanos(10);

    public CrptAPI(TimeUnit timeUnit, int requestLimit) {
        this.intervalUnitsInNanos = timeUnit.toNanos(1);
        this.requestLimit = requestLimit;
        this.semaphore = new Semaphore(requestLimit);
        this.objectMapper = new ObjectMapper();
        this.lastRequestTimeInNanos = System.nanoTime();
        this.okHttpClient = new OkHttpClient();
    }

    public static void main(String[] args) {
        CrptAPI crptAPI = new CrptAPI(TimeUnit.MINUTES, 5);
        Document document = new Document("Test title!", "Test text");
        try {
            crptAPI.createDocument(document, "dlsghdsgl");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
        catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private void createDocument(Object document, String signature) throws IOException, InterruptedException, JSONException {
        String documentJSON = objectMapper.writeValueAsString(document);

        long tempTimeInterval = System.nanoTime();

        //Checking for the number of requests
        if (tempTimeInterval - lastRequestTimeInNanos > intervalUnitsInNanos) {
            semaphore.release(requestLimit);
            lastRequestTimeInNanos = tempTimeInterval;
        }

        semaphore.acquire();

        //Checking token validity
        if (lastTokenGenerationTimeInNanos + expireDateInNanos <= System.currentTimeMillis() * 1_000_000L) {
            getToken();
        }

        ObjectNode jsonBody = objectMapper.createObjectNode();
        jsonBody.put("product_document", documentJSON);
        jsonBody.put("document_format", "MANUAL");
        jsonBody.put("signature", signature);
        jsonBody.put("type", "LP_INTRODUCE_GOODS");

        String jsonBodyAsString = objectMapper.writeValueAsString(jsonBody);

        String requestUrl = baseUrl + "/api/v3/lk/documents/create?pg=milk";

        RequestBody requestBody = RequestBody.create("application/json", MediaType.parse(jsonBodyAsString));
        Request request = new Request.Builder()
                .url(requestUrl)
                .addHeader("Authorization", authToken)
                .post(requestBody)
                .build();

        Response response = okHttpClient.newCall(request).execute();
        if (response.isSuccessful()){
            String responseBody = response.body().string();
            System.out.println("Успешное создание документа! " + responseBody);
        } else {
            throw new IOException("Произошла ошибка при оформлении документа!");
        }
    }

    private void getToken() throws IOException, JSONException {

        String requestGetAuthDataUrl = baseUrl + "/api/v3/auth/cert/key";

        //GETTING RANDOM AUTH DATA

        Request request = new Request.Builder()
                .url(requestGetAuthDataUrl)
                .get()
                .build();

        Response response = okHttpClient.newCall(request).execute();
        String responseBody = response.body().string();
        if (!response.isSuccessful()) {
            throw new IOException(responseBody);
        }

        //GETTING THE AUTH KEY

        String requestGetAuthToken = baseUrl + "/api/v3/auth/cert/";
        JSONObject jsonObject = new JSONObject(responseBody);

        ObjectNode jsonBody = objectMapper.createObjectNode();
        jsonBody.put("uuid", jsonObject.get("uuid").toString());
        jsonBody.put("data", "signature");

        String jsonBodyAsString = objectMapper.writeValueAsString(jsonBody);

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBodyAsString);
        Request gettingToketRequest = new Request.Builder()
                .url(requestGetAuthToken)
                .post(requestBody)
                .build();

        Response responseGetToken = okHttpClient.newCall(gettingToketRequest).execute();
        String responseGetTokenBody = responseGetToken.body().string();
        if (!responseGetToken.isSuccessful()) {
            throw new IOException(responseGetTokenBody);
        }
        JSONObject jsonUUIDDataObject = new JSONObject(responseGetTokenBody);
        String tokenInStringFormat = jsonUUIDDataObject.get("token").toString();
        authToken =  tokenInStringFormat;
        lastTokenGenerationTimeInNanos = System.currentTimeMillis() * 1_000_000L;
    }
}

class Document {
    private String title;
    private String text;

    public Document(String title, String text) {
        this.title = title;
        this.text = text;
    }

    public String getTitle() {
        return title;
    }

    public String getText() {
        return text;
    }
}
