package com.devuloopers.knet.domain.policy

import com.devuloopers.knet.identity.RegisteredDeviceId
import com.devuloopers.knet.traffic.model.IngressContext
import com.devuloopers.knet.traffic.model.IngressKind
import com.devuloopers.knet.traffic.model.TlsFallbackClientScope

/** Execution identity for saved policy; selection and online presence never authenticate a request. */
sealed interface PolicyWorkspace {
    /** Desktop, manual proxy and unattributed traffic. */
    data object Other : PolicyWorkspace
    /** Full admitted device identity; [id] is never derived from its display name or address. */
    data class Device(val id: RegisteredDeviceId) : PolicyWorkspace

    /** Stable database key, independent of addresses and display names. */
    val key: String get() = when (this) { Other -> "other"; is Device -> "device:${id.value}" }

    companion object {
        /** Strict storage decoder; aggregate traffic scopes cannot own a policy. */
        fun decode(value: String): PolicyWorkspace = when {
            value == "other" -> Other
            value.startsWith("device:") && value.length > 7 -> Device(RegisteredDeviceId(value.substring(7)))
            else -> error("Invalid policy workspace.")
        }
        /** Only authenticated ingress categories establish device ownership. */
        fun fromIngress(ingress: IngressContext?): PolicyWorkspace =
            fromIdentity(ingress?.kind, ingress?.clientIdentity?.value)

        /** TLS fallback already carries the admitted client's immutable identity. */
        fun fromClient(client: TlsFallbackClientScope?): PolicyWorkspace = when (client) {
            is TlsFallbackClientScope.Identified -> fromIdentity(client.kind, client.identity.value)
            else -> Other
        }

        private fun fromIdentity(kind: IngressKind?, identity: String?): PolicyWorkspace =
            if (identity != null && (kind == IngressKind.LanPairedDevice || kind == IngressKind.AdbDevice || kind == IngressKind.CompanionDirect || kind == IngressKind.CompanionRelay))
                Device(RegisteredDeviceId(identity)) else Other
    }
}

/** Availability controls discovery; it never enables a rule for another workspace. */
sealed interface RuleAvailability {
    /** Reusable definition visible in every workspace. */
    data object Shared : RuleAvailability
    /** Definition belongs exclusively to [owner]. */
    data class Private(val owner: PolicyWorkspace) : RuleAvailability
}

/** Typed rule family used by library operations and usage diagnostics. */
enum class PolicyRuleKind { BREAKPOINT, NETWORK_CONDITION, PROTECTED_TRAFFIC }

/**
 * A committed definition and its independent device activations.
 * @property rule Authored feature payload; execution enablement is projected separately.
 * @property availability Library visibility and private ownership.
 * @property revision Positive optimistic concurrency token for authored changes.
 * @property enabledOn Full workspace identities that currently execute this definition.
 * @throws IllegalArgumentException For an invalid revision or a foreign private activation.
 */
data class PolicyDefinition<Rule>(
    val rule: Rule,
    val availability: RuleAvailability,
    val revision: Long,
    val enabledOn: Set<PolicyWorkspace>,
) {
    init {
        require(revision >= 1)
        require(availability !is RuleAvailability.Private || enabledOn.all { it == availability.owner })
    }
    /** Whether [workspace] may discover this definition. */
    fun visibleTo(workspace: PolicyWorkspace): Boolean = availability == RuleAvailability.Shared ||
        (availability as? RuleAvailability.Private)?.owner == workspace
    /** Whether the definition participates in this workspace's runtime. */
    fun activeOn(workspace: PolicyWorkspace): Boolean = visibleTo(workspace) && workspace in enabledOn
}

/**
 * Result acknowledged only after the definition and activation transaction is committed and published.
 * @property replacedRuleIds Previous exact matching activations disabled only in the command workspace.
 */
data class RuleSaveResult(val replacedRuleIds: Set<String> = emptySet()) {
    /** Whether this committed save replaced an existing active choice for its device. */
    val replacedExistingRule: Boolean get() = replacedRuleIds.isNotEmpty()
}

/**
 * Immutable editor target; a later tab selection cannot redirect an in-flight save.
 * @property workspace Captured creation or editing identity.
 * @property availability Requested saved library location.
 * @property expectedRevision Opened authored revision, or null for creation.
 */
data class RuleEditContext(
    val workspace: PolicyWorkspace = PolicyWorkspace.Other,
    val availability: RuleAvailability = RuleAvailability.Private(workspace),
    val expectedRevision: Long? = null,
    /** Explicit quick-apply command; ordinary edits retain activation. */
    val activateAfterSave: Boolean = false,
    /** Availability when opened; an unsaved promotion may still be changed back. */
    val originalAvailability: RuleAvailability = availability,
    /** Identifies this editor invocation so an older save cannot close a replacement draft. */
    val editorId: String = newPolicyEditorId(),
    /** Shared usage disclosed when this revision was opened; checked transactionally at commit. */
    val expectedUsage: Set<PolicyWorkspace>? = null,
)

/**
 * Consistent manager projection of definitions and their execution owner.
 * @property workspace Identity whose private library and activations are projected.
 * @property workspaceName Display-only name for the manager header.
 * @property definitions Visible Shared and owned private definitions, including inactive entries.
 */
data class PolicyRuleCatalog<Rule>(
    val workspace: PolicyWorkspace,
    val workspaceName: String,
    val definitions: List<PolicyDefinition<Rule>>,
    /** Display-only names for shared-definition impact. */
    val workspaceNames: Map<PolicyWorkspace, String> = emptyMap(),
)

/**
 * Frozen destructive action; the definition revision is rechecked inside the delete transaction.
 * @property id Stable definition identity within its typed rule family.
 * @property context Original workspace, authored revision and disclosed usage.
 * @property affectedWorkspaceNames Display-only impact captured for the confirmation.
 */
data class PolicyDeletionRequest(
    val id: String,
    val context: RuleEditContext,
    val affectedWorkspaceNames: List<String>,
)

/** Human-readable usage metadata; stable full identities remain the actual policy keys. */
fun <Rule> PolicyDefinition<Rule>.usageNames(names: Map<PolicyWorkspace, String>): List<String> =
    enabledOn.map { names[it] ?: if (it == PolicyWorkspace.Other) "Other traffic" else "Saved device · ${it.key.takeLast(6)}" }.sorted()

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
private fun newPolicyEditorId(): String = kotlin.uuid.Uuid.random().toString()

/** Freezes both the authored revision and its disclosed activation impact for an editor/confirmation. */
fun <Rule> PolicyDefinition<Rule>.editContext(workspace: PolicyWorkspace): RuleEditContext =
    RuleEditContext(workspace, availability, revision, expectedUsage = enabledOn)
