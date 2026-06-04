# Frontend Task — Upload Exam + Answer Key to the Knowledge Base

## Goal
Let a teacher upload an **exam's questions and/or its official answer key** so the
backend chunks, embeds, and adds it to the Chroma vector DB. After this, future
student submissions for that exam can retrieve the real rubric/answers instead of
near-random chunks.

This is **different** from the existing scan/upload flow:
- Existing flow (`CameraFragment` / Dashboard "Upload PDF") → grades a *student
  submission* via `POST /api/evaluate`.
- **New flow** → ingests a *reference exam/answer key* into the DB via
  `POST /api/ingest`. Nothing is graded.

---

## Backend contract (already implemented & tested)

`POST {baseUrl}api/ingest`  — JSON body, JSON response. Same `ApiClient` base URL
as evaluate. `readTimeout` is already 0 (infinite), so OCR-heavy ingests are fine.

### Request
At least one of the two documents (questions / answer key) is required. For each,
send **either** inline `*_text` **or** a downloadable `*_url` (backend OCRs the URL
with RapidOCR, same as evaluate's `image_url`).

```jsonc
{
  "course_code": "6003",     // required — reliable metadata, NOT auto-extracted
  "year": "2019",            // required — e.g. "2019"
  "semester": "sem1",        // required — one of: sem1 | sem2 | sem3
  "moed": "moed1",           // required — one of: moed1 | moed2
  "version": "v1",           // optional — defaults to "v1"

  "questions_text": null,    // optional
  "questions_url": null,     // optional (Firebase Storage download URL)
  "answer_key_text": null,   // optional
  "answer_key_url": "https://.../answer_key.pdf"  // optional
}
```

Auto-detect behaviour (no flag needed):
- both questions + answer key provided → paired (separate question/answer chunks),
- only answer key → `answer_key_only`,
- only questions → `questions_only`.

> ⚠️ The metadata values must match the corpus conventions so retrieval filters
> line up: `semester` ∈ {sem1,sem2,sem3}, `moed` ∈ {moed1,moed2}, `year` a 4-digit
> string, `course_code` the bare code (e.g. "6003"). Use dropdowns, not free text,
> for semester/moed.

### Response
```jsonc
{
  "doc_id": "6003_2019_sem1_moed1_v1",
  "chunks_added": 19,
  "chunk_ids": ["6003_2019_sem1_moed1_v1_q1", "..."],
  "chunk_types": { "answer_key": 17, "reading": 1, "instruction": 1 },
  "mode": "answer_key_only"
}
```
Re-uploading the same `course_code/year/semester/moed/version` **upserts** (no
duplicates), so a re-submit to fix a bad scan is safe.

---

## Files to add / change

### 1. `network/model.kt` — add request/response models
```kotlin
data class IngestRequest(
    @SerializedName("course_code") val courseCode: String,
    @SerializedName("year") val year: String,
    @SerializedName("semester") val semester: String,
    @SerializedName("moed") val moed: String,
    @SerializedName("version") val version: String = "v1",
    @SerializedName("questions_text") val questionsText: String? = null,
    @SerializedName("questions_url") val questionsUrl: String? = null,
    @SerializedName("answer_key_text") val answerKeyText: String? = null,
    @SerializedName("answer_key_url") val answerKeyUrl: String? = null
)

data class IngestResponse(
    @SerializedName("doc_id") val docId: String = "",
    @SerializedName("chunks_added") val chunksAdded: Int = 0,
    @SerializedName("chunk_ids") val chunkIds: List<String> = emptyList(),
    @SerializedName("chunk_types") val chunkTypes: Map<String, Int> = emptyMap(),
    @SerializedName("mode") val mode: String = ""
)
```

### 2. `network/api.kt` — add the endpoint to `FastApiService`
```kotlin
@POST("api/ingest")
suspend fun ingestExam(
    @Body request: IngestRequest
): Response<IngestResponse>
```
(No client changes — reuse the existing `ApiClient`.)

### 3. `network/repository.kt` — new repository (mirror `EvaluationRepository`)
```kotlin
class IngestRepository {
    suspend fun ingest(request: IngestRequest): Result<IngestResponse> {
        return try {
            val response = FastApiService.ApiClient.fastApiService.ingestExam(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Server Error: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

### 4. `viewmodel/IngestViewModel.kt` — new (mirror `EvaluationViewModel`)
```kotlin
class IngestViewModel : ViewModel() {
    private val repository = IngestRepository()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading
    private val _result = MutableLiveData<IngestResponse?>(null)
    val result: LiveData<IngestResponse?> = _result
    private val _errorMessage = MutableLiveData<String?>(null)
    val errorMessage: LiveData<String?> = _errorMessage

    fun ingest(request: IngestRequest) {
        if (_isLoading.value == true) return
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            _result.value = null
            repository.ingest(request)
                .onSuccess { _result.value = it }
                .onFailure { _errorMessage.value = it.localizedMessage ?: "Ingest failed" }
            _isLoading.value = false
        }
    }

    fun clear() { _result.value = null; _errorMessage.value = null }
}
```

### 5. `ui/IngestFragment.kt` + `res/layout/fragment_ingest.xml` — new screen

**Layout — required view IDs** (ViewBinding will generate `FragmentIngestBinding`):
- `etCourseCode` (EditText), `etYear` (EditText, numeric)
- `spSemester` (Spinner → sem1/sem2/sem3), `spMoed` (Spinner → moed1/moed2)
- `etVersion` (EditText, default "v1")
- `btnPickQuestions` (Button) + `tvQuestionsFile` (TextView, shows picked name)
- `btnPickAnswerKey` (Button) + `tvAnswerKeyFile` (TextView)
- `btnSubmitIngest` (Button)
- `pbIngest` (ProgressBar) + `tvIngestStatus` (TextView)

**Fragment logic** — two PDF pickers, then upload-each-to-Storage → POST. Reuse
`FirebaseManager.uploadFileToStorage` (already used by `ValidationFragment`).

```kotlin
class IngestFragment : Fragment() {
    private var _binding: FragmentIngestBinding? = null
    private val binding get() = _binding!!
    private lateinit var vm: IngestViewModel

