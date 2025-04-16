# JSON Fixer API

A simple Java Spring Boot application that provides a REST API endpoint (`/fix-json`) to repair potentially malformed JSON strings, often encountered in AI-generated outputs.

## Features

-   Accepts JSON-like text via HTTP POST to `/fix-json`.
-   Attempts to fix common issues:
    -   Typographic quotes (`“”‘’’`) replaced with standard quotes (`"` or `'`).
    -   Unescaped double quotes (`"`) within string values (heuristic approach).
    -   Literal newlines (`\n`, `\r`) within string values replaced with escaped versions (`\\n`, `\\r`).
    -   Trims leading/trailing whitespace.
    -   Extracts potential JSON object/array from surrounding text.
-   Returns the repaired JSON string or a JSON error object if parsing fails after repairs.
-   Requires an API key sent via the `X-API-KEY` header for authorization.

## Prerequisites

-   Java 21 (or 17, adjust `pom.xml` and `Dockerfile` accordingly)
-   Apache Maven
-   Docker
-   Google Cloud SDK (`gcloud` CLI) configured for your project

## Building and Running Locally

### 1. Using Maven

```bash
# Build the application JAR
mvn package

# Run the application
java -jar target/json-fixer-*.jar
```

The application will start on `http://localhost:8080`.

### 2. Using Docker

```bash
# Build the Docker image
docker build -t json-fixer-app .

# Run the Docker container
docker run -p 8080:8080 json-fixer-app
```

The application will be accessible at `http://localhost:8080`.

## Configuration

The application requires an API key for authorization. This key is configured via the `jsonfixer.api.key` property defined in `src/main/resources/application.properties`.

-   **Environment Variable (Recommended for Deployment):** Set the environment variable named exactly `JSONFIXER_API_KEY` when running the application (e.g., in your Cloud Run service configuration). This overrides any value in `application.properties`.
    ```bash
    # Example for local run:
    export JSONFIXER_API_KEY="your-secure-api-key"
    java -jar target/json-fixer-*.jar
    # Or set it in the Docker/Cloud Run environment settings
    ```
-   **application.properties (Local Testing/Default):** You can set a default key in `src/main/resources/application.properties`. **Do not commit sensitive keys directly to Git.** The provided `application.properties` uses a placeholder `YOUR_DEFAULT_API_KEY_HERE`.

## Testing the Endpoint

You need to provide the configured API key in the `X-API-KEY` header and send the request body as `application/json` with the following structure:

```json
{
  "data": "string_containing_potentially_malformed_json"
}
```

Replace `your-secure-api-key` with the actual key you configured.

**Example Request (Malformed JSON String within JSON Body):**

This example uses the malformed string from your previous test case within the required JSON structure.

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: application/json" \
-H "X-API-KEY: your-secure-api-key" \
-d '{
"data": "{ \"text\": \"Hallo \\\"Welt\\\" – dies ist ein Test mit \\“komischen\\” Zeichen und Zeilen\\numbrüchen.\\\" }"
}'
```

**Expected Response (Repaired JSON String as Plain Text):**

```json
{ "text": "Hallo \"Welt\" – dies ist ein Test mit \"komischen\" Zeichen und Zeilen\numbrüchen." }
```
*(Note: The response `Content-Type` is `text/plain`, containing the repaired JSON string)*

**Example Request (Missing/Invalid API Key):**

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: application/json" \
-H "X-API-KEY: invalid-key" \
-d '{"data": "some string"}'

# Or without the header:
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: application/json" \
-d '{"data": "some string"}'
```

**Expected Response (Unauthorized - JSON):**

```json
{
  "error": "Unauthorized",
  "details": "Invalid or missing X-API-KEY header."
}
```
*(Note: Error responses are `application/json`)*

**Example Request (Invalid Input JSON Format):**

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: application/json" \
-H "X-API-KEY: your-secure-api-key" \
-d '{"wrong_field": "some string"}' # Missing the 'data' field
```

**Expected Response (Bad Request - JSON):**

```json
{
  "error": "Invalid input format.",
  "details": "Request body must be JSON with a 'data' field containing the string to fix."
}
```

**Example Request (Unfixable String within 'data'):**

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: application/json" \
-H "X-API-KEY: your-secure-api-key" \
-d '{
"data": "This is just text, not json { nope"
}'
```

**Expected Response (Bad Request - JSON):**

```json
{
  "error": "Failed to parse JSON after attempting repairs.",
  "original_input": "This is just text, not json { nope",
  "attempted_fix": "This is just text, not json { nope",
  "details": "Unexpected end-of-input: expected close marker for Object (start marker at [Source: (String)\"This is just text, not json { nope\"; line: 1, column: 30])\n at [Source: (String)\"This is just text, not json { nope\"; line: 1, column: 36]"
}
# Note: The exact error message might vary.
```


## Deployment to Google Cloud Run

1.  **Authenticate Docker with Google Artifact Registry (or Container Registry):**
    ```bash
    gcloud auth configure-docker [YOUR_REGION]-docker.pkg.dev
    # Example: gcloud auth configure-docker us-central1-docker.pkg.dev
    ```

2.  **Build and Push the Docker Image:**
    Replace `[YOUR_PROJECT_ID]` and `[YOUR_REGION]` with your Google Cloud project ID and desired region.
    ```bash
    # Define variables (optional, but helpful)
    export PROJECT_ID=[YOUR_PROJECT_ID]
    export REGION=[YOUR_REGION]
    export IMAGE_NAME=json-fixer-api
    export IMAGE_TAG=${REGION}-docker.pkg.dev/${PROJECT_ID}/cloud-run-source-deploy/${IMAGE_NAME}:latest

    # Build the image using Google Cloud Build (recommended)
    gcloud builds submit --tag $IMAGE_TAG .

    # Alternatively, build locally and push (if Cloud Build is not preferred)
    # docker build -t $IMAGE_TAG .
    # docker push $IMAGE_TAG
    ```

3.  **Deploy to Cloud Run:**
    ```bash
    gcloud run deploy ${IMAGE_NAME} \
      --image $IMAGE_TAG \
      --platform managed \
      --region ${REGION} \
      --platform managed \
      --region ${REGION} \
      --allow-unauthenticated \
      --port 8080 \
      --set-env-vars JSONFIXER_API_KEY="your-secure-api-key-for-cloud-run" # <-- Use this exact variable name!
      # Add --project=${PROJECT_ID} if your default gcloud project is different
      # Replace "your-secure-api-key-for-cloud-run" with your actual key
    ```

    -   `--allow-unauthenticated` makes the service publicly accessible. If you remove this, you'll need to handle authentication (e.g., IAM invoker role) *in addition* to the API key. The API key provides application-level authorization.
    -   `--set-env-vars` is used here to securely pass the API key to the Cloud Run instance using the **required environment variable name `JSONFIXER_API_KEY`**. Consider using Secret Manager for more robust secret handling in production.
    -   Cloud Run automatically handles HTTPS termination.

You will get a service URL upon successful deployment. You can then send POST requests to `[SERVICE_URL]/fix-json`, remembering to include the `X-API-KEY` header.
