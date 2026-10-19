# OSS Upload, AI Search, and Docker Deployment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add switchable local/Aliyun OSS image storage, harden the existing F08 forum AI search while preserving the independent LangChain4j lab, and provide a Docker Compose deployment for the app and MySQL with optional middleware profiles.

**Architecture:** F02/F03 keep their existing HTTP routes but delegate file handling to a new `f12_media` package through an `ImageStorage` interface. F08 keeps orchestration in `AiSearchServiceImpl` and moves provider HTTP concerns into an injectable `AiChatClient`. Docker uses a multi-stage Java 17 image, a dedicated `docker` Spring profile, and explicit environment-variable boundaries.

**Tech Stack:** Java 17, Spring Boot 3.2.4, MyBatis-Plus, H2/MySQL, Aliyun OSS SDK 3.18.4, JDK HttpClient, LangChain4j, Maven, JUnit 5/MockMvc, Docker Compose.

---

## File structure

New media files live under `src/main/java/org/example/rebuiltzijingforum/f12_media/`:

- `model/ImageKind.java`: distinguishes avatar and post object-key rules.
- `model/StoredImage.java`: carries storage key and public URL.
- `service/ImageStorage.java`: storage contract used by F02 and F03.
- `service/ImageValidator.java`: validates bytes and returns trusted metadata.
- `service/LocalImageStorage.java`: default disk implementation.
- `service/AliyunOssImageStorage.java`: OSS implementation.
- `config/StorageProperties.java`: typed local/OSS configuration.
- `config/StorageConfiguration.java`: conditional provider wiring and validation.
- `GUIDE.md` and `NeedToUnderstandingF12.md`: beginner-readable request chains.

F08 gains `client/AiChatClient.java`, `client/DeepSeekAiChatClient.java`, and `config/AiSearchProperties.java`. Existing controllers, routes, response records, and the search page remain compatible.

Docker deployment files are placed at repository root, with runtime Spring configuration in `src/main/resources/application-docker.yml` and user instructions in `docs/Docker部署.md`.

## Task 1: Create the media contract and validator

**Files:**
- Create: `src/test/java/org/example/rebuiltzijingforum/f12_media/ImageValidatorTest.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/model/ImageKind.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/model/StoredImage.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/service/ImageStorage.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/service/ImageValidator.java`

- [ ] **Step 1: Write the failing validator tests**

Create tests using `MockMultipartFile` for empty data, data larger than 20 MiB, a text file claiming `image/png`, a real PNG, and a RIFF/WEBP signature. The desired API is:

```java
ImageValidator.ValidatedImage image = validator.validate(file);
assertThat(image.extension()).isEqualTo("png");
assertThat(image.contentType()).isEqualTo("image/png");
assertThat(image.bytes()).isEqualTo(pngBytes);
```

Assert invalid inputs throw `BusinessException` with `FILE_EMPTY`, `FILE_TOO_LARGE`, or `FILE_TYPE_ERROR`.

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
mvn -Dtest=ImageValidatorTest test
```

Expected: compilation fails because `ImageValidator` and its value types do not exist.

- [ ] **Step 3: Add the minimal media API**

Use these public contracts:

```java
public enum ImageKind { AVATAR, POST }

public record StoredImage(String objectKey, String url) {}

