package com.astrawave.app.data

import android.content.Context
import com.astrawave.app.core.AstraWaveEntitlement
import com.astrawave.app.core.AstraWaveEntitlementPolicy
import com.astrawave.app.core.AstraWavePlan
import com.astrawave.app.core.EntitlementSnapshot
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Read-only entitlement client. Public app code never writes plan state; the backend/store
 * remains authoritative through /entitlements/{uid}, which is protected by Firestore rules.
 */
class EntitlementCloudRepository(context: Context) {
    private val ready = AstraWaveFirebase.initialize(context.applicationContext)
    private val auth: FirebaseAuth? get() = if (ready) FirebaseAuth.getInstance() else null
    private val db: FirebaseFirestore? get() = if (ready) FirebaseFirestore.getInstance() else null

    val signedIn: Boolean get() = auth?.currentUser != null

    fun load(
        fallbackPlan: AstraWavePlan = AstraWavePlan.FREE,
        onComplete: (Result<EntitlementSnapshot>) -> Unit,
    ) {
        val uid = auth?.currentUser?.uid
        if (!ready || uid == null) {
            onComplete(Result.success(AstraWaveEntitlementPolicy.snapshot(null, fallbackPlan, source = "local-fallback")))
            return
        }
        val ref = db?.collection("entitlements")?.document(uid)
        if (ref == null) {
            onComplete(Result.success(AstraWaveEntitlementPolicy.snapshot(uid, fallbackPlan, source = "local-fallback")))
            return
        }
        ref.get()
            .addOnSuccessListener { doc ->
                onComplete(runCatching { decode(uid, doc, fallbackPlan) })
            }
            .addOnFailureListener { error -> onComplete(Result.failure(error)) }
    }

    private fun decode(uid: String, doc: DocumentSnapshot, fallbackPlan: AstraWavePlan): EntitlementSnapshot {
        if (!doc.exists()) return AstraWaveEntitlementPolicy.snapshot(uid, fallbackPlan, source = "cloud-missing")
        val plan = parsePlan(doc.getString("plan") ?: doc.getString("tier")) ?: fallbackPlan
        val defaults = AstraWaveEntitlementPolicy.snapshot(uid, plan).activeEntitlements
        val explicit = (doc.get("activeEntitlements") as? List<*>)
            ?.mapNotNull { raw -> runCatching { AstraWaveEntitlement.valueOf(raw.toString().uppercase()) }.getOrNull() }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: defaults
        return EntitlementSnapshot(
            userId = uid,
            plan = plan,
            activeEntitlements = explicit,
            trialEndsAtEpochMs = timestampMs(doc, "trialEndsAt") ?: doc.getLong("trialEndsAtEpochMs"),
            renewsAtEpochMs = timestampMs(doc, "renewsAt") ?: doc.getLong("renewsAtEpochMs"),
            source = "firestore-entitlements",
        )
    }

    private fun parsePlan(raw: String?): AstraWavePlan? {
        val normalized = raw.orEmpty().trim().uppercase().replace("ASTRAWAVE", "").trim()
        return when (normalized) {
            "FREE" -> AstraWavePlan.FREE
            "PLUS", "+" -> AstraWavePlan.PLUS
            "PREMIUM" -> AstraWavePlan.PREMIUM
            else -> null
        }
    }

    private fun timestampMs(doc: DocumentSnapshot, field: String): Long? =
        (doc.get(field) as? Timestamp)?.toDate()?.time
}
