# SignatureGenerator – Burp Suite Extension

Burp Suite extension that automatically recalculates a JSON body signature and updates a custom `X-Signature` header for each outgoing request.

It was originally developed during a mobile application penetration test, where the backend required every request body to be signed with an RSA private key stored in an Android secure-storage file or in the iOS Keychain, and the resulting signature to be sent in the `X-Signature` header. The extension automates this process so that requests remain valid while they are replayed or modified in Burp Suite.

---

## Background & Features 

In the analysed application, once the user completed the login flow:

- The client sent its **public key** to the backend.
- Subsequent API calls:
    - Contained a JSON body with fields like `requestRandomValue` and `requestTimestamp`.
    - Included an `X-Signature` header whose value was the **digital signature of the request body**.
- The backend verified each request by validating the signature against the body with the stored public key.

![json-body.png](docs/img/json-body.png)

On compromised devices (rooted Android / jailbroken iOS), it was possible to extract the private key used to sign the payload (for example from an Android secure storage file or from the iOS Keychain). Once the key was obtained, a Burp extension was written to:

1. Update `requestRandomValue` and `requestTimestamp` for each modified request.
2. Recalculate the signature on the updated JSON body.
3. Replace the `X-Signature` header with the new value for every request forwarded to the backend.

With this extension, which updates all required fields and recomputes the signature at runtime, it becomes possible to edit, fuzz, and replay requests while keeping a valid signature, so the server continues to accept them.

---

## Build Instructions

From the repository root:

```bash
mvn clean package
```

This will create a JAR under `target/`, for example:

```text
target/burpext-signature-generator-1.0-SNAPSHOT-shaded.jar
```

The shaded JAR bundles the required JSON library and is ready to be loaded into Burp Suite.

---

## Configuration

Most of the behaviour is controlled through constants in `SignatureGenerator.java`. Adjust these to match your target application.

### Target URL

Limit processing to a specific API host or path:

```java
private static final String TARGET_URL = "https://api.example.com/";
```

Only requests whose URL starts with `TARGET_URL` will be modified.

### Signature Header Name

Set the header that will carry the signature:

```java
private static final String SIGNATURE_HEADER_NAME = "X-Signature";
```

Change this if the application uses a different header name.

### JSON Fields to Update

Update these to match the body fields used by the target application:

```java
private static final String FIELD_RANDOM     = "requestRandomValue";
private static final String FIELD_TIMESTAMP  = "requestTimestamp";
```

The extension will:

- Generate a new random value for `FIELD_RANDOM`.
- Update `FIELD_TIMESTAMP` to the current time

Because the backend accepts the timestamp only within a short valid time window, this automatic refresh is required for replay and fuzzing to work reliably.

### Signature Algorithm & Private Key

By default the extension uses Java’s `Signature` API with an RSA key. Example:

```java
private static final String SIGNATURE_ALGORITHM = "SHA256withRSA";
private static final String PEM_PRIVATE_KEY     = "pem-private-key";
```
The `PEM_PRIVATE_KEY` variable represents the RSA private key extracted from the Android secure-storage file or from the iOS Keychain. This key is then used, together with the configured `SIGNATURE_ALGORITHM`, to generate the signature value that is placed in the `X-Signature` header for each request.

---

## How It Works (High Level)

1. **Registration**  
   The `SignatureGenerator` class implements `BurpExtension` and `HttpHandler` from the Montoya API:

    - `initialize(MontoyaApi api)` registers the extension and HTTP handler.
    - Logging is configured through `api.logging()`.

2. **Filtering Requests**  
   In `handleHttpRequestToBeSent`:
    - The handler checks if the request:
        - Targets `TARGET_URL`, and
        - Already contains the configured signature header.

3. **Updating the Body**
    - The request body is read and parsed as JSON.
    - `FIELD_RANDOM` and `FIELD_TIMESTAMP` are updated with new values.
    - The updated JSON is serialized back to a string.

4. **Recalculating the Signature**
    - The private key is loaded (for example from PEM).
    - A new signature is computed over the serialized JSON body using `SIGNATURE_ALGORITHM`.

5. **Rebuilding the Request**
    - A new request is built with:
        - The updated JSON body.
        - The `SIGNATURE_HEADER_NAME` header set to the new signature.
    - The modified request is returned to Burp, which then forwards it to the backend.

6. **Logging**
    - Each processed request can log:
        - Old and new headers.
        - Old and new body, if needed.
    - This helps troubleshoot parsing or signing issues during setup.