public interface ImageStorage {
    StoredImage store(MultipartFile file, ImageKind kind, long ownerId);
    void delete(String objectKey);
    Optional<String> objectKeyFromUrl(String url);
}
```

`ImageValidator` reads the multipart bytes once, enforces `20L * 1024 * 1024`, accepts JPEG/PNG/GIF/WEBP only, verifies non-WEBP files with `ImageIO.read`, verifies WEBP with `RIFF....WEBP`, and returns:

```java
public record ValidatedImage(byte[] bytes, String contentType, String extension) {}
```

Do not use the client filename to choose an extension or path.

- [ ] **Step 4: Run validator tests and the full suite**

Run:

```powershell
mvn -Dtest=ImageValidatorTest test
mvn test
```

Expected: validator tests pass; the full suite remains at least 18 tests with zero failures.

- [ ] **Step 5: Commit only Task 1 paths**

```powershell
git add src/main/java/org/example/rebuiltzijingforum/f12_media src/test/java/org/example/rebuiltzijingforum/f12_media/ImageValidatorTest.java
git commit -m "feat: define validated image storage contract"
```

## Task 2: Implement local storage and connect F02/F03

**Files:**
- Create: `src/test/java/org/example/rebuiltzijingforum/f12_media/LocalImageStorageTest.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/config/StorageProperties.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/config/StorageConfiguration.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/service/LocalImageStorage.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f02_profile/service/impl/ProfileServiceImpl.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f03_post/controller/PostImageController.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f01_account/config/WebMvcConfig.java`
- Modify: `src/test/java/org/example/rebuiltzijingforum/AccountRecoveryApiTest.java`
- Modify: `src/main/resources/application.yml`
- Modify: `src/test/resources/application.yml`

- [ ] **Step 1: Write failing local-storage tests**

Use `@TempDir` and a fixed `Clock`. Verify:

```java
StoredImage avatar = storage.store(png, ImageKind.AVATAR, 42L);
assertThat(avatar.objectKey()).startsWith("avatars/42/");
assertThat(avatar.url()).isEqualTo("/uploads/" + avatar.objectKey());
assertThat(temp.resolve(avatar.objectKey())).exists();

