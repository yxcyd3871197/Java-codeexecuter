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

## Testing the Endpoint

You can use `curl` or any API client (like Postman) to test the `/fix-json` endpoint.

**Example Request (Malformed JSON):**

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: text/plain" \
-d '{
"name": "Test "Product"",
"description": "This contains a newline\nand typographic quotes like “these”.",
"valid": true
}'
```

**Expected Response (Repaired JSON):**

```json
{
"name": "Test \"Product\"",
"description": "This contains a newline\\nand typographic quotes like \"these\".",
"valid": true
}
```

**Example Request (Unfixable JSON):**

```bash
curl -X POST http://localhost:8080/fix-json \
-H "Content-Type: text/plain" \
-d 'This is not json { name: "test" '
```

**Expected Response (Error):**

```json
{
  "error": "Failed to parse JSON after attempting repairs.",
  "details": "Unexpected character ('n' (code 110)): was expecting double-quote to start field name\n at [Source: (String)\"{ name: \"test\" \"; line: 1, column: 4]"
}
# Note: The exact error message might vary slightly based on the input and Jackson version.
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
      --allow-unauthenticated \
      --port 8080
      # Add --project=${PROJECT_ID} if your default gcloud project is different
    ```

    -   `--allow-unauthenticated` makes the service publicly accessible. Remove this flag if you want to manage access via IAM.
    -   Cloud Run automatically handles HTTPS termination.

You will get a service URL upon successful deployment. You can then send POST requests to `[SERVICE_URL]/fix-json`.