    private var questionsUri: Uri? = null
    private var answerKeyUri: Uri? = null

    private val pickQuestions = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { questionsUri = it; binding.tvQuestionsFile.text = it.lastPathSegment } }

    private val pickAnswerKey = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { answerKeyUri = it; binding.tvAnswerKeyFile.text = it.lastPathSegment } }

    override fun onCreateView(i: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentIngestBinding.inflate(i, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        vm = ViewModelProvider(this).get(IngestViewModel::class.java)
        binding.btnPickQuestions.setOnClickListener { pickQuestions.launch("application/pdf") }
        binding.btnPickAnswerKey.setOnClickListener { pickAnswerKey.launch("application/pdf") }
        binding.btnSubmitIngest.setOnClickListener { onSubmit() }
        observeVm()
    }

    private fun observeVm() {
        vm.isLoading.observe(viewLifecycleOwner) { loading ->
            binding.pbIngest.visibility = if (loading) View.VISIBLE else View.GONE
            binding.tvIngestStatus.visibility = if (loading) View.VISIBLE else View.GONE
            binding.btnSubmitIngest.isEnabled = !loading
        }
        vm.result.observe(viewLifecycleOwner) { r ->
            r ?: return@observe
            Toast.makeText(requireContext(),
                "Added ${r.chunksAdded} chunks (${r.mode}) → ${r.docId}",
                Toast.LENGTH_LONG).show()
            vm.clear()
            requireActivity().supportFragmentManager.popBackStack()
        }
        vm.errorMessage.observe(viewLifecycleOwner) { msg ->
            msg ?: return@observe
            Toast.makeText(requireContext(), "Ingest failed: $msg", Toast.LENGTH_LONG).show()
            vm.clear()
        }
    }

    private fun onSubmit() {
        val courseCode = binding.etCourseCode.text.toString().trim()
        val year = binding.etYear.text.toString().trim()
        val semester = binding.spSemester.selectedItem.toString()   // sem1/sem2/sem3
        val moed = binding.spMoed.selectedItem.toString()           // moed1/moed2
        val version = binding.etVersion.text.toString().trim().ifEmpty { "v1" }

        if (courseCode.isEmpty() || year.isEmpty()) {
            Toast.makeText(requireContext(), "Course code and year are required", Toast.LENGTH_SHORT).show(); return
        }
        if (questionsUri == null && answerKeyUri == null) {
            Toast.makeText(requireContext(), "Pick a questions and/or answer-key PDF", Toast.LENGTH_SHORT).show(); return
        }

        binding.pbIngest.visibility = View.VISIBLE
        binding.tvIngestStatus.visibility = View.VISIBLE
        binding.tvIngestStatus.text = "Uploading file(s)..."
        val fm = FirebaseManager(requireActivity())

        // Upload questions (if any), then answer key (if any), then POST. Nested
        // callbacks keep it simple; a coroutine wrapper is a fine refactor later.
        uploadOrNull(fm, questionsUri) { qUrl ->
            uploadOrNull(fm, answerKeyUri) { aUrl ->
                binding.tvIngestStatus.text = "Indexing on server..."
                vm.ingest(
                    IngestRequest(
                        courseCode = courseCode, year = year, semester = semester,
                        moed = moed, version = version,
                        questionsUrl = qUrl, answerKeyUrl = aUrl
                    )
                )
            }
        }
    }

    /** Upload [uri] if non-null and return its download URL, else pass null through. */
    private fun uploadOrNull(fm: FirebaseManager, uri: Uri?, next: (String?) -> Unit) {
        if (uri == null) { next(null); return }
        fm.uploadFileToStorage(
            fileUri = uri,
            userId = UserSession.uid,
            onSuccess = { url -> next(url) },
            onFailed = { err ->
                binding.pbIngest.visibility = View.GONE
                binding.tvIngestStatus.visibility = View.GONE
                Toast.makeText(requireContext(), "Upload failed: $err", Toast.LENGTH_SHORT).show()
            }
        )
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }

    companion object { fun newInstance() = IngestFragment() }
}
```

### 6. `ui/DashboardFragment.kt` — add the button/entry
Add a third item to the existing `showAddDocumentDialog()`:

```kotlin
private fun showAddDocumentDialog() {
    AlertDialog.Builder(requireContext())
        .setTitle("Add Document")
        .setItems(arrayOf("Scan Document", "Upload PDF", "Upload Exam Key (Knowledge Base)")) { _, which ->
            when (which) {
                0 -> (requireActivity() as HostActivity).navigateTo(CameraFragment.newInstance())
                1 -> pickPdfLauncher.launch("application/pdf")
                2 -> (requireActivity() as HostActivity).navigateTo(IngestFragment.newInstance())
            }
        }
        .show()
}
```
(If teacher-only gating exists, hide item 2 for student accounts.)

---

## Wiring sequence (summary)
1. User opens **Upload Exam Key**, fills course_code/year/semester/moed/version,
   picks a questions PDF and/or an answer-key PDF.
2. Each picked PDF → `uploadFileToStorage` → Firebase Storage download URL.
3. `POST /api/ingest` with the metadata + URL(s) (reuses existing `ApiClient`).
4. Backend OCRs, chunks, embeds, upserts into Chroma; returns counts.
5. Toast the result; pop back to Dashboard.

## Acceptance criteria
- [ ] New "Upload Exam Key (Knowledge Base)" entry opens the ingest form.
- [ ] Submitting with an answer-key PDF returns 200 and a Toast like
      "Added N chunks (answer_key_only) → 6003_2019_sem1_moed1_v1".
- [ ] Submitting both PDFs returns `mode = "paired"`.
- [ ] Re-submitting the same metadata does **not** duplicate (chunks_added stable).
- [ ] Submitting with no file or missing course_code/year is blocked client-side.
- [ ] semester/moed come from dropdowns with exactly: sem1/sem2/sem3, moed1/moed2.

## Notes / gotchas
- **Base URL readiness**: guard with `FastApiService.ApiClient.isReady` (same as the
  evaluate flow) before submitting; show a "server not reachable yet" message if false.
- **Storage path**: `uploadFileToStorage` currently hard-codes `submissions/{uid}/…pdf`.
  Reusing it is fine for v1 (backend only needs a downloadable URL). Optional polish:
  add a `folder` param and store KB files under `knowledge_base/{uid}/…`.
- **No grading side effects**: do NOT call `saveSubmission` / `updateSubmissionGraded`
  here — ingest must not create a submission record.
- **Large PDFs**: ingest may take 10–40s (multi-page OCR). The infinite `readTimeout`
  already handles it; just keep the progress UI visible.
```
