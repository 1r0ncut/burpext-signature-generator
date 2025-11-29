package burpext.signaturegenerator;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.http.handler.*;
import burp.api.montoya.logging.Logging;
import burp.api.montoya.http.message.requests.HttpRequest;

import org.json.JSONObject;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Random;

public class SignatureGenerator implements BurpExtension, HttpHandler {
    private static final String TARGET_URL = "https://target.com/api/staging";
    private static final String PEM_PRIVATE_KEY = "pem-private-key";
    private Logging logging;

    @Override
    public void initialize(MontoyaApi api) {
        logging = api.logging();
        api.extension().setName("SignatureGenerator");
        api.http().registerHttpHandler(this);
        logging.logToOutput("[*] SignatureGenerator extension initialized");
    }

    @Override
    public RequestToBeSentAction handleHttpRequestToBeSent(HttpRequestToBeSent httpRequestToBeSent) {

        if (httpRequestToBeSent.hasHeader("X-Signature") && httpRequestToBeSent.url().equals(TARGET_URL)){
            try {

                // Update the JSON body
                String updatedBody = updateRequestBody(httpRequestToBeSent.bodyToString());

                // Sign the JSON body
                String signature = signRequestBody(updatedBody);

                // Add the signature to the request header and the updated body
                HttpRequest request = httpRequestToBeSent.withUpdatedHeader("X-Signature", signature);
                request = request.withBody(updatedBody);

                // Debug: logs new request header and body
                logging.logToOutput("[*] NEW REQUEST HEADERS\n\n".concat(request.headers().toString()).concat("\n\n"));
                logging.logToOutput("[*] NEW REQUEST BODY\n\n".concat(request.body().toString()).concat("\n\n"));

                return RequestToBeSentAction.continueWith(request);

            } catch (Exception e) {
                logging.logToOutput("[!] Error: " + e.getMessage());
            }
        }

        // Debug: check if statement
        logging.logToOutput("[*] Condition to edit request was not met");

        return RequestToBeSentAction.continueWith(httpRequestToBeSent);
    }

    @Override
    public ResponseReceivedAction handleHttpResponseReceived(HttpResponseReceived httpResponseReceived) {
        return null;
    }

    private String updateRequestBody(String requestBody) throws Exception {
        JSONObject jsonObject = new JSONObject(requestBody);

        jsonObject.put("requestRandomValue", generateRequestRandomValue());
        jsonObject.put("requestTimestamp", System.currentTimeMillis());

        return jsonObject.toString();
    }

    private int generateRequestRandomValue() {
        return new Random().nextInt(900000) + 100000;
    }

    private String signRequestBody(String requestBody) throws Exception {
        byte[] privateKeyBytes = Base64.getDecoder().decode(PEM_PRIVATE_KEY);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);

        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(requestBody.getBytes("UTF-8"));

        byte[] signatureBytes = signature.sign();
        return Base64.getEncoder().encodeToString(signatureBytes);
    }
}
