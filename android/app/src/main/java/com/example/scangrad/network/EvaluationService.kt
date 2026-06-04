package com.example.scangrad.network

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fire-and-forget grading runner.
 *
 * Unlike a ViewModel-scoped call, this runs on an application-lifetime
 * [CoroutineScope], so grading keeps going after the user leaves the
 * validation screen. When the backend responds, the matching submission doc is
 * flipped from PENDING to GRADED directly in Firestore — any screen with a live
 * listener (dashboard, submission details) then updates on its own.
 */
object EvaluationService {

    private const val TAG = "EvaluationService"

    private val repository = EvaluationRepository()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Sends [request] for grading in the background and persists the result. */
    fun submitForGrading(request: EvaluationRequest) {
        scope.launch {
            repository.sendForGrading(request)
                .onSuccess { response -> persistGrade(request.submissionId, response) }
                .onFailure { e -> Log.e(TAG, "Grading failed for ${request.submissionId}", e) }
        }
    }

    private fun persistGrade(submissionId: String, response: EvaluationResponse) {
        FirebaseFirestore.getInstance()
            .collection("submissions")
            .document(submissionId)
            .update(
                mapOf(
                    "status" to "GRADED",
                    "score" to response.overallScore,
                    "feedback" to response.generalFeedback,
                    "confidenceLevel" to response.confidenceLevel,
                    "evaluations" to response.evaluations
                )
            )
            .addOnFailureListener { e -> Log.e(TAG, "Saving grade failed for $submissionId", e) }
    }
}
