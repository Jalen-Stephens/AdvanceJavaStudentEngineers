MetaDetect Test Partitions
=========================

This document enumerates the equivalence partitions and boundaries exercised by the automated tests. Test method names are referenced so the mapping is explicit.

Unit-Level Partitions
---------------------
- AnalyzeService
  - Storage path present vs missing: `submitAnalysis_happyPath...`, `submitAnalysis_missingStoragePath_throws400`.
  - Ownership success vs Forbidden/NotFound: `submitAnalysis_propagatesOwnershipErrors`, `compare_forbiddenOnLeft_propagates`.
  - Download success vs empty/IO failure: `downloadToTemp_fileUrl_intoProvidedDir_copiesBytes`, `downloadToTemp_withoutExtension_defaultsToBinExtension_andFailsOnEmpty`.
  - C2PA success vs runtime failure: `submitAnalysis_happyPath_marksCompleted_andReturnsId`, `submitAnalysis_c2paFailure_marksFailed`.
  - Analysis lookup present vs missing manifest vs missing row: `getMetadata_success...`, `getMetadata_missing_throws404`, `getMetadata_notFound_throws404`, `getConfidence_missingAnalysis_throwsNotFound`.
  - Utility bounds: `truncate_*` (null, under-limit, over-limit) and `escapeForJson_escapesControlCharacters`.

- SupabaseStorageService
  - Upload content-type provided vs default: `uploadObject_putsRawBytesToCorrectPath_withAuthAndApikeyHeaders`.
  - Signed URL parsing valid vs malformed/null: `createSignedUrl_returnsAbsoluteProjectUrl_andSendsHeaders`, `extractSignedUrlFromJson_handlesMalformed`.
  - Delete path blank vs 404 vs success: `deleteObject_ignoresBlankPath`, `deleteObject_404IsSwallowed`.

- C2paToolInvoker
  - Manifest present with active id + AI generator vs empty manifests: `parseMetadata_detectsActiveManifest_andAiGenerator`, `parseMetadata_noManifest_setsFlagsToZero`.
  - Invalid JSON / CLI failures: `parseMetadata_invalidJson_setsErrorFlag`, `extractMetadata_cliFailure_setsErrorFlag`, `extractMetadata_nullFile_returnsError`.

- FeatureExtractor
  - Missing image path -> C2PA-only defaults: `extractAllFeatures_missingFile_returnsC2paOnly`.
  - Small matrix feature computations (Laplace, noise, edges, frequency, entropy) -> non-negative bounds: `featureMethods_handleSmallMatrix`.

- DTO shape/record coverage: `DtosTest.recordsRoundTrip` covers all request/response records including nullables and collections.

API/Controller Partitions
-------------------------
- /api/analyze
  - POST valid image id -> 202 payload: `submit_returnsAcceptedWithBody`.
  - GET status success vs invalid UUID: `getStatus_returnsDtoFromService`, `getStatus_invalidUuid_returns400`.
  - GET manifest success: `getManifest_returnsManifestJson`.
  - GET compare: both params present vs missing `right`: `compare_returnsComparisonResult`, `compare_missingParam_returnsBadRequest`.

- /auth
  - Signup/login/refresh happy paths: `signup_ok`, `login_ok`, `refresh_ok`.
  - Refresh missing token partition (controller validation) vs framework-invalid body: `refresh_missing_field_400_controller`, `refresh_empty_body_400_framework`.
  - Proxy error bubble-up: `proxy_error_bubbled`, `proxy_error_handler_sets_json`.
  - /auth/me authenticated with/without email vs unauthenticated: `me_authenticated_ok`, `me_authenticated_email_null`, `me_unauthenticated_401`.
  - Method/MediaType boundaries: `signup_method_not_allowed_405`, `signup_invalid_json_400`, `login_unsupported_media_type_415`.

- /api/images
  - List empty/out-of-range vs populated: `listImages_outOfRangePagination_returnsEmptyList`, `listImages_success`.
  - Get found vs not found vs forbidden: `getImage_success`, `getImage_notFound`, `getImage_forbidden`.
  - Update with partial fields vs note/labels change: `updateImage_success`, `updateImage_blankFilename_isIgnored_butOtherFieldsApply`.
  - Upload multipart success: `upload_success_returns201AndPersistsStoragePath`.
  - Signed URL fetch: `signedUrl_success_returns200WithUrl`.

- Security/CORS/JWT
  - Public vs secured resources and CORS preflight: `publicEndpoints_noAuthRequired`, `optionsPreflight_corsHeaders`.
  - JWT issuer boundaries (with/without slash, wrong issuer): `jwtDecoder_validIssuer_noSlash`, `jwtDecoder_validIssuer_withSlash`, `jwtDecoder_wrongIssuer_rejected`.

Integration Tests
-----------------
- `C2paToolInvokerIntegrationTest` and `AnalyzeServiceC2paIntegrationTest`: real c2patool binary when present (external CLI integration).
- `SupabaseStorageServiceTest` uses `MockWebServer` to exercise HTTP-level integration (multiple clients + logging).
- `ClientServiceLiveE2eTest` (opt-in via `LIVE_E2E=true`): full stack against real Supabase auth/storage/database.

Coverage Goal
-------------
All tests run in the Maven `test` phase and are wired for JaCoCo. The above partitions intentionally cover valid/invalid inputs and boundary conditions to drive branch coverage above the 80% requirement.