StoredImage post = storage.store(png, ImageKind.POST, 42L);
assertThat(post.objectKey()).startsWith("posts/2026/10/");
storage.delete(post.objectKey());
assertThat(temp.resolve(post.objectKey())).doesNotExist();
```

Also assert `delete("../../outside")` and an escaped configured root are rejected.

- [ ] **Step 2: Run the local tests and verify RED**

```powershell
mvn -Dtest=LocalImageStorageTest test
```

Expected: compilation fails because `LocalImageStorage` and `StorageProperties` do not exist.

- [ ] **Step 3: Implement typed storage configuration and local provider**

`StorageProperties` uses prefix `forum.storage` with defaults `type=local` and `localRoot=./uploads`. `StorageConfiguration` enables the properties and exposes `LocalImageStorage` when `forum.storage.type=local` or missing.

`LocalImageStorage.store` must:

```java
ImageValidator.ValidatedImage image = validator.validate(file);
String key = switch (kind) {
    case AVATAR -> "avatars/" + ownerId + "/" + UUID.randomUUID() + "." + image.extension();
    case POST -> "posts/" + YearMonth.now(clock).format(DateTimeFormatter.ofPattern("yyyy/MM"))
            + "/" + UUID.randomUUID() + "." + image.extension();
};
Path target = root.resolve(key).normalize();
if (!target.startsWith(root)) throw pathError();
Files.createDirectories(target.getParent());
Files.write(target, image.bytes(), StandardOpenOption.CREATE_NEW);
return new StoredImage(key, "/uploads/" + key.replace('\\', '/'));
```

- [ ] **Step 4: Replace direct disk writes in F02/F03**

Inject `ImageStorage` into `ProfileServiceImpl` and `PostImageController`. Avatar upload stores the new image, updates `avatarUrl`, and calls `storage.delete(newImage.objectKey())` when the database update fails. Post upload returns:

```java
StoredImage stored = imageStorage.store(file, ImageKind.POST, currentUser.userId());
return Map.of("errno", 0, "data", Map.of("url", stored.url()));
```

Accept `CurrentUserDTO` as the trusted request attribute in the post-image controller. Remove direct `Paths`, `Files`, and `AvatarFileUtils` calls from these two paths.

Before changing the avatar URL, retain the old URL. After the database transaction commits, call `imageStorage.objectKeyFromUrl(oldUrl)` and delete only a key recognized as belonging to the active provider. Never delete an external/default URL, and never delete the old object before commit.

Make `WebMvcConfig.addResourceHandlers` use the configured local root instead of hard-coded `uploads`, while continuing to serve `/uploads/**` in local mode.

- [ ] **Step 5: Extend the existing MockMvc image flow**

Keep `AccountRecoveryApiTest.localAvatarAndPostImageCanBeReadBack`, set a test storage root under `target/test-uploads`, assert an unauthenticated post-image upload returns 401, and clean only files created under that test root.

- [ ] **Step 6: Run RED/GREEN verification**

```powershell
mvn -Dtest=LocalImageStorageTest,AccountRecoveryApiTest test
mvn test
```

Expected: local unit and HTTP flows pass; all tests have zero failures.

- [ ] **Step 7: Commit Task 2 paths**

```powershell
git add src/main/java/org/example/rebuiltzijingforum/f12_media src/main/java/org/example/rebuiltzijingforum/f02_profile/service/impl/ProfileServiceImpl.java src/main/java/org/example/rebuiltzijingforum/f03_post/controller/PostImageController.java src/main/java/org/example/rebuiltzijingforum/f01_account/config/WebMvcConfig.java src/test/java/org/example/rebuiltzijingforum/AccountRecoveryApiTest.java src/main/resources/application.yml src/test/resources/application.yml
git commit -m "feat: route image uploads through local storage"
```

## Task 3: Add the Aliyun OSS adapter

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/service/OssClientFactory.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/service/AliyunOssImageStorage.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f12_media/config/StorageConfiguration.java`
- Create: `src/test/java/org/example/rebuiltzijingforum/f12_media/AliyunOssImageStorageTest.java`

- [ ] **Step 1: Add a failing OSS adapter test**

Mock the Aliyun `OSS` interface and inject it through `OssClientFactory`. Verify `putObject(bucket, key, stream)` receives an `avatars/7/...png` key, the returned URL uses `publicBaseUrl`, `deleteObject` receives the same key, and SDK failures become:

```java
BusinessException failure = catchThrowableOfType(
        () -> storage.store(png, ImageKind.AVATAR, 7L), BusinessException.class);
assertThat(failure.getCode()).isEqualTo("STORAGE_UNAVAILABLE");
assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
```

- [ ] **Step 2: Run the test and verify RED**

```powershell
mvn -Dtest=AliyunOssImageStorageTest test
```

Expected: compilation fails because the SDK dependency and OSS adapter are absent.

- [ ] **Step 3: Add the original project's OSS SDK version cleanly**

Add to `pom.xml` without removing the user's LangChain4j dependency:

```xml
<dependency>
    <groupId>com.aliyun.oss</groupId>
    <artifactId>aliyun-sdk-oss</artifactId>
    <version>3.18.4</version>
</dependency>
```

Fix indentation/model warnings in the dependency section while preserving dependency behavior.

- [ ] **Step 4: Implement conditional OSS wiring**

Use `@ConditionalOnProperty(prefix="forum.storage", name="type", havingValue="oss")`. Build the OSS client with V4 signing, configured endpoint/region, and environment-supplied access key and secret. Validate all required OSS properties before exposing the bean.

Build URLs as:

```java
String base = properties.getOss().resolvedPublicBaseUrl();
return new StoredImage(key, base + "/" + key);
```

Never log credentials, request authorization headers, or raw SDK responses.

- [ ] **Step 5: Run adapter and full tests**

```powershell
mvn -Dtest=AliyunOssImageStorageTest test
mvn test
```

Expected: tests use only the mocked OSS client; no real network request occurs.

- [ ] **Step 6: Commit Task 3 paths**

```powershell
git add pom.xml src/main/java/org/example/rebuiltzijingforum/f12_media src/test/java/org/example/rebuiltzijingforum/f12_media/AliyunOssImageStorageTest.java
git commit -m "feat: add opt-in Aliyun OSS image storage"
```

## Task 4: Extract and test the F08 AI provider client

**Files:**
- Create: `src/main/java/org/example/rebuiltzijingforum/f08_search/config/AiSearchProperties.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f08_search/client/AiChatClient.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/f08_search/client/DeepSeekAiChatClient.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f08_search/service/impl/AiSearchServiceImpl.java`
- Create: `src/test/java/org/example/rebuiltzijingforum/f08_search/DeepSeekAiChatClientTest.java`
- Create: `src/test/java/org/example/rebuiltzijingforum/f08_search/AiSearchServiceImplTest.java`

- [ ] **Step 1: Write failing client and orchestration tests**

Use JDK `HttpServer` on a random local port for the provider client. Assert Bearer authorization, JSON model/messages, successful content extraction, non-2xx mapping, empty content mapping, and a bounded timeout.

Mock `AiChatClient` and `SearchService` for orchestration. Cover:

- disabled status and `AI_SEARCH_DISABLED`;
- blank and over-300-character questions;
- JSON rewrite and allowed category;
- invalid rewrite falling back to the original question;
- empty categorized search retrying without category;
- no sources returning a deterministic insufficient-data answer without a second model call;
- sources passed to the final prompt and returned to the API.

- [ ] **Step 2: Run tests and verify RED**

```powershell
mvn -Dtest=DeepSeekAiChatClientTest,AiSearchServiceImplTest test
```

Expected: compilation fails because the new client and properties do not exist.

- [ ] **Step 3: Introduce the client boundary**

Use this contract:

```java
public interface AiChatClient {
    String chat(String systemPrompt, String userPrompt, int maxTokens);
}
```

Move URI creation, request JSON, timeouts, Authorization header, response-status checks, and content extraction from `AiSearchServiceImpl` into `DeepSeekAiChatClient`. Keep the public controller routes and response records unchanged.

`AiSearchServiceImpl` must contain only validation, rewrite parsing, forum search, context construction, and response assembly.

- [ ] **Step 4: Run focused, API, and full tests**

```powershell
mvn -Dtest=DeepSeekAiChatClientTest,AiSearchServiceImplTest,FeatureFlowApiTest test
mvn test
```

Expected: existing `/api/search/ai/status` still returns `available=false` under test defaults; all new tests pass without external API calls.

- [ ] **Step 5: Commit Task 4 paths**

```powershell
git add src/main/java/org/example/rebuiltzijingforum/f08_search src/test/java/org/example/rebuiltzijingforum/f08_search src/main/resources/application.yml
git commit -m "refactor: isolate DeepSeek client from AI search"
```

## Task 5: Preserve and isolate the LangChain4j learning page

**Files:**
- Modify: `src/main/java/org/example/rebuiltzijingforum/learninglangchian/AiController.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/learninglangchian/LearningAiConfiguration.java`
- Create: `src/main/java/org/example/rebuiltzijingforum/learninglangchian/LearningAiRequest.java`
- Modify: `src/main/resources/static/langchian/app.js`
- Modify: `src/main/resources/static/langchian/index.html`
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/org/example/rebuiltzijingforum/learninglangchian/LearningAiDisabledTest.java`

- [ ] **Step 1: Write the failing disabled-by-default test**

Start the Spring context with no learning-AI key and assert the application starts and `/ai/ask` is not registered. Add a small MVC slice or context-runner test that enables the feature with a fake `ChatModel` and verifies:

```http
POST /ai/ask
Content-Type: application/json

{"question":"什么是 LangChain4j？"}
```

returns UTF-8 plain text.

- [ ] **Step 2: Verify RED**

```powershell
mvn -Dtest=LearningAiDisabledTest test
```

Expected: the current constructor-created model/controller is present without the opt-in property and still uses GET.

- [ ] **Step 3: Add conditional configuration without discarding user work**

Keep the existing page and `ChatModel` idea, but create the model only when `forum.learning-ai.enabled=true`. Read the API key, base URL, and model from Spring properties/environment variables. Change the controller to constructor injection and POST JSON:

```java
@PostMapping(value = "/ask", produces = "text/plain;charset=UTF-8")
public String ask(@RequestBody LearningAiRequest request) {
    String question = request.question() == null ? "" : request.question().trim();
    if (question.isBlank() || question.length() > 1000) throw invalidQuestion();
    return model.chat(question);
}
```

Update `app.js` to send JSON with `fetch('/ai/ask', {method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({question})})`. Update the page's visible call-chain label from GET query parameters to POST JSON.

- [ ] **Step 4: Run focused and full tests**

```powershell
mvn -Dtest=LearningAiDisabledTest,BaselineApiTest test
mvn test
```

Expected: the default test context does not require a model key; the independent page assets remain served.

- [ ] **Step 5: Commit the user's learning slice plus isolation changes intentionally**

Review `git diff` first, then commit only:

```powershell
git add pom.xml src/main/java/org/example/rebuiltzijingforum/learninglangchian src/main/resources/static/langchian src/main/resources/application.yml src/test/java/org/example/rebuiltzijingforum/learninglangchian
git commit -m "feat: isolate the LangChain4j learning lab"
```

## Task 6: Add Docker packaging and Compose deployment

**Files:**
- Create: `Dockerfile`
- Create: `.dockerignore`
- Create: `compose.yaml`
- Create: `.env.example`
- Modify: `.gitignore`
- Create: `src/main/resources/application-docker.yml`
- Create: `docs/Docker部署.md`
- Create: `src/test/java/org/example/rebuiltzijingforum/DockerProfileConfigurationTest.java`

- [ ] **Step 1: Write configuration assertions before deployment files**

Add a lightweight test that starts with profiles `docker,mysql`, supplies a non-network datasource override, and asserts the resolved server port/storage/feature flags. It must prove cloud and middleware features stay disabled unless their environment variables are true.

- [ ] **Step 2: Verify RED**

```powershell
mvn -Dtest=DockerProfileConfigurationTest test
```

Expected: failure because `application-docker.yml` is absent.

- [ ] **Step 3: Add the multi-stage Dockerfile**

Use Java 17 and a non-root runtime user:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -B clean package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S forum && adduser -S forum -G forum
WORKDIR /app
COPY --from=build /workspace/target/rebuilt-zijing-forum-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/uploads && chown -R forum:forum /app
USER forum
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
  CMD wget -q -O - http://127.0.0.1:8080/api/health >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

- [ ] **Step 4: Add Compose services and profiles**

Default services are `app` and `mysql`. MySQL has a data volume and `mysqladmin ping`; app waits for `service_healthy` and mounts an uploads volume. Redis uses profiles `redis,full`, RabbitMQ uses `rabbit,full`, and Elasticsearch uses `search,full`. Bind application port `${APP_PORT:-8080}:8080`.

Pass configuration only by environment variables, including `ZIJING_DB_URL=jdbc:mysql://mysql:3306/...`, JWT secret, storage selector, OSS values, F08 AI values, and optional middleware enablement flags.

- [ ] **Step 5: Add the docker Spring profile and examples**

`application-docker.yml` uses service names `mysql`, `redis`, `rabbitmq`, and `elasticsearch`, while preserving false defaults for Redis/Rabbit/Elasticsearch, AI, learning AI, and OSS. Add `.env` to `.gitignore`; `.env.example` contains no usable secrets.

- [ ] **Step 6: Validate without starting containers**

```powershell
mvn -Dtest=DockerProfileConfigurationTest test
docker compose --env-file .env.example config
docker build -t rebuilt-zijing-forum:test .
```

Expected: Spring configuration test passes, Compose configuration renders, and image build exits 0.

- [ ] **Step 7: Commit deployment files**

```powershell
git add Dockerfile .dockerignore compose.yaml .env.example .gitignore src/main/resources/application-docker.yml docs/Docker部署.md src/test/java/org/example/rebuiltzijingforum/DockerProfileConfigurationTest.java
git commit -m "feat: add Docker Compose deployment"
```

## Task 7: Update feature catalog and learning documentation

**Files:**
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/GUIDE.md`
- Create: `src/main/java/org/example/rebuiltzijingforum/f12_media/NeedToUnderstandingF12.md`
- Modify: `src/main/java/org/example/rebuiltzijingforum/common/web/FeatureCatalogController.java`
- Modify: `src/test/java/org/example/rebuiltzijingforum/BaselineApiTest.java`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f02_profile/NeedToUnderstandingF02.md`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f03_post/NeedToUnderstandingF03.md`
- Modify: `src/main/java/org/example/rebuiltzijingforum/f08_search/NeedToUnderstandingF08.md`
- Modify: `README.md`

- [ ] **Step 1: Make the feature-catalog test fail for F12**

Change `featureCatalogContainsElevenSlices` to `featureCatalogContainsTwelveSlices`, expect length 12, and assert item 11 is `F12`/`media`.

- [ ] **Step 2: Verify RED**

```powershell
mvn -Dtest=BaselineApiTest#featureCatalogContainsTwelveSlices test
```

Expected: JSON array length is still 11.

- [ ] **Step 3: Add F12 and write source-grounded guides**

Add:

```java
new FeatureView("F12", "media", "本地与阿里云 OSS 图片存储")
```

The guides must show the exact front-end -> interceptor -> controller -> `ImageStorage` -> local/OSS chain, configuration switches, compensation boundary, tests, and the fact that real OSS is not verified without credentials.

Update F02/F03 references from direct disk writes to F12. Update F08 to name `AiChatClient` and distinguish official forum search from the learning lab. Update README's completed/unfinished list and Docker startup commands.

- [ ] **Step 4: Verify docs and feature catalog**

```powershell
mvn -Dtest=BaselineApiTest test
rg -n "图片上传.*未完成|AI 搜索.*未完成" README.md src/main/java/org/example/rebuiltzijingforum
git diff --check
```

Expected: BaselineApiTest passes, stale unfinished claims are absent, and diff check reports no whitespace errors.

- [ ] **Step 5: Commit documentation paths**

```powershell
git add README.md src/main/java/org/example/rebuiltzijingforum/common/web/FeatureCatalogController.java src/test/java/org/example/rebuiltzijingforum/BaselineApiTest.java src/main/java/org/example/rebuiltzijingforum/f02_profile/NeedToUnderstandingF02.md src/main/java/org/example/rebuiltzijingforum/f03_post/NeedToUnderstandingF03.md src/main/java/org/example/rebuiltzijingforum/f08_search/NeedToUnderstandingF08.md src/main/java/org/example/rebuiltzijingforum/f12_media
git commit -m "docs: teach media AI and Docker request chains"
```

## Task 8: Full verification and optional live integration

**Files:**
- Modify only if verification reveals an in-scope defect.

- [ ] **Step 1: Run the complete deterministic verification**

```powershell
mvn clean test
mvn clean package
git diff --check
docker compose --env-file .env.example config
docker build -t rebuilt-zijing-forum:test .
```

Read every exit code and Surefire summary. Do not infer Maven success from compilation alone.

- [ ] **Step 2: Start the default Compose stack when Docker is available**

Create a temporary `.env` containing non-default local test passwords and a 32+ byte JWT secret, then run:

```powershell
docker compose up -d --build
docker compose ps
```

Wait for `mysql` and `app` to report healthy, then call `http://localhost:8080/api/health`, register/login, upload a local image, read it back, perform ordinary search, and call the AI status endpoint. AI status should be unavailable when no key is provided.

- [ ] **Step 3: Stop without deleting data**

```powershell
docker compose down
```

Do not add `-v`; persistent volumes remain recoverable.

- [ ] **Step 4: Report external-service boundaries**

If no valid OSS/DeepSeek credentials were supplied, report exactly:

- local upload: runtime verified or not verified;
- OSS adapter: deterministic tests passed, real OSS not connected;
- AI orchestration/provider protocol: deterministic tests passed, real DeepSeek not connected;
- Docker image/Compose: config/build/runtime result separately;
- Redis/RabbitMQ/Elasticsearch profiles: config-only or runtime-verified state.
